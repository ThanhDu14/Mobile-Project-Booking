import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const owner = (uid = 'owner1') => env.authenticatedContext(uid, { appRole: 'OWNER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();

function user(uid: string, ownerStatus = 'NONE', role = 'PLAYER') {
  return {
    displayName: 'Người dùng kiểm thử',
    email: `${uid}@example.com`,
    role,
    ownerStatus,
    accountStatus: 'ACTIVE',
    favoriteDistrictIds: [],
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
    createdAt: Timestamp.fromMillis(1_760_000_000_000),
    updatedAt: Timestamp.fromMillis(1_760_000_000_000),
  };
}

function application(uid: string, status = 'PENDING') {
  return {
    uid,
    venueDraft: {
      name: 'Sân kiểm thử',
      address: '1 Đường A',
      districtId: 'q1',
      location: { latitude: 10.78, longitude: 106.69 },
      phone: '0900000000',
      courtCount: 2,
      openTime: '0600',
      closeTime: '2200',
      photoPaths: ['uid/application/venue.jpg'],
    },
    representativeName: 'Người đại diện',
    representativePhone: '0900000001',
    documentPaths: ['uid/application/document.jpg'],
    status,
    submittedAt: Timestamp.fromMillis(1_760_000_000_000),
  };
}

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/player1'), user('player1'));
    await setDoc(doc(db, 'users/owner1'), user('owner1', 'APPROVED', 'OWNER'));
    await setDoc(doc(db, 'users/admin1'), user('admin1', 'NONE', 'ADMIN'));
    await setDoc(doc(db, 'ownerApplications/app-player'), application('player1'));
    await setDoc(doc(db, 'ownerApplications/app-other'), application('owner1', 'APPROVED'));
  });
});

afterAll(async () => {
  if (env) await env.cleanup();
});

describe('ownerApplications/{id}: đọc', () => {
  it('applicant đọc được hồ sơ của chính mình', async () => {
    await assertSucceeds(getDoc(doc(player(), 'ownerApplications/app-player')));
  });

  it('applicant không đọc được hồ sơ của người khác', async () => {
    await assertFails(getDoc(doc(player(), 'ownerApplications/app-other')));
  });

  it.each(['PENDING', 'APPROVED', 'REJECTED'])(
    'applicant đọc được đúng hồ sơ của mình ở trạng thái %s',
    async (status) => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), `ownerApplications/app-${status.toLowerCase()}`), application('player1', status));
      });
      await assertSucceeds(getDoc(doc(player(), `ownerApplications/app-${status.toLowerCase()}`)));
    },
  );

  it('owner đã duyệt vẫn chỉ đọc được hồ sơ của chính mình', async () => {
    await assertSucceeds(getDoc(doc(owner(), 'ownerApplications/app-other')));
    await assertFails(getDoc(doc(owner(), 'ownerApplications/app-player')));
  });

  it('admin đọc từng hồ sơ và list được hàng đợi', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'ownerApplications/app-player')));
    await assertSucceeds(getDocs(collection(admin(), 'ownerApplications')));
  });

  it('chưa đăng nhập không đọc được', async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'ownerApplications/app-player')));
  });
});

describe('ownerApplications/{id}: ghi', () => {
  it('client không tự tạo hồ sơ hoặc ghi status server-only', async () => {
    await assertFails(setDoc(doc(player(), 'ownerApplications/new'), application('player1')));
    await assertFails(updateDoc(doc(player(), 'ownerApplications/app-player'), { status: 'APPROVED' }));
  });

  it('client không sửa hồ sơ approved hoặc xóa hồ sơ', async () => {
    await assertFails(updateDoc(doc(owner(), 'ownerApplications/app-other'), {
      representativeName: 'Sửa trái phép',
    }));
    await assertFails(updateDoc(doc(player(), 'ownerApplications/app-player'), {
      rejectReason: 'Tự đặt lý do',
      reviewedBy: 'player1',
      reviewedAt: Timestamp.now(),
    }));
  });

  it('request stale bị từ chối kể cả khi caller là applicant và hồ sơ đã REJECTED', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'ownerApplications/app-rejected'), application('player1', 'REJECTED'));
    });
    await assertFails(updateDoc(doc(player(), 'ownerApplications/app-rejected'), {
      representativeName: 'Bản nháp cũ',
    }));
    await assertFails(setDoc(doc(player(), 'ownerApplications/stale-create'), application('player1')));
  });

  it('direct client writes remain denied for every application state and role', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'ownerApplications/app-approved-player'), application('player1', 'APPROVED'));
      await setDoc(doc(ctx.firestore(), 'ownerApplications/app-rejected-player'), application('player1', 'REJECTED'));
    });
    for (const id of ['app-player', 'app-approved-player', 'app-rejected-player']) {
      await assertFails(updateDoc(doc(player(), `ownerApplications/${id}`), { status: 'PENDING' }));
      await assertFails(updateDoc(doc(admin(), `ownerApplications/${id}`), { status: 'PENDING' }));
    }
  });
});

describe('districts/{id}: chưa có quyền client', () => {
  it('player, owner và admin đều không đọc khi policy chưa được chấp thuận', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'districts/q1'), { name: 'Quận 1' });
    });
    await assertFails(getDoc(doc(player(), 'districts/q1')));
    await assertFails(getDoc(doc(owner(), 'districts/q1')));
    await assertFails(getDoc(doc(admin(), 'districts/q1')));
  });
});
