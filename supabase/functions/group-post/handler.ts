// Edge Function `group-post` (spec 090, contracts/edge-functions-community.md). Khung: xác thực, parse action, router.
// Đã cài: create, update, delete cho bài POST (T046). Các action còn lại trả 501.
import { createPost, deletePost, updatePost } from "./posts.ts";
import { type ActionHandler, defaultGroupDeps, dispatchGroupAction, type GroupDeps } from "../_shared/groups.ts";

export const ACTIONS = ["create", "update", "delete", "hide", "unhide", "attend", "unattend", "report"] as const;

/** Handler theo action; action nào chưa có thì khung trả 501. */
const handlers: Record<string, ActionHandler> = {
  create: createPost,
  update: updatePost,
  delete: deletePost,
};

export function handleGroupPost(req: Request, deps: GroupDeps = defaultGroupDeps()): Promise<Response> {
  return dispatchGroupAction(req, deps, ACTIONS, handlers);
}
