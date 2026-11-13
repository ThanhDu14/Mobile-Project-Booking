// D1–D13 của specs/080-drop-in-session/contracts/security-rules.md (spec 080, C).
// Client chỉ đọc; mọi ghi buổi và đăng ký đi qua Edge Function (service account).
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

const START = Timestamp.fromMillis(Date.UTC(2026, 10, 20, 11, 0)); // 18:00 giờ VN
const END = Timestamp.fromMillis(Date.UTC(2026, 10, 20, 13, 0));
const CREATED = Timestamp.fromMillis(1_762_000_000_000);

// Đúng schema data-model §1.
function session(ownerId: string, venueId: string, overrides: Record<string, unknown> = {}) {
  return {
    venueId,
    ownerId,
    districtId: 'q10',
    courtIds: ['court1'],
    date: '20261120',
    startAt: START,
    endAt: END,
    timeSlot: 'EVENING',
    venueSnapshot: { name: 'CLB Cầu Lông Trường Chinh', address: '909 Trường Chinh', districtName: 'Quận 10' },
    level: 'INTERMEDIATE',
    capacity: 12,
    registeredCount: 3,
    pricePerPerson: 60000,
    status: 'OPEN',
    note: null,
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

// Đúng schema data-model §2: ID document = userId.
function registration(sessionId: string, userId: string, overrides: Record<string, unknown> = {}) {
  return {
    userId,
    sessionId,
    venueId: 'venueA',
    sessionStartAt: START,
    sessionEndAt: END,
    partySize: 1,
    status: 'REGISTERED',
    paymentMethod: 'MOCK_PAY',
    paymentStatus: 'PAID',
    pricePerPerson: 60000,
    amount: 60000,
    refundAmount: null,
    qrToken: `qr-${sessionId}-${userId}`,
    ticketCode: 'DI-ABCD1234',
    requestId: '00000000-0000-4000-8000-000000000001',
    cancelRequestId: null,
    sessionChanged: false,
    registeredAt: CREATED,
    cancelledAt: null,
    cancelledBy: null,
    checkedInAt: null,
    waitlistedAt: null,
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

const guest = () => env.unauthenticatedContext().firestore();
const player = (uid: string) => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const owner = (uid: string) => env.authenticatedContext(uid, { appRole: 'OWNER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin1');
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/owner1'), { appRole: 'OWNER', ownerStatus: 'APPROVED' });
    await setDoc(doc(db, 'users/owner2'), { appRole: 'OWNER', ownerStatus: 'APPROVED' });
    await setDoc(doc(db, 'dropInSessions/s1'), session('owner1', 'venueA'));
    await setDoc(doc(db, 'dropInSessions/s2'), session('owner2', 'venueB', { status: 'FULL' }));
    await setDoc(doc(db, 'dropInSessions/s1/registrations/playerA'), registration('s1', 'playerA'));
    await setDoc(doc(db, 'dropInSessions/s1/registrations/playerB'), registration('s1', 'playerB'));
    await setDoc(doc(db, 'dropInSessions/s2/registrations/playerA'), registration('s2', 'playerA', {
      paymentMethod: 'AT_VENUE',
      paymentStatus: 'UNPAID',
    }));
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('dropInSessions/{sessionId}', () => {
  it('D1: khách chưa đăng nhập đọc và liệt kê buổi', async () => {
    const db = guest();
    await assertSucceeds(getDoc(doc(db, 'dropInSessions/s1')));
    await assertSucceeds(getDocs(query(collection(db, 'dropInSessions'), where('status', '==', 'OPEN'))));
    await assertSucceeds(getDocs(collection(db, 'dropInSessions')));
  });

  it('D1 (bị chặn): khách chưa đăng nhập không tạo được buổi', async () => {
    await assertFails(setDoc(doc(guest(), 'dropInSessions/s9'), session('owner1', 'venueA')));
  });

  it('D2: người chơi không ghi được registeredCount hoặc status của buổi', async () => {
    const db = player('playerA');
    const ref = doc(db, 'dropInSessions/s1');
    await assertFails(updateDoc(ref, { registeredCount: 4 }));
    await assertFails(updateDoc(ref, { status: 'FULL' }));
    await assertFails(deleteDoc(ref));
  });

  it('D2 (bị chặn): chủ cơ sở cũng không ghi buổi trực tiếp từ client', async () => {
    const db = owner('owner1');
    await assertFails(updateDoc(doc(db, 'dropInSessions/s1'), { registeredCount: 0 }));
    await assertFails(setDoc(doc(db, 'dropInSessions/s9'), session('owner1', 'venueA')));
  });
});

describe('dropInSessions/{sessionId}/registrations/{uid} — ghi', () => {
  it('D3: người chơi không tự tạo đăng ký của chính mình từ client', async () => {
    const db = player('playerC');
    await assertFails(setDoc(doc(db, 'dropInSessions/s1/registrations/playerC'), {
      ...registration('s1', 'playerC'),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
  });

  it('D4: người chơi không sửa amount, paymentStatus, status, qrToken của đăng ký mình', async () => {
    const ref = doc(player('playerA'), 'dropInSessions/s1/registrations/playerA');
    await assertFails(updateDoc(ref, { amount: 0 }));
    await assertFails(updateDoc(ref, { paymentStatus: 'REFUNDED' }));
    await assertFails(updateDoc(ref, { status: 'CHECKED_IN' }));
    await assertFails(updateDoc(ref, { qrToken: 'forged' }));
  });

  it('D13: người chơi không xóa đăng ký của mình (phải gọi cancel-drop-in)', async () => {
    await assertFails(deleteDoc(doc(player('playerA'), 'dropInSessions/s1/registrations/playerA')));
  });

  it('D13 (bị chặn): admin cũng không xóa hay sửa đăng ký từ client', async () => {
    const ref = doc(admin(), 'dropInSessions/s1/registrations/playerA');
    await assertFails(deleteDoc(ref));
    await assertFails(updateDoc(ref, { status: 'CANCELLED' }));
  });
});

describe('dropInSessions/{sessionId}/registrations/{uid} — đọc', () => {
  it('D5: người chơi A không đọc được đăng ký của B', async () => {
    await assertFails(getDoc(doc(player('playerA'), 'dropInSessions/s1/registrations/playerB')));
  });

  it('D5 (bị chặn): khách chưa đăng nhập không đọc được đăng ký', async () => {
    await assertFails(getDoc(doc(guest(), 'dropInSessions/s1/registrations/playerA')));
  });

  it('D6: người chơi A đọc đăng ký của chính mình', async () => {
    const db = player('playerA');
    await assertSucceeds(getDoc(doc(db, 'dropInSessions/s1/registrations/playerA')));
    // Chưa đăng ký thì document không tồn tại, vẫn đọc được để biết "chưa đăng ký".
    await assertSucceeds(getDoc(doc(player('playerC'), 'dropInSessions/s1/registrations/playerC')));
  });

  it('D7: người chơi không liệt kê được cả danh sách đăng ký của buổi', async () => {
    const db = player('playerA');
    await assertFails(getDocs(collection(db, 'dropInSessions/s1/registrations')));
    await assertFails(getDocs(query(collection(db, 'dropInSessions/s1/registrations'), where('status', '==', 'REGISTERED'))));
  });

  it('D7 (được): người chơi truy vấn subcollection nhưng lọc đúng userId của mình', async () => {
    const db = player('playerA');
    await assertSucceeds(getDocs(query(collection(db, 'dropInSessions/s1/registrations'), where('userId', '==', 'playerA'))));
  });

  it('D8: chủ cơ sở mở buổi đọc và liệt kê đăng ký của buổi mình', async () => {
    const db = owner('owner1');
    await assertSucceeds(getDocs(collection(db, 'dropInSessions/s1/registrations')));
    await assertSucceeds(getDoc(doc(db, 'dropInSessions/s1/registrations/playerB')));
  });

  it('D9: chủ sân khác không liệt kê/đọc đăng ký của buổi không thuộc mình', async () => {
    const db = owner('owner2');
    await assertFails(getDocs(collection(db, 'dropInSessions/s1/registrations')));
    await assertFails(getDoc(doc(db, 'dropInSessions/s1/registrations/playerA')));
  });

  it('D9 (bị chặn): token PLAYER có uid trùng ownerId không được quyền chủ sân', async () => {
    const db = player('owner1');
    await assertFails(getDocs(collection(db, 'dropInSessions/s1/registrations')));
  });

  it('D12: admin đọc và liệt kê đăng ký bất kỳ', async () => {
    const db = admin();
    await assertSucceeds(getDoc(doc(db, 'dropInSessions/s1/registrations/playerB')));
    await assertSucceeds(getDocs(collection(db, 'dropInSessions/s2/registrations')));
    await assertSucceeds(getDocs(collectionGroup(db, 'registrations')));
  });

  it('D12 (bị chặn): token không có appRole ADMIN không liệt kê toàn bộ', async () => {
    const db = env.authenticatedContext('admin1', {}).firestore();
    await assertFails(getDocs(collectionGroup(db, 'registrations')));
  });
});

describe('collection group registrations — "Lượt vãng lai của tôi"', () => {
  it('D10: truy vấn collection group where userId == myUid', async () => {
    const db = player('playerA');
    await assertSucceeds(getDocs(query(collectionGroup(db, 'registrations'), where('userId', '==', 'playerA'))));
    await assertSucceeds(getDocs(query(
      collectionGroup(db, 'registrations'),
      where('userId', '==', 'playerA'),
      where('status', '==', 'REGISTERED'),
    )));
  });

  it('D11: collection group không lọc userId, hoặc lọc userId người khác, bị chặn', async () => {
    const db = player('playerA');
    await assertFails(getDocs(collectionGroup(db, 'registrations')));
    await assertFails(getDocs(query(collectionGroup(db, 'registrations'), where('status', '==', 'REGISTERED'))));
    await assertFails(getDocs(query(collectionGroup(db, 'registrations'), where('userId', '==', 'playerB'))));
  });

  it('D11 (bị chặn): khách chưa đăng nhập không truy vấn collection group', async () => {
    await assertFails(getDocs(query(collectionGroup(guest(), 'registrations'), where('userId', '==', 'playerA'))));
  });

  it('D11 (bị chặn): chủ sân không dùng collection group để đọc đăng ký của người khác', async () => {
    await assertFails(getDocs(query(collectionGroup(owner('owner1'), 'registrations'), where('venueId', '==', 'venueA'))));
  });
});
