// T039 (spec 050 US4): cron nhắc chủ sân hoàn tiền sau 24 giờ.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { FakeFirestore } from "../_shared/fake-firestore.ts";
import { handleRefundReminders } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const NOW = new Date("2026-11-25T03:00:00Z");
const HOUR = 60 * 60_000;

function setup() {
  const fs = new FakeFirestore(() => NOW);
  fs.seed("venues/v1", { ownerId: "owner1", name: "Sân Ánh Dương" });
  const base = { venueId: "v1", userId: "p1", amount: 36_000, bookingId: "b1", reason: "REJECTED" };
  fs.seed("refunds/old", {
    ...base,
    status: "PENDING",
    createdAt: new Date(NOW.getTime() - 25 * HOUR),
    remindedAt: null,
  });
  fs.seed("refunds/fresh", {
    ...base,
    status: "PENDING",
    createdAt: new Date(NOW.getTime() - 2 * HOUR),
    remindedAt: null,
  });
  fs.seed("refunds/reminded", {
    ...base,
    status: "PENDING",
    createdAt: new Date(NOW.getTime() - 48 * HOUR),
    remindedAt: new Date(NOW.getTime() - HOUR),
  });
  fs.seed("refunds/done", {
    ...base,
    status: "DONE",
    createdAt: new Date(NOW.getTime() - 48 * HOUR),
    remindedAt: null,
  });
  const firestore = {
    query: (...a: Parameters<FakeFirestore["query"]>) => Promise.resolve(fs.query(...a)),
    runTransaction: fs.runTransaction,
  };
  return { fs, deps: { firestore, now: () => NOW, cronSecret: "cron" } };
}

const call = async (env: ReturnType<typeof setup>, secret: string | null) => {
  const res = await handleRefundReminders(
    new Request("http://localhost", { method: "POST", headers: secret ? { "x-cron-secret": secret } : {} }),
    env.deps,
  );
  return { status: res.status, json: await res.json() };
};

Deno.test("sai x-cron-secret → 401", async () => {
  const env = setup();
  assertEquals((await call(env, null)).status, 401);
  assertEquals((await call(env, "wrong")).status, 401);
});

Deno.test("chỉ nhắc refund PENDING quá 24 giờ chưa nhắc; không nhắc lần hai", async () => {
  const env = setup();
  const res = await call(env, "cron");
  assertEquals(res.status, 200);
  assertEquals(res.json.reminded, 1);
  const n = env.fs.read("users/owner1/notifications/refund_reminder_old")!;
  assertEquals(n.type, "REFUND_REMINDER");
  assertEquals(n.isRead, false);
  assertEquals((env.fs.read("refunds/old")!.remindedAt as Date).getTime(), NOW.getTime());
  assertEquals(env.fs.read("refunds/fresh")!.remindedAt, null);
  assertEquals(env.fs.read("refunds/done")!.remindedAt, null);
  assertEquals(env.fs.read("users/owner1/notifications/refund_reminder_reminded"), null);
  assertEquals((await call(env, "cron")).json.reminded, 0);
});
