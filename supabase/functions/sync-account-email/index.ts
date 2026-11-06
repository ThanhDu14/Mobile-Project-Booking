// Edge Function sync-account-email: đồng bộ users.email sau khi người dùng bấm link xác minh email mới (spec 011 US7).
// verify_jwt = false trong supabase/config.toml: function tự xác minh Firebase ID token bằng requireUser.
import { handleSyncAccountEmail } from "./handler.ts";

Deno.serve((req) => handleSyncAccountEmail(req));
