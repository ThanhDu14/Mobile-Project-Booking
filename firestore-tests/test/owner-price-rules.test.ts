import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc, type Firestore } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;
const rule = (fs: Firestore, venue = 'venue-a', id = 'rule-1') =>
  doc(fs, `venues/${venue}/priceRules/${id}`);

beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await fs.doc('users/owner-a').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await fs.doc('users/owner-b').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await fs.doc('users/pending').set({ ownerStatus: 'PENDING', role: 'OWNER' });
    await fs.doc('venues/venue-a').set({ ownerId: 'owner-a', status: 'ACTIVE', minPricePerHour: 60000 });
    await fs.doc('venues/venue-b').set({ ownerId: 'owner-b', status: 'ACTIVE', minPricePerHour: 70000 });
    await fs.doc('venues/venue-a/priceRules/rule-1').set({ daysOfWeek: [4], startTime: '0600', endTime: '0800', pricePerHour: 80000, label: 'NORMAL' });
    await fs.doc('bookings/history-1').set({ items: [{ price: 40000 }] });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('Feature 111 priceRules boundary', () => {
  it('allows approved owner CRUD only below an owned venue', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertSucceeds(setDoc(rule(fs, 'venue-a', 'rule-2'), { daysOfWeek: [5], startTime: '0800', endTime: '0900', pricePerHour: 60000, label: 'PEAK' }));
    await assertSucceeds(updateDoc(rule(fs), { pricePerHour: 90000 }));
    await assertSucceeds(deleteDoc(rule(fs, 'venue-a', 'rule-2')));
  });

  it('denies pending and foreign owners', async () => {
    const pending = env.authenticatedContext('pending', { appRole: 'OWNER' }).firestore();
    const foreign = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(rule(pending), { pricePerHour: 90000 }));
    await assertFails(updateDoc(rule(foreign, 'venue-b'), { pricePerHour: 90000 }));
    await assertFails(deleteDoc(rule(foreign, 'venue-b')));
  });

  it('rejects protected minPricePerHour and any extra schema field', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertFails(updateDoc(doc(fs, 'venues/venue-a'), { minPricePerHour: 20000 }));
    await assertFails(updateDoc(rule(fs), { minPricePerHour: 20000 }));
    await assertFails(setDoc(rule(fs, 'venue-a', 'rule-extra'), { daysOfWeek: [4], startTime: '0900', endTime: '1000', pricePerHour: 60000, label: 'NORMAL', unexpected: true }));
  });

  it('does not change the historical booking price snapshot', async () => {
    const fs = env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
    await assertSucceeds(updateDoc(rule(fs), { pricePerHour: 100000 }));
    await assertSucceeds(deleteDoc(rule(fs)));
    await env.withSecurityRulesDisabled(async (ctx) => {
      const snapshot = await getDoc(doc(ctx.firestore(), 'bookings/history-1'));
      if (snapshot.data()?.items?.[0]?.price !== 40000) throw new Error('historical booking price changed');
      const venue = await getDoc(doc(ctx.firestore(), 'venues/venue-a'));
      if (venue.data()?.minPricePerHour !== 60000) throw new Error('protected minimum price changed');
    });
  });
});
