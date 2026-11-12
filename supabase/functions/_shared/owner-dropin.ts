import { HttpError } from "./auth.ts";

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
