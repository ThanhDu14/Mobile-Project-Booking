// H-1…H-15 của specs/020-court-search/contracts/security-rules-and-indexes.md §4 (spec 020 US4, A).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
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
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

const OLD = Timestamp.fromMillis(1_760_000_000_000);

/** Đúng schema data-model §2.1; mọi mốc thời gian là serverTimestamp(). */
function entry(overrides: Record<string, unknown> = {}) {
  return {
    keyword: 'Trường Chinh',
    districtId: 'q10',
    priceMin: null,
    priceMax: 100000,
    date: '20261120',
    startTime: '1900',
    endTime: '2100',
    amenityIds: ['parking'],
    sort: 'RATING',
    searchedAt: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const guest = () => env.unauthenticatedContext().firestore();
const uninitialized = (uid = 'player1') => env.authenticatedContext(uid).firestore();
const path = (uid: string, id: string) => `users/${uid}/searchHistory/${id}`;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const seeded = { ...entry(), searchedAt: OLD, createdAt: OLD, updatedAt: OLD };
    await setDoc(doc(db, path('player1', 'old')), seeded);
    await setDoc(doc(db, path('player2', 'theirs')), seeded);
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('users/{uid}/searchHistory', () => {
  it('H-1: chủ tạo mục hợp lệ với serverTimestamp', async () => {
    await assertSucceeds(setDoc(doc(player(), path('player1', 'h1')), entry()));
    // Chỉ có từ khóa, không bộ lọc nào: vẫn hợp lệ.
    await assertSucceeds(
      setDoc(
        doc(player(), path('player1', 'h1b')),
        entry({ districtId: null, priceMax: null, date: null, startTime: null, endTime: null, amenityIds: [] }),
      ),
    );
    // Giá do AI điền ("dưới 55k") là bội số 1.000.
    await assertSucceeds(setDoc(doc(player(), path('player1', 'h1c')), entry({ priceMax: 55000 })));
  });

  it('H-2: chủ đọc danh sách của mình theo searchedAt giảm dần, tối đa 10', async () => {
    const db = player();
    await assertSucceeds(
      getDocs(query(collection(db, 'users/player1/searchHistory'), orderBy('searchedAt', 'desc'), limit(10))),
    );
    await assertSucceeds(getDoc(doc(db, path('player1', 'old'))));
  });

  it('H-3, H-4: người khác không đọc, không xóa được lịch sử của chủ', async () => {
    const other = player('player2');
    await assertFails(getDoc(doc(other, path('player1', 'old'))));
    await assertFails(getDocs(collection(other, 'users/player1/searchHistory')));
    await assertFails(deleteDoc(doc(other, path('player1', 'old'))));
  });

  it('H-5: khách không đọc, không ghi được', async () => {
    await assertFails(getDoc(doc(guest(), path('player1', 'old'))));
    await assertFails(setDoc(doc(guest(), path('player1', 'g')), entry()));
  });

  it('H-6: không có trường lạ như vị trí (FR-014)', async () => {
    await assertFails(setDoc(doc(player(), path('player1', 'h6')), entry({ lat: 10.7, lng: 106.6 })));
  });

  it('H-7: keyword tối đa 100 ký tự', async () => {
    await assertSucceeds(setDoc(doc(player(), path('player1', 'h7a')), entry({ keyword: 'a'.repeat(100) })));
    await assertFails(setDoc(doc(player(), path('player1', 'h7b')), entry({ keyword: 'a'.repeat(101) })));
  });

  it('H-8: giá sai thứ tự hoặc không phải bội số 1.000', async () => {
    await assertFails(setDoc(doc(player(), path('player1', 'h8a')), entry({ priceMin: 200000, priceMax: 100000 })));
    await assertFails(setDoc(doc(player(), path('player1', 'h8b')), entry({ priceMax: 100500 })));
    await assertFails(setDoc(doc(player(), path('player1', 'h8c')), entry({ priceMax: -1000 })));
  });

  it('H-9: khung giờ phải đủ ba trường, bước 30 phút, kết thúc sau bắt đầu', async () => {
    await assertFails(setDoc(doc(player(), path('player1', 'h9a')), entry({ startTime: '1915' })));
    await assertFails(setDoc(doc(player(), path('player1', 'h9b')), entry({ endTime: '1900' })));
    await assertFails(setDoc(doc(player(), path('player1', 'h9c')), entry({ startTime: null, endTime: null })));
  });

  it('H-10: sort chỉ nhận RATING, PRICE_ASC, NEAREST', async () => {
    await assertFails(setDoc(doc(player(), path('player1', 'h10')), entry({ sort: 'DISTANCE' })));
  });

  it('H-11: searchedAt phải do máy chủ đặt', async () => {
    await assertFails(
      setDoc(doc(player(), path('player1', 'h11')), entry({ searchedAt: Timestamp.fromMillis(Date.now()) })),
    );
  });

  it('H-12, H-13: tìm lại cập nhật searchedAt và giữ createdAt; không đổi được createdAt', async () => {
    const ref = doc(player(), path('player1', 'old'));
    await assertSucceeds(updateDoc(ref, { searchedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(
      updateDoc(ref, { searchedAt: serverTimestamp(), updatedAt: serverTimestamp(), createdAt: serverTimestamp() }),
    );
  });

  it('H-14: chủ xóa từng mục và xóa tất cả bằng batch', async () => {
    const db = player();
    await assertSucceeds(setDoc(doc(db, path('player1', 'h14')), entry()));
    await assertSucceeds(deleteDoc(doc(db, path('player1', 'h14'))));
    const batch = writeBatch(db);
    batch.delete(doc(db, path('player1', 'old')));
    await assertSucceeds(batch.commit());
  });

  it('H-15: tài khoản chưa khởi tạo (không có appRole) không ghi được', async () => {
    await assertFails(setDoc(doc(uninitialized(), path('player1', 'h15')), entry()));
  });
});
