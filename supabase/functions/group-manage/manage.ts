// `setRole`, `transferOwner`, `remove`, `delete` của `group-manage` (spec 090 T059, T060; research R11, R13).
// Quản lý thành viên đọc/ghi `groups/{groupId}` trong transaction (R3); `delete` đặt `DELETED` trong transaction
// rồi dọn dữ liệu NGOÀI transaction theo lô, chạy lại được khi lỗi giữa chừng.
import { HttpError } from "../_shared/auth.ts";
import { createWrite, deleteWrite, updateWrite } from "../_shared/firestore.ts";
import {
  type ActionHandler,
  getMembership,
  type GroupDeps,
  groupPath,
  type GroupRole,
  loadGroupForWrite,
  memberPath,
  notifyGroupActivity,
  postPath,
  removalPath,
} from "../_shared/groups.ts";
import { asNumber, inGroupTx, invalid, requireId } from "../_shared/group-tx.ts";
import { deletePrefix, GROUP_MEDIA_BUCKET, postMediaPrefix } from "../_shared/media.ts";
import { AVATAR_BUCKET } from "./common.ts";
import { dropUpcomingAttendance } from "./membership.ts";

const ok = () => Response.json({ ok: true });
const forbidden = (message: string) => new HttpError(403, "FORBIDDEN", message);
const memberNotFound = () => new HttpError(404, "MEMBER_NOT_FOUND", "Người này không phải thành viên của nhóm");

/** Vai trò của người gọi; không phải thành viên → `403 NOT_MEMBER`. */
async function callerRole(
  tx: Parameters<typeof getMembership>[0],
  groupId: string,
  uid: string,
): Promise<GroupRole> {
  const role = await getMembership(tx, groupId, uid);
  if (!role) throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm");
  return role;
}

/** Vai trò của đích; không phải thành viên → `404 MEMBER_NOT_FOUND`. */
async function targetRole(
  tx: Parameters<typeof getMembership>[0],
  groupId: string,
  userId: string,
): Promise<GroupRole> {
  const role = await getMembership(tx, groupId, userId);
  if (!role) throw memberNotFound();
  return role;
}

// ---------- setRole ----------

/** Chỉ OWNER; đích là thành viên khác mình (chủ nhóm tự đổi vai trò thì `400`); `role` là `ADMIN` hoặc `MEMBER`. */
export const setRole: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const userId = requireId(body, "userId");
  const role = body.role;
  if (role !== "ADMIN" && role !== "MEMBER") throw invalid("role phải là ADMIN hoặc MEMBER");

  await inGroupTx(deps, groupId, async (tx, writes) => {
    await loadGroupForWrite(tx, groupId);
    if (await callerRole(tx, groupId, uid) !== "OWNER") throw forbidden("Chỉ chủ nhóm được đổi vai trò");
    if (userId === uid) throw invalid("Không thể đổi vai trò của chính mình");
    await targetRole(tx, groupId, userId);
    writes.push(updateWrite(memberPath(groupId, userId), { role, updatedAt: deps.now() }));
  });
  return ok();
};

// ---------- transferOwner ----------

/** Chỉ OWNER; người nhận thành OWNER, chủ cũ thành ADMIN, `groups.ownerId` đổi. Danh sách thành viên không đổi nên không gọi hook. */
export const transferOwner: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const userId = requireId(body, "userId");

  await inGroupTx(deps, groupId, async (tx, writes) => {
    const now = deps.now();
    await loadGroupForWrite(tx, groupId);
    if (await callerRole(tx, groupId, uid) !== "OWNER") throw forbidden("Chỉ chủ nhóm được chuyển quyền");
    if (userId === uid) throw invalid("Không thể chuyển quyền cho chính mình");
    await targetRole(tx, groupId, userId);
    writes.push(
      updateWrite(memberPath(groupId, userId), { role: "OWNER", updatedAt: now }),
      updateWrite(memberPath(groupId, uid), { role: "ADMIN", updatedAt: now }),
      updateWrite(groupPath(groupId), { ownerId: userId, updatedAt: now }),
    );
  });
  return ok();
};

// ---------- remove ----------

/**
 * OWNER mời ai cũng được trừ chính mình; ADMIN chỉ mời MEMBER (đích OWNER/ADMIN → `FORBIDDEN`); MEMBER → `FORBIDDEN`.
 * Ghi `removals/{uid}`, bỏ người đó khỏi sự kiện chưa diễn ra, hook `removed` cùng transaction; `REMOVED` sau commit.
 */
export const removeMember: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const userId = requireId(body, "userId");

  await inGroupTx(deps, groupId, async (tx, writes) => {
    const now = deps.now();
    const group = await loadGroupForWrite(tx, groupId);
    const caller = await callerRole(tx, groupId, uid);
    if (caller === "MEMBER") throw forbidden("Chỉ chủ nhóm hoặc quản trị viên được mời thành viên ra khỏi nhóm");
    if (userId === uid) throw invalid("Không thể tự mời mình ra khỏi nhóm (hãy dùng rời nhóm)");
    const target = await targetRole(tx, groupId, userId);
    if (caller === "ADMIN" && target !== "MEMBER") {
      throw forbidden("Quản trị viên chỉ được mời thành viên thường ra khỏi nhóm");
    }

    // Mọi tx.get/tx.query nằm trước các lần ghi (writes chỉ gom, commit một lần ở cuối).
    await dropUpcomingAttendance(tx, writes, groupId, userId, now);
    writes.push(
      deleteWrite(memberPath(groupId, userId)),
      createWrite(removalPath(groupId, userId), { userId, removedBy: uid, removedAt: now }),
      updateWrite(groupPath(groupId), { memberCount: Math.max(0, asNumber(group.memberCount) - 1), updatedAt: now }),
    );
    await deps.onMembershipChanged(tx, groupId, { removed: [userId] });
  });

  await notifyGroupActivity(deps, "REMOVED", [userId], { groupId });
  return ok();
};

// ---------- delete ----------

const BATCH = 500;

/** Xóa hết document của collection con `collectionId` dưới `parent`, mỗi lô ≤ 500 (không dùng transaction). */
async function drainCollection(deps: GroupDeps, collectionId: string, parent: string): Promise<void> {
  for (;;) {
    const rows = await deps.firestore.query(collectionId, [], { parent, limit: BATCH });
    if (rows.length === 0) return;
    await deps.firestore.commit(rows.map((r) => deleteWrite(r.path, false)));
    if (rows.length < BATCH) return;
  }
}

/**
 * Phần dọn sau khi nhóm đã `DELETED`; thứ tự: từng bài (ảnh, `attendees`, rồi chính bài), `members`, `joinRequests`,
 * `removals`, ảnh đại diện, document nhóm. Mỗi bước idempotent nên lỗi giữa chừng thì gọi lại `delete` chạy tiếp.
 */
async function purgeGroup(deps: GroupDeps, groupId: string): Promise<void> {
  const parent = groupPath(groupId);
  for (;;) {
    const posts = await deps.firestore.query("posts", [], { parent, limit: BATCH });
    if (posts.length === 0) break;
    for (const post of posts) {
      // Ảnh bài nằm ở `{groupId}/{postId}/...`; `deletePrefix` chỉ xóa file ngay dưới thư mục nên xóa theo từng bài.
      await deletePrefix(deps.storage, GROUP_MEDIA_BUCKET, postMediaPrefix(groupId, post.id));
      await drainCollection(deps, "attendees", postPath(groupId, post.id));
    }
    await deps.firestore.commit(posts.map((p) => deleteWrite(p.path, false)));
  }
  for (const c of ["members", "joinRequests", "removals"]) await drainCollection(deps, c, parent);
  // File trực tiếp dưới `{groupId}/` (ảnh đại diện, và file lạc nếu có) ở cả 2 bucket.
  await deletePrefix(deps.storage, AVATAR_BUCKET, groupId);
  await deletePrefix(deps.storage, GROUP_MEDIA_BUCKET, groupId);
  // TODO(spec 091): gọi `purgeChat("group_" + groupId)` của `_shared/chats.ts` tại đây (file chưa tồn tại).
  await deps.firestore.commit([deleteWrite(parent, false)]);
}

/**
 * Chỉ OWNER; `confirmName` (sau trim) phải bằng tên nhóm. Transaction đặt `DELETED` + hook `{ deleted: true }`,
 * sau commit dọn dữ liệu. Nhóm đang `DELETED` (lần trước lỗi giữa chừng): chủ nhóm gọi lại thì chạy tiếp phần dọn;
 * người khác hoặc nhóm không còn → `404 GROUP_NOT_FOUND`.
 */
export const deleteGroup: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const confirmName = typeof body.confirmName === "string" ? body.confirmName.trim() : "";

  const raw = await deps.firestore.get(groupPath(groupId));
  if (raw?.status === "DELETED") {
    // `members` có thể đã bị dọn nên nhận diện chủ nhóm bằng `groups.ownerId`.
    if (raw.ownerId !== uid) throw new HttpError(404, "GROUP_NOT_FOUND", "Nhóm không còn tồn tại");
    await purgeGroup(deps, groupId);
    return Response.json({ deleted: true });
  }

  await inGroupTx(deps, groupId, async (tx, writes) => {
    const group = await loadGroupForWrite(tx, groupId);
    if (await callerRole(tx, groupId, uid) !== "OWNER") throw forbidden("Chỉ chủ nhóm được xóa nhóm");
    if (confirmName !== String(group.name ?? "").trim()) {
      throw new HttpError(400, "CONFIRM_NAME_MISMATCH", "Tên xác nhận không khớp với tên nhóm");
    }
    writes.push(updateWrite(groupPath(groupId), { status: "DELETED", updatedAt: deps.now() }));
    await deps.onMembershipChanged(tx, groupId, { deleted: true });
  });

  await purgeGroup(deps, groupId);
  return Response.json({ deleted: true });
};
