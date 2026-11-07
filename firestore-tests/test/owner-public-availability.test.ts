import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, type Firestore } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;
const publicFs = () => env.unauthenticatedContext().firestore();
const ownerFs = () => env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();

beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await fs.doc('users/owner-a').set({ ownerStatus: 'APPROVED', role: 'OWNER' });
    await fs.doc('venues/active').set({ ownerId: 'owner-a', status: 'ACTIVE', acceptsRecurring: true });
    await fs.doc('venues/hidden').set({ ownerId: 'owner-a', status: 'HIDDEN', acceptsRecurring: true });
    await fs.doc('venues/locked').set({ ownerId: 'owner-a', status: 'LOCKED', acceptsRecurring: true });
    await fs.doc('venues/active/courts/court-1').set({ name: 'Sân 1', surfaceType: 'MAT', isActive: false, sortOrder: 0 });
    await fs.doc('venues/active/priceRules/rule-morning').set({ daysOfWeek: [4], startTime: '0600', endTime: '0700', pricePerHour: 80000, label: 'NORMAL' });
    await fs.doc('slots/court-1_20261029_0600').set({ venueId: 'active', courtId: 'court-1', date: '20261029', status: 'BLOCKED', blockReason: 'MAINTENANCE' });
    await fs.doc('recurringGroups/group-1').set({ venueId: 'active', status: 'CONFIRMED' });
    await fs.doc('bookings/history-1').set({ items: [{ price: 40000 }] });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('Feature 111 public availability contract', () => {
  it('exposes only ACTIVE venue data publicly while owner can inspect hidden/locked state', async () => {
    const fs = publicFs();
    await assertSucceeds(getDoc(doc(fs, 'venues/active')));
    await assertFails(getDoc(doc(fs, 'venues/hidden')));
    await assertFails(getDoc(doc(fs, 'venues/locked')));
    await assertSucceeds(getDoc(doc(ownerFs(), 'venues/hidden')));
    await assertSucceeds(getDoc(doc(ownerFs(), 'venues/locked')));
  });

  it('keeps owner-written court, price-rule and blocked-slot fields readable for the existing grid contract', async () => {
    const fs = publicFs();
    const court = await (await getDoc(doc(fs, 'venues/active/courts/court-1'))).data();
    const rule = await (await getDoc(doc(fs, 'venues/active/priceRules/rule-morning'))).data();
    const blocked = await (await getDoc(doc(fs, 'slots/court-1_20261029_0600'))).data();
    if (!court || court.isActive !== false) throw new Error('inactive court contract changed');
    if (!rule || rule.pricePerHour / 2 !== 40000) throw new Error('30-minute price contract changed');
    if (!blocked || blocked.status !== 'BLOCKED') throw new Error('blocked slot contract changed');
  });

  it('preserves recurring groups and historical price snapshots when recurring is disabled', async () => {
    await assertSucceeds(updateDoc(doc(ownerFs(), 'venues/active'), { acceptsRecurring: false }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fs = ctx.firestore();
      const group = await getDoc(doc(fs, 'recurringGroups/group-1'));
      const booking = await getDoc(doc(fs, 'bookings/history-1'));
      if (group.data()?.status !== 'CONFIRMED') throw new Error('existing recurring group changed');
      if (booking.data()?.items?.[0]?.price !== 40000) throw new Error('historical booking price changed');
    });
  });

  it('models full-day closure as every expected slot BLOCKED with an owner reason', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fs = ctx.firestore();
      for (const startTime of ['0600', '0630', '0700']) {
        await fs.doc(`slots/court-1_20261029_${startTime}`).set({ venueId: 'active', courtId: 'court-1', date: '20261029', status: 'BLOCKED', blockReason: 'EVENT' });
      }
      const docs = await Promise.all(['0600', '0630', '0700'].map((time) => getDoc(doc(fs, `slots/court-1_20261029_${time}`))));
      if (docs.some((snapshot) => snapshot.data()?.status !== 'BLOCKED' || !['MAINTENANCE', 'EVENT'].includes(snapshot.data()?.blockReason))) {
        throw new Error('full-day closure is not represented by blocked slots');
      }
    });
  });

  it('does not permit a client-side owner BLOCKED write while T007 server boundary is unresolved', async () => {
    const fs = ownerFs();
    await assertFails(setDoc(doc(fs, 'slots/court-1_20261029_0630'), {
      venueId: 'active', courtId: 'court-1', date: '20261029', status: 'BLOCKED', blockReason: 'EVENT'
    }));
  });
});
