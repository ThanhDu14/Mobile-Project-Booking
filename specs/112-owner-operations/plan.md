# Kế hoạch triển khai: Owner Operations

**Nhánh**: `feature/112-owner-operations-plan` | **Ngày**: 2026-10-16 | **Đặc tả**: [spec.md](spec.md)

**Đầu vào**: [Đặc tả Feature 112](spec.md). Quy trình setup-plan theo nhánh đã trả về Feature 100; do đó tài liệu này được đặt theo `SPECIFY_FEATURE_DIRECTORY=specs/112-owner-operations` như chỉ định, không sử dụng đường dẫn setup sai.

## Tóm tắt

Feature 112 bổ sung khu vực vận hành cho owner đã được duyệt: hàng đợi booking chờ duyệt và quyết định an toàn, lịch ngày/tuần tổng hợp, quản lý buổi drop-in cùng đăng ký/check-in, voucher của venue và thống kê. App Android trình bày trạng thái theo MVVM; mọi quyền và cập nhật nghiệp vụ nhạy cảm được xác minh phía server, dùng Firestore/Rules và Supabase Edge Functions theo Constitution. Các thao tác nhiều tài liệu (booking/slot, drop-in/slot/registration) phải nguyên tử theo transaction Firestore. Chỉ dùng schema, trạng thái và quy tắc đã có trong spec và database README.

## Bối cảnh kỹ thuật

**Ngôn ngữ/Phiên bản**: Kotlin; Android `minSdk 29`, `compileSdk/targetSdk 37` theo Constitution.

**Phụ thuộc chính**: AndroidX Fragment, Navigation, ViewBinding; Firebase Auth/Firestore; Supabase Edge Functions; Kotlin Coroutines/Flow, ViewModel và Hilt theo kiến trúc. Các package tính năng/DI và Edge Functions chưa hiện diện trong source tree hiện tại; phụ thuộc thực tế cần được xác nhận khi tích hợp.

**Lưu trữ**: Firestore dùng `venues/{venueId}`, `courts`, `priceRules`, `slots`, `bookings`, `dropInSessions/{id}/registrations`, `venues/{venueId}/vouchers/{code}` và `dailyStats` hiện được nêu trong tài liệu chung. Supabase chỉ cung cấp Edge Functions; không lưu nghiệp vụ trong Supabase DB.

**Kiểm thử**: Unit test miền/ViewModel/repository; Firestore Emulator cho Rules và giao dịch; kiểm thử Edge Function với Firebase Emulator/Supabase local theo mẫu 080/081; kiểm thử UI cho luồng chính. Bộ phụ thuộc và hạ tầng test cụ thể chưa được khai báo trong ứng dụng hiện tại. Không chạy kiểm thử trong bước Plan.

**Nền tảng đích**: Ứng dụng Android, một Activity, Fragment XML/ViewBinding và Navigation Component.

**Loại dự án**: App Android với backend nghiệp vụ serverless Firebase/Supabase.

**Mục tiêu hiệu năng**: Theo SC-008: danh sách và lịch dưới 3 giây trên 4G; thao tác ghi phản hồi dưới 1 giây và hoàn tất dưới 3 giây khi mạng bình thường. Không đưa ra SLA mới cho thống kê.

**Ràng buộc**: Chỉ owner `role=OWNER`, `ownerStatus=APPROVED` và sở hữu venue được thao tác. Múi giờ nghiệp vụ `Asia/Ho_Chi_Minh`; booking/slot 30 phút; drop-in không qua nửa đêm; trạng thái tiền, QR, bộ đếm và thống kê là dữ liệu được bảo vệ. Tuyệt đối không suy diễn schema/API/enum mới. Owner onboarding (110), thiết lập venue/court/giá/khóa giờ (111), admin (120) ngoài phạm vi.

**Quy mô/Phạm vi**: Các màn hình owner trong UI tham chiếu Feature 112; thực hiện User Story 1–5 và thống kê. Không làm các màn hình và nghiệp vụ loại trừ ở trên.

## Đối chiếu Constitution

| Điều kiện | Trạng thái | Cách đáp ứng |
|---|---|---|
| MVVM, package theo feature, StateFlow, Coroutines/Flow, Hilt, repository interface | Đáp ứng theo thiết kế; source hiện còn skeleton | Fragment → ViewModel → use case nếu cần → repository → Firebase/Edge Function; không gọi Firebase từ UI/ViewModel. |
| Kiểm tra bảo mật phía server | Bắt buộc | Xác minh token, owner đã duyệt và `venues.ownerId` ở mọi thao tác; Rules chặn client sửa trường 🔒. Giao diện chỉ hỗ trợ UX. |
| Toàn vẹn booking/slot | Bắt buộc | Mọi quyết định booking phải kiểm tra trạng thái và slot hiện hành; từ chối giải phóng đúng slot trong cùng giao dịch. Tạo/sửa/hủy drop-in đồng bộ slot `DROP_IN` atomically. |
| Kiểm thử tự động và Emulator | Đáp ứng theo chiến lược | Kiểm thử cho phép/từ chối, cạnh tranh transaction, idempotency/QR dùng lại, thống kê đối chiếu; Rules qua Firebase Emulator. |
| XML, vòng đời, loading/empty/error, tiếng Việt và accessibility | Đáp ứng theo thiết kế | StateFlow UiState và sự kiện một lần; collect theo `repeatOnLifecycle`; trạng thái tải/lỗi/thử lại theo FR-020. |
| Riêng tư và schema tối thiểu | Đáp ứng | Truy vấn giới hạn venue; không tiết lộ booking/venue ngoài quyền; dùng các field và enum hiện có. |

**Cổng trước thiết kế**: Không thấy vi phạm Constitution. Hai hợp đồng kỹ thuật chưa được nguồn xác định đầy đủ: nguồn đáng tin cậy cho `ownerStatus=APPROVED` trong Rules/Edge Function và schema/chủ sở hữu cập nhật `dailyStats`. Không tự quyết định thay nhóm phụ trách.

**Cổng sau thiết kế**: Đáp ứng với điều kiện các điểm mở được chốt trước implementation. Tài liệu thiết kế không thêm collection, field, trạng thái hoặc endpoint.

## Quyết định kiến trúc và luồng xử lý

1. **Booking và lịch**: repository đọc `bookings` theo venue và khoảng thời gian, cùng slot liên quan để phát lịch ngày/tuần theo `Asia/Ho_Chi_Minh`. Chỉ `PENDING` hiện trong hàng chờ duyệt, sắp `approvalDeadline`; lịch phân biệt trạng thái theo spec. `HOLD` chỉ cảnh báo tạm nếu còn hạn, không hiển thị là booking xác nhận. Duyệt/từ chối kiểm tra lại owner, `PENDING`, deadline và slot trong giao dịch phía server. Duyệt chỉ `PENDING → CONFIRMED`; từ chối `PENDING → REJECTED` và nhả đúng slot. Hủy `CONFIRMED` chưa bắt đầu/chưa check-in theo FR-004a, có lý do, hoàn tiền và thông báo theo hợp đồng hiện có.
2. **Check-in booking**: dùng Edge Function đã được database README đặt tên `checkin-booking`; QR/mã booking được xác minh server, trong cửa sổ từ 30 phút trước đến giờ kết thúc. Chỉ ghi check-in và thu tiền khi owner xác nhận; `COMPLETED`/`NO_SHOW` thuộc `booking-finalize`.
3. **Drop-in**: tạo buổi theo schema hiện hành, xác minh venue/court/giờ vận hành/`priceRules` và mọi slot 30 phút trong một transaction. Sửa/hủy trước giờ bắt đầu cập nhật slot liên quan nguyên tử; khi tăng sức chứa phải gọi logic đẩy hàng chờ theo Feature 081. Đăng ký/hàng chờ chỉ đọc; owner không sửa trạng thái/bộ đếm. Check-in ghi `CHECKED_IN` một lần ở đăng ký. Tên/hợp đồng Edge Function dành riêng cho thao tác owner/drop-in check-in chưa được nguồn thống nhất nên để mở, không đặt tên API mới trong Plan.
4. **Voucher**: chỉ thao tác voucher venue trong `venues/{venueId}/vouchers/{code}`. Quyền tạo/sửa phải qua server kiểm tra owner và mã duy nhất toàn hệ thống; bảo vệ `usedCount`/`redemptions`. Kiểu, thời hạn, giới hạn và cách tính áp dụng kế thừa Feature 050. `systemVouchers` thuộc Feature 120.
5. **Thống kê**: doanh thu nhóm theo `paymentStatus`: `DEPOSITED` dùng `depositAmount`, `PAID` dùng `total`, `REFUNDED` đếm riêng và cộng `refundAmount` có dữ liệu; loại đơn bị từ chối/hủy/hết hạn và chưa trả. Không suy ra doanh thu từ `priceRules`. Tỷ lệ lấp đầy = thời lượng slot của booking `CONFIRMED`/`COMPLETED`/`NO_SHOW` chia thời lượng có thể đặt trong phạm vi, loại `BLOCKED`, ngoài giờ mở cửa và slot trùng. Cao điểm cộng thời lượng booking hợp lệ theo các bucket 60 phút địa phương. Nếu dùng `dailyStats`, chỉ đọc dữ liệu đúng khoảng và hiển thị dấu hiệu cũ; chưa thể chốt phép đọc/ghi cho tới khi schema được thống nhất.

## Cấu trúc dự án

### Tài liệu của tính năng

```text
specs/112-owner-operations/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── owner-operations-boundaries.md
```

### Mã nguồn (repo hiện tại)

```text
app/src/main/java/com/example/booking_app/
└── feature/owner/
    ├── ui/       # Fragment/ViewModel lịch, duyệt đơn, check-in, drop-in, voucher, thống kê
    ├── domain/   # Ui/domain models, use cases, repository interfaces
    └── data/     # Firestore mapping, repository impl, Edge Function clients

supabase/functions/
├── checkin-booking/       # đã được database README định danh; phạm vi logic booking liên quan
└── [các thao tác owner 112 cần hợp đồng trước khi đặt tên/triển khai]
```

Source tree hiện chưa có feature owner hoặc function nghiệp vụ (chỉ có app skeleton và `supabase/config.toml`). Không tạo tên function chưa được thống nhất trong tài liệu này.

**Structure Decision**: Theo Constitution, feature Android nằm ở `feature/owner`; phần ghi nhạy cảm nằm trong Edge Function dùng Firestore transaction và shared helper của nhóm booking/drop-in. Hợp đồng function cụ thể phải tái sử dụng function đã được nguồn gọi tên hoặc được các nhóm liên quan chốt trước implementation.

## Dependency và phối hợp

| Phụ thuộc | Dùng cho | Điều kiện/phối hợp |
|---|---|---|
| 110 + 120 | trạng thái owner được duyệt, thu hồi quyền, venue bị khóa | Chỉ dựa trên trạng thái/claim đáng tin cậy; nguồn kiểm tra đồng bộ cần chốt. Không triển khai luồng admin. |
| 111 | venue, court, giờ hoạt động, `priceRules`, slot 30 phút, `ACTIVE`/`LOCKED` | Dùng như nguồn dữ liệu; không quản lý các thực thể này trong Feature 112. |
| 040/041/050/060 | booking, approval deadline, thanh toán, voucher redemption, check-in và QR | Phối hợp với phụ trách B; `checkin-booking` đã có trong database README. Không thay quy tắc giá/hoàn/voucher. |
| 080/081 | schema drop-in/registration, transaction đăng ký, queue promotion | Tái sử dụng `_shared/dropin.ts`/helper hiện có theo tài liệu 080/081; tương thích tạo/sửa/hủy buổi. |
| 100 | notification types `BOOKING_NEW`, `BOOKING_CANCELLED`, `DROPIN_CHANGED`, `DROPIN_WAITLIST_CLOSED` | Gọi cơ chế gửi notification hiện có; lỗi thông báo không được làm sai kết quả nghiệp vụ đã commit. |
| Database README | schema, Rules, index, Edge Functions | `dailyStats` chưa có schema; phải thống nhất với D/database owner trước implementation. |

## Điểm cần làm rõ trước implementation

- Database README có hai mô tả chưa đồng nhất về voucher owner: mục 4.6 nói owner tạo voucher ở Feature 111, bảng Rules ghi qua Edge Function, trong khi 112 FR-013 giao thao tác cho Feature 112. Chốt chủ sở hữu nghiệp vụ và ranh giới Feature 111/112; không thay spec âm thầm.
- Xác định trường/truy vấn/cửa sổ thời gian và độ mới của `dailyStats`, cách tính `available duration` khi áp dụng `priceRules` thiếu khoảng hoặc court ngừng hoạt động, và ai cập nhật aggregate. README chưa có schema trường.
- Chốt API/transaction boundary cho approve/reject/cancel booking và thao tác create/update/cancel drop-in, QR check-in drop-in, voucher CRUD. Không có contract nguồn đầy đủ cho các thao tác này; tên function/request/response không thể suy ra an toàn.
- Constitution nói `ownerStatus == approved`, database README dùng `appRole == OWNER`, spec dùng `role=OWNER` và `ownerStatus=APPROVED`. Cần chuẩn hóa claim/trường và nơi kiểm tra ở Rules/Edge Functions.
- Xác nhận cách chọn các booking nhiều court/slot vào báo cáo theo ngày, và quy tắc phân bổ thời lượng khi slot 30 phút băng qua ranh giới bucket 60 phút; áp dụng cùng định nghĩa cho toàn kỳ.

## Theo dõi độ phức tạp

Không có vi phạm Constitution cần ngoại lệ. Không thêm project, collection, field, enum hoặc API mới trong kế hoạch.
