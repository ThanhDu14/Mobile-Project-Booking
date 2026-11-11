// Hàm hỗ trợ test Edge Function `review` trên Firebase Emulator (spec 070). Không dùng trong function.
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken } from "../_shared/testing.ts";
import { handleReview, type Notification, type ReviewDeps, type ReviewStorage } from "./handler.ts";

export const DAY = 24 * 60 * 60 * 1000;
export const NOW = new Date("2026-11-06T10:00:00Z");
export const RATINGS = { court: 5, cleanliness: 4, service: 3 }; // overall 4.0

/** Bucket `review-photos` giả: tập path đang có và danh sách path đã bị xóa. */
export class FakeStorage implements ReviewStorage {
  readonly objects = new Set<string>();
  readonly removed: string[] = [];

  exists(path: string): Promise<boolean> {
    return Promise.resolve(this.objects.has(path));
  }

  list(prefix: string): Promise<string[]> {
    return Promise.resolve([...this.objects].filter((p) => p.startsWith(prefix)));
  }

  remove(paths: string[]): Promise<number> {
    let n = 0;
    for (const p of paths) {
      if (this.objects.delete(p)) {
        this.removed.push(p);
        n++;
      }
    }
    return Promise.resolve(n);
  }

  reset(paths: string[] = []): void {
    this.objects.clear();
    this.removed.length = 0;
    paths.forEach((p) => this.objects.add(p));
  }
}

export const sent: Notification[] = [];
export const storage = new FakeStorage();

export const deps: ReviewDeps = {
  firestore,
  storage,
  now: () => NOW,
  notify: (n) => {
    sent.push(n);
    return Promise.resolve();
  },
};

export const tokenOf = (uid: string, appRole = "PLAYER") => emulatorToken({ sub: uid, user_id: uid, appRole });

// deno-lint-ignore no-explicit-any
export async function call(uid: string, body: unknown, appRole = "PLAYER", d: ReviewDeps = deps): Promise<{ status: number; body: any }> {
  const res = await handleReview(bearer(tokenOf(uid, appRole), { body: JSON.stringify(body) }), d);
  return { status: res.status, body: await res.json() };
}

export const done = (daysAgo = 1) => ({ completedAt: new Date(NOW.getTime() - daysAgo * DAY) });

/**
 * player1, player2, owner1 (đã duyệt, sở hữu venueA), owner2 (đã duyệt, sở hữu venueB), locked1;
 * venueA chưa có đánh giá; đơn của player1 tại venueA theo `bookings`.
 */
export async function seed(bookings: Record<string, Record<string, unknown>> = {}, photos: string[] = []) {
  await clearEmulators();
  sent.length = 0;
  storage.reset(photos);
  await commit([
    createWrite("users/player1", { displayName: "Nam", avatarPath: "player1/a.jpg", accountStatus: "ACTIVE" }),
    createWrite("users/player2", { displayName: "Lan", avatarPath: null, accountStatus: "ACTIVE" }),
    createWrite("users/owner1", { displayName: "Chủ A", accountStatus: "ACTIVE", ownerStatus: "APPROVED" }),
    createWrite("users/owner2", { displayName: "Chủ B", accountStatus: "ACTIVE", ownerStatus: "APPROVED" }),
    createWrite("users/owner3", { displayName: "Chủ C", accountStatus: "ACTIVE", ownerStatus: "PENDING" }),
    createWrite("users/locked1", { displayName: "Khóa", accountStatus: "LOCKED" }),
    createWrite("venues/venueA", { name: "Sân A", ownerId: "owner1", status: "ACTIVE", ratingAvg: 0, ratingCount: 0 }),
    createWrite("venues/venueB", { name: "Sân B", ownerId: "owner2", status: "HIDDEN", ratingAvg: 0, ratingCount: 0 }),
    createWrite("venues/venueC", { name: "Sân C", ownerId: "owner3", status: "ACTIVE", ratingAvg: 0, ratingCount: 0 }),
    ...Object.entries(bookings).map(([id, data]) =>
      createWrite(`bookings/${id}`, { venueId: "venueA", userId: "player1", status: "COMPLETED", ...data })
    ),
  ]);
}

export const createBody = (bookingId: string, extra: Record<string, unknown> = {}) => ({
  action: "create",
  bookingId,
  ratings: RATINGS,
  comment: "Sân sạch, đèn sáng",
  photoPaths: [],
  ...extra,
});
