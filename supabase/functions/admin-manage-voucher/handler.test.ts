import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleAdminManageVoucher, validateVoucherInput } from "./handler.ts";

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
