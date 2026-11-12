// Dữ liệu demo cho spec 090 (Nhóm/CLB), CHỈ trên Firebase Emulator (tasks.md T003, quickstart.md mục 1).
//
//   firebase emulators:start --only auth,firestore --project demo-smashnow     # terminal khác
//   deno run -A --config supabase/functions/deno.json scripts/seed/090-community.ts# từ thư mục gốc repo
//
// LƯU Ý: quickstart.md mục 1 ghi `npx tsx ...` nhưng script import module Deno (`_shared/*.ts` dùng `npm:` specifier,
// đuôi `.ts`), Node/tsx không nạp được. Lệnh đúng là `deno run -A` như seed 080 (cần sửa quickstart cho khớp).
// Mặc định project `demo-smashnow`, Auth 127.0.0.1:9099, Firestore 127.0.0.1:8080; đổi bằng FIREBASE_PROJECT_ID /
// FIREBASE_AUTH_EMULATOR_HOST / FIRESTORE_EMULATOR_HOST. Script từ chối chạy nếu host không phải máy này.
//
// Tạo (ID cố định `demo090-*`, chạy lại được; mỗi lần chạy xóa rồi tạo lại toàn bộ dữ liệu của các nhóm demo):
// - Tài khoản (mật khẩu `Test@1234`): player1…player25 (playerN@test.local), admin1@test.local (appRole ADMIN).
//   Nếu email đã có từ seed 080 (player1, player2) thì dùng lại uid cũ thay vì tạo uid mới (Auth Emulator không cho
//   hai tài khoản trùng email); `users/{uid}` của họ chỉ được gộp thêm displayName, accountStatus.
// - G1 "CLB Cầu Lông Quận 7": công khai, chủ player1, QTV player2, thành viên player3, player4; 25 bài (1 bài ẩn bởi
//   GROUP, 1 bài ẩn bởi SYSTEM), 1 sự kiện sắp tới (dự kiến 8), 1 sự kiện đã qua.
// - G2 "CLB Cầu Lông Bình Thạnh": riêng tư, chủ player1, 2 yêu cầu chờ duyệt (player8; player9 từng bị mời ra, có removals).
// - G3 "Nhóm Cầu Lông Bị Khóa": LOCKED, chủ player10.
// - player5 làm chủ 5 nhóm công khai (demo090-g5-1…5) để thử giới hạn 5 nhóm (Q3).
// Quickstart Q6 dùng player7 xin vào G2 nên seed cố tình KHÔNG đặt player7 làm người xin/thành viên của G2.
import { setCustomClaims } from "../../supabase/functions/_shared/identity.ts";
import {
  commit,
  deleteWrite,
  documentName,
  encodeFields,
  projectId,
  query,
  type Write,
} from "../../supabase/functions/_shared/firestore.ts";
// File do agent khác viết (T005): chữ ký `nameKeywords(name: string): string[]`.
import { nameKeywords } from "../../supabase/functions/_shared/keywords.ts";

// ---------- Môi trường: chỉ Emulator ----------

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

const PREFIX = "demo090";
const PASSWORD = "Test@1234";
const HOUR = 3600_000;
const DAY = 24 * HOUR;
const now = new Date();

/** Ghi đè cả document, không điều kiện. */
const setWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
});
/** Gộp đúng các trường trong `data`, document chưa có thì tạo. */
const mergeWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
  updateMask: { fieldPaths: Object.keys(data) },
});

async function commitChunks(writes: Write[]) {
  for (let i = 0; i < writes.length; i += 400) await commit(writes.slice(i, i + 400));
}

// ---------- Tài khoản (Auth Emulator) ----------

const authBase = () =>
  `http://${Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")}/identitytoolkit.googleapis.com/v1/projects/${projectId()}`;
const authHeaders = { Authorization: "Bearer owner", "Content-Type": "application/json" };

/** uid đang có của email (từ seed khác), hoặc null. */
async function findUidByEmail(email: string): Promise<string | null> {
  const res = await fetch(`${authBase()}/accounts:lookup`, {
    method: "POST",
    headers: authHeaders,
    body: JSON.stringify({ email: [email] }),
  });
  if (!res.ok) throw new Error(`Không tra được ${email}: ${res.status} ${await res.text()}`);
  const body = await res.json();
  return body.users?.[0]?.localId ?? null;
}

/** Tạo tài khoản (hoặc dùng lại tài khoản trùng email), đặt claim, trả về uid thực dùng. */
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

interface Account {
  key: string; // player1…player25, admin1
  email: string;
  name: string;
  role: "PLAYER" | "ADMIN";
}
const ACCOUNTS: Account[] = [
  ...Array.from({ length: 25 }, (_, i) => ({
    key: `player${i + 1}`,
    email: `player${i + 1}@test.local`,
    name: `Người chơi ${i + 1}`,
    role: "PLAYER" as const,
  })),
  { key: "admin1", email: "admin1@test.local", name: "Quản trị viên 1", role: "ADMIN" },
];

function userDoc(a: Account) {
  return {
    displayName: a.name,
    email: a.email,
    phone: null,
    avatarPath: null,
    level: null,
    favoriteDistrictIds: [],
    role: a.role,
    ownerStatus: "NONE",
    accountStatus: "ACTIVE",
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
    termsAcceptedAt: now,
    createdAt: now,
    updatedAt: now,
  };
}

// ---------- Khu vực ----------

// Gộp vào `districts` có sẵn (id theo seed 080: q10, binh-thanh); thêm q7 cho nhóm "Quận 7".
const DISTRICTS = [
  { id: "q7", name: "Quận 7", sortOrder: 6 },
  { id: "q10", name: "Quận 10", sortOrder: 8 },
  { id: "binh-thanh", name: "Bình Thạnh", sortOrder: 12 },
];

// ---------- Nhóm ----------

type Visibility = "PUBLIC" | "PRIVATE";
type GroupStatus = "ACTIVE" | "LOCKED";
type Role = "OWNER" | "ADMIN" | "MEMBER";

interface GroupSpec {
  id: string;
  name: string;
  description: string;
  districtId: string;
  ownerKey: string;
  visibility: Visibility;
  status: GroupStatus;
  /** Thành viên (kể cả chủ nhóm), theo thứ tự vào nhóm. */
  members: { key: string; role: Role }[];
  /** Người xin vào (nhóm riêng tư). */
  requests?: { key: string; wasRemoved: boolean }[];
}

const GROUPS: GroupSpec[] = [
  {
    id: `${PREFIX}-g1`,
    name: "CLB Cầu Lông Quận 7",
    description: "Câu lạc bộ cầu lông Quận 7, đánh tối thứ Ba và thứ Bảy. Mọi trình độ đều được chào đón.",
    districtId: "q7",
    ownerKey: "player1",
    visibility: "PUBLIC",
    status: "ACTIVE",
    members: [
      { key: "player1", role: "OWNER" },
      { key: "player2", role: "ADMIN" },
      { key: "player3", role: "MEMBER" },
      { key: "player4", role: "MEMBER" },
    ],
  },
  {
    id: `${PREFIX}-g2`,
    name: "CLB Cầu Lông Bình Thạnh",
    description: "Nhóm riêng tư, duyệt thành viên trước khi vào.",
    districtId: "binh-thanh",
    ownerKey: "player1",
    visibility: "PRIVATE",
    status: "ACTIVE",
    members: [{ key: "player1", role: "OWNER" }],
    requests: [
      { key: "player8", wasRemoved: false },
      { key: "player9", wasRemoved: true },
    ],
  },
  {
    id: `${PREFIX}-g3`,
    name: "Nhóm Cầu Lông Bị Khóa",
    description: "Nhóm đã bị quản trị viên khóa (spec 120), dùng để thử trạng thái LOCKED.",
    districtId: "q10",
    ownerKey: "player10",
    visibility: "PUBLIC",
    status: "LOCKED",
    members: [{ key: "player10", role: "OWNER" }],
  },
  // player5 làm chủ đúng 5 nhóm ACTIVE: tạo nhóm thứ 6 phải báo OWNER_LIMIT_REACHED (Q3).
  // Tên cố ý không chứa "cầu lông" để không lẫn vào kết quả tìm kiếm của Q4.
  ...[1, 2, 3, 4, 5].map((n): GroupSpec => ({
    id: `${PREFIX}-g5-${n}`,
    name: `Pickleball Cuối Tuần ${n}`,
    description: `Nhóm pickleball số ${n} của người chơi 5.`,
    districtId: n % 2 === 0 ? "q10" : "q7",
    ownerKey: "player5",
    visibility: "PUBLIC",
    status: "ACTIVE",
    members: [{ key: "player5", role: "OWNER" }],
  })),
];

// ---------- Bài viết của G1 ----------

const G1 = GROUPS[0].id;
const postId = (n: number) => `${G1}-p${String(n).padStart(2, "0")}`;

interface PostSpec {
  n: number;
  authorKey: string;
  type: "POST" | "EVENT";
  content: string;
  /** Giờ tạo: now - ageHours. */
  ageHours: number;
  hiddenBy?: "GROUP" | "SYSTEM";
  event?: { title: string; atOffsetMs: number; location: string; expectedCount: number; attendees: string[] };
}

const AUTHORS = ["player1", "player2", "player3", "player4"];
const POSTS: PostSpec[] = [];
for (let n = 1; n <= 25; n++) {
  POSTS.push({
    n,
    authorKey: AUTHORS[n % AUTHORS.length],
    type: "POST",
    content: `Bài viết số ${n} của CLB Cầu Lông Quận 7.`,
    ageHours: n * 3,
  });
}
// Bài 5 bị chủ nhóm/QTV ẩn, bài 6 bị quản trị viên hệ thống ẩn (nhóm không "Hiện lại" được).
POSTS[4].hiddenBy = "GROUP";
POSTS[5].hiddenBy = "SYSTEM";
// Bài 2 là sự kiện sắp tới (dự kiến 8, hiện 2 người tham gia); bài 20 là sự kiện đã qua.
POSTS[1] = {
  n: 2,
  authorKey: "player3",
  type: "EVENT",
  content: "Giao lưu cuối tuần, mang vợt và nước uống.",
  ageHours: 6,
  event: {
    title: "Giao lưu cầu lông thứ Bảy",
    atOffsetMs: 3 * DAY,
    location: "Sân SmashNow Quận 7",
    expectedCount: 8,
    attendees: ["player2", "player4"],
  },
};
POSTS[19] = {
  n: 20,
  authorKey: "player4",
  type: "EVENT",
  content: "Buổi giao lưu đã kết thúc, cảm ơn mọi người.",
  ageHours: 5 * 24,
  event: {
    title: "Giao lưu cầu lông tuần trước",
    atOffsetMs: -2 * DAY,
    location: "Sân SmashNow Quận 7",
    expectedCount: 4,
    attendees: ["player3"],
  },
};

// ---------- Chạy ----------

console.log(`Project ${projectId()}, Auth ${Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")}, Firestore ${Deno.env.get("FIRESTORE_EMULATOR_HOST")}`);

// Tài khoản: key -> uid thực dùng.
const uids = new Map<string, string>();
const reused = new Set<string>();
for (const a of ACCOUNTS) {
  const wanted = `${PREFIX}-${a.key}`;
  const uid = await ensureAuthUser(wanted, a.email, a.name, a.role);
  uids.set(a.key, uid);
  if (uid !== wanted) reused.add(a.key);
}
const uid = (key: string) => uids.get(key)!;
const accountOf = (key: string) => ACCOUNTS.find((a) => a.key === key)!;
const snapshot = (key: string) => ({ displayName: accountOf(key).name, avatarPath: null });

// Xóa dữ liệu demo cũ của các nhóm (kể cả dữ liệu tạo từ app ở lần demo trước) để số đếm khớp. Commit riêng
// trước để một commit không ghi hai lần vào cùng document.
const stale: Write[] = [];
for (const g of GROUPS) {
  const base = `groups/${g.id}`;
  for (const sub of ["members", "joinRequests", "removals"]) {
    const old = await query(sub, [], { parent: base });
    stale.push(...old.map((d) => deleteWrite(d.path, false)));
  }
  const posts = await query("posts", [], { parent: base });
  for (const p of posts) {
    const att = await query("attendees", [], { parent: p.path });
    stale.push(...att.map((d) => deleteWrite(d.path, false)));
    stale.push(deleteWrite(p.path, false));
  }
}
await commitChunks(stale);

const writes: Write[] = [];
for (const d of DISTRICTS) writes.push(mergeWrite(`districts/${d.id}`, { name: d.name, sortOrder: d.sortOrder }));
for (const a of ACCOUNTS) {
  if (reused.has(a.key)) {
    writes.push(mergeWrite(`users/${uid(a.key)}`, { displayName: a.name, accountStatus: "ACTIVE" }));
  } else {
    writes.push(setWrite(`users/${uid(a.key)}`, userDoc(a)));
  }
}

let memberTotal = 0;
let requestTotal = 0;
for (const [gi, g] of GROUPS.entries()) {
  const base = `groups/${g.id}`;
  const created = new Date(now.getTime() - (30 - gi) * DAY);
  const requests = g.requests ?? [];
  writes.push(setWrite(base, {
    name: g.name,
    nameKeywords: nameKeywords(g.name),
    description: g.description,
    avatarPath: null,
    districtId: g.districtId,
    ownerId: uid(g.ownerKey),
    visibility: g.visibility,
    memberCount: g.members.length,
    pendingRequestCount: requests.length,
    status: g.status,
    createdAt: created,
    updatedAt: now,
  }));
  g.members.forEach((m, i) => {
    const joinedAt = new Date(created.getTime() + i * HOUR);
    writes.push(setWrite(`${base}/members/${uid(m.key)}`, {
      userId: uid(m.key),
      groupId: g.id,
      role: m.role,
      userSnapshot: snapshot(m.key),
      joinedAt,
      createdAt: joinedAt,
      updatedAt: joinedAt,
    }));
    memberTotal++;
  });
  requests.forEach((r, i) => {
    const at = new Date(now.getTime() - (2 - i) * DAY); // cũ nhất trước
    writes.push(setWrite(`${base}/joinRequests/${uid(r.key)}`, {
      userId: uid(r.key),
      userSnapshot: snapshot(r.key),
      wasRemoved: r.wasRemoved,
      requestId: crypto.randomUUID(),
      createdAt: at,
    }));
    if (r.wasRemoved) {
      writes.push(setWrite(`${base}/removals/${uid(r.key)}`, {
        userId: uid(r.key),
        removedBy: uid(g.ownerKey),
        removedAt: new Date(now.getTime() - 10 * DAY),
      }));
    }
    requestTotal++;
  });
}

// Bài viết G1 (+ người tham gia sự kiện).
let attendeeTotal = 0;
for (const p of POSTS) {
  const base = `groups/${G1}/posts/${postId(p.n)}`;
  const createdAt = new Date(now.getTime() - p.ageHours * HOUR);
  const ev = p.event;
  const eventAt = ev ? new Date(now.getTime() + ev.atOffsetMs) : null;
  writes.push(setWrite(base, {
    groupId: G1,
    authorId: uid(p.authorKey),
    authorSnapshot: snapshot(p.authorKey),
    authorDeleted: false,
    type: p.type,
    content: p.content,
    photoPaths: [],
    eventTitle: ev?.title ?? null,
    eventAt,
    eventLocation: ev?.location ?? null,
    expectedCount: ev?.expectedCount ?? null,
    attendeeCount: ev ? ev.attendees.length : 0,
    status: p.hiddenBy ? "HIDDEN" : "VISIBLE",
    hiddenBy: p.hiddenBy ?? null,
    hiddenByUid: p.hiddenBy === "GROUP" ? uid("player2") : null,
    editedAt: null,
    createdAt,
    updatedAt: createdAt,
  }));
  ev?.attendees.forEach((key, i) => {
    const joinedAt = new Date(createdAt.getTime() + (i + 1) * HOUR);
    writes.push(setWrite(`${base}/attendees/${uid(key)}`, {
      userId: uid(key),
      groupId: G1,
      postId: postId(p.n),
      eventAt,
      userSnapshot: snapshot(key),
      joinedAt,
    }));
    attendeeTotal++;
  });
}

await commitChunks(writes);
console.log(
  `Xong: ${ACCOUNTS.length} tài khoản (mật khẩu ${PASSWORD}; dùng lại uid cũ: ${[...reused].join(", ") || "không"}), ` +
    `${GROUPS.length} nhóm, ${memberTotal} thành viên, ${requestTotal} yêu cầu, ${POSTS.length} bài (G1), ` +
    `${attendeeTotal} người tham gia sự kiện.`,
);
