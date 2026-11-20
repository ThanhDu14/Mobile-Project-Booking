// Sửa các mốc thời gian bị lưu dạng chuỗi ISO thay vì Timestamp (rà soát 060 ngày 2026-11-18).
//
// Nguyên nhân: player-cancel-booking cũ ghi `new Date().toISOString()`; REST lưu thành stringValue. App đọc
// các trường này bằng getTimestamp nên đơn hủy bị lọc khỏi danh sách và tab "Đã hủy" lỗi khi phân trang.
//
//   deno run -A scripts/admin/fix-string-timestamps.ts --project <id>            # chỉ liệt kê (mặc định)
//   deno run -A scripts/admin/fix-string-timestamps.ts --project <id> --apply    # ghi thật
//
// Token: biến FIRESTORE_ACCESS_TOKEN, nếu không có thì lấy `gcloud auth print-access-token`.
// Chỉ đổi đúng các trường liệt kê ở TARGETS khi giá trị là chuỗi ISO hợp lệ; mỗi lần ghi kèm điều kiện
// updateTime của document để không đè lên thay đổi mới hơn.
import { parseArgs } from "jsr:@std/cli@1.0.24/parse-args";

const TARGETS: Record<string, string[]> = {
  bookings: ["updatedAt", "cancelledAt"],
  refunds: ["createdAt"],
  users: ["updatedAt"],
};

const args = parseArgs(Deno.args, { string: ["project"], boolean: ["apply"] });
const project = args.project ?? Deno.env.get("FIREBASE_PROJECT_ID");
if (!project) {
  console.error("Thiếu --project <id>");
  Deno.exit(1);
}

async function accessToken(): Promise<string> {
  const fromEnv = Deno.env.get("FIRESTORE_ACCESS_TOKEN");
  if (fromEnv) return fromEnv;
  const out = await new Deno.Command("gcloud", { args: ["auth", "print-access-token"], stdout: "piped" }).output();
  if (!out.success) throw new Error("Không lấy được token từ gcloud");
  return new TextDecoder().decode(out.stdout).trim();
}

const token = await accessToken();
const base = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
const headers = {
  Authorization: `Bearer ${token}`,
  "Content-Type": "application/json",
  "x-goog-user-project": project,
};

interface Doc {
  name: string;
  updateTime: string;
  fields?: Record<string, Record<string, unknown>>;
}

async function listAll(collection: string): Promise<Doc[]> {
  const docs: Doc[] = [];
  let pageToken = "";
  do {
    const url = `${base}/${collection}?pageSize=300${pageToken ? `&pageToken=${pageToken}` : ""}`;
    const res = await fetch(url, { headers });
    if (!res.ok) throw new Error(`Đọc ${collection} lỗi ${res.status}: ${await res.text()}`);
    const body = await res.json() as { documents?: Doc[]; nextPageToken?: string };
    docs.push(...(body.documents ?? []));
    pageToken = body.nextPageToken ?? "";
  } while (pageToken);
  return docs;
}

function isoToTimestamp(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

let found = 0;
let fixed = 0;
for (const [collection, fields] of Object.entries(TARGETS)) {
  for (const doc of await listAll(collection)) {
    const changes: Record<string, { timestampValue: string }> = {};
    for (const field of fields) {
      const iso = isoToTimestamp(doc.fields?.[field]?.stringValue);
      if (iso) changes[field] = { timestampValue: iso };
    }
    const names = Object.keys(changes);
    if (names.length === 0) continue;
    found++;
    const id = doc.name.split("/documents/")[1];
    console.log(`${args.apply ? "Sửa" : "Cần sửa"} ${id}: ${names.join(", ")}`);
    if (!args.apply) continue;

    const res = await fetch(`${base}:commit`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        writes: [{
          update: { name: doc.name, fields: changes },
          updateMask: { fieldPaths: names },
          currentDocument: { updateTime: doc.updateTime },
        }],
      }),
    });
    if (!res.ok) {
      console.error(`  Bỏ qua ${id}: ${res.status} ${await res.text()}`);
      continue;
    }
    fixed++;
  }
}
console.log(args.apply ? `Đã sửa ${fixed}/${found} document.` : `${found} document cần sửa. Thêm --apply để ghi.`);
