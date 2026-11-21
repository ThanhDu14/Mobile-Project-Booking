# Kiểm chứng Feature 112

Dùng Firebase Emulator và Supabase local, không chạy fixture trên hosted. APK instrumentation riêng dùng project demo-admin-repair và applicationId com.example.booking_app.adminlocal.

## Lệnh CI

- Android: ./gradlew lint testDebugUnitTest assembleDebug --stacktrace
- Edge: deno check . và deno test -A --no-check trong supabase/functions, với Auth/Firestore Emulator và Supabase local theo .github/workflows/ci.yml.
- Rules: npm ci, SKIP_BLOCKED=1 npm test trong firestore-tests với Firestore Emulator.

Dọn fixture của suite trước mỗi lần chạy lại; owner-transactions.emulator.test.ts dùng namespace demo-owner-concurrency, không dùng lại dữ liệu lần trước. Không chạy Rules và Edge cùng namespace đồng thời.

## Điện thoại thật

Build adminLocalAndroidTest bằng AdminLocalTestRunner và -PadminLocalAppId=true; map các cổng Auth/Firestore/Supabase qua adb reverse. Nạp scripts/admin/seed-venue-addition-device.ts, seed-handoff-device.ts và scripts/owner/seed-feature11-device.ts trên namespace local sạch.

Chạy VenueAdditionDeviceTest (110 lần đầu và bổ sung), HandoffDeviceInteractionTest (081/admin tích hợp), OwnerFeature11DeviceTest (111/112) và OwnerMapDeviceTest (map như phần B). Truy cập màn theo graph OWNER, rồi thực hiện thao tác bằng view/action thật; ghi dữ liệu qua repository/backend production, không mock kết quả mutation. Sau đó force-stop APK local và chạy OwnerColdStartDeviceTest trong một instrumentation session mới, không đăng nhập lại.

1. Chọn cơ sở thứ hai: tạo drop-in hai sân, nhập draft, recreation, sửa sức chứa rồi mở roster/check-in bằng mã.
2. Tạo sân và bảng giá; đối chiếu courtCount/minPricePerHour cùng commit. Ngừng toàn bộ sân, sửa cơ sở ở courtCount=0 rồi kích hoạt lại; không mất lịch sử. Khóa/mở slot và xem lịch ngày/tuần.
3. Duyệt/từ chối booking, hủy hợp lệ và đối chiếu refund/wallet/slots; check-in AT_VENUE với xác nhận thu tiền, thử replay.
4. Tạo/sửa/toggle voucher tại đúng cơ sở, không đổi usedCount.
5. Mở custom range thống kê và đối chiếu fixture tính trước trong báo cáo kiểm chứng.
6. Mở lại ứng dụng và kiểm tra danh sách venue từ server; kiểm thử hồ sơ mới không đổi OWNER/APPROVED hoặc cơ sở cũ.
7. Chạm bản đồ thật chọn ghim, xoay dialog và giữ tọa độ; lưu rồi đọc lại GeoPoint từ server, hủy tọa độ ngoài Việt Nam không ghi thay đổi. Kiểm tra cold start, đổi OWNER/PLAYER, deep link và Back bằng APK local trong runner.

[Bằng chứng và phần chưa xác minh](../../docs/validation/feature11-2026-11-21/README.md). Phân biệt unit/Emulator, physical instrumentation, camera scan thật, process recreation và force-stop/cold start. Không dùng protocol usability thay kết quả khảo sát hoặc khẳng định SLA 4G khi chưa đo.
