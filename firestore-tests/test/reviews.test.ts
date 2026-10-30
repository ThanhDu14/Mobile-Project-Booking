// R1–R9 của specs/070-review-favorite/contracts/security-rules.md §1 (spec 070, C).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

// Đúng schema data-model §1. reviewId = bookingId.
function review(bookingId: string, userId: string | null, status: 'VISIBLE' | 'HIDDEN') {
  const at = Timestamp.fromMillis(1_760_000_000_000);
  return {
    targetType: 'VENUE',
    bookingId,
    venueId: 'venueA',
    userId,
    userSnapshot: { displayName: 'Người chơi', avatarPath: null },
    authorDeleted: false,
    ratings: { court: 5, cleanliness: 4, service: 3 },
    overall: 4.0,
    starBucket: 4,
    comment: 'Sân sạch, đèn sáng',
    photoPaths: [],
    hasPhotos: false,
    ownerReply: null,
    hasOwnerReply: false,
    status,
    completedAt: at,
    editedAt: null,
    createdAt: at,
    updatedAt: at,
  };
}

const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const owner = (uid = 'owner1') => env.authenticatedContext(uid, { appRole: 'OWNER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();
const guest = () => env.unauthenticatedContext().firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'reviews/b-visible'), review('b-visible', 'player2', 'VISIBLE'));
    await setDoc(doc(db, 'reviews/b-hidden'), review('b-hidden', 'player2', 'HIDDEN'));
    await setDoc(doc(db, 'reviews/b-mine-hidden'), review('b-mine-hidden', 'player1', 'HIDDEN'));
    await setDoc(doc(db, 'venues/venueA'), { name: 'Sân A', status: 'ACTIVE', ownerId: 'owner1', ratingAvg: 4.0, ratingCount: 1 });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('reviews', () => {
  it('R1: người chơi đọc và liệt kê đánh giá VISIBLE của một sân', async () => {
    const db = player();
    await assertSucceeds(getDoc(doc(db, 'reviews/b-visible')));
    await assertSucceeds(
      getDocs(query(collection(db, 'reviews'), where('venueId', '==', 'venueA'), where('status', '==', 'VISIBLE'))),
    );
  });

  it('R1: liệt kê không lọc status thì bị chặn (có thể lẫn đánh giá bị ẩn)', async () => {
    await assertFails(getDocs(query(collection(player(), 'reviews'), where('venueId', '==', 'venueA'))));
  });

  it('R2: người chơi không đọc được đánh giá HIDDEN của người khác', async () => {
    await assertFails(getDoc(doc(player(), 'reviews/b-hidden')));
  });

  it('R3: người viết đọc được đánh giá HIDDEN của chính mình, kể cả "Đánh giá của tôi"', async () => {
    const db = player('player1');
    await assertSucceeds(getDoc(doc(db, 'reviews/b-mine-hidden')));
    await assertSucceeds(getDocs(query(collection(db, 'reviews'), where('userId', '==', 'player1'))));
  });

  it('R4: admin đọc được đánh giá HIDDEN', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'reviews/b-hidden')));
    await assertSucceeds(getDocs(query(collection(admin(), 'reviews'), where('status', '==', 'HIDDEN'))));
  });

  it('R5: người chơi không tự tạo đánh giá trực tiếp', async () => {
    await assertFails(setDoc(doc(player('player1'), 'reviews/b-new'), review('b-new', 'player1', 'VISIBLE')));
  });

  it('R6: người viết không tự sửa điểm hay xóa đánh giá trực tiếp', async () => {
    const db = player('player1');
    await assertFails(updateDoc(doc(db, 'reviews/b-mine-hidden'), { ratings: { court: 5, cleanliness: 5, service: 5 } }));
    await assertFails(deleteDoc(doc(db, 'reviews/b-mine-hidden')));
  });

  it('R7: chủ sân không tự ghi ownerReply trực tiếp', async () => {
    await assertSucceeds(getDoc(doc(owner(), 'reviews/b-visible')));
    await assertFails(
      updateDoc(doc(owner(), 'reviews/b-visible'), {
        ownerReply: { text: 'Cảm ơn bạn', repliedAt: Timestamp.now(), editedAt: null },
        hasOwnerReply: true,
      }),
    );
  });

  it('R8: người chơi không ghi được điểm của cơ sở', async () => {
    await assertFails(updateDoc(doc(player(), 'venues/venueA'), { ratingAvg: 5, ratingCount: 99 }));
  });

  it('R9: khách chưa đăng nhập không đọc được đánh giá', async () => {
    const db = guest();
    await assertFails(getDoc(doc(db, 'reviews/b-visible')));
    await assertFails(getDocs(query(collection(db, 'reviews'), where('status', '==', 'VISIBLE'))));
  });
});
