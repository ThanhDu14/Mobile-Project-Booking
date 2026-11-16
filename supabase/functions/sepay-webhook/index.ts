// verify_jwt tắt trong config.toml: SePay không có JWT của Supabase; mọi request được xác thực bằng HMAC
// (SEPAY_WEBHOOK_SECRET) trong handler.
import { handleSepayWebhook } from "./handler.ts";

Deno.serve((req) => handleSepayWebhook(req));
