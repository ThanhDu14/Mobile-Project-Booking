// Edge Function `group-post` (spec 090, contracts/edge-functions-community.md). Khung: xác thực, parse action, router.
// Từng action sẽ được cài ở các task sau; hiện trả 501 NOT_IMPLEMENTED.
import { type ActionHandler, defaultGroupDeps, dispatchGroupAction, type GroupDeps } from "../_shared/groups.ts";

export const ACTIONS = ["create", "update", "delete", "hide", "unhide", "attend", "unattend", "report"] as const;

/** Handler theo action; action nào chưa có thì khung trả 501. */
const handlers: Record<string, ActionHandler> = {};

export function handleGroupPost(req: Request, deps: GroupDeps = defaultGroupDeps()): Promise<Response> {
  return dispatchGroupAction(req, deps, ACTIONS, handlers);
}
