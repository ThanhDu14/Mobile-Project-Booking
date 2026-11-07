# Contract: Security Rules và index cho tìm kiếm

**File:** `firestore.rules`, `firestore.indexes.json`. **Test:** `firestore-tests/test/search.test.ts` (Vitest, `@firebase/rules-unit-testing`, project `demo-smashnow`). Dùng hàm nền `isSelf()`, `isInitialized()`, `isAdmin()`, `onlyChanges()`, `hasServerTimestamps()` theo plan 010 ([contract](../../010-account-auth/contracts/security-rules.md)).

Lý do: [research R11, R13](../research.md#r13-quyền-đọc-cho-khách-chưa-đăng-nhập-fr-026-fr-027).

## 1. Match do A viết

```text
match /districts/{id} {
  allow read: if true;
  allow write: if isAdmin();          // spec 120 bổ sung kiểm tra trường
}

match /amenities/{id} {
  allow read: if true;
  allow write: if isAdmin();
}

match /users/{uid}/searchHistory/{historyId} {
  function historyFields() {
    return ['keyword', 'districtId', 'priceMin', 'priceMax', 'date', 'startTime', 'endTime',
            'amenityIds', 'sort', 'searchedAt', 'createdAt', 'updatedAt'];
  }

  function optString(d, f, maxLen) {
    return !(f in d) || d[f] == null || (d[f] is string && d[f].size() <= maxLen);
  }

  function optPrice(d, f) {
    return !(f in d) || d[f] == null || (d[f] is int && d[f] >= 0 && d[f] % 10000 == 0);
  }

  function validWindow(d) {
    let none = (!('date' in d) || d.date == null)
            && (!('startTime' in d) || d.startTime == null)
            && (!('endTime' in d) || d.endTime == null);
    let all = d.get('date', null) is string && d.date.matches('^[0-9]{8}$')
           && d.get('startTime', null) is string && d.startTime.matches('^[0-9]{2}(00|30)$')
           && d.get('endTime', null) is string && d.endTime.matches('^[0-9]{2}(00|30)$')
           && d.endTime > d.startTime;
    return none || all;
  }

  function validHistory(d) {
    return d.keys().hasOnly(historyFields())
        && d.keyword is string && d.keyword.size() <= 100
        && optString(d, 'districtId', 40)
        && optPrice(d, 'priceMin') && optPrice(d, 'priceMax')
        && (d.get('priceMin', null) == null || d.get('priceMax', null) == null
            || d.priceMin <= d.priceMax)
        && validWindow(d)
        && d.amenityIds is list && d.amenityIds.size() <= 20
        && d.sort in ['RATING', 'PRICE_ASC', 'NEAREST']
        && d.searchedAt == request.time;
  }

  allow read, delete: if isSelf(uid) && isInitialized();
  allow create: if isSelf(uid) && isInitialized()
                && validHistory(request.resource.data)
                && hasServerTimestamps();
  allow update: if isSelf(uid) && isInitialized()
                && validHistory(request.resource.data)
                && request.resource.data.createdAt == resource.data.createdAt
                && request.resource.data.updatedAt == request.time;
}
```

Rules chỉ kiểm tra `amenityIds` là list tối đa 20 phần tử, không kiểm tra từng phần tử là ID có trong danh mục (Rules không đọc được danh mục mà không tốn `get()` cho mỗi phần tử). Mục lịch sử chỉ chủ tài khoản đọc, nên ID lạ không ảnh hưởng ai khác; khi khôi phục, `SearchCriteriaSanitizer` bỏ ID không có trong danh mục (`UNKNOWN_AMENITY`).

Không có quyền admin đọc lịch sử (không cần cho spec nào). Xóa cùng tài khoản do `delete-account` (service account, bỏ qua Rules).

## 2. Yêu cầu Rules gửi người phụ trách (D cho `venues`, `courts`, `priceRules`; B cho `slots`)

```text
// D — trong match /venues/{venueId}
allow read: if resource.data.status == 'ACTIVE'
            || (isInitialized() && resource.data.ownerId == request.auth.uid)
            || isAdmin();

// D — match /venues/{venueId}/courts/{id} và /priceRules/{id}
allow read: if true;

// B — match /slots/{slotId}
allow read: if true;
```

Query của app luôn có `where("status", "==", "ACTIVE")` để Rules chứng minh được mọi kết quả đều được phép (Rules không phải bộ lọc). Test ở §4 (V-1 → V-5, S-1, S-2) do A viết trong file test của mình để kiểm chứng quyền đọc của khách; khi D, B viết Rules thì các test này phải xanh.

## 3. `firestore.indexes.json`

```json
{
  "indexes": [
    { "collectionGroup": "venues", "queryScope": "COLLECTION", "fields": [
      { "fieldPath": "status", "order": "ASCENDING" },
      { "fieldPath": "ratingAvg", "order": "DESCENDING" },
      { "fieldPath": "ratingCount", "order": "DESCENDING" } ] },
    { "collectionGroup": "venues", "queryScope": "COLLECTION", "fields": [
      { "fieldPath": "status", "order": "ASCENDING" },
      { "fieldPath": "districtId", "order": "ASCENDING" },
      { "fieldPath": "ratingAvg", "order": "DESCENDING" },
      { "fieldPath": "ratingCount", "order": "DESCENDING" } ] },
    { "collectionGroup": "venues", "queryScope": "COLLECTION", "fields": [
      { "fieldPath": "status", "order": "ASCENDING" },
      { "fieldPath": "nameKeywords", "arrayConfig": "CONTAINS" },
      { "fieldPath": "ratingAvg", "order": "DESCENDING" },
      { "fieldPath": "ratingCount", "order": "DESCENDING" } ] },
    { "collectionGroup": "venues", "queryScope": "COLLECTION", "fields": [
      { "fieldPath": "status", "order": "ASCENDING" },
      { "fieldPath": "districtId", "order": "ASCENDING" },
      { "fieldPath": "nameKeywords", "arrayConfig": "CONTAINS" },
      { "fieldPath": "ratingAvg", "order": "DESCENDING" },
      { "fieldPath": "ratingCount", "order": "DESCENDING" } ] },

    "… V5–V8: bốn index trên, thêm { \"fieldPath\": \"minPricePerHour\", \"order\": \"ASCENDING\" } ở cuối",

    { "collectionGroup": "venues", "queryScope": "COLLECTION", "fields": [
      { "fieldPath": "status", "order": "ASCENDING" },
      { "fieldPath": "minPricePerHour", "order": "ASCENDING" } ] },

    "… V10–V12: thêm districtId và/hoặc nameKeywords (CONTAINS) trước minPricePerHour",

    { "collectionGroup": "slots", "queryScope": "COLLECTION", "fields": [
      { "fieldPath": "venueId", "order": "ASCENDING" },
      { "fieldPath": "date", "order": "ASCENDING" },
      { "fieldPath": "startAt", "order": "ASCENDING" } ] }
  ],
  "fieldOverrides": []
}
```

Dòng chuỗi `"…"` chỉ để rút gọn tài liệu; file thật liệt kê đủ 13 index. Index `slots` thay index `venueId, date` của 040 (một index phục vụ cả hai query). Báo B trước khi merge.

## 4. Bảng test

Mỗi dòng ít nhất một test. "Khách" = `testEnv.unauthenticatedContext()`. "Người chơi" có claim `appRole: PLAYER`.

| ID | Ai | Thao tác | Kỳ vọng |
|---|---|---|---|
| C-1 | Khách | Đọc `districts/q10`, query `amenities` | ✅ |
| C-2 | Người chơi | Ghi `districts/q10` | ❌ |
| C-3 | Admin | Ghi `amenities/parking` | ✅ |
| H-1 | Chủ | Tạo mục lịch sử hợp lệ với `serverTimestamp()` | ✅ |
| H-2 | Chủ | Đọc `orderBy(searchedAt desc).limit(10)` của mình | ✅ |
| H-3 | Người khác | Đọc lịch sử của chủ | ❌ |
| H-4 | Người khác | Xóa mục lịch sử của chủ | ❌ |
| H-5 | Khách | Đọc hoặc ghi bất kỳ `searchHistory` | ❌ |
| H-6 | Chủ | Tạo với trường lạ (`lat`, `lng`) | ❌ (FR-014) |
| H-7 | Chủ | Tạo với `keyword` 101 ký tự | ❌ |
| H-8 | Chủ | Tạo với `priceMin > priceMax` hoặc giá không chia hết 10.000 | ❌ |
| H-9 | Chủ | Tạo với `startTime = "1915"` hoặc `endTime <= startTime` hoặc chỉ có `date` | ❌ |
| H-10 | Chủ | Tạo với `sort = "DISTANCE"` | ❌ |
| H-11 | Chủ | Tạo với `searchedAt` do máy đặt | ❌ |
| H-12 | Chủ | Cập nhật (tìm lại) đổi `searchedAt`, giữ `createdAt` | ✅ |
| H-13 | Chủ | Cập nhật đổi `createdAt` | ❌ |
| H-14 | Chủ | Xóa từng mục, xóa bằng batch | ✅ |
| H-15 | Tài khoản chưa khởi tạo (không có claim `appRole`) | Tạo mục lịch sử của mình | ❌ |
| V-1 | Khách | Query `venues` với `status == ACTIVE` | ✅ |
| V-2 | Khách | Query `venues` không có điều kiện `status` | ❌ |
| V-3 | Khách | Đọc trực tiếp `venues/{id}` có `status = HIDDEN` hoặc `LOCKED` | ❌ (SC-004) |
| V-4 | Chủ cơ sở | Đọc cơ sở `HIDDEN` của mình | ✅ |
| V-5 | Khách | Đọc `venues/{id}/courts`, `priceRules` | ✅ |
| S-1 | Khách | Query `slots` theo `venueId`, `date`, `startAt` | ✅ |
| S-2 | Khách | Tạo hoặc sửa `slots/{id}` | ❌ |
