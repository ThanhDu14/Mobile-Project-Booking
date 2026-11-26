import * as fs from "./firestore.ts";
import { activeRecipient, notificationInboxId, prepareNotification, sha256 } from "./notification-enqueue.ts";
import { checkVoucher, loadUserUsageCount, loadVoucher } from "./payment-voucher.ts";

const family = "promotion";
const type = "PROMOTION";
const pageSize = 100;
type Store = Pick<typeof fs, "query" | "runTransaction">;

/** A null marker opts a new voucher into publication. Existing documents have no marker. */
export async function preparePromotionPublication(
  voucher: Record<string, unknown>, venueId: string, code: string, now: Date,
): Promise<fs.Write | null> {
  if (voucher.publishedAt !== null || voucher.isActive !== true ||
    !(voucher.validFrom instanceof Date) || !(voucher.validTo instanceof Date) ||
    now < voucher.validFrom || now > voucher.validTo) return null;
  const identity = JSON.stringify([venueId, code, type]);
  const id = await sha256(JSON.stringify([family, identity]));
  return fs.createWrite(`notificationFanouts/${id}`, {
    family, identity, venueId, code, createdAt: now, state: "PENDING", after: null,
  });
}

export async function publishDueVouchers(store: Store, now: Date): Promise<number> {
  const rows = await store.query("vouchers", [
    { field: "isActive", op: "EQUAL", value: true },
    { field: "publishedAt", op: "EQUAL", value: null },
    { field: "validFrom", op: "LESS_THAN_OR_EQUAL", value: now },
    { field: "validTo", op: "GREATER_THAN_OR_EQUAL", value: now },
  ], { allDescendants: true, limit: 10, orderBy: [{ field: "validFrom" }, { field: "validTo" }] });
  let published = 0;
  for (const row of rows) {
    const [collection, venueId, vouchers, code, extra] = row.path.split("/");
    if (collection !== "venues" || vouchers !== "vouchers" || !venueId || !code || extra) continue;
    if (await store.runTransaction(async tx => {
      const current = await tx.get(row.path);
      if (!current) return false;
      const job = await preparePromotionPublication(current, venueId, code, now);
      if (!job) return false;
      tx.write(fs.updateWrite(row.path, { publishedAt: now }));
      tx.write(job);
      return true;
    })) published++;
  }
  return published;
}

export async function processPromotionFanouts(store: Store, now: Date): Promise<number> {
  const jobs = await store.query("notificationFanouts", [
    { field: "family", op: "EQUAL", value: family }, { field: "state", op: "EQUAL", value: "PENDING" },
  ], { limit: 5 });
  let pages = 0;
  for (const job of jobs) {
    if (await store.runTransaction(async tx => {
      const current = await tx.get(job.path);
      if (current?.state !== "PENDING") return false;
      const { venueId, code, identity, createdAt, after } = current;
      if (typeof venueId !== "string" || typeof code !== "string" || typeof identity !== "string" ||
        !(createdAt instanceof Date)) throw new Error("Invalid promotion fanout");
      const [voucher, venue] = await Promise.all([loadVoucher(tx, venueId, code), tx.get(`venues/${venueId}`)]);
      // The order minimum is evaluated by checkout when there is an actual order.
      if (voucher?.scope !== "VENUE" || venue?.status !== "ACTIVE" ||
        !checkVoucher(voucher, 0, Number.POSITIVE_INFINITY, now).ok) {
        tx.write(fs.updateWrite(job.path, { state: "DONE" }));
        return true;
      }
      const favorites = await tx.query("favorites", [{ field: "venueId", op: "EQUAL", value: venueId }], {
        allDescendants: true, limit: pageSize, orderBy: { field: "__name__" },
        ...(typeof after === "string" ? { startAfter: [{ referenceValue: fs.documentName(after) }] } : {}),
      });
      const writes: fs.Write[] = [];
      for (let i = 0; i < favorites.length; i += 8) {
        const batch = await Promise.all(favorites.slice(i, i + 8).map(async row => {
          const [collection, uid, favorites, id, extra] = row.path.split("/");
          if (collection !== "users" || favorites !== "favorites" || id !== venueId || extra ||
            !(row.data.createdAt instanceof Date) || row.data.createdAt > createdAt) return [];
          const user = await tx.get(`users/${uid}`);
          if (!activeRecipient(user) || user?.role !== "PLAYER") return [];
          const usage = await loadUserUsageCount(tx, voucher, uid);
          if (!checkVoucher(voucher, usage, Number.POSITIVE_INFINITY, now).ok) return [];
          const notificationId = await notificationInboxId(family, identity, uid, type);
          return await prepareNotification(tx, family, identity, uid, notificationId, {
            type, title: "Ưu đãi từ sân bạn theo dõi", body: `Sân bạn theo dõi vừa phát hành mã ${code}.`,
            data: { venueId, code },
          }, createdAt);
        }));
        writes.push(...batch.flat());
      }
      for (const write of writes) tx.write(write);
      tx.write(fs.updateWrite(job.path, {
        state: favorites.length < pageSize ? "DONE" : "PENDING", after: favorites.at(-1)?.path ?? null,
      }));
      return true;
    })) pages++;
  }
  return pages;
}
