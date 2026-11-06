// Điều phối xóa tài khoản (A viết; spec 011 US6, research R11, R12, contracts/edge-functions-account.md §1–§2).
//
// 1. computeImpact: đọc đơn, lượt vãng lai, lượt chờ sắp tới và (với chủ sân) cơ sở cùng đơn/buổi của khách.
// 2. commitDeletion: MỘT transaction gọi helper của B, C, D để hủy và hoàn tiền, ghi `deletionRequestedAt`
//    và tạo `accountDeletions/{uid}` (FR-021, FR-022a). Lỗi thì không có gì đổi.
// 3. runCleanup: 10 bước dọn dẹp idempotent; lỗi ghi FAILED và để sweeper chạy lại.
//
// Helper của nhóm khác truyền qua `DeletionHelpers` (xem `account-deletion-helpers.ts`). Helper bước CHỐT chưa có
// thì không xóa được người có dữ liệu tương ứng (500); helper bước DỌN DẸP chưa có thì bước đó ghi SKIPPED.
import * as defaultFirestore from "./firestore.ts";
import {
  deleteWrite,
  encodeValue,
  type FieldTransform,
  type QueryDocument,
  type QueryFilter,
  type QueryOptions,
  updateWrite,
  type Write,
} from "./firestore.ts";
import * as defaultIdentity from "./identity.ts";
import { defaultHelpers } from "./account-deletion-helpers.ts";

export type Doc = Record<string, unknown>;

// ---------- Hợp đồng với helper của B, C, D (plan.md "Phối hợp với nhóm") ----------

/**
 * Transaction đưa cho helper: đọc bằng `get`/`query` (trong transaction), ghi bằng `write`/`increment` (gom lại,
 * commit một lần ở cuối). `increment` cùng document được gộp vào một thao tác ghi.
 */
export interface DeletionTx {
  get(path: string): Promise<Doc | null>;
  query(collectionId: string, filters: QueryFilter[], opts?: QueryOptions): Promise<QueryDocument[]>;
  write(...writes: Write[]): void;
  increment(path: string, field: string, amount: number): void;
}

export interface NotificationInput {
  recipientUid: string;
  type: "BOOKING_CANCELLED" | "DROPIN_CHANGED";
  data: Record<string, string>;
  /** Cùng khóa gọi lại thì không gửi thêm. */
  idempotencyKey: string;
}

export interface DeletionHelpers {
  // B (_shared/booking.ts)
  cancelBookingInTx?: (
    tx: DeletionTx,
    booking: QueryDocument,
    opts: { cancelledBy: "SYSTEM"; reason: string; refund: "POLICY" | "FULL" },
  ) => Promise<{ refundAmount: number }>;
  computeBookingRefund?: (booking: Doc, now: Date) => number;
  anonymizeBookings?: (uid: string) => Promise<void>;
  // C (_shared/dropin.ts, _shared/waitlist.ts, _shared/reviews.ts, _shared/community.ts)
  cancelRegistrationInTx?: (
    tx: DeletionTx,
    sessionId: string,
    uid: string,
    cancelledBy: "SYSTEM",
    opts?: { forceFullRefund?: boolean },
  ) => Promise<{ refundAmount: number }>;
  computeRefund?: (reg: Doc, session: Doc, now: Date) => { refundAmount: number };
  leaveWaitlistInTx?: (tx: DeletionTx, sessionId: string, uid: string, reason: "LEFT", by: "SYSTEM") => Promise<void>;
  anonymizeReviews?: (uid: string) => Promise<void>;
  anonymizeCommunity?: (uid: string) => Promise<void>;
  // D (_shared/venue.ts, _shared/owner.ts, send-notification)
  hideVenueForOwnerDeletionInTx?: (tx: DeletionTx, venueId: string) => Promise<void>;
  cancelSessionInTx?: (tx: DeletionTx, session: QueryDocument, opts: { reason: string; refund: "FULL" }) => Promise<void>;
  deleteOwnerApplications?: (uid: string) => Promise<void>;
  sendNotification?: (n: NotificationInput) => Promise<void>;
  /** Số field transform mỗi lần gọi helper (`TRANSFORMS_PER_CALL` của từng module); thiếu thì dùng 2. */
  transformsPerCall?: number;
}

export interface StorageAdmin {
  /** Đường dẫn đầy đủ của mọi object trong `avatars/{uid}/`. */
  listAvatarPaths(uid: string): Promise<string[]>;
  removeAvatars(paths: string[]): Promise<void>;
}

export interface DeletionDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit" | "query" | "runTransaction">;
  identity: Pick<typeof defaultIdentity, "setUserDisabled" | "deleteUser">;
  storage: StorageAdmin;
  helpers: DeletionHelpers;
  now: () => Date;
}

/** Storage API của Supabase với service role (bucket `avatars`). */
export function supabaseStorageAdmin(cfg: { url: string; serviceKey: string }): StorageAdmin {
  const headers = {
    Authorization: `Bearer ${cfg.serviceKey}`,
    apikey: cfg.serviceKey,
    "Content-Type": "application/json",
  };
  return {
    async listAvatarPaths(uid) {
      const paths: string[] = [];
      for (let offset = 0;; offset += 100) {
        const res = await fetch(`${cfg.url}/storage/v1/object/list/avatars`, {
          method: "POST",
          headers,
          body: JSON.stringify({ prefix: `${uid}/`, limit: 100, offset }),
        });
        if (!res.ok) throw new Error(`STORAGE_LIST_${res.status}`);
        const items = await res.json() as { name: string }[];
        paths.push(...items.map((i) => `${uid}/${i.name}`));
        if (items.length < 100) return paths;
      }
    },
    async removeAvatars(paths) {
      if (paths.length === 0) return;
      const res = await fetch(`${cfg.url}/storage/v1/object/avatars`, {
        method: "DELETE",
        headers,
        body: JSON.stringify({ prefixes: paths }),
      });
      if (!res.ok) throw new Error(`STORAGE_DELETE_${res.status}`);
      await res.body?.cancel();
    },
  };
}

export function defaultDeletionDeps(): DeletionDeps {
  return {
    firestore: defaultFirestore,
    identity: defaultIdentity,
    storage: supabaseStorageAdmin({
      url: Deno.env.get("SUPABASE_URL") ?? "",
      serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    }),
    helpers: defaultHelpers,
    now: () => new Date(),
  };
}

// ---------- Tác động của việc xóa ----------

type Reader = Pick<DeletionTx, "get" | "query">;

export interface GuestSession {
  session: QueryDocument;
  registrations: QueryDocument[];
}

export interface Impact {
  version: string;
  walletBalance: number;
  myBookings: QueryDocument[];
  /** Lượt `REGISTERED` của buổi chưa bắt đầu (kèm document buổi). */
  myRegistrations: { registration: QueryDocument; session: Doc }[];
  /** Lượt `WAITLISTED`. */
  myWaitlists: QueryDocument[];
  venues: QueryDocument[];
  guestBookings: QueryDocument[];
  guestSessions: GuestSession[];
}

const UPCOMING_BOOKING = ["HOLD", "PENDING", "CONFIRMED"];
const OPEN_SESSION = ["OPEN", "FULL"];
const ACTIVE_REGISTRATION = ["REGISTERED", "WAITLISTED"];

const asDate = (v: unknown): Date | null => (v instanceof Date ? v : null);
const after = (v: unknown, now: Date): boolean => (asDate(v)?.getTime() ?? 0) > now.getTime();

function isUpcomingBooking(b: Doc, now: Date): boolean {
  return UPCOMING_BOOKING.includes(String(b.status)) && b.checkedInAt == null && after(b.endAt, now);
}

/** Tiền người dùng đã trả cho đơn hoặc lượt vãng lai (VND). */
export function paidAmount(doc: Doc): number {
  if (doc.paymentStatus === "DEPOSITED") return Number(doc.depositAmount ?? 0);
  if (doc.paymentStatus === "PAID") return Number(doc.total ?? doc.amount ?? 0);
  return 0;
}

async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const byId = (a: QueryDocument, b: QueryDocument) => a.path.localeCompare(b.path);

export async function computeImpact(uid: string, now: Date, reader: Reader): Promise<Impact> {
  const user = await reader.get(`users/${uid}`);
  const myBookings = (await reader.query("bookings", [
    { field: "userId", op: "EQUAL", value: uid },
    { field: "status", op: "IN", value: UPCOMING_BOOKING },
  ])).filter((b) => isUpcomingBooking(b.data, now)).sort(byId);

  const regs = await reader.query("registrations", [
    { field: "userId", op: "EQUAL", value: uid },
    { field: "status", op: "IN", value: ACTIVE_REGISTRATION },
  ], { allDescendants: true });
  const myRegistrations: Impact["myRegistrations"] = [];
  const myWaitlists: QueryDocument[] = [];
  for (const reg of regs.sort(byId)) {
    if (!after(reg.data.sessionStartAt, now)) continue;
    if (reg.data.status === "WAITLISTED") {
      myWaitlists.push(reg);
    } else {
      const sessionId = String(reg.data.sessionId ?? reg.path.split("/")[1]);
      myRegistrations.push({ registration: reg, session: (await reader.get(`dropInSessions/${sessionId}`)) ?? {} });
    }
  }

  const venues = (await reader.query("venues", [{ field: "ownerId", op: "EQUAL", value: uid }])).sort(byId);
  const guestBookings: QueryDocument[] = [];
  const guestSessions: GuestSession[] = [];
  for (const venue of venues) {
    const bookings = await reader.query("bookings", [
      { field: "venueId", op: "EQUAL", value: venue.id },
      { field: "status", op: "IN", value: UPCOMING_BOOKING },
    ]);
    guestBookings.push(...bookings.filter((b) => b.data.userId !== uid && isUpcomingBooking(b.data, now)));
    const sessions = await reader.query("dropInSessions", [
      { field: "venueId", op: "EQUAL", value: venue.id },
      { field: "status", op: "IN", value: OPEN_SESSION },
    ]);
    for (const session of sessions.filter((s) => after(s.data.startAt, now)).sort(byId)) {
      const registrations = await reader.query("registrations", [
        { field: "status", op: "IN", value: ACTIVE_REGISTRATION },
      ], { parent: session.path });
      guestSessions.push({ session, registrations: registrations.sort(byId) });
    }
  }
  guestBookings.sort(byId);

  const ids = [
    ...myBookings.map((d) => d.path),
    ...myRegistrations.map((r) => r.registration.path),
    ...myWaitlists.map((d) => d.path),
    ...venues.map((d) => d.path),
    ...guestBookings.map((d) => d.path),
    ...guestSessions.flatMap((g) => [g.session.path, ...g.registrations.map((r) => r.path)]),
  ].sort();
  return {
    version: `sha256:${await sha256(ids.join("\n"))}`,
    walletBalance: Number(user?.walletBalance ?? 0),
    myBookings,
    myRegistrations,
    myWaitlists,
    venues,
    guestBookings,
    guestSessions,
  };
}

/** Body `200` của `preview` (contracts §1.1). Thiếu helper tính tiền hoàn thì tạm dùng số đã trả. */
export function toPreview(impact: Impact, now: Date, helpers: DeletionHelpers) {
  const guestRegs = impact.guestSessions.flatMap((g) => g.registrations);
  const guests = new Set([
    ...impact.guestBookings.map((b) => String(b.data.userId)),
    ...guestRegs.map((r) => r.id),
  ]);
  return {
    impactVersion: impact.version,
    walletBalance: impact.walletBalance,
    myBookings: impact.myBookings.map((b) => ({
      bookingId: b.id,
      venueName: String((b.data.venueSnapshot as Doc | undefined)?.name ?? ""),
      startAt: asDate(b.data.startAt)?.toISOString() ?? null,
      status: b.data.status,
      paidAmount: paidAmount(b.data),
      refundAmount: helpers.computeBookingRefund?.(b.data, now) ?? paidAmount(b.data),
    })),
    myDropIns: impact.myRegistrations.map(({ registration, session }) => ({
      sessionId: String(registration.data.sessionId ?? registration.path.split("/")[1]),
      venueName: String((session.venueSnapshot as Doc | undefined)?.name ?? ""),
      startAt: asDate(registration.data.sessionStartAt)?.toISOString() ?? null,
      paidAmount: paidAmount(registration.data),
      refundAmount: helpers.computeRefund?.(registration.data, session, now).refundAmount ??
        paidAmount(registration.data),
    })),
    waitlistCount: impact.myWaitlists.length,
    ownerImpact: impact.venues.length === 0 ? null : {
      venueCount: impact.venues.length,
      guestBookingCount: impact.guestBookings.length,
      guestDropInCount: guestRegs.length,
      guestCount: guests.size,
    },
  };
}

export const MAX_TRANSFORMS = 450;

/**
 * Ước tính số field transform của transaction chốt (research R11, giới hạn 500):
 * `k × (đơn + lượt vãng lai + lượt chờ + buổi bị hủy + lượt của khách trong các buổi) + 2 × (số ví được hoàn)
 *  + số cơ sở + 3`, `k = transformsPerCall` (mặc định 2).
 */
export function estimateTransforms(impact: Impact, helpers: DeletionHelpers = {}): number {
  const k = helpers.transformsPerCall ?? 2;
  const guestRegs = impact.guestSessions.flatMap((g) => g.registrations);
  const calls = impact.myBookings.length + impact.guestBookings.length + impact.myRegistrations.length +
    impact.myWaitlists.length + impact.guestSessions.length + guestRegs.length;
  const wallets = new Set<string>();
  for (const b of [...impact.myBookings, ...impact.guestBookings]) {
    if (paidAmount(b.data) > 0) wallets.add(String(b.data.userId));
  }
  for (const r of [...impact.myRegistrations.map((x) => x.registration), ...guestRegs]) {
    if (paidAmount(r.data) > 0) wallets.add(r.id);
  }
  return k * calls + 2 * wallets.size + impact.venues.length + 3;
}

// ---------- Chốt ----------

export class ImpactChangedError extends Error {
  constructor(readonly impact: Impact) {
    super("IMPACT_CHANGED");
  }
}

export class HelperMissingError extends Error {
  constructor(readonly helper: string) {
    super(`HELPER_MISSING:${helper}`);
  }
}

export interface NotifyEntry {
  kind: "BOOKING" | "DROPIN";
  id: string;
  recipientUid: string;
}

const MINUTE = 60_000;
const OWNER_DELETED_REASON = "Cơ sở ngừng hoạt động";
const ACCOUNT_DELETED_REASON = "Tài khoản đã bị xóa";

function need<K extends keyof DeletionHelpers>(helpers: DeletionHelpers, name: K): NonNullable<DeletionHelpers[K]> {
  const fn = helpers[name];
  if (!fn) throw new HelperMissingError(name);
  return fn as NonNullable<DeletionHelpers[K]>;
}

/** Gom thao tác ghi của helper; `increment` cùng document gộp vào thao tác ghi đã có của document đó. */
class TxWriter implements DeletionTx {
  private readonly writes: Write[] = [];
  private readonly increments = new Map<string, Map<string, number>>();

  constructor(private readonly tx: defaultFirestore.FirestoreTransaction) {}

  get(path: string) {
    return this.tx.get(path);
  }

  query(collectionId: string, filters: QueryFilter[], opts?: QueryOptions) {
    return this.tx.query(collectionId, filters, opts);
  }

  write(...writes: Write[]) {
    this.writes.push(...writes);
  }

  increment(path: string, field: string, amount: number) {
    const fields = this.increments.get(path) ?? new Map<string, number>();
    fields.set(field, (fields.get(field) ?? 0) + amount);
    this.increments.set(path, fields);
  }

  build(): Write[] {
    const writes = [...this.writes];
    for (const [path, fields] of this.increments) {
      const transforms: FieldTransform[] = [...fields].map(([fieldPath, n]) => ({ fieldPath, increment: encodeValue(n) }));
      const name = defaultFirestore.documentName(path);
      const existing = writes.find((w) => w.update?.name === name);
      if (existing) {
        existing.updateTransforms = [...(existing.updateTransforms ?? []), ...transforms];
      } else {
        writes.push({
          update: { name, fields: {} },
          updateMask: { fieldPaths: [] },
          updateTransforms: transforms,
          currentDocument: { exists: true },
        });
      }
    }
    return writes;
  }
}

/**
 * Transaction chốt (contracts §1.2 bước 6). Đọc lại tác động trong transaction; khác `expectedVersion` thì ném
 * `ImpactChangedError`. Đã có `accountDeletions/{uid}` thì không ghi gì và trả `"ALREADY"`.
 */
export async function commitDeletion(
  uid: string,
  expectedVersion: string,
  deps: DeletionDeps,
): Promise<"COMMITTED" | "ALREADY"> {
  const { helpers } = deps;
  return await deps.firestore.runTransaction(async (rawTx) => {
    const tx = new TxWriter(rawTx);
    if (await tx.get(`accountDeletions/${uid}`)) return { result: "ALREADY" as const, writes: [] };

    const now = deps.now();
    const impact = await computeImpact(uid, now, tx);
    if (impact.version !== expectedVersion) throw new ImpactChangedError(impact);

    for (const booking of impact.myBookings) {
      await need(helpers, "cancelBookingInTx")(tx, booking, {
        cancelledBy: "SYSTEM",
        reason: ACCOUNT_DELETED_REASON,
        refund: "POLICY",
      });
    }
    for (const { registration } of impact.myRegistrations) {
      const sessionId = String(registration.data.sessionId ?? registration.path.split("/")[1]);
      await need(helpers, "cancelRegistrationInTx")(tx, sessionId, uid, "SYSTEM");
    }
    for (const reg of impact.myWaitlists) {
      const sessionId = String(reg.data.sessionId ?? reg.path.split("/")[1]);
      await need(helpers, "leaveWaitlistInTx")(tx, sessionId, uid, "LEFT", "SYSTEM");
    }

    const notify: NotifyEntry[] = [];
    for (const venue of impact.venues) {
      await need(helpers, "hideVenueForOwnerDeletionInTx")(tx, venue.id);
    }
    for (const booking of impact.guestBookings) {
      await need(helpers, "cancelBookingInTx")(tx, booking, {
        cancelledBy: "SYSTEM",
        reason: OWNER_DELETED_REASON,
        refund: "FULL",
      });
      notify.push({ kind: "BOOKING", id: booking.id, recipientUid: String(booking.data.userId) });
    }
    for (const { session, registrations } of impact.guestSessions) {
      await need(helpers, "cancelSessionInTx")(tx, session, { reason: OWNER_DELETED_REASON, refund: "FULL" });
      for (const reg of registrations) notify.push({ kind: "DROPIN", id: session.id, recipientUid: reg.id });
    }

    const userExists = (await tx.get(`users/${uid}`)) != null;
    if (userExists) {
      tx.write(updateWrite(`users/${uid}`, { fcmTokens: [] }, ["deletionRequestedAt"]));
    }
    tx.write({
      ...defaultFirestore.createWrite(`accountDeletions/${uid}`, {
        state: "COMMITTED",
        wasOwner: impact.venues.length > 0,
        notify,
        steps: {},
        attempts: 0,
        finalSweepAfter: new Date(now.getTime() + 65 * MINUTE),
      }, ["requestedAt"]),
    });
    return { result: "COMMITTED" as const, writes: tx.build() };
  });
}

// ---------- Dọn dẹp ----------

export const CLEANUP_STEPS = [
  "disableAuth",
  "notifyGuests",
  "anonymizeReviews",
  "anonymizeCommunity",
  "anonymizeBookings",
  "deleteOwnerData",
  "deleteSubcollections",
  "deleteAvatars",
  "deleteUserDoc",
  "deleteAuthUser",
] as const;

export type CleanupStep = typeof CLEANUP_STEPS[number] | "finalSweep";
type StepResult = "DONE" | "SKIPPED";

const SUBCOLLECTIONS = ["favorites", "searchHistory", "aiUsage", "notifications"];
const BATCH = 400;

const isUserNotFound = (e: unknown) => e instanceof Error && e.message.includes("USER_NOT_FOUND");

export async function deleteSubcollections(uid: string, deps: DeletionDeps): Promise<void> {
  for (const name of SUBCOLLECTIONS) {
    for (;;) {
      const docs = await deps.firestore.query(name, [], { parent: `users/${uid}`, limit: BATCH });
      if (docs.length === 0) break;
      await deps.firestore.commit(docs.map((d) => deleteWrite(d.path, false)));
      if (docs.length < BATCH) break;
    }
  }
}

/** Chạy một bước; trả `SKIPPED` khi helper của nhóm khác chưa có (research R12). */
export async function runStep(step: CleanupStep, uid: string, record: Doc, deps: DeletionDeps): Promise<StepResult> {
  const h = deps.helpers;
  const optional = async (fn: ((uid: string) => Promise<void>) | undefined): Promise<StepResult> => {
    if (!fn) return "SKIPPED";
    await fn(uid);
    return "DONE";
  };
  switch (step) {
    case "disableAuth":
      try {
        await deps.identity.setUserDisabled(uid, true);
      } catch (e) {
        if (!isUserNotFound(e)) throw e;
      }
      return "DONE";
    case "notifyGuests": {
      const notify = (record.notify ?? []) as NotifyEntry[];
      if (notify.length === 0) return "DONE";
      if (!h.sendNotification) return "SKIPPED";
      for (const n of notify) {
        await h.sendNotification({
          recipientUid: n.recipientUid,
          type: n.kind === "BOOKING" ? "BOOKING_CANCELLED" : "DROPIN_CHANGED",
          data: { [n.kind === "BOOKING" ? "bookingId" : "sessionId"]: n.id, reason: OWNER_DELETED_REASON },
          idempotencyKey: `${n.kind}_${n.id}_${n.recipientUid}_OWNER_DELETED`,
        });
      }
      return "DONE";
    }
    case "anonymizeReviews":
      return await optional(h.anonymizeReviews);
    case "anonymizeCommunity":
      return await optional(h.anonymizeCommunity);
    case "anonymizeBookings":
      return await optional(h.anonymizeBookings);
    case "deleteOwnerData":
      return await optional(h.deleteOwnerApplications);
    case "deleteSubcollections":
    case "finalSweep":
      await deleteSubcollections(uid, deps);
      return "DONE";
    case "deleteAvatars":
      await deps.storage.removeAvatars(await deps.storage.listAvatarPaths(uid));
      return "DONE";
    case "deleteUserDoc":
      await deps.firestore.commit([deleteWrite(`users/${uid}`, false)]);
      return "DONE";
    case "deleteAuthUser":
      await deps.identity.deleteUser(uid);
      return "DONE";
  }
}

/** Ghi một bước bằng field mask `steps.<tên>` để hai lần chạy song song không ghi đè bước của nhau. */
export async function markStep(
  uid: string,
  step: CleanupStep,
  value: "DONE" | "SKIPPED" | "FAILED",
  deps: DeletionDeps,
  extra: Doc = {},
): Promise<void> {
  const w = updateWrite(`accountDeletions/${uid}`, { steps: { [step]: value }, ...extra });
  w.updateMask = { fieldPaths: [`steps.${step}`, ...Object.keys(extra)] };
  await deps.firestore.commit([w]);
}

export type CleanupOutcome = "DONE" | "PENDING" | "FAILED" | "NOT_FOUND";

/**
 * Chạy các bước chưa `DONE`/`SKIPPED` theo thứ tự cố định tới khi xong, gặp lỗi (ghi `FAILED`, `lastError` chỉ có
 * tên bước và loại lỗi) hoặc quá `deadline` (epoch ms).
 */
export async function runCleanup(uid: string, deadline: number, deps: DeletionDeps): Promise<CleanupOutcome> {
  const record = await deps.firestore.get(`accountDeletions/${uid}`);
  if (!record) return "NOT_FOUND";
  if (record.state === "DONE") return "DONE";
  const attempts = Number(record.attempts ?? 0) + 1;
  await deps.firestore.commit([updateWrite(`accountDeletions/${uid}`, { attempts })]);
  if (attempts >= 20) console.error(`[delete-account] accountDeletions/${uid} đã chạy ${attempts} lần, cần xử lý tay`);

  const steps = (record.steps ?? {}) as Record<string, string>;
  for (const step of CLEANUP_STEPS) {
    if (steps[step] === "DONE" || steps[step] === "SKIPPED") continue;
    if (Date.now() > deadline) return "PENDING";
    try {
      await markStep(uid, step, await runStep(step, uid, record, deps), deps);
    } catch (e) {
      const kind = e instanceof Error ? e.name : "Unknown";
      await markStep(uid, step, "FAILED", deps, { lastError: `${step}:${kind}` });
      return "FAILED";
    }
  }
  const done = updateWrite(`accountDeletions/${uid}`, { state: "DONE" }, ["completedAt"]);
  await deps.firestore.commit([done]);
  return "DONE";
}
