import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

const unauth = () => env.unauthenticatedContext().firestore();
const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const otherPlayer = (uid = 'player2') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();
const owner = (uid = 'owner1') => env.authenticatedContext(uid, { appRole: 'OWNER' }).firestore();

function validGroupData(userId = 'player1', overrides: Record<string, unknown> = {}) {
  return {
    userId,
    venueId: 'demo-venue-01',
    status: 'HOLD',
    holdExpiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000),
    requestId: 'req-01',
    createdAt: Timestamp.now(),
    ...overrides,
  };
}

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
});

afterAll(async () => {
  await env.cleanup();
});

describe('recurringGroups collection (Spec 041)', () => {
  describe('create', () => {
    it('player can create recurringGroup with HOLD status and valid expiry within 5 minutes', async () => {
      const fs = player('player1');
      await assertSucceeds(setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('player1')));
    });

    it('create fails when userId does not match auth uid', async () => {
      const fs = player('player1');
      await assertFails(setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('player2')));
    });

    it('create fails when status is not HOLD', async () => {
      const fs = player('player1');
      await assertFails(
        setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('player1', { status: 'CONFIRMED' }))
      );
    });

    it('create fails when holdExpiresAt exceeds 5 minutes from now', async () => {
      const fs = player('player1');
      const tooFarExpiry = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
      await assertFails(
        setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('player1', { holdExpiresAt: tooFarExpiry }))
      );
    });

    it('create fails when holdExpiresAt is in the past', async () => {
      const fs = player('player1');
      const pastExpiry = Timestamp.fromMillis(Date.now() - 60 * 1000);
      await assertFails(
        setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('player1', { holdExpiresAt: pastExpiry }))
      );
    });

    it('unauthenticated user cannot create recurringGroup', async () => {
      const fs = unauth();
      await assertFails(setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('player1')));
    });

    it('non-player (e.g. owner) cannot create recurringGroup', async () => {
      const fs = owner('owner1');
      await assertFails(setDoc(doc(fs, 'recurringGroups/group-01'), validGroupData('owner1')));
    });
  });

  describe('read', () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), 'recurringGroups/group-01'), validGroupData('player1'));
      });
    });

    it('owner of group can read their recurringGroup', async () => {
      const fs = player('player1');
      await assertSucceeds(getDoc(doc(fs, 'recurringGroups/group-01')));
    });

    it('another player cannot read the recurringGroup', async () => {
      const fs = otherPlayer('player2');
      await assertFails(getDoc(doc(fs, 'recurringGroups/group-01')));
    });

    it('admin can read any recurringGroup', async () => {
      const fs = admin();
      await assertSucceeds(getDoc(doc(fs, 'recurringGroups/group-01')));
    });

    it('unauthenticated user cannot read recurringGroup', async () => {
      const fs = unauth();
      await assertFails(getDoc(doc(fs, 'recurringGroups/group-01')));
    });
  });

  describe('update', () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), 'recurringGroups/group-01'), validGroupData('player1'));
      });
    });

    it('owner cannot directly cancel a recurring group (contract pending)', async () => {
      const fs = player('player1');
      await assertFails(updateDoc(doc(fs, 'recurringGroups/group-01'), { status: 'CANCELLED' }));
    });

    it('owner cannot directly expire a recurring group', async () => {
      const fs = player('player1');
      await assertFails(updateDoc(doc(fs, 'recurringGroups/group-01'), { status: 'EXPIRED' }));
    });

    it('owner cannot update status to CONFIRMED (reserved for spec 050)', async () => {
      const fs = player('player1');
      await assertFails(
        updateDoc(doc(fs, 'recurringGroups/group-01'), { status: 'CONFIRMED' })
      );
    });

    it('another player cannot update recurringGroup', async () => {
      const fs = otherPlayer('player2');
      await assertFails(
        updateDoc(doc(fs, 'recurringGroups/group-01'), { status: 'CANCELLED' })
      );
    });

    it('owner cannot modify fields other than status', async () => {
      const fs = player('player1');
      await assertFails(
        updateDoc(doc(fs, 'recurringGroups/group-01'), { total: 100000 })
      );
      await assertFails(
        updateDoc(doc(fs, 'recurringGroups/group-01'), { status: 'CANCELLED', total: 100000 })
      );
    });
  });

  describe('delete', () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), 'recurringGroups/group-01'), validGroupData('player1'));
      });
    });

    it('player cannot delete recurringGroup', async () => {
      const fs = player('player1');
      await assertFails(deleteDoc(doc(fs, 'recurringGroups/group-01')));
    });

    it('admin cannot delete recurringGroup (allow delete: if false)', async () => {
      const fs = admin();
      await assertFails(deleteDoc(doc(fs, 'recurringGroups/group-01')));
    });
  });
});
