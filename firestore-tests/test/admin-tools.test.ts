import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin-uid');
  await env.withSecurityRulesDisabled(async (ctx) => {
    // Fixture system stats
    await ctx.firestore().doc('systemStats/latest').set({
      totalUsers: 1000,
      totalVenues: 50,
      totalBookings: 2000,
      totalRevenue: 250000000,
      updatedAt: new Date(),
    });

    // Fixture system voucher
    await ctx.firestore().doc('systemVouchers/CHAOBAN2026').set({
      code: 'CHAOBAN2026',
      type: 'PERCENTAGE',
      value: 10,
      maxDiscount: 30000,
      minOrderTotal: 50000,
      validFrom: '2026-11-01T00:00:00Z',
      validTo: '2026-11-30T23:59:59Z',
      usageLimit: 100,
      perUserLimit: 1,
      usedCount: 0,
      isActive: true,
    });

    // Fixture district
    await ctx.firestore().doc('districts/quan-1').set({
      name: 'Quận 1',
      sortOrder: 1,
      isActive: true,
    });

    // Fixture amenity
    await ctx.firestore().doc('amenities/WIFI').set({
      name: 'Wifi miễn phí',
      isActive: true,
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 US4 Admin Tools, Stats, Vouchers & Categories Rules Contract', () => {
  it('Admin reads systemStats; non-admin is denied', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();
    const ownerDb = env.authenticatedContext('owner-uid', { appRole: 'OWNER' }).firestore();
    const unauthDb = env.unauthenticatedContext().firestore();

    await assertSucceeds(getDoc(doc(adminDb, 'systemStats/latest')));
    await assertFails(getDoc(doc(playerDb, 'systemStats/latest')));
    await assertFails(getDoc(doc(ownerDb, 'systemStats/latest')));
    await assertFails(getDoc(doc(unauthDb, 'systemStats/latest')));
  });

  it('Client writes to systemStats are denied for all users including admin', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertFails(setDoc(doc(adminDb, 'systemStats/new'), { totalUsers: 2000 }));
  });

  it('Signed-in users can read systemVouchers; client writes are completely denied', async () => {
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();

    await assertSucceeds(getDoc(doc(playerDb, 'systemVouchers/CHAOBAN2026')));
    await assertSucceeds(getDoc(doc(adminDb, 'systemVouchers/CHAOBAN2026')));

    // Client write denied (must go through Edge Function)
    await assertFails(updateDoc(doc(playerDb, 'systemVouchers/CHAOBAN2026'), { usedCount: 1 }));
    await assertFails(updateDoc(doc(adminDb, 'systemVouchers/CHAOBAN2026'), { usedCount: 5 }));
    await assertFails(setDoc(doc(adminDb, 'systemVouchers/NEWCODE'), { value: 50 }));
  });

  it('Districts & Amenities: initialized users read, only admin writes', async () => {
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();

    // Read allowed
    await assertSucceeds(getDoc(doc(playerDb, 'districts/quan-1')));
    await assertSucceeds(getDoc(doc(playerDb, 'amenities/WIFI')));

    // Non-admin write denied
    await assertFails(setDoc(doc(playerDb, 'districts/quan-2'), { name: 'Quận 2', isActive: true }));
    await assertFails(setDoc(doc(playerDb, 'amenities/PARKING'), { name: 'Bãi đỗ xe', isActive: true }));

    // Admin write allowed
    await assertSucceeds(setDoc(doc(adminDb, 'districts/quan-2'), { name: 'Quận 2', isActive: true }));
    await assertSucceeds(setDoc(doc(adminDb, 'amenities/PARKING'), { name: 'Bãi đỗ xe', isActive: true }));
  });
});
