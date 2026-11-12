// `anonymizeCommunityGroups(uid)` cho `delete-account` (A) gọi (spec 090 T069, research R16): ẩn danh bài, xóa yêu cầu
// tham gia, rời mọi nhóm (chủ nhóm thì chuyển quyền hoặc xóa nhóm). Idempotent: gọi lại khi lỗi giữa chừng chạy tiếp.
// Tái dùng logic của `group-manage` (`applyLeave`, `markGroupDeleted`, `purgeGroup`) để hành vi giống hệt `leave`/`delete`.
import { deleteWrite, updateWrite, type Write } from "./firestore.ts";
import { asDate, asNumber, inGroupTx } from "./group-tx.ts";
import {
  defaultGroupDeps,
  getMembership,
  type GroupDeps,
  groupPath,
  type GroupRole,
  joinRequestPath,
  loadGroupForWrite,
  memberPath,
} from "./groups.ts";
import { applyLeave } from "../group-manage/membership.ts";
import { markGroupDeleted, purgeGroup } from "../group-manage/manage.ts";

export interface AnonymizeResult {
  /** Nhóm mà `uid` là chủ và đã chuyển quyền cho người khác. */
  transferredGroups: string[];
  /** Nhóm mà `uid` là thành viên duy nhất (chủ nhóm) nên bị xóa. */
  deletedGroups: string[];
}

const BATCH = 400;

async function commitChunks(deps: GroupDeps, writes: Write[]): Promise<void> {
  for (let i = 0; i < writes.length; i += BATCH) await deps.firestore.commit(writes.slice(i, i + BATCH));
}

/** `groups/{groupId}/...` → `groupId`. */
const groupIdOf = (path: string) => path.split("/")[1];

/** (1) Bài của `uid` ở mọi nhóm: `authorSnapshot = null`, `authorDeleted = true` (bài đã ẩn danh thì bỏ qua). */
async function anonymizePosts(deps: GroupDeps, uid: string): Promise<void> {
  const posts = await deps.firestore.query("posts", [{ field: "authorId", op: "EQUAL", value: uid }], {
    allDescendants: true,
  });
  await commitChunks(
    deps,
    posts.filter((p) => p.data.authorDeleted !== true).map((p) =>
      updateWrite(p.path, { authorSnapshot: null, authorDeleted: true })
    ),
  );
}

/** (2) Xóa `joinRequests/{uid}` đang chờ ở mọi nhóm và giảm `pendingRequestCount` (nhóm không còn thì chỉ xóa yêu cầu). */
async function dropJoinRequests(deps: GroupDeps, uid: string): Promise<void> {
  const requests = await deps.firestore.query("joinRequests", [{ field: "userId", op: "EQUAL", value: uid }], {
    allDescendants: true,
  });
  for (const groupId of new Set(requests.map((r) => groupIdOf(r.path)))) {
    await inGroupTx(deps, groupId, async (tx, writes) => {
      const group = await tx.get(groupPath(groupId));
      if (!await tx.get(joinRequestPath(groupId, uid))) return;
      writes.push(deleteWrite(joinRequestPath(groupId, uid)));
      if (group && group.status !== "DELETED") {
        writes.push(updateWrite(groupPath(groupId), {
          pendingRequestCount: Math.max(0, asNumber(group.pendingRequestCount) - 1),
          updatedAt: deps.now(),
        }));
      }
    });
  }
}

type Outcome = "skipped" | "left" | "transferred" | "deleted" | "purge";

/** (3) Rời một nhóm; chủ nhóm thì chuyển quyền (ADMIN rồi MEMBER có `joinedAt` sớm nhất) hoặc đánh dấu xóa nhóm. */
async function leaveOrTransfer(deps: GroupDeps, groupId: string, uid: string): Promise<Outcome> {
  return await inGroupTx<Outcome>(deps, groupId, async (tx, writes) => {
    const raw = await tx.get(groupPath(groupId));
    if (!raw) return "skipped";
    if (raw.status === "DELETED") {
      // Lần trước đã đặt DELETED nhưng chưa dọn xong: chủ cũ chạy tiếp phần dọn (giống `delete`).
      return raw.ownerId === uid ? "purge" : "skipped";
    }
    // Nhóm LOCKED vẫn cho rời (giống `leave`).
    const group = await loadGroupForWrite(tx, groupId, { allowLocked: true });
    const role: GroupRole | null = await getMembership(tx, groupId, uid);
    if (!role) return "skipped";
    if (role !== "OWNER") {
      await applyLeave(tx, writes, deps, groupId, group, uid);
      return "left";
    }

    // Chọn người nhận quyền trong lúc đọc, sắp xếp ở server để không cần composite index (role + joinedAt).
    const others = (await tx.query("members", [], { parent: groupPath(groupId) })).filter((m) => m.id !== uid);
    const at = (m: { data: Record<string, unknown> }) => asDate(m.data.joinedAt)?.getTime() ?? Infinity;
    const earliest = (r: GroupRole) =>
      others.filter((m) => m.data.role === r).sort((a, b) => at(a) - at(b) || a.id.localeCompare(b.id))[0];
    const heir = earliest("ADMIN") ?? earliest("MEMBER");
    if (!heir) {
      await markGroupDeleted(tx, writes, deps, groupId);
      return "deleted";
    }
    writes.push(updateWrite(memberPath(groupId, heir.id), { role: "OWNER", updatedAt: deps.now() }));
    await applyLeave(tx, writes, deps, groupId, group, uid, { ownerId: heir.id });
    return "transferred";
  });
}

/**
 * Ẩn danh dữ liệu cộng đồng của `uid` khi xóa tài khoản. `deps` mặc định `defaultGroupDeps()`.
 * Trả danh sách nhóm đã chuyển quyền và nhóm đã xóa; gọi lại sau khi xong trả hai mảng rỗng.
 */
export async function anonymizeCommunityGroups(
  uid: string,
  deps: GroupDeps = defaultGroupDeps(),
): Promise<AnonymizeResult> {
  await anonymizePosts(deps, uid);
  await dropJoinRequests(deps, uid);

  const memberships = await deps.firestore.query("members", [{ field: "userId", op: "EQUAL", value: uid }], {
    allDescendants: true,
  });
  const result: AnonymizeResult = { transferredGroups: [], deletedGroups: [] };
  for (const groupId of new Set(memberships.map((m) => groupIdOf(m.path)))) {
    const outcome = await leaveOrTransfer(deps, groupId, uid);
    if (outcome === "transferred") result.transferredGroups.push(groupId);
    if (outcome === "deleted" || outcome === "purge") {
      await purgeGroup(deps, groupId);
      result.deletedGroups.push(groupId);
    }
  }
  return result;
}
