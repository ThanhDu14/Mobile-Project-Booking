# Tích hợp tin nhắn OWNER — 091/112

Ngày kiểm tra: 21/11/2026, giờ Việt Nam. Thiết bị thật: vivo V2247, Android 15; ứng dụng `com.example.booking_app`, bản debug. Tài khoản PLAYER và OWNER do người dùng đăng nhập; chỉ gửi nội dung kiểm thử trong chat của cơ sở `AUDIT151126` đã được cho phép.

Đã nối tab OWNER với module chat thật, xác minh trao đổi hai chiều bằng cách luân phiên tài khoản trên một điện thoại. Chưa xác minh giao nhận FCM vì thiếu phần triển khai thông báo của feature 100 trong repository.

## Yêu cầu, sai lệch và kết quả

| Phần | Sai lệch ban đầu | Thay đổi | Xác minh |
|---|---|---|---|
| 112 tab Tin nhắn → 091 OwnerInbox | Tab chỉ hiện “Tin nhắn đang được phát triển” | Nối vào destination `ownerChatListFragment` qua deep link hiện có; giữ menu OWNER và tab được chọn | Máy thật: mở từ tab, chọn khách, Back về danh sách, chuyển tab và mở lại; cold/warm deep link |
| 091 danh sách OWNER | Thiếu subtitle, bộ lọc và chú thích phân biệt chế độ | Thêm Tất cả/Chưa đọc/Cơ sở; dùng `ChatSummary` thật và cùng listener; lưu lựa chọn theo tài khoản; empty riêng khi bộ lọc không có kết quả | Máy thật: danh tính khách, AUDIT, tin cuối, giờ, unread; lọc cơ sở; Chưa đọc 1 → mở chat → Chưa đọc 0/rỗng; JVM kiểm tra không tăng listener |
| 091 badge | MainActivity chỉ gắn badge cho Cộng đồng PLAYER; số cũ có thể còn khi đổi tài khoản | Badge theo menu OWNER/PLAYER; xóa badge của menu cũ; phát 0 ngay khi thay UID | Máy thật: badge OWNER 1, PLAYER 1, biến mất sau đọc; JVM kiểm tra đổi UID và callback cũ |
| 091 phiên/draft/media | Conversation giữ header, draft, ảnh chọn và URL của phiên trước; việc bất đồng bộ có thể hoàn tất muộn | Hủy công việc theo phiên, xóa state/cache/event cũ, kiểm tra cancellation trước khi áp dụng kết quả | JVM: sign-out xóa header/draft/ảnh/URL; máy thật: luân phiên tài khoản, header/ô nhập đổi đúng Khách hàng/Chủ sân |
| 091 outbox | Khi không có UID vẫn coi tin cũ là của phiên hiện tại; sau upload chưa kiểm tra lại tài khoản trước gửi | Chỉ xử lý entry có senderId đúng UID hiện tại; kiểm tra lại trước gửi và sau các lần chờ | Hai test tái hiện lỗi trước sửa, đạt sau sửa; các test offline/retry/restart hiện có vẫn đạt |
| 091 lỗi phân quyền inbox | Query bị từ chối được biến thành danh sách rỗng | Trả lỗi `NOT_PARTICIPANT` để UI hiển thị lỗi/retry | Test repository tái hiện trước sửa, đạt sau sửa; Rules không đổi |
| 030 PLAYER nhắn OWNER | Đường mở chat đã có, cần xác nhận tích hợp | Tái sử dụng nguyên module/repository/API và ID chat hiện có | Máy thật: trang chi tiết AUDIT → nhắn chữ + ảnh → OWNER nhận và trả lời → PLAYER thấy phản hồi |
| 011 chuyển chế độ | Cần tách chat khách hàng với chat ở tư cách người chơi | Giữ hợp đồng lọc hiện có; reset state không phù hợp khi đổi tài khoản | Máy thật: cùng tài khoản OWNER chuyển PLAYER thấy hộp thư rỗng, chuyển OWNER thấy chat khách hàng; JVM kiểm tra scope hai chế độ |
| 100 thông báo | Handoff có envelope nhưng repo thiếu `send-notification` và dịch vụ nhận FCM Android | Giữ envelope và deep link hiện có; không xây lại toàn bộ 100 | Deno C8 xác minh envelope/suppression/idempotency; ADB kiểm tra deep link nóng/lạnh. Chưa kiểm tra bấm thông báo FCM thật |

Ảnh trước sửa: [before-owner-tab.png](screenshots/before-owner-tab.png). Ảnh giao diện mới: [owner-inbox-content.png](screenshots/owner-inbox-content.png), [owner-reply.png](screenshots/owner-reply.png).

## Backend và dữ liệu thật

- `FirestoreChatRepository`: chat, messages, participants, lịch sử realtime, unread/read. Project hiện tại `mobile-22e6c`.
- `SupabaseChatActionApi`, `SupabaseChatSendApi`, `SupabaseChatMediaRepository`: gọi `chat-action`, `chat-send`, `chat-media-url`; ảnh trong bucket private `chat-media` của backend hiện tại.
- Bỏ placeholder ở tab OWNER để dùng các thành phần này. Không tạo repository chat riêng, schema song song, dữ liệu giả hay ghi trùng hai backend. Không thêm migration/deploy và không sửa Rules/RLS vì tích hợp dùng hợp đồng đã triển khai.
- [backend-before-owner-read.json](backend-before-owner-read.json): 1 chat, 2 tin PLAYER, unread OWNER = 2.
- [backend-after-owner-reply.json](backend-after-owner-reply.json): 3 tin, unread OWNER = 0, PLAYER = 1.
- [backend-final.json](backend-final.json): đúng 1 chat DIRECT, 4 message ID khác nhau, đều `NORMAL`; 3 tin từ PLAYER gồm ảnh và tin offline, 1 phản hồi OWNER; unread hai phía = 0. Chỉ lưu metadata kiểm thử, không lưu nội dung chat/token/signed URL.
- Ảnh TEST được upload và hiển thị được ở cả hai tài khoản. HEAD không đăng nhập qua endpoint public cho ảnh đó trả HTTP 400, không tải được ảnh private. Quyền media của người ngoài còn được kiểm tra trong Deno M1/M2.
- Tin TEST được giữ lại làm bằng chứng; không chỉnh sửa các cuộc trò chuyện cá nhân hay dữ liệu booking. Ảnh tải lên chỉ là giao diện hộp thư rỗng của lần kiểm tra này.

## Thao tác trên điện thoại

| Trường hợp thực tế | Kết quả/bằng chứng |
|---|---|
| OWNER chưa có chat | [owner-inbox-empty.png](screenshots/owner-inbox-empty.png) |
| PLAYER mở từ chi tiết cơ sở và gửi chữ | [player-sent-text.png](screenshots/player-sent-text.png); backend có timestamp server |
| PLAYER chọn ảnh TEST từ thư viện và gửi | [player-sent-image.png](screenshots/player-sent-image.png); OWNER hiển thị đúng ảnh trong [owner-reply.png](screenshots/owner-reply.png) |
| OWNER trả lời, PLAYER nhận | [player-unread-reply.png](screenshots/player-unread-reply.png): badge 1 và preview phản hồi; mở chat xong backend PLAYER = 0 |
| Mất mạng khi gửi | [player-offline-pending.png](screenshots/player-offline-pending.png): banner mất mạng và Đang gửi; [player-offline-recovered.png](screenshots/player-offline-recovered.png): tự gửi thành công, không còn pending; backend chỉ thêm 1 tin |
| Restart/deep link lạnh | [player-cold-start-history.png](screenshots/player-cold-start-history.png): vẫn đủ lịch sử/ảnh/phản hồi/tin offline |
| Cùng OWNER chuyển sang PLAYER | [same-owner-player-mode-empty.png](screenshots/same-owner-player-mode-empty.png): chat khách hàng không xuất hiện; chuyển lại OWNER vẫn có chat |
| Badge OWNER và bộ lọc | [owner-unread-tab-badge.png](screenshots/owner-unread-tab-badge.png), [owner-unread-filter.png](screenshots/owner-unread-filter.png): 1 chat chưa đọc |
| Sau OWNER đọc | [owner-read-clears-filter-and-badge.png](screenshots/owner-read-clears-filter-and-badge.png): badge biến mất, Chưa đọc 0 và empty phù hợp |

Đã đối chiếu hai ảnh OwnerInbox/OwnerChat trong `specs/091-chat/ui/` và design system hiện có: subtitle, bộ lọc, danh tính khách, cơ sở gần nhất, preview, thời gian, unread và chú thích chế độ. Không thêm số booking/banner nghiệp vụ minh họa khi chưa có dữ liệu/hợp đồng tương ứng. Trên máy, nội dung bộ lọc không bị cắt; ô nhập và nút gửi nằm trên bàn phím. Bàn phím tiếng Việt biến đổi một số nhãn ASCII (`TEST`/`OFFLINE`); đây là các tin có marker `211126`, được đối chiếu bằng ID và timestamp backend.

Wi-Fi và mobile data trước kiểm tra đều bật; đã khôi phục và đọc lại cả hai bằng 1. Script offline ghi được ảnh và khôi phục trong `finally`; PowerShell báo `NativeCommandError` do dòng tiến độ `adb pull` đi qua stderr dù file tải thành công. Kết luận offline dựa trên ảnh, UI sau hồi mạng và dữ liệu server, không dựa trên exit code script đó.

Kết thúc ở tài khoản OWNER, hộp thư chọn Tất cả. [Logcat cuối phiên](logs/device-runtime-filtered.log) không có dòng khớp AndroidRuntime:E/Firestore:W trong cửa sổ 2000 dòng gần nhất; đây là snapshot cuối phiên, không phải thu liên tục toàn bộ phiên.

## Test tự động và build

| Kiểm tra | Kết quả | Log |
|---|---|---|
| Regression trước sửa: badge/session | 35 test, 2 lỗi đúng ca mới | [session-regression-before.log](logs/session-regression-before.log) |
| Regression trước sửa: outbox/permission | 26 test, 3 lỗi đúng ca mới | [privacy-regression-before.log](logs/privacy-regression-before.log) |
| JVM liên quan, bao gồm chat, cache media dùng chung và một số ca nhắn từ chi tiết sân | 105 passed, 0 failed/error/skipped | [unit-test-results.json](unit-test-results.json), [android.log](logs/android.log) |
| `:app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL; lint 0 errors, còn 849 warnings trên repository | [android.log](logs/android.log) |
| Deno chat trên Auth/Firestore Emulator | 25 passed, 0 failed/ignored; có type check | [deno.log](logs/deno.log) |
| Firestore Rules `test/chats.test.ts` | 15 passed, không skip | [firestore-rules.log](logs/firestore-rules.log) |

Lệnh Android: `gradlew.bat :app:testDebugUnitTest --tests '*chat*' --tests '*SignedUrlCacheTest' :app:assembleDebug :app:lintDebug --console=plain`.

Deno chạy `_shared/chats.test.ts`, `chat-action/index.test.ts`, `chat-send/index.test.ts`, `chat-media-url/index.test.ts` bằng `deno test -A --config supabase/functions/deno.json`. Firebase Emulator riêng dùng Firestore `127.0.0.1:28080`, Auth `127.0.0.1:29199`, project `demo-smashnow`; Rules chạy tuần tự sau Deno, rồi Emulator được dừng. Không chạy test tự động vào hosted backend. Các handler tests dùng Firebase Emulator và dependency fixture của bộ test hiện có; chúng không thay thế bằng chứng upload/download Storage thật trên điện thoại.

Deno đã kiểm tra: một chat/cặp kể cả nhiều cơ sở, cập nhật venue gần nhất, mở lại khi venue khóa/OWNER hết quyền, membership, idempotency, blocked/read-only, giới hạn nội dung, quyền media, recall/report và envelope thông báo. JVM kiểm tra phân trang, offline timeout/retry/restart, listener và trạng thái lỗi. Các trường hợp này không đồng nghĩa đã thao tác tất cả trên điện thoại.

## File thay đổi

Các đường dẫn dưới đây tính từ repository root:

- `app/src/main/java/com/example/booking_app/MainActivity.kt`: tab, selected state và badge OWNER.
- `app/src/main/java/com/example/booking_app/core/media/SignedUrlCache.kt`: chặn cache kết quả của coroutine đã bị hủy.
- `app/src/main/java/com/example/booking_app/feature/chat/data/UnreadChatsReaderImpl.kt`: reset badge khi thay tài khoản.
- `app/src/main/java/com/example/booking_app/feature/chat/data/FirestoreChatRepository.kt`: lỗi quyền query inbox.
- `app/src/main/java/com/example/booking_app/feature/chat/data/ChatOutbox.kt`: scope sender và kiểm tra tài khoản sau upload/chờ.
- `app/src/main/java/com/example/booking_app/feature/chat/ui/conversation/ChatViewModel.kt`: công việc theo phiên và xóa state riêng tư.
- `app/src/main/java/com/example/booking_app/feature/chat/ui/list/ChatListViewModel.kt`: bộ lọc OWNER dùng dữ liệu hiện có và reset theo tài khoản.
- `app/src/main/java/com/example/booking_app/feature/chat/ui/list/ChatListFragment.kt`: header, chips, empty và footer OWNER.
- `app/src/main/res/layout/fragment_chat_list.xml`, `app/src/main/res/values/strings_chat.xml`: UI và nội dung tương ứng.
- `app/src/test/java/com/example/booking_app/feature/chat/data/{ChatOutboxTest,FirestoreChatRepositoryTest,UnreadChatsReaderImplTest}.kt`: hồi quy dữ liệu/quyền/đổi tài khoản.
- `app/src/test/java/com/example/booking_app/feature/chat/ui/conversation/ChatViewModelTest.kt`, `app/src/test/java/com/example/booking_app/feature/chat/ui/list/ChatListViewModelTest.kt`: state riêng tư và lọc inbox.
- `specs/112-owner-operations/tasks.md`: thêm và theo dõi T069–T074 cho đợt tích hợp.
- `docs/validation/owner-chat-2026-11-21/`: báo cáo, metadata, ảnh và log đã lọc.

## Giới hạn còn lại

- Feature 100: repository thiếu `send-notification` và dịch vụ nhận FCM Android. Handoff 091 mục 2 chốt `{type: CHAT_MESSAGE, recipientUid, data: {chatId, messageId}}`; C8 xác minh đúng envelope. Cần triển khai phần phụ thuộc 100 rồi kiểm tra bấm thông báo thật khi ứng dụng mở/đóng. Không báo giao nhận FCM đã đạt.
- Một thiết bị: đã kiểm tra luân phiên hai tài khoản và đối chiếu backend; chưa đo SLA realtime với hai máy mở chat đồng thời.
- Chưa kiểm thử tay lịch sử trên 30 tin/phân trang, timeout mất mạng đủ 60 giây, từ chối camera, thao tác report/recall/block, xóa/khóa tài khoản hoặc thu hồi OWNER thật. Các ca tự động liên quan được ghi riêng ở trên; không thực hiện thay đổi phá hủy trên tài khoản đang dùng.
- Nhiều cơ sở cùng OWNER/cập nhật cơ sở gần nhất được xác minh bằng Deno D1/D2; trên hosted chỉ thao tác cơ sở TEST AUDIT, không thay ngữ cảnh chat sang cơ sở legacy.
- Không sửa checklist reviewer, không commit/push và không chạy refactor ngoài phạm vi tích hợp. Những tiêu chí SLA/usability còn mở trong 091 không được đánh dấu hoàn tất bởi báo cáo này.
