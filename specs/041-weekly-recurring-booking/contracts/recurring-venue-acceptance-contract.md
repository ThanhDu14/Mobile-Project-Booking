# Hợp đồng Tích hợp: Giới hạn Đặt Cố Định Theo Tuần (`acceptsRecurring`)

**Ngày ban hành**: 2026-10-30  
**Bên phát hành**: Feature 111 — Owner Venue Setup  
**Bên tiếp nhận**: Feature 041 — Weekly Recurring Booking  
**Tham chiếu**: [Feature 111 tasks.md T034](../../111-owner-venue-setup/tasks.md), [Feature 041 spec.md FR-001](../spec.md)

---

## 1. Bối cảnh và Ranh giới Nghiệp vụ

- **Feature 111** chịu trách nhiệm quản lý cấu hình cơ sở của chủ sân, trong đó có thuộc tính `venues/{venueId}.acceptsRecurring: Boolean` (mặc định là `true`).
- **Feature 041** chịu trách nhiệm về luồng cấu hình, kiểm tra khả dụng, giữ chỗ chu kỳ All-or-Nothing và thanh toán đặt cố định theo tuần.
- Nhóm phát triển Feature 041 **BẮT BUỘC** tuân thủ các điều khoản trong hợp đồng này khi triển khai mã nguồn.

---

## 2. Các Bất Biến Nghiệp Vụ Bắt Buộc (Invariants)

### Bất biến 1: Chặn khởi tạo chuỗi cố định mới khi `acceptsRecurring == false`
1. **Ở Tầng Giao Diện (UI Layer - Feature 030 / 040 / 041)**:
   - Khi tải thông tin cơ sở `venues/{venueId}`, nếu trường `acceptsRecurring` có giá trị `false`, hệ thống **PHẢI**:
     - Ẩn nút hoặc tab chuyển sang luồng "Đặt cố định theo tuần" (hoặc hiển thị ở trạng thái Disabled kèm tooltip: *"Cơ sở này hiện không nhận đặt sân cố định theo tuần"*).
     - Chặn người dùng điều hướng vào `bookingRecurringConfigFragment`. Nếu cố tình deep-link hoặc điều hướng vào với `venueId` không nhận đặt cố định, ViewModel phải phát ra hiệu ứng thông báo lỗi và quay trở về màn hình trước.

2. **Ở Tầng Giao Dịch Giữ Chỗ (Domain & Transaction Layer - Feature 041)**:
   - Trong UseCase `HoldRecurringBookingUseCase` và hàm thực thi giao dịch `RecurringBookingRepository.holdRecurringBookingTransaction`, hệ thống **BẮT BUỘC** đọc document `venues/{venueId}` trong transaction:
     ```kotlin
     val venueDoc = transaction.get(venueRef)
     val acceptsRecurring = venueDoc.getBoolean("acceptsRecurring") ?: true
     if (!acceptsRecurring) {
         throw RecurringNotAcceptedException("Cơ sở hiện không chấp nhận đặt sân cố định theo tuần")
     }
     ```
   - Thao tác giữ chỗ toàn chu kỳ phải bị hủy bỏ ngay lập tức nếu cơ sở không bật tính năng này.

### Bất biến 2: Bảo toàn tuyệt đối các chuỗi cố định hiện có (Existing Chains Intact)
1. Việc chủ sân chuyển `acceptsRecurring` từ `true` sang `false` **CHỈ** áp dụng cho các yêu cầu đặt cố định mới phát sinh sau thời điểm thay đổi.
2. Tất cả các nhóm đặt cố định đã được xác nhận trước đó (`status = CONFIRMED` trong `recurringGroups` hoặc `bookings`) và toàn bộ các slot đã được giữ/đặt (`status = BOOKED`) trong tương lai của chuỗi đó **PHẢI ĐƯỢC GIỮ NGUYÊN VẸN 100%**.
3. Hệ thống không được phép tự ý hủy, sửa đổi hoặc xóa các đơn đặt đã tạo khi chủ sân tắt nhận đặt cố định.

---

## 3. Hướng Dẫn Kỹ Thuật Cho Nhóm Feature 041

### 3.1. Cập nhật Domain Interface trong Feature 041
Trong interface `RecurringBookingRepository`:
```kotlin
package com.example.booking_app.feature.booking.domain.repository

interface RecurringBookingRepository {
    /**
     * Kiểm tra trạng thái chấp nhận đặt cố định của cơ sở từ venues/{venueId}
     */
    suspend fun isRecurringAccepted(venueId: String): Result<Boolean>

    // ... các hàm hiện có khác
}
```

### 3.2. Cập nhật UseCase `HoldRecurringBookingUseCase`
Trước khi gọi giao dịch giữ chỗ nhiều ngày:
```kotlin
class HoldRecurringBookingUseCase @Inject constructor(
    private val repository: RecurringBookingRepository
) {
    suspend operator fun invoke(...): Result<RecurringToPaymentArgs> {
        val isAccepted = repository.isRecurringAccepted(venueId).getOrDefault(false)
        if (!isAccepted) {
            return Result.failure(IllegalStateException("Cơ sở không nhận đặt cố định theo tuần"))
        }
        // Tiếp tục tiến trình All-or-Nothing giữ chỗ...
    }
}
```

---

## 4. Test Matrix Xác Minh Tích Hợp Cho Feature 041

Khi nhóm Feature 041 triển khai mã nguồn, bộ kiểm thử đơn vị và Emulator phải bao gồm:

| Mã Test | Kịch bản kiểm thử | Kết quả mong đợi |
|---|---|---|
| **041-INT-01** | Người dùng xem cơ sở có `acceptsRecurring = false` | Nút/Tab đặt cố định bị ẩn hoặc disabled. |
| **041-INT-02** | Cố tình gọi `HoldRecurringBookingUseCase` khi `acceptsRecurring = false` | Trả về `Result.failure` với lỗi `Cơ sở không nhận đặt cố định`. |
| **041-INT-03** | Đua giao dịch: Chủ sân tắt `acceptsRecurring` đúng lúc người chơi bấm xác nhận giữ chỗ | Transaction phát hiện `acceptsRecurring == false` và rollback toàn bộ, không tạo slot `HELD`. |
| **041-INT-04** | Cơ sở có chuỗi đang chạy (`recurringGroupId`), chủ sân chuyển `acceptsRecurring = false` | Chuỗi cũ và toàn bộ các buổi đặt trong tương lai giữ nguyên trạng thái `BOOKED`, không bị hủy. |
