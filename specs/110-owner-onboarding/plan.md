# Kế hoạch triển khai: Đăng ký làm chủ sân

**Nhánh**: `feature/110-owner-onboarding-plan` | **Ngày**: 2026-10-15 | **Đặc tả**: [spec.md](spec.md)

**Đầu vào**: Đặc tả tính năng tại `specs/110-owner-onboarding/spec.md`

## Tóm tắt

Lập kế hoạch cho luồng người chơi đã đăng nhập tạo hoặc nộp lại hồ sơ chủ sân, tải ảnh cơ sở và giấy tờ riêng tư, xem lại rồi gửi hồ sơ, và theo dõi trạng thái cùng lý do từ chối do máy chủ trả về. Máy chủ là nguồn dữ liệu có thẩm quyền cho `users.role` và `users.ownerStatus`. Quyết định duyệt thuộc Feature 120; quản lý cơ sở và vận hành thuộc Feature 111, 112. Mô hình dữ liệu và chính sách lưu trữ tuân theo tài liệu CSDL chung, không bổ sung trường hay collection.

## Bối cảnh kỹ thuật

**Ngôn ngữ/Phiên bản**: Kotlin; dự án Android dùng `minSdk 29`, `compileSdk/targetSdk 37` theo hiến chương.

**Phụ thuộc chính**: Dự án hiện có AndroidX Fragment, Navigation, ViewBinding, Firebase Auth và Firestore. Kiến trúc yêu cầu ViewModel, Coroutines/Flow, Hilt và interface repository; các phụ thuộc này chưa được khai báo trong version catalog của ứng dụng. Theo hiến chương, chuyển tệp phải dùng Supabase Storage và Edge Functions; ứng dụng hiện chưa khai báo thư viện Supabase cho Android.

**Lưu trữ**: Firestore lưu `users/{uid}` và `ownerApplications/{id}`; Supabase Storage có bucket riêng tư `owner-docs`; đường dẫn ảnh cơ sở tham chiếu bucket `venue-photos` hiện có. Không dùng cơ sở dữ liệu Supabase để lưu dữ liệu nghiệp vụ.

**Kiểm thử**: JUnit4, MockK, `kotlinx-coroutines-test`, Turbine cho ViewModel/use case/repository; Firebase Emulator cho Firestore Rules; Supabase local cho Storage RLS và Edge Functions; Espresso cho luồng giao diện chính (theo hiến chương). Ứng dụng hiện chỉ khai báo JUnit4 và Espresso; các phụ thuộc kiểm thử còn lại chưa có.

**Nền tảng đích**: Ứng dụng Android, một Activity, các Fragment dùng XML/ViewBinding và Navigation Component.

**Loại dự án**: Ứng dụng di động Android tích hợp Firebase/Firestore và Supabase Storage/Edge Functions.

**Mục tiêu hiệu năng**: Đặc tả không đặt ngưỡng độ trễ riêng. Hiển thị tiến trình tải tệp; không báo gửi thành công trước khi máy chủ xác nhận.

**Ràng buộc**: Chỉ tài khoản `PLAYER` đã đăng nhập có `ownerStatus` là `NONE` hoặc `REJECTED` mới được bắt đầu/nộp lại. Hồ sơ `PENDING` không được sửa hoặc rút. Máy chủ quyết định danh tính, quyền, trạng thái, vai trò và quyền truy cập tệp riêng tư. Giấy tờ phải là JPEG/PNG/WebP, tối đa 2 MB mỗi tệp; cần ít nhất một giấy tờ và một ảnh cơ sở. Quyền truy cập tệp ký sẵn hết hạn sau 5 phút. Khi lỗi mạng/tệp có thể khôi phục, phải giữ dữ liệu biểu mẫu. Gửi lại hoặc thao tác đồng thời không được tạo hồ sơ đang hiệu lực thứ hai.

**Quy mô/Phạm vi**: Ba chặng phía người nộp: biểu mẫu, xem lại/gửi, theo dõi trạng thái; mỗi người nộp có ít hồ sơ và tệp. Tính năng này không bao gồm giao diện duyệt admin, quản lý cơ sở/sân hay vận hành chủ sân.

## Đối chiếu hiến chương

| Điều kiện | Trạng thái | Căn cứ/kế hoạch |
|---|---|---|
| MVVM, package theo tính năng, gọi tuần tự từng tầng, StateFlow, Coroutines/Flow, Hilt | Đáp ứng khi bổ sung phụ thuộc lúc triển khai | UI/ViewModel/data đặt trong `feature/owner`; repository cô lập Firebase/Storage. Các phụ thuộc kiến trúc chưa có trong version catalog. |
| Bảo mật phía máy chủ; client không ghi vai trò/trạng thái/trường duyệt | Đáp ứng theo thiết kế | Firestore Rules, Storage RLS và signed URL; các trường được bảo vệ của user/hồ sơ do máy chủ kiểm soát. |
| Bảo vệ giấy tờ nhạy cảm | Đáp ứng theo thiết kế | Bucket `owner-docs` riêng tư; đường dẫn `{uid}/{applicationId}/{filename}`; chỉ người nộp/admin truy cập qua signed URL ngắn hạn theo tài liệu CSDL. |
| Kiểm thử tự động trọng tâm và emulator cục bộ | Đáp ứng theo chiến lược | Kiểm thử quyền cho phép/từ chối ở Rules và RLS, Edge Function, repository/ViewModel và luồng UI chính; cần bổ sung phụ thuộc kiểm thử còn thiếu khi triển khai. |
| Tài nguyên XML, trạng thái màn hình, tiếng Việt, accessibility | Đáp ứng theo thiết kế | Layout/resource XML; có trạng thái tải/rỗng/lỗi/mất mạng/đang tải tệp, thử lại, lỗi theo trường và điều khiển tệp hỗ trợ accessibility. |
| Quyền riêng tư/thu thập tối thiểu | Đáp ứng, còn thiếu chính sách lưu trữ | Chỉ thu thập trường trong spec. Xóa tài khoản phải xóa hồ sơ và giấy tờ theo spec 011/CSDL; cần xác nhận việc lưu hoặc xóa hồ sơ lịch sử. |
| YAGNI/không tự đặt schema | Đáp ứng | Chỉ dùng `users`, `ownerApplications` và bucket/function đã có. Không đề xuất collection, trường, endpoint hay enum mới. |

**Cổng trước thiết kế**: Chưa thấy vi phạm hiến chương. Hai quyết định tích hợp/schema còn bỏ ngỏ (định danh hồ sơ qua các lần nộp lại/thu hồi quyền và cách ứng dụng gọi Supabase) được ghi là điều kiện cần chốt trước khi triển khai, không tự suy đoán.

**Cổng sau thiết kế**: Đáp ứng, vẫn giữ nguyên hai điểm cần làm rõ. Ranh giới đề xuất không đổi schema và không mở rộng phạm vi admin/quản lý cơ sở.

## Cấu trúc dự án

### Tài liệu của tính năng

```text
specs/110-owner-onboarding/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── owner-onboarding-boundaries.md
```

### Mã nguồn (thư mục gốc repo)

```text
app/src/main/java/com/example/booking_app/
├── feature/owner/
│   ├── ui/       # Fragment/ViewModel cho biểu mẫu, xem lại/gửi và trạng thái hồ sơ
│   ├── domain/   # Use case và interface repository nếu cần
│   └── data/     # Bộ chuyển đổi Firestore/tải tệp và ánh xạ DTO
└── core/         # Hạ tầng dùng chung hiện có hoặc sẽ được tổ chức: auth, điều hướng, mạng, UI

app/src/main/res/
├── layout/       # Layout XML và layout cho thành phần/tệp
├── navigation/   # Đích điều hướng của người nộp trong navigation graph hiện có
└── values/       # Chuỗi tiếng Việt, màu, kích thước, giao diện

firestore.rules
supabase/functions/        # Hàm ký/tải lên hoặc xử lý hồ sơ sau khi chốt hợp đồng
supabase/migrations/       # Chính sách Storage RLS và kiểm thử cục bộ cho owner-docs
firestore-tests/           # Kiểm thử Rules Emulator
app/src/test/              # Kiểm thử đơn vị
app/src/androidTest/       # Kiểm thử giao diện
```

**Quyết định cấu trúc**: Hiến chương yêu cầu MVVM theo package tính năng trong ứng dụng Android hiện có. Đây là cấu trúc đích, không phải mô tả trạng thái hiện tại: ứng dụng mới là bộ khung với Fragment mẫu ở package gốc, chưa có `core/` hoặc `feature/`. Backend hiện chỉ có `supabase/config.toml`, chưa có Edge Function hay migration.

## Phạm vi và thiết kế thành phần

1. **Lối vào tài khoản và trạng thái**: Feature 011 sở hữu màn hình tài khoản và phần tóm tắt trạng thái. Feature 110 cung cấp các màn hình luồng người nộp, đọc `users.ownerStatus` hiện tại cùng hồ sơ/lý do từ chối liên quan. Làm mới trạng thái có thẩm quyền khi vào/tiếp tục luồng; không quyết định quyền từ cache.
2. **Biểu mẫu và kiểm tra dữ liệu**: `feature/owner/ui` chứa màn hình biểu mẫu/xem lại/trạng thái và ViewModel. Ranh giới repository ở domain phụ trách đọc hồ sơ hiện hành, lưu các trường người nộp được phép sửa, gửi/nộp lại. Kiểm tra phía client chỉ phản ánh quy tắc đã có trong spec; máy chủ/Rules vẫn là nơi thực thi cuối cùng.
3. **Chọn và tải tệp**: Kiểm tra MIME/kích thước ảnh, giữ tiến trình riêng cho từng tệp; tải ảnh cơ sở vào bucket công khai đã định nghĩa và giấy tờ vào bucket riêng tư `owner-docs`. Chỉ lưu đường dẫn object, không lưu URL đầy đủ. Chỉ đánh dấu đã đính kèm sau khi tải thành công. Truy cập qua cơ chế Supabase được thống nhất; không đưa thông tin xác thực đặc quyền vào ứng dụng.
4. **Gửi/nộp lại**: Đọc lại trạng thái user/hồ sơ có thẩm quyền ngay trước khi ghi. Chỉ tạo mới từ `NONE`, chỉ nộp lại từ `REJECTED`, từ chối thao tác khi `PENDING`/`APPROVED`. Dùng schema hiện có; các trường `users.ownerStatus` và metadata xét duyệt chỉ đổi qua luồng máy chủ được phép. Cần làm rõ thao tác gửi và cách định danh hồ sơ; tài liệu này không tự đặt tên Edge Function hay cấu trúc request.
5. **Trạng thái/lý do từ chối**: Hiển thị `NONE`, `PENDING`, `APPROVED`, `REJECTED` từ `users.ownerStatus`, kèm `submittedAt` và `rejectReason` khi có. Nếu trạng thái đổi khi biểu mẫu đang mở, hủy thao tác dựa trên dữ liệu cũ và tải lại; không ghi đè dữ liệu mới. Giao diện duyệt admin nằm ngoài phạm vi.
6. **Ranh giới bảo mật**: Firestore Rules chỉ cho người nộp đọc hồ sơ của mình và tạo/cập nhật trong trường hợp được phép khi `REJECTED`; client không ghi trường được bảo vệ. Storage RLS và signed URL do máy chủ cấp giới hạn tải lên/tải xuống giấy tờ cho người nộp/admin. Feature 111/112 phải tự kiểm tra trạng thái owner đã duyệt cho thao tác quản lý.

## Luồng dữ liệu và xử lý lỗi

- Giữ biểu mẫu chưa gửi trên thiết bị trong phiên chỉnh sửa; bản nháp cục bộ không được hiển thị như hồ sơ đã gửi. Thời gian lưu chính xác (qua tái tạo tiến trình hay lưu bền vững) chưa được đặc tả.
- Tải từng tệp trước khi gửi hồ sơ cuối cùng. Nếu tải một phần thất bại, hồ sơ vẫn chưa được gửi; cho phép tải lại tệp lỗi mà không nhập lại biểu mẫu. Cần xác định cách dọn object đã tải nhưng không còn được hồ sơ tham chiếu.
- Khi gửi bị timeout và chưa biết máy chủ đã nhận hay chưa, tải lại `users.ownerStatus` và hồ sơ trước. Chỉ thử lại sau khi xác định kết quả; không báo `PENDING` theo kiểu lạc quan.
- Khóa nút gửi trùng khi request đang xử lý. Máy chủ vẫn phải kiểm tra quyền/trạng thái và từ chối request cũ hoặc trùng; khóa nút không thay thế tính idempotent.
- Khi đọc ngoại tuyến, nếu có thì hiển thị trạng thái đã biết là dữ liệu cũ kèm nút thử lại; không mở chức năng owner dựa trên cache. Giấy tờ riêng tư không khả dụng ngoại tuyến.
- Báo lỗi theo trường mà không xóa dữ liệu hợp lệ; giữ nội dung biểu mẫu sau lỗi mạng/tải tệp có thể khôi phục. Phân biệt lỗi quyền, signed URL hết hạn, ghi xung đột do dữ liệu cũ và backend không khả dụng.

## Dependency và thứ tự thực hiện

| Phụ thuộc | Phần cần cung cấp | Ranh giới phạm vi |
|---|---|---|
| Spec 010 | Người dùng Firebase đã xác thực và vòng đời tài khoản | Không bổ sung luồng xác thực ở đây |
| Spec 011 | Lối vào từ tài khoản và hiển thị trạng thái owner; `users.ownerStatus` là nguồn trạng thái | Kế hoạch này phụ trách biểu mẫu gửi/nộp lại phía người nộp |
| README CSDL chung / Feature D | `ownerApplications`, `users`, đường dẫn bucket, Rules, trách nhiệm `sign-upload`/`sign-download`/`review-owner-application` | Phải chốt hợp đồng tải tệp và gửi hồ sơ trước khi triển khai |
| Spec 120 | Quyết định admin, lý do từ chối, đổi claim/vai trò/trạng thái, tạo venue từ `venueDraft` | Không triển khai màn hình duyệt admin |
| Spec 111 | Chỉ quản lý sau khi được duyệt; venue đầu tiên lấy từ draft đã duyệt | Không quản lý venue/court ở đây |
| Spec 112 | Vận hành chỉ dành cho owner đã duyệt | Không làm giao diện đặt/điều hành |
| Spec 100 | Các loại thông báo duyệt/từ chối hiện có | Việc phát thông báo thuộc luồng review; trạng thái vẫn phải tra cứu được trong app |

**Thứ tự**: (1) làm rõ định danh/lịch sử hồ sơ và hợp đồng tải/gửi; (2) thống nhất Firestore Rules, Storage RLS và kiểm thử cục bộ; (3) triển khai repository và tải tệp; (4) triển khai ViewModel, màn hình và điều hướng; (5) chạy kiểm thử đơn vị, emulator, Supabase local và UI. Không đưa phần duyệt admin hay thiết lập venue vào làm điều kiện ngầm của tính năng này.

## Rủi ro và quyết định còn bỏ ngỏ

1. **Mâu thuẫn về định danh/lịch sử hồ sơ (chặn triển khai)**: README CSDL quy định chung ID Firestore tự sinh; spec giả định mỗi người có một hồ sơ đang hiệu lực và nộp lại tiếp tục chuỗi xét duyệt; FR-018 lại cho phép nộp mới sau khi bị thu hồi quyền, đồng thời giữ hồ sơ `APPROVED` cũ. Chưa có quy tắc xác định hồ sơ hiện hành hoặc tránh trùng khi nộp mới. Cần chốt cách truy vấn hồ sơ hiện hành, cách chọn ID, ý nghĩa cập nhật khi nộp lại và nhu cầu index; không tự thêm con trỏ hay ID tất định.
2. **Tích hợp ứng dụng với Supabase (chặn triển khai)**: Hiến chương yêu cầu dùng Firebase Auth token làm xác thực bên thứ ba cho Supabase và dùng Edge Function cho thao tác nhạy cảm. README có tên hàm ký URL nhưng chưa định nghĩa hợp đồng upload-create/submit hoặc cách ứng dụng Android gọi. Cần chọn REST/SDK hay ranh giới máy chủ khác, đồng thời chốt truyền và làm mới token.
3. **Không khớp quyền tải ảnh cơ sở**: README mô tả `venue-photos` được tải qua `sign-upload` sau khi kiểm tra owner đã duyệt; người nộp mới chưa có quyền này. Tuy nhiên Feature 110 yêu cầu tải ảnh trước khi admin duyệt và lưu `photoPaths` trong `venueDraft`. Cần quy định quyền tải lên riêng, giới hạn cho người nộp và vòng đời tệp, không nới chính sách upload venue thông thường.
4. **Dọn bản nháp/tệp mồ côi và xóa tài khoản**: Tải dở có thể để lại object mồ côi; spec 011 yêu cầu xóa hồ sơ và giấy tờ khi xóa tài khoản, nhưng CSDL chung chưa quy định thời điểm dọn hay phục hồi khi lỗi. Cần xác định bên chịu trách nhiệm và cách thử lại.
5. **Quy tắc xác thực chưa đủ**: Chưa rõ số sân tối đa, giờ mở qua ngày/ngày nghỉ, quan hệ giữa địa chỉ và ghim bản đồ, cũng như chuẩn hóa số điện thoại. Không tự đặt giới hạn hay định dạng.
6. **Thao tác đồng thời**: Hai thiết bị cùng nộp lại hoặc admin duyệt khi biểu mẫu còn mở cần kiểm tra trạng thái/phiên bản ở máy chủ; schema chưa quy định trường phiên bản hay hợp đồng ghi có điều kiện.

## Theo dõi độ phức tạp

Không đề xuất ngoại lệ hiến chương. Không cần module Gradle, collection hay thư viện mới trong bước lập kế hoạch. Các phụ thuộc hiện tại chưa đủ cho kiến trúc và tích hợp lưu trữ bắt buộc; việc bổ sung phải theo quyết định tích hợp đã thống nhất và được ghi nhận trong thay đổi triển khai.
