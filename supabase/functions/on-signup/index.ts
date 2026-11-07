// Edge Function on-signup: khởi tạo users/{uid} và claim appRole sau khi đăng ký (spec 010, FR-006).
// verify_jwt = false trong supabase/config.toml: function tự xác minh Firebase ID token (research R3).
import { handleOnSignup } from "./handler.ts";

Deno.serve((req) => handleOnSignup(req));
