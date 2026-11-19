// Dữ liệu và công cụ dùng chung cho test Deno của spec 091 (chỉ dùng trong test, cần Firebase Emulator).
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { requireUser } from "./auth.ts";
import {
  type ChatDeps,
  type ChatNotification,
  chatPath,
  directChatId,
  groupChatId,
  messagePath,
  participantPath,
} from "./chats.ts";
import * as firestore from "./firestore.ts";
import { commit, createWrite, type Write } from "./firestore.ts";
import { ensureUsers, FakeStorage, SEED_TIME } from "./groups_fixtures.ts";

export { ensureUsers, FakeStorage, SEED_TIME };

/** Thông báo `CHAT_MESSAGE` đã gửi (thay `deps.notify`, tức mock `send-notification`). */
export class ChatNotificationRecorder {
  readonly sent: ChatNotification[] = [];
  readonly notify = (n: ChatNotification): Promise<void> => {
    this.sent.push(n);
    return Promise.resolve();
  };
  recipients(messageId?: string): string[] {
    return this.sent.filter((n) => !messageId || n.data.messageId === messageId).map((n) => n.recipientUid).sort();
  }
  reset(): void {
    this.sent.length = 0;
  }
}

/** `ChatDeps` cho test: Firestore thật (Emulator), storage và notify giả. */
export function makeChatDeps(overrides: Partial<ChatDeps> = {}) {
  const storage = new FakeStorage();
  const notifications = new ChatNotificationRecorder();
  const deps: ChatDeps = {
    firestore,
    storage,
    authenticate: (req) => requireUser(req),
    notify: notifications.notify,
    now: () => new Date(),
    sleep: () => Promise.resolve(),
    ...overrides,
  };
  return { deps, storage, notifications };
}

export interface SeedMessage {
  id?: string;
  senderId: string;
  text?: string;
  imagePath?: string | null;
  status?: "NORMAL" | "RECALLED" | "HIDDEN";
  createdAt?: Date;
}

const snap = async (uid: string) => {
  const u = await firestore.get(`users/${uid}`);
  return {
    displayName: typeof u?.displayName === "string" ? u.displayName : null,
    avatarPath: typeof u?.avatarPath === "string" ? u.avatarPath : null,
  };
};

/** Ghi `participants` (mỗi người một doc) và `messages` (createdAt tăng dần mỗi giây), trả `lastMessage`. */
async function seedParticipantsAndMessages(
  chatId: string,
  memberIds: string[],
  messages: SeedMessage[],
  writes: Write[],
): Promise<Record<string, unknown> | null> {
  for (const uid of memberIds) {
    writes.push(createWrite(participantPath(chatId, uid), {
      userId: uid,
      chatId,
      userSnapshot: await snap(uid),
      joinedAt: SEED_TIME,
      lastReadAt: SEED_TIME,
      muted: false,
      blockedOther: false,
      viewingUntil: null,
    }));
  }
  let last: Record<string, unknown> | null = null;
  for (const [i, m] of messages.entries()) {
    const id = m.id ?? crypto.randomUUID();
    const createdAt = m.createdAt ?? new Date(SEED_TIME.getTime() + (i + 1) * 1000);
    const status = m.status ?? "NORMAL";
    const text = status === "RECALLED" ? "" : m.text ?? `Tin ${i + 1}`;
    writes.push(createWrite(messagePath(chatId, id), {
      senderId: m.senderId,
      senderSnapshot: await snap(m.senderId),
      senderDeleted: false,
      text,
      imagePath: status === "RECALLED" ? null : m.imagePath ?? null,
      createdAt,
      status,
      recalledAt: status === "RECALLED" ? createdAt : null,
    }));
    last = {
      messageId: id,
      senderId: m.senderId,
      type: m.imagePath ? "IMAGE" : "TEXT",
      preview: status === "RECALLED" ? "Tin nhắn đã được thu hồi" : text,
      createdAt,
    };
  }
  return last;
}

async function commitChunks(writes: Write[]): Promise<void> {
  for (let i = 0; i < writes.length; i += 400) await commit(writes.slice(i, i + 400));
}

/** Chat riêng `player`–`owner` (ID `{nhỏ}_{lớn}`), `unreadCounts` = 0; trả `chatId`. */
export async function seedDirectChat(
  player: string,
  owner: string,
  venueId: string,
  messages: SeedMessage[] = [],
  status: "ACTIVE" | "READ_ONLY" = "ACTIVE",
): Promise<string> {
  await ensureUsers([player, owner]);
  const chatId = directChatId(player, owner);
  const writes: Write[] = [];
  const lastMessage = await seedParticipantsAndMessages(chatId, [player, owner], messages, writes);
  writes.unshift(createWrite(chatPath(chatId), {
    type: "DIRECT",
    memberIds: [player, owner].sort(),
    groupId: null,
    ownerId: owner,
    playerId: player,
    venueId,
    venueSnapshot: { name: "Sân mẫu", photoPath: null },
    memberSnapshots: { [player]: await snap(player), [owner]: await snap(owner) },
    lastMessage,
    unreadCounts: { [player]: 0, [owner]: 0 },
    status,
    createdAt: SEED_TIME,
    updatedAt: SEED_TIME,
  }));
  await commitChunks(writes);
  return chatId;
}

/** `chats/group_{groupId}` khớp `memberIds`; trả `chatId`. */
export async function seedGroupChat(groupId: string, memberIds: string[], messages: SeedMessage[] = []): Promise<string> {
  await ensureUsers(memberIds);
  const chatId = groupChatId(groupId);
  const writes: Write[] = [];
  const lastMessage = await seedParticipantsAndMessages(chatId, memberIds, messages, writes);
  writes.unshift(createWrite(chatPath(chatId), {
    type: "GROUP",
    memberIds,
    groupId,
    ownerId: null,
    playerId: null,
    lastMessage,
    unreadCounts: Object.fromEntries(memberIds.map((u) => [u, 0])),
    status: "ACTIVE",
    createdAt: SEED_TIME,
    updatedAt: SEED_TIME,
  }));
  await commitChunks(writes);
  return chatId;
}

/** `users/{uid}` với `ownerStatus` (merge vào user có sẵn) và `venues/{venueId}`. */
export async function seedVenue(
  venueId: string,
  ownerId: string,
  opts: { status?: string; ownerStatus?: string } = {},
): Promise<void> {
  await ensureUsers([ownerId]);
  await commit([
    firestore.updateWrite(`users/${ownerId}`, { ownerStatus: opts.ownerStatus ?? "APPROVED" }),
    createWrite(`venues/${venueId}`, {
      name: `Sân ${venueId}`,
      ownerId,
      status: opts.status ?? "ACTIVE",
      photoPaths: [`${venueId}/cover.jpg`],
    }),
  ]);
}

/**
 * Bất biến của một chat đang hoạt động: khóa `unreadCounts` đúng bằng `memberIds`; đúng một `participants/{uid}`
 * cho mỗi `memberIds`; `lastMessage.messageId` là tin có `createdAt` lớn nhất (không có tin thì `lastMessage` rỗng).
 */
export async function assertChatInvariant(chatId: string): Promise<void> {
  const chat = await firestore.get(chatPath(chatId));
  assert(chat, `chats/${chatId} không tồn tại`);
  const members = (chat.memberIds as string[]).slice().sort();
  assertEquals(Object.keys((chat.unreadCounts ?? {}) as object).sort(), members, "khóa unreadCounts != memberIds");
  const parts = await firestore.query("participants", [], { parent: chatPath(chatId) });
  assertEquals(parts.map((p) => p.id).sort(), members, "participants != memberIds");
  const msgs = await firestore.query("messages", [], { parent: chatPath(chatId) });
  if (msgs.length === 0) return;
  const t = (m: { data: Record<string, unknown> }) => (m.data.createdAt as Date).getTime();
  const newest = msgs.reduce((a, b) => (t(b) > t(a) ? b : a));
  assertEquals((chat.lastMessage as { messageId: string }).messageId, newest.id, "lastMessage không phải tin mới nhất");
}
