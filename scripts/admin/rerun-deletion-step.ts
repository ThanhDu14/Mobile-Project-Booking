// Chạy lại một bước dọn dẹp cho mọi accountDeletions có steps.<bước> == "SKIPPED" (spec 011 T082, research R12).
// Dùng khi helper của B, C, D đã có sau khi một số tài khoản đã được xóa (bước đó từng bị ghi SKIPPED):
//
//   deno run -A scripts/admin/rerun-deletion-step.ts <bước> --service-account <file.json>
//
// <bước>: notifyGuests | anonymizeReviews | anonymizeCommunity | anonymizeBookings | deleteOwnerData.
// Trên Emulator (FIRESTORE_EMULATOR_HOST, FIREBASE_AUTH_EMULATOR_HOST) không cần service account. Không commit file khóa.
import { parseArgs } from "jsr:@std/cli@1.0.24/parse-args";

const RERUNNABLE = ["notifyGuests", "anonymizeReviews", "anonymizeCommunity", "anonymizeBookings", "deleteOwnerData"];

function fail(message: string): never {
  console.error(message);
  Deno.exit(1);
}

const args = parseArgs(Deno.args, { string: ["service-account"] });
const step = String(args._[0] ?? "");
if (!RERUNNABLE.includes(step)) fail(`Cách dùng: rerun-deletion-step.ts <${RERUNNABLE.join("|")}>`);

const keyFile = args["service-account"] ?? Deno.env.get("GOOGLE_APPLICATION_CREDENTIALS");
if (keyFile) {
  const raw = await Deno.readTextFile(keyFile);
  Deno.env.set("FIREBASE_SERVICE_ACCOUNT", raw);
  if (!Deno.env.get("FIREBASE_PROJECT_ID")) Deno.env.set("FIREBASE_PROJECT_ID", JSON.parse(raw).project_id);
} else if (!Deno.env.get("FIRESTORE_EMULATOR_HOST")) {
  fail("Thiếu --service-account hoặc GOOGLE_APPLICATION_CREDENTIALS");
}

// Import sau khi đã đặt biến môi trường.
const { defaultDeletionDeps, markStep, runStep } = await import("../../supabase/functions/_shared/account-deletion.ts");
const { query } = await import("../../supabase/functions/_shared/firestore.ts");

const deps = defaultDeletionDeps();
const records = (await query("accountDeletions", []))
  .filter((r) => (r.data.steps as Record<string, string> | undefined)?.[step] === "SKIPPED");

let done = 0;
let stillSkipped = 0;
for (const record of records) {
  const result = await runStep(step as Parameters<typeof runStep>[0], record.id, record.data, deps);
  await markStep(record.id, step as Parameters<typeof markStep>[1], result, deps);
  if (result === "DONE") done++;
  else stillSkipped++;
}
console.log(`Bước ${step}: ${records.length} bản ghi SKIPPED, đã chạy xong ${done}, vẫn thiếu helper ${stillSkipped}.`);
if (stillSkipped > 0) console.log("Helper chưa được nối trong supabase/functions/_shared/account-deletion-helpers.ts.");
