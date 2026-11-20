// GR1–GR12 của specs/090-community-group/contracts/security-rules.md (spec 090, C).
// Rules chỉ mở quyền đọc; mọi ghi nhóm/thành viên/bài đi qua Edge Function (service account).
// Lọc theo mã: vitest -t "GR5".
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
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

const CREATED = Timestamp.fromMillis(1_762_000_000_000);
const EVENT_AT = Timestamp.fromMillis(Date.UTC(2026, 10, 25, 11, 0));

// Đúng schema data-model §1.
function group(ownerId: string, overrides: Record<string, unknown> = {}) {
  return {
    name: 'CLB Cầu Lông Q10',
    nameKeywords: ['clb', 'cau', 'long', 'q10'],
    description: 'Giao lưu cuối tuần',
    avatarPath: null,
    districtId: 'q10',
    ownerId,
    visibility: 'PUBLIC',
    memberCount: 4,
    pendingRequestCount: 1,
    status: 'ACTIVE',
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

// Đúng schema data-model §2: ID document = userId.
function member(groupId: string, userId: string, role: string, overrides: Record<string, unknown> = {}) {
  return {
    userId,
    groupId,
    role,
    userSnapshot: { displayName: `Tên ${userId}`, avatarPath: null },
    joinedAt: CREATED,
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

// Đúng schema data-model §5.
function post(groupId: string, authorId: string, overrides: Record<string, unknown> = {}) {
  return {
    groupId,
    authorId,
    authorSnapshot: { displayName: `Tên ${authorId}`, avatarPath: null },
    authorDeleted: false,
    type: 'EVENT',
    content: 'Đánh giao lưu tối thứ Sáu',
    photoPaths: [],
    eventTitle: 'Giao lưu tối T6',
    eventAt: EVENT_AT,
    eventLocation: 'Sân Trường Chinh',
    expectedCount: 10,
    attendeeCount: 1,
    status: 'VISIBLE',
    hiddenBy: null,
    hiddenByUid: null,
    editedAt: null,
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

const guest = () => env.unauthenticatedContext().firestore();
const player = (uid: string) => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

// Nhóm g1: PUBLIC, ACTIVE; owner1 (OWNER), mod1 (ADMIN), mem1, mem2 (MEMBER).
// Nhóm g2: PRIVATE, ACTIVE; owner2. Nhóm g3: DELETED (đang xóa dở), owner3.
// outsider không thuộc nhóm nào; asker có yêu cầu tham gia g1.
beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin1');
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'groups/g1'), group('owner1'));
    await setDoc(doc(db, 'groups/g2'), group('owner2', { visibility: 'PRIVATE', memberCount: 1 }));
    await setDoc(doc(db, 'groups/g3'), group('owner3', { status: 'DELETED' }));

    await setDoc(doc(db, 'groups/g1/members/owner1'), member('g1', 'owner1', 'OWNER'));
    await setDoc(doc(db, 'groups/g1/members/mod1'), member('g1', 'mod1', 'ADMIN'));
    await setDoc(doc(db, 'groups/g1/members/mem1'), member('g1', 'mem1', 'MEMBER'));
    await setDoc(doc(db, 'groups/g1/members/mem2'), member('g1', 'mem2', 'MEMBER'));
    await setDoc(doc(db, 'groups/g2/members/owner2'), member('g2', 'owner2', 'OWNER'));
    await setDoc(doc(db, 'groups/g2/members/mem1'), member('g2', 'mem1', 'MEMBER'));

    await setDoc(doc(db, 'groups/g1/joinRequests/asker'), {
      userId: 'asker',
      userSnapshot: { displayName: 'Tên asker', avatarPath: null },
      wasRemoved: false,
      requestId: '00000000-0000-4000-8000-000000000001',
      createdAt: CREATED,
    });
    await setDoc(doc(db, 'groups/g1/removals/oldmember'), {
      userId: 'oldmember',
      removedBy: 'owner1',
      removedAt: CREATED,
    });

    await setDoc(doc(db, 'groups/g1/posts/pVisible'), post('g1', 'mem1'));
    await setDoc(doc(db, 'groups/g1/posts/pHidden'), post('g1', 'mem1', { status: 'HIDDEN', hiddenBy: 'GROUP', hiddenByUid: 'mod1' }));
    await setDoc(doc(db, 'groups/g1/posts/pVisible/attendees/mem1'), {
      userId: 'mem1',
      groupId: 'g1',
      postId: 'pVisible',
      eventAt: EVENT_AT,
      userSnapshot: { displayName: 'Tên mem1', avatarPath: null },
      joinedAt: CREATED,
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('groups/{groupId} — get/list và write', () => {
  it('GR1: khách chưa đăng nhập đọc nhóm ACTIVE công khai và riêng tư', async () => {
    const db = guest();
    await assertSucceeds(getDoc(doc(db, 'groups/g1')));
    await assertSucceeds(getDoc(doc(db, 'groups/g2')));
    await assertSucceeds(getDocs(query(collection(db, 'groups'), where('status', '==', 'ACTIVE'))));
  });

  it('GR1 (bị chặn): đọc nhóm DELETED, hoặc list không lọc status', async () => {
    await assertFails(getDoc(doc(guest(), 'groups/g3')));
    await assertFails(getDoc(doc(player('mem1'), 'groups/g3')));
    await assertFails(getDocs(collection(guest(), 'groups')));
    await assertFails(getDocs(query(collection(guest(), 'groups'), where('status', '==', 'DELETED'))));
  });

  it('GR1 (bị chặn): nhóm không tồn tại không gây lỗi bất thường, chỉ bị chặn', async () => {
    await assertFails(getDoc(doc(guest(), 'groups/khongco')));
  });

  it('GR2: chủ nhóm tự ghi memberCount, ownerId, name bị chặn', async () => {
    const ref = doc(player('owner1'), 'groups/g1');
    await assertFails(updateDoc(ref, { memberCount: 100 }));
    await assertFails(updateDoc(ref, { ownerId: 'owner1x' }));
    await assertFails(updateDoc(ref, { name: 'Tên mới' }));
    await assertFails(deleteDoc(ref));
  });

  it('GR2 (bị chặn): người chơi tạo groups/x; admin cũng không ghi từ client', async () => {
    await assertFails(setDoc(doc(player('outsider'), 'groups/x'), group('outsider')));
    await assertFails(setDoc(doc(admin(), 'groups/x'), group('admin1')));
    await assertFails(updateDoc(doc(admin(), 'groups/g1'), { status: 'LOCKED' }));
  });
});

describe('groups/{groupId}/members/{uid}', () => {
  it('GR3: thành viên và admin đọc danh sách thành viên', async () => {
    await assertSucceeds(getDocs(collection(player('mem1'), 'groups/g1/members')));
    await assertSucceeds(getDoc(doc(player('mem1'), 'groups/g1/members/mem2')));
    await assertSucceeds(getDocs(collection(admin(), 'groups/g1/members')));
  });

  it('GR3 (bị chặn): người ngoài đọc members của nhóm công khai; khách; thành viên nhóm khác', async () => {
    await assertFails(getDocs(collection(player('outsider'), 'groups/g1/members')));
    await assertFails(getDoc(doc(player('outsider'), 'groups/g1/members/mem1')));
    await assertFails(getDocs(collection(guest(), 'groups/g1/members')));
    // owner2 chỉ thuộc g2.
    await assertFails(getDocs(collection(player('owner2'), 'groups/g1/members')));
  });

  it('GR4: người chơi tự tạo members/{mình} (tự vào nhóm) bị chặn', async () => {
    await assertFails(setDoc(doc(player('outsider'), 'groups/g1/members/outsider'), member('g1', 'outsider', 'MEMBER')));
    // Thành viên không tự nâng quyền hay xóa chính mình.
    await assertFails(updateDoc(doc(player('mem1'), 'groups/g1/members/mem1'), { role: 'OWNER' }));
    await assertFails(deleteDoc(doc(player('mem1'), 'groups/g1/members/mem1')));
    await assertFails(setDoc(doc(admin(), 'groups/g1/members/admin1'), member('g1', 'admin1', 'MEMBER')));
  });
});

describe('collection group members — "Nhóm của tôi"', () => {
  it('GR5: player1 list collection group where userId == player1', async () => {
    const db = player('mem1');
    const snap = await assertSucceeds(getDocs(query(collectionGroup(db, 'members'), where('userId', '==', 'mem1'))));
    // mem1 thuộc g1 và g2.
    if (snap.size !== 2) throw new Error(`Mong 2 nhóm, nhận ${snap.size}`);
    await assertSucceeds(getDocs(query(
      collectionGroup(db, 'members'),
      where('userId', '==', 'mem1'),
    )));
  });

  it('GR5 (bị chặn): list không lọc userId, lọc userId người khác, hoặc là khách', async () => {
    const db = player('mem1');
    await assertFails(getDocs(collectionGroup(db, 'members')));
    await assertFails(getDocs(query(collectionGroup(db, 'members'), where('role', '==', 'OWNER'))));
    await assertFails(getDocs(query(collectionGroup(db, 'members'), where('userId', '==', 'mem2'))));
    await assertFails(getDocs(query(collectionGroup(guest(), 'members'), where('userId', '==', 'mem1'))));
  });
});

describe('groups/{groupId}/joinRequests/{uid}', () => {
  it('GR6: người xin đọc yêu cầu của mình; quản trị viên, chủ nhóm, admin đọc danh sách', async () => {
    await assertSucceeds(getDoc(doc(player('asker'), 'groups/g1/joinRequests/asker')));
    await assertSucceeds(getDocs(collection(player('mod1'), 'groups/g1/joinRequests')));
    await assertSucceeds(getDocs(collection(player('owner1'), 'groups/g1/joinRequests')));
    await assertSucceeds(getDocs(collection(admin(), 'groups/g1/joinRequests')));
  });

  it('GR6 (bị chặn): thành viên thường đọc danh sách yêu cầu; người ngoài đọc yêu cầu của người khác', async () => {
    await assertFails(getDocs(collection(player('mem1'), 'groups/g1/joinRequests')));
    await assertFails(getDoc(doc(player('mem1'), 'groups/g1/joinRequests/asker')));
    await assertFails(getDoc(doc(player('outsider'), 'groups/g1/joinRequests/asker')));
    await assertFails(getDocs(collection(guest(), 'groups/g1/joinRequests')));
  });

  it('GR6 (bị chặn): người xin không tự tạo hay xóa yêu cầu từ client', async () => {
    await assertFails(setDoc(doc(player('outsider'), 'groups/g1/joinRequests/outsider'), { userId: 'outsider' }));
    await assertFails(deleteDoc(doc(player('asker'), 'groups/g1/joinRequests/asker')));
  });
});

describe('groups/{groupId}/removals/{uid}', () => {
  it('GR7: chủ nhóm, quản trị viên và admin đọc removals', async () => {
    await assertSucceeds(getDoc(doc(player('owner1'), 'groups/g1/removals/oldmember')));
    await assertSucceeds(getDocs(collection(player('mod1'), 'groups/g1/removals')));
    await assertSucceeds(getDocs(collection(admin(), 'groups/g1/removals')));
  });

  it('GR7 (bị chặn): thành viên thường, người bị mời ra, người ngoài đọc removals', async () => {
    await assertFails(getDocs(collection(player('mem1'), 'groups/g1/removals')));
    await assertFails(getDoc(doc(player('oldmember'), 'groups/g1/removals/oldmember')));
    await assertFails(getDoc(doc(player('outsider'), 'groups/g1/removals/oldmember')));
    await assertFails(setDoc(doc(player('owner1'), 'groups/g1/removals/x'), { userId: 'x' }));
  });
});

describe('groups/{groupId}/posts/{postId} — đọc', () => {
  it('GR8: thành viên list bài where status == VISIBLE', async () => {
    const db = player('mem2');
    const snap = await assertSucceeds(getDocs(query(
      collection(db, 'groups/g1/posts'),
      where('status', '==', 'VISIBLE'),
    )));
    if (snap.size !== 1) throw new Error(`Mong 1 bài VISIBLE, nhận ${snap.size}`);
    await assertSucceeds(getDoc(doc(db, 'groups/g1/posts/pVisible')));
  });

  it('GR8 (bị chặn): thành viên list không lọc status; người ngoài và khách get/list bài VISIBLE (FR-014)', async () => {
    await assertFails(getDocs(collection(player('mem2'), 'groups/g1/posts')));
    await assertFails(getDoc(doc(player('outsider'), 'groups/g1/posts/pVisible')));
    await assertFails(getDocs(query(collection(player('outsider'), 'groups/g1/posts'), where('status', '==', 'VISIBLE'))));
    await assertFails(getDoc(doc(guest(), 'groups/g1/posts/pVisible')));
    // Thành viên nhóm g2 không đọc được bài của g1.
    await assertFails(getDoc(doc(player('owner2'), 'groups/g1/posts/pVisible')));
  });

  it('GR9: người đăng, quản trị viên, chủ nhóm và admin get bài HIDDEN', async () => {
    await assertSucceeds(getDoc(doc(player('mem1'), 'groups/g1/posts/pHidden')));
    await assertSucceeds(getDoc(doc(player('mod1'), 'groups/g1/posts/pHidden')));
    await assertSucceeds(getDoc(doc(player('owner1'), 'groups/g1/posts/pHidden')));
    await assertSucceeds(getDoc(doc(admin(), 'groups/g1/posts/pHidden')));
    // Người đăng xem bài ẩn của mình qua query authorId; quản trị viên list không lọc.
    await assertSucceeds(getDocs(query(collection(player('mem1'), 'groups/g1/posts'), where('authorId', '==', 'mem1'))));
    await assertSucceeds(getDocs(collection(player('mod1'), 'groups/g1/posts')));
  });

  it('GR9 (bị chặn): thành viên khác get bài HIDDEN (FR-019, SC-007)', async () => {
    await assertFails(getDoc(doc(player('mem2'), 'groups/g1/posts/pHidden')));
    await assertFails(getDoc(doc(player('outsider'), 'groups/g1/posts/pHidden')));
    await assertFails(getDocs(query(collection(player('mem2'), 'groups/g1/posts'), where('authorId', '==', 'mem1'))));
  });
});

describe('groups/{groupId}/posts/{postId} — ghi', () => {
  it('GR10: người đăng tự sửa content, status, attendeeCount của bài mình bị chặn', async () => {
    const ref = doc(player('mem1'), 'groups/g1/posts/pVisible');
    await assertFails(updateDoc(ref, { content: 'Sửa trái phép' }));
    await assertFails(updateDoc(ref, { status: 'HIDDEN' }));
    await assertFails(updateDoc(ref, { attendeeCount: 99 }));
    await assertFails(deleteDoc(ref));
  });

  it('GR10 (bị chặn): thành viên tạo bài; chủ nhóm, admin cũng không ghi từ client', async () => {
    await assertFails(setDoc(doc(player('mem2'), 'groups/g1/posts/pNew'), post('g1', 'mem2')));
    await assertFails(updateDoc(doc(player('owner1'), 'groups/g1/posts/pVisible'), { status: 'HIDDEN' }));
    await assertFails(updateDoc(doc(admin(), 'groups/g1/posts/pVisible'), { status: 'HIDDEN' }));
  });
});

describe('groups/{groupId}/posts/{postId}/attendees/{uid}', () => {
  it('GR11: thành viên và admin đọc danh sách người tham gia', async () => {
    await assertSucceeds(getDocs(collection(player('mem2'), 'groups/g1/posts/pVisible/attendees')));
    await assertSucceeds(getDoc(doc(player('mem2'), 'groups/g1/posts/pVisible/attendees/mem1')));
    await assertSucceeds(getDocs(collection(admin(), 'groups/g1/posts/pVisible/attendees')));
  });

  it('GR11 (bị chặn): người ngoài và khách đọc người tham gia', async () => {
    await assertFails(getDocs(collection(player('outsider'), 'groups/g1/posts/pVisible/attendees')));
    await assertFails(getDoc(doc(player('outsider'), 'groups/g1/posts/pVisible/attendees/mem1')));
    await assertFails(getDocs(collection(guest(), 'groups/g1/posts/pVisible/attendees')));
  });

  it('GR12: thành viên tự tạo attendees/{mình} bị chặn', async () => {
    const ref = doc(player('mem2'), 'groups/g1/posts/pVisible/attendees/mem2');
    await assertFails(setDoc(ref, {
      userId: 'mem2',
      groupId: 'g1',
      postId: 'pVisible',
      eventAt: EVENT_AT,
      userSnapshot: { displayName: 'Tên mem2', avatarPath: null },
      joinedAt: CREATED,
    }));
    // Cũng không xóa attendees của mình (bỏ tham gia đi qua Edge Function).
    await assertFails(deleteDoc(doc(player('mem1'), 'groups/g1/posts/pVisible/attendees/mem1')));
  });
});
