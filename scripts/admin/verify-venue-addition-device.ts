import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../../supabase/functions/_shared/firestore.ts";
assertEquals(fs.projectId(),"demo-admin-repair");
assertEquals(Deno.env.get("FIRESTORE_EMULATOR_HOST"),"127.0.0.1:18080");
const base=Deno.env.get("SUPABASE_URL");
assertEquals(base,"http://127.0.0.1:54321");
const owners=[];
for(const uid of ["venue-test-owner","venue-test-player"]) {
  const user=await fs.get(`users/${uid}`);
  assertEquals(user?.role,"OWNER");assertEquals(user?.ownerStatus,"APPROVED");
  const apps=await fs.listOwnerApplications(uid);
  const app=apps.find(a=>uid==="venue-test-owner"?a.purpose==="VENUE_ADDITION":a.purpose==="OWNER_ONBOARDING");
  assert(app);assertEquals(app.status,"APPROVED");
  const venue=await fs.get(`venues/${app.__documentId}`);
  assertEquals(venue?.ownerId,uid);assertEquals(venue?.status,"ACTIVE");
  assertEquals(venue?.courtCount,0);
  assertEquals((app.venueDraft as Record<string,unknown>).courtCount,2);
  assertEquals((await fs.query("courts",[],{parent:`venues/${app.__documentId}`})).length,0);
  const venues=await fs.query("venues",[{field:"ownerId",op:"EQUAL",value:uid}]);
  assertEquals(venues.length,uid==="venue-test-owner"?2:1);
  const documents=app.documentPaths as string[];
  assert(documents.length>0);
  for(const path of documents) {
    const response=await fetch(`${base}/storage/v1/object/public/owner-docs/${path}`);
    assert(response.status!==200);await response.body?.cancel();
    assert(!(venue?.photoPaths as string[]).includes(path));
  }
  for(const path of venue?.photoPaths as string[]) {
    const response=await fetch(`${base}/storage/v1/object/public/venue-photos/${path}`);
    assertEquals(response.status,200);await response.body?.cancel();
  }
  owners.push({uid,role:user?.role,ownerStatus:user?.ownerStatus,applicationId:app.__documentId,purpose:app.purpose,status:app.status,venueCount:venues.length,activeCourts:venue?.courtCount,declaredCourts:2,privateDocuments:documents.length});
}
assertEquals((await fs.get("venues/venue-test-existing"))?.status,"ACTIVE");
console.log(JSON.stringify({backend:"LOCAL Firebase Emulator + Supabase",owners,existingVenuePreserved:true,privateDocumentsNotPublic:true},null,2));
