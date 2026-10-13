# Specification Quality Checklist: Thanh toán và Khuyến mãi (Giả lập)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-06
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Đã làm rõ và giải quyết các điểm clarification (Phiên 2026-10-06 & Thống nhất liên thành viên 2026-10-12):
  1. Trạng thái sau thanh toán chuyển sang `PENDING` (chờ chủ sân duyệt trong tối đa 60 phút; Server Cron Job của D quét timeout tự động chuyển `REJECTED`, hoàn trả 100% tiền cọc/ví vào ví giả lập theo thỏa thuận 2.A).
  2. Tỷ lệ cọc khi chọn `DEPOSIT` là cố định 30% trên tổng giá trị đơn hàng, làm tròn đến hàng nghìn đồng chẵn gần nhất.
  3. Số dư ban đầu của ví giả lập tài khoản mới là 0đ; người dùng có thể nạp tiền ảo bất kỳ lúc nào ngay tại màn hình thanh toán hoặc trang cá nhân.
  4. Cấu trúc voucher phân tách 2 nguồn theo thỏa thuận 3.B: `venues/{venueId}/vouchers` (voucher cơ sở do chủ sân tạo) và `systemVouchers` (voucher toàn sàn do admin tạo).
  5. Cung cấp nội dung FAQ tĩnh chuẩn hóa về cọc/ví/voucher cho chatbot của Thành viên A theo thỏa thuận 6.A.
- Toàn bộ 16/16 tiêu chí chất lượng đặc tả đạt yêu cầu; spec sẵn sàng chuyển sang bước lập kế hoạch kỹ thuật (`/speckit-plan`).
