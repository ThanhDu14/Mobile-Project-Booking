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

Edge Function dùng service account bypass Rules, do đó phải xác minh nguồn, recipient, type và field trước khi ghi. `firestore.rules` hiện chỉ có helper chung, chưa có match notification/user; phối hợp sửa file chia sẻ. CSDL §7 ghi Admin C notifications trong khi §4.13/120 mô tả server-authoritative creation; cần thống nhất trước khi code.

