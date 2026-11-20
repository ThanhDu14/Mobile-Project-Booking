// Isolated fixture for the additional-venue physical-device test. Never use hosted services.
import { assert } from "jsr:@std/assert@1.0.19";
import * as fs from "../../supabase/functions/_shared/firestore.ts";
import { createUser } from "../../supabase/functions/_shared/testing.ts";
import { mergeClaims } from "../../supabase/functions/_shared/identity.ts";
assert(fs.projectId() === "demo-admin-repair");
assert(Deno.env.get("FIRESTORE_EMULATOR_HOST") === "127.0.0.1:18080");
assert(Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") === "127.0.0.1:19099");
const docs: Record<string,Record<string,unknown>> = {};
for (const [uid,role] of [["venue-test-owner","OWNER"],["venue-test-admin","ADMIN"],["venue-test-player","PLAYER"]]) {
  try { await createUser({localId:uid,email:`${uid}@example.test`,password:"Test123456",emailVerified:true}); }
  catch (error) { if (!/EMAIL_EXISTS|LOCAL_ID_EXISTS|DUPLICATE_LOCAL_ID/.test(String(error))) throw error; }
  await mergeClaims(uid,{appRole:role,role:"authenticated"});
  docs[`users/${uid}`]={role,ownerStatus:role==="OWNER"?"APPROVED":"NONE",accountStatus:"ACTIVE",displayName:uid,email:`${uid}@example.test`};
}
docs["districts/venue-test-district"]={name:"TEST District",isActive:true,sortOrder:0};
const venue={name:"TEST Existing Venue",address:"Local TEST address",districtId:"venue-test-district",location:{latitude:10.77,longitude:106.66},phone:"0900000000",courtCount:2,openTime:"0600",closeTime:"2200",photoPaths:[],amenityIds:[],status:"ACTIVE",ownerId:"venue-test-owner",acceptsRecurring:true};
docs["venues/venue-test-existing"]=venue;
docs["ownerApplications/venue-test-initial"]={uid:"venue-test-owner",venueDraft:venue,representativeName:"TEST Owner",representativePhone:"0900000000",documentPaths:[],status:"APPROVED",submittedAt:new Date("2026-01-01T00:00:00Z")};
// Idempotent test reset, scoped to the test identities; retain unrelated fixtures.
for(const uid of ["venue-test-owner","venue-test-player"]) {
  const applications=await fs.listOwnerApplications(uid);
  const writes=applications.filter(app=>app.__documentId!=="venue-test-initial").flatMap(app=>[
    {delete:fs.documentName(`ownerApplications/${app.__documentId}`)},
    {delete:fs.documentName(`venues/${app.__documentId}`)},
  ]);
  if(writes.length) await fs.commit(writes);
}
await fs.commit(Object.entries(docs).map(([path,data])=>({update:{name:fs.documentName(path),fields:fs.encodeFields(data)}})));
console.log("Venue addition TEST fixtures ready: local demo-admin-repair only");
