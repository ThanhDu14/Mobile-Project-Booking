// Edge Function `group-manage` (spec 090, contracts/edge-functions-community.md). Khung: xác thực, parse action, router.
// Đã cài: create, update (T024, T034), join, cancelRequest, approve, reject, leave (T033). Còn lại trả 501.
import { approveRequest, cancelRequest, joinGroup, leaveGroup, rejectRequest } from "./membership.ts";
import { createGroup, updateGroup } from "./create.ts";
import { type ActionHandler, defaultGroupDeps, dispatchGroupAction, type GroupDeps } from "../_shared/groups.ts";

export const ACTIONS = [
  "create",
  "update",
  "delete",
  "join",
  "cancelRequest",
  "leave",
  "approve",
  "reject",
  "setRole",
  "transferOwner",
  "remove",
] as const;

/** Handler theo action; action nào chưa có thì khung trả 501. */
const handlers: Record<string, ActionHandler> = {
  create: createGroup,
  update: updateGroup,
  join: joinGroup,
  cancelRequest,
  approve: approveRequest,
  reject: rejectRequest,
  leave: leaveGroup,
};

export function handleGroupManage(req: Request, deps: GroupDeps = defaultGroupDeps()): Promise<Response> {
  return dispatchGroupAction(req, deps, ACTIONS, handlers);
}
