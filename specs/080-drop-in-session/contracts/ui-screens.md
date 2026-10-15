# Contract: Màn hình và điểm nối với tính năng khác

Spec 080 cung cấp các màn hình trong nav graph lồng `nav_dropin.xml` và vài interface ở `feature/dropin/domain/` để nhóm khác gọi vào. Theo constitution nguyên tắc I, các nhóm không import `ui/` hay `data/` của nhau.

Wireframe: [ui/README.md](../ui/README.md).

## 1. Màn hình (Fragment)

| Fragment | Layout | Wireframe | Story | Safe Args | Vào từ |
|---|---|---|---|---|---|
| `DropInListFragment` | `fragment_dropin_list.xml` (+ `item_dropin_session.xml`) | `08_drop-in_buoc1` | US1 | `filter: DropInFilterArgs?` (nullable, Parcelable) | Màn hình chính, AI Assistant (spec 022) |
| `DropInFilterBottomSheet` | `bottom_sheet_dropin_filter.xml` | — | US1 | `current: DropInFilterArgs` | `DropInListFragment` |
| `DropInDetailFragment` | `fragment_dropin_detail.xml` | `08_drop-in_buoc2` | US1, US2 | `sessionId: String` | Danh sách, thông báo |
| `DropInConfirmFragment` | `fragment_dropin_confirm.xml` | `08_drop-in_buoc3` | US2 | `sessionId: String` | Chi tiết buổi (nút "Đăng ký") |
| `DropInTicketFragment` | `fragment_dropin_ticket.xml` | `08_drop-in_buoc4` | US2, US3, US4 | `sessionId: String` | Sau khi đăng ký thành công, "Lượt của tôi", "Xem vé của bạn" |
| `CancelDropInBottomSheet` | `bottom_sheet_dropin_cancel.xml` | — | US4 | `sessionId: String` | Màn hình vé |
| `MyDropInsFragment` | `fragment_dropin_mine.xml` (+ `item_dropin_registration.xml`) | `08_drop-in_buoc5` | US3, US5 | — | Tab "Lịch của tôi" / trang Tài khoản |

Luồng chính dài 4 bước (FR-025): `DropInList` → `DropInDetail` → `DropInConfirm` → `DropInTicket`. Màn hình xác nhận bị gỡ khỏi back stack sau khi đăng ký thành công (`popUpTo`), để bấm Back từ vé quay về chi tiết buổi.

Mỗi màn hình có đủ 4 trạng thái (loading, content, empty, error kèm nút "Thử lại") và hiện thông báo khi mất mạng (FR-024).

**Chuỗi và resource:** đặt trong `strings_dropin.xml` với tiền tố `dropin_`. Màu lấy từ theme (constitution V).

**Màn hình vé:**
- QR có cạnh ≥ 240dp, hiển thị trên nền trắng cả ở chế độ tối để máy quét đọc được.
- Mã vé dạng chữ có thể chọn để sao chép.
- Khi màn hình vé mở, độ sáng tăng lên mức tối đa (`WindowManager.LayoutParams.screenBrightness`) và trở lại như cũ khi rời màn hình.

**Nhãn:** dùng `Chip`/`Badge` của Material 3 cho các nhãn "Đã đủ người", "Buổi đã thay đổi", "Buổi đã bị hủy", "Cơ sở tạm ngừng hoạt động" và nhãn kết quả ở tab "Đã qua".

## 2. Trạng thái màn hình (ViewModel → `StateFlow<UiState>`)

| ViewModel | UiState chính | Sự kiện một lần (`Channel`) |
|---|---|---|
| `DropInListViewModel` | `Loading`, `Content(items, chips, canLoadMore)`, `Empty(hasFilter)`, `Error` | mở chi tiết |
| `DropInDetailViewModel` | `Content(session, myRegistration?, actions)` với `actions ∈ {REGISTER, VIEW_TICKET, FULL, CLOSED}` | yêu cầu đăng nhập (khách) |
| `DropInConfirmViewModel` | `Editing(partySize, maxParty, paymentMethod, total, warnings)`, `Submitting`, `Failed(reason, remaining?)` | điều hướng tới vé, snackbar mất mạng |
| `DropInTicketViewModel` | `Content(registration, session, qrBitmap, labels, canCancel)`, `Offline(cached)` | — |
| `CancelDropInViewModel` | `Quoting`, `Quote(refundAmount, reason)`, `Cancelling`, `Done` | đóng sheet |
| `MyDropInsViewModel` | `Content(upcoming, past, canLoadMorePast)`, `Empty` | mở vé / mở viết đánh giá |

`maxParty = min(4, capacity − registeredCount)` (AC 2.2). `requestId` được sinh khi tạo `DropInConfirmViewModel` và giữ qua `SavedStateHandle`, nên khi xoay màn hình hoặc thử lại vẫn dùng lại cùng mã.

## 3. Điểm nối cho nhóm khác

| Nhóm | Họ làm | Spec 080 cung cấp |
|---|---|---|
| A, spec 022 (AI Assistant) | Khi `type = DROP_IN`, mở danh sách vãng lai kèm bộ lọc | Action `nav_dropin/dropInListFragment` với `DropInFilterArgs(districtIds, date, timeSlot, level, maxPrice)` (mọi trường nullable). AI chỉ điền các trường này (A ↔ C) |
| A, spec 011 (hồ sơ) | Có `users.level` và `favoriteDistrictIds` | Đọc qua `UserProfileReader` ở `core/` (A cung cấp) để điền sẵn khu vực và cảnh báo trình độ |
| A, spec 010 (đăng nhập) | Màn hình đăng nhập | Khách bấm "Đăng ký" → điều hướng tới đăng nhập kèm `returnTo = sessionId` |
| B, spec 060 | Hiển thị lượt vãng lai trong "Lịch của tôi" (nếu B gộp tab) | `DropInRegistrationReader.observeUpcoming(uid): Flow<List<DropInRegistration>>` |
| B, spec 040/060 | Cảnh báo trùng giờ | Spec 080 **dùng** `UpcomingBookingsReader.upcoming(uid, from, to)` ở `feature/booking/domain/` (B cung cấp) |
| C, spec 070 | Viết đánh giá buổi | Mở rộng Safe Args của `WriteReviewFragment`: thêm `sessionId: String?` (thay cho `bookingId` khi đánh giá buổi), `mode`. `ReviewEligibility.ofDropIn(registration, session, now)` |
| C, spec 081 | Phần hàng chờ trong chi tiết buổi và "Lượt của tôi" | `DropInDetailFragment` để trống vùng `waitlist_container`; `MyDropInsViewModel` nhận thêm trạng thái `WAITLISTED` |
| D, spec 100 | Deep link từ thông báo `DROPIN_*` | `smashnow://dropin/session/{sessionId}`, `smashnow://dropin/ticket/{sessionId}` |
| D, spec 112 | Chủ sân xem đăng ký và check-in | Rules cho owner đọc `registrations` của buổi mình ([security-rules.md](security-rules.md)); collection group index `qrToken`, `ticketCode` |

Interface đặt trong `feature/dropin/domain/` và được Hilt bind ở `feature/dropin/data/di/DropInModule.kt`:

```kotlin
interface DropInRegistrationReader {
    fun observeUpcoming(uid: String): Flow<List<DropInRegistration>>
}
```
