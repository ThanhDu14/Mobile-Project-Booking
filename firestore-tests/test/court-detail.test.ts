// VD-1 → VD-10 của specs/030-court-detail/contracts/security-rules.md §2 (spec 030, A).
// Spec 030 chỉ đọc: Rules của `venues`, `courts`, `priceRules` thuộc D, `reviews` thuộc C. File này kiểm chứng
// Rules đề nghị ở research R10; ca nào còn đỏ do chờ D/C sửa Rules thì tên ghi [BLOCKED: D] / [BLOCKED: C].
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

const VENUES = ['v-active', 'v-hidden', 'v-locked', 'v-deleted'] as const;
const STATUS: Record<(typeof VENUES)[number], string> = {
  'v-active': 'ACTIVE',
  'v-hidden': 'HIDDEN',
  'v-locked': 'LOCKED',
  'v-deleted': 'DELETED',
};

function venue(status: string) {
  return {
    ownerId: 'owner1',
    name: `Sân ${status}`,
    nameKeywords: ['san'],
    address: '1 Đường Thử',
    districtId: 'q1',
    openTime: '0500',
    closeTime: '2400',
    phone: '0901234567',
    amenityIds: [],
    photoPaths: [],
    courtCount: 1,
    minPricePerHour: 100000,
    acceptsRecurring: true,
    ratingAvg: 0,
    ratingCount: 0,
    status,
  };
}

function review(id: string, status: 'VISIBLE' | 'HIDDEN', createdAtMs: number) {
  const at = Timestamp.fromMillis(createdAtMs);
  return {
    targetType: 'VENUE',
    bookingId: id,
    venueId: 'v-active',
    userId: 'player2',
    userSnapshot: { displayName: 'Người chơi', avatarPath: null },
    authorDeleted: false,
    ratings: { court: 5, cleanliness: 4, service: 4 },
    overall: 4.3,
    comment: 'Sân tốt',
    photoPaths: [],
    ownerReply: null,
    status,
    createdAt: at,
    updatedAt: at,
  };
}

const guest = () => env.unauthenticatedContext().firestore();
const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const id of VENUES) {
      await setDoc(doc(db, `venues/${id}`), venue(STATUS[id]));
      await setDoc(doc(db, `venues/${id}/courts/c1`), { name: 'Sân 1', surfaceType: 'PU', isActive: true, sortOrder: 1 });
      await setDoc(doc(db, `venues/${id}/priceRules/r1`), {
        daysOfWeek: [1, 2, 3, 4, 5],
        startTime: '0500',
        endTime: '2400',
        pricePerHour: 100000,
        label: 'NORMAL',
      });
    }
    await setDoc(doc(db, 'reviews/rv-visible'), review('rv-visible', 'VISIBLE', 1_760_000_000_000));
    await setDoc(doc(db, 'reviews/rv-hidden'), review('rv-hidden', 'HIDDEN', 1_760_000_100_000));
  });
});

afterAll(async () => {
  await env.cleanup();
});

const latestReviews = (db: ReturnType<typeof guest>) =>
  query(
    collection(db, 'reviews'),
    where('venueId', '==', 'v-active'),
    where('status', '==', 'VISIBLE'),
    orderBy('createdAt', 'desc'),
    limit(3),
  );

describe('Chi tiết sân – quyền đọc (spec 030)', () => {
  it('VD-1: khách get venues/v-active và đọc courts, priceRules của nó', async () => {
    const db = guest();
    await assertSucceeds(getDoc(doc(db, 'venues/v-active')));
    await assertSucceeds(getDocs(collection(db, 'venues/v-active/courts')));
    await assertSucceeds(getDocs(collection(db, 'venues/v-active/priceRules')));
  });

  it('VD-1b [BLOCKED: D]: khách đọc courts, priceRules của cơ sở tạm ngừng (R10: read if true)', async () => {
    const db = guest();
    await assertSucceeds(getDocs(collection(db, 'venues/v-hidden/courts')));
    await assertSucceeds(getDocs(collection(db, 'venues/v-hidden/priceRules')));
  });

  it('VD-2: khách get venues/v-hidden, v-locked bị chặn', async () => {
    const db = guest();
    await assertFails(getDoc(doc(db, 'venues/v-hidden')));
    await assertFails(getDoc(doc(db, 'venues/v-locked')));
  });

  it('VD-3 [BLOCKED: D]: khách get venues/v-missing được, exists == false', async () => {
    const snap = await assertSucceeds(getDoc(doc(guest(), 'venues/v-missing')));
    expect(snap.exists()).toBe(false);
  });

  it('VD-4 [BLOCKED: D]: người chơi đã khởi tạo get v-hidden, v-locked, v-deleted', async () => {
    const db = player();
    await assertSucceeds(getDoc(doc(db, 'venues/v-hidden')));
    await assertSucceeds(getDoc(doc(db, 'venues/v-locked')));
    await assertSucceeds(getDoc(doc(db, 'venues/v-deleted')));
  });

  it('VD-5: người chơi list venues không lọc status bị chặn', async () => {
    await assertFails(getDocs(collection(player(), 'venues')));
  });

  it('VD-6 [BLOCKED: C]: khách list 3 đánh giá VISIBLE mới nhất của v-active', async () => {
    const snap = await assertSucceeds(getDocs(latestReviews(guest())));
    expect(snap.docs.map((d) => d.id)).toEqual(['rv-visible']);
  });

  it('VD-6b: người chơi list 3 đánh giá VISIBLE mới nhất, không có đánh giá HIDDEN', async () => {
    const snap = await assertSucceeds(getDocs(latestReviews(player())));
    expect(snap.docs.map((d) => d.id)).toEqual(['rv-visible']);
  });

  it('VD-7: khách list reviews của v-active không lọc status bị chặn', async () => {
    await assertFails(getDocs(query(collection(guest(), 'reviews'), where('venueId', '==', 'v-active'))));
  });

  it('VD-8: người chơi get đánh giá HIDDEN của người khác bị chặn', async () => {
    await assertFails(getDoc(doc(player(), 'reviews/rv-hidden')));
  });

  describe('VD-9: khách và người chơi không ghi được', () => {
    for (const [who, ctx] of [
      ['khách', guest],
      ['người chơi', () => player()],
    ] as const) {
      it(`${who} ghi venues, courts, priceRules, reviews`, async () => {
        const db = ctx();
        await assertFails(setDoc(doc(db, 'venues/v-new'), venue('ACTIVE')));
        await assertFails(updateDoc(doc(db, 'venues/v-active'), { name: 'Đổi tên' }));
        await assertFails(deleteDoc(doc(db, 'venues/v-active')));
        await assertFails(setDoc(doc(db, 'venues/v-active/courts/c2'), { name: 'Sân 2', surfaceType: 'PU', isActive: true }));
        await assertFails(updateDoc(doc(db, 'venues/v-active/priceRules/r1'), { pricePerHour: 1 }));
        await assertFails(setDoc(doc(db, 'reviews/rv-new'), review('rv-new', 'VISIBLE', 1_760_000_200_000)));
        await assertFails(updateDoc(doc(db, 'reviews/rv-visible'), { comment: 'Sửa' }));
      });
    }
  });

  it('VD-10 [BLOCKED: D]: khách get venues/v-deleted được, status == DELETED', async () => {
    const snap = await assertSucceeds(getDoc(doc(guest(), 'venues/v-deleted')));
    expect(snap.get('status')).toBe('DELETED');
  });
});
