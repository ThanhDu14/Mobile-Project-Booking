// Logic dùng chung cho buổi chơi vãng lai (C, spec 080; contracts/edge-functions-dropin.md §3).
// Người dùng: register-drop-in, cancel-drop-in (080), hàng chờ (081), tạo/sửa/hủy buổi và check-in (D, 112),
// delete-account (A, 011).
//
// Các hàm `...InTx` chạy BÊN TRONG transaction của người gọi: chỉ đọc bằng `tx.get` và gom ghi bằng `tx.write`,
// không tự commit. Mọi thao tác đọc nằm trước mọi thao tác ghi (transaction của `_shared/firestore.ts` không cho
// đọc sau khi đã ghi).
import { updateWrite, type Write } from "./firestore.ts";

// ---------- Kiểu dữ liệu (data-model.md mục 1, 2) ----------

export type TimeSlot = "MORNING" | "NOON" | "AFTERNOON" | "EVENING";
export type SessionStatus = "OPEN" | "FULL" | "CANCELLED" | "COMPLETED";
export type RegistrationStatus = "REGISTERED" | "WAITLISTED" | "CANCELLED" | "CHECKED_IN";
export type PaymentMethod = "MOCK_PAY" | "AT_VENUE";
export type PaymentStatus = "PAID" | "UNPAID" | "REFUNDED";
export type CancelledBy = "USER" | "OWNER" | "SYSTEM";
export type RefundReason = "EARLY" | "LATE" | "SESSION_CHANGED" | "AT_VENUE";

/**
 * Document đọc từ Firestore là `Record<string, unknown>`; các kiểu dưới đây có index signature để nhận thẳng
 * document đó (A truyền `Doc`), còn các trường có tên chỉ để gợi ý. Luôn kiểm tra kiểu khi đọc.
 */
export interface VenueDoc {
  [field: string]: unknown;
  name?: string;
  address?: string;
  districtId?: string;
  ownerId?: string;
  status?: string;
}

export interface VenueSnapshot {
  name: string;
  address: string;
  districtName: string;
}

/** `dropInSessions/{sessionId}`. `id` không có trong document, người gọi tự gắn khi cần. */
export interface SessionDoc {
  [field: string]: unknown;
  id?: string;
  venueId?: string;
  ownerId?: string;
  districtId?: string;
  courtIds?: string[];
  date?: string;
  startAt?: Date;
  endAt?: Date;
  timeSlot?: TimeSlot;
  venueSnapshot?: VenueSnapshot;
  level?: string;
  capacity?: number;
  registeredCount?: number;
  pricePerPerson?: number;
  status?: SessionStatus | string;
}

/** `dropInSessions/{sessionId}/registrations/{uid}`. */
export interface RegistrationDoc {
  [field: string]: unknown;
  userId?: string;
  sessionId?: string;
  venueId?: string;
  sessionStartAt?: Date;
  sessionEndAt?: Date;
  partySize?: number;
  status?: RegistrationStatus | string;
  paymentMethod?: PaymentMethod | string;
  paymentStatus?: PaymentStatus | string;
  pricePerPerson?: number;
  amount?: number;
  refundAmount?: number | null;
  qrToken?: string | null;
  ticketCode?: string | null;
  requestId?: string;
  cancelRequestId?: string | null;
  sessionChanged?: boolean;
  registeredAt?: Date;
  cancelledAt?: Date | null;
  cancelledBy?: CancelledBy | null;
  checkedInAt?: Date | null;
  waitlistedAt?: Date | null;
  createdAt?: Date;
  updatedAt?: Date;
}

/**
 * Phần transaction mà helper cần. Khớp cả `FirestoreTransaction` của `_shared/firestore.ts` lẫn `DeletionTx`
 * của `_shared/account-deletion.ts`.
 */
export interface DropInTx {
  get(path: string): Promise<Record<string, unknown> | null>;
  write(write: Write): void;
}

export const sessionPath = (sessionId: string) => `dropInSessions/${sessionId}`;
export const registrationPath = (sessionId: string, uid: string) => `dropInSessions/${sessionId}/registrations/${uid}`;

/** Đăng ký đang giữ chỗ (được tính vào `registeredCount`). */
export const ACTIVE_STATUSES: readonly string[] = ["REGISTERED", "CHECKED_IN"];

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const asNumber = (v: unknown, fallback = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : fallback);

// ---------- Trường suy ra của buổi (FR-003; D gọi khi tạo/sửa buổi) ----------

/** Asia/Ho_Chi_Minh luôn là UTC+7 (không có giờ mùa hè). */
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

/**
 * `timeSlot` theo giờ bắt đầu: MORNING 05:00–11:00, NOON 11:00–13:00, AFTERNOON 13:00–18:00, EVENING 18:00–23:00.
 * Giờ ngoài 05:00–23:00 (không nên có) được xếp vào khung gần nhất: trước 05:00 → MORNING, từ 23:00 → EVENING.
 * `date` là `yyyyMMdd` theo giờ Việt Nam.
 */
export function deriveSessionFields(input: { startAt: Date; venue: VenueDoc; districtName: string }): {
  timeSlot: TimeSlot;
  date: string;
  venueSnapshot: VenueSnapshot;
} {
  const local = new Date(input.startAt.getTime() + VN_OFFSET_MS);
  const minutes = local.getUTCHours() * 60 + local.getUTCMinutes();
  const timeSlot: TimeSlot = minutes < 11 * 60
    ? "MORNING"
    : minutes < 13 * 60
    ? "NOON"
    : minutes < 18 * 60
    ? "AFTERNOON"
    : "EVENING";
  const date = `${local.getUTCFullYear()}${String(local.getUTCMonth() + 1).padStart(2, "0")}${
    String(local.getUTCDate()).padStart(2, "0")
  }`;
  return {
    timeSlot,
    date,
    venueSnapshot: {
      name: typeof input.venue.name === "string" ? input.venue.name : "",
      address: typeof input.venue.address === "string" ? input.venue.address : "",
      districtName: input.districtName,
    },
  };
}

// ---------- Hoàn tiền (research R4) ----------

/** Hủy trước giờ bắt đầu từ mốc này trở lên thì được hoàn 100% (cùng mốc với hủy đặt sân). */
export const FREE_CANCEL_MINUTES = 240;

/** Số tiền của lượt: `amount`, thiếu thì `pricePerPerson × partySize`. */
export function registrationAmount(reg: RegistrationDoc): number {
  if (typeof reg.amount === "number") return reg.amount;
  return asNumber(reg.pricePerPerson) * asNumber(reg.partySize, 1);
}

/**
 * Tiền hoàn khi hủy ở thời điểm `serverNow`. Chỉ lượt `paymentStatus == PAID` mới có tiền hoàn; lượt khác
 * (`UNPAID` của AT_VENUE, hoặc đã `REFUNDED`) trả `0` với `reason = AT_VENUE` ("Không mất phí").
 * Giờ bắt đầu lấy từ `session.startAt`, thiếu thì dùng `reg.sessionStartAt`.
 */
export function computeRefund(
  reg: RegistrationDoc,
  session: SessionDoc,
  serverNow: Date,
): { refundAmount: number; reason: RefundReason } {
  if (reg.paymentStatus !== "PAID") return { refundAmount: 0, reason: "AT_VENUE" };
  const amount = registrationAmount(reg);
  if (reg.sessionChanged === true) return { refundAmount: amount, reason: "SESSION_CHANGED" };
  const startAt = asDate(session.startAt) ?? asDate(reg.sessionStartAt);
  if (startAt && startAt.getTime() - serverNow.getTime() >= FREE_CANCEL_MINUTES * 60_000) {
    return { refundAmount: amount, reason: "EARLY" };
  }
  return { refundAmount: 0, reason: "LATE" };
}

// ---------- Hủy đăng ký trong transaction của người gọi ----------

export interface CancelRegistrationOptions {
  /** Hoàn 100% số đã trả bất kể mốc giờ (chủ sân hủy buổi). */
  forceFullRefund?: boolean;
  /** Giờ server; mặc định `new Date()`. */
  now?: Date;
  /** `cancel-drop-in` ghi `requestId` của lần hủy để gọi lặp trả kết quả cũ. */
  cancelRequestId?: string;
}

/**
 * Hủy đăng ký `uid` của buổi `sessionId` và trả chỗ, trong transaction `tx` của người gọi.
 *
 * - Chỉ xử lý đăng ký đang giữ chỗ (`REGISTERED`, `CHECKED_IN`). Không có, đã `CANCELLED` hoặc `WAITLISTED`
 *   (spec 081 có `leaveWaitlistInTx` riêng) thì không ghi gì và trả `refundAmount = 0`. Người gọi tự kiểm tra
 *   quyền và trạng thái được phép hủy (ví dụ `cancel-drop-in` chặn `CHECKED_IN` bằng `NOT_CANCELLABLE`).
 * - Đăng ký → `CANCELLED`, `qrToken = null`, `ticketCode = null`, ghi `cancelledAt`, `cancelledBy`,
 *   `refundAmount`; `paymentStatus = REFUNDED` khi `refundAmount > 0`.
 * - Buổi: `registeredCount -= partySize` (không âm); `FULL → OPEN`. Buổi `CANCELLED`/`COMPLETED` giữ trạng thái.
 * - Buổi đang `OPEN`/`FULL` thì gọi `afterSpotsReleased` TRƯỚC khi ghi buổi: hook đọc thêm nếu cần rồi sửa trực
 *   tiếp `registeredCount`/`status` của object `session` được truyền vào; hàm này ghi buổi theo object đó.
 *
 * Không gọi hai lần cho cùng một buổi trong một transaction (lần sau đọc lại số đếm cũ).
 */
export async function cancelRegistrationInTx(
  tx: DropInTx,
  sessionId: string,
  uid: string,
  cancelledBy: CancelledBy,
  opts: CancelRegistrationOptions = {},
): Promise<{ refundAmount: number; reason?: RefundReason }> {
  const now = opts.now ?? new Date();
  const regPath = registrationPath(sessionId, uid);
  const reg = await tx.get(regPath) as RegistrationDoc | null;
  if (!reg || !ACTIVE_STATUSES.includes(String(reg.status))) return { refundAmount: 0 };
  const sessionDoc = await tx.get(sessionPath(sessionId)) as SessionDoc | null;
  const session: SessionDoc = sessionDoc ?? {};

  const quote = computeRefund(reg, session, now);
  const refundAmount = opts.forceFullRefund && reg.paymentStatus === "PAID"
    ? registrationAmount(reg)
    : quote.refundAmount;

  const partySize = asNumber(reg.partySize, 1);
  const liveSession = session.status === "OPEN" || session.status === "FULL";
  const released: SessionDoc = {
    ...session,
    id: sessionId,
    registeredCount: Math.max(0, asNumber(session.registeredCount) - partySize),
    status: session.status === "FULL" ? "OPEN" : session.status,
  };
  if (liveSession) await afterSpotsReleased(tx, released);

  tx.write(updateWrite(regPath, {
    status: "CANCELLED",
    qrToken: null,
    ticketCode: null,
    cancelledAt: now,
    cancelledBy,
    refundAmount,
    ...(refundAmount > 0 ? { paymentStatus: "REFUNDED" } : {}),
    ...(opts.cancelRequestId ? { cancelRequestId: opts.cancelRequestId } : {}),
    updatedAt: now,
  }));
  // Buổi không còn document (dữ liệu lệch): chỉ hủy đăng ký, không ghi buổi để precondition `exists` không làm
  // hỏng transaction của người gọi (ví dụ delete-account).
  if (sessionDoc) {
    tx.write(updateWrite(sessionPath(sessionId), {
      registeredCount: released.registeredCount,
      ...(released.status !== undefined ? { status: released.status } : {}),
      updatedAt: now,
    }));
  }
  return { refundAmount, reason: quote.reason };
}

/**
 * Hook sau khi trả chỗ, chạy trong cùng transaction. Spec 080 để trống; spec 081 thay bằng logic đẩy người từ
 * hàng chờ lên (081 FR-007): sửa `session.registeredCount`/`session.status` tại chỗ, ghi đăng ký được đẩy lên
 * bằng `tx.write`.
 */
export async function afterSpotsReleased(_tx: DropInTx, _session: SessionDoc): Promise<void> {
  // Spec 081 cài.
}

// ---------- Sinh mã (research R5) ----------

/** 32 byte ngẫu nhiên, base64url không đệm (43 ký tự). */
export function newQrToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Bỏ I, O, 0, 1 để đọc mã không nhầm. Đúng 32 ký tự nên lấy 5 bit thấp mỗi byte không lệch phân bố. */
const TICKET_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/** `DI-` + 8 ký tự `[A-HJ-NP-Z2-9]`. */
export function newTicketCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  let code = "DI-";
  for (const b of bytes) code += TICKET_ALPHABET[b & 31];
  return code;
}

// ---------- D (spec 112, owner-cancel-session) đang dùng: giữ nguyên chữ ký và hành vi ----------

export function closeWaitlistInTx(
  sessionId: string,
  reason: string,
): Write {
  return updateWrite(`dropInSessions/${sessionId}`, {
    waitlistStatus: "CLOSED",
    closeReason: reason,
    updatedAt: new Date().toISOString(),
  });
}
