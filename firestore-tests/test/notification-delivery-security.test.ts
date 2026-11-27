import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';
import { seedUser } from './fixtures';
let env: RulesTestEnvironment;
const player = (uid = 'alice', role = 'PLAYER') => env.authenticatedContext(uid, { appRole: role }).firestore();
beforeAll(async () => { env = await createTestEnv(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/alice'), seedUser('alice', { fcmTokens: ['old', 'other-device'] }));
    await setDoc(doc(db, 'users/admin'), { role: 'ADMIN', accountStatus: 'ACTIVE' });
    await setDoc(doc(db, 'notificationDeliveries/job'), { recipientUid: 'alice', state: 'PENDING' });
    await setDoc(doc(db, 'notificationDeliveries/job/tokens/hash'), { state: 'PENDING' });
    await setDoc(doc(db, 'notificationFanouts/event'), { state: 'PENDING' });
    await setDoc(doc(db, 'notificationFanouts/event/recipients/0'), { uids: ['alice'] });
    await setDoc(doc(db, 'groups/group/notificationCommands/request'), { actorUid: 'alice' });
    await setDoc(doc(db, 'chats/c'), { memberIds: ['alice', 'bob'] });
    await setDoc(doc(db, 'chats/c/participants/alice'), { userId: 'alice' });
  });
});
afterAll(async () => { await env.cleanup(); });
describe('Feature 100 approved backend security', () => {
  it('jobs and token receipts are inaccessible to every client including current ADMIN', async () => {
    for (const db of [player(), player('bob'), player('admin', 'ADMIN'), env.unauthenticatedContext().firestore()]) {
      for (const path of ['notificationDeliveries/job', 'notificationDeliveries/job/tokens/hash',
        'notificationFanouts/event', 'notificationFanouts/event/recipients/0', 'groups/group/notificationCommands/request']) {
        await assertFails(getDoc(doc(db, path)));
        await assertFails(setDoc(doc(db, path), { recipientUid: 'alice' }));
        await assertFails(deleteDoc(doc(db, path)));
      }
      await assertFails(getDocs(collection(db, 'notificationDeliveries')));
      await assertFails(getDocs(collection(db, 'notificationFanouts')));
    }
  });
  it('own token register/rotate/remove preserves another device; foreign UID and bulk mutations denied', async () => {
    const ref = doc(player(), 'users/alice');
    await assertSucceeds(updateDoc(ref, { fcmTokens: ['other-device', 'fresh'], updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref, { fcmTokens: ['other-device'], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { fcmTokens: ['other-device', 'a', 'b'], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(player('bob'), 'users/alice'), { fcmTokens: ['forged'], updatedAt: serverTimestamp() }));
  });
  it('new token must be a bounded nonempty string, no device count cap; P15 legacy removal remains possible', async () => {
    const ref = doc(player(), 'users/alice');
    for (const invalid of ['', 42, 'x'.repeat(4097)]) await assertFails(updateDoc(ref, { fcmTokens: ['old', 'other-device', invalid], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { fcmTokens: 'invalid', updatedAt: serverTimestamp() }));
    await env.withSecurityRulesDisabled(async ctx => { await updateDoc(doc(ctx.firestore(), 'users/alice'), { fcmTokens: ['', 'legacy', ...Array.from({ length: 105 }, (_, i) => `t${i}`)] }); });
    await assertSucceeds(updateDoc(ref, { fcmTokens: ['legacy', ...Array.from({ length: 105 }, (_, i) => `t${i}`)], updatedAt: serverTimestamp() }));
  });
  it('two installations can hold independent leases; leaving one cannot delete the other', async () => {
    const db = player(), path = 'chats/c/participants/alice/presence/';
    const lease = { viewingUntil: Timestamp.fromMillis(Date.now() + 60_000) };
    await assertSucceeds(setDoc(doc(db, path + 'one'), lease));
    await assertSucceeds(setDoc(doc(db, path + 'two'), lease));
    await assertSucceeds(deleteDoc(doc(db, path + 'one')));
    await assertSucceeds(getDoc(doc(db, path + 'two')));
    await assertFails(setDoc(doc(player('bob'), path + 'two'), lease));
    await assertFails(setDoc(doc(player('outsider'), 'chats/c/participants/outsider/presence/x'), lease));
    await assertFails(setDoc(doc(db, path + 'bad'), { viewingUntil: Timestamp.fromMillis(Date.now() + 11 * 60_000) }));
    await assertFails(setDoc(doc(db, path + 'extra'), { ...lease, token: 'raw' }));
    await assertFails(setDoc(doc(db, path + 'string'), { viewingUntil: 'later' }));
  });
});
