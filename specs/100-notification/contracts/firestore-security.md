# Contract: Firestore security và notification access

Yêu cầu Rules, chưa phải Rules code. Nguồn: DB README §4.13/§7, spec 100 FR-004/005, constitution II.

| Actor | Read | Create | Update | Delete |
|---|---|---|---|---|
| Signed-in user, own uid | Chỉ `users/{uid}/notifications/{id}` của mình | Deny | Chỉ document của mình, affected keys chỉ `isRead` | Deny |
| User khác / unauthenticated | Deny | Deny | Deny | Deny |
| Admin client | Không đọc xuyên user | Deny mặc định; SYSTEM gửi qua server trong khi chờ giải quyết DB §7 | Chỉ `isRead` trên hộp thư mình | Deny |
| Trusted server/Edge Function | Server access | Tạo sau khi xác thực event/recipient/type | Không có update field được định nghĩa; không suy diễn schema delivery | Account deletion theo lifecycle server |

User preference write tách riêng trên `users/{uid}` và chỉ được sửa `notificationPrefs`, bảo toàn field hồ sơ khác. Token write theo `fcmTokens` và lifecycle đã thống nhất. Exact profile `diff().affectedKeys()` phải được tích hợp với Rules user hiện có, không ghi đè quy tắc nhóm khác.

## Emulator test cases

- Chủ đọc được notification của mình; uid khác và unauthenticated bị từ chối.
- Chủ cập nhật `isRead`; không thể đổi title/body/type/data/createdAt, create/delete.
- User khác không thể mark-read hoặc mutate.
- User chỉ sửa preferences của mình; không sửa hồ sơ protected/uid khác.
- Token chỉ được ghi cho tài khoản mình theo lifecycle được chốt.
- Admin client không giả mạo SYSTEM item trừ khi contract DB/120 được duyệt rõ.

Edge Function dùng service account bypass Rules, do đó phải xác minh nguồn, recipient, type và field trước khi ghi. Rules hiện đã có own-user inbox/isRead và preference validation, được kiểm thử trên emulator; mô tả discovery chỉ có helper chung không còn phản ánh code hiện tại. CSDL §7 vẫn ghi Admin C notifications trong khi §4.13/120 mô tả server-authoritative creation; T007 giữ mở cho phần quyền này.

## Feature 011 token handoff audit, 23/11/2026

011 research R5, contracts/ui-screens.md và security-rules P15 xác nhận logout dùng FcmSignOutHook: arrayRemove đúng token máy hiện tại trước khi mất auth, rồi gọi FirebaseMessaging.deleteToken; không xóa token thiết bị khác. ACCOUNT_DELETED bỏ qua authenticated hooks, server xóa token/profile và local session cleanup vẫn chạy. Không đăng ký hook FCM thứ hai ở Feature 100.

Code bỏ qua lỗi thông thường và SessionManager giới hạn tổng hook năm giây; việc deleteToken được thử gọi không chứng minh mọi lần rotate/provider invalidation đã thành công. Chưa có contract đủ cho đăng ký/refresh, chuyển UID sau detach thất bại, chính sách quyền OS, kích thước/format token list hay giới hạn add/remove. T011/T051/T052 vẫn partial. Chi tiết lựa chọn và bằng chứng nằm trong ../decision-review-2026-11-23.md; không đổi Rules token theo suy đoán.

