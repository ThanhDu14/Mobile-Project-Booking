import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { decodeFields } from "./firestore.ts";
import { refundCreateWrite, refundIdFor } from "./refunds.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

const pathOf = (name: string) => name.split("/documents/")[1];

Deno.test("refunds: ID theo đơn là refund_{bookingId}, theo giao dịch SePay là refund_sepay_{id}", () => {
  assertEquals(refundIdFor({ bookingId: "b1" }), "refund_b1");
  assertEquals(refundIdFor({ sepayTransactionId: 92704 }), "refund_sepay_92704");
  // Có cả hai thì theo giao dịch SePay: một đơn có thể có nhiều khoản tiền về muộn/sai số.
  assertEquals(refundIdFor({ bookingId: "b1", sepayTransactionId: 92704 }), "refund_sepay_92704");
  assertThrows(() => refundIdFor({}));
});

Deno.test("refunds: refundCreateWrite tạo refund PENDING với đủ trường và createdAt giờ server", () => {
  const w = refundCreateWrite({
    id: "refund_b1",
    bookingId: "b1",
    venueId: "v1",
    userId: "u1",
    amount: 53000,
    penaltyAmount: 0,
    reason: "REJECTED",
  });
  assertEquals(pathOf(w.update!.name), "refunds/refund_b1");
  assertEquals(w.currentDocument, { exists: false });
  assertEquals(decodeFields(w.update!.fields), {
    bookingId: "b1",
    recurringGroupId: null,
    venueId: "v1",
    userId: "u1",
    amount: 53000,
    penaltyAmount: 0,
    reason: "REJECTED",
    status: "PENDING",
    sepayTransactionId: null,
    doneAt: null,
    remindedAt: null,
    reportedAt: null,
  });
  assertEquals(w.updateTransforms, [{ fieldPath: "createdAt", setToServerValue: "REQUEST_TIME" }]);
});

Deno.test("refunds: khoản tiền về muộn của nhóm định kỳ giữ recurringGroupId và sepayTransactionId", () => {
  const w = refundCreateWrite({
    id: "refund_sepay_7",
    recurringGroupId: "g1",
    venueId: "v1",
    userId: "u1",
    amount: 60000,
    penaltyAmount: 0,
    reason: "LATE_TRANSFER",
    sepayTransactionId: 7,
  });
  const f = decodeFields(w.update!.fields);
  assertEquals(f.bookingId, null);
  assertEquals(f.recurringGroupId, "g1");
  assertEquals(f.sepayTransactionId, 7);
  assertEquals(f.reason, "LATE_TRANSFER");
});

Deno.test("refunds: từ chối dữ liệu sai (lý do lạ, tiền âm/không nguyên, thiếu venue/user, không có đơn lẫn nhóm)", () => {
  const base = { id: "refund_b1", bookingId: "b1", venueId: "v1", userId: "u1", amount: 1000, penaltyAmount: 0, reason: "CANCELLED" as const };
  // deno-lint-ignore no-explicit-any
  assertThrows(() => refundCreateWrite({ ...base, reason: "WALLET" as any }));
  assertThrows(() => refundCreateWrite({ ...base, amount: -1 }));
  assertThrows(() => refundCreateWrite({ ...base, amount: 10.5 }));
  assertThrows(() => refundCreateWrite({ ...base, penaltyAmount: -1 }));
  assertThrows(() => refundCreateWrite({ ...base, venueId: "" }));
  assertThrows(() => refundCreateWrite({ ...base, userId: "" }));
  assertThrows(() => refundCreateWrite({ ...base, bookingId: undefined }));
});
