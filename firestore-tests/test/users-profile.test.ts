// P1–P18 của specs/011-account-profile/contracts/security-rules.md §4 (spec 011, A).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { arrayRemove, doc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';
import { seedDistrict, seedUser } from './fixtures';

let env: RulesTestEnvironment;

const me = () => env.authenticatedContext('player1', { appRole: 'PLAYER' }).firestore();
const update = (data: Record<string, unknown>) =>
  updateDoc(doc(me(), 'users/player1'), { ...data, updatedAt: serverTimestamp() });
const AVATAR = 'avatar_1760500000000.jpg';

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/player1'), seedUser('player1', { fcmTokens: ['token-a', 'token-b'] }));
    await setDoc(doc(db, 'users/player2'), seedUser('player2'));
    await setDoc(doc(db, 'districts/q1'), seedDistrict('Quận 1', 1));
    await setDoc(doc(db, 'districts/q3'), seedDistrict('Quận 3', 3));
    await setDoc(doc(db, 'districts/q10'), seedDistrict('Quận 10', 10));
    await setDoc(doc(db, 'districts/thu-duc'), seedDistrict('TP Thủ Đức', 20));
    await setDoc(doc(db, 'districts/binh-thanh'), seedDistrict('Bình Thạnh', 21));
    await setDoc(doc(db, 'districts/go-vap'), seedDistrict('Gò Vấp', 22));
    await setDoc(doc(db, 'districts/q-cu'), seedDistrict('Quận cũ', 99, false));
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('users/{uid}: trình độ', () => {
  it('P1: đặt level hợp lệ', async () => {
    await assertSucceeds(update({ level: 'INTERMEDIATE' }));
  });

  it('P2: bỏ chọn level (null)', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      updateDoc(doc(ctx.firestore(), 'users/player1'), { level: 'BEGINNER' }));
    await assertSucceeds(update({ level: null }));
  });

  it('P3: level ngoài danh sách bị chặn', async () => {
    await assertFails(update({ level: 'PRO' }));
  });
});

describe('users/{uid}: khu vực hay chơi', () => {
  it('P4: hai khu vực đang hoạt động', async () => {
    await assertSucceeds(update({ favoriteDistrictIds: ['q1', 'q3'] }));
  });

  it('P5: 6 khu vực bị chặn', async () => {
    await assertFails(update({ favoriteDistrictIds: ['q1', 'q3', 'q10', 'thu-duc', 'binh-thanh', 'go-vap'] }));
  });

  it('P5b: đúng 5 khu vực được phép', async () => {
    await assertSucceeds(update({ favoriteDistrictIds: ['q1', 'q3', 'q10', 'thu-duc', 'binh-thanh'] }));
  });

  it('P6: trùng khu vực bị chặn', async () => {
    await assertFails(update({ favoriteDistrictIds: ['q1', 'q1'] }));
  });

  it('P7: khu vực không tồn tại bị chặn', async () => {
    await assertFails(update({ favoriteDistrictIds: ['khong-ton-tai'] }));
  });

  it('P8: khu vực đã ngừng (isActive = false) bị chặn', async () => {
    await assertFails(update({ favoriteDistrictIds: ['q-cu'] }));
  });

  it('P9: danh sách rỗng được phép', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      updateDoc(doc(ctx.firestore(), 'users/player1'), { favoriteDistrictIds: ['q1'] }));
    await assertSucceeds(update({ favoriteDistrictIds: [] }));
  });
});

describe('users/{uid}: ảnh đại diện', () => {
  it('P10: đường dẫn trong thư mục của mình', async () => {
    await assertSucceeds(update({ avatarPath: `player1/${AVATAR}` }));
  });

  it('P11: đường dẫn trong thư mục người khác bị chặn', async () => {
    await assertFails(update({ avatarPath: `player2/${AVATAR}` }));
  });

  it('P12: đường dẫn sai mẫu bị chặn', async () => {
    await assertFails(update({ avatarPath: 'player1/../x.jpg' }));
    await assertFails(update({ avatarPath: 'player1/avatar.png' }));
    await assertFails(update({ avatarPath: `player1/${AVATAR}/thua` }));
  });

  it('P13: xóa ảnh (null)', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      updateDoc(doc(ctx.firestore(), 'users/player1'), { avatarPath: `player1/${AVATAR}` }));
    await assertSucceeds(update({ avatarPath: null }));
  });
});

describe('users/{uid}: nhiều trường và trường bị cấm', () => {
  it('P14: đổi tên, trình độ, khu vực cùng lúc', async () => {
    await assertSucceeds(update({ displayName: 'Trần Thu Hà', level: 'ADVANCED', favoriteDistrictIds: ['q10'] }));
  });

  it('P14b: một trường sai làm cả request bị chặn', async () => {
    await assertFails(update({ displayName: 'Trần Thu Hà', level: 'PRO' }));
  });

  it('P15: gỡ token FCM của máy khi đăng xuất (US2)', async () => {
    await assertSucceeds(update({ fcmTokens: arrayRemove('token-a') }));
  });

  it('P16: tự đổi email bị chặn', async () => {
    await assertFails(update({ email: 'moi@x.com' }));
  });

  it('P17: tự đặt deletionRequestedAt bị chặn', async () => {
    await assertFails(update({ deletionRequestedAt: serverTimestamp() }));
  });

  it('P18: tự đổi walletBalance, ownerStatus, role bị chặn', async () => {
    await assertFails(update({ walletBalance: 1_000_000 }));
    await assertFails(update({ ownerStatus: 'APPROVED' }));
    await assertFails(update({ role: 'ADMIN' }));
  });

  it('P-updatedAt: thiếu updatedAt = request.time bị chặn', async () => {
    await assertFails(updateDoc(doc(me(), 'users/player1'), { level: 'BEGINNER' }));
  });
});
