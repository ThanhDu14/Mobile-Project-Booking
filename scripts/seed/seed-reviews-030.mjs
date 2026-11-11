// Đánh giá demo cho venues/demo-k3 (spec 030 T026, research R13). CHỈ chạy trên Firebase Emulator.
//
//   node seed-reviews-030.mjs --emulator                          # project demo-smashnow (như seed.mjs)
//   node seed-reviews-030.mjs --emulator --project-id=mobile-22e6c  # Emulator chạy với projectId của app
//
// Ghi 12 reviews theo specs/070-review-favorite/data-model.md §1: 1 HIDDEN, 1 của người đã xóa tài khoản,
// 1 có phản hồi của chủ sân, 2 có ảnh; createdAt cách nhau 1 ngày. Cập nhật ratingAvg, ratingCount, ratingSums
// của venues/demo-k3 theo 11 đánh giá VISIBLE. ID cố định (demo-k3-review-01…12) nên chạy lại không tạo bản trùng.
// Không sửa seed.mjs. Bỏ đi khi seed đánh giá của spec 070 (T025) có.
import { parseArgs } from "node:util";

const { values: args } = parseArgs({
  options: {
    emulator: { type: "boolean", default: false },
    "project-id": { type: "string" },
  },
});

if (!args.emulator) {
  console.error("Script này chỉ chạy trên Firebase Emulator: thêm --emulator. Không có chế độ ghi lên project thật.");
  process.exit(1);
}
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
const host = process.env.FIRESTORE_EMULATOR_HOST.split(":")[0];
if (!["127.0.0.1", "localhost", "0.0.0.0", "::1"].includes(host)) {
  console.error(`FIRESTORE_EMULATOR_HOST phải trỏ về máy này, đang là ${process.env.FIRESTORE_EMULATOR_HOST}.`);
  process.exit(1);
}
const projectId = args["project-id"] ?? "demo-smashnow";

const VENUE_ID = "demo-k3";
const OWNER_REPLY = "Cảm ơn bạn đã ủng hộ sân, hẹn gặp lại bạn!";
// [court, cleanliness, service], tên người viết, bình luận. Chỉ số 1 là mới nhất.
const DATA = [
  [[5, 5, 4], "Trần Minh Khoa", "Sân thảm mới, đèn sáng đều, nhân viên hỗ trợ nhiệt tình. Sẽ quay lại vào cuối tuần.", { reply: true }],
  [[1, 1, 1], "Tài khoản spam", "Nội dung vi phạm đã bị quản trị viên ẩn.", { hidden: true }],
  [[4, 4, 5], null, "Giá giờ cao điểm hơi cao nhưng sân đẹp.", { deleted: true }],
  [[5, 4, 4], "Nguyễn Thu Hà", "Có chỗ gửi xe rộng, nước uống đủ loại.", { photos: 2 }],
  [[3, 3, 4], "Lê Hoàng Nam", "Giờ tối hơi đông, nên đặt trước.", {}],
  [[4, 5, 4], "Phạm Quốc Bảo", "Phòng thay đồ sạch sẽ.", { photos: 1 }],
  [[5, 5, 5], "Võ Thanh Tâm", "", {}],
  [[4, 4, 4], "Đỗ Gia Huy", "Ổn trong tầm giá.", {}],
  [[5, 4, 5], "Bùi Ngọc Anh", "Chủ sân thân thiện, đặt sân nhanh.", {}],
  [[4, 3, 4], "Hồ Đức Thịnh", "Một vài sân hơi trơn, nhờ chủ sân lau thường xuyên hơn.", {}],
  [[5, 5, 4], "Ngô Bảo Châu", "Sân tiêu chuẩn thi đấu.", {}],
  [[4, 4, 3], "Đặng Khánh Linh", "Ổn.", {}],
];

const round1 = (x) => Math.round(x * 10) / 10;

const { initializeApp } = await import("firebase-admin/app");
const { getFirestore, Timestamp } = await import("firebase-admin/firestore");
initializeApp({ projectId });
const db = getFirestore();

const venueRef = db.collection("venues").doc(VENUE_ID);
if (!(await venueRef.get()).exists) {
  console.error(`Chưa có venues/${VENUE_ID} trên Emulator (project ${projectId}). Chạy seed.mjs trước.`);
  process.exit(1);
}

const newest = Date.UTC(2026, 9, 30, 3, 0, 0); // 30/10/2026 10:00 giờ Việt Nam
const DAY = 24 * 60 * 60 * 1000;
const sums = { court: 0, cleanliness: 0, service: 0, overall10: 0 };
let visible = 0;
const batch = db.batch();

DATA.forEach(([[court, cleanliness, service], name, comment, opts], i) => {
  const id = `${VENUE_ID}-review-${String(i + 1).padStart(2, "0")}`;
  const at = Timestamp.fromMillis(newest - i * DAY);
  const overall = round1((court + cleanliness + service) / 3);
  const userId = opts.deleted ? null : `demo-player-${String(i + 1).padStart(2, "0")}`;
  const photoPaths = Array.from({ length: opts.photos ?? 0 }, (_, k) => `${userId}/${id}/photo-${k + 1}.jpg`);
  const status = opts.hidden ? "HIDDEN" : "VISIBLE";
  batch.set(db.collection("reviews").doc(id), {
    targetType: "VENUE",
    bookingId: id,
    venueId: VENUE_ID,
    userId,
    userSnapshot: { displayName: opts.deleted ? null : name, avatarPath: null },
    authorDeleted: Boolean(opts.deleted),
    ratings: { court, cleanliness, service },
    overall,
    starBucket: Math.round(overall),
    comment,
    photoPaths,
    hasPhotos: photoPaths.length > 0,
    ownerReply: opts.reply ? { text: OWNER_REPLY, repliedAt: Timestamp.fromMillis(newest - i * DAY + 3_600_000), editedAt: null } : null,
    hasOwnerReply: Boolean(opts.reply),
    status,
    completedAt: Timestamp.fromMillis(newest - i * DAY - DAY),
    editedAt: null,
    createdAt: at,
    updatedAt: at,
  });
  if (status === "VISIBLE") {
    visible++;
    sums.court += court;
    sums.cleanliness += cleanliness;
    sums.service += service;
    sums.overall10 += Math.round(overall * 10);
  }
});

const ratingAvg = round1(sums.overall10 / visible / 10);
batch.update(venueRef, { ratingAvg, ratingCount: visible, ratingSums: sums });
await batch.commit();
console.log(`Đã ghi ${DATA.length} đánh giá cho ${VENUE_ID} (${visible} VISIBLE), ratingAvg = ${ratingAvg}, ` +
  `ratingSums = ${JSON.stringify(sums)} trên Emulator ${process.env.FIRESTORE_EMULATOR_HOST} (project ${projectId}).`);
