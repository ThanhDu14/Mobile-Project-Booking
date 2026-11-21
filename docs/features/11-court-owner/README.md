# Feature 11 — Quản lý sân dành cho OWNER

Các spec: [110 đăng ký/hồ sơ bổ sung](../../../specs/110-owner-onboarding/spec.md), [111 cơ sở/sân/giá/khóa giờ](../../../specs/111-owner-venue-setup/spec.md), [112 vận hành](../../../specs/112-owner-operations/spec.md). Phụ trách D. Code Android MVVM/XML/Hilt; Firestore là dữ liệu nghiệp vụ, Supabase Functions/Storage là boundary server/upload.

| Phạm vi | Triển khai |
|---|---|
| Onboarding và thêm nhiều cơ sở | Form/upload/review/status/admin approve/reject/resubmit; purpose OWNER_ONBOARDING hoặc VENUE_ADDITION; missing purpose vẫn onboarding |
| Cơ sở/sân con | Server-confirmed owned list, sửa/ẩn, recurring toggle; owner-manage-court transaction tạo/sửa/tắt/đổi thứ tự và tính courtCount từ số sân hoạt động, cho phép 0; không hard-delete lịch sử |
| Bảng giá | Validation/coverage/overlap; server transaction cho rule và minPricePerHour; Rules chặn client bypass |
| Khóa/mở giờ | manage-slot-blocks nguyên tử; giữ HOLD/BOOKED/DROP_IN và ownership |
| Booking/check-in | Queue/deadline/detail, approve/reject/cancel/refund, QR/mã chữ và xác nhận thu tiền; server chống replay |
| Lịch và drop-in | Ngày/tuần, nhiều court, roster/check-in; capacity/waitlist helper 081 và dropin-close |
| Voucher | CRUD/toggle venue, uniqueness toàn hệ thống, counters do server quản lý |
| Thống kê | Projection bookings/slots + availability cùng snapshot; revenue/refund, occupancy/peak; custom range ≤30 ngày |

Mở thêm cơ sở cần admin duyệt trước khi công khai, tạo đúng venues/{applicationId}. Hồ sơ bổ sung pending/rejected/resubmit không đổi OWNER/APPROVED, claims hoặc venue cũ. Không đặt giới hạn số cơ sở/hồ sơ. Owner-docs private; signed URL kiểm tra quyền hiện tại. Chuyển OWNER/PLAYER, session và chat/community đã merge được giữ.

[Mapping production 112](../../../specs/112-owner-operations/implementation-mapping.md) và [báo cáo kiểm chứng hiện tại](../../validation/feature11-2026-11-21/README.md) chỉ rõ phần sửa, ba job CI, máy thật, backend local và giới hạn còn lại. Các tên Placeholder/ContractPending không phản ánh production binding; không dùng checklist đã tick thay bằng chứng.

Không khẳng định đã đạt SLA 4G hoặc usability khi chưa đo; không ghi nhận merge/deploy chỉ từ build local.
