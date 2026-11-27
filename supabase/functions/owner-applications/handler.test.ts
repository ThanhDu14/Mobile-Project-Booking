import { assertEquals } from "jsr:@std/assert@1.0.19";
import * as defaultFirestore from "../_shared/firestore.ts";
import { commit, createWrite, updateWrite } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken } from "../_shared/testing.ts";
import { handleOwnerApplications } from "./handler.ts";
import { handleAdminReviewApplication } from "../admin-review-application/handler.ts";

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

Deno.test("additional venues have independent lifecycles, do not hide onboarding, and cannot be forged", async () => {
  await seed();
  await commit([
    updateWrite(`users/${uid}`, {role:"OWNER",ownerStatus:"APPROVED"}),
    createWrite("ownerApplications/initial",{uid,status:"APPROVED",submittedAt:"2026-01-01T00:00:00Z"}),
    createWrite("venues/initial",{ownerId:uid,status:"ACTIVE",name:"Existing venue"}),
  ]);
  const addition = {...draft(),purpose:"VENUE_ADDITION"};
  for (let n=0; n<2; n++) {
    const response=await handleOwnerApplications(request(addition,"OWNER"),TEST_DEPS);
    assertEquals(response.status,200); await response.body?.cancel();
  }
  assertEquals((await defaultFirestore.get(`users/${uid}`))?.ownerStatus,"APPROVED");
  assertEquals((await defaultFirestore.get(`users/${uid}`))?.role,"OWNER");
  assertEquals((await defaultFirestore.get("venues/initial"))?.name,"Existing venue");
  assertEquals(await defaultFirestore.get(`venues/${applicationId}`),null);
  const status=await handleOwnerApplications(request({action:"STATUS"},"OWNER"),TEST_DEPS);
  assertEquals(status.status,200); assertEquals((await status.json()).application.applicationId,"initial");
  const scoped=await handleOwnerApplications(request({action:"STATUS",purpose:"VENUE_ADDITION",applicationId},"OWNER"),TEST_DEPS);
  assertEquals((await scoped.json()).application.status,"PENDING");
  const secondId="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const second=await handleOwnerApplications(request({...draft(secondId),purpose:"VENUE_ADDITION"},"OWNER"),TEST_DEPS);
  assertEquals(second.status,200); await second.body?.cancel();
  await commit([updateWrite(`ownerApplications/${applicationId}`,{status:"REJECTED",rejectReason:"TEST reason"})]);
  const resubmit=await handleOwnerApplications(request(addition,"OWNER"),TEST_DEPS);
  assertEquals(resubmit.status,200); await resubmit.body?.cancel();
  assertEquals((await defaultFirestore.get(`ownerApplications/${applicationId}`))?.rejectReason,undefined);
  assertEquals((await defaultFirestore.get(`users/${uid}`))?.ownerStatus,"APPROVED");
  const list=await handleOwnerApplications(request({action:"LIST",purpose:"VENUE_ADDITION"},"OWNER"),TEST_DEPS);
  assertEquals((await list.json()).applications.length,2);
  await commit([updateWrite(`ownerApplications/${applicationId}`,{uid:"another-owner"})]);
  const forged=await handleOwnerApplications(request(addition,"OWNER"),TEST_DEPS);
  assertEquals(forged.status,403); await forged.body?.cancel();
  await commit([updateWrite(`users/${uid}`,{role:"PLAYER",ownerStatus:"NONE"})]);
  const revoked=await handleOwnerApplications(request({...draft(crypto.randomUUID()),purpose:"VENUE_ADDITION"},"OWNER"),TEST_DEPS);
  assertEquals(revoked.status,403); await revoked.body?.cancel();
});

Deno.test("concurrent additional submit/review creates one application and one venue; revocation during publication wins", async () => {
  await seed();
  await commit([
    updateWrite(`users/${uid}`,{role:"OWNER",ownerStatus:"APPROVED"}),
    ...["addition-admin-a","addition-admin-b"].map(id=>createWrite(`users/${id}`,{role:"ADMIN",accountStatus:"ACTIVE"})),
  ]);
  const addition={...draft(),purpose:"VENUE_ADDITION"};
  const submits=await Promise.all([handleOwnerApplications(request(addition,"OWNER"),TEST_DEPS),handleOwnerApplications(request(addition,"OWNER"),TEST_DEPS)]);
  assertEquals(submits.map(r=>r.status),[200,200]);
  await Promise.all(submits.map(r=>r.body?.cancel()));
  assertEquals((await defaultFirestore.listOwnerApplications(uid)).length,1);
  const app=await defaultFirestore.get(`ownerApplications/${applicationId}`);
  const body={applicationId,decision:"APPROVE",expectedSubmittedAt:app!.submittedAt instanceof Date ? app!.submittedAt.toISOString() : app!.submittedAt};
  const admin=(id:string, payload=body)=>bearer(emulatorToken({sub:id,appRole:"ADMIN"}),{body:JSON.stringify(payload)});
  const deps={firestore:defaultFirestore,mergeClaims:async()=>{throw Error("Addition must not change claims");},publishPhotos:async()=>[`${applicationId}/venue.jpg`]};
  const reviews=await Promise.all([handleAdminReviewApplication(admin("addition-admin-a"),deps),handleAdminReviewApplication(admin("addition-admin-b"),deps)]);
  assertEquals(reviews.map(r=>r.status).sort(),[200,409]);
  await Promise.all(reviews.map(r=>r.body?.cancel()));
  assertEquals((await defaultFirestore.get(`venues/${applicationId}`))?.ownerId,uid);
  assertEquals((await defaultFirestore.get(`users/${uid}`))?.role,"OWNER");
  const id="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const submit=await handleOwnerApplications(request({...draft(id),purpose:"VENUE_ADDITION"},"OWNER"),TEST_DEPS);
  assertEquals(submit.status,200); await submit.body?.cancel();
  const pending=await defaultFirestore.get(`ownerApplications/${id}`);
  const version=pending!.submittedAt instanceof Date ? pending!.submittedAt.toISOString() : pending!.submittedAt;
  const revoked=await handleAdminReviewApplication(admin("addition-admin-a",{...body,applicationId:id,expectedSubmittedAt:version}),{
    ...deps,publishPhotos:async()=>{await commit([updateWrite(`users/${uid}`,{role:"PLAYER",ownerStatus:"NONE"})]);return[];},
  });
  assertEquals(revoked.status,403); await revoked.body?.cancel();
  assertEquals(await defaultFirestore.get(`venues/${id}`),null);
  assertEquals((await defaultFirestore.get(`ownerApplications/${id}`))?.status,"PENDING");
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
