import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerCreateVoucher } from "../owner-create-voucher/handler.ts";
import * as fs from "./firestore.ts";
import { processPromotionFanouts, publishDueVouchers } from "./promotion-notification.ts";
import { runDeliveryWorker, type DeliveryStore } from "./notification-worker.ts";
import { bearer, emulatorToken } from "./testing.ts";

const enabled = Deno.env.get("FIREBASE_PROJECT_ID")?.startsWith("demo-") &&
  !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const test = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !enabled, fn });
const id = () => crypto.randomUUID();
const notify = (uid: string) => fs.query("notifications", [], { parent: `users/${uid}` });

async function setup() {
  const owner = id(), venueId = id();
  await fs.commit([
    fs.createWrite(`users/${owner}`, { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" }),
    fs.createWrite(`venues/${venueId}`, { status: "ACTIVE", ownerId: owner }),
  ]);
  const voucher = {
    venueId, code: "P" + id().replaceAll("-", "").slice(0, 12).toUpperCase(), type: "FIXED",
    value: 10_000, maxDiscount: 10_000, minOrderTotal: 10_000_000, usageLimit: 4, perUserLimit: 1, isActive: true,
    validFrom: new Date(Date.now() - 60_000).toISOString(), validTo: new Date(Date.now() + 3_600_000).toISOString(),
  };
  const path = `venues/${venueId}/vouchers/${voucher.code}`;
  const token = emulatorToken({ sub: owner, user_id: owner, appRole: "OWNER" });
  const command = async (body = {}) => {
    const response = await handleOwnerCreateVoucher(bearer(token, { body: JSON.stringify({ ...voucher, ...body }) }));
    assertEquals(response.status, 200, await response.text());
  };
  const jobs = () => fs.query("notificationFanouts", [{ field: "venueId", op: "EQUAL", value: venueId }]);
  return { venueId, voucher, path, command, jobs };
}

test("P2 checkout eligibility: includes reserved usage, pages followers, skips late/unfollowed/ineligible and honors prefs", async () => {
  const fixture = await setup();
  const { venueId, voucher, path, command, jobs } = fixture;
  const [allowed, optedOut, used, gone, late, inactive, deleting, owner, nonFollower] =
    ["allowed", "opted-out", "used", "gone", "late", "inactive", "deleting", "owner", "non-follower"].map(tag => `z-${tag}-${id()}`);
  const recipients = [allowed, optedOut, used, gone, late, inactive, deleting, owner, nonFollower];
  const before = new Date(Date.now() - 60_000);
  await fs.commit([
    ...recipients.map(uid => fs.createWrite(`users/${uid}`, {
      role: uid === owner ? "OWNER" : "PLAYER", accountStatus: uid === inactive ? "LOCKED" : "ACTIVE",
      ...(uid === allowed ? { notificationPrefs: { PROMOTION: true } } : {}),
      ...(uid === deleting ? { deletionRequestedAt: before } : {}),
      fcmTokens: [`demo-token-${uid}`],
    })),
    ...recipients.filter(uid => uid !== late && uid !== nonFollower).map(uid =>
      fs.createWrite(`users/${uid}/favorites/${venueId}`, { venueId, createdAt: before })),
    ...Array.from({ length: 100 }, (_, i) =>
      fs.createWrite(`users/a-${id()}-${i}/favorites/${venueId}`, { venueId, createdAt: before })),
  ]);
  await command();
  await command();
  const publishedAt = (await fs.get(path))?.publishedAt;
  assert(publishedAt instanceof Date);
  assertEquals((await jobs()).length, 1);
  await fs.commit([
    fs.updateWrite(path, { usedCount: 1 }),
    fs.createWrite(`${path}/userUsage/${used}`, { userId: used, count: 1 }),
    fs.createWrite(`${path}/redemptions/demo-hold`, { userId: used, status: "RESERVED" }),
    fs.createWrite(`users/${late}/favorites/${venueId}`, { venueId, createdAt: new Date(publishedAt.getTime() + 1) }),
    fs.deleteWrite(`users/${gone}/favorites/${venueId}`),
  ]);
  const store: DeliveryStore = {
    ...fs,
    query: (collection, filters, options) => collection === "notificationFanouts"
      ? fs.query(collection, [...filters, { field: "venueId", op: "EQUAL", value: venueId }], options)
      : collection === "notificationDeliveries"
      ? fs.query(collection, [...filters, { field: "recipientUid", op: "IN", value: recipients }], options)
      : fs.query(collection, filters, options),
  };
  assertEquals(await processPromotionFanouts(store, new Date()), 1);
  assertEquals((await jobs())[0].data.state, "PENDING");
  await Promise.all([processPromotionFanouts(store, new Date()), processPromotionFanouts(store, new Date())]);
  assertEquals((await jobs())[0].data.state, "DONE");
  await command({ action: "SET_ACTIVE", isActive: false });
  await command({ action: "SET_ACTIVE", isActive: true });
  await command({ action: "UPDATE", value: 20_000 });
  assertEquals((await fs.get(path))?.publishedAt, publishedAt);
  assertEquals((await jobs()).length, 1);

  for (const uid of recipients) {
    const rows = await notify(uid);
    assertEquals(rows.map(row => row.data.data), [allowed, optedOut].includes(uid) ? [{ venueId, code: voucher.code }] : []);
    if (rows.length) assertEquals(rows[0].data.type, "PROMOTION");
  }
  const sends: string[] = [];
  await runDeliveryWorker(store, (_token, { recipientUid }) => {
    sends.push(recipientUid);
    return Promise.resolve({ kind: "accepted" });
  });
  assertEquals(sends, [allowed]);
});

test("P2 delayed cron: active in the valid window once, concurrently, with no legacy backfill or exhausted delivery", async () => {
  const { venueId, voucher, path, command, jobs } = await setup();
  const due = new Date(Date.now() + 120_000);
  const later = new Date(due.getTime() + 1);
  const uid = id();
  await fs.commit([
    fs.createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" }),
    fs.createWrite(`users/${uid}/favorites/${venueId}`, { venueId, createdAt: new Date() }),
    fs.createWrite(`venues/${venueId}/vouchers/LEGACY`, {
      code: "LEGACY", isActive: true, validFrom: new Date(Date.now() - 60_000), validTo: new Date(voucher.validTo),
    }),
  ]);
  await command({ validFrom: due.toISOString(), validTo: later.toISOString() });
  assertEquals((await fs.get(path))?.publishedAt, null);
  assertEquals(await publishDueVouchers(fs, new Date(due.getTime() - 1)), 0);
  const results = await Promise.all([publishDueVouchers(fs, due), publishDueVouchers(fs, due)]);
  assertEquals(results.reduce((sum, n) => sum + n, 0), 1);
  assertEquals((await fs.get(path))?.publishedAt, due);
  assertEquals((await fs.get(`venues/${venueId}/vouchers/LEGACY`))?.publishedAt, undefined);
  assertEquals((await jobs()).length, 1);

  const store = { ...fs, query: (collection: string, filters: fs.QueryFilter[], options?: fs.QueryOptions) =>
    fs.query(collection, [...filters, { field: "venueId", op: "EQUAL", value: venueId }], options) };
  await fs.commit([fs.updateWrite(path, { usedCount: voucher.usageLimit })]);
  await processPromotionFanouts(store, later);
  assertEquals(await notify(uid), []);
  assertEquals((await jobs())[0].data.state, "DONE");
});
