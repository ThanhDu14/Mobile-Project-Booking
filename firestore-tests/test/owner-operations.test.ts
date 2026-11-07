import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await fs.doc('users/owner-a').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await fs.doc('users/owner-b').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await fs.doc('users/pending').set({ ownerStatus: 'PENDING', role: 'PLAYER' });
    await fs.doc('users/revoked').set({ ownerStatus: 'NONE', role: 'PLAYER' });
    await fs.doc('venues/venue-a').set({ ownerId: 'owner-a', name: 'Sân A', status: 'ACTIVE' });
    await fs.doc('venues/venue-b').set({ ownerId: 'owner-b', name: 'Sân B', status: 'ACTIVE' });
    await fs.doc('venues/venue-locked').set({ ownerId: 'owner-a', name: 'Sân khóa', status: 'LOCKED' });
    await fs.doc('venues/venue-a/courts/court-1').set({
      name: 'Sân 1', surfaceType: 'MAT', isActive: true, sortOrder: 0,
    });
    await fs.doc('venues/venue-b/courts/court-2').set({
      name: 'Sân 2', surfaceType: 'PU', isActive: true, sortOrder: 1,
    });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('Feature 112 owner access prerequisites', () => {
  it('allows an approved owner to access editable data under an owned active venue', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertSucceeds(updateDoc(doc(fs, 'venues/venue-a'), { name: 'Sân A mới' }));
    await assertSucceeds(updateDoc(doc(fs, 'venues/venue-a/courts/court-1'), { isActive: false }));
  });

  it('denies pending and revoked owners even with a stale OWNER claim', async () => {
    const pending = env.authenticatedContext('pending', { appRole: 'OWNER' }).firestore();
    const revoked = env.authenticatedContext('revoked', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(doc(pending, 'venues/venue-a'), { name: 'No access' }));
    await assertFails(updateDoc(doc(revoked, 'venues/venue-a'), { name: 'No access' }));
  });

  it('denies access through another owner venue or court parent', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(doc(fs, 'venues/venue-b'), { name: 'No access' }));
    await assertFails(updateDoc(doc(fs, 'venues/venue-b/courts/court-2'), { isActive: false }));
  });

  it('does not allow an owner to operate a locked venue or its child court', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(doc(fs, 'venues/venue-locked'), { name: 'Unlock by client' }));
    await assertFails(updateDoc(doc(fs, 'venues/venue-locked/courts/court-1'), { isActive: false }));
  });
});
