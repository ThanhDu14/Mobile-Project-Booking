# Contract: Security Rules cho `users/{uid}` (mở rộng 010) và `districts/{id}`

**File:** `firestore.rules`. **Test:** `firestore-tests/test/users-profile.test.ts`, `firestore-tests/test/districts.test.ts` (Vitest + `@firebase/rules-unit-testing`, project `demo-smashnow`). Bộ test U1–U18 của 010 vẫn phải xanh.

## 1. Bổ sung trong `match /users/{uid}`

010 đã có `selfEditableFields()`, `validDisplayName()` và điều kiện `update`. Spec 011 thêm các hàm kiểm tra dưới đây và gộp vào điều kiện `update`:

```text
function changed(field) {
  return field in request.resource.data.diff(resource.data).affectedKeys();
}

function validLevel() {
  return !changed('level')
      || request.resource.data.level == null
      || request.resource.data.level in ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
}

function activeDistrict(id) {
  return get(/databases/$(database)/documents/districts/$(id)).data.isActive == true;
}

function validDistricts() {
  let ids = request.resource.data.favoriteDistrictIds;
  return !changed('favoriteDistrictIds')
      || (ids is list && ids.size() <= 5 && ids.toSet().size() == ids.size()
          && (ids.size() < 1 || activeDistrict(ids[0]))
          && (ids.size() < 2 || activeDistrict(ids[1]))
          && (ids.size() < 3 || activeDistrict(ids[2]))
          && (ids.size() < 4 || activeDistrict(ids[3]))
          && (ids.size() < 5 || activeDistrict(ids[4])));
}

function validAvatarPath() {
  let p = request.resource.data.avatarPath;
  return !changed('avatarPath')
      || p == null
      || (p is string && p.matches(uid + '/avatar_[0-9]{13}[.]jpg'));
}

allow update: if isSelf(uid) && isInitialized()
              && onlyChanges(selfEditableFields())
              && request.resource.data.updatedAt == request.time
              && validDisplayName() && validLevel() && validDistricts() && validAvatarPath();
```

Ghi chú:
- Tối đa 5 lượt `get()` mỗi request, dưới giới hạn 10 của Rules.
- `uid` là biến của `match`, nên `avatarPath` của người khác không qua được.
- `fcmTokens`, `notificationPrefs`: spec 100 (D) thêm kiểm tra riêng nếu cần; 011 chỉ `arrayRemove` token của máy khi đăng xuất.

## 2. `match /districts/{id}` (mới)

```text
match /districts/{id} {
  allow get, list: if isInitialized();
  allow write: if isAdmin();    // spec 120 làm màn hình; có thể chuyển sang Edge Function nếu D muốn
}
```

## 3. `match /accountDeletions/{uid}` (mới)

```text
match /accountDeletions/{uid} {
  allow read, write: if false;  // chỉ delete-account / delete-account-sweeper (service account)
}
```

## 4. Bảng test

| # | Ngữ cảnh | Thao tác | Kỳ vọng |
|---|---|---|---|
| P1 | Chủ tài khoản | `level = 'INTERMEDIATE'`, `updatedAt = serverTimestamp()` | Được phép |
| P2 | Chủ tài khoản | `level = null` | Được phép |
| P3 | Chủ tài khoản | `level = 'PRO'` | Bị chặn |
| P4 | Chủ tài khoản | `favoriteDistrictIds = ['q1','q3']` (đều `isActive`) | Được phép |
| P5 | Chủ tài khoản | `favoriteDistrictIds` 6 phần tử | Bị chặn |
| P6 | Chủ tài khoản | `favoriteDistrictIds = ['q1','q1']` | Bị chặn |
| P7 | Chủ tài khoản | `favoriteDistrictIds = ['khong-ton-tai']` | Bị chặn |
| P8 | Chủ tài khoản | `favoriteDistrictIds = ['q-cu']` (`isActive = false`) | Bị chặn |
| P9 | Chủ tài khoản | `favoriteDistrictIds = []` | Được phép |
| P10 | Chủ tài khoản | `avatarPath = '{uid}/avatar_1760500000000.jpg'` | Được phép |
| P11 | Chủ tài khoản | `avatarPath = '{uid khác}/avatar_1760500000000.jpg'` | Bị chặn |
| P12 | Chủ tài khoản | `avatarPath = '{uid}/../x.jpg'` hoặc `'{uid}/avatar.png'` | Bị chặn |
| P13 | Chủ tài khoản | `avatarPath = null` | Được phép |
| P14 | Chủ tài khoản | Đổi `displayName`, `level`, `favoriteDistrictIds` cùng lúc, đều hợp lệ | Được phép |
| P15 | Chủ tài khoản | `fcmTokens` = `arrayRemove(token)` | Được phép |
| P16 | Chủ tài khoản | `email = 'moi@x.com'` | Bị chặn |
| P17 | Chủ tài khoản | `deletionRequestedAt = serverTimestamp()` | Bị chặn |
| P18 | Chủ tài khoản | `walletBalance` / `ownerStatus` / `role` (lặp lại U9–U12 của 010 để chắc chắn sau khi sửa) | Bị chặn |
| D1 | Người đã khởi tạo | `list districts where isActive == true` | Được phép |
| D2 | Chưa có `appRole` | `get districts/q1` | Bị chặn |
| D3 | Người chơi | `set districts/q99` | Bị chặn |
| D4 | Admin | `set districts/q99` | Được phép |
| A1 | Chủ tài khoản | `get accountDeletions/{mình}` | Bị chặn |

SC-006 (không tự đổi vai trò, trạng thái): P16–P18 cộng U9–U13, U16 của 010.
