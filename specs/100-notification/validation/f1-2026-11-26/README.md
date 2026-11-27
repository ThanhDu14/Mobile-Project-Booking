# Kiểm chứng Feature 100 ngày 26/11/2026

## Backend

- F1 đạt 70 ca producer và emulator. Phủ bảy subtype, phân trang cho 505 thành viên, xóa sự kiện với 501 người tham dự, replay, quyền hiện tại, loại actor, hai worker đồng thời, rollback và fake FCM retry. Muted và viewer vẫn có inbox. [Log F1](f1-producers.log)
- P2/B1 đạt ba ca emulator, gồm bộ đếm lượt RESERVED, loại tài khoản/follower không đủ điều kiện, phát đến hạn đồng thời, checkout HOLD → PENDING đồng thời và rollback. Bộ hồi quy checkout và worker đạt 86 ca. D4, B2 và F1 đạt thêm 19 ca chồng lấp. [P2/B1](p2-b1-emulator.log) · [Payment và worker](payment-and-worker.log) · [D4/B2](d4-b2-and-f1.log)
- Firestore Rules đạt 52 ca. Build APK cô lập, APK instrumentation và unit test booking/community/notification thành công. [Rules](firestore-rules.log) · [Gradle](android-build-and-unit.log)
- Supabase local chạy hai lượt cho mỗi cron qua pg_cron/pg_net, đến handler demo với fake sender, HTTP 200 cả bốn lượt. Sau kiểm thử, số cron và Vault key tạm còn lại bằng 0. [Cron local](supabase-cron.log)

## Điện thoại thật

Vivo V2247 chạy 48 ca UI qua bốn cấu hình phone/w600dp sáng/tối. Chín ca quyền, channel và local payload injection đạt. Tap notification, chi tiết đã lưu và chuyển tài khoản có kiểm thử qua UI cục bộ. Thư mục lưu 28 screenshot, gồm 24 ảnh ma trận UI và bốn ảnh quyền. [Phone sáng](phone-light/instrumentation.log) · [Phone tối](phone-dark/instrumentation.log) · [w600dp sáng](tablet-light/instrumentation.log) · [w600dp tối](tablet-dark/instrumentation.log) · [Local payload](push-permission/channels-renderer-handler.log)

Lượt SDK P2/B1 trên vivo đạt 1/1. Firebase Auth và Firestore SDK đi qua USB reverse. Producer demo tạo ưu đãi và checkout qua HTTP cục bộ, sau đó app đọc inbox bằng repository thật. Log xác nhận voucher của follower, replay checkout, chi tiết đã lưu và cô lập tài khoản PLAYER/OWNER. [Instrumentation](device-producers-payment.log) · [HTTP demo](device-http-payment.log) · [Mốc SDK](device-acceptance.txt)

Lượt F1 lúc 16:30 ghi mốc hoàn thành assertion cho producer, replay, route nhóm/chat, fallback khi mất quyền và cô lập UID. Lượt chạy lại thay fixture thu hồi quyền bằng lệnh group-manage đã nhận inbox nhóm/chat qua SDK. Instrumentation của lượt chạy lại bị dừng khi chờ kết quả, nên bằng chứng mất quyền trên thiết bị dùng mốc 16:30. Backend emulator kiểm tra trực tiếp lệnh group-manage cùng quy tắc quyền và fanout. Cuối lượt, density ở 440, USB reverse thử nghiệm đã gỡ và cờ quyền thông báo đã khôi phục. [Mốc SDK](device-acceptance.txt) · [Lượt chạy lại](device-producers-group.log) · [Thiết bị sau kiểm thử](device-final.txt) · [Quyền](push-permission/device-after.txt)

## Production

Bảy Edge Function đã deploy và xác minh ACTIVE. Firestore indexes đã deploy. Rules production chỉ áp dụng phần notification, token và presence để giữ tương thích với các APK còn dùng checkout trực tiếp. Hai migration cron đã ghi vào lịch sử production, cả hai job có `active=false` và endpoint trả 401 khi thiếu secret. SePay webhook được kiểm thử local. Rollout checkout APK và thu hồi quyền ghi PENDING của APK cũ trên production vẫn cần triển khai đồng bộ. [Edge Function](hosted-functions-verified.json) · [Indexes](hosted-indexes-deploy.log) · [Rules](hosted-rules-deploy.log) · [Migrations](hosted-migrations-verified.log) · [Cron](hosted-cron-verified.log)
