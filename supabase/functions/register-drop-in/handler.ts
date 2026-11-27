// Edge Function `register-drop-in` (C, spec 080 US2; contracts/edge-functions-dropin.md §1).
// Đăng ký một lượt chơi vãng lai: kiểm tra điều kiện và giữ chỗ trong MỘT Firestore transaction trên document
// buổi `dropInSessions/{sessionId}` (research R2), idempotent theo `requestId` (research R3).
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { documentName, encodeFields, type FirestoreTransaction, updateWrite, type Write } from "../_shared/firestore.ts";
import {
  createKeyedSerializer,
  type KeyedSerializer,
  MAX_TX_ATTEMPTS,
  readJsonObject,
  runWithBackoff,
  UUID,
} from "../_shared/dropin-tx.ts";
import {
  newQrToken,
  newTicketCode,
  type PaymentMethod,
  registrationPath,
  type RegistrationDoc,
  type SessionDoc,
  sessionPath,
} from "../_shared/dropin.ts";
import { isWaitlistOpen } from "../_shared/waitlist.ts";
import { notificationInboxId, prepareNotification } from "../_shared/notification-enqueue.ts";

export interface Notification {
  type: "DROPIN_REGISTERED";
  userId: string;
  data: { sessionId: string };
}

export interface RegisterDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Xác thực người gọi; mặc định `requireUser` (401 UNAUTHENTICATED, 403 ACCOUNT_LOCKED, ...). */
  authenticate: (req: Request) => Promise<{ uid: string }>;
  /** Retired callback seam retained for source test compatibility; D3 never invokes it. */
  notify: (n: Notification) => Promise<void>;
  /** Giờ server (`serverNow`). */
  now: () => Date;
  /** Chờ giữa các lần thử lại; test thay để chạy nhanh. */
  sleep?: (ms: number) => Promise<void>;
  /**
   * Xếp hàng các request cùng `sessionId` trong một isolate trước khi mở transaction, để chúng không cùng giữ khóa
   * đọc trên document buổi (Emulator khi đó chờ tới "Transaction lock timeout" ~2 giây rồi abort tất cả).
   * Chỉ giảm tranh chấp: giữa các isolate vẫn dựa vào transaction. Mặc định dùng chung một hàng đợi cho cả isolate.
   */
  serialize?: KeyedSerializer;
}

export type { KeyedSerializer };
export { createKeyedSerializer, MAX_TX_ATTEMPTS };

const isolateSerializer = createKeyedSerializer();

const PAYMENT_METHODS: readonly string[] = ["MOCK_PAY", "AT_VENUE"];

interface RegisterInput {
  kind: "REGISTER";
  sessionId: string;
  partySize: number;
  paymentMethod: PaymentMethod;
  requestId: string;
  /** Spec 081: vào hàng chờ của buổi đã đủ người thay vì đăng ký giữ chỗ. */
  joinWaitlist: boolean;
}

/** `action: "ADD_PARTY"` (FR-008a): thêm người vào đăng ký đang có, tổng tối đa [MAX_PARTY]. */
interface AddPartyInput {
  kind: "ADD_PARTY";
  sessionId: string;
  additional: number;
  requestId: string;
}

/** Tối đa số người của một lượt (bản thân + 3 bạn, FR-006). */
export const MAX_PARTY = 4;

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);

function parseInput(body: Record<string, unknown>): RegisterInput | AddPartyInput {
  const sessionId = typeof body.sessionId === "string" ? body.sessionId.trim() : "";
  if (!sessionId || sessionId.includes("/")) throw invalid("Thiếu hoặc sai sessionId");
  if (body.action !== undefined && body.action !== "REGISTER" && body.action !== "ADD_PARTY") {
    throw invalid("action phải là REGISTER hoặc ADD_PARTY");
  }
  if (body.action === "ADD_PARTY") {
    const additional = body.additional;
    if (typeof additional !== "number" || !Number.isInteger(additional) || additional < 1 || additional >= MAX_PARTY) {
      throw invalid(`additional phải từ 1 đến ${MAX_PARTY - 1}`);
    }
    if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) throw invalid("requestId phải là UUID");
    return { kind: "ADD_PARTY", sessionId, additional, requestId: body.requestId };
  }
  const partySize = body.partySize;
  if (typeof partySize !== "number" || !Number.isInteger(partySize) || partySize < 1 || partySize > 4) {
    throw invalid("partySize phải từ 1 đến 4");
  }
  if (typeof body.paymentMethod !== "string" || !PAYMENT_METHODS.includes(body.paymentMethod)) {
    throw invalid("paymentMethod phải là MOCK_PAY hoặc AT_VENUE");
  }
  if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) throw invalid("requestId phải là UUID");
  if (body.joinWaitlist !== undefined && typeof body.joinWaitlist !== "boolean") {
    throw invalid("joinWaitlist phải là boolean");
  }
  return {
    kind: "REGISTER",
    sessionId,
    partySize,
    paymentMethod: body.paymentMethod as PaymentMethod,
    requestId: body.requestId,
    joinWaitlist: body.joinWaitlist === true,
  };
}

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const asNumber = (v: unknown, fallback = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : fallback);

interface RegisterResult {
  created: boolean;
  registration: RegistrationDoc;
  session: { registeredCount: number; capacity: number; status: string };
  /** Chỉ có khi `joinWaitlist`: vị trí của lượt chờ và tổng số lượt chờ (081 contract §1). */
  waitlist?: { position: number; total: number } | null;
}

function sessionSummary(s: SessionDoc) {
  return {
    registeredCount: asNumber(s.registeredCount),
    capacity: asNumber(s.capacity),
    status: String(s.status ?? ""),
  };
}

interface Checked {
  session: SessionDoc;
  startAt: Date;
  venueId: string;
  regPath: string;
  existing: RegistrationDoc | null;
}

/**
 * Các bước kiểm tra chung của đăng ký và thêm người, theo thứ tự bảng "Quy tắc kiểm tra của register-drop-in"
 * (data-model.md): buổi tồn tại, chưa hủy, chưa bắt đầu, cơ sở ACTIVE, không phải buổi của mình.
 */
async function loadAndCheck(tx: FirestoreTransaction, uid: string, sessionId: string, now: Date): Promise<Checked> {
  const session = await tx.get(sessionPath(sessionId)) as SessionDoc | null;
  if (!session) throw new HttpError(404, "SESSION_NOT_FOUND", "Không tìm thấy buổi chơi");
  if (session.status !== "OPEN" && session.status !== "FULL") {
    throw new HttpError(409, "SESSION_CANCELLED", "Buổi đã bị hủy");
  }
  const startAt = asDate(session.startAt);
  if (!startAt) throw new Error(`dropInSessions/${sessionId} thiếu startAt (Timestamp)`);
  if (now.getTime() >= startAt.getTime()) throw new HttpError(409, "SESSION_STARTED", "Buổi đã bắt đầu");

  const venueId = typeof session.venueId === "string" ? session.venueId : "";
  const regPath = registrationPath(sessionId, uid);
  const [venue, existing] = await Promise.all([
    venueId ? tx.get(`venues/${venueId}`) : Promise.resolve(null),
    tx.get(regPath) as Promise<RegistrationDoc | null>,
  ]);
  if (!venue || venue.status !== "ACTIVE") {
    throw new HttpError(409, "VENUE_INACTIVE", "Cơ sở tạm ngừng hoạt động");
  }
  if (session.ownerId === uid || venue.ownerId === uid) {
    throw new HttpError(403, "OWN_SESSION", "Bạn không thể đăng ký buổi do mình mở");
  }
  return { session, startAt, venueId, regPath, existing };
}

/** Đăng ký mới (US2). */
async function register(
  tx: FirestoreTransaction,
  uid: string,
  input: RegisterInput,
  now: Date,
): Promise<{ result: RegisterResult; writes: Write[] }> {
  const checked = await loadAndCheck(tx, uid, input.sessionId, now);
  if (input.joinWaitlist) return joinWaitlist(uid, input, now, checked);
  const { session, startAt, venueId, regPath, existing } = checked;

  if (existing && (existing.status === "REGISTERED" || existing.status === "CHECKED_IN")) {
    if (existing.requestId === input.requestId) {
      // Gửi lại (mất mạng, bấm nhiều lần): trả bản đã lưu, không ghi gì.
      return { result: { created: false, registration: existing, session: sessionSummary(session) }, writes: [] };
    }
    throw new HttpError(409, "ALREADY_REGISTERED", "Bạn đã đăng ký buổi này");
  }
  if (existing?.status === "WAITLISTED") {
    throw new HttpError(409, "ALREADY_WAITLISTED", "Bạn đang ở hàng chờ của buổi này");
  }

  const identity = JSON.stringify([input.sessionId, uid, input.requestId, "DROPIN_REGISTERED"]);
  const notificationId = await notificationInboxId("dropin-registration", identity, uid, "DROPIN_REGISTERED");
  if (await tx.get(`users/${uid}/notifications/${notificationId}`)) {
    throw new HttpError(409, "STALE_REQUEST", "Lần đăng ký cũ đã kết thúc; hãy dùng mã yêu cầu mới");
  }
  const notificationWrites = await prepareNotification(tx, "dropin-registration", identity, uid, notificationId, {
    type: "DROPIN_REGISTERED", title: "Đăng ký buổi chơi thành công",
    body: "Mở vé để xem thông tin buổi chơi.", data: { sessionId: input.sessionId },
  }, now);

  const capacity = asNumber(session.capacity);
  const registeredCount = asNumber(session.registeredCount);
  const remaining = Math.max(0, capacity - registeredCount);
  if (input.partySize > remaining) {
    throw new HttpError(409, "SESSION_FULL", `Buổi không còn đủ chỗ (còn ${remaining} chỗ)`, {
      remaining,
      // Để app hiện nút "Vào hàng chờ" (081): buổi đã đủ người và còn hơn 30 phút nữa mới bắt đầu.
      waitlistOpen: session.status === "FULL" && isWaitlistOpen(session, now),
    });
  }

  const newCount = registeredCount + input.partySize;
  const newStatus = newCount >= capacity ? "FULL" : "OPEN";
  const pricePerPerson = asNumber(session.pricePerPerson);
  const registration: RegistrationDoc = {
    userId: uid,
    sessionId: input.sessionId,
    venueId,
    sessionStartAt: startAt,
    sessionEndAt: asDate(session.endAt) ?? undefined,
    partySize: input.partySize,
    status: "REGISTERED",
    paymentMethod: input.paymentMethod,
    paymentStatus: input.paymentMethod === "MOCK_PAY" ? "PAID" : "UNPAID",
    pricePerPerson,
    amount: pricePerPerson * input.partySize,
    refundAmount: null,
    qrToken: newQrToken(),
    ticketCode: newTicketCode(),
    requestId: input.requestId,
    cancelRequestId: null,
    sessionChanged: false,
    registeredAt: now,
    cancelledAt: null,
    cancelledBy: null,
    checkedInAt: null,
    waitlistedAt: null,
    // Ghi đè bản CANCELLED cũ (E11) thì giữ createdAt ban đầu.
    createdAt: asDate(existing?.createdAt) ?? now,
    updatedAt: now,
  };
  if (registration.sessionEndAt === undefined) delete registration.sessionEndAt;

  return {
    result: {
      created: true,
      registration,
      session: { registeredCount: newCount, capacity, status: newStatus },
    },
    writes: [
      updateWrite(sessionPath(input.sessionId), { registeredCount: newCount, status: newStatus, updatedAt: now }),
      // Ghi cả document (không updateMask) để xóa sạch trường của lần hủy trước; precondition theo bản đã đọc.
      {
        update: { name: documentName(regPath), fields: encodeFields(registration) },
        currentDocument: { exists: existing != null },
      },
      ...notificationWrites,
    ],
  };
}

/**
 * Vào hàng chờ của buổi đã đủ người (spec 081 US1, data-model "Vào hàng chờ"): chưa giữ chỗ, chưa thanh toán.
 * Trong cùng transaction: lượt chờ nhận số vé `waitlistNextTicket`, buổi thêm số vé vào `waitlistTickets`. Chỉ ghi
 * `waitlist*` của buổi, không đụng `registeredCount`. Idempotent theo `requestId` như đăng ký thường.
 */
function joinWaitlist(
  uid: string,
  input: RegisterInput,
  now: Date,
  { session, startAt, venueId, regPath, existing }: Checked,
): { result: RegisterResult; writes: Write[] } {
  const tickets = Array.isArray(session.waitlistTickets)
    ? session.waitlistTickets.filter((n): n is number => typeof n === "number")
    : [];
  if (existing?.status === "WAITLISTED") {
    if (existing.requestId === input.requestId) {
      // Gửi lại: trả bản đã lưu, không ghi gì.
      const position = tickets.indexOf(asNumber(existing.waitlistTicket, -1)) + 1;
      return {
        result: {
          created: false,
          registration: existing,
          session: sessionSummary(session),
          waitlist: position > 0 ? { position, total: tickets.length } : null,
        },
        writes: [],
      };
    }
    throw new HttpError(409, "ALREADY_WAITLISTED", "Bạn đang ở hàng chờ của buổi này");
  }
  if (existing && (existing.status === "REGISTERED" || existing.status === "CHECKED_IN")) {
    if (existing.requestId === input.requestId) {
      // Gửi lại sau khi đã được đẩy lên: trả đăng ký hiện tại.
      return {
        result: { created: false, registration: existing, session: sessionSummary(session), waitlist: null },
        writes: [],
      };
    }
    throw new HttpError(409, "ALREADY_REGISTERED", "Bạn đã đăng ký buổi này");
  }

  if (session.status !== "FULL") {
    const remaining = Math.max(0, asNumber(session.capacity) - asNumber(session.registeredCount));
    throw new HttpError(409, "SESSION_HAS_SPOTS", `Buổi còn ${remaining} chỗ, hãy đăng ký thường`, { remaining });
  }
  if (!isWaitlistOpen(session, now)) throw new HttpError(409, "WAITLIST_CLOSED", "Hàng chờ đã đóng");

  const ticket = asNumber(session.waitlistNextTicket, 1);
  const pricePerPerson = asNumber(session.pricePerPerson);
  const newTickets = [...tickets, ticket];
  const registration: RegistrationDoc = {
    userId: uid,
    sessionId: input.sessionId,
    venueId,
    sessionStartAt: startAt,
    sessionEndAt: asDate(session.endAt) ?? undefined,
    partySize: input.partySize,
    status: "WAITLISTED",
    paymentMethod: input.paymentMethod,
    paymentStatus: null,
    // Giá chốt lúc vào hàng chờ (Edge Cases: giá đổi khi đang chờ).
    pricePerPerson,
    amount: pricePerPerson * input.partySize,
    refundAmount: null,
    qrToken: null,
    ticketCode: null,
    requestId: input.requestId,
    cancelRequestId: null,
    sessionChanged: false,
    registeredAt: null,
    cancelledAt: null,
    cancelledBy: null,
    checkedInAt: null,
    waitlistedAt: now,
    waitlistTicket: ticket,
    promotedAt: null,
    waitlistEndReason: null,
    createdAt: asDate(existing?.createdAt) ?? now,
    updatedAt: now,
  };
  if (registration.sessionEndAt === undefined) delete registration.sessionEndAt;

  return {
    result: {
      created: true,
      registration,
      session: sessionSummary(session),
      waitlist: { position: newTickets.length, total: newTickets.length },
    },
    writes: [
      updateWrite(sessionPath(input.sessionId), {
        waitlistNextTicket: ticket + 1,
        waitlistTickets: newTickets,
        waitlistCount: newTickets.length,
        updatedAt: now,
      }),
      // Ghi cả document để xóa sạch trường của lần hủy/rời trước (AC 3.3); precondition theo bản đã đọc.
      {
        update: { name: documentName(regPath), fields: encodeFields(registration) },
        currentDocument: { exists: existing != null },
      },
    ],
  };
}

/**
 * Thêm người vào đăng ký đang có (FR-008a). Giữ nguyên QR, mã vé, cách thanh toán: MOCK_PAY coi như đã trả phần
 * thêm (vẫn PAID), AT_VENUE trả tại sân. Gửi lại cùng `requestId` (lưu ở `lastAddRequestId`) thì không cộng lần hai.
 */
async function addParty(
  tx: FirestoreTransaction,
  uid: string,
  input: AddPartyInput,
  now: Date,
): Promise<{ result: RegisterResult; writes: Write[] }> {
  const { session, regPath, existing } = await loadAndCheck(tx, uid, input.sessionId, now);
  if (!existing || existing.status !== "REGISTERED") {
    throw new HttpError(409, "NOT_REGISTERED", "Bạn chưa có lượt đăng ký còn hiệu lực ở buổi này");
  }
  if (existing.lastAddRequestId === input.requestId) {
    return { result: { created: false, registration: existing, session: sessionSummary(session) }, writes: [] };
  }
  const currentParty = asNumber(existing.partySize, 1);
  const maxAdditional = Math.max(0, MAX_PARTY - currentParty);
  if (input.additional > maxAdditional) {
    throw new HttpError(409, "PARTY_LIMIT", `Mỗi lượt tối đa ${MAX_PARTY} người (thêm được ${maxAdditional})`, {
      maxAdditional,
    });
  }
  const capacity = asNumber(session.capacity);
  const registeredCount = asNumber(session.registeredCount);
  const remaining = Math.max(0, capacity - registeredCount);
  if (input.additional > remaining) {
    throw new HttpError(409, "SESSION_FULL", `Buổi không còn đủ chỗ (còn ${remaining} chỗ)`, { remaining });
  }

  const newCount = registeredCount + input.additional;
  const newStatus = newCount >= capacity ? "FULL" : "OPEN";
  const pricePerPerson = asNumber(existing.pricePerPerson, asNumber(session.pricePerPerson));
  const patch = {
    partySize: currentParty + input.additional,
    amount: asNumber(existing.amount) + pricePerPerson * input.additional,
    lastAddRequestId: input.requestId,
    updatedAt: now,
  };
  return {
    result: {
      created: false,
      registration: { ...existing, ...patch },
      session: { registeredCount: newCount, capacity, status: newStatus },
    },
    writes: [
      updateWrite(sessionPath(input.sessionId), { registeredCount: newCount, status: newStatus, updatedAt: now }),
      updateWrite(regPath, patch),
    ],
  };
}

/** Body trả về: không có `qrToken` (contract §1). */
function toResponseRegistration(r: RegistrationDoc) {
  return {
    sessionId: r.sessionId,
    status: r.status,
    partySize: r.partySize,
    paymentMethod: r.paymentMethod,
    paymentStatus: r.paymentStatus,
    pricePerPerson: r.pricePerPerson,
    amount: r.amount,
    ticketCode: r.ticketCode ?? null,
    registeredAt: asDate(r.registeredAt)?.toISOString() ?? null,
    // Chỉ lượt chờ mới có (không đổi dạng response của đăng ký thường).
    ...(r.status === "WAITLISTED" ? { waitlistedAt: asDate(r.waitlistedAt)?.toISOString() ?? null } : {}),
  };
}

export async function handleRegisterDropIn(req: Request, deps: RegisterDeps = defaultDeps()): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const { uid } = await deps.authenticate(req);
    const input = parseInput(await readJsonObject(req));
    const serialize = deps.serialize ?? isolateSerializer;
    // Lấy giờ sau khi tới lượt trong hàng đợi để kiểm tra SESSION_STARTED không dùng giờ cũ.
    const result = await serialize(input.sessionId, () => {
      const now = deps.now();
      return runWithBackoff(
        deps,
        (tx) => input.kind === "ADD_PARTY" ? addParty(tx, uid, input, now) : register(tx, uid, input, now),
      );
    });

    // REGISTER enqueues atomically; waitlist and ADD_PARTY are silent self actions (D3).
    return Response.json(
      {
        registration: toResponseRegistration(result.registration),
        session: result.session,
        ...(result.waitlist !== undefined ? { waitlist: result.waitlist } : {}),
      },
      { status: result.created ? 201 : 200 },
    );
  } catch (e) {
    return toErrorResponse(e);
  }
}

// ---------- Mặc định khi chạy thật ----------


export function defaultDeps(): RegisterDeps {
  return {
    firestore: defaultFirestore,
    authenticate: (req) => requireUser(req),
    notify: async () => {},
    now: () => new Date(),
  };
}
