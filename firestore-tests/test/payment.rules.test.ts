// Rules cho thanh toán spec 050 (tasks T016, data-model §1): mọi ghi tiền, trạng thái đơn, voucher,
// mã thanh toán, hoàn tiền đều qua Edge Function (service account bỏ qua Rules).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

const VENUE = 'venue-a';
const OTHER_VENUE = 'venue-b';

const unauth = () => env.unauthenticatedContext().firestore();
const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const ownerA = () => env.authenticatedContext('owner-a', { appRole: 'OWNER' }).firestore();
const ownerB = () => env.authenticatedContext('owner-b', { appRole: 'OWNER' }).firestore();

const payoutAccount = {
  bankBin: '970436',
  bankShortName: 'Vietcombank',
  accountNumber: '0123456789',
  accountHolderName: 'NGUYEN VAN A',
};

function pendingBooking(userId = 'player1') {
  return {
    userId,
    venueId: VENUE,
    status: 'PENDING',
    subtotal: 200000,
    discount: 0,
    total: 200000,
    depositAmount: 0,
    paidAmount: 0,
    remainingAmount: 200000,
    paymentMethod: 'AT_VENUE',
    paymentStatus: 'UNPAID',
  };
}

function refund(overrides: Record<string, unknown> = {}) {
  return {
    bookingId: 'b1',
    recurringGroupId: null,
    venueId: VENUE,
    userId: 'player1',
    amount: 60000,
    penaltyAmount: 0,
    reason: 'REJECTED',
    status: 'PENDING',
    ...overrides,
  };
}

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/owner-a'), { role: 'OWNER', ownerStatus: 'APPROVED' });
    await setDoc(doc(db, 'users/owner-b'), { role: 'OWNER', ownerStatus: 'APPROVED' });
    await setDoc(doc(db, `venues/${VENUE}`), { ownerId: 'owner-a', status: 'ACTIVE', name: 'Sân A' });
    await setDoc(doc(db, `venues/${OTHER_VENUE}`), { ownerId: 'owner-b', status: 'ACTIVE', name: 'Sân B' });
    await setDoc(doc(db, `venues/${VENUE}/vouchers/GIAM20`), {
      code: 'GIAM20', type: 'PERCENT', value: 20, usedCount: 0, isActive: true,
    });
    await setDoc(doc(db, `venues/${VENUE}/vouchers/GIAM20/userUsage/player1`), { userId: 'player1', count: 1 });
    await setDoc(doc(db, `venues/${VENUE}/vouchers/GIAM20/redemptions/b1`), { userId: 'player1', status: 'RESERVED' });
    await setDoc(doc(db, 'systemVouchers/HE2026/userUsage/player1'), { userId: 'player1', count: 1 });
    await setDoc(doc(db, 'systemVouchers/HE2026/redemptions/b1'), { userId: 'player1', status: 'USED' });
    await setDoc(doc(db, 'paymentCodes/SNPABCD2345'), { code: 'SNPABCD2345', userId: 'player1', bookingId: 'b1', active: true });
    await setDoc(doc(db, 'sepayTransactions/92704'), { sepayId: 92704, outcome: 'CONFIRMED' });
    await setDoc(doc(db, 'refunds/refund_b1'), refund());
    await setDoc(doc(db, 'refunds/refund_b2'), refund({ bookingId: 'b2', userId: 'player2', venueId: OTHER_VENUE }));
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('050 bookings: app không tự chốt đơn', () => {
  it('player không tạo được bookings status PENDING (chốt đơn chỉ qua booking-payment-confirm)', async () => {
    await assertFails(setDoc(doc(player(), 'bookings/b-new'), pendingBooking()));
  });

  it('player vẫn tạo được đơn HOLD của mình (giữ chỗ 041)', async () => {
    await assertSucceeds(setDoc(doc(player(), 'bookings/b-hold'), { userId: 'player1', venueId: VENUE, status: 'HOLD' }));
  });
});

describe('050 paymentCodes, sepayTransactions: chỉ server', () => {
  for (const path of ['paymentCodes/SNPABCD2345', 'sepayTransactions/92704']) {
    it(`không đọc/ghi được ${path.split('/')[0]}`, async () => {
      await assertFails(getDoc(doc(player(), path)));
      await assertFails(getDoc(doc(ownerA(), path)));
      await assertFails(setDoc(doc(player(), path), { active: false }));
      await assertFails(deleteDoc(doc(player(), path)));
      await assertFails(getDoc(doc(unauth(), path)));
    });
  }

  it('không tạo được mã thanh toán mới', async () => {
    await assertFails(setDoc(doc(player(), 'paymentCodes/SNPZZZZ2222'), { userId: 'player1', active: true }));
  });
});

describe('050 voucher: đọc voucher cơ sở, không đụng userUsage/redemptions', () => {
  it('người đã đăng nhập đọc được venues/{v}/vouchers/{code}', async () => {
    await assertSucceeds(getDoc(doc(player(), `venues/${VENUE}/vouchers/GIAM20`)));
    await assertSucceeds(getDoc(doc(ownerB(), `venues/${VENUE}/vouchers/GIAM20`)));
  });

  it('người đã đăng nhập liệt kê được voucher của một cơ sở (danh sách chọn)', async () => {
    await assertSucceeds(getDocs(collection(player(), `venues/${VENUE}/vouchers`)));
  });

  it('khách chưa đăng nhập không đọc được voucher cơ sở', async () => {
    await assertFails(getDoc(doc(unauth(), `venues/${VENUE}/vouchers/GIAM20`)));
  });

  it('app không ghi được voucher cơ sở (tạo/sửa qua owner-create-voucher, usedCount do server)', async () => {
    await assertFails(updateDoc(doc(player(), `venues/${VENUE}/vouchers/GIAM20`), { usedCount: 0 }));
    await assertFails(updateDoc(doc(ownerA(), `venues/${VENUE}/vouchers/GIAM20`), { usedCount: 0 }));
    await assertFails(setDoc(doc(player(), `venues/${VENUE}/vouchers/FREE`), { code: 'FREE', type: 'FIXED', value: 999999 }));
  });

  for (const base of [`venues/${VENUE}/vouchers/GIAM20`, 'systemVouchers/HE2026']) {
    it(`không đọc/ghi userUsage và redemptions dưới ${base.split('/')[0]}`, async () => {
      await assertFails(getDoc(doc(player(), `${base}/userUsage/player1`)));
      await assertFails(setDoc(doc(player(), `${base}/userUsage/player1`), { userId: 'player1', count: 0 }));
      await assertFails(getDoc(doc(player(), `${base}/redemptions/b1`)));
      await assertFails(deleteDoc(doc(player(), `${base}/redemptions/b1`)));
      await assertFails(getDoc(doc(ownerA(), `${base}/redemptions/b1`)));
    });
  }
});

describe('050 refunds: đọc theo người chơi / chủ sân, không ai ghi', () => {
  it('người chơi đọc được refund có userId của mình', async () => {
    await assertSucceeds(getDoc(doc(player('player1'), 'refunds/refund_b1')));
    await assertSucceeds(getDocs(query(collection(player('player1'), 'refunds'), where('userId', '==', 'player1'))));
  });

  it('người chơi không đọc được refund của người khác', async () => {
    await assertFails(getDoc(doc(player('player1'), 'refunds/refund_b2')));
    await assertFails(getDocs(collection(player('player1'), 'refunds')));
  });

  it('chủ sân đọc được refund của cơ sở mình, không đọc được của cơ sở khác', async () => {
    await assertSucceeds(getDoc(doc(ownerA(), 'refunds/refund_b1')));
    await assertFails(getDoc(doc(ownerA(), 'refunds/refund_b2')));
    await assertSucceeds(getDoc(doc(ownerB(), 'refunds/refund_b2')));
  });

  it('không ai ghi được refunds từ app (kể cả chủ sân bấm "Đã hoàn tiền": đi qua owner-mark-refunded)', async () => {
    await assertFails(setDoc(doc(player(), 'refunds/refund_b9'), refund({ bookingId: 'b9' })));
    await assertFails(updateDoc(doc(ownerA(), 'refunds/refund_b1'), { status: 'DONE' }));
    await assertFails(updateDoc(doc(player(), 'refunds/refund_b1'), { reportedAt: new Date() }));
    await assertFails(deleteDoc(doc(ownerA(), 'refunds/refund_b1')));
  });
});

describe('050 venues.payoutAccount: chỉ chủ venue ghi', () => {
  it('chủ venue ghi được payoutAccount', async () => {
    await assertSucceeds(updateDoc(doc(ownerA(), `venues/${VENUE}`), { payoutAccount }));
  });

  it('chủ venue khác và người chơi không ghi được payoutAccount', async () => {
    await assertFails(updateDoc(doc(ownerB(), `venues/${VENUE}`), { payoutAccount }));
    await assertFails(updateDoc(doc(player(), `venues/${VENUE}`), { payoutAccount }));
  });

  it('payoutAccount phải đúng 4 trường, BIN 6 số, số tài khoản 6–19 chữ số', async () => {
    const fs = ownerA();
    await assertFails(updateDoc(doc(fs, `venues/${VENUE}`), { payoutAccount: { ...payoutAccount, extra: 'x' } }));
    await assertFails(updateDoc(doc(fs, `venues/${VENUE}`), { payoutAccount: { ...payoutAccount, bankBin: '97043' } }));
    await assertFails(updateDoc(doc(fs, `venues/${VENUE}`), { payoutAccount: { ...payoutAccount, accountNumber: '12345' } }));
    await assertFails(updateDoc(doc(fs, `venues/${VENUE}`), { payoutAccount: { ...payoutAccount, accountNumber: '01234abc89' } }));
    await assertFails(updateDoc(doc(fs, `venues/${VENUE}`), { payoutAccount: { ...payoutAccount, accountHolderName: '' } }));
  });

  it('người đã đăng nhập đọc được payoutAccount của venue ACTIVE (app hiện QR/ẩn chuyển khoản)', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), `venues/${VENUE}`), { payoutAccount });
    });
    await assertSucceeds(getDoc(doc(player(), `venues/${VENUE}`)));
  });
});
