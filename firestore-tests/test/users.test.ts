// U1–U18 của specs/010-account-auth/contracts/security-rules.md §3 (spec 010, A).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

// Document như on-signup tạo (data-model §2).
function seedUser(uid: string, overrides: Record<string, unknown> = {}) {
  return {
    displayName: 'Lê Hoàng Nam',
    email: `${uid}@example.com`,
    phone: null,
    avatarPath: null,
    level: null,
    favoriteDistrictIds: [],
    role: 'PLAYER',
    ownerStatus: 'NONE',
    accountStatus: 'ACTIVE',
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
    termsAcceptedAt: Timestamp.fromMillis(1_760_000_000_000),
    createdAt: Timestamp.fromMillis(1_760_000_000_000),
    updatedAt: Timestamp.fromMillis(1_760_000_000_000),
    ...overrides,
  };
}

const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/player1'), seedUser('player1'));
    await setDoc(doc(db, 'users/player2'), seedUser('player2'));
    await setDoc(doc(db, 'users/admin1'), seedUser('admin1', { role: 'ADMIN' }));
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('users/{uid}: đọc', () => {
  it('U1: chủ tài khoản đọc được document của mình', async () => {
    await assertSucceeds(getDoc(doc(player(), 'users/player1')));
  });

  it('U2: người chơi khác không đọc được', async () => {
    await assertFails(getDoc(doc(player('player2'), 'users/player1')));
  });

  it('U3: chưa đăng nhập không đọc được', async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'users/player1')));
  });

  it('U4: đã đăng nhập Firebase nhưng chưa có claim appRole thì không đọc được của mình', async () => {
    const db = env.authenticatedContext('player1').firestore();
    await assertFails(getDoc(doc(db, 'users/player1')));
  });

  it('U5: admin get và list được', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'users/player1')));
    await assertSucceeds(getDocs(collection(admin(), 'users')));
  });

  it('U6: người chơi không list được', async () => {
    await assertFails(getDocs(collection(player(), 'users')));
  });
});

describe('users/{uid}: tạo và xóa', () => {
  it('U7: chủ tài khoản không tự tạo document (kể cả đủ trường)', async () => {
    const db = env.authenticatedContext('newbie', { appRole: 'PLAYER' }).firestore();
    await assertFails(setDoc(doc(db, 'users/newbie'), {
      ...seedUser('newbie'),
      termsAcceptedAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
  });

  it('U17: chủ tài khoản không tự xóa document', async () => {
    await assertFails(deleteDoc(doc(player(), 'users/player1')));
  });
});

describe('users/{uid}: sửa', () => {
  const me = () => doc(player(), 'users/player1');

  it('U8: sửa displayName với updatedAt = serverTimestamp() được phép', async () => {
    await assertSucceeds(updateDoc(me(), { displayName: 'Nam Lê', updatedAt: serverTimestamp() }));
  });

  it('U8: sửa các trường người dùng tự sửa được', async () => {
    await assertSucceeds(updateDoc(me(), {
      level: 'INTERMEDIATE',
      favoriteDistrictIds: ['q1'],
      avatarPath: 'avatars/player1.jpg',
      fcmTokens: ['token'],
      notificationPrefs: { booking: false },
      updatedAt: serverTimestamp(),
    }));
  });

  it.each([
    ['U9', { role: 'ADMIN' }],
    ['U10', { ownerStatus: 'APPROVED' }],
    ['U11', { accountStatus: 'LOCKED' }],
    ['U12', { walletBalance: 1_000_000 }],
    ['U13', { termsAcceptedAt: serverTimestamp() }],
  ])('%s: chủ tài khoản không sửa được trường 🔒 %o', async (_id, patch) => {
    await assertFails(updateDoc(me(), { ...patch, updatedAt: serverTimestamp() }));
  });

  it('U11: tài khoản bị khóa không tự mở khóa', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'users/player1'), { accountStatus: 'LOCKED' });
    });
    await assertFails(updateDoc(me(), { accountStatus: 'ACTIVE', updatedAt: serverTimestamp() }));
  });

  it.each([['A'], ['x'.repeat(51)], [42]])('U14: displayName không hợp lệ %o bị chặn', async (displayName) => {
    await assertFails(updateDoc(me(), { displayName, updatedAt: serverTimestamp() }));
  });

  it('U14: displayName đúng 2 và đúng 50 ký tự được phép', async () => {
    await assertSucceeds(updateDoc(me(), { displayName: 'An', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(me(), { displayName: 'x'.repeat(50), updatedAt: serverTimestamp() }));
  });

  it('U15: updatedAt không phải giờ server bị chặn', async () => {
    await assertFails(updateDoc(me(), { displayName: 'Nam Lê', updatedAt: Timestamp.now() }));
    await assertFails(updateDoc(me(), { displayName: 'Nam Lê' }));
  });

  it('U16: admin sửa accountStatus trực tiếp bị chặn (phải qua set-account-status)', async () => {
    await assertFails(updateDoc(doc(admin(), 'users/player1'), {
      accountStatus: 'LOCKED',
      updatedAt: serverTimestamp(),
    }));
  });

  it('U18: người chơi không sửa document của người khác', async () => {
    await assertFails(updateDoc(doc(player('player2'), 'users/player1'), {
      displayName: 'Bị sửa',
      updatedAt: serverTimestamp(),
    }));
  });

  it('chưa có claim appRole thì không sửa được document của mình', async () => {
    const db = env.authenticatedContext('player1').firestore();
    await assertFails(updateDoc(doc(db, 'users/player1'), { displayName: 'Nam Lê', updatedAt: serverTimestamp() }));
  });
});
