// `attend`, `unattend` của `group-post` (spec 090 T054; data-model mục 6, research R3).
// Khóa theo `groupId` (cùng hàng đợi với create/update/delete/leave/remove của nhóm): `leave`/`remove` cũng sửa
// `attendees` + `attendeeCount` nên dùng chung một khóa tránh xung đột; Firestore transaction vẫn là chốt chặn
// cuối khi nhiều isolate chạy song song (đọc `attendees/{uid}` + bài trong transaction, ABORTED thì thử lại).
import { HttpError } from "../_shared/auth.ts";
import { createWrite, deleteWrite, updateWrite } from "../_shared/firestore.ts";
import { asDate, asNumber, inGroupTx, requireId, requireUuid } from "../_shared/group-tx.ts";
import {
  type ActionHandler,
  attendeePath,
  getMembership,
  loadGroupForWrite,
  postPath,
  userSnapshot,
} from "../_shared/groups.ts";

/** Quyết định: bài không có, không phải EVENT hoặc đang HIDDEN đều trả `404 POST_NOT_FOUND` (không lộ bài ẩn). */
const notFound = () => new HttpError(404, "POST_NOT_FOUND", "Sự kiện không còn tồn tại");

function attendance(attend: boolean): ActionHandler {
  return async ({ uid, body, deps }) => {
    const groupId = requireId(body, "groupId");
    const postId = requireUuid(body, "postId");

    const result = await inGroupTx(deps, groupId, async (tx, writes) => {
      await loadGroupForWrite(tx, groupId);
      if (await getMembership(tx, groupId, uid) === null) {
        throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm này");
      }
      const post = await tx.get(postPath(groupId, postId));
      if (!post || post.type !== "EVENT" || post.status !== "VISIBLE") throw notFound();
      const eventAt = asDate(post.eventAt);
      if (!eventAt || eventAt.getTime() <= deps.now().getTime()) {
        throw new HttpError(409, "EVENT_PAST", "Sự kiện đã diễn ra");
      }

      const count = asNumber(post.attendeeCount);
      const existing = await tx.get(attendeePath(groupId, postId, uid));
      // Đã ở đúng trạng thái yêu cầu: trả kết quả hiện tại, không ghi (idempotent, FR-016).
      if ((existing !== null) === attend) return { attendeeCount: count, attending: attend };

      if (attend) {
        // Không giới hạn theo `expectedCount` (AC 4.4): vượt vẫn được, app hiển thị nhãn "Vượt số dự kiến".
        writes.push(createWrite(attendeePath(groupId, postId, uid), {
          userId: uid,
          groupId,
          postId,
          eventAt,
          userSnapshot: await userSnapshot(tx, uid),
          joinedAt: deps.now(),
        }));
      } else {
        writes.push(deleteWrite(attendeePath(groupId, postId, uid)));
      }
      const next = Math.max(0, count + (attend ? 1 : -1));
      writes.push(updateWrite(postPath(groupId, postId), { attendeeCount: next }));
      return { attendeeCount: next, attending: attend };
    });
    return Response.json(result);
  };
}

export const attendEvent: ActionHandler = attendance(true);
export const unattendEvent: ActionHandler = attendance(false);
