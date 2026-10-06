# Quickstart: Kiểm chứng spec 070 (đánh giá và yêu thích)

Hướng dẫn chạy và kiểm tra tính năng từ đầu đến cuối. Chi tiết dữ liệu ở [data-model.md](data-model.md), API ở [contracts/edge-function-review.md](contracts/edge-function-review.md), quyền ở [contracts/security-rules.md](contracts/security-rules.md).

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Android Studio, JDK 17, thiết bị/emulator API 29+ | |
| Node.js 20 + `firebase-tools` | Firebase Emulator Suite (Auth, Firestore) |
| Supabase CLI + Docker | `supabase start` chạy Storage, Edge Functions local |
| Deno | Test Edge Function |
| `local.properties` có `SUPABASE_URL`, `SUPABASE_ANON_KEY` trỏ về local; app bật `useEmulator` ở build debug | Không commit khóa thật (nguyên tắc II) |

```bash
firebase emulators:start --only auth,firestore      # cửa sổ 1
supabase start                                       # cửa sổ 2
supabase functions serve review --env-file supabase/.env.local   # cửa sổ 3
```

Nạp dữ liệu demo (`scripts/seed/070-review.ts`, dữ liệu giả):
- `player1`, `player2` (người chơi), `owner1` (chủ sân đã duyệt, sở hữu `venueA`), `owner2` (sở hữu `venueB`), `admin1`.
- `venueA` (`ACTIVE`, 25 đánh giá demo, có ảnh), `venueB` (`HIDDEN`).
- Đơn của `player1` tại `venueA`: `b-done` (`COMPLETED`, `completedAt` = hôm qua), `b-old` (`COMPLETED`, 8 ngày trước), `b-confirmed` (`CONFIRMED`).
- Đơn của `owner1` tại `venueA`: `b-own` (`COMPLETED`).

## 2. Test tự động

```bash
./gradlew testDebugUnitTest                       # ViewModel, UseCase, Repository (MockK, Turbine)
cd firestore-tests && npm test                    # Rules R1–R9, F1–F4 trên Emulator
deno test supabase/functions/review/              # Edge Function: điều kiện, idempotent, tổng điểm, 10 gửi đồng thời
supabase test db                                  # RLS bucket review-photos S1–S6
```

Kỳ vọng: tất cả đều qua. Test đồng thời: 10 lời gọi `create` song song cho 10 đơn khác nhau của cùng `venueA` → `ratingCount` tăng đúng 10, `ratingSums` đúng tổng (SC-005).

## 3. Kịch bản kiểm tra trên app

| # | Đăng nhập | Thao tác | Kết quả mong đợi | Spec |
|---|---|---|---|---|
| 1 | player1 | Lịch đặt → `b-done` → "Đánh giá" → chấm 5/4/3, viết bình luận, gửi | Đánh giá hiện ở "Xem tất cả" của `venueA` với 4,0 sao; `ratingCount` +1; nút đổi thành "Xem đánh giá của bạn" | US1, SC-001, SC-004 |
| 2 | player1 | Mở `b-confirmed`, `b-old` | Không có nút "Đánh giá" (`b-old` báo hết hạn) | US1-4, FR-001 |
| 3 | player1 | Gửi lại đánh giá `b-done` khi tắt mạng rồi bật lại, bấm "Gửi" 3 lần | Chỉ một đánh giá | FR-002 |
| 4 | player1 | Viết đánh giá kèm 3 ảnh; thử thêm ảnh thứ 4; tắt mạng giữa lúc tải ảnh | Ảnh thứ 4 bị chặn; ảnh lỗi được đánh dấu, "Thử lại" hoặc bỏ ảnh | US2 |
| 5 | player1 | Sửa đánh giá `b-done` từ 3 lên 5 sao "Thái độ" | Nhãn "Đã chỉnh sửa", điểm sân tính lại | US5 |
| 6 | owner1 | Chế độ quản lý sân → "Đánh giá của khách" → lọc "Chưa phản hồi" → phản hồi | Phản hồi hiện dưới đánh giá; player1 nhận thông báo `REVIEW_REPLY`; chỉ có "Sửa phản hồi", không có "Xóa" | US4 |
| 7 | owner2 | Gọi `review` action `reply` cho đánh giá ở `venueA` (curl) | `403 NOT_OWNER` | FR-018, SC-003 |
| 8 | owner1 | Đánh giá `b-own` | `403 OWN_VENUE` | Edge Cases |
| 9 | player2 | Chi tiết `venueA` → trái tim → Tài khoản → "Sân yêu thích" | `venueA` có trong danh sách; trái tim đổi ngay (< 0,5 s) | US3, SC-006 |
| 10 | player2 | Lưu `venueB` (bằng dữ liệu seed), mở "Sân yêu thích" | `venueB` hiện "Sân tạm ngừng hoạt động", không đặt được, vẫn bỏ được | FR-024 |
| 11 | player2 | Bật chế độ máy bay, bấm trái tim trên `venueA`, tắt chế độ máy bay | Giao diện đổi ngay; sau khi có mạng, Firestore có/không có document tương ứng | FR-021 |
| 12 | admin1 | Ẩn một đánh giá của player1 (qua công cụ spec 120 hoặc script) | Không hiện với player2; player1 thấy nhãn "Đánh giá đã bị ẩn"; `ratingCount` −1 | FR-011, FR-015 |
| 13 | player1 | Xóa đánh giá `b-done` | Biến mất, ảnh bị xóa khỏi Storage, `ratingCount` −1 | US5, FR-008 |
| 14 | player2 | "Xem tất cả" của `venueA`: cuộn cuối danh sách, lọc "5 sao", "Có ảnh" | Tải thêm 20/lần; kết quả lọc đúng; trang đầu < 3 s | US6, SC-007 |
| 15 | bất kỳ | Xem đánh giá của người dùng đã xóa tài khoản (seed `authorDeleted = true`) | "Người dùng đã xóa" + ảnh mặc định | FR-016 |

Kiểm tra thêm: chế độ tối, màn hình `w600dp`, TalkBack đọc được số sao và nút trái tim (nguyên tắc V).

## 4. Minh chứng (Definition of Done)

Chụp màn hình/quay video các kịch bản 1, 4, 6, 9, 10, 12 lưu vào `docs/features/07-review-favorite/screenshots/` theo tên `07_review-favorite_buocX_mo-ta.png`.
