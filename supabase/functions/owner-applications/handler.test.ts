import { assertEquals } from "jsr:@std/assert@1.0.19";
import * as defaultFirestore from "../_shared/firestore.ts";
import { commit, createWrite, updateWrite } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken } from "../_shared/testing.ts";
import { handleOwnerApplications } from "./handler.ts";

const uid = "owner-application-test-user";
const applicationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const TEST_DEPS = { firestore: defaultFirestore, verifyObjects: async (_paths: string[]) => true };

function draft(id = applicationId, name = "Sân thử nghiệm") {
  return {
    action: "SUBMIT",
    applicationId: id,
    venueDraft: {
      name,
      address: "88 Sư Vạn Hạnh, Quận 10",
      districtId: "district-10",
      location: { latitude: 10.7731, longitude: 106.6672 },
      phone: "028 3862 1145",
      courtCount: 5,
      openTime: "0530",
      closeTime: "2230",
      photoPaths: [`${uid}/${id}/venue.jpg`],
    },
    representativeName: "Nguyễn Thị Lan",
    representativePhone: "0903412778",
    documentPaths: [`${uid}/${id}/license.jpg`],
  };
}

async function seed(ownerStatus = "NONE") {
  await clearEmulators();
  await commit([
    createWrite(`users/${uid}`, { role: "PLAYER", ownerStatus, accountStatus: "ACTIVE" }),
    createWrite("districts/district-10", { isActive: true, name: "Quận 10" }),
  ]);
}

function request(body: unknown, tokenRole = "PLAYER"): Request {
  return bearer(emulatorToken({ sub: uid, appRole: tokenRole }), {
    body: JSON.stringify(body),
    headers: { "Content-Type": "application/json" },
  });
}

Deno.test("owner-applications rejects non-POST", async () => {
  const response = await handleOwnerApplications(new Request("http://localhost", { method: "GET" }));
  assertEquals(response.status, 405);
});

Deno.test("owner-applications creates once and returns committed result after retry", async () => {
  await seed();
  const first = await handleOwnerApplications(request(draft()), TEST_DEPS);
  const repeated = await handleOwnerApplications(request(draft()), TEST_DEPS);
  assertEquals(first.status, 200);
  assertEquals((await first.json()).alreadyCommitted, false);
  assertEquals(repeated.status, 200);
  assertEquals((await repeated.json()).alreadyCommitted, true);
  const user = await (await fetch(`http://${Deno.env.get("FIRESTORE_EMULATOR_HOST")}/v1/projects/${Deno.env.get("FIREBASE_PROJECT_ID")}/databases/(default)/documents/users/${uid}`, { headers: { Authorization: "Bearer owner" } })).json();
  assertEquals(user.fields.ownerStatus.stringValue, "PENDING");
  assertEquals(user.fields.role.stringValue, "PLAYER");
});

Deno.test("owner-applications rejects changed payload and competing lifecycle IDs", async () => {
  await seed();
  assertEquals((await handleOwnerApplications(request(draft()), TEST_DEPS)).status, 200);
  const changed = await handleOwnerApplications(request(draft(applicationId, "Tên khác")), TEST_DEPS);
  const competing = await handleOwnerApplications(request(draft("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")), TEST_DEPS);
  assertEquals(changed.status, 409);
  assertEquals(competing.status, 409);
});

Deno.test("owner-applications serializes concurrent lifecycle submissions", async () => {
  await seed();
  const otherId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const results = await Promise.all([
    handleOwnerApplications(request(draft()), TEST_DEPS),
    handleOwnerApplications(request(draft(otherId)), TEST_DEPS),
  ]);
  assertEquals(results.map((result) => result.status).sort(), [200, 409]);
  const applications = await defaultFirestore.latestOwnerApplication(uid);
  assertEquals(applications?.status, "PENDING");
});

Deno.test("owner-applications resubmits the same rejected document and clears its old reason", async () => {
  await seed("REJECTED");
  await commit([createWrite(`ownerApplications/${applicationId}`, {
    uid,
    venueDraft: draft().venueDraft,
    representativeName: "Nguyễn Thị Lan",
    representativePhone: "0903412778",
    documentPaths: draft().documentPaths,
    status: "REJECTED",
    rejectReason: "Bổ sung giấy phép",
  }, ["submittedAt"]) ]);
  const response = await handleOwnerApplications(request(draft()), TEST_DEPS);
  assertEquals(response.status, 200);
  const app = await (await fetch(`http://${Deno.env.get("FIRESTORE_EMULATOR_HOST")}/v1/projects/${Deno.env.get("FIREBASE_PROJECT_ID")}/databases/(default)/documents/ownerApplications/${applicationId}`, { headers: { Authorization: "Bearer owner" } })).json();
  assertEquals(app.fields.status.stringValue, "PENDING");
  assertEquals("rejectReason" in app.fields, false);
});

Deno.test("owner-applications ignores client role and rejects invalid files/paths", async () => {
  await seed();
  const invalid = { ...draft(), role: "OWNER", documentPaths: ["someone-else/file.jpg"] };
  assertEquals((await handleOwnerApplications(request(invalid, "PLAYER"), TEST_DEPS)).status, 400);
  const invalidFields = { ...draft(), role: "OWNER", ownerStatus: "APPROVED" };
  assertEquals((await handleOwnerApplications(request(invalidFields, "PLAYER"), TEST_DEPS)).status, 200);
  const user = await (await fetch(`http://${Deno.env.get("FIRESTORE_EMULATOR_HOST")}/v1/projects/${Deno.env.get("FIREBASE_PROJECT_ID")}/databases/(default)/documents/users/${uid}`, { headers: { Authorization: "Bearer owner" } })).json();
  assertEquals(user.fields.role.stringValue, "PLAYER");
  assertEquals(user.fields.ownerStatus.stringValue, "PENDING");
});

Deno.test("owner-applications serves authoritative current status", async () => {
  await seed();
  await commit([
    createWrite(`ownerApplications/${applicationId}`, {
      uid, ...draft().venueDraft, venueDraft: draft().venueDraft,
      representativeName: "Nguyễn Thị Lan", representativePhone: "0903412778",
      documentPaths: draft().documentPaths, status: "PENDING",
    }, ["submittedAt"]),
    updateWrite(`users/${uid}`, { ownerStatus: "PENDING" }),
  ]);
  const response = await handleOwnerApplications(request({ action: "STATUS" }), TEST_DEPS);
  assertEquals(response.status, 200);
  const state = await response.json();
  assertEquals(state.ownerStatus, "PENDING");
  assertEquals(state.application.applicationId, applicationId);
});

Deno.test("owner-applications refuses inconsistent ownerStatus and trusted role", async () => {
  await seed();
  await commit([updateWrite(`users/${uid}`, { ownerStatus: "APPROVED" })]);
  const response = await handleOwnerApplications(request({ action: "STATUS" }), TEST_DEPS);
  assertEquals(response.status, 409);
  assertEquals((await response.json()).error.code, "OWNER_STATE_INVALID");
});
