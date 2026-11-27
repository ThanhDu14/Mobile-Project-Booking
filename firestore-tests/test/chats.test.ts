// CR1–CR8 của specs/091-chat/contracts/security-rules.md (spec 091).
// Tin nhắn ghi qua Edge Function; client chỉ ghi unreadCounts.{mình} = 0 và participants/{mình}.
// Lọc theo mã: vitest -t "CR5".
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;
const CREATED = Timestamp.fromMillis(1_762_000_000_000);

const player = (uid: string) => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();

function chat(memberIds: string[], overrides: Record<string, unknown> = {}) {
  return {
    type: 'DIRECT',
    memberIds,
    ownerId: memberIds[1],
    playerId: memberIds[0],
    status: 'ACTIVE',
    unreadCounts: Object.fromEntries(memberIds.map((u) => [u, 3])),
    lastMessage: { messageId: 'm1', senderId: memberIds[0], type: 'TEXT', preview: 'Xin chào', createdAt: CREATED },
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

const participant = (chatId: string, userId: string, overrides: Record<string, unknown> = {}) => ({
  userId,
  chatId,
  userSnapshot: { displayName: `Tên ${userId}`, avatarPath: null },
  joinedAt: CREATED,
  lastReadAt: CREATED,
  muted: false,
  blockedOther: false,
  viewingUntil: null,
  ...overrides,
});

const message = (senderId: string) => ({
  senderId,
  senderSnapshot: { displayName: `Tên ${senderId}`, avatarPath: null },
  senderDeleted: false,
  text: 'Tối thứ 7 còn sân không?',
  imagePath: null,
  createdAt: CREATED,
  status: 'NORMAL',
  recalledAt: null,
});

beforeAll(async () => {
  env = await createTestEnv();
});
afterAll(async () => {
  await env.cleanup();
});

// c1: p1 (người chơi) và o1 (chủ sân); c2: p2 và o2; admin1 là admin hiện hành.
beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin1');
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'chats/c1'), chat(['p1', 'o1']));
    await setDoc(doc(db, 'chats/c2'), chat(['p2', 'o2']));
    for (const [c, u] of [['c1', 'p1'], ['c1', 'o1'], ['c2', 'p2'], ['c2', 'o2']]) {
      await setDoc(doc(db, `chats/${c}/participants/${u}`), participant(c, u));
    }
    for (let i = 0; i < 35; i++) {
      await setDoc(doc(db, `chats/c1/messages/m${i}`), {
        ...message('p1'),
        createdAt: Timestamp.fromMillis(1_762_000_000_000 + i),
      });
    }
    await setDoc(doc(db, 'chats/c2/messages/x1'), message('p2'));
  });
});

describe('CR1 chats get/list', () => {
  it('người trong cuộc get; list có array-contains mình; admin get', async () => {
    await assertSucceeds(getDoc(doc(player('p1'), 'chats/c1')));
    await assertSucceeds(getDocs(query(collection(player('p1'), 'chats'), where('memberIds', 'array-contains', 'p1'))));
    await assertSucceeds(getDoc(doc(admin(), 'chats/c1')));
  });
  it('người ngoài get; list không lọc array-contains mình', async () => {
    await assertFails(getDoc(doc(player('p2'), 'chats/c1')));
    await assertFails(getDocs(collection(player('p1'), 'chats')));
    await assertFails(getDocs(query(collection(player('p1'), 'chats'), where('memberIds', 'array-contains', 'p2'))));
  });
});

describe('CR2 chats update unreadCounts', () => {
  it('đặt unreadCounts.{mình} = 0', async () => {
    await assertSucceeds(updateDoc(doc(player('p1'), 'chats/c1'), { 'unreadCounts.p1': 0 }));
  });
  it('đặt của người khác, của mình = 5, sửa lastMessage / memberIds, người ngoài', async () => {
    const db = player('p1');
    await assertFails(updateDoc(doc(db, 'chats/c1'), { 'unreadCounts.o1': 0 }));
    await assertFails(updateDoc(doc(db, 'chats/c1'), { 'unreadCounts.p1': 5 }));
    await assertFails(updateDoc(doc(db, 'chats/c1'), { 'lastMessage.preview': 'sửa' }));
    await assertFails(updateDoc(doc(db, 'chats/c1'), { memberIds: ['p1', 'o1', 'p9'] }));
    await assertFails(updateDoc(doc(player('p2'), 'chats/c1'), { 'unreadCounts.p2': 0 }));
  });
});

describe('CR3 chats create/delete', () => {
  it('người chơi tự tạo chat hoặc xóa chat đều bị chặn', async () => {
    await assertFails(setDoc(doc(player('p1'), 'chats/p1_o9'), chat(['p1', 'o9'])));
    await assertFails(deleteDoc(doc(player('p1'), 'chats/c1')));
  });
});

describe('CR4 participants get', () => {
  it('đọc document của mình; admin đọc', async () => {
    await assertSucceeds(getDoc(doc(player('p1'), 'chats/c1/participants/p1')));
    await assertSucceeds(getDoc(doc(admin(), 'chats/c1/participants/p1')));
  });
  it('đọc document của người kia', async () => {
    await assertFails(getDoc(doc(player('p1'), 'chats/c1/participants/o1')));
  });
});

describe('CR5 participants update', () => {
  const ref = (uid = 'p1', as = uid) => doc(player(as), `chats/c1/participants/${uid}`);
  it('lastReadAt = request.time, muted, blockedOther, viewingUntil = now + 60s', async () => {
    await assertSucceeds(updateDoc(ref(), { lastReadAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref(), { muted: true }));
    await assertSucceeds(updateDoc(ref(), { blockedOther: true }));
    await assertSucceeds(updateDoc(ref(), { viewingUntil: Timestamp.fromMillis(Date.now() + 60_000) }));
    await assertSucceeds(updateDoc(ref(), { viewingUntil: null }));
  });
  it('ghi của người khác, userSnapshot, lastReadAt giờ client, viewingUntil > 10 phút', async () => {
    await assertFails(updateDoc(ref('o1', 'p1'), { muted: true }));
    await assertFails(updateDoc(ref(), { userSnapshot: { displayName: 'Giả', avatarPath: null } }));
    await assertFails(updateDoc(ref(), { lastReadAt: Timestamp.fromMillis(Date.now() - 1000) }));
    await assertFails(updateDoc(ref(), { viewingUntil: Timestamp.fromMillis(Date.now() + 3_600_000) }));
  });
  it('ghi sau khi đã rời nhóm (không còn trong memberIds)', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'chats/c1'), { memberIds: ['o1'] });
    });
    await assertFails(updateDoc(ref(), { muted: true }));
  });
});

describe('CR6 messages read', () => {
  it('người trong cuộc list 30 tin; admin get', async () => {
    const q = query(collection(player('p1'), 'chats/c1/messages'), orderBy('createdAt', 'desc'), limit(30));
    await assertSucceeds(getDocs(q));
    await assertSucceeds(getDoc(doc(admin(), 'chats/c1/messages/m1')));
  });
  it('người ngoài list; người vừa bị mời ra khỏi nhóm list', async () => {
    await assertFails(getDocs(query(collection(player('p2'), 'chats/c1/messages'), limit(30))));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'chats/c1'), { memberIds: ['o1'] });
    });
    await assertFails(getDocs(query(collection(player('p1'), 'chats/c1/messages'), limit(30))));
  });
});

describe('CR7 messages write', () => {
  it('tự tạo tin, tự sửa status, xóa tin đều bị chặn', async () => {
    const db = player('p1');
    await assertFails(setDoc(doc(db, 'chats/c1/messages/new1'), message('p1')));
    await assertFails(updateDoc(doc(db, 'chats/c1/messages/m1'), { status: 'NORMAL' }));
    await assertFails(deleteDoc(doc(db, 'chats/c1/messages/m1')));
  });
});

describe('CR8 collection group participants list', () => {
  it('where userId == mình', async () => {
    await assertSucceeds(getDocs(query(collectionGroup(player('p1'), 'participants'), where('userId', '==', 'p1'))));
  });
  it('không lọc userId, hoặc lọc người khác', async () => {
    await assertFails(getDocs(collectionGroup(player('p1'), 'participants')));
    await assertFails(getDocs(query(collectionGroup(player('p1'), 'participants'), where('userId', '==', 'o1'))));
  });
});
