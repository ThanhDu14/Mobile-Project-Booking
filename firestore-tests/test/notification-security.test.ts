import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, connectFirestoreEmulator, deleteDoc, doc, getDoc, getDocs, getFirestore, limit, orderBy, query, runTransaction, startAfter, serverTimestamp, setDoc, terminate, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';
import { deleteApp, initializeApp } from 'firebase/app';
import { createServer } from 'node:net';
import { createTestEnv, PROJECT_ID } from './setup';
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
  it('unreachable transaction transport cannot change preferences and online transaction updates one key', async () => {
    const db = dbFor('alice');
    const profile = doc(db, 'users/alice');
    const save = () => runTransaction(db, async tx => {
      const current = await tx.get(profile);
      expect(current.exists()).toBe(true);
      tx.update(profile, { 'notificationPrefs.PROMOTION': true, updatedAt: serverTimestamp() });
    }, { maxAttempts: 1 });
    // disableNetwork() does not disable the JS transaction RPC transport. Use a closed loopback port.
    const portProbe = createServer();
    await new Promise<void>(resolve => portProbe.listen(0, '127.0.0.1', resolve));
    const port = (portProbe.address() as { port: number }).port;
    await new Promise<void>((resolve, reject) => portProbe.close(error => error ? reject(error) : resolve()));
    const app = initializeApp({ projectId: PROJECT_ID }, `notification-offline-${port}`);
    const offline = getFirestore(app);
    connectFirestoreEmulator(offline, '127.0.0.1', port, { mockUserToken: { sub: 'alice', appRole: 'PLAYER' } });
    try {
      await expect(runTransaction(offline, async tx => {
        const target = doc(offline, 'users/alice');
        await tx.get(target);
        tx.update(target, { 'notificationPrefs.PROMOTION': true, updatedAt: serverTimestamp() });
      }, { maxAttempts: 1 })).rejects.toMatchObject({ code: 'unavailable' });
    } finally { await terminate(offline); await deleteApp(app); }
    const unchanged = (await getDoc(profile)).data()!;
    expect(unchanged.notificationPrefs?.PROMOTION).not.toBe(true);
    await assertSucceeds(save());
    expect((await getDoc(profile)).data()!.notificationPrefs.PROMOTION).toBe(true);
  });
  it('legacy string dates break chronology and a createdAt-only Timestamp repair restores it', async () => {
    const legacyDate = '2026-01-01T00:00:00.000Z';
    await env.withSecurityRulesDisabled(async ctx => {
      await setDoc(doc(ctx.firestore(), `${inbox}/legacy`), { ...item, isRead: true, createdAt: legacyDate });
      await setDoc(doc(ctx.firestore(), `${inbox}/current`), { ...item, createdAt: Timestamp.fromDate(new Date('2026-11-22T00:00:00.000Z')) });
    });
    const newest = query(collection(dbFor('alice'), inbox), orderBy('createdAt', 'desc'), limit(30));
    expect((await getDocs(newest)).docs.slice(0, 2).map(d => d.id)).toEqual(['legacy', 'current']);
    await env.withSecurityRulesDisabled(async ctx => {
      await updateDoc(doc(ctx.firestore(), `${inbox}/legacy`), { createdAt: Timestamp.fromDate(new Date(legacyDate)) });
    });
    expect((await getDocs(newest)).docs.slice(0, 2).map(d => d.id)).toEqual(['current', 'legacy']);
    const repaired = (await getDoc(doc(dbFor('alice'), `${inbox}/legacy`))).data()!;
    expect(repaired.isRead).toBe(true);
    expect(repaired.body).toBe(item.body);
    expect(repaired.createdAt.toDate().toISOString()).toBe(legacyDate);
  });
  it('approved parent query pages 30 items newest first through equal timestamps without duplicates', async () => {
    await env.withSecurityRulesDisabled(async ctx => {
      const batch = writeBatch(ctx.firestore());
      batch.delete(doc(ctx.firestore(), `${inbox}/one`));
      batch.delete(doc(ctx.firestore(), `${inbox}/two`));
      for (let i = 0; i < 65; i++) batch.set(doc(ctx.firestore(), `${inbox}/n${String(i).padStart(2, '0')}`), {
        ...item, createdAt: Timestamp.fromMillis(Math.floor(i / 2) * 1000),
      });
      await batch.commit();
    });
    const db = dbFor('alice');
    const base = query(collection(db, inbox), orderBy('createdAt', 'desc'), limit(30));
    const first = await assertSucceeds(getDocs(base));
    const second = await assertSucceeds(getDocs(query(base, startAfter(first.docs[29]))));
    const third = await assertSucceeds(getDocs(query(base, startAfter(second.docs[29]))));
    expect([first.size, second.size, third.size]).toEqual([30, 30, 5]);
    const docs = [...first.docs, ...second.docs, ...third.docs];
    expect(new Set(docs.map(d => d.id)).size).toBe(65);
    const times = docs.map(d => d.data().createdAt.toMillis());
    expect(times).toEqual([...times].sort((a, b) => b - a));
  });
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
