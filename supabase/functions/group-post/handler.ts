// Edge Function `group-post` (spec 090, contracts/edge-functions-community.md). Khung: xác thực, parse action, router.
// Đã cài: create, update, delete (POST và EVENT, T046/T053), attend, unattend (T054). Các action còn lại trả 501.
import { attendEvent, unattendEvent } from "./attendance.ts";
import { createPost, deletePost, updatePost } from "./posts.ts";
import { type ActionHandler, defaultGroupDeps, dispatchGroupAction, type GroupDeps } from "../_shared/groups.ts";

export const ACTIONS = ["create", "update", "delete", "hide", "unhide", "attend", "unattend", "report"] as const;

/** Handler theo action; action nào chưa có thì khung trả 501. */
const handlers: Record<string, ActionHandler> = {
  create: createPost,
  update: updatePost,
  delete: deletePost,
  attend: attendEvent,
  unattend: unattendEvent,
};

export function handleGroupPost(req: Request, deps: GroupDeps = defaultGroupDeps()): Promise<Response> {
  return dispatchGroupAction(req, deps, ACTIONS, handlers);
}
