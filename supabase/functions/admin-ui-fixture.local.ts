import { assert } from "jsr:@std/assert@1.0.19";
import * as fs from "./_shared/firestore.ts";
import { createUser } from "./_shared/testing.ts";
import { mergeClaims } from "./_shared/identity.ts";

assert(fs.projectId()==="demo-admin-repair");
assert(Deno.env.get("FIRESTORE_EMULATOR_HOST")==="127.0.0.1:18080");
assert(Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")==="127.0.0.1:19099");
for(const uid of ["ui-player","admin-local"]) {
  try { await createUser({localId:uid,email:`${uid}@example.test`,password:"Test123456",emailVerified:true}); } catch (error) {
    if (!String(error).includes("EMAIL_EXISTS") && !String(error).includes("LOCAL_ID_EXISTS") && !String(error).includes("DUPLICATE_LOCAL_ID")) throw error;
  }
}
await mergeClaims("admin-local",{appRole:"ADMIN",role:"authenticated"});
try { await createUser({localId:"ui-role-check",email:"eganvenven@gmail.com",password:"Test123456",emailVerified:true}); } catch(error) {
  if (!/EMAIL_EXISTS|LOCAL_ID_EXISTS|DUPLICATE_LOCAL_ID/.test(String(error))) throw error;
}
await mergeClaims("ui-role-check",{appRole:"PLAYER",role:"authenticated"});
await fs.commit([{update:{name:fs.documentName("users/ui-role-check"),fields:fs.encodeFields({role:"PLAYER",accountStatus:"ACTIVE",ownerStatus:"NONE"})}}]);
const previousAdded=await fs.query("amenities",[{field:"name",op:"EQUAL",value:"UI Added Amenity"}]);
if(previousAdded.length) await fs.commit(previousAdded.map(doc=>({delete:fs.documentName(doc.path)})));
const documents=["ui-player/ui-application/one.jpg","ui-player/ui-application/two.jpg"];
const storage=Deno.env.get("SUPABASE_URL") ?? "";
assert(storage==="http://127.0.0.1:54321");
const storageKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
assert(storageKey);
for(const path of documents) {
  const upload=await fetch(`${storage}/storage/v1/object/owner-docs/${path}`,{method:"POST",headers:{Authorization:`Bearer ${storageKey}`,apikey:storageKey,"Content-Type":"image/jpeg","x-upsert":"true"},body:new Uint8Array([255,216,255,217])});
  assert(upload.ok,await upload.text());
}
await fs.commit([
  {update:{name:fs.documentName("users/admin-local"),fields:fs.encodeFields({role:"ADMIN",accountStatus:"ACTIVE",email:"admin-local@example.test",displayName:"Local Admin",ownerStatus:"NONE"})}},
  {update:{name:fs.documentName("users/ui-player"),fields:fs.encodeFields({role:"PLAYER",ownerStatus:"PENDING",accountStatus:"ACTIVE",email:"ui-player@example.test",displayName:"UI Applicant",phone:"0900000000"})}},
  {delete:fs.documentName("venues/ui-application")},
  {update:{name:fs.documentName("ownerApplications/ui-application"),fields:fs.encodeFields({uid:"ui-player",status:"PENDING",submittedAt:new Date().toISOString(),representativeName:"UI Applicant",representativePhone:"0900000000",documentPaths:documents,venueDraft:{name:"UI Local Venue",address:"Local test address",districtId:"ui-district",location:{latitude:10.7,longitude:106.6},phone:"0900000000",courtCount:2,openTime:"0600",closeTime:"2200",photoPaths:["test.jpg"]}})}},
  {update:{name:fs.documentName("districts/ui-district"),fields:fs.encodeFields({name:"UI Local District",sortOrder:1,isActive:true,cityName:"TP. Hồ Chí Minh"})}},
  {update:{name:fs.documentName("amenities/ui-amenity"),fields:fs.encodeFields({name:"UI Local Amenity",sortOrder:1,isActive:true})}},
  {update:{name:fs.documentName("reports/ui-report"),fields:fs.encodeFields({reporterId:"ui-player",targetType:"VENUE",targetPath:"venues/ui-application",reason:"UI Local Report",detail:"Local report details",status:"OPEN",action:"NONE",createdAt:new Date().toISOString()})}},
  {update:{name:fs.documentName(`systemStats/${new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date()).replaceAll("-","")}`),fields:fs.encodeFields({totalUsers:5,totalVenues:2,totalBookings:12,totalRevenue:300000,updatedAt:new Date().toISOString()})}},
]);
console.log("Local admin UI fixtures ready (demo-admin-repair only)");
