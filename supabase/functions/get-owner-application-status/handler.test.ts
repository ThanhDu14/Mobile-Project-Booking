import { assertEquals } from "jsr:@std/assert@1.0.19";
import type { VerifiedToken } from "../_shared/auth.ts";
import type { OwnerStatusDeps } from "./handler.ts";
import { handleGetOwnerApplicationStatus } from "./handler.ts";

const player: VerifiedToken & { appRole: "PLAYER" } = {
  uid: "player-1",
  appRole: "PLAYER",
  email: null,
  emailVerified: false,
  phone: null,
  name: null,
  signInProvider: "password",
};

function dependencies(
  account: Record<string, unknown>,
  rows: { id: string; data: Record<string, unknown> }[] = [],
  caller: VerifiedToken & { appRole: "PLAYER" | "OWNER" | "ADMIN" } = player,
): OwnerStatusDeps {
  return {
    auth: { requireUser: async () => caller },
    firestore: {
      get: async () => account,
      query: async (_collection, filters) => {
        assertEquals(filters[0], { field: "uid", op: "EQUAL", value: player.uid });
        return rows;
      },
    },
  };
}

Deno.test("NONE uses users document and ignores old approved history", async () => {
  const response = await handleGetOwnerApplicationStatus(
    new Request("http://localhost", { method: "GET" }),
    dependencies({ role: "PLAYER", ownerStatus: "NONE" }, [{ id: "old-approved", data: { uid: player.uid, status: "APPROVED" } }]),
  );
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { ownerStatus: "NONE", role: "PLAYER", application: null });
});

Deno.test("single rejected application returns only applicant status metadata", async () => {
  const submittedAt = new Date("2026-10-26T01:02:03.000Z");
  const response = await handleGetOwnerApplicationStatus(
    new Request("http://localhost", { method: "GET" }),
    dependencies({ role: "PLAYER", ownerStatus: "REJECTED" }, [{
      id: "app-1",
      data: { uid: player.uid, status: "REJECTED", submittedAt, rejectReason: "Thiếu giấy tờ", documentPaths: ["private/path"] },
    }]),
  );
  assertEquals(response.status, 200);
  assertEquals(await response.json(), {
    ownerStatus: "REJECTED",
    role: "PLAYER",
    application: {
      id: "app-1",
      status: "REJECTED",
      submittedAt: submittedAt.toISOString(),
      rejectReason: "Thiếu giấy tờ",
      documentPaths: ["private/path"],
    },
  });
});

Deno.test("returns applicant document paths for the current pending application", async () => {
  const response = await handleGetOwnerApplicationStatus(
    new Request("http://localhost", { method: "GET" }),
    dependencies({ role: "PLAYER", ownerStatus: "PENDING" }, [{
      id: "app-pending",
      data: { uid: player.uid, status: "PENDING", documentPaths: ["player-1/app/file.webp"] },
    }]),
  );
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(body.application.documentPaths, ["player-1/app/file.webp"]);
});

Deno.test("revoked owner with a stale OWNER token reads authoritative NONE", async () => {
  const staleOwner = { ...player, appRole: "OWNER" as const };
  const response = await handleGetOwnerApplicationStatus(
    new Request("http://localhost", { method: "GET" }),
    dependencies({ role: "PLAYER", ownerStatus: "NONE" }, [], staleOwner),
  );

  assertEquals(response.status, 200);
  assertEquals(await response.json(), { ownerStatus: "NONE", role: "PLAYER", application: null });
});

Deno.test("duplicate matching applications fail closed as ambiguous", async () => {
  const matching = { uid: player.uid, status: "PENDING" };
  const response = await handleGetOwnerApplicationStatus(
    new Request("http://localhost", { method: "GET" }),
    dependencies({ role: "PLAYER", ownerStatus: "PENDING" }, [
      { id: "one", data: matching },
      { id: "two", data: matching },
    ]),
  );
  assertEquals(response.status, 409);
  assertEquals((await response.json()).error.code, "AMBIGUOUS_RESULT");
});

Deno.test("role/status mismatch fails closed", async () => {
  const response = await handleGetOwnerApplicationStatus(
    new Request("http://localhost", { method: "GET" }),
    dependencies({ role: "PLAYER", ownerStatus: "APPROVED" }),
  );
  assertEquals(response.status, 409);
  assertEquals((await response.json()).error.code, "AMBIGUOUS_RESULT");
});
