# Kiểm chứng nguồn và repository trên vivo, 24/11/2026

## Backend và Android

- O2/RR1: 13 ca fake đạt. Auth/Firestore Emulator xác minh hai tick hoàn tất đồng thời chỉ tạo một REVIEW_REMINDER, replay thu hồi sau tái duyệt giữ OWNER và dọn journal khi xóa tài khoản. Kiểm thử claims quan sát lệnh đồng bộ qua fake Auth writer. [Log emulator](source-device-2026-11-24/source-emulator.log)
- Hồi quy delivery và các nguồn đã duyệt: 65 ca fake đạt trước khi nối D4. [Log](source-device-2026-11-24/backend-regression.log)
- D4 và delivery hồi quy: 37 ca fake đạt, gồm tách người nhận đăng ký/hàng chờ, replay đổi giờ/hủy, payload xung đột và bỏ qua sửa ghi chú. Firestore Emulator buộc nhánh snapshot với 101 UID, hai trang và hai tick đồng thời, xác nhận mỗi UID còn hoạt động nhận đúng một job, UID không có profile bị bỏ qua, dọn snapshot sau retention. [Log fake](source-device-2026-11-24/dropin-fake.log), [log emulator](source-device-2026-11-24/source-with-dropin-emulator.log)
- APK cô lập và APK instrumentation build thành công. Bộ ViewModel quản trị đạt 10 ca, gồm giữ UUID qua lỗi và dùng UUID mới sau thành công. [Log build và unit test](source-device-2026-11-24/android-build.log)
- Sau khi thêm requestId cho drop-in, `assembleDebug` biến thể cô lập đạt và 20 ca Android về admin/owner drop-in đạt. Hành trình gọi Edge Function đổi giờ, hủy buổi hoặc thu hồi trên điện thoại còn cần Supabase local. [Log build cuối](source-device-2026-11-24/dropin-android-build.log)

## Điện thoại thật

- Vivo V2247, ứng dụng `.adminlocal`, Auth/Firestore Emulator project `demo-admin-repair` qua USB reverse. Repository và route resolver production dùng Firebase SDK thật với hai tài khoản demo. Ca kiểm thử đọc 30+1 mục theo thứ tự, tải theo ID ngoài trang đầu, lưu đọc từng mục và đọc tất cả, đổi preference, resolve booking khi còn quyền và trả fallback khi nguồn bị xóa hoặc đổi chủ. Sau logout/đổi UID, kiểm tra inbox/cursor cũ bị loại và preference tách riêng. Lần đầu Rules từ chối fixture chưa khởi tạo. Gán claim PLAYER trong Auth Emulator rồi chạy lại đạt 1/1. [Log đạt](source-device-2026-11-24/device-repository.log)
- Smoke UI phone/light dùng fixture riêng. Kiểm tra chạm thông báo ngoài trang đầu, chi tiết dự phòng, sai UID, logout/đổi tài khoản và settings. Lần đầu Espresso nhầm cửa sổ Activity lúc dialog đang có focus. Bài test chuyển sang kiểm tra và đóng dialog qua cửa sổ accessibility đang hiển thị. Chạy lại đạt 12/12. [Log đạt](source-device-2026-11-24/phone-light/instrumentation.log), [log lỗi ban đầu](source-device-2026-11-24/phone-light-initial-failure.log).
- [Inbox](source-device-2026-11-24/phone-light/notification-393dp-light-inbox.png) và [settings](source-device-2026-11-24/phone-light/notification-393dp-light-settings.png). Hai mẫu hiện hành giữ nguyên vì lượt này chỉ sửa test UI. Bằng chứng bốn cấu hình ở lượt trước: [ma trận đã lưu](continuation-2026-11-24.md).
- Cuối lượt thiết bị có density 440, quyền của app cô lập đã được khôi phục và không còn USB reverse do harness mở. [Trạng thái cuối](source-device-2026-11-24/device-final.txt)

Ca repository dùng transport device-to-emulator. Ca tap dùng deep link và fixture cục bộ. Không có phép đo nhận push qua FCM. Docker không mở được pipe `dockerDesktopLinuxEngine`; Supabase local, cron SQL và hành trình đầy đủ T056 vẫn chưa chạy được.
