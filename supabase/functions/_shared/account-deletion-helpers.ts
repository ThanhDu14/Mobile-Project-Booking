// Helper thật của B, C, D cho delete-account (spec 011 research R12, plan.md "Phối hợp với nhóm").
//
// Hiện CHƯA nhóm nào có helper, nên object rỗng:
// - bước CHỐT (hủy đơn, lượt vãng lai, lượt chờ, ẩn cơ sở, hủy buổi): `confirm` trả 500 nếu người dùng có dữ liệu
//   cần xử lý ở bước đó, người không có vẫn xóa được (an toàn theo FR-021);
// - bước DỌN DẸP (ẩn danh, thông báo, xóa hồ sơ chủ sân): ghi SKIPPED, chạy lại bằng
//   `scripts/admin/rerun-deletion-step.ts <bước>` khi helper đã có.
//
// Khi có helper, import vào đây, ví dụ:
//   import { cancelBookingInTx, computeBookingRefund, anonymizeBookings } from "./booking.ts";   // B
//   import { cancelRegistrationInTx, computeRefund } from "./dropin.ts";                         // C (080)
//   import { leaveWaitlistInTx } from "./waitlist.ts";                                            // C (081)
//   import { anonymizeReviews } from "./reviews.ts";                                              // C (070)
//   import { anonymizeCommunity } from "./community.ts";                                          // C (090, 091)
//   import { hideVenueForOwnerDeletionInTx, cancelSessionInTx } from "./venue.ts";                // D
//   import { deleteOwnerApplications } from "./owner.ts";                                         // D
//   import { sendNotification } from "./notifications.ts";                                       // D (100)
// Chữ ký phải khớp `DeletionHelpers` trong account-deletion.ts (giao dịch là `DeletionTx`).
import type { DeletionHelpers } from "./account-deletion.ts";

export const defaultHelpers: DeletionHelpers = {};
