import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, updateDoc, setDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('reports/rep-venue-1').set({
      targetType: 'VENUE',
      targetPath: 'venues/v-bad',
      reason: 'Sân không có thật',
      status: 'OPEN',
      createdAt: new Date(),
    });
    await ctx.firestore().doc('reports/rep-user-2').set({
      targetType: 'USER',
      targetPath: 'users/u-spammer',
      reason: 'Spam tin nhắn quấy rối',
      status: 'OPEN',
      createdAt: new Date(),
    });
    await ctx.firestore().doc('venues/v-bad').set({
      name: 'Sân Giả',
      status: 'ACTIVE',
    });
    await ctx.firestore().doc('users/u-spammer').set({
      accountStatus: 'ACTIVE',
      role: 'PLAYER',
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 US2 Report Moderation Security Rules Contract', () => {
  it('Admin reads reports queue and details', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertSucceeds(getDoc(doc(adminDb, 'reports/rep-venue-1')));
    await assertSucceeds(getDocs(collection(adminDb, 'reports')));
  });

  it('Denies non-admin (PLAYER, OWNER, unauthenticated) from reading reports', async () => {
    const unauthDb = env.unauthenticatedContext().firestore();
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();
    const ownerDb = env.authenticatedContext('owner-uid', { appRole: 'OWNER' }).firestore();

    await assertFails(getDoc(doc(unauthDb, 'reports/rep-venue-1')));
    await assertFails(getDoc(doc(playerDb, 'reports/rep-venue-1')));
    await assertFails(getDoc(doc(ownerDb, 'reports/rep-venue-1')));
    await assertFails(getDocs(collection(playerDb, 'reports')));
  });

  it('Denies direct client moderation writes to reports collection (enforces Edge Function only)', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();

    // Direct resolution attempt by admin client
    await assertFails(updateDoc(doc(adminDb, 'reports/rep-venue-1'), {
      status: 'RESOLVED',
      action: 'LOCK_VENUE',
      handledBy: 'admin-uid',
    }));

    // Direct dismissal attempt by player client
    await assertFails(updateDoc(doc(playerDb, 'reports/rep-venue-1'), {
      status: 'DISMISSED',
      action: 'NONE',
    }));

    // Direct report creation / deletion forbidden from client
    await assertFails(setDoc(doc(adminDb, 'reports/rep-new'), {
      targetType: 'POST',
      status: 'OPEN',
    }));
  });
});
