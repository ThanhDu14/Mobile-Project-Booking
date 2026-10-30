import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, type Firestore } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;
const court = (fs: Firestore, venueId = 'venue-a', courtId = 'court-1') => doc(fs, `venues/${venueId}/courts/${courtId}`);

beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/owner-a').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await ctx.firestore().doc('users/owner-b').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await ctx.firestore().doc('users/pending').set({ ownerStatus: 'PENDING', role: 'OWNER' });
    await ctx.firestore().doc('venues/venue-a').set({ ownerId: 'owner-a', status: 'ACTIVE' });
    await ctx.firestore().doc('venues/venue-b').set({ ownerId: 'owner-b', status: 'ACTIVE' });
    await ctx.firestore().doc('venues/venue-a/courts/court-1').set({ name: 'Sân 1', surfaceType: 'MAT', isActive: true, sortOrder: 0 });
    await ctx.firestore().doc('slots/court-1_20261029_0600').set({ venueId: 'venue-a', courtId: 'court-1', date: '20261029', status: 'BOOKED', bookingId: 'booking-1' });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('Feature 111 court management', () => {
  it('approved owner can create, reorder and deactivate an owned court', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    const court2 = court(fs, 'venue-a', 'court-2');
    await assertSucceeds(setDoc(court2, { name: 'Sân 2', surfaceType: 'PU', isActive: true, sortOrder: 1 }));
    await assertSucceeds(updateDoc(court2, { sortOrder: 2, isActive: false }));
    await assertSucceeds(updateDoc(court(fs), { isActive: false }));
    await assertSucceeds(getDoc(doc(fs, 'slots/court-1_20261029_0600')));
  });

  it('pending and foreign owners cannot mutate courts', async () => {
    const pending = env.authenticatedContext('pending', { appRole: 'OWNER' }).firestore();
    const foreign = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(court(pending), { isActive: false }));
    await assertFails(updateDoc(court(foreign, 'venue-b', 'court-1'), { isActive: false }));
  });

  it('court writes cannot add fields outside the existing schema', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(court(fs), { ownerId: 'owner-a' }));
  });
});
