import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('ownerApplications/app-100').set({
      uid: 'applicant-100',
      status: 'PENDING',
      documentPaths: ['applicant-100/app-100/license.pdf'],
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 / 110 Owner Docs Access Boundaries', () => {
  it('Admin can read application to verify owner-docs references', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    const snap = await getDoc(doc(adminDb, 'ownerApplications/app-100'));
    assertSucceeds(Promise.resolve(snap));
  });

  it('Applicant can read their own document metadata in application', async () => {
    const applicantDb = env.authenticatedContext('applicant-100', { appRole: 'PLAYER' }).firestore();
    await assertSucceeds(getDoc(doc(applicantDb, 'ownerApplications/app-100')));
  });

  it('Third-party cannot read applicant document metadata in application', async () => {
    const thirdPartyDb = env.authenticatedContext('other-user', { appRole: 'PLAYER' }).firestore();
    await assertFails(getDoc(doc(thirdPartyDb, 'ownerApplications/app-100')));
  });
});
