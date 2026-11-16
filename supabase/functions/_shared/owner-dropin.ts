import { HttpError } from "./auth.ts";

export function validateOwnerSessionSchedule(input: ReturnType<typeof parseOwnerSessionInput>, venue: Record<string,unknown>, rules: Record<string,unknown>[]) {
  const open=String(venue.openTime ?? ""),close=String(venue.closeTime ?? "");
  if(!/^\d{4}$/.test(open) || !/^\d{4}$/.test(close) || input.startTime < open || input.endTime > close) {
    throw new HttpError(400,"OUTSIDE_OPENING_HOURS","Buổi chơi phải nằm trong giờ hoạt động của cơ sở");
  }
  if(input.startAt.getTime() <= Date.now()) throw new HttpError(400,"SESSION_IN_PAST","Chọn thời gian bắt đầu trong tương lai");
  const day=new Date(input.startAt.getTime()+7*3600_000).getUTCDay() || 7;
  const start=Number(input.startTime.slice(0,2))*60+Number(input.startTime.slice(2));
  const end=Number(input.endTime.slice(0,2))*60+Number(input.endTime.slice(2));
  for(let minute=start;minute<end;minute+=30) {
    const from=String(Math.floor(minute/60)).padStart(2,"0")+String(minute%60).padStart(2,"0");
    const next=minute+30;
    const to=String(Math.floor(next/60)).padStart(2,"0")+String(next%60).padStart(2,"0");
    if(!rules.some(rule=>Array.isArray(rule.daysOfWeek) && rule.daysOfWeek.includes(day) &&
      String(rule.startTime)<=from && String(rule.endTime)>=to && Number(rule.pricePerHour)>0)) {
      throw new HttpError(400,"PRICE_REQUIRED","Cần bảng giá cho toàn bộ khoảng thời gian mở buổi");
    }
  }
}

/** Owner wire format: local Vietnam date yyyyMMdd and time HHmm. */
export function parseOwnerSessionInput(body: Record<string, unknown>) {
  const courtIds = Array.isArray(body.courtIds)
    ? body.courtIds
    : [body.courtId];
  if (
    !courtIds.length ||
    courtIds.some((id) =>
      typeof id !== "string" || !id.trim() || id.includes("/")
    )
  ) {
    throw new HttpError(400, "INVALID_COURT_ID", "Chọn ít nhất một sân");
  }
  const courts = [...new Set((courtIds as string[]).map((id) => id.trim()))];
  const date = typeof body.date === "string" ? body.date.trim() : "";
  const startTime = typeof body.startTime === "string"
    ? body.startTime.trim()
    : "";
  const endTime = typeof body.endTime === "string" ? body.endTime.trim() : "";
  if (!/^\d{8}$/.test(date)) {
    throw new HttpError(400, "INVALID_DATE", "Ngày phải có định dạng yyyyMMdd");
  }
  if (
    ![startTime, endTime].every((time) =>
      /^(?:[01]\d|2[0-3])(?:00|30)$/.test(time)
    )
  ) {
    throw new HttpError(
      400,
      "INVALID_TIME",
      "Thời gian phải theo khung 30 phút",
    );
  }
  const day = `${date.slice(0, 4)}-${date.slice(4, 6)}-${date.slice(6, 8)}`;
  const timestamp = (time: string) =>
    new Date(`${day}T${time.slice(0, 2)}:${time.slice(2)}:00+07:00`);
  const startAt = timestamp(startTime);
  const endAt = timestamp(endTime);
  if (
    !Number.isFinite(startAt.getTime()) ||
    new Date(startAt.getTime() + 7 * 3600_000).toISOString().slice(0, 10) !==
      day
  ) {
    throw new HttpError(400, "INVALID_DATE", "Ngày không hợp lệ");
  }
  if (endAt <= startAt) {
    throw new HttpError(
      400,
      "INVALID_TIME_RANGE",
      "Giờ kết thúc phải sau giờ bắt đầu",
    );
  }
  const capacity = Number(body.capacity);
  const pricePerPerson = Number(body.pricePerPerson);
  const level = typeof body.level === "string" ? body.level.trim() : "";
  if (!Number.isSafeInteger(capacity) || capacity <= 0) {
    throw new HttpError(
      400,
      "INVALID_CAPACITY",
      "Sức chứa phải là số nguyên dương",
    );
  }
  if (!Number.isSafeInteger(pricePerPerson) || pricePerPerson < 0) {
    throw new HttpError(400, "INVALID_PRICE", "Giá vé không được âm");
  }
  if (!["BEGINNER", "INTERMEDIATE", "ADVANCED"].includes(level)) {
    throw new HttpError(400, "INVALID_LEVEL", "Trình độ không hợp lệ");
  }
  const slotIds: string[] = [];
  for (const courtId of courts) {
    for (let at = startAt.getTime(); at < endAt.getTime(); at += 30 * 60_000) {
      const local = new Date(at + 7 * 3600_000);
      slotIds.push(
        `${courtId}_${date}_${String(local.getUTCHours()).padStart(2, "0")}${
          String(local.getUTCMinutes()).padStart(2, "0")
        }`,
      );
    }
  }
  return {
    courtIds: courts,
    date,
    startTime,
    endTime,
    startAt,
    endAt,
    capacity,
    pricePerPerson,
    level,
    note: typeof body.note === "string" ? body.note.trim() : "",
    slotIds,
  };
}
