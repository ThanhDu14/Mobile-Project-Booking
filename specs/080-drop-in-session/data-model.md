# Data Model: Đặt lịch chơi vãng lai theo lượt (spec 080)

**Ngày**: 2026-10-15 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

Khớp với [đặc tả CSDL chung](../../docs/design/database/README.md) mục 4.9 (quy ước ở mục 1).

- 🔒 nghĩa là chỉ server (Edge Function) ghi, Rules chặn client.
- Trường thêm mới so với đặc tả CSDL hiện tại có nhãn **[MỚI]**. Danh sách thay đổi ở cuối file để cập nhật vào `docs/design/database/README.md`.
- Slot đã chốt **30 phút**. Spec này **không ghi `slots`**: khi chủ sân mở, sửa hoặc hủy buổi, các slot `BLOCKED` + `blockReason = DROP_IN` do Edge Function của D (spec 112 FR-009, FR-010a) ghi trong cùng transaction.

---

## 1. `dropInSessions/{sessionId}` — C (schema), D (tạo/sửa/hủy, spec 112)

Spec 080 **chỉ đọc** buổi. Riêng `registeredCount` và `status` (`OPEN`/`FULL`) được ghi trong transaction đăng ký/hủy của spec này.

| Trường | Kiểu | Ghi | Ghi chú |
|---|---|---|---|
| `venueId`, `ownerId` | String | D | |
| `districtId` | String | D | Sao từ `venues.districtId` |
| `courtIds` | Array\<String> | D | ≥ 1 sân con của cơ sở |
| `date` | String `yyyyMMdd` | D | Theo giờ `Asia/Ho_Chi_Minh` |
| `startAt`, `endAt` | Timestamp | D | Ranh giới slot 30 phút, không qua nửa đêm |
| `timeSlot` **[MỚI]** | String | D (qua `deriveSessionFields`) | `MORNING` (05:00–11:00), `NOON` (11:00–13:00), `AFTERNOON` (13:00–18:00), `EVENING` (18:00–23:00), theo giờ bắt đầu (FR-003) |
| `venueSnapshot` **[MỚI]** | Map | D (qua `deriveSessionFields`) | `name`, `address`, `districtName`, lúc tạo/sửa buổi |
| `level` | String | D | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` (cùng enum `users.level`) |
| `capacity` | Int | D | > 0, ≥ số người đang được tính (112 FR-010) |
| `registeredCount` 🔒 | Int | Server | Tổng `partySize` của các đăng ký `REGISTERED` + `CHECKED_IN` |
| `pricePerPerson` | Long | D | VND, ≥ 0 |
| `status` | String | D / Server | `OPEN`, `FULL` (server đổi theo `registeredCount`), `CANCELLED` (D), `COMPLETED` (cron spec 081) |
| `note` | String? | D | ≤ 500 ký tự |
| `createdAt`, `updatedAt` | Timestamp | | |

**Ràng buộc luôn đúng:** `0 ≤ registeredCount ≤ capacity`. `status == FULL` ⇔ `registeredCount == capacity`, áp dụng cho buổi chưa `CANCELLED`/`COMPLETED`.

### Máy trạng thái buổi

```
          (D tạo)
             │
             ▼
           OPEN ◀──(hủy/được trả chỗ, server)──┐
             │                                 │
   (registeredCount == capacity, server)       │
             ▼                                 │
           FULL ───────────────────────────────┘
             │
 OPEN/FULL ──(D hủy buổi, spec 112)──▶ CANCELLED
 OPEN/FULL ──(qua endAt, cron spec 081)──▶ COMPLETED
```

## 2. `dropInSessions/{sessionId}/registrations/{uid}` — C

ID = `uid` (đặc tả CSDL mục 5). Client **chỉ đọc**. Mọi ghi đi qua Edge Function: `register-drop-in`, `cancel-drop-in` (spec này), các function hàng chờ (spec 081), check-in và hủy buổi (spec 112).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `userId` 🔒 **[MỚI]** | String | = ID document; cần cho truy vấn collection group "Lượt của tôi" và cho Rules |
| `sessionId` 🔒 **[MỚI]** | String | = ID buổi cha (để đọc collection group không phải tách path) |
| `venueId` 🔒 **[MỚI]** | String | Sao từ buổi |
| `sessionStartAt`, `sessionEndAt` 🔒 **[MỚI]** | Timestamp | Sao từ buổi; D cập nhật khi đổi giờ (spec 112 FR-010a) |
| `partySize` 🔒 | Int | 1–4 (bản thân + tối đa 3 bạn) |
| `status` 🔒 | String | `REGISTERED`, `WAITLISTED` (spec 081), `CANCELLED`, `CHECKED_IN` (spec 112) |
| `paymentMethod` 🔒 | String | `MOCK_PAY`, `AT_VENUE` |
| `paymentStatus` 🔒 **[MỚI]** | String | `PAID`, `UNPAID`, `REFUNDED` |
| `pricePerPerson` 🔒 **[MỚI]** | Long | Giá chốt lúc đăng ký (Edge Cases: chủ sân đổi giá thì giữ nguyên) |
| `amount` 🔒 | Long | `pricePerPerson × partySize` |
| `refundAmount` 🔒 **[MỚI]** | Long? | Số tiền đã hoàn (giả lập) |
| `qrToken` 🔒 | String? | 32 byte ngẫu nhiên base64url; `null` khi `CANCELLED` |
| `ticketCode` 🔒 **[MỚI]** | String? | `DI-XXXXXXXX`; `null` khi `CANCELLED` |
| `requestId` 🔒 | String | UUID của lần đăng ký gần nhất (idempotent) |
| `cancelRequestId` 🔒 **[MỚI]** | String? | UUID của lần hủy |
| `sessionChanged` 🔒 **[MỚI]** | Boolean | `true` khi chủ sân đổi giờ/sân/giá sau khi đăng ký (D ghi); cho hoàn 100% (FR-017) và nhãn "Buổi đã thay đổi" |
| `registeredAt` 🔒 **[MỚI]** | Timestamp | |
| `cancelledAt` 🔒 **[MỚI]** | Timestamp? | |
| `cancelledBy` 🔒 **[MỚI]** | String? | `USER`, `OWNER`, `SYSTEM` |
| `checkedInAt` 🔒 **[MỚI]** | Timestamp? | Spec 112 ghi khi quét QR |
| `waitlistedAt` 🔒 | Timestamp? | Spec 081 |
| `createdAt`, `updatedAt` 🔒 | Timestamp | |

### Máy trạng thái đăng ký (phần của spec 080)

```
(chưa có) / CANCELLED ──register-drop-in──▶ REGISTERED ──check-in (spec 112)──▶ CHECKED_IN
                                               │
                     cancel-drop-in (người chơi, trước startAt)
                     hủy buổi (spec 112) / delete-account (A)
                                               ▼
                                           CANCELLED
```

`WAITLISTED → REGISTERED` và `WAITLISTED → CANCELLED` do spec 081 định nghĩa. Mọi chuyển trạng thái khác bị Edge Function từ chối.

### Quy tắc kiểm tra của `register-drop-in` (trong transaction)

| Quy tắc | Lỗi | FR |
|---|---|---|
| Đã đăng nhập, `accountStatus == ACTIVE` | `UNAUTHENTICATED` / `ACCOUNT_LOCKED` | FR-006 |
| `1 ≤ partySize ≤ 4`, `paymentMethod ∈ {MOCK_PAY, AT_VENUE}`, `requestId` là UUID | `INVALID_ARGUMENT` | FR-006, FR-010 |
| Buổi tồn tại, `status ∈ {OPEN, FULL}` | `SESSION_NOT_FOUND` / `SESSION_CANCELLED` | FR-011 |
| `serverNow < startAt` | `SESSION_STARTED` | FR-011 |
| `venues/{venueId}.status == ACTIVE` | `VENUE_INACTIVE` | FR-011 |
| `session.ownerId != uid` | `OWN_SESSION` | Edge Cases |
| Đăng ký hiện có: `REGISTERED`/`CHECKED_IN` cùng `requestId` → trả bản cũ; khác `requestId` → lỗi; `WAITLISTED` → lỗi (spec 081 xử lý) | `ALREADY_REGISTERED` / `ALREADY_WAITLISTED` | FR-008 |
| `registeredCount + partySize ≤ capacity` | `SESSION_FULL` (kèm `remaining`) | FR-007, Edge Cases |

Khi hợp lệ, trong cùng `commit`:
1. `registeredCount += partySize`; nếu bằng `capacity` thì `status = FULL`.
2. Ghi đăng ký: `REGISTERED`, `amount`, `paymentStatus` (`PAID` nếu `MOCK_PAY`, `UNPAID` nếu `AT_VENUE`), `qrToken`, `ticketCode`, các trường sao chép.

### Quy tắc kiểm tra của `cancel-drop-in`

| Quy tắc | Lỗi | FR |
|---|---|---|
| Đăng ký tồn tại và ID = `uid` người gọi | `NOT_FOUND` | FR-016, AC 4.6 |
| `status == CANCELLED` và cùng `cancelRequestId` → trả kết quả cũ | (200) | FR-018 |
| `status == REGISTERED` (đã check-in thì không hủy được) | `NOT_CANCELLABLE` | FR-016 |
| `serverNow < session.startAt` | `SESSION_STARTED` | AC 4.4 |

Khi hợp lệ, trong cùng `commit`:
1. Đăng ký → `CANCELLED`, `cancelledBy = USER`, `qrToken = null`, `ticketCode = null`; tính `refundAmount` theo [R4](research.md#r4-thanh-toán-giả-lập-và-chính-sách-hủy).
2. `registeredCount -= partySize`; nếu đang `FULL` thì `status = OPEN`.
3. Gọi hook `afterSpotsReleased(tx, session)`: spec 080 để trống; spec 081 đẩy người từ hàng chờ lên trong cùng transaction (081 FR-007).

## 3. `reviews/{sessionId}_{uid}` — C (mở rộng spec 070)

Dùng nguyên schema ở [data-model 070](../070-review-favorite/data-model.md#1-reviewsreviewid--c), với:
- `targetType = DROP_IN`, `sessionId` = ID buổi, `bookingId = null`.
- `completedAt` = `session.endAt`, dùng để tính hạn 7 ngày.

Điều kiện tạo xem [R10](research.md#r10-đánh-giá-buổi-chơi-fr-022-fr-023).

## 4. Collection của người khác mà spec này đọc

| Collection | Trường đọc | Người phụ trách |
|---|---|---|
| `venues/{venueId}` | `status`, `ownerId`, `address`, `location` (nút "Chỉ đường") | D |
| `venues/{venueId}/courts/{courtId}` | `name` (chi tiết buổi) | D |
| `users/{uid}` | `level`, `favoriteDistrictIds`, `accountStatus` | A |
| `districts` | `name` (chip khu vực) | A |
| `bookings` | đơn sắp tới của chính mình (cảnh báo trùng giờ, qua interface của B) | B |

## 5. Index cần thêm vào `firestore.indexes.json`

| Collection | Trường | Dùng cho |
|---|---|---|
| `dropInSessions` | `status`, `startAt` | Danh sách không lọc (FR-001) |
| `dropInSessions` | `status`, `districtId`, `startAt` | Lọc khu vực (thay index `districtId, date, status` ở đặc tả CSDL mục 6) |
| `dropInSessions` | `status`, `date`, `startAt` | Lọc ngày |
| `dropInSessions` | `status`, `timeSlot`, `startAt` | Lọc khung giờ **[MỚI]** |
| `dropInSessions` | `status`, `level`, `startAt` | Lọc trình độ |
| `dropInSessions` | `status`, `startAt`, `pricePerPerson` | Lọc giá tối đa (nhiều bất đẳng thức) |
| `registrations` (collection group) | `userId`, `status`, `sessionStartAt` | Tab "Sắp tới" **[MỚI]** |
| `registrations` (collection group) | `userId`, `sessionStartAt desc` | Tab "Đã qua" **[MỚI]** |
| `registrations` (collection group) | `qrToken` | Check-in bằng QR (Edge Function của D) **[MỚI]** |
| `registrations` (collection group) | `ticketCode` | Check-in bằng mã vé (D) **[MỚI]** |

Tổ hợp nhiều bộ lọc cùng lúc (ví dụ quận + ngày + trình độ) cần index tổ hợp. Firebase Emulator sẽ báo link tạo index còn thiếu khi chạy quickstart. Mỗi tổ hợp thật sự dùng thì ghi thêm vào `firestore.indexes.json`.

## 6. Mô hình phía app (Kotlin, `feature/dropin/domain`)

| Lớp | Nội dung |
|---|---|
| `DropInSession` | `id`, `venueId`, `venueName`, `venueAddress`, `districtName`, `courtIds`, `startAt`, `endAt`, `timeSlot`, `level`, `capacity`, `registeredCount`, `remaining`, `pricePerPerson`, `status`, `note`, `isOwnedBy(uid)` |
| `DropInFilter` | `districtIds: Set<String>`, `date: LocalDate?`, `timeSlot: TimeSlot?`, `level: Level?`, `maxPrice: Long?`, có hàm chuyển thành chip (FR-003) |
| `DropInRegistration` | `sessionId`, `partySize`, `status`, `paymentMethod`, `paymentStatus`, `amount`, `refundAmount`, `qrToken`, `ticketCode`, `sessionChanged`, `sessionStartAt`, `sessionEndAt`, `cancelledBy` |
| `RegistrationOutcome` | `UPCOMING`, `ATTENDED`, `CANCELLED_BY_ME`, `SESSION_CANCELLED`, `NO_SHOW`, tính bằng `RegistrationOutcomeResolver` ([R7](research.md#r7-lượt-vãng-lai-của-tôi-fr-019-fr-020)) |
| `RegisterRequest` | `sessionId`, `partySize`, `paymentMethod`, `requestId` |
| `CancelQuote` | `refundAmount`, `isFree`, `reason` (`EARLY`, `LATE`, `SESSION_CHANGED`), lấy từ `dryRun` |
| `DropInWarning` | `OVERLAP_DROP_IN`, `OVERLAP_BOOKING`, `LEVEL_MISMATCH` |

Tiền là `Long`, thời gian là `Instant`; hiển thị theo `Asia/Ho_Chi_Minh` và dạng `80.000 đ` (FR-027).

---

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.9 `dropInSessions` (C):**
   - Thêm `timeSlot`, `venueSnapshot`.
   - Ghi rõ `status` `OPEN`↔`FULL` do server đổi, `COMPLETED` do cron của spec 081.
   - Spec 112 FR-008 nói "không thêm field". Cần D đồng ý thêm hai trường suy ra này, ghi bằng helper `deriveSessionFields()` (xem bảng phối hợp trong [plan.md](plan.md#phối-hợp-với-nhóm)).
2. **Mục 4.9 `registrations` (C):**
   - Thêm `userId`, `sessionId`, `venueId`, `sessionStartAt`, `sessionEndAt`, `paymentStatus`, `pricePerPerson`, `refundAmount`, `ticketCode`, `cancelRequestId`, `sessionChanged`, `registeredAt`, `cancelledAt`, `cancelledBy`, `checkedInAt`.
   - Đánh dấu mọi trường là 🔒.
   - `paymentMethod`: `MOCK_PAY`, `AT_VENUE`.
3. **Mục 4.7 `reviews`:** sửa câu "Rules chỉ cho tạo khi đơn tương ứng `COMPLETED`" thành "mọi ghi qua Edge Function `review`; sân: đơn `COMPLETED`; buổi vãng lai: đăng ký `CHECKED_IN` và buổi đã kết thúc".
4. **Mục 6:**
   - Thay index `dropInSessions (districtId, date, status)` bằng các index ở mục 5 của file này.
   - Thêm 4 index collection group `registrations`.
5. **Mục 7:**
   - Dòng `dropInSessions`: người chơi R (kể cả khách chưa đăng nhập).
   - Thêm dòng `registrations`: người chơi R của mình (kể cả collection group theo `userId`); owner R đăng ký buổi của mình; admin R; mọi ghi qua Edge Function.
6. **Mục 8a:**
   - `register-drop-in`, `cancel-drop-in` ghi rõ "đăng ký/hủy, trả chỗ; hook hàng chờ của 081".
   - Thêm `_shared/dropin.ts` (helper dùng chung cho 081, 112, `delete-account`).
