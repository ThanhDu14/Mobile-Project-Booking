import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;
beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    await ctx.firestore().doc('ownerApplications/private').set({ uid: 'applicant', status: 'PENDING' });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('R120-16 current admin profile', () => {
  for (const [name, profile] of [
    ['revoked', { role: 'PLAYER', accountStatus: 'ACTIVE' }],
    ['locked', { role: 'ADMIN', accountStatus: 'LOCKED' }],
    ['deleting', { role: 'ADMIN', accountStatus: 'ACTIVE', deletionRequestedAt: new Date() }],
  ] as const) {
    it(`denies stale ADMIN claim when profile is ${name}`, async () => {
      await env.withSecurityRulesDisabled(async ctx => { await ctx.firestore().doc('users/stale').set(profile); });
      const db = env.authenticatedContext('stale', { appRole: 'ADMIN' }).firestore();
      await assertFails(getDoc(doc(db, 'ownerApplications/private')));
      await assertFails(setDoc(doc(db, 'amenities/forbidden'), { name: 'Forbidden' }));
    });
  }
});
