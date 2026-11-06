// Edge Function delete-account: xem trước và xóa vĩnh viễn tài khoản (spec 011 US6, FR-018–FR-022a).
// verify_jwt = false trong supabase/config.toml: function tự xác minh Firebase ID token bằng requireUser.
import { handleDeleteAccount } from "./handler.ts";

Deno.serve((req) => handleDeleteAccount(req));
