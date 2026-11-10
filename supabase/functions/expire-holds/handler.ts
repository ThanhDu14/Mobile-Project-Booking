import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { deleteWrite, updateWrite } from "../_shared/firestore.ts";

export interface ExpireHoldsDeps {
  firestore?: Pick<typeof defaultFirestore, "query" | "runTransaction">;
  now?: () => Date;
  cronSecret?: string;
}

function defaults(): Required<ExpireHoldsDeps> {
  return {
    firestore: defaultFirestore,
    now: () => new Date(),
    cronSecret: Deno.env.get("CRON_SECRET") ?? "",
  };
}

function millis(value: unknown): number | null {
  if (value instanceof Date) return value.getTime();
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  if (value && typeof value === "object" && "_seconds" in value) {
    const seconds = Number((value as { _seconds: unknown })._seconds);
    return Number.isFinite(seconds) ? seconds * 1000 : null;
  }
  return null;
}

/** Server cleanup for elapsed 040 holds and 041 recurring groups. */
export async function handleExpireHolds(
  req: Request,
  injected: ExpireHoldsDeps = {},
): Promise<Response> {
  const deps = { ...defaults(), ...injected };
  if (req.method !== "POST") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  }
  const suppliedSecret = req.headers.get("x-cron-secret") ?? "";
  if (!deps.cronSecret || suppliedSecret !== deps.cronSecret) {
    return errorResponse(401, "UNAUTHENTICATED", "Thiếu hoặc sai CRON_SECRET");
  }

  try {
    const now = deps.now();
    const nowIso = now.toISOString();
    const candidates = await deps.firestore.query("bookings", [
      { field: "status", op: "EQUAL", value: "HOLD" },
    ], { limit: 200 });
    let expired = 0;
    let skipped = 0;

    for (const doc of candidates) {
      const expiry = millis(doc.data.holdExpiresAt);
      if (expiry === null || expiry > now.getTime()) {
        skipped++;
        continue;
      }
      const didExpire = await deps.firestore.runTransaction(async (tx) => {
        const current = await tx.get(`bookings/${doc.id}`);
        if (!current || current.status !== "HOLD") {
          return { result: false, writes: [] };
        }
        const currentExpiry = millis(current.holdExpiresAt);
        if (currentExpiry === null || currentExpiry > now.getTime()) {
          return { result: false, writes: [] };
        }

        const ids = new Set<string>();
        if (Array.isArray(current.slotIds)) {
          for (const id of current.slotIds) {
            if (typeof id === "string" && id.trim()) ids.add(id.trim());
          }
        }
        if (Array.isArray(current.items)) {
          for (const item of current.items) {
            if (item && typeof item === "object") {
              const id = (item as Record<string, unknown>).slotId;
              if (typeof id === "string" && id.trim()) ids.add(id.trim());
            }
          }
        }
        if (ids.size > 499) return { result: false, writes: [] };
        const ownedSlots: string[] = [];
        for (const id of ids) {
          const slot = await tx.get(`slots/${id}`);
          if (
            slot?.status === "HELD" && slot.bookingId === doc.id &&
            slot.holdBy === current.userId &&
            millis(slot.holdExpiresAt) !== null &&
            millis(slot.holdExpiresAt)! <= now.getTime()
          ) {
            ownedSlots.push(id);
          }
        }
        return {
          result: true,
          writes: [
            updateWrite(`bookings/${doc.id}`, {
              status: "EXPIRED",
              updatedAt: nowIso,
            }),
            ...ownedSlots.map((id) => deleteWrite(`slots/${id}`, false)),
          ],
        };
      });
      if (didExpire) expired++;
      else skipped++;
    }

    // 041 has one group with multiple session docs. Use its hold expiry as the
    // authority and transact group + sessions + only group-owned HELD slots together.
    const groups = await deps.firestore.query("recurringGroups", [
      { field: "status", op: "EQUAL", value: "HOLD" },
    ], { limit: 100 });
    for (const group of groups) {
      const expiry = millis(group.data.holdExpiresAt);
      if (expiry === null || expiry > now.getTime()) continue;
      const didExpire = await deps.firestore.runTransaction(async (tx) => {
        const currentGroup = await tx.get(`recurringGroups/${group.id}`);
        if (!currentGroup || currentGroup.status !== "HOLD") {
          return { result: 0, writes: [] };
        }
        const currentExpiry = millis(currentGroup.holdExpiresAt);
        if (currentExpiry === null || currentExpiry > now.getTime()) {
          return { result: 0, writes: [] };
        }
        const sessions = await tx.query("bookings", [
          { field: "recurringGroupId", op: "EQUAL", value: group.id },
        ], { limit: 200 });
        if (sessions.length >= 200) return { result: 0, writes: [] };
        const slotDocs = new Map<string, Record<string, unknown>>();
        for (const session of sessions) {
          const ids = Array.isArray(session.data.slotIds)
            ? session.data.slotIds
            : [];
          for (const id of ids) {
            if (typeof id !== "string" || !id.trim() || slotDocs.has(id)) {
              continue;
            }
            const slot = await tx.get(`slots/${id}`);
            if (slot) slotDocs.set(id, slot);
          }
        }
        const deletable = [...slotDocs].filter(([, slot]) =>
          slot.status === "HELD" && slot.bookingId === group.id &&
          slot.holdBy === currentGroup.userId &&
          millis(slot.holdExpiresAt) !== null &&
          millis(slot.holdExpiresAt)! <= now.getTime()
        ).map(([id]) => id);
        const writes = [
          updateWrite(`recurringGroups/${group.id}`, {
            status: "EXPIRED",
            updatedAt: nowIso,
          }),
          ...sessions.filter((s) => s.data.status === "HOLD")
            .map((s) =>
              updateWrite(`bookings/${s.id}`, {
                status: "EXPIRED",
                updatedAt: nowIso,
              })
            ),
          ...deletable.map((id) => deleteWrite(`slots/${id}`, false)),
        ];
        if (writes.length > 499) return { result: 0, writes: [] };
        return { result: sessions.length, writes };
      });
      if (didExpire) expired += didExpire;
      else skipped++;
    }

    // 040 creates only HELD slot documents before confirmation (no booking doc yet),
    // so clean remaining expired HOLD slots independently. Re-read inside the
    // transaction and require the exact same expired slot to avoid racing confirm.
    const heldSlots = await deps.firestore.query("slots", [
      { field: "status", op: "EQUAL", value: "HELD" },
    ], { limit: 500 });
    for (const candidate of heldSlots) {
      const expiry = millis(candidate.data.holdExpiresAt);
      if (expiry === null || expiry > now.getTime()) continue;
      const removed = await deps.firestore.runTransaction(async (tx) => {
        const slot = await tx.get(`slots/${candidate.id}`);
        if (!slot || slot.status !== "HELD") {
          return { result: false, writes: [] };
        }
        const currentExpiry = millis(slot.holdExpiresAt);
        if (currentExpiry === null || currentExpiry > now.getTime()) {
          return { result: false, writes: [] };
        }
        const writes: defaultFirestore.Write[] = [
          deleteWrite(`slots/${candidate.id}`, true),
        ];
        const bookingId = typeof slot.bookingId === "string"
          ? slot.bookingId
          : "";
        if (bookingId) {
          const booking = await tx.get(`bookings/${bookingId}`);
          if (
            booking?.status === "HOLD" &&
            millis(booking.holdExpiresAt) !== null &&
            millis(booking.holdExpiresAt)! <= now.getTime()
          ) {
            writes.push(
              updateWrite(`bookings/${bookingId}`, {
                status: "EXPIRED",
                updatedAt: nowIso,
              }),
            );
          }
        }
        return { result: true, writes };
      });
      if (removed) expired++;
    }

    return Response.json({
      success: true,
      scanned: candidates.length + groups.length + heldSlots.length,
      expired,
      skipped,
      timestamp: nowIso,
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
