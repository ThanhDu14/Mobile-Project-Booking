# Quickstart: kiểm chứng spec 091

> Chỉ chạy trên Firebase Emulator và Supabase local (constitution IV). Chi tiết: [data-model.md](data-model.md), [contracts/](contracts/).

## 1. Chuẩn bị

Làm theo [quickstart 090](../090-community-group/quickstart.md#1-chuẩn-bị) (Emulator, Supabase local, seed `090-community.ts`), rồi:

```bash
npx tsx scripts/seed/091-chat.ts
```

Script `091-chat.ts` tạo:
- `owner1` (`appRole: OWNER`, `ownerStatus = APPROVED`) có 2 cơ sở `V1`, `V2` đang hoạt động và `V3` bị ẩn; `owner2` chưa được duyệt;
- chat riêng `player1`–`owner1` có 40 tin (1 tin ảnh, 1 tin `HIDDEN`, 1 tin `RECALLED`);
- chat nhóm `group_G1` khớp thành viên của nhóm `G1` (spec 090) với 35 tin;
- chat riêng `player2`–`owner1` trong đó `owner1` đã chặn `player2`.

## 2. Test tự động

```bash
cd firestore-tests && npm test                 # Rules CR1–CR8
npx supabase test db                           # Storage S1–S4
deno test -A supabase/functions/chat-send supabase/functions/chat-action supabase/functions/chat-media-url supabase/functions/_shared/chats.test.ts   # C1–C8, D1–D4, R1–R2, H1–H2, M1–M2, A1
./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.chat.*"
./gradlew connectedDebugAndroidTest --tests "*ChatOutboxOfflineTest"     # SC-004 trên Emulator
```

**Kết quả mong đợi:** tất cả xanh. C3 in `runs=10 messages=20 unreadOk=true lastOk=true`; D1 in `runs=20 chats=1`.

## 3. Kịch bản thủ công

| # | Bước | Kết quả đúng | Liên quan |
|---|---|---|---|
| Q1 | `player3` mở chi tiết `V1`, bấm "Nhắn tin cho chủ sân", gửi câu hỏi | Mở chat, đầu màn hình có tên và ảnh `V1`; gửi được trong < 30 giây kể từ khi mở chi tiết sân | US1, SC-002 |
| Q2 | Máy `owner1` (chế độ quản lý sân) mở "Tin nhắn khách hàng" | Thấy cuộc với `player3` kèm tên `V1`; trả lời; máy `player3` thấy trong ≤ 3 giây | AC 1.3, 1.4, SC-001 |
| Q3 | `player3` mở `V2` (cùng chủ) và nhắn | Vẫn cuộc cũ, đầu màn hình đổi sang `V2` | AC 1.2 |
| Q4 | `owner1` mở chi tiết `V1` của mình | Không có nút "Nhắn tin cho chủ sân" | AC 1.5 |
| Q5 | Thành viên `G1` mở "Chat nhóm", gửi tin; 2 máy khác đang mở | Thấy ngay, đúng thứ tự, có tên và ảnh người gửi | US2 |
| Q6 | `player7` vào `G1` (spec 090) rồi mở chat nhóm | Đọc được 35 tin cũ, không bị tính chưa đọc | AC 2.2 |
| Q7 | `player4` bị mời ra khỏi `G1` | Chat nhóm biến mất khỏi danh sách, không mở lại được | AC 2.3, SC-003 |
| Q8 | `player1` nhận 2 tin trong khi đang ở danh sách | Cuộc nhảy lên đầu, số "2"; mở cuộc thì về 0, huy hiệu giảm | US3 |
| Q9 | Tắt thông báo một cuộc, nhận tin mới | Không có thông báo đẩy, số chưa đọc vẫn tăng | AC 3.5 |
| Q10 | Gửi ảnh; bật chế độ máy bay giữa lúc tải | Tin ảnh "Đang gửi" → "Gửi lỗi" + "Gửi lại"/"Xóa"; bên kia không thấy ảnh hỏng | US4, AC 4.2 |
| Q11 | Bật chế độ máy bay, gửi tin chữ, đợi 20 giây, bật mạng | Tin tự gửi đúng một lần | FR-008, SC-004 |
| Q12 | Thu hồi tin vừa gửi; thử thu hồi tin 15 phút trước | "Tin nhắn đã được thu hồi" ở cả hai máy; tin cũ không có "Thu hồi" | AC 5.1 |
| Q13 | Báo cáo một tin 2 lần | Lần 1 "Đã gửi báo cáo"; lần 2 "Bạn đã báo cáo tin này" | AC 5.2 |
| Q14 | `player2` gửi tin cho `owner1` (đã chặn) | "Bạn không thể gửi tin trong cuộc trò chuyện này"; `owner1` bỏ chặn thì gửi được | AC 5.4 |
| Q15 | Từ chối quyền camera khi bấm gửi ảnh | App giải thích, vẫn chọn ảnh từ thư viện và nhắn chữ được | AC 4.5 |
| Q16 | Chế độ tối, `w600dp`, mất mạng trên 2 màn hình | Đủ 4 trạng thái, không crash, giữ nội dung đang soạn | FR-017, constitution V |

## 4. Đo nhanh

- **SC-001:** 20 lần gửi tin chữ giữa 2 máy trên mạng 4G, ít nhất 19 lần hiện trong ≤ 3 giây.
- **SC-006:** danh sách trò chuyện và 30 tin đầu tải < 3 giây.
- **SC-007:** 5 người thử tự nhắn cho chủ sân, gửi ảnh trong chat nhóm, báo cáo một tin; ghi vào báo cáo tuần.
