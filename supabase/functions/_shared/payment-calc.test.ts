import { assertEquals } from "jsr:@std/assert@1.0.19";
import { amountDue, type PaymentOption, paymentBreakdown, type VoucherTerms } from "./payment-calc.ts";

/**
 * Bản TS của công thức tiền spec 050 (research R7) chạy trên cùng bảng ca test với
 * `PaymentCalculatorTest` (Kotlin): specs/050-payment-voucher/contracts/payment-calc-cases.json.
 */
interface Case {
  id: string;
  subtotal: number;
  voucher: VoucherTerms | null;
  expected: {
    discount: number;
    total: number;
    depositAmount: number;
    remainingAfterDeposit: number;
    isFree: boolean;
    amountDue: Record<PaymentOption, number>;
  };
}

const CASES_URL = new URL("../../../specs/050-payment-voucher/contracts/payment-calc-cases.json", import.meta.url);
const cases: Case[] = JSON.parse(await Deno.readTextFile(CASES_URL)).cases;

Deno.test("payment-calc: bảng ca test có đủ 6 dòng của quickstart", () => {
  const ids = new Set(cases.map((c) => c.id));
  for (const id of ["Q1", "Q2", "Q3", "Q4", "Q5", "Q6"]) assertEquals(ids.has(id), true, id);
});

for (const c of cases) {
  Deno.test(`payment-calc: ${c.id} giảm giá, tổng, cọc, còn lại, số phải trả`, () => {
    const b = paymentBreakdown(c.subtotal, c.voucher);
    assertEquals(
      {
        discount: b.discount,
        total: b.total,
        depositAmount: b.depositAmount,
        remainingAfterDeposit: b.remainingAfterDeposit,
        isFree: b.isFree,
      },
      {
        discount: c.expected.discount,
        total: c.expected.total,
        depositAmount: c.expected.depositAmount,
        remainingAfterDeposit: c.expected.remainingAfterDeposit,
        isFree: c.expected.isFree,
      },
    );
    assertEquals(b.subtotal, c.subtotal);
    const options: PaymentOption[] = ["AT_VENUE", "TRANSFER_DEPOSIT", "TRANSFER_FULL"];
    assertEquals(Object.keys(c.expected.amountDue).sort(), [...options].sort());
    for (const o of options) assertEquals(amountDue(b, o), c.expected.amountDue[o], `${c.id} ${o}`);
  });
}
