import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleOwnerCreateVoucher, validateOwnerVoucher } from "./handler.ts";
import { adminFixture } from "../_shared/admin-testing.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import * as fs from "../_shared/firestore.ts";

const draft = { venueId:"v1",code:"AUDIT10",type:"PERCENT",value:10,maxDiscount:40000,minOrderTotal:100000,
  validFrom:"2026-11-01T00:00:00+07:00",validTo:"2026-12-01T00:00:00+07:00",usageLimit:10,perUserLimit:1,isActive:true };

Deno.test("voucher validation rejects NaN, decimals, invalid dates and limits", () => {
  for (const patch of [{value:NaN},{value:1.5},{value:101},{maxDiscount:Infinity},{validFrom:"invalid"},
    {perUserLimit:11},{usageLimit:null},{isActive:"false"}]) assertThrows(() => validateOwnerVoucher({...draft,...patch}));
});

Deno.test("voucher mutations check current ownership and preserve usage counters", () => adminFixture(async ({records}) => {
  records.set("users/owner",{role:"OWNER",ownerStatus:"APPROVED",accountStatus:"ACTIVE"});
  records.set("venues/v1",{ownerId:"owner",status:"ACTIVE"});
  records.set("venues/v1/vouchers/AUDIT10",{...draft,usedCount:3});
  const request = (body:unknown) => bearer(emulatorToken({sub:"owner",appRole:"OWNER"}),{body:JSON.stringify(body)});
  let response = await handleOwnerCreateVoucher(request({...draft,action:"UPDATE",usedCount:0}),{firestore:fs});
  assertEquals(response.status,200); await response.body?.cancel();
  assertEquals(records.get("venues/v1/vouchers/AUDIT10")?.usedCount,3);
  response = await handleOwnerCreateVoucher(request({venueId:"v1",code:"AUDIT10",action:"SET_ACTIVE",isActive:false,usedCount:0}),{firestore:fs});
  assertEquals(response.status,200); await response.body?.cancel();
  assertEquals(records.get("venues/v1/vouchers/AUDIT10")?.isActive,false);
  assertEquals(records.get("venues/v1/vouchers/AUDIT10")?.usedCount,3);
  response = await handleOwnerCreateVoucher(request({...draft,action:"UPDATE",usageLimit:2}),{firestore:fs});
  assertEquals(response.status,409); await response.body?.cancel();
  records.set("venues/v1",{ownerId:"another-owner",status:"ACTIVE"});
  response = await handleOwnerCreateVoucher(request({...draft,action:"UPDATE"}),{firestore:fs});
  assertEquals(response.status,403); await response.body?.cancel();
}));

Deno.test("stale owner claim and locked/deleting accounts cannot mutate vouchers", () => adminFixture(async ({records}) => {
  records.set("venues/v1",{ownerId:"owner",status:"ACTIVE"});
  for (const patch of [{ownerStatus:"NONE"},{role:"PLAYER"},{accountStatus:"LOCKED"},{deletionRequestedAt:new Date()}]) {
    records.set("users/owner",{role:"OWNER",ownerStatus:"APPROVED",accountStatus:"ACTIVE",...patch});
    const request=bearer(emulatorToken({sub:"owner",appRole:"OWNER"}),{body:JSON.stringify({...draft,action:"SET_ACTIVE"})});
    const response=await handleOwnerCreateVoucher(request,{firestore:fs});
    assertEquals(response.status,403); await response.body?.cancel();
  }
}));

Deno.test("Firestore GeoPoint encoding preserves native geographic type", () => {
  const point = new fs.FirestoreGeoPoint(10.77,106.7);
  assertEquals(fs.encodeValue(point),{geoPointValue:{latitude:10.77,longitude:106.7}});
  assertEquals(fs.decodeValue(fs.encodeValue(point)),point);
  assertEquals(fs.encodeValue({latitude:10.77,longitude:106.7}),{mapValue:{fields:{latitude:{doubleValue:10.77},longitude:{doubleValue:106.7}}}});
});

Deno.test("owner-create-voucher: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-create-voucher", { method: "GET" });
  const res = await handleOwnerCreateVoucher(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-create-voucher: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-create-voucher", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      code: "SUMMER20",
      type: "PERCENTAGE",
      value: 20,
    }),
  });
  const res = await handleOwnerCreateVoucher(req);
  assertEquals(res.status, 401);
});
