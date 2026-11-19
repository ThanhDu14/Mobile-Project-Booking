# Kiểm tra chủ sân 110, 111, 112 — 2026-11-14–15

Trạng thái: đã hoàn tất lượt sửa và các ca xác minh ghi dưới đây; còn các giới hạn/tài liệu mâu thuẫn được ghi riêng. Không dùng bảng task `[X]` cũ làm bằng chứng nghiệm thu.

## Môi trường và phạm vi cho phép

- Thiết bị thật: vivo V2247, USB adb `10ADBY2GH30038C`.
- Package: `com.example.booking_app`, debug, Firebase `mobile-22e6c`, Supabase `dbjzrpmtslncogipwpai`.
- Người dùng cho phép dùng tài khoản/cơ sở OWNER hiện tại để tạo/sửa dữ liệu kiểm thử và deploy phần backend thiếu. Không đưa token, mật khẩu hoặc giấy tờ thật vào bằng chứng.
- Ảnh thiết kế: toàn bộ 18 ảnh trong `specs/110-owner-onboarding/ui`, `111-owner-venue-setup/ui`, `112-owner-operations/ui`.
- Nguồn yêu cầu: [110](../../../specs/110-owner-onboarding/spec.md), [111](../../../specs/111-owner-venue-setup/spec.md), [112](../../../specs/112-owner-operations/spec.md). Các bảng dưới phân biệt thao tác backend thật với kiểm thử tự động; chưa xác nhận khớp toàn bộ tiêu chí UI/nghiệm thu.
- Các ca tự động về phân quyền/cạnh tranh chạy với emulator hoặc transport giả; thao tác trên backend thật được ghi riêng bên dưới.

## Phát hiện và sửa

| Feature/màn hình | Vấn đề ban đầu | Thay đổi | Bằng chứng hiện có |
|---|---|---|---|
| 110/111/112 đọc cơ sở | `location` do server lưu map làm `getGeoPoint` ném lỗi, chặn toàn bộ quản lý | Bộ đọc tương thích dữ liệu cũ; server duyệt lưu GeoPoint đúng kiểu | `before-home-location-error.png`, `after-home-live.png`, unit test mapper |
| 110 đăng ký (FR-001–007, FR-015–016) | Popup quận/huyện không mở trên máy; lỗi tải danh mục bị nuốt, không có retry | Dialog chọn từ danh mục Firebase; lỗi/retry ngay tại trường, giữ draft khi tải lại; scope state trong graph tài khoản | Máy PLAYER nhập AUDIT151126, chọn Quận 1/map, upload 2 fixture, sửa preview và nộp thành công; đúng 1 application PENDING, role PLAYER |
| 110 xem hồ sơ/tệp (FR-007, FR-012) | Sau restart tài khoản PENDING không có nút vào hồ sơ; preview giấy tờ bị collapse nên ảnh không hiện; nút lộ UUID dài | Nút Xem hồ sơ đã nộp; vùng ảnh cố định trong container; nạp bytes qua download private có signed URL; nhãn Xem giấy tờ 1 | `onboarding-document-before.png`, `onboarding-document-fixed.png`; tệp tải xuống 6788 bytes có SHA-256 khớp fixture gốc |
| 112 Hôm nay | Banner 4 đơn, hạn 12 phút và lịch mẫu vẫn xuất hiện khi lỗi tải | Kết hợp booking, drop-in, slot theo cơ sở và ngày Việt Nam | `after-home-live.png` xác nhận 0 đơn và trạng thái rỗng thật |
| 112 voucher | DI dùng `ContractPendingOwnerVoucherRepository`, không đọc/ghi backend | Adapter Firestore và Edge Function cho tạo/sửa/bật/tắt; kiểm tra quyền trong transaction; bảo vệ usage counters | `voucher-server-result.png`, truy vấn Firestore độc lập xác nhận `AUDIT141126` |
| 112 form voucher | Preview mã/giảm giá cố định, footer lộ `%1$s`, không có phản hồi điều hướng sau lưu | Preview theo dữ liệu nhập, tên cơ sở thực, quay lại danh sách một lần sau thành công | Đã tạo và tắt voucher qua máy thật; xem bằng chứng backend |
| 112 thống kê | Doanh thu luôn bằng 0; chỉ đọc 10 ngày; số đơn và tiền hoàn mẫu | Booking thực qua server projection tối đa 30 ngày; mẫu số từ court/giờ/giá; tách thanh toán và hoàn; loại BLOCKED; chia bucket giờ | Unit test và máy thật: 2.000đ, lấp đầy 50%, peak 23:00/30 phút, đúng sau restart |
| 112 chi tiết booking | Gọi `.get().result` trước khi Task hoàn tất; không kiểm tra venue khớp | `await()`, kiểm tra document và venue, đếm slot từ dữ liệu thật | Máy thật mở đúng booking TEST, tổng 2.000đ, 1 khung giờ; `booking-details-real.png` |
| 111 sửa cơ sở | Thiếu khu vực, vị trí tương tác, tiện ích, ảnh, trạng thái hiển thị | Danh mục Firebase, map pin thật, ký quyền upload Supabase, draft khi nhập, ACTIVE/HIDDEN | `venue-details-live.png`, `venue-map-picker.png` |
| 111 danh sách cơ sở/sân | Thiếu lối vào các màn vận hành; listener switch có thể còn gắn item cũ; không bật lại court; bốn nút cùng hàng làm chữ bị tách giữa từ | Menu điều hướng, tháo listener khi bind, hỗ trợ bật lại court; chia thao tác thành hai hàng, dành chiều rộng cho nhãn hoạt động | `venue-list-live.png`, `court-created-real.png`, `before-court-buttons.png`, `after-court-buttons.png` |
| 111 khóa giờ | Lưới cứng 06:00–22:00, chỉ sân đầu; đọc rồi ghi ngoài transaction; UNLOCK không kiểm tra venue/court của slot | Giờ hoạt động thật, chọn sân, transaction toàn bộ quyền/slot, chặn mở khóa chéo cơ sở | 4/4 ca emulator về cạnh tranh, rollback, giả mạo UNLOCK và AVAILABLE |
| 112 drop-in (FR-008–012, FR-020) | Không có đường UI sửa/hủy; roster dùng số mẫu 14/16, 3, 0; không tải lại sau check-in; lộ `%1$d`; quyền hủy/check-in đọc ngoài transaction | Form edit, hủy có lý do, bộ đếm từ registration, reload sau check-in, chọn nhiều sân và định dạng số sân; quyền hiện tại trong transaction; kiểm tra slot thuộc buổi trước khi xóa | Máy thật tạo buổi 2 chỗ, sửa thành 3, hủy có lý do; backend xác nhận CANCELLED và slots rỗng. Test quyền bị thu hồi/tài khoản khóa và slot giả mạo. `dropin-created-real.png`, `dropin-updated-real.png`, `dropin-cancelled-real.png` |
| 110/111 ảnh | Bucket ảnh công khai thiếu; ảnh cơ sở khi duyệt vẫn tham chiếu staging riêng tư | Tạo `venue-photos`; chỉ copy photoPaths sang public, giữ documentPaths private; ký upload theo ownership; sửa race khi tải catalog ghi đè trạng thái venue | Upload/lưu ảnh thật tăng photoCount lên 2, gỡ ảnh thử còn 1, HEAD ảnh hiện tại HTTP 200; test hồi quy delayed catalog |
| 112 duyệt/từ chối/hủy booking | Đọc/ghi ngoài transaction; có thể xóa slot không thuộc booking; trạng thái hoàn tiền không đi cùng cập nhật ví | Transaction kiểm tra quyền hiện tại và liên kết slot; hoàn số đã thu theo hợp đồng ví dự án, ledger idempotent; QR không trả cho owner | Máy thật duyệt/từ chối/hủy đơn chưa thu; emulator kiểm tra cạnh tranh, hoàn một lần và slot giả mạo. Hoàn tiền đơn đã thu trên hosted chưa kiểm thử |
| 112 booking trên máy | Nút duyệt/từ chối bị vô hiệu; thời gian bị ép thành cột do hạn xử lý; mỗi giây dựng lại row làm mất touch đang thực hiện | Bật thao tác theo quyền/trạng thái/deadline, thêm lý do bắt buộc và hủy đơn; giờ/hạn xử lý riêng dòng, giữ row qua countdown tick | Máy thật duyệt 2, từ chối 1 và hủy 1 booking TEST; ảnh `booking-time-before.png`, `booking-time-fixed.png`, `booking-reject-reason-required.png`; test đo chiều rộng với chuỗi dài |
| 112 check-in | Client có fallback giờ hiện tại nếu server chưa trả timestamp; chưa có preview thu tiền rõ ràng; handler đọc/ghi không nguyên tử | Preview chỉ đọc số tiền còn thu; nút Đã thu tiền gửi xác nhận riêng; timestamp phải từ server; transaction kiểm tra quyền hiện tại/thời gian/tiền và chống trùng | Máy thật preview 2.000đ → PAID, remaining=0, checkedInAt thật; nhập lại mã bị chặn. 9 unit Edge và emulator cạnh tranh; ảnh `booking-checkin-success.png`, `booking-checkin-duplicate.png` |

## Backend đã deploy

- Firebase Rules: thêm đọc `venues/{venueId}/vouchers`, vẫn cấm toàn bộ ghi trực tiếp từ client.
- Firebase indexes: deploy file khai báo; giữ các index ngoài repo, không dùng `--force`. Index voucher `code` và hàng đợi hồ sơ status/submittedAt đã READY; bổ sung index thống kê venue/startAt và venue/date.
- Supabase: `owner-create-voucher`, `owner-update-session` (trước đó thiếu), `owner-create-session`, `manage-slot-blocks`, `owner-docs`, `admin-review-application`, `owner-approve-booking`, `owner-reject-booking`, `owner-cancel-booking`, `owner-cancel-session`, `owner-checkin-dropin-registration`, `owner-statistics-source`, `checkin-booking`.
- SQL: chạy riêng migration `20261114150000_owner_venue_photos_bucket.sql`, không đẩy các migration khác chưa được đối chiếu. Tạo bucket public với giới hạn 2 MB và MIME JPEG/PNG/WebP; không thêm quyền ghi trực tiếp.

## Kết quả kiểm tra đã chạy

- Build debug thành công; toàn bộ JVM 1.196 test, 0 failure, 0 error. Log cập nhật: `owner-booking-layout-build.log`, `owner-final-verification.log`.
- Android instrumentation cuối trên máy thật: 4/4 layout tests; thêm đo dòng giờ với hạn xử lý dài tại chiều rộng 320dp. Các cấu hình phone/light/dark/w600dp được inflate; không phải bằng chứng mọi luồng nghiệp vụ đã qua. [Log](logs/owner-instrumentation-final.log).
- Edge cuối: 55/55 ca voucher, duyệt hồ sơ, ảnh, thống kê, booking/check-in và drop-in. [Log Edge](logs/owner-edge-final-55.log).
- Auth/Firestore Emulator: 29/29 ca onboarding, private uploads, đầu vào, giao dịch slot/booking, check-in và hoàn tiền. [Log emulator](logs/owner-edge-emulator-final.log). Lệnh tái chạy: `scripts/owner/test-edge-emulators.ps1`.
- Firebase Emulator transaction: 7/7 ca cạnh tranh, rollback, phân quyền slot và booking/hoàn tiền. `owner-transactions-emulator.log`.
- Rules toàn repository: 224 passed, 4 failed trong Feature 030 (VD-1b, VD-3, VD-4, VD-10). Không nới quyền đọc cơ sở ẩn/khóa để làm các ca đó xanh.
- Lint và JVM cuối: BUILD SUCCESSFUL, 0 errors; lint 753 warnings toàn app. [Log](logs/owner-final-verification.log). Không coi warnings tồn tại toàn app là lỗi đã sửa hết.
- Sau chỉnh padding nút lọc, build/lint tiếp tục thành công (`owner-filter-layout-final.log`); đã cài APK cuối và xác nhận nhãn Chờ duyệt hiển thị đủ (`booking-filter-labels-fixed.png`).

## Dữ liệu và bằng chứng backend thật

- Voucher `AUDIT141126` được tạo từ giao diện máy thật, value 10%, usageLimit 1, usedCount 0; backend độc lập xác nhận `isActive=false`.
- Court `AUDIT141126` được tạo qua UI, sau kiểm thử đã ngừng hoạt động. Quy tắc Chủ nhật 08:00–09:00, 100.000 VND/giờ đã tạo rồi xóa; backend xác nhận `priceRules=[]`, `minPricePerHour=null`.
- Khóa rồi mở slot `20261115_0800` qua UI. Buổi miễn phí `20261115_0830` tạo/sửa/hủy qua UI; server xác nhận capacity=3, CANCELLED và `slots=[]`.
- Ảnh dùng thử là ảnh màn hình ứng dụng của chính cơ sở kiểm thử. Đã gỡ khỏi photoPaths sau khi lưu thành công; ảnh ban đầu được giữ, HTTP 200. Không chọn ảnh cá nhân/giấy tờ thật từ thư viện.
- Không coi dữ liệu test trên server là tiền thu thật. Các bản ghi `AUDIT...` phải được tắt/ngừng hoạt động hoặc dọn sau kiểm thử.
- Ba booking `owner-audit151126-{checkin,reject,cancel}` được tạo bằng script fixture quản trị giới hạn đúng cơ sở AUDIT, mỗi đơn 2.000đ AT_VENUE/UNPAID. Đây không phải kiểm thử PLAYER checkout. Mọi thao tác duyệt/từ chối/hủy/check-in sau đó thực hiện qua Android bằng tài khoản OWNER thật.
- Backend độc lập xác nhận: checkin=CONFIRMED/PAID, paidAmount=2000, remainingAmount=0, checkedInAt=`2026-11-15T16:13:08.801Z`; reject=REJECTED/UNPAID/refundAmount=0; cancel=CANCELLED/UNPAID/refundAmount=0, lý do AUDIT151126. Không ghi vào ví hay thực hiện chuyển tiền thật.
- Màn thống kê sau tải lại: doanh thu 2.000đ, cọc/hoàn 0đ, lấp đầy 50%, peak 23:00/30 phút; `statistics-positive-real.png`.
- Đã force-stop và mở lại ứng dụng: thống kê vẫn đúng (`statistics-after-restart.png`). Cơ sở AUDIT đã HIDDEN, ảnh public vẫn HEAD200; court/khung giá TEST và 3 booking được giữ để truy vết, không xóa booking đang chơi/đã check-in. Cơ sở `demo-truong-chinh-909` không thay đổi.
- [Danh sách file thay đổi](files-changed.txt), [xác nhận backend độc lập](logs/owner-hosted-data-verification.log), ảnh bàn giao tại `screenshots/`, log kiểm thử tại `logs/`.
- [Chỉ mục ảnh và ca kiểm thử](evidence.md) nêu rõ ảnh nào là bằng chứng, trạng thái cuối xác nhận bằng CLI, và các luồng chưa được thao tác.
- [Lỗi, cách tái hiện, nguyên nhân và mức ảnh hưởng](issues.md).

### Hồ sơ PLAYER kiểm thử trên backend thật

- Tên cơ sở test: `AUDIT151126`; application `1b9faac0-11df-47c3-8110-bf928265fb4f`. Trước nộp: 0; sau nộp: đúng 1, application/status user đều PENDING, role PLAYER. Không dùng giấy tờ thật.
- Chọn chính xác `owner-audit-venue.png` và `AUDIT-TEST-NOT-LEGAL-DOCUMENT.png` từ trình duyệt tệp. Storage xác nhận 2 object, mỗi object 6788 bytes.
- Xem trước và tải giấy tờ bằng quyền người nộp; SHA-256 của tệp tải xuống trùng fixture: `917ca1700a8819edae6037fe50caacbf5ce8f8f24f3ff82dd70db4594dab2e69`.
- PENDING tiếp tục dùng Trang chủ PLAYER. Deep link quản lý venue trả lỗi quyền; không hiển thị dữ liệu venue/court.
- Tài khoản PLAYER này có sẵn venue `demo-truong-chinh-909`, createdAt 27/10, trước hồ sơ AUDIT nộp 15/11. Không coi venue cũ đó là cơ sở được cấp do hồ sơ PENDING; không sửa/xóa venue đó.
- Đã thao tác toàn vòng trên máy thật với các tài khoản do người dùng đăng nhập: PENDING → ADMIN từ chối lý do `AUDIT151126 - CAN BO SUNG` → PLAYER sửa địa chỉ/nộp lại trên cùng application ID → ADMIN duyệt → đăng nhập lại đúng tài khoản, mở được giao diện OWNER và cơ sở AUDIT mới. Truy vấn độc lập xác nhận đúng 1 application, role OWNER/ownerStatus APPROVED, venue location là GeoPoint và ảnh public HEAD 200; giấy tờ vẫn ở bucket private.
- Đã sửa lỗi giờ 0530/2230 từ server không đổi thành HH:mm khi mở form nộp lại; lỗi so sánh version Date mất mili giây gây 409 sai ở màn duyệt; bổ sung index hàng đợi ADMIN đang thiếu trên hosted Firebase.

## Vấn đề tài liệu/phần cần xác minh

- Feature 111 contract yêu cầu Thêm cơ sở đi qua 110; spec 110 FR-018 lại cấm OWNER APPROVED tạo hồ sơ mới. Không cấp quyền tạo venue trực tiếp để vượt mâu thuẫn. Cần chốt lifecycle thêm cơ sở cho owner đang có cơ sở.
- Luồng 110 nộp mới, từ chối, sửa/nộp lại và duyệt đã xác minh thực tế. Chưa đo toàn bộ tiêu chí nghiệm thu về hiệu năng/usability.
- Chưa đo usability với người tham gia, SLA 4G, hoặc xác nhận check-in QR dương tính bằng registration/booking đúng cửa sổ thời gian.
- Độ trễ ghi/upload backend thật có lúc hàng chục giây; chưa đạt bằng chứng SLA 3 giây. Có loading và chặn gửi lặp; không báo SLA đã đạt.
- Thống kê dùng booking/slot thật theo quyết định T007; FR-015 nhắc `dailyStats` cần đồng bộ tài liệu. Endpoint `owner-statistics-source` đã deploy, giới hạn kỳ 30 ngày, xác minh quyền hiện tại, whitelist trường và không trả QR/danh tính người đặt; số tiền dương đã đối chiếu trên máy thật.
- Check-in từng gặp lỗi mạng trên Android/4G, không ghi thay đổi khi lỗi; sau mở lại ứng dụng đã preview, xác nhận và chống trùng thành công. Chưa xác định được nguyên nhân kết nối gián đoạn; log chẩn đoán chỉ ghi tên lớp exception, không ghi request/token.
- Chưa xác minh camera QR dương tính, hoàn tiền booking đã thu trên hosted backend, hay check-in registration vãng lai có người tham gia. Các ca cạnh tranh/hoàn tiền tự động dùng emulator; không suy diễn là đã thao tác các ca này trên backend thật.
- Test booking `generateDailySlots_assignsPastStatus_forElapsedSlotsToday` trước đây phụ thuộc ngày chạy và fixture chỉ có giá ngày thường nên lỗi cuối tuần. Đã cố định ngày/thời điểm thứ Ba trong test; không đổi logic sản phẩm.

## Tài liệu kỹ thuật tham khảo

- [Supabase copy objects across buckets](https://supabase.com/docs/guides/storage/management/copy-move-objects): dùng copy giữa bucket staging private và bucket ảnh cơ sở public; không copy giấy tờ.
