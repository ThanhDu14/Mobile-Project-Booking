// T054 (spec 050 US5): hai tài khoản cùng giữ lượt cuối của voucher `usageLimit = 1`.
// Firestore giả ở chế độ optimistic: transaction chạy xen nhau, document đã đọc bị đổi thì commit báo ABORTED và
// `runWithBackoff` thử lại, như Firestore thật.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { authedRequest, fakeAuthenticate } from "../_shared/payment-test-auth.ts";
import { handleBookingPaymentTransfer, type TransferDeps } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const NOW = new Date("2026-11-20T03:00:00Z");
const HOLD_UNTIL = new Date(NOW.getTime() + 4 * 60_000);
const PAYOUT = {
  bankBin: "970422",
  bankShortName: "MBBank",
  accountNumber: "0123456789",
  accountHolderName: "NGUYEN VAN A",
};
const A = { uid: "playerA", rid: "11111111-1111-4111-8111-111111111111", slot: "c1_20261120_1800" };
const B = { uid: "playerB", rid: "22222222-2222-4222-8222-222222222222", slot: "c1_20261120_1900" };

function setup(): { fs: FakeFirestore; deps: TransferDeps } {
  const fs = new FakeFirestore(() => NOW, { optimistic: true });
  fs.seed("venues/v1", { ownerId: "owner1", name: "Sân A", status: "ACTIVE", payoutAccount: PAYOUT });
  fs.seed("venues/v1/courts/c1", { name: "Sân 1" });
  fs.seed("venues/v1/priceRules/r1", { daysOfWeek: [], startTime: "0600", endTime: "2200", pricePerHour: 120_000 });
  for (const p of [A, B]) {
    fs.seed(
      `slots/${p.slot}`,
      { venueId: "v1", status: "HELD", holdBy: p.uid, bookingId: p.rid, holdExpiresAt: HOLD_UNTIL } as Doc,
    );
  }
  fs.seed("systemVouchers/LAST1", {
    type: "FIXED",
    value: 10_000,
    minOrderTotal: 0,
    usageLimit: 1,
    usedCount: 0,
    perUserLimit: 1,
    isActive: true,
    validFrom: "2026-11-01T00:00:00+07:00",
    validTo: "2026-11-30T23:59:59+07:00",
  });
  let n = 0;
  const codes = ["SNPAAAA2222", "SNPBBBB3333", "SNPCCCC4444", "SNPDDDD5555", "SNPEEEE6666", "SNPFFFF7777"];
  return {
    fs,
    deps: {
      firestore: fs,
      authenticate: fakeAuthenticate,
      now: () => NOW,
      sleep: () => Promise.resolve(),
      newCode: () => codes[n++ % codes.length],
    },
  };
}

const body = (p: typeof A) => ({
  requestId: p.rid,
  recurringGroupId: null,
  venueId: "v1",
  slotIds: [p.slot],
  voucherCode: "LAST1",
  option: "TRANSFER_FULL",
  expectedTotal: 50_000,
});

Deno.test("hai người cùng giữ lượt cuối của voucher: đúng một 200, một 422 OUT_OF_USES", async () => {
  const { fs, deps } = setup();
  const [ra, rb] = await Promise.all([
    handleBookingPaymentTransfer(authedRequest(A.uid, body(A)), deps),
    handleBookingPaymentTransfer(authedRequest(B.uid, body(B)), deps),
  ]);
  const results = [{ status: ra.status, json: await ra.json() }, { status: rb.status, json: await rb.json() }];
  const ok = results.filter((r) => r.status === 200);
  const rejected = results.filter((r) => r.status === 422);
  assertEquals(ok.length, 1);
  assertEquals(rejected.length, 1);
  assertEquals(rejected[0].json.error.code, "VOUCHER_INVALID");
  assertEquals(rejected[0].json.error.reason, "OUT_OF_USES");
  assertEquals(fs.read("systemVouchers/LAST1")!.usedCount, 1);
  assertEquals(fs.children("systemVouchers/LAST1/redemptions").length, 1);
  // Xung đột thật sự đã xảy ra và được thử lại, không phải hai transaction chạy lần lượt.
  assertEquals(fs.aborts > 0, true);
});
