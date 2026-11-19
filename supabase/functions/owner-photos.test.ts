import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { adminFixture } from "./_shared/admin-testing.ts";
import { bearer, emulatorToken } from "./_shared/testing.ts";
import { handleOwnerDocs } from "./owner-docs/handler.ts";
import { publishApprovedVenuePhotos } from "./_shared/venue-photo-publication.ts";

Deno.test("approved owner signs only bounded images under an owned venue; others cannot sign",()=>adminFixture(async({records})=>{
  records.set("users/owner",{role:"OWNER",ownerStatus:"APPROVED",accountStatus:"ACTIVE"});
  records.set("venues/v1",{ownerId:"owner",status:"ACTIVE"});
  const previous=globalThis.fetch;
  const oldUrl=Deno.env.get("SUPABASE_URL"),oldKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  Deno.env.set("SUPABASE_URL","http://storage.local");Deno.env.set("SUPABASE_SERVICE_ROLE_KEY","test-only");
  let signed=0;
  globalThis.fetch=(input,init)=>{
    if(String(input).startsWith("http://storage.local/")) {
      signed++;
      assertEquals(String(input).includes("/object/upload/sign/venue-photos/v1/"),true);
      return Promise.resolve(Response.json({token:"test-ticket"}));
    }
    return previous(input,init);
  };
  try {
    const request=(patch:Record<string,unknown>={})=>bearer(emulatorToken({sub:"owner",appRole:"OWNER"}),{body:JSON.stringify({action:"SIGN_VENUE_UPLOAD",venueId:"v1",contentType:"image/png",sizeBytes:1024,...patch})});
    let response=await handleOwnerDocs(request());assertEquals(response.status,200);await response.body?.cancel();
    response=await handleOwnerDocs(request({sizeBytes:2097153}));assertEquals(response.status,400);await response.body?.cancel();
    response=await handleOwnerDocs(request({contentType:"application/pdf"}));assertEquals(response.status,400);await response.body?.cancel();
    records.set("venues/v1",{ownerId:"other",status:"ACTIVE"});
    response=await handleOwnerDocs(request());assertEquals(response.status,403);await response.body?.cancel();
    assertEquals(signed,1);
  } finally {
    globalThis.fetch=previous;
    oldUrl===undefined?Deno.env.delete("SUPABASE_URL"):Deno.env.set("SUPABASE_URL",oldUrl);
    oldKey===undefined?Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY"):Deno.env.set("SUPABASE_SERVICE_ROLE_KEY",oldKey);
  }
}));

Deno.test("publication copies only declared venue photos and rejects private documents",async()=>{
  const previous=globalThis.fetch;
  const oldUrl=Deno.env.get("SUPABASE_URL"),oldKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  Deno.env.set("SUPABASE_URL","http://storage.local");Deno.env.set("SUPABASE_SERVICE_ROLE_KEY","test-only");
  const copies:unknown[]=[];
  globalThis.fetch=(_input,init)=>{copies.push(JSON.parse(String(init?.body)));return Promise.resolve(Response.json({Key:"a/photo.png"}));};
  try {
    assertEquals(await publishApprovedVenuePhotos("u","a",["u/a/photo.png"],["u/a/license.png"]),["a/photo.png"]);
    assertEquals(copies,[{bucketId:"owner-docs",sourceKey:"u/a/photo.png",destinationKey:"a/photo.png",destinationBucket:"venue-photos"}]);
    await assertRejects(()=>publishApprovedVenuePhotos("u","a",["u/a/license.png"],["u/a/license.png"]));
    await assertRejects(()=>publishApprovedVenuePhotos("u","a",["other/a/photo.png"],[]));
    assertEquals(copies.length,1);
  } finally {
    globalThis.fetch=previous;
    oldUrl===undefined?Deno.env.delete("SUPABASE_URL"):Deno.env.set("SUPABASE_URL",oldUrl);
    oldKey===undefined?Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY"):Deno.env.set("SUPABASE_SERVICE_ROLE_KEY",oldKey);
  }
});

Deno.test("legacy repair migrates only venue photos and preserves private evidence",()=>adminFixture(async({records})=>{
  records.set("users/owner",{role:"OWNER",ownerStatus:"APPROVED",accountStatus:"ACTIVE"});
  records.set("venues/a",{ownerId:"owner",status:"ACTIVE",photoPaths:["owner/a/photo.png"]});
  records.set("ownerApplications/a",{uid:"owner",status:"APPROVED",documentPaths:["owner/a/license.png"]});
  const previous=globalThis.fetch;
  const oldUrl=Deno.env.get("SUPABASE_URL"),oldKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  Deno.env.set("SUPABASE_URL","http://storage.local");Deno.env.set("SUPABASE_SERVICE_ROLE_KEY","test-only");
  let copies=0;
  globalThis.fetch=(input,init)=>{
    if(String(input).startsWith("http://storage.local/")) {
      copies++;
      assertEquals(JSON.parse(String(init?.body)).sourceKey,"owner/a/photo.png");
      return Promise.resolve(Response.json({Key:"a/photo.png"}));
    }
    return previous(input,init);
  };
  const request=()=>bearer(emulatorToken({sub:"owner",appRole:"OWNER"}),{body:JSON.stringify({action:"PUBLISH_VENUE_PHOTOS",venueId:"a"})});
  try {
    let response=await handleOwnerDocs(request());assertEquals(response.status,200);await response.body?.cancel();
    assertEquals(records.get("venues/a")?.photoPaths,["a/photo.png"]);
    assertEquals(records.get("ownerApplications/a")?.documentPaths,["owner/a/license.png"]);
    records.set("venues/a",{ownerId:"owner",status:"ACTIVE",photoPaths:["owner/a/license.png"]});
    response=await handleOwnerDocs(request());assertEquals(response.status,400);await response.body?.cancel();
    assertEquals(copies,1);
  } finally {
    globalThis.fetch=previous;
    oldUrl===undefined?Deno.env.delete("SUPABASE_URL"):Deno.env.set("SUPABASE_URL",oldUrl);
    oldKey===undefined?Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY"):Deno.env.set("SUPABASE_SERVICE_ROLE_KEY",oldKey);
  }
}));
