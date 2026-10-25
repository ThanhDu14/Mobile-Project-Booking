// D1–D4, A1 của specs/011-account-profile/contracts/security-rules.md §4 (spec 011, A).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, orderBy, query, setDoc, where } from 'firebase/firestore';
import { createTestEnv } from './setup';
import { seedDistrict, seedUser } from './fixtures';

let env: RulesTestEnvironment;

const player = () => env.authenticatedContext('player1', { appRole: 'PLAYER' }).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/player1'), seedUser('player1'));
    await setDoc(doc(db, 'districts/q1'), seedDistrict('Quận 1', 1));
    await setDoc(doc(db, 'districts/q-cu'), seedDistrict('Quận cũ', 99, false));
    await setDoc(doc(db, 'accountDeletions/player1'), { state: 'COMMITTED' });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('districts/{id}', () => {
  it('D1: người đã khởi tạo list được danh mục đang hoạt động', async () => {
    await assertSucceeds(getDocs(query(collection(player(), 'districts'),
      where('isActive', '==', true), orderBy('sortOrder'))));
  });

  it('D1b: vẫn get được khu vực đã ngừng để hiện tên', async () => {
    await assertSucceeds(getDoc(doc(player(), 'districts/q-cu')));
  });

  it('D2: chưa có appRole thì không get được', async () => {
    await assertFails(getDoc(doc(env.authenticatedContext('newbie').firestore(), 'districts/q1')));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'districts/q1')));
  });

  it('D3: người chơi không ghi được', async () => {
    await assertFails(setDoc(doc(player(), 'districts/q99'), seedDistrict('Quận 99', 99)));
  });

  it('D4: admin ghi được', async () => {
    const db = env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();
    await assertSucceeds(setDoc(doc(db, 'districts/q99'), seedDistrict('Quận 99', 99)));
  });
});

describe('accountDeletions/{uid}', () => {
  it('A1: chủ tài khoản không đọc được tiến trình xóa của mình', async () => {
    await assertFails(getDoc(doc(player(), 'accountDeletions/player1')));
  });

  it('A1b: không client nào ghi được', async () => {
    await assertFails(setDoc(doc(player(), 'accountDeletions/player1'), { state: 'DONE' }));
    const admin = env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();
    await assertFails(setDoc(doc(admin, 'accountDeletions/player1'), { state: 'DONE' }));
  });
});
