import { assertEquals, assert } from "jsr:@std/assert@1.0.19";
import * as fs from "./_shared/firestore.ts";
import { getCustomClaims, mergeClaims } from "./_shared/identity.ts";
import { bearer, clearEmulators, createUser, emulatorToken } from "./_shared/testing.ts";
import { handleAdminReviewApplication as review } from "./admin-review-application/handler.ts";
import { handleAdminRevokeOwner as revoke } from "./admin-revoke-owner/handler.ts";
import { handleAdminManageUser as user } from "./admin-manage-user/handler.ts";
import { handleAdminManageVenue as venue } from "./admin-manage-venue/handler.ts";
import { handleAdminModerateReport as moderate } from "./admin-moderate-report/handler.ts";
import { handleAdminManageVoucher as voucher } from "./admin-manage-voucher/handler.ts";
import { handleAdminBroadcastNotification as broadcast } from "./admin-broadcast-notification/handler.ts";
import { handleOwnerDocs as docs } from "./owner-docs/handler.ts";
import { handleOwnerCreateVoucher as ownerVoucher } from "./owner-create-voucher/handler.ts";

// Explicit invocation only. Never skip tests or fall back to a hosted project.
function requireLocal() {
  assert(fs.projectId().startsWith("demo-"));
  for (const key of ["FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"]) assert(/^127\.0\.0\.1:\d+$/.test(Deno.env.get(key) ?? ""));
}
const request = (body: unknown, uid = "admin-local", appRole = "ADMIN") => bearer(emulatorToken({sub:uid,appRole}), {body:JSON.stringify(body)});
async function status(response: Response, expected = 200) {
  const body = await response.json();
  assertEquals(response.status, expected, JSON.stringify(body));
  return body;
}
const application = (uid: string, submittedAt = "2026-11-01T00:00:00Z") => ({uid,status:"PENDING",submittedAt,representativeName:"Test representative",representativePhone:"0900000000",venueDraft:{name:"Test venue",address:"Test address",districtId:"test-district",location:{latitude:10.7,longitude:106.6},phone:"0900000000",courtCount:2,openTime:"0600",closeTime:"2200",photoPaths:["test.jpg"]},documentPaths:[]});

Deno.test("R120 local Firebase: decisions, claim recovery, race, controls, moderation, voucher and broadcast", async () => {
  requireLocal();
  await clearEmulators();
  for (const uid of ["admin-local","admin-second","player-local","reject-local","race-local"]) await createUser({localId:uid,email:`${uid}@example.test`,password:"Test123456",emailVerified:true});
  for (const uid of ["admin-local","admin-second"]) await mergeClaims(uid,{appRole:"ADMIN",role:"authenticated"});
  await fs.commit([
    ...["admin-local","admin-second"].map((uid)=>fs.createWrite(`users/${uid}`,{role:"ADMIN",accountStatus:"ACTIVE"})),
    ...["player-local","reject-local","race-local"].map((uid)=>fs.createWrite(`users/${uid}`,{role:"PLAYER",ownerStatus:"PENDING",accountStatus:"ACTIVE"})),
    fs.createWrite("ownerApplications/approve-local",application("player-local")),
    fs.createWrite("ownerApplications/reject-local",application("reject-local")),
    fs.createWrite("ownerApplications/race-local",application("race-local")),
  ]);
  const approveBody = {applicationId:"approve-local",decision:"APPROVE",expectedSubmittedAt:"2026-11-01T00:00:00Z"};
  await status(await review(request(approveBody),{firestore:fs,mergeClaims:async()=>{throw new Error("Injected claim failure");}}),503);
  assertEquals((await fs.get("ownerApplications/approve-local"))?.status,"APPROVED");
  await status(await review(request(approveBody)));
  assertEquals((await getCustomClaims("player-local")).appRole,"OWNER");
  assertEquals((await fs.list("users/player-local/notifications")).length,1);
  await status(await review(request({applicationId:"reject-local",decision:"REJECT",rejectReason:"  Missing evidence  ",expectedSubmittedAt:"2026-11-01T00:00:00Z"})));
  assertEquals((await fs.get("ownerApplications/reject-local"))?.rejectReason,"Missing evidence");
  const race = await Promise.all([
    review(request({applicationId:"race-local",decision:"APPROVE",expectedSubmittedAt:"2026-11-01T00:00:00Z"})),
    review(request({applicationId:"race-local",decision:"REJECT",rejectReason:"Missing",expectedSubmittedAt:"2026-11-01T00:00:00Z"},"admin-second")),
  ]);
  assertEquals(race.map(r=>r.status).sort(),[200,409]);
  for(const r of race) await r.body?.cancel();
  assertEquals((await fs.list("users/race-local/notifications")).length,1);
  await status(await venue(request({venueId:"approve-local",status:"LOCKED"})));
  await status(await venue(request({venueId:"approve-local",status:"HIDDEN"})));
  await status(await venue(request({venueId:"approve-local",status:"ACTIVE"},"player-local","OWNER")),403);
  await status(await user(request({action:"SET_ACCOUNT_STATUS",uid:"reject-local",accountStatus:"LOCKED"})));
  await status(await user(request({action:"SET_ACCOUNT_STATUS",uid:"reject-local",accountStatus:"ACTIVE"})));
  await status(await revoke(request({uid:"player-local",reason:"Policy"})));
  assertEquals((await getCustomClaims("player-local")).appRole,"PLAYER");
  await status(await review(request(approveBody)));
  assertEquals((await getCustomClaims("player-local")).appRole,"PLAYER");
  await status(await review(request({applicationId:"approve-local",action:"RECOVER_CLAIMS"},"admin-second")));
  assertEquals((await getCustomClaims("player-local")).appRole,"PLAYER");
  assertEquals((await fs.get("venues/approve-local"))?.status,"HIDDEN");
  await fs.commit([fs.createWrite("reports/report-local",{status:"OPEN",targetType:"VENUE",targetPath:"venues/approve-local",reason:"Policy"})]);
  await status(await moderate(request({reportId:"report-local",action:"LOCK_VENUE"})));
  assertEquals((await fs.get("reports/report-local"))?.status,"RESOLVED");
  await status(await moderate(request({reportId:"report-local",action:"LOCK_VENUE"})));
  const validVoucher = {action:"CREATE",code:"LOCAL10",type:"FIXED",value:10,maxDiscount:10,minOrderTotal:0,validFrom:"2026-11-01T00:00:00+07:00",validTo:"2026-12-01T00:00:00+07:00",usageLimit:10,perUserLimit:1};
  await status(await voucher(request(validVoucher)));
  await status(await voucher(request({...validVoucher,action:"UPDATE",value:20,maxDiscount:20})));
  assertEquals((await fs.get("systemVouchers/LOCAL10"))?.usedCount,0);
  assertEquals((await fs.get("systemVouchers/LOCAL10"))?.value,20);
  await status(await voucher(request(validVoucher)),409);
  await fs.commit([fs.createWrite("venues/approve-local/vouchers/TAKEN10",{code:"TAKEN10"})]);
  await status(await voucher(request({...validVoucher,code:"TAKEN10"})),409);
  await status(await voucher(request({action:"TOGGLE_ACTIVE",code:"LOCAL10",isActive:false})));
  await fs.commit(Array.from({length:330},(_,i)=>fs.createWrite(`users/recipient-${i}`,{role:"PLAYER",accountStatus:i===329?"LOCKED":"ACTIVE"})));
  const active=(await fs.list("users")).filter(u=>u.accountStatus==="ACTIVE");
  assert(active.length>300);
  const broadcastBody={title:"Local test",body:"Local test only",requestId:crypto.randomUUID()};
  let batches=0;
  await status(await broadcast(request(broadcastBody),{firestore:{...fs,commit:async(writes,transaction)=>{
    if(++batches===2) throw new fs.FirestoreError("UNKNOWN",503,"Injected mid-batch failure");
    await fs.commit(writes,transaction);
  }}}),500);
  const delivered=(await fs.list("users/recipient-0/notifications"))[0];
  assert(delivered);
  await fs.commit([fs.updateWrite(`users/recipient-0/notifications/${delivered.__documentId}`,{isRead:true})]);
  const sent=await status(await broadcast(request(broadcastBody)));
  assertEquals(sent.count,active.length);
  await status(await broadcast(request(broadcastBody)));
  assertEquals((await fs.list("users/recipient-0/notifications")).length,1);
  assertEquals((await fs.list("users/recipient-0/notifications"))[0].isRead,true);
  assertEquals((await fs.list("users/recipient-329/notifications")).length,0);
});

Deno.test("R120 voucher global: simultaneous system and venue code has one winner", async () => {
  requireLocal();
  const code=`RACE${Date.now()}`;
  const venueId=`voucher-${Date.now()}`;
  await fs.commit([fs.createWrite(`venues/${venueId}`,{ownerId:"admin-local",status:"ACTIVE"})]);
  const data={action:"CREATE",code,type:"FIXED",value:10,maxDiscount:10,minOrderTotal:0,validFrom:"2026-11-01T00:00:00Z",validTo:"2026-12-01T00:00:00Z",usageLimit:10,perUserLimit:1};
  let reached=0;
  let release!:()=>void;
  const ready=new Promise<void>(resolve=>{release=resolve;});
  const barrier=async()=>{if(++reached===2)release();await ready;};
  const withBarrier=()=>{
    let initial=true;
    const transaction: typeof fs.runTransaction = async (operation,maxAttempts)=>fs.runTransaction(async tx=>operation({...tx,query:async (...args)=>{
      const result=await tx.query(...args);if(initial){initial=false;await barrier();}return result;
    }}),maxAttempts);
    return {...fs,runTransaction:transaction};
  };
  const results=await Promise.all([voucher(request(data),{firestore:withBarrier()}),ownerVoucher(request({...data,venueId}),{firestore:withBarrier()})]);
  const statuses=results.map(r=>r.status).sort();
  for(const r of results) await r.body?.cancel();
  assertEquals(statuses,[200,409]);
});

Deno.test("R120 local Storage: both signed documents, denial, download and expiry", async () => {
  requireLocal();
  const base=Deno.env.get("SUPABASE_URL") ?? "";
  assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base),"Supabase must be local");
  const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  assert(key);
  const headers={Authorization:`Bearer ${key}`,apikey:key,"Content-Type":"application/json"};
  const bucket=await fetch(`${base}/storage/v1/bucket`,{method:"POST",headers,body:JSON.stringify({id:"owner-docs",name:"owner-docs",public:false})});
  assert([200,400,409].includes(bucket.status)); await bucket.body?.cancel();
  const id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const paths=[`player-local/${id}/one.jpg`,`player-local/${id}/two.jpg`];
  await fs.commit([fs.createWrite(`ownerApplications/${id}`,{...application("player-local"),documentPaths:paths})]);
  for(const path of paths) {
    const upload=await fetch(`${base}/storage/v1/object/owner-docs/${path}`,{method:"POST",headers:{...headers,"Content-Type":"image/jpeg","x-upsert":"true"},body:new Uint8Array([255,216,255,217])});
    assertEquals(upload.status,200);await upload.body?.cancel();
    const signed=await status(await docs(request({action:"SIGN_DOWNLOAD",applicationId:id,path})));
    assertEquals(signed.expiresInSeconds,300);
    const download=await fetch(signed.signedUrl);assertEquals(download.status,200);await download.body?.cancel();
    await status(await docs(request({action:"SIGN_DOWNLOAD",applicationId:id,path},"reject-local","PLAYER")),403);
  }
  const short=await fetch(`${base}/storage/v1/object/sign/owner-docs/${paths[0]}`,{method:"POST",headers,body:JSON.stringify({expiresIn:1})});
  const shortBody=await short.json();assertEquals(short.status,200);
  await new Promise(r=>setTimeout(r,2200));
  const expired=await fetch(`${base}/storage/v1${shortBody.signedURL}`);
  assert(expired.status>=400);await expired.body?.cancel();
  await status(await docs(request({action:"SIGN_DOWNLOAD",applicationId:id,path:paths[1]})));
});
