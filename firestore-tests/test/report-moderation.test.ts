import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, updateDoc, setDoc } from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin-uid');
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('reports/rep-venue-1').set({
      targetType: 'VENUE',
      targetPath: 'venues/v-bad',
      reason: 'Sân không có thật',
      status: 'OPEN',
      createdAt: new Date(),
    });
    await ctx.firestore().doc('reports/rep-user-2').set({
      targetType: 'USER',
      targetPath: 'users/u-spammer',
      reason: 'Spam tin nhắn quấy rối',
      status: 'OPEN',
      createdAt: new Date(),
    });
    await ctx.firestore().doc('venues/v-bad').set({
      name: 'Sân Giả',
      status: 'ACTIVE',
    });
    await ctx.firestore().doc('users/u-spammer').set({
      accountStatus: 'ACTIVE',
      role: 'PLAYER',
    });
    await ctx.firestore().doc('groups/private-group').set({ ownerId: 'group-owner', visibility: 'PRIVATE', status: 'LOCKED' });
    await ctx.firestore().doc('chats/private-chat').set({ memberIds: ['sender', 'recipient'] });
    for (const [id, status] of [['visible', 'VISIBLE'], ['hidden', 'HIDDEN']]) {
      await ctx.firestore().doc(`groups/private-group/posts/${id}`).set({ authorId: 'sender', content: 'Private post content', status });
      await ctx.firestore().doc(`chats/private-chat/messages/${id}`).set({ senderId: 'sender', text: 'Private message content', status });
    }
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Feature 120 US2 Report Moderation Security Rules Contract', () => {
  const privateTargets = [
    'groups/private-group/posts/visible', 'groups/private-group/posts/hidden',
    'chats/private-chat/messages/visible', 'chats/private-chat/messages/hidden',
  ];

  it('Current active admin can get reported posts and messages, including hidden content, without membership', async () => {
    const db = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    for (const path of privateTargets) await assertSucceeds(getDoc(doc(db, path)));
  });

  it('Preview access does not grant admin collection listing or direct content writes', async () => {
    const db = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertFails(getDocs(collection(db, 'groups/private-group/posts')));
    await assertFails(getDocs(collection(db, 'chats/private-chat/messages')));
    for (const path of privateTargets) {
      await assertFails(updateDoc(doc(db, path), { status: 'VISIBLE' }));
      await assertFails(deleteDoc(doc(db, path)));
    }
    await assertFails(setDoc(doc(db, 'groups/private-group/posts/new'), { content: 'Forged' }));
    await assertFails(setDoc(doc(db, 'chats/private-chat/messages/new'), { text: 'Forged' }));
  });

  for (const role of ['PLAYER', 'OWNER', null]) {
    it(`Preview permission does not expose private targets to ${role ?? 'guests'}`, async () => {
      const db = role ? env.authenticatedContext('outsider', { appRole: role }).firestore() : env.unauthenticatedContext().firestore();
      for (const path of privateTargets) await assertFails(getDoc(doc(db, path)));
    });
  }

  for (const [name, profile] of [
    ['revoked', { role: 'PLAYER', accountStatus: 'ACTIVE' }],
    ['locked', { role: 'ADMIN', accountStatus: 'LOCKED' }],
    ['deleting', { role: 'ADMIN', accountStatus: 'ACTIVE', deletionRequestedAt: new Date() }],
    ['missing', null],
  ] as const) {
    it(`Stale ADMIN claim cannot preview private targets when profile is ${name}`, async () => {
      if (profile) await env.withSecurityRulesDisabled(async ctx => { await ctx.firestore().doc('users/stale').set(profile); });
      const db = env.authenticatedContext('stale', { appRole: 'ADMIN' }).firestore();
      for (const path of privateTargets) await assertFails(getDoc(doc(db, path)));
    });
  }

  it('Locked group owner cannot delete, soft-delete or unlock; admin reads and uses server API for changes', async () => {
    await env.withSecurityRulesDisabled(async ctx => {
      await ctx.firestore().doc('groups/locked').set({ ownerId: 'group-owner', status: 'LOCKED' });
    });
    const ownerDb = env.authenticatedContext('group-owner', { appRole: 'PLAYER' }).firestore();
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertFails(deleteDoc(doc(ownerDb, 'groups/locked')));
    await assertFails(updateDoc(doc(ownerDb, 'groups/locked'), { status: 'DELETED' }));
    await assertFails(updateDoc(doc(ownerDb, 'groups/locked'), { status: 'ACTIVE' }));
    await assertSucceeds(getDoc(doc(adminDb, 'groups/locked')));
    await assertFails(updateDoc(doc(adminDb, 'groups/locked'), { status: 'ACTIVE' }));
  });
  it('Admin reads reports queue and details', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    await assertSucceeds(getDoc(doc(adminDb, 'reports/rep-venue-1')));
    await assertSucceeds(getDocs(collection(adminDb, 'reports')));
  });

  it('Denies non-admin (PLAYER, OWNER, unauthenticated) from reading reports', async () => {
    const unauthDb = env.unauthenticatedContext().firestore();
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();
    const ownerDb = env.authenticatedContext('owner-uid', { appRole: 'OWNER' }).firestore();

    await assertFails(getDoc(doc(unauthDb, 'reports/rep-venue-1')));
    await assertFails(getDoc(doc(playerDb, 'reports/rep-venue-1')));
    await assertFails(getDoc(doc(ownerDb, 'reports/rep-venue-1')));
    await assertFails(getDocs(collection(playerDb, 'reports')));
  });

  it('Denies direct client moderation writes to reports collection (enforces Edge Function only)', async () => {
    const adminDb = env.authenticatedContext('admin-uid', { appRole: 'ADMIN' }).firestore();
    const playerDb = env.authenticatedContext('player-uid', { appRole: 'PLAYER' }).firestore();

    // Direct resolution attempt by admin client
    await assertFails(updateDoc(doc(adminDb, 'reports/rep-venue-1'), {
      status: 'RESOLVED',
      action: 'LOCK_VENUE',
      handledBy: 'admin-uid',
    }));

    // Direct dismissal attempt by player client
    await assertFails(updateDoc(doc(playerDb, 'reports/rep-venue-1'), {
      status: 'DISMISSED',
      action: 'NONE',
    }));

    // Direct report creation / deletion forbidden from client
    await assertFails(setDoc(doc(adminDb, 'reports/rep-new'), {
      targetType: 'POST',
      status: 'OPEN',
    }));
  });
});
