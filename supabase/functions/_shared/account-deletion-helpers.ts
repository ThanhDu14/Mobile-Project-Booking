// Helper thật của B, C, D cho delete-account (spec 011 research R12, plan.md "Phối hợp với nhóm").
//
// Hiện CHƯA nhóm nào có helper, nên object rỗng:
// - bước CHỐT (hủy đơn, lượt vãng lai, lượt chờ, ẩn cơ sở, hủy buổi): `confirm` trả 500 nếu người dùng có dữ liệu
//   cần xử lý ở bước đó, người không có vẫn xóa được (an toàn theo FR-021);
// - bước DỌN DẸP (ẩn danh, thông báo, xóa hồ sơ chủ sân): ghi SKIPPED, chạy lại bằng
//   `scripts/admin/rerun-deletion-step.ts <bước>` khi helper đã có.
//
// Khi có helper, thêm import vào file này (không viết dòng import mẫu trong comment: Supabase CLI quét cả comment
// và báo thiếu file khi `supabase start`/deploy). Helper cần có, theo file dự kiến của từng nhóm:
// - B, booking.ts: cancelBookingInTx, computeBookingRefund, anonymizeBookings
// - C, dropin.ts (080): cancelRegistrationInTx, computeRefund; waitlist.ts (081): leaveWaitlistInTx;
//   reviews.ts (070): anonymizeReviews; community.ts (090, 091): anonymizeCommunity
// - D, venue.ts: hideVenueForOwnerDeletionInTx, cancelSessionInTx; owner.ts: deleteOwnerApplications;
//   notifications (100): sendNotification
// Chữ ký phải khớp `DeletionHelpers` trong account-deletion.ts (giao dịch là `DeletionTx`).
import type { DeletionHelpers } from "./account-deletion.ts";

export const defaultHelpers: DeletionHelpers = {};
