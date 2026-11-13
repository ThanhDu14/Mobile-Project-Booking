import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, setDoc } from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin-1');
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('reports/rep-1').set({ targetType: 'VENUE', status: 'OPEN' });
    await ctx.firestore().doc('systemStats/20261106').set({ totalUsers: 100 });
    await ctx.firestore().doc('systemVouchers/VOUCHER10').set({ code: 'VOUCHER10', discount: 10 });
    await ctx.firestore().doc('ownerApplications/app-1').set({ uid: 'applicant-1', status: 'PENDING' });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 Admin Authorization & Security Rules', () => {
  it('denies unauthenticated read/write to all admin and sensitive collections', async () => {
    const unauthDb = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(unauthDb, 'reports/rep-1')));
    await assertFails(getDoc(doc(unauthDb, 'systemStats/20261106')));
    await assertFails(getDoc(doc(unauthDb, 'systemVouchers/VOUCHER10')));
    await assertFails(getDoc(doc(unauthDb, 'ownerApplications/app-1')));
    await assertFails(setDoc(doc(unauthDb, 'reports/rep-2'), { targetType: 'USER' }));
  });

  it('denies PLAYER and OWNER access to admin reports and system stats', async () => {
    const playerDb = env.authenticatedContext('player-1', { appRole: 'PLAYER' }).firestore();
    const ownerDb = env.authenticatedContext('owner-1', { appRole: 'OWNER' }).firestore();

    await assertFails(getDoc(doc(playerDb, 'reports/rep-1')));
    await assertFails(getDoc(doc(playerDb, 'systemStats/20261106')));
    await assertFails(getDoc(doc(ownerDb, 'reports/rep-1')));
    await assertFails(getDoc(doc(ownerDb, 'systemStats/20261106')));
  });

  it('allows signed-in users to read systemVouchers but forbids client writes', async () => {
    const playerDb = env.authenticatedContext('player-1', { appRole: 'PLAYER' }).firestore();
    await assertSucceeds(getDoc(doc(playerDb, 'systemVouchers/VOUCHER10')));
    await assertFails(setDoc(doc(playerDb, 'systemVouchers/VOUCHER20'), { code: 'VOUCHER20' }));
  });

  it('allows ADMIN to read reports, systemStats, and ownerApplications', async () => {
    const adminDb = env.authenticatedContext('admin-1', { appRole: 'ADMIN' }).firestore();
    await assertSucceeds(getDoc(doc(adminDb, 'reports/rep-1')));
    await assertSucceeds(getDoc(doc(adminDb, 'systemStats/20261106')));
    await assertSucceeds(getDoc(doc(adminDb, 'ownerApplications/app-1')));
    await assertSucceeds(getDocs(collection(adminDb, 'ownerApplications')));
  });

  it('forbids direct client writes to reports, systemStats, and ownerApplications even for ADMIN', async () => {
    const adminDb = env.authenticatedContext('admin-1', { appRole: 'ADMIN' }).firestore();
    await assertFails(setDoc(doc(adminDb, 'reports/rep-1'), { status: 'RESOLVED' }, { merge: true }));
    await assertFails(setDoc(doc(adminDb, 'systemStats/20261106'), { totalUsers: 101 }, { merge: true }));
    await assertFails(setDoc(doc(adminDb, 'ownerApplications/app-1'), { status: 'APPROVED' }, { merge: true }));
  });
});
