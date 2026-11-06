# Contract: Rules, index và test Emulator cho spec 021

Spec 021 **không thêm match Rules nào**. Chỉ thêm một index và bộ test kiểm chứng quyền đọc của khách cho query theo vùng. Lý do: [research R2, R15](../research.md).

## 1. Index

Thêm vào `firestore.indexes.json`:

```json
{
  "collectionGroup": "venues",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "status", "order": "ASCENDING" },
    { "fieldPath": "geohash", "order": "ASCENDING" }
  ]
}
```

- Chế độ `DISTRICT` dùng lại index V2 của 020 (`status`, `districtId`, `ratingAvg DESC`, `ratingCount DESC`). Nếu 020 chưa ghi V2 vào file lúc làm 021 thì 021 ghi cùng, không tạo bản trùng.
- Emulator không kiểm tra index. Kiểm tra bằng cách deploy lên project thật rồi chạy kịch bản quickstart §4 (bước 2 và 9), giống 020 T098.

## 2. Rules mà 021 dựa vào (không sở hữu)

| Collection | Yêu cầu | Người viết | Nguồn | Hiện trạng trên `main` (28/10) |
|---|---|---|---|---|
| `venues` | Mọi người đọc khi `status == 'ACTIVE'`; chủ cơ sở và admin đọc mọi trạng thái | D | 020 R13, 030 R10 | `allow read: if true` (PR #43): rộng hơn yêu cầu, query của 021 vẫn chạy |
| `venues/*/courts`, `priceRules` | Mọi người đọc | D | 020 R13 | `allow read: if true` |
| `slots` | Mọi người đọc | B | 020 R13 | `allow read: if true` |
| `districts` | Mọi người đọc | A (020) | 020 contract security-rules | Chưa có (020 T019) |

Query của 021 **luôn** có `whereEqualTo("status", "ACTIVE")`, để Rules dạng "chỉ đọc ACTIVE" chứng minh được query (Rules không phải bộ lọc).

## 3. Bảng test (`firestore-tests/test/map.test.ts`)

Chạy với `@firebase/rules-unit-testing` trên Firestore Emulator. Dữ liệu: 2 cơ sở `ACTIVE`, 1 `HIDDEN` có `geohash` cùng vùng.

| # | Người gọi | Thao tác | Kết quả |
|---|---|---|---|
| M-1 | Khách (chưa đăng nhập) | `where status == ACTIVE` + `orderBy geohash` + `startAt/endAt` | Thành công, 2 document |
| M-2 | Người chơi đã đăng nhập | Như M-1 | Thành công |
| M-3 | Khách | `orderBy geohash` + `startAt/endAt`, **không** có `status == ACTIVE` | Bị từ chối. `it.todo` cho tới khi D áp Rules `venues` theo 020 R13 (hiện Rules đang `read: if true`) |
| M-4 | Khách | `get` cơ sở `HIDDEN` | Bị từ chối. `it.todo` như M-3 |
| M-5 | Khách | `where status == ACTIVE` + `where districtId == X` + `orderBy ratingAvg` | Thành công |
| M-6 | Người chơi | `set`/`update` trường `geohash` của một cơ sở không phải của mình | Bị từ chối (Rules ghi của D đã có trên `main`: `isOwner()` + `ownerId`) |

## 4. Kiểm tra dữ liệu (`scripts/seed`)

- `npm run check` thêm bước: với mỗi cơ sở trong `venues.json` (và `map-demo.json` nếu có), `geohash(location)` trùng `geohash.vectors.json`; cơ sở `ACTIVE` có `location` khác `(0,0)` trừ những ID ghi rõ trong `map-demo.json` là ca thử "thiếu vị trí".
- `--with-map-demo` bị từ chối nếu thiếu `--emulator`.
