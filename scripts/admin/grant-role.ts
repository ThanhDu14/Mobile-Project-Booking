// Cấp hoặc thu hồi vai trò thủ công (spec 010 quickstart §5, constitution II). Chạy trên máy người quản lý:
//
//   deno run -A scripts/admin/grant-role.ts <uid> <ADMIN|OWNER|PLAYER> --service-account <file.json>
//
// Thay --service-account bằng biến GOOGLE_APPLICATION_CREDENTIALS cũng được. Không commit file khóa.
// Thứ tự bắt buộc: gộp claim appRole TRƯỚC, rồi mới ghi users.role, để app không thấy vai trò mới trong
// Firestore khi token chưa có quyền tương ứng. Người dùng mở lại app (làm mới token) để nhận vai trò mới.
import { parseArgs } from "jsr:@std/cli@1.0.24/parse-args";

const ROLES = ["ADMIN", "OWNER", "PLAYER"] as const;
type Role = typeof ROLES[number];

function fail(message: string): never {
  console.error(message);
  Deno.exit(1);
}

const args = parseArgs(Deno.args, { string: ["service-account"] });
const [uid, role] = args._.map(String);
if (!uid || !ROLES.includes(role as Role)) {
  fail("Cách dùng: grant-role.ts <uid> <ADMIN|OWNER|PLAYER> [--service-account <file.json>]");
}

// Emulator (FIRESTORE_EMULATOR_HOST, FIREBASE_AUTH_EMULATOR_HOST) không cần service account.
const keyFile = args["service-account"] ?? Deno.env.get("GOOGLE_APPLICATION_CREDENTIALS");
if (keyFile) {
  const raw = await Deno.readTextFile(keyFile);
  Deno.env.set("FIREBASE_SERVICE_ACCOUNT", raw);
  if (!Deno.env.get("FIREBASE_PROJECT_ID")) Deno.env.set("FIREBASE_PROJECT_ID", JSON.parse(raw).project_id);
} else if (!Deno.env.get("FIRESTORE_EMULATOR_HOST")) {
  fail("Thiếu --service-account hoặc GOOGLE_APPLICATION_CREDENTIALS");
}

// Import sau khi đã đặt biến môi trường.
const { commit, get, updateWrite } = await import("../../supabase/functions/_shared/firestore.ts");
const { getCustomClaims, mergeClaims } = await import("../../supabase/functions/_shared/identity.ts");

const path = `users/${uid}`;
const user = await get(path);
if (!user) fail(`Không có ${path}: người dùng phải hoàn tất đăng ký (on-signup) trước.`);

await mergeClaims(uid, { role: "authenticated", appRole: role });
await commit([updateWrite(path, { role }, ["updatedAt"])]);

console.log(`Đã cấp ${role} cho ${uid}. Claim hiện tại: ${JSON.stringify(await getCustomClaims(uid))}`);
console.log("Người dùng cần mở lại app để token nhận vai trò mới.");
