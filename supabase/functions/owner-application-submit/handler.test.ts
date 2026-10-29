import { assertEquals } from "jsr:@std/assert@1.0.19";
import { HttpError } from "../_shared/auth.ts";
import type { VerifiedToken } from "../_shared/auth.ts";
import { decodeFields, type FirestoreTransaction, type Write } from "../_shared/firestore.ts";
import type { OwnerApplicationSubmitDeps } from "./handler.ts";
import { handleOwnerApplicationSubmit } from "./handler.ts";

const uid = "player-1";
const appId = "Abcdefghijklmnopqrst";
const player: VerifiedToken & { appRole: "PLAYER" } = {
  uid, appRole: "PLAYER", email: null, emailVerified: false, phone: null, name: null, signInProvider: "password",
};

const body = {
  applicationId: appId,
  venueDraft: {
    name: " Sân thử ", address: " 1 Đường A ", districtId: "q1",
    location: { latitude: 10.78, longitude: 106.69 }, phone: "0900000000", courtCount: 2,
    openTime: "0600", closeTime: "2200", photoPaths: [`${uid}/${appId}/venue.jpg`],
  },
  representativeName: " Người đại diện ", representativePhone: "0900000001",
  documentPaths: [`${uid}/${appId}/document.jpg`],
};

function deps(options: {
  account?: Record<string, unknown>;
  applications?: { id: string; data: Record<string, unknown> }[];
} = {}) {
  const account = options.account ?? { role: "PLAYER", ownerStatus: "NONE", accountStatus: "ACTIVE" };
  const applications = options.applications ?? [];
  let writes: Write[] = [];
  const transaction: FirestoreTransaction = {
    get: async (path) => path === `users/${uid}`
      ? account
      : applications.find((item) => path === `ownerApplications/${item.id}`)?.data ?? null,
    query: async (_collection, filters) => applications.filter((item) =>
      filters.every((filter) => item.data[filter.field] === filter.value)
    ),
    commit: async (nextWrites) => { writes = nextWrites; },
  };
  const services: OwnerApplicationSubmitDeps = {
    auth: { requireUser: async () => player },
    verifyUploadedFiles: async () => undefined,
    firestore: { runTransaction: async (operation) => {
      const outcome = await operation(transaction);
      if (outcome.writes.length) await transaction.commit(outcome.writes);
      return outcome.result;
    } },
  };
  return { services, getWrites: () => writes };
}

async function call(d: OwnerApplicationSubmitDeps, payload: unknown = body) {
  return await handleOwnerApplicationSubmit(new Request("http://localhost", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  }), d);
}

Deno.test("new submit atomically creates PENDING application and updates authoritative ownerStatus", async () => {
  const d = deps();
  const response = await call(d.services);
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { applicationId: appId });
  assertEquals(d.getWrites().length, 2);
  const applicationWrite = d.getWrites().find((write) => write.update.name.endsWith(`/ownerApplications/${appId}`))!;
  const accountWrite = d.getWrites().find((write) => write.update.name.endsWith(`/users/${uid}`))!;
  assertEquals(decodeFields(applicationWrite.update.fields).status, "PENDING");
  assertEquals(decodeFields(applicationWrite.update.fields).uid, uid);
  assertEquals(decodeFields(accountWrite.update.fields), { ownerStatus: "PENDING" });
});

Deno.test("retry while already PENDING returns the unique authoritative application without another write", async () => {
  const d = deps({
    account: { role: "PLAYER", ownerStatus: "PENDING" },
    applications: [{ id: "ExistingApplication000", data: { uid, status: "PENDING" } }],
  });
  const response = await call(d.services);
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { applicationId: "ExistingApplication000" });
  assertEquals(d.getWrites().length, 0);
});

Deno.test("resubmit updates only the unique REJECTED document and moves user to PENDING", async () => {
  const d = deps({
    account: { role: "PLAYER", ownerStatus: "REJECTED" },
    applications: [{ id: appId, data: { uid, status: "REJECTED", rejectReason: "Thiếu giấy tờ" } }],
  });
  const response = await call(d.services);
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { applicationId: appId });
  assertEquals(d.getWrites().length, 2);
  const applicationWrite = d.getWrites().find((write) => write.update.name.endsWith(`/ownerApplications/${appId}`))!;
  const fields = decodeFields(applicationWrite.update.fields);
  assertEquals([fields.status, fields.rejectReason, fields.reviewedBy, fields.reviewedAt], ["PENDING", null, null, null]);
});

Deno.test("ambiguous active history fails closed and does not write", async () => {
  const d = deps({
    account: { role: "PLAYER", ownerStatus: "PENDING" },
    applications: [
      { id: "ApplicationIdentifier1", data: { uid, status: "PENDING" } },
      { id: "ApplicationIdentifier2", data: { uid, status: "PENDING" } },
    ],
  });
  const response = await call(d.services);
  assertEquals(response.status, 409);
  assertEquals((await response.json()).error.code, "AMBIGUOUS_RESULT");
  assertEquals(d.getWrites().length, 0);
});

Deno.test("foreign object path and client-supplied protected fields are never accepted as trusted values", async () => {
  const d = deps();
  const payload = { ...body, documentPaths: ["other-user/other-app/secret.jpg"], status: "APPROVED", role: "OWNER" };
  const response = await call(d.services, payload);
  assertEquals(response.status, 400);
  assertEquals((await response.json()).error.code, "VALIDATION_ERROR");
  assertEquals(d.getWrites().length, 0);
});

Deno.test("submission fails closed when uploaded object verification is unavailable", async () => {
  const d = deps();
  d.services.verifyUploadedFiles = async () => {
    throw new HttpError(503, "UPLOAD_VERIFICATION_UNAVAILABLE", "Chưa thể xác minh tệp");
  };

  const response = await call(d.services);

  assertEquals(response.status, 503);
  assertEquals((await response.json()).error.code, "UPLOAD_VERIFICATION_UNAVAILABLE");
  assertEquals(d.getWrites().length, 0);
});
