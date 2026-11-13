import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleAdminManageVoucher, validateVoucherInput } from "./handler.ts";
import { adminFixture } from "../_shared/admin-testing.ts";

Deno.test("R120-15: edit preserves counters and rejects limits below usage",()=>adminFixture(async({records,request})=>{
  records.set("systemVouchers/EDIT10",{code:"EDIT10",usedCount:3});
  const fs=await import("../_shared/firestore.ts");
  const body={action:"UPDATE",code:"EDIT10",type:"FIXED",value:10,maxDiscount:10,minOrderTotal:0,validFrom:"2026-11-01T00:00:00Z",validTo:"2026-12-01T00:00:00Z",usageLimit:10,perUserLimit:1,usedCount:999};
  const changed=await handleAdminManageVoucher(request(body),{firestore:fs});
  assertEquals(changed.status,200);await changed.body?.cancel();
  assertEquals(records.get("systemVouchers/EDIT10")?.usedCount,3);
  const invalid=await handleAdminManageVoucher(request({...body,usageLimit:2}),{firestore:fs});
  assertEquals(invalid.status,400);await invalid.body?.cancel();
}));

Deno.test("R120-06: canonical PERCENT contract is accepted", () => {
  const result=validateVoucherInput({code:"PERCENT10",type:"PERCENT",value:10,maxDiscount:100,minOrderTotal:0,validFrom:"2026-11-01T00:00:00Z",validTo:"2026-12-01T00:00:00Z",usageLimit:10,perUserLimit:1});
  assertEquals(result.type,"PERCENT");
});

Deno.test("R120-06: rejects invalid dates, absent timezone, non-finite and fractional amounts", () => {
  const valid = { code: "SYS10", type: "FIXED", value: 10, maxDiscount: 10, minOrderTotal: 0, validFrom: "2026-11-01T00:00:00+07:00", validTo: "2026-12-01T00:00:00+07:00", usageLimit: 10, perUserLimit: 1, isActive: true };
  for (const bad of [{ validFrom: "garbage" }, { validTo: "31/12" }, { validFrom: "2026-11-01" }, { validFrom: "2026-02-30T00:00:00Z" }, { value: Infinity }, { maxDiscount: NaN }, { minOrderTotal: "oops" }, { usageLimit: 1.5 }, { isActive: "false" }]) {
    assertThrows(() => validateVoucherInput({ ...valid, ...bad }));
  }
});

Deno.test("admin-manage-voucher: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-manage-voucher", { method: "GET" });
  const res = await handleAdminManageVoucher(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-manage-voucher: rejects unauthenticated request", async () => {
  const req = new Request("https://localhost/admin-manage-voucher", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "CREATE", code: "CHAOBANMOI" }),
  });
  const res = await handleAdminManageVoucher(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-manage-voucher: validateVoucherInput validates valid input and rejects invalid", () => {
  const valid = validateVoucherInput({
    code: "SYSTEM50K",
    type: "FIXED",
    value: 50000,
    maxDiscount: 50000,
    minOrderTotal: 100000,
    validFrom: "2026-11-01T00:00:00Z",
    validTo: "2026-12-01T00:00:00Z",
    usageLimit: 100,
    perUserLimit: 1,
    isActive: true,
  });
  assertEquals(valid.code, "SYSTEM50K");
  assertEquals(valid.type, "FIXED");
  assertEquals(valid.value, 50000);

  // Invalid code
  assertThrows(() => validateVoucherInput({ code: "ab" }));
  // Invalid type
  assertThrows(() => validateVoucherInput({ code: "SYS10", type: "INVALID", value: 10 }));
  // Invalid date range
  assertThrows(() => validateVoucherInput({
    code: "SYS10",
    type: "PERCENTAGE",
    value: 10,
    validFrom: "2026-12-01T00:00:00Z",
    validTo: "2026-11-01T00:00:00Z",
    usageLimit: 10,
    perUserLimit: 1,
  }));
});
