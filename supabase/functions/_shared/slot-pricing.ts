// Giá và giờ của slot 30 phút, cho các Edge Function chốt đơn (spec 050). Cùng luật với app
// (`GenerateDailySlotsUseCase` / `IndexedPriceRules`): `daysOfWeek` rỗng = mọi ngày (ISO 1 = thứ Hai … 7 = Chủ nhật),
// slot phải nằm trọn trong [startTime, endTime] dạng "HHmm", nhiều rule khớp thì rule đầu tiên thắng,
// giá slot = pricePerHour / 2. Slot không lưu giá nên server tính lại từ `venues/{venueId}/priceRules`.

export const SLOT_MINUTES = 30;
/** Việt Nam không có giờ mùa hè: UTC+7 cố định. */
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

export interface PriceRule {
  daysOfWeek: number[];
  startTime: string;
  endTime: string;
  pricePerHour: number;
}

export interface ParsedSlot {
  slotId: string;
  courtId: string;
  /** yyyyMMdd */
  date: string;
  /** HHmm */
  startTime: string;
  startMinutes: number;
  startAt: Date;
  endAt: Date;
}

/** `{courtId}_{yyyyMMdd}_{HHmm}`; sai dạng thì `null`. */
export function parseSlotId(slotId: string): ParsedSlot | null {
  const m = /^(.+)_(\d{8})_(\d{3,4})$/.exec(slotId);
  if (!m) return null;
  const [, courtId, date, rawTime] = m;
  const startTime = rawTime.padStart(4, "0");
  const hour = Number(startTime.slice(0, 2)),
    minute = Number(startTime.slice(2));
  const y = Number(date.slice(0, 4)),
    mo = Number(date.slice(4, 6)),
    d = Number(date.slice(6));
  if (hour > 23 || minute > 59 || mo < 1 || mo > 12 || d < 1 || d > 31) {
    return null;
  }
  const startMs = Date.UTC(y, mo - 1, d, hour, minute) - VN_OFFSET_MS;
  return {
    slotId,
    courtId,
    date,
    startTime,
    startMinutes: hour * 60 + minute,
    startAt: new Date(startMs),
    endAt: new Date(startMs + SLOT_MINUTES * 60_000),
  };
}

/** Thứ trong tuần theo ISO (1 = thứ Hai) của ngày `yyyyMMdd`. */
export function isoDayOfWeek(date: string): number {
  const day = new Date(
    Date.UTC(
      Number(date.slice(0, 4)),
      Number(date.slice(4, 6)) - 1,
      Number(date.slice(6)),
    ),
  ).getUTCDay();
  return day === 0 ? 7 : day;
}

function toMinutes(hhmm: string): number {
  const clean = hhmm.replace(":", "").padStart(4, "0");
  return Number(clean.slice(0, 2)) * 60 + Number(clean.slice(2, 4));
}

/** Đọc rule từ document `priceRules` (thiếu trường thì bỏ). */
export function toPriceRule(doc: Record<string, unknown>): PriceRule | null {
  const price = Number(doc.pricePerHour);
  if (
    typeof doc.startTime !== "string" || typeof doc.endTime !== "string" ||
    !Number.isFinite(price)
  ) return null;
  const days = Array.isArray(doc.daysOfWeek) ? doc.daysOfWeek.map(Number).filter(Number.isFinite) : [];
  return {
    daysOfWeek: days,
    startTime: doc.startTime,
    endTime: doc.endTime,
    pricePerHour: price,
  };
}

/** Giá của một slot; `null` khi không có rule nào khớp (slot đó đáng ra bị khóa ở lưới). */
export function slotPrice(rules: PriceRule[], slot: ParsedSlot): number | null {
  const dow = isoDayOfWeek(slot.date);
  const start = slot.startMinutes, end = start + SLOT_MINUTES;
  const rule = rules.find((r) =>
    (r.daysOfWeek.length === 0 || r.daysOfWeek.includes(dow)) &&
    start >= toMinutes(r.startTime) &&
    end <= toMinutes(r.endTime)
  );
  return rule ? Math.floor(rule.pricePerHour / 2) : null;
}
