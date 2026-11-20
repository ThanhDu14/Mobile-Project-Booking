// Tạo bù chat nhóm `chats/group_{groupId}` cho nhóm tạo TRƯỚC khi deploy spec 091 (research R5: chat nhóm chỉ được
// tạo trong transaction `create` của group-manage, nên nhóm cũ chưa có). Chạy một lần sau khi deploy 091:
//
//   deno run -A --config supabase/functions/deno.json scripts/admin/backfill-group-chats.ts [--dry-run] --service-account <file.json>
//
// Trên Emulator (FIRESTORE_EMULATOR_HOST) không cần service account. Không commit file khóa. Chạy lại an toàn:
// nhóm đã có chat bị bỏ qua (kiểm tra lại trong transaction). Bỏ qua nhóm DELETED; nhóm LOCKED vẫn tạo chat
// (chat-send tự chặn bằng GROUP_LOCKED).
import { parseArgs } from "jsr:@std/cli@1.0.24/parse-args";

const args = parseArgs(Deno.args, { string: ["service-account"], boolean: ["dry-run"] });
const keyFile = args["service-account"] ?? Deno.env.get("GOOGLE_APPLICATION_CREDENTIALS");
if (keyFile) {
  const raw = await Deno.readTextFile(keyFile);
  Deno.env.set("FIREBASE_SERVICE_ACCOUNT", raw);
  if (!Deno.env.get("FIREBASE_PROJECT_ID")) Deno.env.set("FIREBASE_PROJECT_ID", JSON.parse(raw).project_id);
} else if (!Deno.env.get("FIRESTORE_EMULATOR_HOST")) {
  console.error("Thiếu --service-account hoặc GOOGLE_APPLICATION_CREDENTIALS");
  Deno.exit(1);
}

const emulator = Deno.env.get("FIRESTORE_EMULATOR_HOST");
if (keyFile && emulator) {
  console.error(`Có --service-account nhưng FIRESTORE_EMULATOR_HOST=${emulator} vẫn bật (sẽ ghi vào Emulator). Bỏ biến đó rồi chạy lại.`);
  Deno.exit(1);
}
console.log(`Project ${Deno.env.get("FIREBASE_PROJECT_ID")}${emulator ? ` (Emulator ${emulator})` : " (THẬT)"}`);

// Import sau khi đã đặt biến môi trường.
const { backfillGroupChats } = await import("./backfill-group-chats-lib.ts");
const r = await backfillGroupChats({ dryRun: args["dry-run"] });
console.log(
  `${args["dry-run"] ? "[dry-run] " : ""}Nhóm: ${r.scanned}, đã có chat: ${r.existing}, tạo mới: ${r.created.length}` +
    `, bỏ qua DELETED: ${r.skippedDeleted}, lỗi: ${r.failed.length}${r.created.length ? `\n  ${r.created.join("\n  ")}` : ""}`,
);
for (const f of r.failed) console.error(`  LỖI ${f.groupId}: ${f.error}`);
if (r.failed.length > 0) Deno.exit(1);
