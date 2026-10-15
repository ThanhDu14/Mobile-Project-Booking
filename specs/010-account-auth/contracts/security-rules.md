# Contract: Security Rules cho `users/{uid}`

**File:** `firestore.rules` (Rules nền đã có từ PR #22). **Test:** `firestore-tests/test/users.test.ts` (Vitest và `@firebase/rules-unit-testing`, project `demo-smashnow`).

## 1. Sửa Rules nền

Hai thay đổi, ảnh hưởng cả nhóm ([research R10](../research.md#r10-security-rules-cho-usersuid)):

```text
// Trước: thiếu claim thì coi là PLAYER, nên tài khoản chưa qua on-signup vẫn có quyền người chơi.
function appRole() {
  return isSignedIn() ? request.auth.token.get('appRole', null) : null;
}

// Đã qua on-signup: có claim appRole. Dùng thay isSignedIn() cho mọi quyền của người dùng.
function isInitialized() {
  return appRole() != null;
}

function isPlayer() { return appRole() == 'PLAYER'; }   // thêm cho đủ bộ
```

`isSelf(uid)` giữ nguyên. Collection nào đang dùng `isSignedIn()` cho quyền ghi của người dùng thì nên đổi sang `isInitialized()`. Báo cả nhóm trước khi merge (mục Phối hợp của plan).

## 2. Match `users/{uid}`

```text
match /users/{uid} {
  // Tên trường người dùng tự sửa được. Spec 011 bổ sung kiểm tra chi tiết cho từng trường.
  function selfEditableFields() {
    return ['displayName', 'avatarPath', 'level', 'favoriteDistrictIds',
            'fcmTokens', 'notificationPrefs', 'updatedAt'];
  }

  function validDisplayName() {
    return !('displayName' in request.resource.data.diff(resource.data).affectedKeys())
        || (request.resource.data.displayName is string
            && request.resource.data.displayName.size() >= 2
            && request.resource.data.displayName.size() <= 50);
  }

  allow get: if (isSelf(uid) && isInitialized()) || isAdmin();
  allow list: if isAdmin();                         // spec 120 tra cứu người dùng
  allow create, delete: if false;                   // chỉ on-signup / delete-account (service account)
  allow update: if isSelf(uid) && isInitialized()
                && onlyChanges(selfEditableFields())
                && request.resource.data.updatedAt == request.time
                && validDisplayName();
}
```

Ghi chú:
- Các subcollection (`favorites` của C, `notifications` của D, `aiUsage` của A) có `match` riêng. Rule trên không áp dụng xuống subcollection.
- Ghi `get` và `list` riêng để admin `list` được, còn người dùng không `list` được toàn bộ `users`.

## 3. Bảng test (mỗi dòng ít nhất một test, theo đặc tả CSDL mục 7)

| # | Ngữ cảnh | Thao tác | Kỳ vọng |
|---|---|---|---|
| U1 | Chủ tài khoản (`appRole: PLAYER`) | `get users/{mình}` | Được phép |
| U2 | Người chơi khác | `get users/{người khác}` | Bị chặn |
| U3 | Chưa đăng nhập | `get users/{x}` | Bị chặn |
| U4 | Đã đăng nhập Firebase nhưng **chưa có claim** `appRole` | `get users/{mình}` | Bị chặn |
| U5 | Admin | `get users/{x}`, `list users` | Được phép |
| U6 | Người chơi | `list users` | Bị chặn |
| U7 | Chủ tài khoản | `create users/{mình}` (kể cả đủ trường) | Bị chặn |
| U8 | Chủ tài khoản | `update displayName` và `updatedAt = serverTimestamp()` | Được phép |
| U9 | Chủ tài khoản | `update role = 'ADMIN'` | Bị chặn |
| U10 | Chủ tài khoản | `update ownerStatus = 'APPROVED'` | Bị chặn |
| U11 | Chủ tài khoản | `update accountStatus = 'ACTIVE'` (tự mở khóa) | Bị chặn |
| U12 | Chủ tài khoản | `update walletBalance` | Bị chặn |
| U13 | Chủ tài khoản | `update termsAcceptedAt` | Bị chặn |
| U14 | Chủ tài khoản | `update displayName` dài 1 ký tự hoặc 51 ký tự | Bị chặn |
| U15 | Chủ tài khoản | `update displayName` mà `updatedAt` không phải giờ server | Bị chặn |
| U16 | Admin | `update accountStatus = 'LOCKED'` trực tiếp | Bị chặn (phải qua `set-account-status`) |
| U17 | Chủ tài khoản | `delete users/{mình}` | Bị chặn |
| U18 | Người chơi | `update users/{người khác}.displayName` | Bị chặn |

Tổng cộng U1–U18 và 3 test nền đã có. Kịch bản phân quyền SC-006 là U9–U13 và U16.
