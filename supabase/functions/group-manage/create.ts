// `create` và `update` của `group-manage` (spec 090 T024, T034; data-model mục 1–2, research R4, R12).
import { HttpError } from "../_shared/auth.ts";
import { createWrite, deleteWrite, type FirestoreTransaction, updateWrite, type Write } from "../_shared/firestore.ts";
import {
  type ActionHandler,
  getMembership,
  type GroupDoc,
  groupPath,
  joinRequestPath,
  loadGroupForWrite,
  memberPath,
  notifyGroupActivity,
  type SignedUpload,
  userSnapshot,
} from "../_shared/groups.ts";
import { nameKeywords } from "../_shared/keywords.ts";
import { asNumber, inGroupTx, invalid, requireId, requireRequestId } from "../_shared/group-tx.ts";
import {
  assertDistrictExists,
  AVATAR_BUCKET,
  AVATAR_EXTS,
  avatarObjectPath,
  groupJson,
  parseAvatarExt,
  parseDescription,
  parseDistrictId,
  parseName,
  parseVisibility,
} from "./common.ts";

/** Mỗi chủ nhóm tối đa 5 nhóm `ACTIVE`/`LOCKED` (R4). */
export const MAX_OWNED_GROUPS = 5;
/** Quá số này thì không tự duyệt trong một transaction được (R12, giới hạn 500 ghi). */
export const MAX_AUTO_APPROVE = 200;

// ---------- create ----------

export const createGroup: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireRequestId(body);
  const name = parseName(body.name);
  const description = parseDescription(body.description ?? "");
  const districtId = parseDistrictId(body.districtId);
  const visibility = parseVisibility(body.visibility);
  if (body.hasAvatar !== undefined && typeof body.hasAvatar !== "boolean") throw invalid("hasAvatar phải là boolean");
  const hasAvatar = body.hasAvatar === true;
  const ext = parseAvatarExt(body);

  const outcome = await inGroupTx(deps, groupId, async (tx, writes) => {
    const now = deps.now();
    const existing = await tx.get(groupPath(groupId)) as GroupDoc | null;
    if (existing) {
      // Gửi lại cùng requestId: trả nhóm cũ, không ghi gì (hook cũng không được gọi).
      if (existing.ownerId !== uid) {
        throw new HttpError(409, "REQUEST_ID_CONFLICT", "requestId đã được dùng cho nhóm khác");
      }
      return { created: false, group: existing };
    }

    const owned = await tx.query("groups", [
      { field: "ownerId", op: "EQUAL", value: uid },
      { field: "status", op: "IN", value: ["ACTIVE", "LOCKED"] },
    ]);
    if (owned.length >= MAX_OWNED_GROUPS) {
      throw new HttpError(409, "OWNER_LIMIT_REACHED", `Bạn đã làm chủ tối đa ${MAX_OWNED_GROUPS} nhóm`);
    }
    await assertDistrictExists(tx, districtId);
    const snapshot = await userSnapshot(tx, uid);

    const group: GroupDoc = {
      name,
      nameKeywords: nameKeywords(name),
      description,
      avatarPath: null,
      districtId,
      ownerId: uid,
      visibility,
      memberCount: 1,
      pendingRequestCount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    };
    writes.push(
      createWrite(groupPath(groupId), group),
      createWrite(memberPath(groupId, uid), {
        userId: uid,
        groupId,
        role: "OWNER",
        userSnapshot: snapshot,
        joinedAt: now,
        createdAt: now,
        updatedAt: now,
      }),
    );
    await deps.onMembershipChanged(tx, groupId, { created: true, added: [uid] });
    return { created: true, group };
  });

  // Cấp URL tải ảnh sau commit. Lặp lại (200) mà nhóm chưa có ảnh thì cấp lại, để app thử tải lại được.
  let avatarUpload: SignedUpload | undefined;
  if (hasAvatar && !outcome.group.avatarPath) {
    avatarUpload = await deps.storage.createSignedUploadUrl(AVATAR_BUCKET, avatarObjectPath(groupId, ext), true);
  }
  return Response.json(
    { group: groupJson(groupId, outcome.group), ...(avatarUpload ? { avatarUpload } : {}) },
    { status: outcome.created ? 201 : 200 },
  );
};

// ---------- update ----------

const AVATAR_ACTIONS = ["KEEP", "REPLACE", "CONFIRM", "REMOVE"];

export const updateGroup: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const patch: { name?: string; description?: string; districtId?: string; visibility?: "PUBLIC" | "PRIVATE" } = {};
  if (body.name !== undefined) patch.name = parseName(body.name);
  if (body.description !== undefined) patch.description = parseDescription(body.description);
  if (body.districtId !== undefined) patch.districtId = parseDistrictId(body.districtId);
  if (body.visibility !== undefined) patch.visibility = parseVisibility(body.visibility);
  const avatar = body.avatar ?? "KEEP";
  if (typeof avatar !== "string" || !AVATAR_ACTIONS.includes(avatar)) {
    throw invalid("avatar phải là KEEP, REPLACE, CONFIRM hoặc REMOVE");
  }
  const ext = parseAvatarExt(body);

  const outcome = await inGroupTx(deps, groupId, async (tx, writes) => {
    const now = deps.now();
    const group = await loadGroupForWrite(tx, groupId);
    const role = await getMembership(tx, groupId, uid);
    if (!role) throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm");
    if (role !== "OWNER") throw new HttpError(403, "FORBIDDEN", "Chỉ chủ nhóm được sửa nhóm");

    const changes: Record<string, unknown> = { ...patch };
    if (patch.name !== undefined) changes.nameKeywords = nameKeywords(patch.name);
    if (patch.districtId !== undefined && patch.districtId !== group.districtId) {
      await assertDistrictExists(tx, patch.districtId);
    }

    // Ảnh đại diện: CONFIRM chỉ ghi `avatarPath` khi object đã có thật trong Storage.
    let obsolete: string[] = [];
    if (avatar === "CONFIRM") {
      const path = avatarObjectPath(groupId, ext);
      if (!await deps.storage.exists(AVATAR_BUCKET, path)) {
        throw new HttpError(400, "PHOTO_MISSING", "Ảnh đại diện chưa được tải lên");
      }
      changes.avatarPath = path;
      // Ảnh cũ khác đuôi (đã thay ảnh) thì xóa sau commit.
      if (typeof group.avatarPath === "string" && group.avatarPath !== path) obsolete = [group.avatarPath];
    } else if (avatar === "REMOVE") {
      changes.avatarPath = null;
      obsolete = AVATAR_EXTS.map((e) => avatarObjectPath(groupId, e));
    }

    // PRIVATE -> PUBLIC: tự duyệt yêu cầu đang chờ trong cùng transaction (R12, T034).
    let approved: string[] = [];
    if (group.visibility === "PRIVATE" && patch.visibility === "PUBLIC") {
      approved = await autoApprove(tx, writes, groupId, now);
      if (approved.length > 0) {
        changes.memberCount = asNumber(group.memberCount) + approved.length;
        changes.pendingRequestCount = Math.max(0, asNumber(group.pendingRequestCount) - approved.length);
      }
    }

    changes.updatedAt = now;
    writes.push(updateWrite(groupPath(groupId), changes));
    if (approved.length > 0) await deps.onMembershipChanged(tx, groupId, { added: approved });
    return { group: { ...group, ...changes } as GroupDoc, approved, obsolete };
  });

  if (outcome.obsolete.length > 0) {
    try {
      await deps.storage.remove(AVATAR_BUCKET, outcome.obsolete);
    } catch (e) {
      console.warn(`[group-manage] xóa ảnh đại diện cũ của nhóm ${groupId} thất bại`, e);
    }
  }
  await notifyGroupActivity(deps, "JOIN_APPROVED", outcome.approved, { groupId });

  let avatarUpload: SignedUpload | undefined;
  if (avatar === "REPLACE") {
    avatarUpload = await deps.storage.createSignedUploadUrl(AVATAR_BUCKET, avatarObjectPath(groupId, ext), true);
  }
  return Response.json({
    group: groupJson(groupId, outcome.group),
    ...(avatarUpload ? { avatarUpload } : {}),
    autoApproved: outcome.approved.length,
  });
};

/**
 * Chuyển mọi `joinRequests` thành `members` (trừ người có `removals`, họ vẫn chờ duyệt). Chỉ đẩy các `Write` của
 * yêu cầu và thành viên; bộ đếm do caller ghi. Trả uid được duyệt. Hơn 200 yêu cầu thì 409 TOO_MANY_PENDING.
 */
async function autoApprove(
  tx: FirestoreTransaction,
  writes: Write[],
  groupId: string,
  now: Date,
): Promise<string[]> {
  const parent = groupPath(groupId);
  const requests = await tx.query("joinRequests", [], {
    parent,
    limit: MAX_AUTO_APPROVE + 1,
    orderBy: [{ field: "createdAt" }],
  });
  if (requests.length > MAX_AUTO_APPROVE) {
    throw new HttpError(409, "TOO_MANY_PENDING", "Có quá nhiều yêu cầu đang chờ, hãy duyệt bớt trước");
  }
  if (requests.length === 0) return [];
  const removed = new Set((await tx.query("removals", [], { parent })).map((r) => r.id));
  const approved: string[] = [];
  for (const r of requests) {
    if (removed.has(r.id)) continue;
    writes.push(
      deleteWrite(joinRequestPath(groupId, r.id)),
      createWrite(memberPath(groupId, r.id), {
        userId: r.id,
        groupId,
        role: "MEMBER",
        userSnapshot: r.data.userSnapshot ?? { displayName: null, avatarPath: null },
        joinedAt: now,
        createdAt: now,
        updatedAt: now,
      }),
    );
    approved.push(r.id);
  }
  return approved;
}
