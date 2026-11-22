import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as fs from "../_shared/firestore.ts";
import { ownerTransaction } from "../_shared/owner-transaction.ts";

export interface SyncVenuePriceDeps {
  firestore: Pick<typeof fs, "runTransaction">;
}
export function validatePriceRule(value: Record<string, unknown>) {
  const days = value.daysOfWeek;
  const time = (v: unknown): v is string =>
    typeof v === "string" && /^(?:[01]\d|2[0-3])(?:00|30)$/.test(v);
  if (
    !Array.isArray(days) || !days.length ||
    days.some((d) => !Number.isInteger(d) || d < 1 || d > 7) ||
    new Set(days).size !== days.length ||
    !time(value.startTime) || !time(value.endTime) ||
    value.startTime >= value.endTime ||
    !Number.isSafeInteger(value.pricePerHour) ||
    Number(value.pricePerHour) <= 0 ||
    Number(value.pricePerHour) % 2000 !== 0 ||
    !["NORMAL", "PEAK", "WEEKEND"].includes(String(value.label))
  ) {
    throw new HttpError(
      400,
      "INVALID_PRICE_RULE",
      "Ngày, khung giờ, giá hoặc nhãn bảng giá không hợp lệ",
    );
  }
  return {
    daysOfWeek: [...days].sort((a, b) => a - b) as number[],
    startTime: value.startTime,
    endTime: value.endTime,
    pricePerHour: Number(value.pricePerHour),
    label: String(value.label),
  };
}

/** Compatibility SYNC and rule CRUD use one transaction, including current authority reads. */
export async function handlePriceMutation(
  req: Request,
  { firestore }: SyncVenuePriceDeps = { firestore: fs },
): Promise<Response> {
  return await ownerTransaction(req, firestore, async (request, source) => {
    try {
      const token = await verifyIdToken(request), body = await request.json();
      const venueId = typeof body.venueId === "string"
          ? body.venueId.trim()
          : "",
        action = body.action ?? "SYNC";
      if (!venueId || venueId.includes("/")) {
        throw new HttpError(400, "INVALID_VENUE_ID", "Cơ sở không hợp lệ");
      }
      if (!["SYNC", "CREATE", "UPDATE", "DELETE"].includes(action)) {
        throw new HttpError(400, "INVALID_ACTION", "Thao tác không hợp lệ");
      }
      const user = await source.get(`users/${token.uid}`),
        admin = token.appRole === "ADMIN" && user?.role === "ADMIN";
      if (
        !user || user.accountStatus !== "ACTIVE" ||
        user.deletionRequestedAt != null ||
        (!admin &&
          (token.appRole !== "OWNER" || user.role !== "OWNER" ||
            user.ownerStatus !== "APPROVED"))
      ) {
        throw new HttpError(
          403,
          "FORBIDDEN",
          "Tài khoản không có quyền quản lý cơ sở",
        );
      }
      const venue = await source.get(`venues/${venueId}`);
      if (!venue || (!admin && venue.ownerId !== token.uid)) {
        throw new HttpError(
          404,
          "NOT_FOUND",
          "Không tìm thấy cơ sở hoặc bạn không có quyền",
        );
      }
      if (!admin && venue.status === "LOCKED") {
        throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị khóa");
      }
      const rule = action === "CREATE" || action === "UPDATE"
        ? validatePriceRule(body.rule ?? {})
        : null;
      // Identical create retries resolve to one document, without new schema fields.
      const digest = rule
        ? Array.from(
          new Uint8Array(
            await crypto.subtle.digest(
              "SHA-256",
              new TextEncoder().encode(JSON.stringify(rule)),
            ),
          ),
        ).map((b) => b.toString(16).padStart(2, "0")).join("")
        : "";
      const ruleId = action === "CREATE" ? `price_${digest}` : body.ruleId;
      if (
        action !== "SYNC" &&
        (typeof ruleId !== "string" || !ruleId || ruleId.includes("/"))
      ) throw new HttpError(400, "INVALID_RULE_ID", "Khung giá không hợp lệ");
      const rows = await source.query("priceRules", [], {
        parent: `venues/${venueId}`,
      });
      const existing = rows.find((row) => row.id === ruleId);
      if (
        action === "CREATE" && existing &&
        JSON.stringify(validatePriceRule(existing.data)) !==
          JSON.stringify(rule)
      ) {
        throw new HttpError(
          409,
          "STALE_CREATE",
          "Khung giá đã được sửa. Hãy tải lại trước khi thử lại.",
        );
      }
      if (action === "UPDATE" && !existing) {
        throw new HttpError(404, "NOT_FOUND", "Khung giá không còn tồn tại");
      }
      if (
        rule &&
        rows.some((row) =>
          row.id !== ruleId && Array.isArray(row.data.daysOfWeek) &&
          rule.daysOfWeek.some((day) =>
            (row.data.daysOfWeek as unknown[]).includes(day)
          ) && rule.startTime < String(row.data.endTime) &&
          String(row.data.startTime) < rule.endTime
        )
      ) {
        throw new HttpError(
          409,
          "PRICE_OVERLAP",
          "Khoảng giờ bị chồng với bảng giá hiện có. Hãy tải lại.",
        );
      }
      const remaining = (action === "SYNC" ? rows : rows.filter((row) =>
        row.id !== ruleId
      )).map((row) => row.data);
      if (rule) remaining.push(rule);
      const prices = remaining.map((row) => row.pricePerHour).filter((
        p,
      ): p is number => typeof p === "number" && p > 0);
      const minPricePerHour = prices.length ? Math.min(...prices) : null,
        writes: fs.Write[] = [];
      const path = `venues/${venueId}/priceRules/${ruleId}`;
      if (rule) {
        writes.push(
          existing ? fs.updateWrite(path, rule) : fs.createWrite(path, rule),
        );
      }
      if (action === "DELETE" && existing) writes.push(fs.deleteWrite(path));
      // Writing the venue serializes concurrent rule mutations and prevents phantom overlaps.
      writes.push(
        fs.updateWrite(`venues/${venueId}`, { minPricePerHour }, ["updatedAt"]),
      );
      await source.commit(writes);
      return Response.json({
        venueId,
        ruleId: action === "SYNC" ? null : ruleId,
        minPricePerHour,
      });
    } catch (error) {
      if (error instanceof fs.FirestoreError && error.code === "ABORTED") {
        throw error;
      }
      if (error instanceof HttpError) {
        return errorResponse(error.status, error.code, error.message);
      }
      return errorResponse(
        500,
        "PRICE_UNAVAILABLE",
        "Chưa xác nhận được kết quả. Hãy tải lại hoặc thử lại cùng khung giá.",
      );
    }
  });
}
