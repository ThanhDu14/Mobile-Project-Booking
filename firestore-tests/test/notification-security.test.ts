import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';
import { createTestEnv } from './setup';
import { seedUser } from './fixtures';

let env: RulesTestEnvironment;
const inbox = 'users/alice/notifications';
const item = { type: 'SYSTEM', title: 'Thông báo', body: 'Nội dung đã lưu', data: {}, isRead: false, createdAt: Timestamp.fromMillis(1000) };
const dbFor = (uid: string, appRole = 'PLAYER') => env.authenticatedContext(uid, { appRole }).firestore();
beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/alice'), seedUser('alice'));
    await setDoc(doc(db, `${inbox}/one`), item);
    await setDoc(doc(db, `${inbox}/two`), item);
    await setDoc(doc(db, 'users/admin'), { role: 'ADMIN', accountStatus: 'ACTIVE' });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('notification inbox ownership', () => {
  it('owner can read and list own inbox and mark one/all read', async () => {
    const db = dbFor('alice');
    await assertSucceeds(getDoc(doc(db, `${inbox}/one`)));
    await assertSucceeds(getDocs(collection(db, inbox)));
    await assertSucceeds(updateDoc(doc(db, `${inbox}/one`), { isRead: true }));
    const batch = writeBatch(db);
    for (const id of ['one', 'two']) batch.update(doc(db, `${inbox}/${id}`), { isRead: true });
    await assertSucceeds(batch.commit());
  });
  it('other users, guests and admin cannot access another inbox', async () => {
    for (const db of [dbFor('bob'), env.unauthenticatedContext().firestore(), dbFor('admin', 'ADMIN')]) {
      await assertFails(getDoc(doc(db, `${inbox}/one`)));
      await assertFails(getDocs(collection(db, inbox)));
      await assertFails(updateDoc(doc(db, `${inbox}/one`), { isRead: true }));
    }
  });
  it('owner cannot create/delete or alter server fields or write a nonboolean read flag', async () => {
    const db = dbFor('alice');
    const ref = doc(db, `${inbox}/one`);
    await assertFails(setDoc(doc(db, `${inbox}/new`), item));
    await assertFails(deleteDoc(ref));
    for (const change of [{ title: 'forged' }, { body: 'forged' }, { type: 'BOOKING_NEW' }, { data: { bookingId: 'other' } }, { createdAt: Timestamp.now() }, { isRead: 'true' }, { isRead: null }]) {
      await assertFails(updateDoc(ref, { isRead: true, ...change }));
    }
  });
});

describe('notification preferences preserve profile security', () => {
  it('owner can update preferences under existing profile timestamp contract', async () => {
    await assertSucceeds(updateDoc(doc(dbFor('alice'), 'users/alice'), {
      notificationPrefs: { PROMOTION: false, BOOKING_CONFIRMED: true }, updatedAt: serverTimestamp(),
    }));
  });
  it('denies cross-user preferences and combined protected-field mutations', async () => {
    await assertFails(updateDoc(doc(dbFor('bob'), 'users/alice'), { notificationPrefs: { PROMOTION: true }, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(dbFor('alice'), 'users/alice'), { notificationPrefs: { PROMOTION: true }, role: 'ADMIN', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(dbFor('alice'), 'users/alice'), { notificationPrefs: { PROMOTION: true }, accountStatus: 'ACTIVE', ownerStatus: 'APPROVED', updatedAt: serverTimestamp() }));
  });
});
