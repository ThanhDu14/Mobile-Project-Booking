import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, setDoc, where } from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin');
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('ownerApplications/app-a').set({ uid: 'applicant-a', status: 'PENDING', submittedAt: new Date() });
    await ctx.firestore().doc('ownerApplications/app-b').set({ uid: 'applicant-b', status: 'REJECTED', submittedAt: new Date() });
    await ctx.firestore().doc('ownerApplications/app-c').set({ uid: 'applicant-a', status: 'APPROVED', submittedAt: new Date() });
  });
});
afterAll(async () => { await env.cleanup(); });

describe('Feature 110 ownerApplications boundary', () => {
  it('approved owner reads additions, keeps existing venue access, but cannot write application or create venue', async () => {
    await env.withSecurityRulesDisabled(async ctx => {
      await ctx.firestore().doc('users/owner').set({role:'OWNER',ownerStatus:'APPROVED',accountStatus:'ACTIVE'});
      await ctx.firestore().doc('venues/existing').set({ownerId:'owner',status:'ACTIVE',name:'Existing'});
      await ctx.firestore().doc('ownerApplications/addition').set({uid:'owner',purpose:'VENUE_ADDITION',status:'PENDING'});
    });
    const db=env.authenticatedContext('owner',{appRole:'OWNER'}).firestore();
    await assertSucceeds(getDoc(doc(db,'ownerApplications/addition')));
    await assertSucceeds(getDocs(query(collection(db,'venues'),where('ownerId','==','owner'))));
    await assertFails(setDoc(doc(db,'ownerApplications/addition'),{status:'APPROVED'},{merge:true}));
    await assertFails(setDoc(doc(db,'ownerApplications/new'),{uid:'owner',purpose:'VENUE_ADDITION',status:'PENDING'}));
    await assertFails(setDoc(doc(db,'venues/new'),{ownerId:'owner',status:'ACTIVE'}));
    await assertFails(setDoc(doc(db,'users/owner'),{ownerStatus:'PENDING'},{merge:true}));
  });
  it('admin clients also cannot bypass server review by creating a venue directly', async () => {
    const db=env.authenticatedContext('admin',{appRole:'ADMIN'}).firestore();
    await assertFails(setDoc(doc(db,'venues/bypass'),{ownerId:'applicant-a',status:'ACTIVE'}));
  });
  it('applicant reads their own application and query scoped by uid', async () => {
    const db = env.authenticatedContext('applicant-a', { appRole: 'PLAYER' }).firestore();
    await assertSucceeds(getDoc(doc(db, 'ownerApplications/app-a')));
    await assertSucceeds(getDocs(query(collection(db, 'ownerApplications'), where('uid', '==', 'applicant-a'))));
  });

  it('denies anonymous, other applicant and unfiltered cross-user reads', async () => {
    const anonymous = env.unauthenticatedContext().firestore();
    const other = env.authenticatedContext('applicant-a', { appRole: 'PLAYER' }).firestore();
    await assertFails(getDoc(doc(anonymous, 'ownerApplications/app-a')));
    await assertFails(getDoc(doc(other, 'ownerApplications/app-b')));
    await assertFails(getDocs(collection(other, 'ownerApplications')));
  });

  it('only Admin may read across applicants; client writes remain forbidden in every lifecycle state', async () => {
    const admin = env.authenticatedContext('admin', { appRole: 'ADMIN' }).firestore();
    const player = env.authenticatedContext('applicant-a', { appRole: 'PLAYER' }).firestore();
    await assertSucceeds(getDoc(doc(admin, 'ownerApplications/app-b')));
    await assertFails(setDoc(doc(player, 'ownerApplications/new-app'), { uid: 'applicant-a', status: 'PENDING' }));
    await assertFails(setDoc(doc(player, 'ownerApplications/app-a'), { uid: 'applicant-a', status: 'APPROVED' }, { merge: true }));
    await assertFails(setDoc(doc(player, 'ownerApplications/app-b'), { uid: 'applicant-a', status: 'PENDING' }, { merge: true }));
    await assertFails(setDoc(doc(player, 'ownerApplications/app-c'), { uid: 'applicant-a', status: 'PENDING' }, { merge: true }));
  });
});
