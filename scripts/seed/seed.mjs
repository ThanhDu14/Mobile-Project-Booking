// Nạp dữ liệu mẫu (danh mục quận/tiện ích + 10 cơ sở cầu lông) vào Firestore. Xem README.md.
//
//   node seed.mjs --emulator            # Firestore Emulator (127.0.0.1:8080)
//   node seed.mjs --project=<id>        # project thật, dùng Application Default Credentials, hỏi xác nhận
//   node seed.mjs --dry-run             # chỉ kiểm tra dữ liệu, không kết nối
//   thêm --reset để xóa cơ sở có ID bắt đầu bằng "demo-" trước khi nạp
import fs from "node:fs";
import readline from "node:readline/promises";
import { parseArgs } from "node:util";

// Quy ước daysOfWeek chưa chốt (spec 020 research R5 đề xuất ISO, spec 040/041 đang ghi 1 = CN).
// Đổi một chỗ này khi nhóm chốt.
const DAY_CONVENTION = "ISO"; // "ISO": 1 = T2 … 7 = CN; "CALENDAR": 1 = CN, 2 = T2 … 7 = T7
const DAY_ORDER = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
const OWNER_ID = "demo-owner-1";
const DEMO_PREFIX = "demo-";

const { values: args } = parseArgs({
  options: {
    emulator: { type: "boolean", default: false },
    project: { type: "string" },
    reset: { type: "boolean", default: false },
    "dry-run": { type: "boolean", default: false },
  },
});

const here = (f) => new URL(f, import.meta.url);
const catalog = JSON.parse(fs.readFileSync(here("./catalog.json"), "utf8"));
const { venues } = JSON.parse(fs.readFileSync(here("./venues.json"), "utf8"));

// ---------- chuyển đổi ----------

function dayNumber(code) {
  const iso = DAY_ORDER.indexOf(code) + 1;
  if (iso === 0) throw new Error(`Thứ không hợp lệ: ${code}`);
  return DAY_CONVENTION === "ISO" ? iso : (iso % 7) + 1;
}

// "T2-T6" | "T7-CN" | "T2" -> danh sách mã thứ
function expandDays(spec) {
  const [from, to = from] = spec.split("-");
  const a = DAY_ORDER.indexOf(from);
  const b = DAY_ORDER.indexOf(to);
  if (a < 0 || b < 0 || b < a) throw new Error(`Khoảng thứ không hợp lệ: ${spec}`);
  return DAY_ORDER.slice(a, b + 1);
}

const minutes = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(2));

function label(days, pricePerHour, weekdayMax) {
  if (days.every((d) => d === "T7" || d === "CN")) return "WEEKEND";
  return pricePerHour === weekdayMax ? "PEAK" : "NORMAL";
}

// Contract spec 020 keyword-normalization §2–3. Thay bằng _shared/search-keywords.ts khi A làm xong T017.
function normalize(s) {
  return s
    .replace(/[đĐ]/g, "d")
    .normalize("NFD")
    .replace(/\p{Mn}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function generateKeywords(name) {
  const words = normalize(name).split(" ").filter(Boolean);
  const out = new Set();
  for (const w of words) {
    if (w.length === 1) out.add(w);
    else for (let k = 2; k <= Math.min(w.length, 15); k++) out.add(w.slice(0, k));
  }
  return [...out].sort().slice(0, 60);
}

function geohash(lat, lng, precision = 10) {
  const base32 = "0123456789bcdefghjkmnpqrstuvwxyz";
  const range = [[-90, 90], [-180, 180]];
  let hash = "", bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    const [r, v] = even ? [range[1], lng] : [range[0], lat];
    const mid = (r[0] + r[1]) / 2;
    if (v >= mid) { ch = (ch << 1) | 1; r[0] = mid; } else { ch <<= 1; r[1] = mid; }
    even = !even;
    if (++bit === 5) { hash += base32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

function buildVenue(v) {
  const errors = [];
  const open = minutes(v.openTime);
  const close = minutes(v.closeTime);
  if (!catalog.districts.some((d) => d.id === v.districtId)) errors.push(`districtId ${v.districtId} không có trong catalog`);
  if (open >= close) errors.push("openTime phải nhỏ hơn closeTime");

  const rules = v.priceRules.map((r) => ({ ...r, dayCodes: expandDays(r.days) }));
  for (const r of rules) {
    const s = minutes(r.start), e = minutes(r.end);
    if (s % 30 || e % 30) errors.push(`${r.days} ${r.start}-${r.end}: giờ không theo mốc 30 phút`);
    if (s >= e) errors.push(`${r.days} ${r.start}-${r.end}: giờ bắt đầu phải nhỏ hơn giờ kết thúc`);
    if (s < open || e > close) errors.push(`${r.days} ${r.start}-${r.end}: nằm ngoài giờ mở cửa`);
    // Schema yêu cầu chia hết cho 2.000 (giá khung 30 phút = pricePerHour / 2). Giá thật có thể lẻ (65.000):
    // làm tròn lên, giữ giá gốc trong venues.json.
    if (r.pricePerHour % 2000) {
      const rounded = Math.ceil(r.pricePerHour / 2000) * 2000;
      console.warn(`  [làm tròn] ${v.id} ${r.days} ${r.start}-${r.end}: ${r.pricePerHour} -> ${rounded}`);
      r.pricePerHour = rounded;
    }
  }
  for (const day of DAY_ORDER) {
    const sameDay = rules.filter((r) => r.dayCodes.includes(day)).sort((a, b) => minutes(a.start) - minutes(b.start));
    for (let i = 1; i < sameDay.length; i++) {
      if (minutes(sameDay[i].start) < minutes(sameDay[i - 1].end)) errors.push(`${day}: ${sameDay[i - 1].start}-${sameDay[i - 1].end} chồng giờ với ${sameDay[i].start}-${sameDay[i].end}`);
    }
  }
  if (!v.id.startsWith(DEMO_PREFIX)) errors.push(`ID phải bắt đầu bằng "${DEMO_PREFIX}"`);
  if (errors.length) throw new Error(`${v.id}:\n  - ${errors.join("\n  - ")}`);
  const weekdayMax = Math.max(...rules.filter((r) => r.dayCodes.some((d) => d !== "T7" && d !== "CN")).map((r) => r.pricePerHour));

  const courts = v.courts.map((name, i) => ({
    id: `court-${String(i + 1).padStart(2, "0")}`,
    data: { name, surfaceType: "PU", isActive: true, sortOrder: i + 1 },
  }));
  const priceRules = rules.map((r, i) => ({
    id: `rule-${String(i + 1).padStart(2, "0")}`,
    data: {
      daysOfWeek: r.dayCodes.map(dayNumber),
      startTime: r.start,
      endTime: r.end,
      pricePerHour: r.pricePerHour,
      label: label(r.dayCodes, r.pricePerHour, weekdayMax),
    },
  }));
  const venue = {
    ownerId: OWNER_ID,
    name: v.name,
    nameKeywords: generateKeywords(v.name),
    address: v.address,
    districtId: v.districtId,
    location: v.location,
    geohash: geohash(v.location.lat, v.location.lng),
    openTime: v.openTime,
    closeTime: v.closeTime,
    phone: v.phone,
    amenityIds: [],
    photoPaths: v.photoUrls,
    courtCount: courts.length,
    minPricePerHour: Math.min(...rules.map((r) => r.pricePerHour)),
    acceptsRecurring: true,
    status: "ACTIVE",
  };
  return { id: v.id, venue, courts, priceRules };
}

const built = venues.map(buildVenue);
console.log(`Dữ liệu hợp lệ: ${catalog.districts.length} quận, ${catalog.amenities.length} tiện ích, ${built.length} cơ sở, ` +
  `${built.reduce((n, b) => n + b.courts.length, 0)} sân con, ${built.reduce((n, b) => n + b.priceRules.length, 0)} dòng giá ` +
  `(daysOfWeek theo ${DAY_CONVENTION}).`);
if (args["dry-run"]) process.exit(0);

// ---------- ghi Firestore ----------

if (args.emulator === Boolean(args.project)) {
  console.error("Chọn đúng một trong --emulator hoặc --project=<id> (hoặc --dry-run).");
  process.exit(1);
}
if (args.emulator) process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
const projectId = args.project ?? "demo-smashnow";

if (args.project) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  const answer = await rl.question(`Sắp ghi dữ liệu mẫu lên project THẬT "${projectId}"${args.reset ? " (có --reset)" : ""}. Gõ tên project để xác nhận: `);
  rl.close();
  if (answer.trim() !== projectId) {
    console.log("Đã hủy.");
    process.exit(1);
  }
}

const { initializeApp, applicationDefault } = await import("firebase-admin/app");
const { getFirestore, FieldValue, FieldPath, GeoPoint } = await import("firebase-admin/firestore");
initializeApp(args.emulator ? { projectId } : { projectId, credential: applicationDefault() });
const db = getFirestore();
const now = FieldValue.serverTimestamp();

if (args.reset) {
  const snap = await db.collection("venues")
    .where(FieldPath.documentId(), ">=", DEMO_PREFIX)
    .where(FieldPath.documentId(), "<", DEMO_PREFIX + "")
    .get();
  for (const doc of snap.docs) await db.recursiveDelete(doc.ref);
  console.log(`--reset: đã xóa ${snap.size} cơ sở demo cũ.`);
}

// Danh mục: giữ createdAt nếu đã có.
async function upsertCatalog(collection, items, extra) {
  const refs = items.map((it) => db.collection(collection).doc(it.id));
  const existing = await db.getAll(...refs);
  const batch = db.batch();
  items.forEach(({ id, ...fields }, i) => {
    const data = { ...fields, ...extra, isActive: true, updatedAt: now };
    if (!existing[i].exists) data.createdAt = now;
    batch.set(refs[i], data, { merge: true });
  });
  await batch.commit();
}
await upsertCatalog("districts", catalog.districts, { cityName: "TP. Hồ Chí Minh" });
await upsertCatalog("amenities", catalog.amenities, {});

for (const { id, venue, courts, priceRules } of built) {
  const ref = db.collection("venues").doc(id);
  const old = await ref.get();
  const batch = db.batch();
  // Điểm đánh giá do spec 070 cập nhật; chạy lại seed không được xóa.
  batch.set(ref, {
    ...venue,
    location: new GeoPoint(venue.location.lat, venue.location.lng),
    ratingAvg: old.exists ? old.get("ratingAvg") ?? 0 : 0,
    ratingCount: old.exists ? old.get("ratingCount") ?? 0 : 0,
    createdAt: old.exists ? old.get("createdAt") ?? now : now,
    updatedAt: now,
  });
  for (const sub of ["courts", "priceRules"]) {
    const keep = new Set((sub === "courts" ? courts : priceRules).map((c) => c.id));
    const stale = await ref.collection(sub).get();
    stale.docs.filter((d) => !keep.has(d.id)).forEach((d) => batch.delete(d.ref));
  }
  for (const c of courts) batch.set(ref.collection("courts").doc(c.id), { ...c.data, createdAt: now, updatedAt: now });
  for (const r of priceRules) batch.set(ref.collection("priceRules").doc(r.id), { ...r.data, createdAt: now, updatedAt: now });
  await batch.commit();
  console.log(`  ${id}: ${courts.length} sân, ${priceRules.length} dòng giá, từ ${venue.minPricePerHour.toLocaleString("vi-VN")}đ/giờ`);
}
console.log(`Xong (${args.emulator ? "Emulator" : projectId}).`);
