// F1–F4 của specs/070-review-favorite/contracts/security-rules.md §1 (spec 070, C).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  where,
} from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

// Đúng schema data-model §2: venueId = ID document, venueSnapshot, createdAt = serverTimestamp().
function favorite(venueId: string, overrides: Record<string, unknown> = {}) {
  return {
    venueId,
    venueSnapshot: { name: 'CLB Cầu Lông Trường Chinh 909', photoPath: null, address: '909/6 Trường Chinh' },
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const guest = () => env.unauthenticatedContext().firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/player2/favorites/venueB'), {
      ...favorite('venueB'),
      createdAt: Timestamp.fromMillis(1_760_000_000_000),
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('users/{uid}/favorites', () => {
  it('F1: chủ tài khoản tạo, đọc, liệt kê và xóa favorite đúng schema', async () => {
    const db = player('player1');
    const ref = doc(db, 'users/player1/favorites/venueA');
    await assertSucceeds(setDoc(ref, favorite('venueA')));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(getDocs(collection(db, 'users/player1/favorites')));
    await assertSucceeds(deleteDoc(ref));
  });

  it('F1: ghi lại cùng sân (bấm hai lần) vẫn được, không tạo bản trùng', async () => {
    const ref = doc(player('player1'), 'users/player1/favorites/venueA');
    await assertSucceeds(setDoc(ref, favorite('venueA')));
    await assertSucceeds(setDoc(ref, favorite('venueA')));
  });

  it('F2: người khác không đọc, liệt kê, ghi hay xóa được favorites của uid', async () => {
    const db = player('player1');
    await assertFails(getDoc(doc(db, 'users/player2/favorites/venueB')));
    await assertFails(getDocs(collection(db, 'users/player2/favorites')));
    await assertFails(setDoc(doc(db, 'users/player2/favorites/venueA'), favorite('venueA')));
    await assertFails(deleteDoc(doc(db, 'users/player2/favorites/venueB')));
  });

  it('F2: khách chưa đăng nhập không đọc hay ghi được', async () => {
    const db = guest();
    await assertFails(getDoc(doc(db, 'users/player2/favorites/venueB')));
    await assertFails(setDoc(doc(db, 'users/player2/favorites/venueA'), favorite('venueA')));
  });

  it('F3: venueId khác ID document thì bị chặn', async () => {
    const ref = doc(player('player1'), 'users/player1/favorites/venueA');
    await assertFails(setDoc(ref, favorite('venueOther')));
  });

  it('F3: thêm trường lạ ở document hoặc venueSnapshot thì bị chặn', async () => {
    const ref = doc(player('player1'), 'users/player1/favorites/venueA');
    await assertFails(setDoc(ref, favorite('venueA', { note: 'x' })));
    await assertFails(
      setDoc(ref, favorite('venueA', { venueSnapshot: { name: 'A', photoPath: null, address: 'B', ratingAvg: 5 } })),
    );
  });

  it('F3: createdAt không phải server timestamp thì bị chặn', async () => {
    const ref = doc(player('player1'), 'users/player1/favorites/venueA');
    await assertFails(setDoc(ref, favorite('venueA', { createdAt: Timestamp.fromMillis(1_760_000_000_000) })));
  });

  it('F4: truy vấn collection group favorites từ client bị chặn', async () => {
    const db = player('player1');
    await assertFails(getDocs(collectionGroup(db, 'favorites')));
    await assertFails(getDocs(query(collectionGroup(db, 'favorites'), where('venueId', '==', 'venueB'))));
  });
});
