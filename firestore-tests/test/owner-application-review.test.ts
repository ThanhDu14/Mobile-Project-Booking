import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('ownerApplications/app-pending-1').set({
      uid: 'user-applicant-1',
      status: 'PENDING',
      submittedAt: new Date(),
      venueDraft: { name: 'Sân Cầu Lông ABC' },
    });
    await ctx.firestore().doc('ownerApplications/app-approved-2').set({
      uid: 'user-applicant-2',
      status: 'APPROVED',
      submittedAt: new Date(),
    });
    await ctx.firestore().doc('users/user-applicant-1').set({
      role: 'PLAYER',
      ownerStatus: 'PENDING',
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 US1 Owner Application Review Rules Contract', () => {
  it('Admin reads any pending application', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertSucceeds(getDoc(doc(adminDb, 'ownerApplications/app-pending-1')));
    await assertSucceeds(getDocs(collection(adminDb, 'ownerApplications')));
  });

  it('Applicant reads only their own application', async () => {
    const applicantDb = env.authenticatedContext('user-applicant-1', { appRole: 'PLAYER' }).firestore();
    const otherApplicantDb = env.authenticatedContext('user-applicant-3', { appRole: 'PLAYER' }).firestore();

    await assertSucceeds(getDoc(doc(applicantDb, 'ownerApplications/app-pending-1')));
    await assertFails(getDoc(doc(applicantDb, 'ownerApplications/app-approved-2')));
    await assertFails(getDoc(doc(otherApplicantDb, 'ownerApplications/app-pending-1')));
  });

  it('Direct client approval/rejection is completely forbidden (enforces server Edge Function only)', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    const applicantDb = env.authenticatedContext('user-applicant-1', { appRole: 'PLAYER' }).firestore();

    // Client admin attempting direct Firestore write
    await assertFails(updateDoc(doc(adminDb, 'ownerApplications/app-pending-1'), {
      status: 'APPROVED',
      reviewedBy: 'admin-uid',
    }));
    await assertFails(updateDoc(doc(adminDb, 'ownerApplications/app-pending-1'), {
      status: 'REJECTED',
      rejectReason: 'Không đạt tiêu chuẩn',
    }));

    // Client user attempting direct privilege escalation
    await assertFails(updateDoc(doc(applicantDb, 'users/user-applicant-1'), {
      role: 'OWNER',
      ownerStatus: 'APPROVED',
    }));
  });
});
