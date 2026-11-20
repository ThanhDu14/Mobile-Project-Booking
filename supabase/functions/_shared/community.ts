// Helper `anonymizeCommunity` nhóm 09 (spec 090 + 091) cho `delete-account` (A, `DeletionHelpers.anonymizeCommunity`).
import { anonymizeCommunityChats } from "./chats.ts";
import { anonymizeCommunityGroups } from "./group-anonymize.ts";

/** Ẩn danh bài/nhóm (090) rồi chat (091) của `uid`. Idempotent, chạy lại an toàn. */
export async function anonymizeCommunity(uid: string): Promise<void> {
  await anonymizeCommunityGroups(uid);
  await anonymizeCommunityChats(uid);
}
