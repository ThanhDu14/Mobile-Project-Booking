import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, updateDoc } from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin-uid');
  await env.withSecurityRulesDisabled(async (ctx) => {
    // Fixture user
    await ctx.firestore().doc('users/user-1').set({
      displayName: 'Nguyen Van A',
      phone: '0901234567',
      role: 'OWNER',
      ownerStatus: 'APPROVED',
      accountStatus: 'ACTIVE',
      favoriteDistrictIds: [],
    });

    // Fixture venue owned by user-1, currently LOCKED
    await ctx.firestore().doc('venues/venue-locked-1').set({
      name: 'San Cau Long ABC',
      ownerId: 'user-1',
      status: 'LOCKED',
      districtId: 'quan-1',
    });

    // Fixture venue owned by user-1, currently ACTIVE
    await ctx.firestore().doc('venues/venue-active-1').set({
      name: 'San Cau Long XYZ',
      ownerId: 'user-1',
      status: 'ACTIVE',
      districtId: 'quan-1',
    });

    // Fixture owner application
    await ctx.firestore().doc('ownerApplications/app-1').set({
      uid: 'user-1',
      status: 'APPROVED',
      venueDraft: { name: 'San Cau Long ABC' },
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 US3 User & Venue Management Security Rules Contract', () => {
  it('Admin can lookup user and list users collection', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertSucceeds(getDoc(doc(adminDb, 'users/user-1')));
    await assertSucceeds(getDocs(collection(adminDb, 'users')));
  });

  it('Player/Owner cannot list all users in collection', async () => {
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();
    const ownerDb = env.authenticatedContext('user-1', { appRole: 'OWNER' }).firestore();

    await assertFails(getDocs(collection(playerDb, 'users')));
    await assertFails(getDocs(collection(ownerDb, 'users')));
  });

  it('Client cannot mutate protected user administration fields (role, ownerStatus, accountStatus, ownerRevokedReason)', async () => {
    const selfDb = env.authenticatedContext('user-1', { appRole: 'OWNER' }).firestore();

    // Owner tries to change their own role or accountStatus
    await assertFails(updateDoc(doc(selfDb, 'users/user-1'), {
      role: 'ADMIN',
    }));

    await assertFails(updateDoc(doc(selfDb, 'users/user-1'), {
      accountStatus: 'LOCKED',
    }));

    await assertFails(updateDoc(doc(selfDb, 'users/user-1'), {
      ownerStatus: 'REJECTED',
    }));

    await assertFails(updateDoc(doc(selfDb, 'users/user-1'), {
      ownerRevokedReason: 'Tự gỡ',
    }));
  });

  it('Owner cannot unlock a LOCKED venue', async () => {
    const ownerDb = env.authenticatedContext('user-1', { appRole: 'OWNER' }).firestore();

    // Attempting to unlock from LOCKED to ACTIVE directly from client fails
    await assertFails(updateDoc(doc(ownerDb, 'venues/venue-locked-1'), {
      status: 'ACTIVE',
    }));
  });

  it('Owner cannot set their venue status to LOCKED', async () => {
    const ownerDb = env.authenticatedContext('user-1', { appRole: 'OWNER' }).firestore();

    // Owner can only toggle between ACTIVE and HIDDEN
    await assertFails(updateDoc(doc(ownerDb, 'venues/venue-active-1'), {
      status: 'LOCKED',
    }));
  });

  it('Owner application status cannot be modified directly by client', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    const ownerDb = env.authenticatedContext('user-1', { appRole: 'OWNER' }).firestore();

    // Client direct update on ownerApplications is always forbidden
    await assertFails(updateDoc(doc(adminDb, 'ownerApplications/app-1'), {
      status: 'REJECTED',
    }));

    await assertFails(updateDoc(doc(ownerDb, 'ownerApplications/app-1'), {
      status: 'PENDING',
    }));
  });
});
