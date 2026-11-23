// Giá slot ở server phải khớp app (`GenerateDailySlotsUseCase`).
//   cd supabase/functions && deno test _shared/slot-pricing.test.ts
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { isoDayOfWeek, parseSlotId, type PriceRule, slotPrice } from "./slot-pricing.ts";

const rules: PriceRule[] = [
  {
    daysOfWeek: [6, 7],
    startTime: "0600",
    endTime: "2200",
    pricePerHour: 150_000,
  },
  { daysOfWeek: [], startTime: "0600", endTime: "1700", pricePerHour: 100_000 },
  {
    daysOfWeek: [],
    startTime: "17:00",
    endTime: "22:00",
    pricePerHour: 120_000,
  },
];

Deno.test("parseSlotId: giờ Việt Nam UTC+7, slot 30 phút", () => {
  const s = parseSlotId("c1_20261120_1800")!;
  assertEquals([s.courtId, s.date, s.startTime], ["c1", "20261120", "1800"]);
  assertEquals(s.startAt.toISOString(), "2026-11-20T11:00:00.000Z");
  assertEquals(s.endAt.toISOString(), "2026-11-20T11:30:00.000Z");
  assertEquals(parseSlotId("court_a_20261120_930")!.startTime, "0930");
  assertEquals(parseSlotId("c1_20261120_2460"), null);
  assertEquals(parseSlotId("sai"), null);
});

Deno.test("isoDayOfWeek: 20/11/2026 là thứ Sáu, 22/11/2026 là Chủ nhật", () => {
  assertEquals(isoDayOfWeek("20261120"), 5);
  assertEquals(isoDayOfWeek("20261122"), 7);
});

Deno.test("slotPrice: rule đầu tiên khớp thắng, slot phải nằm trọn, ngoài bảng giá thì null", () => {
  assertEquals(slotPrice(rules, parseSlotId("c1_20261120_1630")!), 50_000);
  assertEquals(slotPrice(rules, parseSlotId("c1_20261120_1700")!), 60_000);
  assertEquals(slotPrice(rules, parseSlotId("c1_20261122_1700")!), 75_000);
  assertEquals(slotPrice(rules, parseSlotId("c1_20261120_2200")!), null);
  assertEquals(slotPrice(rules, parseSlotId("c1_20261120_0530")!), null);
});
