# Quickstart: kiểm chứng spec 090

> Chỉ chạy trên Firebase Emulator và Supabase local (constitution IV). Chi tiết: [data-model.md](data-model.md), [contracts/](contracts/).

## 1. Chuẩn bị

- Có Node 20+, Java 21+, Docker, Deno. Cấu hình Firebase và Supabase local đã có trên main (PR #22, #23).
- `local.properties`: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `USE_EMULATOR=true` (quickstart 010).

```bash
npx firebase-tools emulators:start --only auth,firestore --project demo-smashnow
npx supabase start                      # chạy cả migration bucket group-media, group-avatars
npx tsx scripts/seed/090-community.ts   # dữ liệu demo
```

Script `090-community.ts` tạo:
- `player1`…`player25`, `admin1` (`appRole: ADMIN`);
- nhóm `G1` công khai (chủ `player1`, quản trị viên `player2`, thành viên `player3`, `player4`) có 25 bài (1 bài `HIDDEN` bởi nhóm, 1 bài `HIDDEN` bởi `SYSTEM`), 1 sự kiện sắp tới (dự kiến 8 người), 1 sự kiện đã qua;
- nhóm `G2` riêng tư (chủ `player1`) có 2 yêu cầu chờ duyệt (1 người từng bị mời ra);
- nhóm `G3` `LOCKED`;
- `player5` đã làm chủ 5 nhóm.

## 2. Test tự động

```bash
cd firestore-tests && npm test                 # Rules GR1–GR12
npx supabase test db                           # Storage S1–S5
deno test -A supabase/functions/group-manage supabase/functions/group-post supabase/functions/group-media-url   # G1–G15, P1–P11, M1–M3
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.community.*"
```

**Kết quả mong đợi:** tất cả xanh. Ca G5 và P6 in `runs=10 expected=20 actual=20 duplicates=0`.

## 3. Kịch bản thủ công

| # | Bước | Kết quả đúng | Liên quan |
|---|---|---|---|
| Q1 | `player6` tạo nhóm công khai có ảnh | Vào trang nhóm, vai trò chủ nhóm, 1 thành viên; đo < 1 phút | US1, SC-001 |
| Q2 | Tạo nhóm, bật chế độ máy bay lúc tải ảnh | Báo lỗi ảnh, giữ form; chọn "Tạo không có ảnh" thì nhóm có ảnh mặc định | AC 1.3 |
| Q3 | `player5` tạo nhóm thứ 6 | "Bạn đã làm chủ tối đa 5 nhóm" | AC 1.4 |
| Q4 | `player7` tìm "cau long" (không dấu), lọc quận | Thấy `G1`, `G2` (nhãn "Riêng tư"), không thấy `G3`; vào `G1` ngay; đo < 30 giây | US2, SC-002 |
| Q5 | `player7` mở `G2` (chưa là thành viên) | Chỉ thấy trang giới thiệu, không thấy bài | AC 3.7, FR-014 |
| Q6 | `player7` xin vào `G2`; `player1` duyệt | "Đang chờ duyệt" → thành viên; `memberCount` tăng 1; `player7` nhận thông báo | AC 2.3, 2.4 |
| Q7 | `player3` đăng bài 2 ảnh; máy `player4` đang mở bảng tin | Bài hiện ở đầu trong ≤ 5 giây, đủ 2 ảnh đúng thứ tự | US3, SC-005 |
| Q8 | Đăng bài, ảnh thứ 2 tải lỗi | Ảnh đánh dấu lỗi, nội dung giữ, nút "Đăng" khóa tới khi thử lại hoặc bỏ ảnh | AC 3.3 |
| Q9 | `player3` tạo sự kiện; `player4` tham gia rồi bỏ | "1/8" → "0/8"; tên hiện rồi mất trong danh sách | US4 |
| Q10 | `player2` (quản trị viên) mời `player1` (chủ nhóm) ra | Không có nút; gọi thẳng Edge Function → `FORBIDDEN` | AC 5.3, SC-004 |
| Q11 | `player1` chuyển quyền cho `player2` rồi rời nhóm | `player2` là chủ nhóm | AC 5.5 |
| Q12 | `player4` báo cáo bài của `player3` 2 lần | Lần 1 "Đã gửi báo cáo"; lần 2 "Bạn đã báo cáo bài này"; báo cáo có trong `reports` | US6 |
| Q13 | `player2` ẩn bài; mở bằng `player4` và `player3` | `player4` không thấy; `player3` thấy nhãn "Đã bị ẩn"; bài `SYSTEM` thì `player2` không "Hiện lại" được | AC 6.3, 6.4 |
| Q14 | `player1` xóa `G1`, gõ đúng tên | Nhóm biến mất khỏi tìm kiếm và "Nhóm của tôi" của mọi thành viên | AC 5.6 |
| Q15 | Chế độ tối, màn hình `w600dp`, mất mạng trên mọi màn hình | Đủ 4 trạng thái, không crash | FR-020, constitution V |

## 4. Đo nhanh

- **SC-006:** bấm giờ danh sách nhóm và trang đầu bảng tin trên mạng 4G, mục tiêu < 3 giây.
- **SC-008:** 5 người thử tự tạo nhóm, tham gia nhóm người khác, đăng sự kiện mà không cần hướng dẫn; ghi kết quả vào báo cáo tuần.
