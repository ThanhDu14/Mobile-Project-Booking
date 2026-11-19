import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleAdminManageUser, sanitizeUserRecord } from "./handler.ts";
import { adminFixture } from "../_shared/admin-testing.ts";
import * as fs from "../_shared/firestore.ts";

Deno.test("R120-08: exact email beyond scan window is found without unrelated user pages",()=>adminFixture(async({request})=>{
  const deps={firestore:{...fs,listPage:async()=>({documents:[],nextPageToken:"more"}),query:async()=>[{id:"tail",path:"users/tail",data:{email:"tail@example.test",role:"PLAYER",accountStatus:"ACTIVE"}}]},setUserDisabled:async()=>{}};
  const response=await handleAdminManageUser(request({action:"LIST",query:"tail@example.test"}),deps);
  assertEquals(response.status,200);
  assertEquals((await response.json()).users[0]?.uid,"tail");
}));

Deno.test("R120-08: LIST searches name/email/phone, filters, follows cursors and sanitizes",()=>adminFixture(async({request})=>{
  const first={__documentId:"owner-id",uid:"spoofed",displayName:"Nguyễn Ánh",email:"owner@example.test",phone:"090 123 4567",role:"OWNER",ownerStatus:"APPROVED",accountStatus:"ACTIVE",passwordHash:"never return"};
  const second={__documentId:"locked-id",displayName:"Player",email:"player@example.test",phone:"0911111111",role:"PLAYER",accountStatus:"LOCKED"};
  const pages:string[]=[];
  const deps={firestore:{...fs,query:async(_:string,filters:fs.QueryFilter[])=>[first,second].filter(u=>u.email===filters[0].value).map(u=>({id:u.__documentId,path:`users/${u.__documentId}`,data:u})),listPage:async(_:string,cursor="",size=30)=>{assertEquals(size,30);pages.push(cursor);return cursor==="second"?{documents:[second],nextPageToken:""}:{documents:[first],nextPageToken:"second"};}},setUserDisabled:async()=>{}};
  for(const [query,filter,id] of [["nguyen","OWNERS","owner-id"],["owner@example.test","ALL","owner-id"],["0901234567","ALL","owner-id"],["","LOCKED","locked-id"],["player@example.test","ALL","locked-id"]]) {
    const result=await handleAdminManageUser(request({action:"LIST",query,filter}),deps);
    assertEquals(result.status,200);
    const body=await result.json();assertEquals(body.users[0].uid,id);assertEquals(body.users[0].passwordHash,undefined);
  }
  assertEquals(pages.includes("second"),true);
  const empty=await handleAdminManageUser(request({action:"LIST",query:"missing"}),deps);
  assertEquals((await empty.json()).users,[]);
}));

Deno.test("R120-08: email lookup returns document identity and email", () => adminFixture(async ({request}) => {
  const deps = { firestore: { ...fs, list: async () => [{__documentId:"actual-id",email:"player@example.test",displayName:"Player"}] }, setUserDisabled: async () => {} };
  const result = await handleAdminManageUser(request({action:"LOOKUP",query:"player@example.test"}),deps);
  assertEquals(result.status,200);
  const body=await result.json();
  assertEquals(body.user?.uid,"actual-id");
  assertEquals(body.user?.email,"player@example.test");
}));

Deno.test("admin-manage-user: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-manage-user", { method: "GET" });
  const res = await handleAdminManageUser(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-manage-user: rejects unauthenticated request", async () => {
  const req = new Request("https://localhost/admin-manage-user", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "LOOKUP", query: "0901234567" }),
  });
  const res = await handleAdminManageUser(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-manage-user: sanitizeUserRecord filters sensitive fields", () => {
  const raw = {
    displayName: "Nguyen Van A",
    phone: "0901234567",
    role: "PLAYER",
    ownerStatus: "NONE",
    accountStatus: "ACTIVE",
    favoriteDistrictIds: ["d1", "d2"],
    fcmTokens: ["token1"],
    internalSecret: "do-not-leak",
  };
  const sanitized = sanitizeUserRecord("uid123", raw);
  assertEquals(sanitized?.uid, "uid123");
  assertEquals(sanitized?.fullName, "Nguyen Van A");
  assertEquals(sanitized?.phone, "0901234567");
  assertEquals(sanitized?.role, "PLAYER");
  assertEquals((sanitized as Record<string, unknown>)?.internalSecret, undefined);
  assertEquals((sanitized as Record<string, unknown>)?.fcmTokens, undefined);
});
