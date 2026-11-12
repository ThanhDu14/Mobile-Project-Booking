// Edge Function `group-post` (spec 090, contracts/edge-functions-community.md). Khung: xác thực, parse action, router.
// Đủ 8 action: create, update, delete (posts.ts), attend, unattend (attendance.ts), hide, unhide, report (moderation.ts).
import { attendEvent, unattendEvent } from "./attendance.ts";
import { hidePost, reportPost, unhidePost } from "./moderation.ts";
import { createPost, deletePost, updatePost } from "./posts.ts";
import { type ActionHandler, defaultGroupDeps, dispatchGroupAction, type GroupDeps } from "../_shared/groups.ts";

export const ACTIONS = ["create", "update", "delete", "hide", "unhide", "attend", "unattend", "report"] as const;

/** Handler theo action. */
const handlers: Record<string, ActionHandler> = {
  create: createPost,
  update: updatePost,
  delete: deletePost,
  attend: attendEvent,
  unattend: unattendEvent,
  hide: hidePost,
  unhide: unhidePost,
  report: reportPost,
};

export function handleGroupPost(req: Request, deps: GroupDeps = defaultGroupDeps()): Promise<Response> {
  return dispatchGroupAction(req, deps, ACTIONS, handlers);
}
