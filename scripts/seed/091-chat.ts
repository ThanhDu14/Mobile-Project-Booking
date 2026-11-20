// Dữ liệu demo cho spec 091 (Chat), CHỈ trên Firebase Emulator (tasks.md T003, quickstart.md mục 1).
// Chạy SAU `scripts/seed/090-community.ts` (dùng player1…player25 và nhóm demo090-g1):
//
//   deno run -A --config supabase/functions/deno.json scripts/seed/091-chat.ts   # từ thư mục gốc repo
//
// (quickstart ghi `npx tsx` nhưng script import module Deno, giống seed 090: lệnh đúng là `deno run -A`.)
// Mặc định project `demo-smashnow`, Auth 127.0.0.1:9099, Firestore 127.0.0.1:8080; đổi bằng biến môi trường.
// Script từ chối chạy nếu host không phải máy này. ID cố định `demo091-*` nên chạy lại được (ghi đè).
//
// Tạo (mật khẩu `Test@1234`):
// - owner1@test.local (ownerStatus APPROVED) có 3 cơ sở: V1, V2 ACTIVE, V3 HIDDEN; owner2@test.local chưa duyệt (NONE).
// - Chat riêng player1–owner1: 40 tin (tin 11 là ảnh, tin 21 HIDDEN, tin 31 RECALLED).
// - Chat nhóm group_demo090-g1 khớp thành viên nhóm demo090-g1: 35 tin.
// - Chat riêng player2–owner1: owner1 đã chặn player2 (participants/owner1.blockedOther = true).
// Mọi chat có unreadCounts, participants khớp memberIds. Object ảnh `chat-media` KHÔNG được tải lên Storage
// (seed chỉ ghi Firestore): tin ảnh trỏ tới path chưa có object nên ảnh hiện lỗi cho tới khi tải thủ công.
import { setCustomClaims } from "../../supabase/functions/_shared/identity.ts";
import { commit, documentName, encodeFields, projectId, query, type Write } from "../../supabase/functions/_shared/firestore.ts";

const defaults: Record<string, string> = {
  FIREBASE_PROJECT_ID: "demo-smashnow",
  FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099",
  FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080",
};
for (const [k, v] of Object.entries(defaults)) if (!Deno.env.get(k)) Deno.env.set(k, v);
for (const k of ["FIREBASE_AUTH_EMULATOR_HOST", "FIRESTORE_EMULATOR_HOST"]) {
  const host = Deno.env.get(k)!;
  if (!/^(127\.0\.0\.1|localhost|0\.0\.0\.0|10\.0\.2\.2):\d+$/.test(host)) {
    console.error(`${k}=${host} không phải Emulator trên máy này. Dừng.`);
    Deno.exit(1);
  }
}

const PREFIX = "demo091";
const PASSWORD = "Test@1234";
const G1 = "demo090-g1";
const MIN = 60_000;
const now = new Date();

const setWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
});
const mergeWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
  updateMask: { fieldPaths: Object.keys(data) },
});
async function commitChunks(writes: Write[]) {
  for (let i = 0; i < writes.length; i += 400) await commit(writes.slice(i, i + 400));
}

// ---------- Tài khoản ----------

const authBase = () =>
  `http://${Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")}/identitytoolkit.googleapis.com/v1/projects/${projectId()}`;
const authHeaders = { Authorization: "Bearer owner", "Content-Type": "application/json" };

async function findUidByEmail(email: string): Promise<string | null> {
  const res = await fetch(`${authBase()}/accounts:lookup`, {
    method: "POST",
    headers: authHeaders,
    body: JSON.stringify({ email: [email] }),
  });
  if (!res.ok) throw new Error(`Không tra được ${email}: ${res.status} ${await res.text()}`);
  return (await res.json()).users?.[0]?.localId ?? null;
}

async function ensureAuthUser(uid: string, email: string, displayName: string, appRole: string): Promise<string> {
  const existing = await findUidByEmail(email);
  const finalUid = existing ?? uid;
  if (!existing) {
    const res = await fetch(`${authBase()}/accounts`, {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({ localId: uid, email, password: PASSWORD, displayName, emailVerified: true }),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`Không tạo được ${email}: ${res.status} ${text}`);
  }
  await setCustomClaims(finalUid, { role: "authenticated", appRole });
  return finalUid;
}

async function playerUid(n: number): Promise<string> {
  const uid = await findUidByEmail(`player${n}@test.local`);
  if (!uid) throw new Error(`Chưa có player${n}@test.local: chạy scripts/seed/090-community.ts trước`);
  return uid;
}

const userSnap = (_uid: string, name: string) => ({ displayName: name, avatarPath: null });

// ---------- Dữ liệu ----------

const owner1 = await ensureAuthUser(`${PREFIX}-owner1`, "owner1@test.local", "Chủ sân 1", "OWNER");
const owner2 = await ensureAuthUser(`${PREFIX}-owner2`, "owner2@test.local", "Chủ sân 2", "OWNER");
const p1 = await playerUid(1);
const p2 = await playerUid(2);

const names = new Map<string, string>([[owner1, "Chủ sân 1"], [owner2, "Chủ sân 2"]]);
const nameOf = (uid: string) => names.get(uid) ?? `Người chơi ${uid.match(/\d+$/)?.[0] ?? uid.slice(-4)}`;

const writes: Write[] = [];
for (const [uid, email, name, status] of [
  [owner1, "owner1@test.local", "Chủ sân 1", "APPROVED"],
  [owner2, "owner2@test.local", "Chủ sân 2", "NONE"],
] as const) {
  writes.push(mergeWrite(`users/${uid}`, {
    displayName: name,
    email,
    avatarPath: null,
    role: "OWNER",
    ownerStatus: status,
    accountStatus: "ACTIVE",
    fcmTokens: [],
    notificationPrefs: {},
    createdAt: now,
    updatedAt: now,
  }));
}
const venues = [
  { id: `${PREFIX}-v1`, name: "Sân Cầu Lông Quận 7 - Cơ sở 1", status: "ACTIVE" },
  { id: `${PREFIX}-v2`, name: "Sân Cầu Lông Quận 7 - Cơ sở 2", status: "ACTIVE" },
  { id: `${PREFIX}-v3`, name: "Sân Cầu Lông Quận 7 - Cơ sở 3 (đang ẩn)", status: "HIDDEN" },
];
for (const v of venues) {
  writes.push(mergeWrite(`venues/${v.id}`, {
    name: v.name,
    ownerId: owner1,
    status: v.status,
    districtId: "q7",
    photoPaths: [],
    updatedAt: now,
  }));
}

interface Msg {
  id: string;
  senderId: string;
  text: string;
  imagePath?: string | null;
  status?: "NORMAL" | "RECALLED" | "HIDDEN";
}

/** Ghi chat + participants + messages (createdAt cách nhau 1 phút, tin cuối là tin mới nhất). */
function chatWrites(chat: {
  id: string;
  type: "DIRECT" | "GROUP";
  memberIds: string[];
  extra: Record<string, unknown>;
  messages: Msg[];
  unread: Record<string, number>;
  participantOverrides?: Record<string, Record<string, unknown>>;
}): Write[] {
  const out: Write[] = [];
  const start = now.getTime() - chat.messages.length * MIN;
  const at = (i: number) => new Date(start + i * MIN);
  const last = chat.messages[chat.messages.length - 1];
  const lastStatus = last.status ?? "NORMAL";
  out.push(setWrite(`chats/${chat.id}`, {
    type: chat.type,
    memberIds: chat.memberIds,
    status: "ACTIVE",
    lastMessage: {
      messageId: last.id,
      senderId: last.senderId,
      type: last.imagePath ? "IMAGE" : "TEXT",
      preview: lastStatus === "RECALLED" ? "Tin nhắn đã được thu hồi" : last.text || "[Hình ảnh]",
      createdAt: at(chat.messages.length - 1),
    },
    unreadCounts: Object.fromEntries(chat.memberIds.map((u) => [u, chat.unread[u] ?? 0])),
    createdAt: at(0),
    updatedAt: at(chat.messages.length - 1),
    ...chat.extra,
  }));
  for (const uid of chat.memberIds) {
    out.push(setWrite(`chats/${chat.id}/participants/${uid}`, {
      userId: uid,
      chatId: chat.id,
      userSnapshot: userSnap(uid, nameOf(uid)),
      joinedAt: at(0),
      lastReadAt: at(0),
      muted: false,
      blockedOther: false,
      viewingUntil: null,
      ...chat.participantOverrides?.[uid],
    }));
  }
  chat.messages.forEach((m, i) => {
    const status = m.status ?? "NORMAL";
    out.push(setWrite(`chats/${chat.id}/messages/${m.id}`, {
      senderId: m.senderId,
      senderSnapshot: userSnap(m.senderId, nameOf(m.senderId)),
      senderDeleted: false,
      text: status === "RECALLED" ? "" : m.text,
      imagePath: status === "RECALLED" ? null : m.imagePath ?? null,
      createdAt: at(i),
      status,
      recalledAt: status === "RECALLED" ? at(i) : null,
    }));
  });
  return out;
}

const pad = (n: number) => String(n).padStart(3, "0");
const directId = (a: string, b: string) => [a, b].sort().join("_");

// Chat riêng player1–owner1: 40 tin xen kẽ; tin 11 ảnh, 21 HIDDEN, 31 RECALLED.
const chatA = directId(p1, owner1);
const msgsA: Msg[] = Array.from({ length: 40 }, (_, i) => {
  const n = i + 1;
  const id = `${PREFIX}-a${pad(n)}`;
  const senderId = n % 2 === 1 ? p1 : owner1;
  const m: Msg = { id, senderId, text: n % 2 === 1 ? `Tối nay còn sân không? (${n})` : `Còn sân lúc 19h ạ (${n})` };
  if (n === 11) Object.assign(m, { text: "Ảnh sân", imagePath: `${chatA}/${id}.jpg` });
  if (n === 21) m.status = "HIDDEN";
  if (n === 31) m.status = "RECALLED";
  return m;
});
writes.push(...chatWrites({
  id: chatA,
  type: "DIRECT",
  memberIds: [p1, owner1].sort(),
  extra: {
    groupId: null,
    ownerId: owner1,
    playerId: p1,
    venueId: venues[0].id,
    venueSnapshot: { name: venues[0].name, photoPath: null },
    memberSnapshots: { [p1]: userSnap(p1, nameOf(p1)), [owner1]: userSnap(owner1, "Chủ sân 1") },
  },
  messages: msgsA,
  unread: { [owner1]: 2, [p1]: 0 },
}));

// Chat nhóm group_G1: khớp members của nhóm demo090-g1 (đọc từ Firestore), 35 tin do các thành viên xen kẽ.
const g1Members = (await query("members", [], { parent: `groups/${G1}` })).map((m) => m.id);
if (g1Members.length === 0) throw new Error(`Chưa có nhóm ${G1}: chạy scripts/seed/090-community.ts trước`);
const msgsG: Msg[] = Array.from({ length: 35 }, (_, i) => ({
  id: `${PREFIX}-g${pad(i + 1)}`,
  senderId: g1Members[i % g1Members.length],
  text: `Tin nhóm số ${i + 1}`,
}));
writes.push(...chatWrites({
  id: `group_${G1}`,
  type: "GROUP",
  memberIds: g1Members,
  extra: { groupId: G1, ownerId: null, playerId: null },
  messages: msgsG,
  unread: Object.fromEntries(g1Members.map((u) => [u, 3])),
}));

// Chat riêng player2–owner1: owner1 đã chặn player2.
const chatB = directId(p2, owner1);
writes.push(...chatWrites({
  id: chatB,
  type: "DIRECT",
  memberIds: [p2, owner1].sort(),
  extra: {
    groupId: null,
    ownerId: owner1,
    playerId: p2,
    venueId: venues[1].id,
    venueSnapshot: { name: venues[1].name, photoPath: null },
    memberSnapshots: { [p2]: userSnap(p2, nameOf(p2)), [owner1]: userSnap(owner1, "Chủ sân 1") },
  },
  messages: [
    { id: `${PREFIX}-b001`, senderId: p2, text: "Cho mình hỏi giá sân cuối tuần" },
    { id: `${PREFIX}-b002`, senderId: owner1, text: "Bạn xem bảng giá trong ứng dụng nhé" },
    { id: `${PREFIX}-b003`, senderId: p2, text: "Cảm ơn bạn" },
  ],
  unread: { [owner1]: 1 },
  participantOverrides: { [owner1]: { blockedOther: true } },
}));

await commitChunks(writes);
console.log(`Seed 091 xong: owner1=${owner1}, owner2=${owner2}, chat ${chatA} (40 tin), group_${G1} (35 tin), ${chatB} (bị chặn).`);
