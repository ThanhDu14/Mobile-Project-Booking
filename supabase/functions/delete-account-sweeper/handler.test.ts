// T076: delete-account-sweeper (contracts/edge-functions-account.md §2, X16) trên Firestore + Auth Emulator.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { type DeletionDeps, defaultDeletionDeps, supabaseStorageAdmin } from "../_shared/account-deletion.ts";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { clearEmulators } from "../_shared/testing.ts";
import { fakeHelpers, fakeStorageServer, seedFavorites, seedUser, storageFor } from "../delete-account/test_fakes.ts";
import { handleSweeper } from "./handler.ts";

const SECRET = "cron-secret-test";
const MINUTE = 60_000;

async function withDeps(fn: (deps: DeletionDeps & { cronSecret: string }) => Promise<void>) {
  await clearEmulators();
  const storage = fakeStorageServer();
  try {
    await fn({
      ...defaultDeletionDeps(),
      helpers: fakeHelpers().helpers,
      storage: supabaseStorageAdmin(storageFor(storage)),
      cronSecret: SECRET,
    });
  } finally {
    await storage.close();
  }
}

function sweep(deps: DeletionDeps & { cronSecret: string }, secret: string | null = SECRET) {
  const headers: Record<string, string> = secret === null ? {} : { "x-cron-secret": secret };
  return handleSweeper(new Request("http://localhost/", { method: "POST", headers }), deps);
}

/** Bản ghi đã chốt; người dùng chỉ còn trong Firestore (không có tài khoản Auth). */
async function seedDeletion(uid: string, data: Record<string, unknown>) {
  await seedUser(uid, { deletionRequestedAt: new Date() });
  await commit([createWrite(`accountDeletions/${uid}`, {
    state: "COMMITTED",
    requestedAt: new Date(),
    wasOwner: false,
    notify: [],
    steps: {},
    attempts: 0,
    finalSweepAfter: new Date(Date.now() + 65 * MINUTE),
    ...data,
  })]);
}

Deno.test("X16: thiếu hoặc sai x-cron-secret → 401", async () => {
  await withDeps(async (deps) => {
    assertEquals((await sweep(deps, null)).status, 401);
    assertEquals((await sweep(deps, "sai")).status, 401);
    assertEquals((await sweep({ ...deps, cronSecret: "" }, "")).status, 401);
  });
});

Deno.test("bản ghi COMMITTED có bước FAILED → chạy lại tới DONE, bước SKIPPED không bị chạy lại", async () => {
  await withDeps(async (deps) => {
    await seedDeletion("u1", {
      steps: { disableAuth: "DONE", notifyGuests: "DONE", anonymizeReviews: "FAILED", anonymizeCommunity: "SKIPPED" },
      lastError: "anonymizeReviews:Error",
    });
    const calls: string[] = [];
    const helpers = {
      ...deps.helpers,
      anonymizeReviews: async () => void calls.push("anonymizeReviews"),
      anonymizeCommunity: async () => void calls.push("anonymizeCommunity"),
    };
    const res = await sweep({ ...deps, helpers });
    assertEquals(res.status, 200);
    assertEquals(calls, ["anonymizeReviews"]);
    const del = await get("accountDeletions/u1");
    const steps = del?.steps as Record<string, string>;
    assertEquals([del?.state, steps.anonymizeReviews, steps.anonymizeCommunity], ["DONE", "DONE", "SKIPPED"]);
    assertEquals(await get("users/u1"), null);
  });
});

Deno.test("mỗi lần tối đa 5 bản ghi COMMITTED, theo requestedAt tăng dần", async () => {
  await withDeps(async (deps) => {
    for (let i = 0; i < 7; i++) {
      await seedDeletion(`u${i}`, { requestedAt: new Date(Date.now() - (10 - i) * MINUTE) });
    }
    const body = await (await sweep(deps)).json();
    assertEquals(body.processed, 5);
    const states = await Promise.all([0, 1, 2, 3, 4, 5, 6].map(async (i) => (await get(`accountDeletions/u${i}`))?.state));
    assertEquals(states, ["DONE", "DONE", "DONE", "DONE", "DONE", "COMMITTED", "COMMITTED"]);
  });
});

Deno.test("lượt quét cuối: DONE và finalSweepAfter <= now → xóa lại subcollection một lần, ghi steps.finalSweep", async () => {
  await withDeps(async (deps) => {
    await commit([createWrite("accountDeletions/u-final", {
      state: "DONE",
      requestedAt: new Date(Date.now() - 70 * MINUTE),
      steps: { deleteSubcollections: "DONE" },
      attempts: 1,
      finalSweepAfter: new Date(Date.now() - 5 * MINUTE),
    })]);
    // Token cũ còn hạn đã ghi thêm yêu thích sau khi xóa.
    await seedFavorites("u-final", ["v9"]);
    const body = await (await sweep(deps)).json();
    assertEquals(body.finalSweeps, 1);
    assertEquals(await get("users/u-final/favorites/v9"), null);
    assertEquals(((await get("accountDeletions/u-final"))?.steps as Record<string, string>).finalSweep, "DONE");

    await seedFavorites("u-final", ["v10"]);
    assertEquals((await (await sweep(deps)).json()).finalSweeps, 0);
    assertEquals((await get("users/u-final/favorites/v10"))?.venueId, "v10");
  });
});

Deno.test("attempts >= 20 → ghi log mức error", async () => {
  await withDeps(async (deps) => {
    await seedDeletion("u-stuck", { attempts: 20 });
    const errors: unknown[][] = [];
    const original = console.error;
    console.error = (...args: unknown[]) => errors.push(args);
    try {
      const helpers = { ...deps.helpers, anonymizeReviews: () => Promise.reject(new Error("x")) };
      await sweep({ ...deps, helpers });
    } finally {
      console.error = original;
    }
    assertEquals(errors.some((args) => String(args[0]).includes("u-stuck")), true);
  });
});
