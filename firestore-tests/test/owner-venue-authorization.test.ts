import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await ctx.firestore().doc('users/owner-a').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await ctx.firestore().doc('users/owner-b').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await ctx.firestore().doc('users/pending').set({ ownerStatus: 'PENDING', role: 'OWNER' });
    await ctx.firestore().doc('venues/venue-a').set({ ownerId: 'owner-a', name: 'A', status: 'ACTIVE' });
    await ctx.firestore().doc('venues/venue-hidden').set({ ownerId: 'owner-a', name: 'Hidden', status: 'HIDDEN' });
    await ctx.firestore().doc('venues/venue-b').set({ ownerId: 'owner-b', name: 'B', status: 'ACTIVE' });
    await ctx.firestore().doc('venues/venue-locked').set({ ownerId: 'owner-a', name: 'Locked', status: 'LOCKED' });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('Feature 111 owner boundary', () => {
  it('approved owner can update an owned editable venue field', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertSucceeds(updateDoc(doc(fs, 'venues/venue-a'), { name: 'A updated' }));
  });

  it('pending owner and foreign owner cannot update a venue', async () => {
    const pending = env.authenticatedContext('pending', { appRole: 'OWNER' }).firestore();
    const foreign = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(doc(pending, 'venues/venue-a'), { name: 'no' }));
    await assertFails(updateDoc(doc(foreign, 'venues/venue-b'), { name: 'no' }));
  });

  it('owner cannot change protected ownership or min price fields', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(doc(fs, 'venues/venue-a'), { ownerId: 'owner-b' }));
    await assertFails(updateDoc(doc(fs, 'venues/venue-a'), { minPricePerHour: 20_000 }));
  });

  it('hidden venue is not public but remains readable by its approved owner', async () => {
    const publicFs = env.unauthenticatedContext().firestore();
    const ownerFs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(getDoc(doc(publicFs, 'venues/venue-hidden')));
    await assertSucceeds(getDoc(doc(ownerFs, 'venues/venue-hidden')));
  });

  it('owner can hide and toggle recurring without changing protected fields', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertSucceeds(updateDoc(doc(fs, 'venues/venue-a'), { status: 'HIDDEN' }));
    await assertSucceeds(updateDoc(doc(fs, 'venues/venue-a'), { acceptsRecurring: false }));
    await assertFails(updateDoc(doc(fs, 'venues/venue-a'), { ratingAvg: 5 }));
    await assertFails(updateDoc(doc(fs, 'venues/venue-locked'), { status: 'ACTIVE' }));
  });
});
