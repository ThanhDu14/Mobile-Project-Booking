# Contract: Firestore Rules cho `dropInSessions` và `registrations`

**Vị trí**: phần `dropInSessions` trong `firestore.rules` ở gốc repo. Test đặt ở `firestore-tests/test/dropInSessions.test.ts` (`@firebase/rules-unit-testing`, chạy trên Firebase Emulator).

Mọi ghi nghiệp vụ đi qua Edge Function (service account, bỏ qua Rules). Rules của spec này **chỉ mở quyền đọc**. Riêng việc chủ sân tạo, sửa, hủy buổi do D quyết định ở spec 112. Nếu D ghi qua Edge Function thì Rules cũng không cho client ghi.

## Bản phác Rules

```
match /dropInSessions/{sessionId} {
  // Ai cũng xem được danh sách và chi tiết buổi, kể cả khách chưa đăng nhập
  allow read: if true;
  allow write: if false;            // D (spec 112) ghi qua Edge Function

  match /registrations/{uid} {
    allow get, list: if isSelf(uid)
                     || isSessionOwner(sessionId)
                     || isAdmin();
    allow write: if false;          // chỉ Edge Function
  }
}

// "Lượt vãng lai của tôi": truy vấn collection group theo userId
match /{path=**}/registrations/{uid} {
  allow list: if request.auth != null
              && resource.data.userId == request.auth.uid;
}

function isSelf(uid)            { return request.auth != null && request.auth.uid == uid; }
function isAdmin()              { return request.auth != null && request.auth.token.appRole == 'ADMIN'; }
function isSessionOwner(sid)    { return request.auth != null
                                    && request.auth.token.appRole == 'OWNER'
                                    && get(/databases/$(database)/documents/dropInSessions/$(sid)).data.ownerId == request.auth.uid; }
```

## Ca test bắt buộc (mỗi dòng có ít nhất một ca "được" và một ca "bị chặn")

| # | Ca | Kỳ vọng | Liên quan |
|---|---|---|---|
| D1 | Khách chưa đăng nhập đọc `dropInSessions/{id}` | Được | Assumptions |
| D2 | Người chơi ghi `registeredCount` hoặc `status` của buổi | Bị chặn | FR-009, SC-004 |
| D3 | Người chơi tạo `registrations/{uid}` của chính mình từ client | Bị chặn | FR-007 |
| D4 | Người chơi sửa `amount`, `paymentStatus`, `status`, `qrToken` của đăng ký mình | Bị chặn | FR-009 |
| D5 | Người chơi A đọc `registrations/{uidB}` | Bị chặn | FR-021 |
| D6 | Người chơi A đọc `registrations/{uidA}` | Được | FR-013 |
| D7 | Người chơi liệt kê `dropInSessions/{id}/registrations` | Bị chặn (chỉ đọc được document của mình, không liệt kê cả buổi) | FR-021 |
| D8 | Chủ cơ sở mở buổi liệt kê đăng ký của buổi mình | Được | spec 112 FR-011 |
| D9 | Chủ sân khác liệt kê đăng ký của buổi không thuộc mình | Bị chặn | spec 112 FR-002 |
| D10 | Truy vấn collection group `registrations where userId == myUid` | Được | FR-019 |
| D11 | Truy vấn collection group `registrations` không có `where userId`, hoặc `userId` của người khác | Bị chặn | FR-021 |
| D12 | Admin đọc đăng ký bất kỳ | Được | FR-021 |
| D13 | Người chơi xóa đăng ký của mình (thay vì gọi `cancel-drop-in`) | Bị chặn | FR-018 |

## Test Edge Function (`deno test`, Firebase Emulator + `supabase functions serve`)

| # | Ca | Kỳ vọng | Liên quan |
|---|---|---|---|
| E1 | 20 người cùng đăng ký 5 chỗ cuối (mỗi người 1 chỗ) | Đúng 5 `201`, 15 `SESSION_FULL`; `registeredCount == capacity`; buổi `FULL` | SC-002, FR-007 |
| E2 | 2 người cùng đăng ký nhóm 2 người vào 3 chỗ còn lại | Đúng 1 thành công, người kia nhận `SESSION_FULL` với `remaining = 1` | AC 2.5 |
| E3 | Gửi cùng `requestId` 5 lần, kể cả song song | 1 đăng ký, `registeredCount` chỉ tăng 1 lần, 4 lần sau trả `200` | SC-003 |
| E4 | Hủy cùng `requestId` 5 lần | Chỉ trả chỗ 1 lần, hoàn tiền 1 lần | SC-003, FR-018 |
| E5 | Hủy lúc còn đúng 240 phút / 239 phút / 241 phút | Hoàn 100% / 0 / 100% | SC-005 |
| E6 | Hủy khi `sessionChanged = true`, còn 30 phút | Hoàn 100% | FR-017 |
| E7 | Hủy lượt `AT_VENUE` | `refundAmount = 0`, `reason = AT_VENUE`, `paymentStatus` giữ `UNPAID` | AC 4.1 |
| E8 | Đăng ký buổi đã bắt đầu / đã hủy / cơ sở `HIDDEN` / chủ cơ sở tự đăng ký | `SESSION_STARTED` / `SESSION_CANCELLED` / `VENUE_INACTIVE` / `OWN_SESSION` | FR-011 |
| E9 | `partySize = 5`, hoặc `partySize = 0` | `INVALID_ARGUMENT` | FR-006 |
| E10 | Hủy đăng ký của người khác (gọi bằng token khác) | `NOT_FOUND` (không lộ đăng ký tồn tại) | AC 4.6 |
| E11 | Đăng ký lại sau khi hủy, buổi còn chỗ | `201`, có `qrToken`/`ticketCode` mới, khác bản cũ | Edge Cases |
| E12 | Buổi `FULL`, một người hủy | `status` trở về `OPEN` | Edge Cases |
| E13 | Sau khi hủy, `qrToken` và `ticketCode` bằng `null` | Đúng | FR-014 |
| E14 | `review create` với `sessionId` khi đăng ký `REGISTERED` (chưa check-in) | `NOT_CHECKED_IN` | FR-022 |
| E15 | `review create` lần 2 cho cùng lượt | `ALREADY_REVIEWED` | AC 5.4 |
