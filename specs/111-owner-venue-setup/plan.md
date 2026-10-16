# Kế hoạch triển khai: Thiết lập cơ sở của chủ sân

**Nhánh**: `111-owner-venue-setup` | **Ngày**: 2026-10-16 | **Đặc tả**: [spec.md](spec.md)

**Đầu vào**: `specs/111-owner-venue-setup/spec.md`

## Tóm tắt

Triển khai package tính năng chủ sân để quản lý cơ sở và sân con thuộc quyền sở hữu, cấu hình bảng giá theo thứ, đồng thời khóa/mở khóa các slot sân 30 phút. Giữ nguyên schema Firestore hiện có (`venues`, các subcollection `courts`, `priceRules` và collection cấp cao nhất `slots`), không thêm trường hay giá trị enum. Máy chủ phải xác thực danh tính và quyền sở hữu của chủ sân. Tuân theo quy ước Android MVVM, repository, Hilt, Flow và XML/ViewBinding hiện có; dùng Firestore Rules để giới hạn CRUD theo quyền sở hữu và dùng giao dịch phía máy chủ cho thay đổi slot khi thao tác ghi trực tiếp từ client không bảo đảm các bất biến đặt sân. Tài liệu hiện chưa định nghĩa giao diện server cho thao tác khóa slot; đây là điểm cần làm rõ.

## Bối cảnh kỹ thuật

**Ngôn ngữ/Phiên bản**: Kotlin cho ứng dụng Android; chỉ dùng TypeScript/Deno nếu cần Edge Function Supabase được phê duyệt theo thiết kế server hiện có.

**Phụ thuộc chính**: AndroidX ViewModel, Coroutines/Flow, Hilt, Firebase Auth/Firestore, Navigation Component, XML/ViewBinding, Material 3; JUnit4, MockK, kotlinx-coroutines-test, Turbine; Firebase Emulator Suite cho kiểm thử Rules và giao dịch. Không thêm thư viện nếu chưa có nhu cầu cụ thể.

**Lưu trữ**: Firestore lưu dữ liệu nghiệp vụ. Supabase Storage tiếp tục lưu ảnh cơ sở theo cơ chế hiện có; tính năng dùng lại các đường dẫn hiện có, không thêm bucket hay schema.

**Kiểm thử**: Kiểm thử đơn vị cho validation, chuyển trạng thái, use case và repository bằng fake; kiểm thử phân quyền owner và cạnh tranh khóa/mở khóa slot trên Firebase Emulator. Không kiểm thử trên dự án Firebase/Supabase thật.

**Nền tảng đích**: Android, `minSdk 29`; giao diện điện thoại và `w600dp`; hỗ trợ giao diện sáng và tối.

**Loại dự án**: Ứng dụng Android và backend Supabase Edge Functions hiện có.

**Mục tiêu hiệu năng**: Danh sách cơ sở và lịch tải dưới 3 giây trên 4G; thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường, theo Feature 111.

**Ràng buộc**: Slot 30 phút; khoảng thời gian không qua nửa đêm; giờ hoạt động giống nhau mọi ngày trong tuần. Giá VND lưu bằng `Long`, theo giờ và chia hết cho 2.000; giá mỗi slot là `pricePerHour / 2`. Không sửa booking khi ẩn cơ sở hoặc ngừng hoạt động sân. Không thêm schema nghiệp vụ, endpoint, enum hoặc luồng ngoài phạm vi.

**Quy mô/Phạm vi**: Màn hình và luồng domain/data quản lý cơ sở, sân con, bảng giá và slot thuộc chủ sân hiện tại. Tài liệu chưa quy định số lượng cơ sở/sân tối đa.

## Đối chiếu Constitution

| Nguyên tắc | Kết quả | Hệ quả thiết kế |
|---|---|---|
| I. MVVM, package theo tính năng, gọi tuần tự từng tầng | ĐẠT | Triển khai trong `feature/owner/{ui,data,domain}`; Fragment → ViewModel → use case khi cần → repository → Firebase/server source. |
| II. Phân quyền phía máy chủ | ĐẠT CÓ ĐIỀU KIỆN | Máy chủ xác minh `appRole == OWNER`, trạng thái owner đã duyệt và quan hệ `venues.ownerId` đáng tin cậy. Không coi việc ẩn giao diện hoặc dữ liệu cache là phân quyền. Rules và/hoặc Edge Function phải bảo vệ từng đường ghi. |
| III. Toàn vẹn đặt sân | ĐẠT CÓ ĐIỀU KIỆN | Thay đổi slot phải nguyên tử và nhất quán với thao tác booking. Cạnh tranh giao dịch chỉ có một bên thắng; không dùng luồng client đọc rồi ghi. Giữ nguyên slot đã đặt/giữ khi ẩn hoặc ngừng hoạt động. |
| IV. Kiểm thử có trọng tâm | ĐẠT | Bổ sung kiểm thử ViewModel/use case/repository và Emulator cho Rules cũng như cạnh tranh giao dịch slot. |
| V. XML và accessibility | ĐẠT | XML + ViewBinding + Material 3; chuỗi/style qua resource; có loading/content/empty/error, thử lại, vùng chạm 48dp; hỗ trợ điện thoại, `w600dp`, sáng/tối. |
| VI. Quyền riêng tư và tối thiểu hóa dữ liệu | ĐẠT | Dùng trường cơ sở và đường dẫn ảnh sẵn có; không thu thập thêm dữ liệu cá nhân. |
| VII. YAGNI và thay đổi dễ giải thích | ĐẠT | Không mở rộng schema hoặc dependency. Mọi bổ sung server function/giao diện phải chờ làm rõ vấn đề quyền sở hữu và hợp đồng đã nêu. |

**Kết luận cổng kiểm tra**: Chưa phát hiện vi phạm Constitution cần chấp nhận. Trước triển khai cần thống nhất cách server đáng tin cậy xác minh `ownerStatus` và cách gọi thao tác slot nguyên tử; xem [research.md](research.md).

## Cấu trúc dự án

### Tài liệu của tính năng

```text
specs/111-owner-venue-setup/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── firestore-boundary.md
```

### Mã nguồn (từ thư mục gốc repo)

```text
app/src/main/java/com/example/booking_app/feature/owner/
├── ui/          # Màn hình quản lý cơ sở, sân con, bảng giá và slot
├── domain/      # Validation nghiệp vụ và use case khi cần
└── data/        # Cài đặt repository và nguồn dữ liệu Firestore/server

app/src/main/res/
├── layout/      # XML layout của tính năng
├── navigation/  # Destination/action trong navigation graph hiện có
└── values*/     # Chuỗi tiếng Việt, theme và dimension

app/src/test/java/com/example/booking_app/feature/owner/
app/src/androidTest/java/com/example/booking_app/feature/owner/
firestore-tests/ # Kiểm thử Emulator cho Rules và giao dịch (quy ước hiện có)
supabase/functions/ # Chỉ dùng nếu cần giao dịch slot phía server được phê duyệt
```

**Quyết định cấu trúc**: Theo cấu trúc một ứng dụng Android và quy ước package tính năng trong Constitution. Mã hiện tại mới có các Fragment khởi tạo; kế hoạch dùng package `com.example.booking_app` hiện có và không tạo Gradle module mới. Backend tiếp tục dùng cấu trúc Supabase function hiện có.

## Thiết kế tính năng

1. **Quản lý cơ sở**: Liệt kê cơ sở của owner đã xác thực; tạo/sửa các trường được phép trong bản ghi `venues`; ẩn bằng `status = HIDDEN`; chỉnh `acceptsRecurring`. Không cho owner ghi `ownerId`, trạng thái `LOCKED`, trường 🔒 hoặc timestamp hệ thống trái hợp đồng chung. Luồng duyệt hồ sơ tiếp tục tạo cơ sở đầu tiên; tính năng này chỉ tạo thêm cơ sở.
2. **Quản lý sân con**: Thêm/sửa/sắp xếp bằng các trường hiện có `name`, `surfaceType`, `isActive`, `sortOrder`. Ngừng hoạt động không phải xóa; không xóa lịch hoặc lịch sử booking. Chặn booking mới vào sân không hoạt động nhưng giữ nguyên booking đã xác nhận.
3. **Bảng giá**: Tạo/sửa/xóa `priceRules` lồng trong venue bằng các trường hiện có. Kiểm tra thứ, mốc HHmm theo bước 30 phút, giờ kết thúc sau giờ bắt đầu, không qua nửa đêm, giá VND/giờ dương chia hết cho 2.000, label hợp lệ và không chồng giờ ở các thứ chung. Xóa rule không đổi giá booking cũ; slot tương lai không có rule phù hợp thì không đặt được. Không tự tạo liên kết rule-booking.
4. **Khóa slot**: Chọn sân/ngày/giờ và lý do `MAINTENANCE`, `EVENT` hoặc `WALK_IN`; ánh xạ khoảng giờ thành toàn bộ ID slot xác định theo quy ước, mỗi slot 30 phút. Ghi nguyên tử `status = BLOCKED` và `blockReason` cho từng slot. Từ chối toàn bộ nếu có slot `HOLD`, `BOOKED` hoặc `BLOCKED`; không khóa một phần. Chỉ mở khóa block thuộc thao tác quản lý này khi trạng thái hiện tại vẫn đúng như mong đợi; không xóa/ghi đè `HOLD` hoặc `BOOKED`. `DROP_IN` thuộc spec 080/112.
5. **Tích hợp khả năng đặt**: Lưới booking hiện có (040) đọc trạng thái venue/court, `priceRules` và `slots`; `HIDDEN`, sân không hoạt động, thiếu rule phù hợp và `BLOCKED` đều chặn booking mới theo spec tương ứng. Không sửa booking đã tạo. Giá tính theo từng slot 30 phút bằng `pricePerHour / 2`.
6. **Lỗi và khôi phục**: Ánh xạ validation trường, quyền, xung đột/trạng thái cũ, lỗi mạng/kết quả chưa rõ và lỗi server vào trạng thái giao diện. Nếu không rõ thao tác ghi đã thành công chưa, tải lại dữ liệu có thẩm quyền trước khi thử lại; cache chỉ để hiển thị, không dùng để cấp quyền. Giữ nội dung biểu mẫu khi lỗi.

## Theo dõi độ phức tạp

Không đề xuất vi phạm Constitution hoặc dependency mới. Thay đổi slot nguyên tử phía server là yêu cầu từ nguyên tắc bảo mật và toàn vẹn booking hiện có. Việc dùng lại Edge Function nào hay bổ sung function chuyên biệt phải chờ làm rõ hợp đồng trong tài liệu research, không suy đoán trước trong kế hoạch.
