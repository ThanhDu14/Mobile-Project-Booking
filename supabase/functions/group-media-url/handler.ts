// Edge Function `group-media-url` (spec 090, contracts/edge-functions-community.md). Khung: xác thực, parse action, router.
// Đã cài đủ 3 action (T045).
import { discardUploads, downloadUrls, uploadUrls } from "./actions.ts";
import { type ActionHandler, defaultGroupDeps, dispatchGroupAction, type GroupDeps } from "../_shared/groups.ts";

export const ACTIONS = ["upload", "download", "discard"] as const;

/** Handler theo action. */
const handlers: Record<string, ActionHandler> = {
  upload: uploadUrls,
  download: downloadUrls,
  discard: discardUploads,
};

export function handleGroupMediaUrl(req: Request, deps: GroupDeps = defaultGroupDeps()): Promise<Response> {
  return dispatchGroupAction(req, deps, ACTIONS, handlers);
}
