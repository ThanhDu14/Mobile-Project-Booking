<!--
Sync Impact Report
- Version change: (template) → 1.0.0
- Modified principles: thay toàn bộ placeholder bằng 7 nguyên tắc:
  I. Kiến trúc MVVM, package theo tính năng
  II. Bảo mật kiểm tra phía server
  III. Toàn vẹn dữ liệu đặt lịch (NON-NEGOTIABLE)
  IV. Kiểm thử có trọng tâm
  V. Giao diện XML nhất quán và dễ dùng
  VI. Quyền riêng tư và tuân thủ pháp luật
  VII. Đơn giản và minh bạch khi dùng AI
- Added sections: Ràng buộc công nghệ (Technology Stack & Constraints),
  Quy trình phát triển và cổng chất lượng (Development Workflow & Quality Gates), Governance
- Removed sections: none
- Templates: plan-template.md, spec-template.md, tasks-template.md đọc constitution lúc chạy
  nên không cần sửa; mục "Constitution Check" trong plan sẽ dựa vào các nguyên tắc dưới đây.
- Deferred TODOs: none
-->

# SmashNow Constitution

Ứng dụng Android đặt sân và kết nối cộng đồng cầu lông, gồm ba vai trò: người chơi, chủ sân
và admin. Nguồn yêu cầu nghiệp vụ là `docs/proposal/Proposal.html`.

## Core Principles

### I. Kiến trúc MVVM, package theo tính năng

- App dùng **một Activity** với **Navigation Component** để điều hướng giữa các Fragment.
  Mỗi màn hình là một `Fragment` có layout XML, truy cập view qua **ViewBinding**. Không dùng
  `findViewById`, Kotlin synthetics hay Data Binding hai chiều.
- Mã nguồn tổ chức theo **package tính năng** bên trong `com.example.booking_app`:
  `core/` cho phần dùng chung (DI, network, util, UI base, design system) và
  `feature/<ten>/` gồm `ui/`, `data/`, `domain/` (nếu cần). Tên package khớp với các nhóm trong
  `docs/features/`, ví dụ `feature/booking`, `feature/dropin`, `feature/owner`.
- Mỗi tầng chỉ được gọi tầng ngay dưới nó: Fragment → ViewModel → (UseCase) → Repository →
  data source (Firebase). Fragment và ViewModel KHÔNG ĐƯỢC gọi trực tiếp Firebase SDK.
- ViewModel cung cấp trạng thái màn hình bằng một `StateFlow<UiState>` (gồm loading, nội dung,
  rỗng, lỗi). Sự kiện chỉ xảy ra một lần (điều hướng, snackbar) dùng `Channel`/`SharedFlow`.
  Fragment thu thập flow bằng `repeatOnLifecycle(STARTED)`.
- Bất đồng bộ dùng **Kotlin Coroutines/Flow**, không dùng callback lồng nhau hay RxJava.
  Listener realtime của Firestore được bọc trong `callbackFlow` và phải hủy khi không còn
  được thu thập.
- Inject phụ thuộc bằng **Hilt**. Repository được khai báo qua interface để test có thể thay
  bằng bản giả.
- Một tính năng KHÔNG ĐƯỢC import trực tiếp class trong `ui/` hoặc `data/` của tính năng khác.
  Phần cần dùng chung phải đưa lên `core/` hoặc dùng qua interface ở `domain/`.

**Lý do:** proposal yêu cầu chia module theo tính năng để bốn thành viên làm song song mà ít
xung đột khi merge. MVVM với StateFlow giúp viết unit test cho logic mà không cần thiết bị.

### II. Bảo mật kiểm tra phía server

- Mọi quyền hạn PHẢI được kiểm tra bằng **Firebase Security Rules** (Firestore và Storage).
  Thao tác nhạy cảm còn PHẢI đi qua **Cloud Functions**. Ẩn nút trên giao diện chỉ để trải
  nghiệm tốt hơn, không được coi là biện pháp bảo mật.
- Người dùng KHÔNG ĐƯỢC tự sửa các trường `role`, `ownerStatus`, trạng thái đơn đã thanh toán
  hay số tiền. Các trường này chỉ do Cloud Functions/Admin SDK hoặc Rules có kiểm tra chặt
  được ghi. Vai trò nên lưu bằng custom claims, có bản sao trong document người dùng để
  hiển thị.
- Chỉ chủ sân đã được duyệt (`ownerStatus == approved`) mới được ghi vào cơ sở/sân của chính
  mình. Ảnh giấy tờ chủ sân lưu ở đường dẫn Storage riêng, chỉ admin và người nộp được đọc.
- Tài khoản admin do nhóm cấp thủ công, không có màn hình đăng ký admin.
- KHÔNG ĐƯỢC commit bí mật vào repo: `google-services.json`, khóa Maps, service account,
  keystore. Khóa đặt trong `local.properties` và đọc qua Secrets Gradle Plugin. Khóa Maps phải
  giới hạn theo package name và SHA-1. Nên bật **App Check**.
- Mọi thay đổi Security Rules PHẢI có test chạy trên Firebase Emulator (xem nguyên tắc IV).

**Lý do:** người dùng có thể gọi thẳng vào Firestore mà không qua app, nên server là nơi duy
nhất đáng tin cậy để kiểm tra quyền.

### III. Toàn vẹn dữ liệu đặt lịch (NON-NEGOTIABLE)

- Mọi thao tác giữ chỗ, đặt sân, đăng ký vãng lai hay nhận chỗ từ hàng chờ PHẢI chạy trong
  **Firestore transaction** (hoặc Cloud Function có transaction). Transaction kiểm tra slot còn
  trống rồi ghi trong cùng một bước. KHÔNG ĐƯỢC đọc ở client rồi ghi riêng sau đó.
- Mỗi slot (sân con + ngày + khung giờ) có một document với ID tất định, ví dụ
  `{courtId}_{yyyyMMdd}_{HHmm}`, để hai người đặt cùng lúc sẽ tranh chấp đúng một document.
- Giữ chỗ tạm có `holdExpiresAt` (5 phút) theo **server timestamp**. Slot đang giữ mà đã hết
  hạn được coi là trống. Việc dọn dẹp và tự hủy đơn quá hạn phải chạy ở phía server.
- Thao tác tạo đơn PHẢI idempotent: client sinh `requestId` (UUID) cho mỗi lần bấm xác nhận,
  để bấm nhiều lần hoặc thử lại khi mất mạng không tạo đơn trùng.
- Thời gian lưu bằng `Timestamp` (UTC) và hiển thị theo múi giờ `Asia/Ho_Chi_Minh`. Tiền lưu
  bằng `Long` đơn vị VND, KHÔNG ĐƯỢC dùng `Double`.
- Trạng thái đơn là một máy trạng thái có danh sách chuyển trạng thái hợp lệ được ghi trong
  spec (ví dụ `HOLD → PENDING → CONFIRMED → COMPLETED`, nhánh `CANCELLED`/`EXPIRED`), và
  Rules/Functions phải chặn các chuyển trạng thái không hợp lệ.

**Lý do:** proposal xác định "không trùng khung giờ khi nhiều người đặt cùng lúc" là bài toán
cốt lõi và là nội dung bắt buộc trong báo cáo kỹ thuật.

### IV. Kiểm thử có trọng tâm

- **Bắt buộc có test:** logic nghiệp vụ trong ViewModel, UseCase và Repository (JUnit4,
  MockK, `kotlinx-coroutines-test`, Turbine); Security Rules và các transaction đặt lịch trên
  **Firebase Emulator Suite**, trong đó có test hai người đặt cùng một slot đồng thời;
  logic tính tiền, voucher và chính sách hủy.
- **Nên có:** test UI bằng Espresso cho luồng chính của mỗi nhóm tính năng (đăng nhập, đặt
  sân, đăng ký vãng lai, duyệt chủ sân).
- Test KHÔNG ĐƯỢC chạy trên project Firebase thật. Unit test thay Firebase bằng fake
  repository, còn integration test chạy với Emulator.
- Bug đã sửa ở phần đặt lịch hoặc phân quyền PHẢI kèm test tái hiện bug đó.

**Lý do:** đồ án có thời gian hạn chế nên test tập trung vào chỗ sai sẽ gây hậu quả nặng nhất,
gồm trùng lịch, sai tiền và lộ quyền, thay vì đặt mục tiêu coverage cho toàn bộ code.

### V. Giao diện XML nhất quán và dễ dùng

- Giao diện viết bằng **XML layout** với **Material Components (Material 3)** và theme
  `Theme.Material3.DayNight`. PHẢI hỗ trợ cả chế độ sáng và tối.
- Màu, kích thước, kiểu chữ và chuỗi ký tự PHẢI lấy từ resource (`colors.xml`/theme
  attributes, `dimens.xml`, `themes.xml`, `strings.xml`). Không hardcode chuỗi hay màu trong
  layout và code. Chuỗi mặc định là tiếng Việt.
- Danh sách dùng `RecyclerView` với `ListAdapter` + `DiffUtil`. Layout ưu tiên
  `ConstraintLayout` và tránh lồng nhiều tầng.
- Mỗi màn hình có dữ liệu PHẢI có đủ bốn trạng thái: đang tải, có dữ liệu, rỗng, lỗi (kèm
  nút thử lại). Mất mạng phải hiển thị thông báo, không được crash hay treo.
- Luồng đặt sân không quá **5 bước**. Danh sách và lưới giờ phải tải xong dưới **3 giây** trên
  mạng 4G. Giao diện phải hiển thị đúng trên ít nhất hai kích thước màn hình (điện thoại và
  `w600dp`).
- Khi đặt tên resource, dùng tiền tố theo loại và tính năng, ví dụ `fragment_booking_slot.xml`,
  `item_court.xml`, `ic_shuttlecock.xml`, `booking_confirm_title`.
- Có hỗ trợ accessibility cơ bản: `contentDescription` cho ảnh/icon có ý nghĩa và vùng chạm
  tối thiểu 48dp.

**Lý do:** bốn người cùng làm giao diện, nên dùng chung design system và quy ước đặt tên giúp
app trông như một sản phẩm thống nhất.

### VI. Quyền riêng tư và tuân thủ pháp luật

- Chỉ thu thập dữ liệu cá nhân tối thiểu: họ tên, số điện thoại, email, và vị trí khi người dùng
  cho phép. Quyền vị trí, camera, thông báo phải xin lúc cần dùng (runtime) và app vẫn hoạt
  động được khi người dùng từ chối.
- Khi đăng ký phải có màn hình đồng ý điều khoản và chính sách quyền riêng tư. Người dùng phải
  xóa được tài khoản và dữ liệu cá nhân của mình, theo tinh thần Nghị định 13/2023/NĐ-CP.
- Thanh toán chỉ là **giả lập**: không thu tiền thật, không lưu thông tin thẻ, và trong app
  ghi rõ đây là phiên bản học tập.
- Nội dung do người dùng tạo (bình luận, tin nhắn, ảnh, bài đăng) phải báo cáo được và admin
  phải kiểm duyệt được.
- Dữ liệu demo là dữ liệu giả hợp lý. Ảnh dùng ảnh tự chụp, ảnh miễn phí bản quyền hoặc ảnh
  chủ sân cung cấp. Không sao chép giao diện, logo hay nội dung của app tham khảo.

### VII. Đơn giản và minh bạch khi dùng AI

- Áp dụng YAGNI: chỉ làm những gì có trong spec đã duyệt. Thêm thư viện, tầng trừu tượng hay
  module Gradle mới PHẢI ghi lý do trong `plan.md` (mục Complexity Tracking).
- Mọi thành viên PHẢI giải thích được phần mã mình nộp. Việc dùng AI phải ghi log trong
  `docs/ai-log/` theo mẫu. Không đưa dữ liệu cá nhân thật hay khóa bí mật vào prompt.

## Ràng buộc công nghệ (Technology Stack & Constraints)

| Hạng mục | Lựa chọn bắt buộc |
|---|---|
| Ngôn ngữ | Kotlin (dùng Kotlin tích hợp sẵn trong AGP 9), không viết mã Java mới |
| Build | Gradle Kotlin DSL; mọi phụ thuộc khai báo trong `gradle/libs.versions.toml` |
| Android | `minSdk 29`, `targetSdk`/`compileSdk` theo bản ổn định mới nhất (hiện là 37) |
| Kiến trúc UI | Single Activity, Fragment + XML + ViewBinding, Navigation Component + Safe Args |
| Trạng thái/async | AndroidX ViewModel, Coroutines, Flow/StateFlow, `lifecycle-runtime-ktx` |
| DI | Hilt |
| Backend | Firebase qua Firebase BoM: Authentication (Email, Google, Phone), Cloud Firestore, Cloud Storage, Cloud Messaging (FCM), Cloud Functions, App Check |
| Cloud Functions | TypeScript, thư mục `functions/` ở gốc repo, Rules ở `firestore.rules`/`storage.rules` |
| Bản đồ | Google Maps SDK + Places/Geocoding; nếu không có tài khoản thanh toán thì dùng **osmdroid** (OpenStreetMap) với cùng marker |
| Ảnh | Coil (hoặc Glide) để tải ảnh; nén ảnh trước khi upload |
| QR | ZXing (`zxing-android-embedded`) để tạo và quét mã QR check-in |
| Biểu đồ | MPAndroidChart cho thống kê của chủ sân và admin |
| Lưu cục bộ | Firestore offline cache; DataStore cho tùy chọn người dùng (bật/tắt từng loại thông báo, chế độ chủ sân) |
| Kiểm thử | JUnit4, MockK, kotlinx-coroutines-test, Turbine, Espresso, Firebase Emulator Suite |
| Chất lượng mã | ktlint (định dạng) và Android Lint; không để cảnh báo Lint mức Error |

Các ràng buộc khác:

- Cloud Functions và tác vụ định kỳ (tự hủy đơn quá hạn, nhắc lịch) cần gói **Blaze**. Nếu
  nhóm không bật được Blaze, `plan.md` của tính năng liên quan PHẢI ghi rõ phương án thay thế
  (chẳng hạn kiểm tra `holdExpiresAt` ngay trong transaction và Rules) và vẫn phải đáp ứng
  nguyên tắc III.
- Đăng nhập bằng OTP số điện thoại dùng số thử nghiệm của Firebase. Ưu tiên Email và Google.
- Giữ trong hạn mức gói miễn phí: dùng truy vấn có index, phân trang danh sách, và gỡ listener
  realtime khi màn hình không còn hiển thị.
- Mỗi lần thêm phụ thuộc mới PHẢI đi qua version catalog, và phải nêu lý do trong PR.

## Quy trình phát triển và cổng chất lượng (Development Workflow & Quality Gates)

- **Spec Kit:** mỗi tính năng đi theo thứ tự `/speckit-specify` → (`/speckit-clarify`) →
  `/speckit-plan` → `/speckit-tasks` → (`/speckit-analyze`) → triển khai. Spec nằm trong
  `specs/NNN-<ten>/` và được link từ `docs/features/NN-<nhom>/README.md`. Quy trình chi tiết ở
  `docs/guides/feature-workflow.md`.
- **Git:** nhánh `main` (ổn định), `develop` (tích hợp), `feature/<NN>-<ten>`. Commit theo
  Conventional Commits với scope là tính năng, ví dụ `feat(booking): ...`. Quy ước đầy đủ ở
  `docs/guides/git-workflow.md`.
- **Pull Request:** cần ít nhất một thành viên khác review. Trước khi merge vào `develop`, PR
  PHẢI qua được `./gradlew lint testDebugUnitTest assembleDebug`. PR có thay đổi Rules, Functions
  hoặc logic đặt lịch PHẢI qua thêm test Emulator. Nên chạy các bước này bằng GitHub Actions.
- **Definition of Done** cho mỗi nhóm tính năng: đủ chức năng trong spec và có xử lý lỗi; giao
  diện đúng thiết kế trên hai kích thước màn hình; đã review và merge vào `develop`; có kịch bản
  kiểm thử và ảnh/video minh chứng; có log AI nếu có dùng AI; đã cập nhật trạng thái trong
  `docs/features/README.md`.
- **Constitution Check:** mục này trong `plan.md` PHẢI đối chiếu với nguyên tắc I–VII. Chỗ nào
  vi phạm thì ghi vào Complexity Tracking kèm lý do và phương án đơn giản hơn đã bị loại.

## Governance

- Constitution này có hiệu lực cao hơn mọi quy ước khác trong repo. Khi có mâu thuẫn, làm theo
  constitution và sửa tài liệu còn lại cho khớp.
- **Sửa đổi:** mở PR chỉ sửa `.specify/memory/constitution.md`, ghi rõ lý do và ảnh hưởng tới
  các spec/plan đang có. Cần ít nhất **3/4 thành viên** đồng ý mới được merge.
- **Phiên bản (SemVer):** MAJOR khi bỏ hoặc định nghĩa lại một nguyên tắc; MINOR khi thêm nguyên
  tắc/mục hoặc mở rộng đáng kể; PATCH khi chỉ sửa câu chữ, làm rõ nghĩa.
- **Tuân thủ:** người review PR kiểm tra theo nguyên tắc I–VII. Mỗi khi chuyển sang một mốc
  trong kế hoạch 12 tuần, nhóm rà lại constitution một lần.
- Hướng dẫn chi tiết lúc phát triển nằm trong `docs/` (bắt đầu từ `docs/README.md`).

**Version**: 1.0.0 | **Ratified**: 2026-10-03 | **Last Amended**: 2026-10-03
