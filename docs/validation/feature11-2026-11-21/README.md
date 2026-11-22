# Feature 11 — convergence và kiểm chứng local

Phạm vi: 110 onboarding/mở thêm cơ sở, 111 cấu hình cơ sở/sân/giá/giờ khóa, 112 vận hành. Không cập nhật AI_log_4, commit, push hoặc deploy hosted trong lượt này.

## Thiếu sót đã xác nhận

| Yêu cầu | Code trước sửa | Sửa và kiểm chứng |
|---|---|---|
| Giá không chồng giờ, minPricePerHour khớp | Client ghi rule rồi gọi sync riêng; server sync thiếu kiểm tra profile hiện tại | Transaction `sync-venue-price` kiểm tra quyền, rule, overlap, ghi rule/minimum cùng lúc; Rules cấm client bỏ qua boundary; race/retry/thu hồi quyền trên Emulator |
| Khóa venue chặn sửa sân con | Rules chỉ kiểm tra ownerStatus/ownership | Kiểm tra OWNER/APPROVED/ACTIVE/non-deleting và parent không LOCKED; validate court; ACTIVE/HIDDEN vẫn cho bảo trì cấu hình |
| Tạo sân không trùng khi timeout | Mỗi retry tạo ID ngẫu nhiên mới | SavedStateHandle giữ ID theo lần tạo; transaction đọc lại và từ chối ghi đè kết quả đã đổi |
| Vận hành theo cơ sở đang chọn | Dashboard/form tạo VM mới chọn cơ sở đầu; refresh booking hủy lượt tải cơ sở | Truyền/lưu venueId; sửa thứ tự tải context booking; test immediate onResume và cơ sở thứ hai |
| Draft drop-in/voucher khi xoay | Form sửa nạp lại server đè trường nhập và chọn sân mặc định | Giữ trạng thái form/court selections, không populate lại draft đã khôi phục; test rotation trên điện thoại |
| Retry drop-in/voucher | Kết quả cũ trả conflict; Android có thể dựng session ID giả | Server đối chiếu kết quả cũ với payload và quyền hiện tại; không reset counters/ghi đè bản đã sửa; client chỉ nhận ID thật |
| Quyền Edge hiện tại | Một số nhánh ADMIN bỏ qua profile; cancel/check-in drop-in thiếu gate venue | Helper kiểm tra claims + profile trong transaction; chặn account xóa/khóa, quyền thu hồi, venue HIDDEN/LOCKED cho mutation |
| Thống kê đúng nguồn và mới | Cấu hình availability tải một lần; custom range không mở được; báo cáo cũ có thể xóa lỗi range | Projection trả cấu hình cùng transaction mỗi lần; mở form custom và xác nhận; hủy observation cũ khi range sai |
| Lịch bảo toàn lịch sử | Lọc bỏ court inactive khỏi tất cả hàng | Giữ court trong lịch đọc; ngừng hoạt động vẫn chặn tạo booking/drop-in mới ở server |
| Bản đồ như phần B | Form dùng hình nền/ghim minh họa; picker đổi user agent thành tên gói, mất trạng thái dialog khi xoay | Dùng core OsmdroidSetup của B, tile/cache/marker/chế độ tối chung; preview thật và DialogFragment chọn ghim, loading/error/retry; giữ ghim qua xoay và lưu/đọc GeoPoint từ server |
| Số sân hoạt động | courtCount giữ số khai hồ sơ; thêm/ngừng sân không cập nhật | Quyết định người dùng: đếm sân isActive=true, cho phép 0. owner-manage-court tính lại cùng transaction, Rules cấm ghi vòng ngoài. Hồ sơ vẫn giữ số khai; venue mới duyệt có 0 court thực |

## Môi trường

- Điện thoại thật vivo V2247, Android 15, APK test riêng `com.example.booking_app.adminlocal`.
- Firebase Auth/Firestore Emulator: project `demo-admin-repair`, cổng 19099/18080. CI Auth riêng project `demo-smashnow`, cổng 19199.
- Supabase local `http://127.0.0.1:54321`; functions chạy từ checkout hiện tại. Không deploy hosted và không dùng tài khoản người dùng thật.
- Fixture bắt buộc localhost/demo project; seed tại `scripts/owner/seed-feature11-device.ts` và hai fixture admin có sẵn. Mỗi lượt điện thoại dọn namespace emulator của APK test trước khi seed.
- Backend dữ liệu là local; tile bản đồ lấy từ OpenStreetMap qua Internet/cache dùng chung với B. Đây là hai nguồn khác nhau, không gọi backend hosted của ứng dụng.

## Fixture thống kê tính trước

Ngày trước ngày seed, venue TEST Statistics: 2 sân × 4 giờ × 2 slot/giờ = 16 slot. Một slot MAINTENANCE bị loại: mẫu số 15; 2 slot BOOKED thuộc booking COMPLETED: occupancy **13,333333%**. Peak **08:00**, **60 phút**. Tiền PAID **100.000**, DEPOSITED **30.000**, REFUNDED **20.000 VND**, một đơn hoàn. Không lấy giá rule làm doanh thu.

## Ba nhóm CI

Đây là các job tương đương chạy local theo `.github/workflows/ci.yml`, chưa chạy GitHub Actions vì lượt này không push.

| Job | Kết quả | Bằng chứng |
|---|---|---|
| Android lint/unit/build | PASS; 1.639 tests, 0 fail, 2 skipped; lint 0 errors, 850 warnings | [android-ci.log](android-ci.log) |
| Edge type check và toàn bộ test | PASS; 513 passed, 0 failed | [edge-typecheck.log](edge-typecheck.log), [edge-tests.log](edge-tests.log) |
| Firestore Rules | PASS; 30 files, 311 passed, 4 skipped theo SKIP_BLOCKED=1 | [firestore-rules.log](firestore-rules.log) |

Số liệu tổng hợp: [ci-results.json](ci-results.json). Các lượt thất bại trong quá trình sửa không được dùng để tuyên bố pass.

## Kiểm thử máy thật

PASS trên vivo V2247 Android 15: **9 ca hồi quy + 1 ca cold start**, 0 fail. Đây là instrumentation thao tác view thật trên điện thoại, dùng Fragment/ViewModel/repository và backend production chạy local. OS file picker được Espresso Intents cấp ảnh TEST; upload, review, private signed-download và ghi dữ liệu vẫn đi qua luồng thật.

| Phạm vi | Đã kiểm chứng |
|---|---|
| 110/120 | Đăng ký lần đầu; OWNER thêm cơ sở, upload/xem giấy tờ private, PENDING → từ chối → sửa/nộp lại → admin duyệt; OWNER/APPROVED và cơ sở cũ giữ nguyên; mở lại thấy venue mới. Venue mới courtCount=0, số khai báo 2 vẫn ở hồ sơ |
| 111 | Tạo/sửa sân qua recreation, ngừng/kích hoạt lại, đổi thứ tự; count 2 → 3 → 2 → 0 → 2; lưu cơ sở ở count=0; bảng giá/minimum; khóa/mở slot và giữ booking |
| 112 | Duyệt/từ chối/hủy booking và hoàn ví 130.000; check-in xác nhận thu tiền và từ chối replay; lịch ngày/tuần; drop-in hai sân tại cơ sở thứ hai, xoay draft, tăng sức chứa/check-in; voucher sửa/tắt tại đúng venue, usedCount không đổi; thống kê khớp fixture |
| Map chung với B | Tile đường phố thật; chạm chọn ghim, xoay dialog giữ tọa độ; preview trong form; lưu venue và đọc lại GeoPoint từ SERVER; tọa độ ngoài Việt Nam bị chặn, hủy không ghi |
| Session/navigation | Force-stop rồi instrumentation session mới không đăng nhập lại; đọc đủ 3 venue, đổi OWNER → PLAYER → OWNER giữ role, Intent deep link và Back |
| Tích hợp đã merge | Admin ẩn bài/tin nhắn và khóa nhóm, helper 081 khi đổi sức chứa/hủy, cron dropin-close local lặp không tạo hiệu ứng trùng |

Log: [device-tests.log](device-tests.log), [cold-start-test.log](cold-start-test.log). Đối chiếu độc lập bằng script: [backend-assertions.json](backend-assertions.json). Tổng hợp môi trường, hash APK/source và kết quả: [results.json](results.json), [source-hashes.json](source-hashes.json).

Lưu **63 ảnh** của các màn TEST; manifest [screenshots.json](screenshots.json). Một số ảnh chính: [ghim bản đồ](screenshots/20-application-map-pin.png), [xoay dialog](screenshots/21-map-pin-dialog-landscape.png), [venue đọc lại từ server](screenshots/23-venue-map-server-reload.png), [không còn sân hoạt động](screenshots/01-zero-active-courts-venue-edit.png), [thống kê](screenshots/16-statistics-occupancy-peaks.png). Đã loại ảnh lỗi cũ khỏi bằng chứng bàn giao và khôi phục animation/thiết lập giữ sáng ban đầu của điện thoại.

## Giới hạn kiểm chứng

Không đo SLA 4G, usability với người tham gia hoặc xác nhận vận hành hosted. QR quét qua camera, mọi nhánh lỗi mạng trên điện thoại và mọi kiểu process death phải được phân biệt với unit/Emulator hoặc instrumentation recreation.

Các ca ownership giả, thu hồi quyền, venue LOCKED, timeout/retry, dữ liệu stale và request đồng thời được kiểm tra tự động qua unit/Emulator; không khẳng định đã bấm mọi nhánh này trên điện thoại. Chưa kiểm chứng push delivery, thiết bị tablet thật hoặc mọi đường chat 091 trên điện thoại trong lượt này. Nút chọn map cho LOCKED đã được kiểm tra qua code/boundary tự động; chưa có ca bấm riêng trên điện thoại cho trạng thái đó.

Availability thống kê dùng cấu hình hiện tại cùng snapshot và slot/booking được giữ lại, không có schema version lịch sử cho giờ mở cửa/bảng giá. Không suy diễn đây là bản chụp cấu hình tại thời điểm phát sinh từng booking.

Với dữ liệu legacy, courtCount được tính lại trong lần mutation sân kế tiếp. Không tự backfill dữ liệu hosted trong lượt này; cần rà dữ liệu và chạy migration khi triển khai thật.

## Tái chạy

Ba lệnh CI và cấu hình dịch vụ theo `.github/workflows/ci.yml`. Trên Windows dùng JDK của Android Studio tương thích Gradle/Firebase CLI. Chạy Rules và Edge tuần tự nếu dùng chung project demo-smashnow; dọn namespace fixture của suite trước mỗi lượt Edge. Fixture mới owner-feature11 tự dọn project demo-owner-feature11 riêng.

Build APK local: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PadminTestRunner=com.example.booking_app.feature.admin.AdminLocalTestRunner -PadminLocalAppId=true`. Khởi động Auth/Firestore Emulator 19099/18080 với project demo-admin-repair, Supabase/functions local 54321 và env trỏ emulator. Dọn riêng project TEST này, nạp Rules cùng ba script seed nêu trên, rồi adb reverse ba cổng. Cài cả APK và test APK; chỉ mở APK qua AdminLocalTestRunner để DI tiếp tục dùng backend local.

Chạy lần lượt HandoffDeviceInteractionTest, VenueAdditionDeviceTest, OwnerFeature11DeviceTest, OwnerMapDeviceTest bằng `am instrument -w -r -e class <danh-sách-class> -e localSupabaseKey <publishable-key-local>`. Sau force-stop chạy OwnerColdStartDeviceTest bằng runner mới. Chạy scripts owner/admin `verify-*-device.ts` với env localhost/demo để đối chiếu backend; không đưa service key/token vào log. Xem [quickstart 112](../../../specs/112-owner-operations/quickstart.md).

## Đề xuất chia commit

1. `fix(owner): make court and price mutations atomic` — backend, court API/repository Android, Rules và regression tests, khởi tạo derived count khi admin duyệt.
2. `fix(owner): preserve operation context and authoritative state` — draft/context, drop-in/idempotency/check-in window, thống kê/slot generation và tests.
3. `feat(owner): reuse shared map for venue location` — picker/preview chung với B, layout và kiểm thử map.
4. `docs(owner): align contracts and record local validation` — specs/tasks, fixture/verification scripts và bằng chứng. Các instrumentation tests nên đi cùng commit tính năng tương ứng.

Chưa commit/push/deploy hosted; AI_log_4 không đổi. Không sửa checkbox checklist reviewer hoặc dùng kết quả này để tự xác nhận các SC về SLA/usability chưa đo.
