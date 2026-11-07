// Edge Function delete-account-sweeper: làm tiếp các lần xóa tài khoản chưa xong (spec 011 US6, research R11).
// verify_jwt = false: xác thực bằng header x-cron-secret.
import { handleSweeper } from "./handler.ts";

Deno.serve((req) => handleSweeper(req));
