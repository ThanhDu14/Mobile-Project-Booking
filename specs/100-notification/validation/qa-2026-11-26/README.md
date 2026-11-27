# Kiểm thử hồi quy Feature 100 trên vivo V2247, 26–27/11/2026

Bản nguồn `462a5891` trên `feat/100-notification`, cùng hai bản sửa Settings và các ca hồi quy trong working tree. APK `com.example.booking_app.adminlocal` và APK instrumentation dùng tài khoản demo. Auth/Firestore Emulator chạy trên máy phát triển, nối với SDK Android qua USB reverse. [SHA-256 hai APK](apk-sha256.txt)

## Kết quả trên điện thoại

- SDK đạt 3/3 hành trình. Core bao phủ 65 mục inbox, các route đã duyệt, fallback khi mất nguồn/quyền, offline qua `FirebaseFirestore.disableNetwork()`, kết nối lại và tách UID. F1 và P2/B1 dùng producer ứng với dữ liệu được đọc trên điện thoại. Những họ producer còn lại chạy với Firebase Emulator ở backend. [Core](sdk-core-final.log) · [F1](sdk-group-final.log) · [P2/B1](sdk-payment-final.log) · [Mốc SDK](sdk-milestones.log)
- UI đạt 52/52 ca với repository giả, 13 ca mỗi cấu hình. Đối chiếu hai wireframe theo bố cục, thứ tự nhóm, chỉ báo chưa đọc, checkbox bị khóa của BOOKING_NEW, màu sáng/tối và các ngoại lệ đã duyệt. w600dp được chạy ở 720dp trên cùng điện thoại. [Phone sáng](phone-light/instrumentation.log) · [Phone tối](phone-dark/instrumentation.log) · [w600dp sáng](tablet-light/instrumentation.log) · [w600dp tối](tablet-dark/instrumentation.log)
- 9 ca quyền, channel và payload injection đạt. `PendingIntent` từ bản ghi Android đã đăng mở chi tiết và đánh dấu đã đọc ở foreground, background và khi khởi tạo lại Activity. Bộ tự động hóa mở được SystemUI nhưng chưa tìm thấy mục demo để chạm trực tiếp, nên T056 vẫn mở. Việc truyền/nhận qua FCM thật nằm ngoài đợt QA này. [Local injection](push-permission/channels-renderer-handler.log) · [PendingIntent](posted-pending-intent-final.log) · [Khay SystemUI](shade-touch-final.log)
- Lưu 40 ảnh. Cuối lượt, density về 440, các cờ quyền POST_NOTIFICATIONS khớp trạng thái ban đầu và USB reverse dùng khi kiểm thử đã được gỡ. [Trước](device-before.txt) · [Sau](device-after.txt)
- APK cô lập build thành công. JVM ghi 1.702 ca, 2 bỏ qua, 0 failure/error. Lint ghi 0 lỗi và 851 cảnh báo. [Build và lint](android-build-unit-lint.log) · [APK instrumentation](android-final-test-apk.log) · [Tổng hợp JUnit](android-unit-summary.txt)

## Ma trận yêu cầu

| Yêu cầu | Ca kiểm thử | Lớp bằng chứng |
|---|---|---|
| FR-003–005, FR-009, FR-015 | Mới nhất trước, 65 mục qua ba trang 30/30/5, đọc một, đọc tất cả qua trang chưa tải, rỗng, lỗi rồi retry, giao diện chỉ có thao tác đọc | [SDK thật](sdk-core-final.log), UI với repository giả, [Rules](firestore-rules.log) |
| FR-006–009, FR-012 | Nhóm hỗn hợp, mỗi loại lưu riêng, `BOOKING_NEW` luôn bật, `PROMOTION` mặc định tắt, cache chỉ đọc khi offline và tải lại từ server khi kết nối | [SDK thật](sdk-core-final.log), UI với repository giả, unit và Rules |
| FR-006–008 | Cấp/từ chối quyền, channel, payload tối thiểu, nội dung chung, mở từ khay khi foreground, background và mở lại Activity | Local payload injection và UI trên vivo, worker với fake sender |
| FR-004, FR-013 | Đích còn quyền, nguồn bị xóa, membership bị thu hồi, chi tiết lưu sẵn, từ chối ID/cursor của UID trước | SDK thật [core](sdk-core-final.log), [F1](sdk-group-final.log), [P2/B1](sdk-payment-final.log), unit route, UI và Rules |
| FR-007, FR-011, FR-014 | Replay, actor/sender bị loại theo quyết định nguồn, hai worker, retry/token lỗi, inbox vẫn lưu khi muted/đang xem và khi gửi thất bại | [Fake backend](backend-fake.log), [worker emulator](worker-emulator.log), [tích hợp producer](backend-integration.log) |
| FR-004, FR-016 | Logout/đổi tài khoản, xóa cache, hủy hoặc từ chối thông báo cũ, xóa account với 405 inbox và 205 token receipt | SDK thật, unit, UI và [account deletion emulator](account-deletion.log) |
| UI | Inbox và settings theo PNG gốc, light/dark, phone và w600dp trên cùng điện thoại đổi density | Ảnh PNG và instrumentation theo cấu hình |

## Danh mục producer và route

| Type | Producer và route đã triển khai | Bằng chứng của lượt này |
|---|---|---|
| `BOOKING_CONFIRMED`, `BOOKING_REJECTED`, `BOOKING_CANCELLED`, `BOOKING_REMINDER` | W1/B2, chi tiết booking sau khi kiểm tra UID | Nguồn/eligibility/replay qua Firestore Emulator, SDK vivo mở booking xác nhận rồi kiểm tra xóa và mất quyền |
| `BOOKING_NEW` | B1 từ checkout PENDING, mở chi tiết đã lưu theo lựa chọn đã duyệt | Checkout/replay và inbox OWNER bằng SDK vivo, giao dịch/rollback qua Emulator |
| `DROPIN_REGISTERED`, `DROPIN_SPOT_OPENED`, `DROPIN_CHANGED`, `DROPIN_WAITLIST_CLOSED` | D3, D2-W1 và D4, chi tiết session sau kiểm tra source | Producer, đăng ký, waitlist, đổi/hủy và loại người nhận sai qua Emulator |
| `PROMOTION` | P2, chi tiết đã lưu theo lựa chọn đã duyệt | Voucher follower và fallback bằng SDK vivo, lượt RESERVED/USED, eligibility, cron và replay qua Emulator |
| `REVIEW_REPLIED`, `REVIEW_NEW`, `REVIEW_REMINDER` | R1/RR1, tab review của sân hoặc màn viết review theo loại, chi tiết đã lưu cho `REVIEW_NEW` | Producer, tạo lại, reply đầu, hoàn tất booking và replay qua Emulator |
| `OWNER_APPROVED`, `OWNER_REJECTED`, `OWNER_REVOKED` | D2-W1/O2, chi tiết đã lưu theo contract | Producer và replay qua fake/Emulator, gồm replay revoke sau khi duyệt lại |
| `GROUP_ACTIVITY`, `CHAT_MESSAGE` | F1/H1, nhóm/chat có quyền và fallback khi mất quyền, chi tiết đã lưu khi đích sự kiện bị xóa | Vivo qua lệnh group-post/chat-send/group-manage, Emulator cho bảy subtype, nhóm lớn, direct chat và suppression |
| `SYSTEM` | S1, chi tiết đã lưu | Producer qua Emulator cho tài khoản ACTIVE đủ role và fake FCM lỗi, UI với payload cục bộ |

## Supabase local

Hai cron gọi Deno handler qua `pg_cron`/`pg_net`. Lượt fixture có booking CONFIRMED còn 100 phút và một fake token. Hai lần quét reminder tạo đúng một inbox `BOOKING_REMINDER`. Delivery ghi một job `SENT` và một token receipt `SENT` có `acceptedAt`. Cả bốn lần gọi trả HTTP 200. Đã xóa fixture và xác nhận còn 0 cron/Vault key tạm. [Lượt có dữ liệu](supabase-cron-seeded.log) · [Lượt không có job](supabase-cron.log)

## Lỗi tái hiện và cách kiểm tra lại

- Settings offline hiển thị lỗi dù đã có DataStore cache. SDK `get(Source.SERVER)` trả Firestore `UNAVAILABLE`, lớp chuyển đổi lỗi trả `AppError.Unknown` nên repository bỏ qua nhánh đọc cache. Ánh xạ `UNAVAILABLE` sang `Network`, `DEADLINE_EXCEEDED` sang `Timeout` và giữ lỗi quyền ở nhánh lỗi hiện có. [Tái hiện trên vivo](sdk-offline-reproduction.log)
- Khi SDK tắt mạng theo `disableNetwork()`, tải Settings đã dùng cache nhưng transaction trực tiếp vẫn ghi được. Repository nay yêu cầu đọc profile từ server thành công trước khi bắt đầu transaction và kiểm tra lại UID. Transaction tiếp tục cập nhật riêng các key được chọn. [Tái hiện thao tác ghi](sdk-offline-write-reproduction.log)
- Harness quyền dừng khi `adb pull` ghi tiến độ ảnh vào stderr trong PowerShell. Tách stdout/stderr và kiểm tra mã thoát của từng lần pull để giữ đủ bốn ảnh và báo đúng ảnh gặp lỗi.
- Một lượt gom bộ tích hợp chạy 116 ca đạt và hai ca worker lỗi do dùng chung project với dữ liệu của các ca nguồn. Ca worker dùng hàng đợi toàn project và chạy đạt 2/2 trong project demo riêng. Bộ xóa tài khoản đạt 21/21 sau khi cấu hình project test trùng project Auth Emulator. [Lượt gom](backend-integration.log) · [Worker](worker-emulator.log) · [Xóa tài khoản](account-deletion.log)

## Các bộ kiểm thử backend và Rules

| Bộ | Kết quả |
|---|---|
| Firestore Rules cho inbox, delivery, profile, chat và review | 61 đạt. [Log](firestore-rules.log) |
| Fake worker, W1, SYSTEM, owner/admin và waitlist | 65 đạt. [Log](backend-fake.log) |
| Checkout, voucher và worker | 63 đạt, có ca worker trùng với bộ fake. [Log](checkout-and-delivery.log) |
| Producer và tích hợp Firebase Emulator | 116 đạt trong lượt gom. D1/SYSTEM đạt 2/2 trong project riêng. [Nguồn](backend-integration.log) · [D1/SYSTEM](worker-emulator.log) |
| Producer group-manage và group-post | 55 đạt. [Log](group-producers.log) |
| Xóa tài khoản Firebase Emulator | 21 đạt. [Log](account-deletion.log) |
