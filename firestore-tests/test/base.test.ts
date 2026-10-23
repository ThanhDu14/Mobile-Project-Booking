import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
});

afterAll(async () => {
  await env.cleanup();
});

// Rules nền: collection chưa có match thì mọi vai trò đều bị chặn.
describe('Rules nền', () => {
  it('chặn người chưa đăng nhập đọc', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'anyCollection/doc1')));
  });

  it('chặn người chơi đã đăng nhập ghi vào collection chưa khai báo', async () => {
    const db = env.authenticatedContext('player1', { appRole: 'PLAYER' }).firestore();
    await assertFails(setDoc(doc(db, 'anyCollection/doc1'), { createdAt: serverTimestamp() }));
  });

  it('chặn cả admin khi collection chưa khai báo', async () => {
    const db = env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();
    await assertFails(getDoc(doc(db, 'anyCollection/doc1')));
  });
});
