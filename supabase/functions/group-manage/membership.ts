// `join`, `cancelRequest`, `approve`, `reject`, `leave` của `group-manage` (spec 090 T033; data-model mục 8,
// research R3, R11, R15). Mọi thao tác đọc và ghi `groups/{groupId}` trong transaction để các request cùng nhóm
// tranh chấp đúng một document (R3); thông báo chỉ gửi SAU khi commit.
import { HttpError } from "../_shared/auth.ts";
import { createWrite, deleteWrite, updateWrite } from "../_shared/firestore.ts";
import {
  type ActionHandler,
  getMembership,
  type GroupDeps,
  groupPath,
  joinRequestPath,
  loadGroupForWrite,
  memberPath,
  notifyGroupActivity,
  postPath,
  removalPath,
  userSnapshot,
} from "../_shared/groups.ts";
import { UUID } from "../_shared/dropin-tx.ts";
import { asNumber, inGroupTx, requireId } from "../_shared/group-tx.ts";
import type { FirestoreTransaction, Write } from "../_shared/firestore.ts";

const ok = () => Response.json({ ok: true });

/** Chủ nhóm và quản trị viên của nhóm (người nhận `JOIN_REQUESTED`). Lỗi truy vấn chỉ ghi log. */
async function managerUids(deps: GroupDeps, groupId: string): Promise<string[]> {
  try {
    const rows = await deps.firestore.query("members", [{ field: "role", op: "IN", value: ["OWNER", "ADMIN"] }], {
      parent: groupPath(groupId),
    });
    return rows.map((r) => r.id);
  } catch (e) {
    console.warn(`[group-manage] không lấy được người quản lý nhóm ${groupId} để thông báo`, e);
    return [];
  }
}

// ---------- join ----------

export const joinGroup: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  // `requestId` lưu vào yêu cầu để app nhận lại đúng yêu cầu của mình; thiếu thì tự sinh.
  const requestId = typeof body.requestId === "string" && UUID.test(body.requestId)
    ? body.requestId.toLowerCase()
    : crypto.randomUUID();

  const result = await inGroupTx(deps, groupId, async (tx, writes) => {
    const now = deps.now();
    const group = await loadGroupForWrite(tx, groupId);
    const member = await tx.get(memberPath(groupId, uid));
    const request = await tx.get(joinRequestPath(groupId, uid));
    const removal = await tx.get(removalPath(groupId, uid));
    const snapshot = await userSnapshot(tx, uid);
    if (member) throw new HttpError(409, "ALREADY_MEMBER", "Bạn đã là thành viên của nhóm");
    if (request) throw new HttpError(409, "ALREADY_REQUESTED", "Bạn đã gửi yêu cầu tham gia, đang chờ duyệt");

    if (group.visibility === "PUBLIC" && !removal) {
      writes.push(
        createWrite(memberPath(groupId, uid), {
          userId: uid,
          groupId,
          role: "MEMBER",
          userSnapshot: snapshot,
          joinedAt: now,
          createdAt: now,
          updatedAt: now,
        }),
        updateWrite(groupPath(groupId), { memberCount: asNumber(group.memberCount) + 1, updatedAt: now }),
      );
      await deps.onMembershipChanged(tx, groupId, { added: [uid] });
      return "JOINED" as const;
    }
    writes.push(
      createWrite(joinRequestPath(groupId, uid), {
        userId: uid,
        userSnapshot: snapshot,
        wasRemoved: removal != null,
        requestId,
        createdAt: now,
      }),
      updateWrite(groupPath(groupId), {
        pendingRequestCount: asNumber(group.pendingRequestCount) + 1,
        updatedAt: now,
      }),
    );
    return "REQUESTED" as const;
  });

  if (result === "REQUESTED") {
    await notifyGroupActivity(deps, "JOIN_REQUESTED", await managerUids(deps, groupId), { groupId });
  }
  return Response.json({ result });
};

// ---------- cancelRequest ----------

export const cancelRequest: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  await inGroupTx(deps, groupId, async (tx, writes) => {
    const group = await loadGroupForWrite(tx, groupId);
    if (!await tx.get(joinRequestPath(groupId, uid))) {
      throw new HttpError(404, "REQUEST_NOT_FOUND", "Không có yêu cầu tham gia đang chờ");
    }
    writes.push(
      deleteWrite(joinRequestPath(groupId, uid)),
      updateWrite(groupPath(groupId), {
        pendingRequestCount: Math.max(0, asNumber(group.pendingRequestCount) - 1),
        updatedAt: deps.now(),
      }),
    );
  });
  return ok();
};

// ---------- approve, reject ----------

/** Người gọi phải là OWNER/ADMIN; `userId` là người xin. */
function reviewHandler(approve: boolean): ActionHandler {
  return async ({ uid, body, deps }) => {
    const groupId = requireId(body, "groupId");
    const userId = requireId(body, "userId");

    await inGroupTx(deps, groupId, async (tx, writes) => {
      const now = deps.now();
      const group = await loadGroupForWrite(tx, groupId);
      const role = await getMembership(tx, groupId, uid);
      if (!role) throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm");
      if (role === "MEMBER") {
        throw new HttpError(403, "FORBIDDEN", "Chỉ chủ nhóm hoặc quản trị viên được duyệt yêu cầu");
      }
      const request = await tx.get(joinRequestPath(groupId, userId));
      if (!request) throw new HttpError(404, "REQUEST_NOT_FOUND", "Yêu cầu tham gia không còn tồn tại");

      const groupPatch: Record<string, unknown> = {
        pendingRequestCount: Math.max(0, asNumber(group.pendingRequestCount) - 1),
        updatedAt: now,
      };
      writes.push(deleteWrite(joinRequestPath(groupId, userId)));
      if (approve) {
        // Được duyệt vào lại thì xóa dấu "từng bị mời ra".
        if (await tx.get(removalPath(groupId, userId))) writes.push(deleteWrite(removalPath(groupId, userId)));
        writes.push(
          createWrite(memberPath(groupId, userId), {
            userId,
            groupId,
            role: "MEMBER",
            userSnapshot: request.userSnapshot ?? { displayName: null, avatarPath: null },
            joinedAt: now,
            createdAt: now,
            updatedAt: now,
          }),
        );
        groupPatch.memberCount = asNumber(group.memberCount) + 1;
      }
      writes.push(updateWrite(groupPath(groupId), groupPatch));
      if (approve) await deps.onMembershipChanged(tx, groupId, { added: [userId] });
    });

    await notifyGroupActivity(deps, approve ? "JOIN_APPROVED" : "JOIN_REJECTED", [userId], { groupId });
    return ok();
  };
}

export const approveRequest = reviewHandler(true);
export const rejectRequest = reviewHandler(false);

// ---------- leave ----------

/**
 * Bỏ `uid` khỏi `attendees` của các sự kiện CHƯA diễn ra trong nhóm (dùng chung cho `leave` và `remove`, T059):
 * collection group `attendees where userId == uid, groupId == g, eventAt > now`; với mỗi kết quả xóa `attendees`
 * và giảm `attendeeCount` của bài (không âm). Phải gọi TRƯỚC mọi lần ghi khác không cần thiết: ở đây `writes` chỉ
 * được gom (commit một lần ở cuối `inGroupTx`) nên mọi `tx.get`/`tx.query` đều đứng trước lần ghi, đúng giới hạn
 * của Firestore (đọc trước ghi).
 */
export async function dropUpcomingAttendance(
  tx: FirestoreTransaction,
  writes: Write[],
  groupId: string,
  uid: string,
  now: Date,
): Promise<void> {
  const rows = await tx.query("attendees", [
    { field: "userId", op: "EQUAL", value: uid },
    { field: "groupId", op: "EQUAL", value: groupId },
    { field: "eventAt", op: "GREATER_THAN", value: now },
  ], { allDescendants: true });
  for (const row of rows) {
    const postId = typeof row.data.postId === "string" ? row.data.postId : row.path.split("/")[3];
    const post = await tx.get(postPath(groupId, postId));
    writes.push(deleteWrite(row.path));
    if (post) {
      writes.push(
        updateWrite(postPath(groupId, postId), { attendeeCount: Math.max(0, asNumber(post.attendeeCount) - 1) }),
      );
    }
  }
}

export const leaveGroup: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  await inGroupTx(deps, groupId, async (tx, writes) => {
    const now = deps.now();
    const group = await loadGroupForWrite(tx, groupId, { allowLocked: true });
    const role = await getMembership(tx, groupId, uid);
    if (!role) throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm");
    if (role === "OWNER") {
      throw new HttpError(409, "OWNER_MUST_TRANSFER", "Chủ nhóm cần chuyển quyền trước khi rời nhóm", {
        soleMember: asNumber(group.memberCount) === 1,
      });
    }
    await dropUpcomingAttendance(tx, writes, groupId, uid, now);
    writes.push(
      deleteWrite(memberPath(groupId, uid)),
      updateWrite(groupPath(groupId), { memberCount: Math.max(0, asNumber(group.memberCount) - 1), updatedAt: now }),
    );
    await deps.onMembershipChanged(tx, groupId, { removed: [uid] });
  });
  return ok();
};
