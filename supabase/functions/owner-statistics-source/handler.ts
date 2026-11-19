import { errorResponse, HttpError, verifyIdToken, type VerifiedToken } from "../_shared/auth.ts";
import * as firestore from "../_shared/firestore.ts";
import { requireOwnerVenue } from "../_shared/owner-access.ts";

const TIMING = ["startAt", "endAt", "date", "startTime", "endTime"];
const BOOKING_FIELDS = [...TIMING, "status", "paymentStatus", "paymentMethod", "total", "depositAmount", "refundAmount"];
const SLOT_FIELDS = [...TIMING, "courtId", "status", "blockReason", "bookingId"];
const LIMIT = 5001;
const instant = (data: Record<string, unknown>, field: string): Date | null => {
  const value = data[field];
  const direct = value instanceof Date ? value : typeof value === "string" ? new Date(value) : null;
  if (direct && Number.isFinite(direct.getTime())) return direct;
  const date = String(data.date ?? ""), time = String(data[field === "startAt" ? "startTime" : "endTime"] ?? "");
  if (!/^\d{8}$/.test(date) || !/^\d{4}$/.test(time)) return null;
  const parsed = new Date(`${date.slice(0,4)}-${date.slice(4,6)}-${date.slice(6,8)}T${time.slice(0,2)}:${time.slice(2)}:00+07:00`);
  return Number.isFinite(parsed.getTime()) ? parsed : null;
};
const pick = (data: Record<string, unknown>, fields: string[]) => Object.fromEntries(fields.filter(key => key in data).map(key => [key, data[key]]));

export interface OwnerStatisticsDeps {
  verifyToken?: (request: Request) => Promise<VerifiedToken>;
  runTransaction?: typeof firestore.runTransaction;
}

/** Bounded, venue-scoped snapshot. QR, player identity and other private fields never leave the server. */
export async function handleOwnerStatisticsSource(req: Request, deps: OwnerStatisticsDeps = {}): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const token = await (deps.verifyToken ?? verifyIdToken)(req);
    const body = await req.json();
    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    if (!venueId || venueId.includes("/")) throw new HttpError(400, "INVALID_VENUE", "Cơ sở không hợp lệ");
    const date = (value: unknown) => {
      if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new HttpError(400,"INVALID_DATE","Ngày không hợp lệ");
      const parsed = new Date(value+"T00:00:00+07:00");
      if (!Number.isFinite(parsed.getTime()) || new Date(parsed.getTime()+7*3600000).toISOString().slice(0,10)!==value) throw new HttpError(400,"INVALID_DATE","Ngày không hợp lệ");
      return parsed;
    };
    const start = date(body.startDate), end = new Date(date(body.endDate).getTime()+86400000);
    const days = (end.getTime()-start.getTime())/86400000;
    if (days < 1 || days > 30) throw new HttpError(400,"INVALID_RANGE","Chọn khoảng từ 1 đến 30 ngày");
    const dates = Array.from({length:days},(_,i)=>new Date(start.getTime()+i*86400000+7*3600000).toISOString().slice(0,10).replaceAll("-",""));
    const result = await (deps.runTransaction ?? firestore.runTransaction)(async tx => {
      await requireOwnerVenue(tx, token.uid, venueId);
      const venueFilter: firestore.QueryFilter = {field:"venueId",op:"EQUAL",value:venueId};
      const lower = new Date(start.getTime()-86400000);
      const groups: firestore.QueryDocument[][] = [];
      // Timestamp and legacy ISO-string values use separate type-aware range queries.
      for (const [from,to] of [[lower,end],[lower.toISOString(),new Date(end.getTime()+86400000).toISOString()]] as const) {
        groups.push(await tx.query("bookings",[venueFilter,{field:"startAt",op:"GREATER_THAN_OR_EQUAL",value:from},{field:"startAt",op:"LESS_THAN",value:to}],{limit:LIMIT,select:BOOKING_FIELDS}));
      }
      groups.push(await tx.query("bookings",[venueFilter,{field:"date",op:"IN",value:dates}],{limit:LIMIT,select:BOOKING_FIELDS}));
      const slots = await tx.query("slots",[venueFilter,{field:"date",op:"IN",value:dates}],{limit:LIMIT,select:SLOT_FIELDS});
      if ([...groups,slots].some(rows=>rows.length===LIMIT)) throw new HttpError(409,"REPORT_LIMIT","Quá nhiều dữ liệu trong kỳ. Chọn khoảng ngắn hơn.");
      const bookings = [...new Map(groups.flat().map(row=>[row.id,row])).values()].flatMap(row=>{
        const from=instant(row.data,"startAt"), to=instant(row.data,"endAt");
        if (!from || !to || to<=from) throw new HttpError(409,"INVALID_REPORT_DATA","Dữ liệu thời gian booking chưa đầy đủ");
        if (from>=end || to<=start) return [];
        return [{...pick(row.data,BOOKING_FIELDS),bookingId:row.id,startAt:from.toISOString(),endAt:to.toISOString()}];
      });
      return {bookings,slots:slots.map(row=>{
        const from=instant(row.data,"startAt"),to=instant(row.data,"endAt");
        if (!from || !to || to<=from) throw new HttpError(409,"INVALID_REPORT_DATA","Dữ liệu thời gian slot chưa đầy đủ");
        return {...pick(row.data,SLOT_FIELDS),slotId:row.id,startAt:from.toISOString(),endAt:to.toISOString()};
      }),computedAt:new Date().toISOString()};
    });
    return Response.json(result);
  } catch(error) {
    if (error instanceof HttpError) return errorResponse(error.status,error.code,error.message);
    return errorResponse(503,"STATS_UNAVAILABLE","Chưa tải được thống kê. Vui lòng thử lại.");
  }
}
