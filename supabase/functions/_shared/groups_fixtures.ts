// Dữ liệu và công cụ dùng chung cho test Deno của spec 090 (chỉ dùng trong test, cần Firebase Emulator trừ
// FakeStorage và các bộ ghi lại). Không dùng trong function.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { requireUser } from "./auth.ts";
import * as firestore from "./firestore.ts";
import { commit, createWrite, type Write } from "./firestore.ts";
import { nameKeywords } from "./keywords.ts";
import {
  attendeePath,
  type GroupBucket,
  type GroupDeps,
  type GroupNotification,
  groupPath,
  type GroupStorage,
  joinRequestPath,
  memberPath,
  type MembershipChange,
  postPath,
  removalPath,
  type SignedUpload,
} from "./groups.ts";

/** Thời điểm cố định dùng làm `joinedAt`/`createdAt` của dữ liệu mẫu. */
export const SEED_TIME = new Date("2026-11-01T00:00:00Z");

// ---------- Người dùng ----------

/** Tạo `users/{uid}` (ACTIVE) nếu chưa có; đã có thì giữ nguyên. */
export async function ensureUsers(uids: string[]): Promise<void> {
  const writes: Write[] = [];
  for (const uid of new Set(uids)) {
    if (await firestore.get(`users/${uid}`)) continue;
    writes.push(createWrite(`users/${uid}`, {
      displayName: `Người dùng ${uid}`,
      avatarPath: null,
      appRole: "PLAYER",
      accountStatus: "ACTIVE",
      createdAt: SEED_TIME,
    }));
  }
  await commitChunks(writes);
}

async function commitChunks(writes: Write[], size = 400): Promise<void> {
  for (let i = 0; i < writes.length; i += size) await commit(writes.slice(i, i + size));
}

const snapshotOf = async (uid: string) => {
  const u = await firestore.get(`users/${uid}`);
  return {
    displayName: typeof u?.displayName === "string" ? u.displayName : null,
    avatarPath: typeof u?.avatarPath === "string" ? u.avatarPath : null,
  };
};

// ---------- Nhóm ----------

export interface SeedGroupOptions {
  id?: string;
  visibility?: "PUBLIC" | "PRIVATE";
  /** Chủ nhóm (bắt buộc). */
  owner: string;
  admins?: string[];
  members?: string[];
  /** Người có yêu cầu tham gia đang chờ. */
  pending?: string[];
  /** Người từng bị mời ra: ghi `removals/{uid}`. */
  removed?: string[];
  status?: "ACTIVE" | "LOCKED" | "DELETED";
  districtId?: string;
  name?: string;
}

/**
 * Ghi `groups/{id}` cùng `members`, `joinRequests`, `removals` đủ trường data-model; `memberCount` khớp số
 * `members`, `pendingRequestCount` khớp số `joinRequests`. Tạo `users/{uid}` ACTIVE nếu chưa có. Trả `id`.
 * `joinedAt` tăng dần theo thứ tự owner, admins, members (cách nhau 1 phút).
 */
export async function seedGroup(opts: SeedGroupOptions): Promise<string> {
  const id = opts.id ?? crypto.randomUUID();
  const name = opts.name ?? "CLB Cầu Lông Test";
  const admins = opts.admins ?? [];
  const members = opts.members ?? [];
  const pending = opts.pending ?? [];
  const removed = opts.removed ?? [];
  await ensureUsers([opts.owner, ...admins, ...members, ...pending, ...removed]);

  const roster: [string, "OWNER" | "ADMIN" | "MEMBER"][] = [
    [opts.owner, "OWNER"],
    ...admins.map((u): [string, "ADMIN"] => [u, "ADMIN"]),
    ...members.map((u): [string, "MEMBER"] => [u, "MEMBER"]),
  ];
  const writes: Write[] = [
    createWrite(groupPath(id), {
      name,
      nameKeywords: nameKeywords(name),
      description: "Nhóm mẫu cho test",
      avatarPath: null,
      districtId: opts.districtId ?? "d1",
      ownerId: opts.owner,
      visibility: opts.visibility ?? "PUBLIC",
      memberCount: roster.length,
      pendingRequestCount: pending.length,
      status: opts.status ?? "ACTIVE",
      createdAt: SEED_TIME,
      updatedAt: SEED_TIME,
    }),
  ];
  for (const [i, [uid, role]] of roster.entries()) {
    writes.push(createWrite(memberPath(id, uid), {
      userId: uid,
      groupId: id,
      role,
      userSnapshot: await snapshotOf(uid),
      joinedAt: new Date(SEED_TIME.getTime() + i * 60_000),
      createdAt: SEED_TIME,
      updatedAt: SEED_TIME,
    }));
  }
  for (const uid of pending) {
    writes.push(createWrite(joinRequestPath(id, uid), {
      userId: uid,
      userSnapshot: await snapshotOf(uid),
      wasRemoved: removed.includes(uid),
      requestId: crypto.randomUUID(),
      createdAt: SEED_TIME,
    }));
  }
  for (const uid of removed) {
    writes.push(createWrite(removalPath(id, uid), { userId: uid, removedBy: opts.owner, removedAt: SEED_TIME }));
  }
  await commitChunks(writes);
  return id;
}

// ---------- Bài đăng ----------

export interface SeedPostOptions {
  id?: string;
  type?: "POST" | "EVENT";
  authorId: string;
  /** Chỉ EVENT; mặc định giờ hiện tại + 1 ngày. */
  eventAt?: Date;
  expectedCount?: number;
  /** Người đã tham gia (chỉ EVENT): ghi `attendees/{uid}`, `attendeeCount` = số phần tử. */
  attendees?: string[];
  status?: "VISIBLE" | "HIDDEN";
  hiddenBy?: "GROUP" | "SYSTEM" | null;
  photoPaths?: string[];
}

/** Ghi `groups/{groupId}/posts/{id}` và `attendees`; trả `id`. `hiddenBy` có giá trị thì `status` mặc định `HIDDEN`. */
export async function seedPost(groupId: string, opts: SeedPostOptions): Promise<string> {
  const id = opts.id ?? crypto.randomUUID();
  const type = opts.type ?? "POST";
  const attendees = type === "EVENT" ? opts.attendees ?? [] : [];
  const eventAt = opts.eventAt ?? new Date(Date.now() + 24 * 3600_000);
  const hiddenBy = opts.hiddenBy ?? null;
  await ensureUsers([opts.authorId, ...attendees]);

  const writes: Write[] = [
    createWrite(postPath(groupId, id), {
      groupId,
      authorId: opts.authorId,
      authorSnapshot: await snapshotOf(opts.authorId),
      authorDeleted: false,
      type,
      content: type === "EVENT" ? "Mô tả sự kiện" : "Nội dung bài viết",
      photoPaths: opts.photoPaths ?? [],
      eventTitle: type === "EVENT" ? "Giao lưu cuối tuần" : null,
      eventAt: type === "EVENT" ? eventAt : null,
      eventLocation: type === "EVENT" ? "Sân A, Quận 7" : null,
      expectedCount: type === "EVENT" ? opts.expectedCount ?? 8 : null,
      attendeeCount: attendees.length,
      status: opts.status ?? (hiddenBy ? "HIDDEN" : "VISIBLE"),
      hiddenBy,
      hiddenByUid: null,
      editedAt: null,
      createdAt: SEED_TIME,
      updatedAt: SEED_TIME,
    }),
  ];
  for (const [i, uid] of attendees.entries()) {
    writes.push(createWrite(attendeePath(groupId, id, uid), {
      userId: uid,
      groupId,
      postId: id,
      eventAt,
      userSnapshot: await snapshotOf(uid),
      joinedAt: new Date(SEED_TIME.getTime() + i * 1000),
    }));
  }
  await commitChunks(writes);
  return id;
}

// ---------- Kiểm tra bất biến (T007) ----------

/**
 * Bất biến của một nhóm: `memberCount` = số `members`; đúng một `members` có `role = OWNER` và trùng
 * `groups.ownerId`; `pendingRequestCount` = số `joinRequests`; mỗi bài `EVENT` có `attendeeCount` = số `attendees`.
 */
export async function assertGroupInvariant(groupId: string): Promise<void> {
  const group = await firestore.get(groupPath(groupId));
  if (!group) throw new Error(`groups/${groupId} không tồn tại`);
  const parent = groupPath(groupId);

  const members = await firestore.query("members", [], { parent });
  assertEquals(group.memberCount, members.length, "memberCount != số document members");
  const owners = members.filter((m) => m.data.role === "OWNER");
  assertEquals(owners.length, 1, "phải có đúng một OWNER");
  assertEquals(owners[0].id, group.ownerId, "OWNER không trùng groups.ownerId");

  const requests = await firestore.query("joinRequests", [], { parent });
  assertEquals(group.pendingRequestCount ?? 0, requests.length, "pendingRequestCount != số joinRequests");

  const posts = await firestore.query("posts", [], { parent });
  for (const post of posts) {
    if (post.data.type !== "EVENT") continue;
    const attendees = await firestore.query("attendees", [], { parent: post.path });
    assertEquals(post.data.attendeeCount, attendees.length, `attendeeCount của bài ${post.id} != số attendees`);
  }
}

// ---------- Bộ ghi lại cho mock ----------

/** Thông báo đã gửi (thay `deps.notify`). */
export class NotificationRecorder {
  readonly sent: GroupNotification[] = [];
  private readonly captured = new Set<string>();
  callbackCalls = 0;
  readonly notify = (n: GroupNotification): Promise<void> => {
    this.callbackCalls++;
    this.sent.push(n);
    return Promise.resolve();
  };
  async capture(groupId: string): Promise<void> {
    const fanouts = await firestore.query("notificationFanouts", [{ field: "groupId", op: "EQUAL", value: groupId }]);
    for (const fanout of fanouts) {
      if (this.captured.has(fanout.id)) continue;
      const content = fanout.data.content as Pick<GroupNotification, "type" | "data">;
      if (content.type !== "GROUP_ACTIVITY") continue;
      assertEquals(fanout.data.mode, "SNAPSHOT");
      const pages = await firestore.query("recipients", [], { parent: fanout.path });
      for (const page of pages) {
        for (const recipientUid of page.data.uids as string[]) this.sent.push({ ...content, recipientUid });
      }
      this.captured.add(fanout.id);
    }
    assertEquals(this.callbackCalls, 0);
  }
  /** Người nhận của các thông báo có `data.event` đã cho. */
  recipients(event: string): string[] {
    return this.sent.filter((n) => n.data.event === event).map((n) => n.recipientUid);
  }
  reset(): void {
    this.sent.length = 0;
  }
}

/** Các lần `onMembershipChanged` được gọi (thay `deps.onMembershipChanged`). */
export class MembershipHookRecorder {
  readonly calls: { groupId: string; change: MembershipChange; inTransaction: boolean }[] = [];
  readonly hook: GroupDeps["onMembershipChanged"] = (tx, groupId, change) => {
    this.calls.push({ groupId, change, inTransaction: tx !== undefined && tx !== null });
    return Promise.resolve();
  };
  /** Mọi uid đã được thêm vào nhóm `groupId` qua hook. */
  added(groupId: string): string[] {
    return this.calls.filter((c) => c.groupId === groupId).flatMap((c) => c.change.added ?? []);
  }
  reset(): void {
    this.calls.length = 0;
  }
}

/** Supabase Storage giả: tập object theo bucket và danh sách path đã bị xóa (dạng `bucket/path`). */
export class FakeStorage implements GroupStorage {
  readonly objects = new Set<string>();
  readonly removed: string[] = [];
  readonly signedUploads: string[] = [];

  private key = (bucket: string, path: string) => `${bucket}/${path}`;

  /** Thêm object có sẵn. */
  put(bucket: GroupBucket | string, ...paths: string[]): void {
    for (const p of paths) this.objects.add(this.key(bucket, p));
  }

  has(bucket: string, path: string): boolean {
    return this.objects.has(this.key(bucket, path));
  }

  exists(bucket: string, path: string): Promise<boolean> {
    return Promise.resolve(this.has(bucket, path));
  }

  list(bucket: string, prefix: string): Promise<string[]> {
    const start = this.key(bucket, prefix.replace(/\/+$/, "") + "/");
    return Promise.resolve(
      [...this.objects].filter((k) => k.startsWith(start) && !k.substring(start.length).includes("/"))
        .map((k) => k.substring(bucket.length + 1)),
    );
  }

  remove(bucket: string, paths: string[]): Promise<number> {
    let n = 0;
    for (const p of paths) {
      if (this.objects.delete(this.key(bucket, p))) {
        this.removed.push(this.key(bucket, p));
        n++;
      }
    }
    return Promise.resolve(n);
  }

  createSignedUploadUrl(bucket: string, path: string): Promise<SignedUpload> {
    this.signedUploads.push(this.key(bucket, path));
    return Promise.resolve({
      path,
      signedUrl: `http://storage.test/${bucket}/${path}?token=upload`,
      token: "upload",
    });
  }

  createSignedUrls(bucket: string, paths: string[], _expiresInSeconds: number): Promise<Record<string, string>> {
    return Promise.resolve(Object.fromEntries(
      paths.filter((p) => this.has(bucket, p)).map((p) => [p, `http://storage.test/${bucket}/${p}?token=dl`]),
    ));
  }

  reset(): void {
    this.objects.clear();
    this.removed.length = 0;
    this.signedUploads.length = 0;
  }
}

/** Bộ `GroupDeps` cho test: Firestore thật (Emulator), storage/notify/hook giả, `authenticate` tùy chọn. */
export function makeTestDeps(overrides: Partial<GroupDeps> = {}) {
  const storage = new FakeStorage();
  const notifications = new NotificationRecorder();
  const hooks = new MembershipHookRecorder();
  const deps: GroupDeps = {
    firestore,
    storage,
    // Mặc định dùng requireUser thật (token không ký của Auth Emulator).
    authenticate: (req) => requireUser(req),
    notify: notifications.notify,
    onMembershipChanged: hooks.hook,
    now: () => new Date(),
    sleep: () => Promise.resolve(),
    ...overrides,
  };
  return { deps, storage, notifications, hooks };
}
