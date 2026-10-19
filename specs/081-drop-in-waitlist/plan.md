# Implementation Plan: Hàng chờ buổi vãng lai

**Branch**: `feature/08-drop-in` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/081-drop-in-waitlist/spec.md`

## Summary

Khi buổi vãng lai đã đủ người, người chơi vào hàng chờ (1–4 người, chọn cách thanh toán, chưa trừ tiền) và thấy vị trí của mình theo thời gian thực. Có chỗ trống (người hủy hoặc chủ sân tăng sức chứa) thì hệ thống **tự đẩy người chờ lâu nhất có số người vừa chỗ** lên thành đăng ký chính thức có vé QR. Hàng chờ đóng 30 phút trước giờ bắt đầu hoặc khi buổi bị hủy.

**Cách làm:** dựa hoàn toàn trên dữ liệu và Edge Function của [spec 080](../080-drop-in-session/plan.md) ([research R1](research.md#r1-dùng-lại-dữ-liệu-và-edge-function-của-spec-080)):
- **Dữ liệu:** lượt chờ là document `registrations/{uid}` với `status = WAITLISTED`. Không có collection mới.
- **Vào và rời hàng chờ:** đi qua `register-drop-in` (`joinWaitlist: true`) và `cancel-drop-in`.
- **Đẩy lên:** hàm `promoteFromWaitlist` được gắn vào hook `afterSpotsReleased` mà spec 080 để sẵn. Hàm chạy **trong cùng transaction hủy lượt** (hoặc tăng sức chứa của D), nên không vượt chỗ và không đẩy trùng (R3).
- **Vị trí hàng chờ:** document buổi giữ mảng số vé `waitlistTickets` (chỉ có số, không có danh tính). App tính vị trí từ listener realtime sẵn có (R2).
- **Đóng hàng chờ:** chỉ thêm một Edge Function `dropin-close` chạy bằng pg_cron mỗi phút. Function này đóng hàng chờ lúc T−30 phút và chuyển buổi đã qua sang `COMPLETED` (R7).
- **Thông báo:** dùng `DROPIN_SPOT_OPENED` và `DROPIN_WAITLIST_CLOSED` đã chốt trong spec 100 (R12).

## Technical Context

**Language/Version**: Kotlin (Kotlin tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function; SQL (pg_cron) cho lịch gọi `dropin-close`

**Primary Dependencies**: giống spec 080 (Fragment/Navigation, ViewBinding, Material 3, ViewModel/StateFlow, Coroutines, Hilt, Firebase Auth/Firestore, supabase-kt Functions). Không thêm thư viện mới.

**Storage**: Cloud Firestore, gồm `dropInSessions` (thêm `waitlistNextTicket`, `waitlistTickets`, `waitlistCount`) và `registrations` (thêm `waitlistTicket`, `promotedAt`, `waitlistEndReason`). Supabase dùng pg_cron và Edge Functions, không lưu dữ liệu nghiệp vụ.

**Testing**: `deno test` trên Firebase Emulator (Q1–Q14, gồm **5 lượt hủy với 10 người chờ chạy đồng thời, lặp 20 lần**); test Rules W1–W2; unit test ViewModel/UseCase (JUnit4, MockK, Turbine)

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime + pg_cron

**Project Type**: Mobile app (Android) + server function (Supabase)

**Performance Goals**:
- Vào hàng chờ < 30 giây (SC-001).
- Người được đẩy lên có vé ≤ 10 giây sau khi có người hủy (SC-002). Thực tế là ngay khi transaction commit.
- Vị trí cập nhật realtime.

**Constraints**:
- Không vượt sức chứa; mỗi lượt chờ được đẩy lên tối đa một lần (FR-008).
- Mốc 30 phút luôn kiểm tra theo giờ server trong transaction, không phụ thuộc lúc cron chạy.
- Không ghi khoản phí nào cho lượt chờ không được nhận (SC-005).

**Scale/Scope**: Quy mô đồ án (vài chục người chờ mỗi buổi). Mở rộng 4 màn hình của spec 080, thêm 1 màn hình mới. Mở rộng 2 Edge Function, thêm 1 function chạy theo lịch. Không thêm Rules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Nằm trong `feature/dropin` của spec 080; thêm `WaitlistEntryFragment`/`ViewModel`, UseCase `JoinWaitlist`, `LeaveWaitlist`; ViewModel cung cấp `StateFlow<UiState>` |
| II. Bảo mật phía server | ✅ | Thứ tự, vị trí, trạng thái lượt chờ đều 🔒 do Edge Function ghi; danh tính người chờ được Rules của spec 080 che (FR-006); `dropin-close` yêu cầu `CRON_SECRET` |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ | Vào hàng chờ, rời hàng chờ, đẩy lên, đóng hàng chờ đều chạy trong Firestore transaction trên document buổi; `requestId` idempotent; số vé do server cấp; tác vụ định kỳ chạy ở server (pg_cron); mốc thời gian kiểm tra lại trong transaction |
| IV. Kiểm thử có trọng tâm | ✅ | Test đồng thời Q6 (SC-003), rời hàng chờ cùng lúc bị đẩy lên Q7, mốc hoàn tiền Q12, đóng hàng chờ Q10 ([quickstart.md](quickstart.md)) |
| V. Giao diện XML nhất quán | ✅ | Dùng lại layout và chuỗi của spec 080 (`dropin_waitlist_*`); 4 trạng thái; `w600dp` |
| VI. Quyền riêng tư | ✅ | Mảng `waitlistTickets` công khai chỉ chứa số; người chơi khác không đọc được lượt chờ; xóa tài khoản thì rời hàng chờ |
| VII. Đơn giản, minh bạch AI | ✅ | Không có collection mới, không thư viện mới; chỉ thêm 1 function chạy theo lịch (bắt buộc để báo đúng mốc); log AI trong `docs/ai-log/AI_log_3.md` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thiết kế chỉ thêm 6 trường 🔒 (3 trên buổi, 3 trên đăng ký) và 2 index.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **D** (spec 112) | 1. Sau khi **tăng sức chứa**, gọi `promoteFromWaitlist(tx, session, serverNow)` trong cùng transaction, rồi gửi `DROPIN_SPOT_OPENED` cho danh sách `promoted`.<br>2. Khi **hủy buổi**, gọi `closeWaitlistInTx(tx, session, "SESSION_CANCELLED")` (cùng với `cancelRegistrationInTx` của spec 080), rồi gửi `DROPIN_WAITLIST_CLOSED`.<br>3. Màn hình chủ sân hiển thị `waitlistCount` và mốc đóng hàng chờ 30 phút.<br>4. Đồng ý 3 trường `waitlist*` trên `dropInSessions` (do server ghi, spec 112 FR-008 ghi "không thêm field"). | Họp 16/10 |
| **D** (spec 100) | `DROPIN_SPOT_OPENED` (`data.sessionId`, `data.autoLeftSessionIds`) gửi cho người được đẩy lên. `DROPIN_WAITLIST_CLOSED` (`data.sessionId`, `data.reason`: `CLOSED` hoặc `SESSION_CANCELLED`) gửi cho người đang chờ. Vào hoặc rời hàng chờ không gửi thông báo. | Họp 16/10 |
| **A** | `delete-account` gọi `leaveWaitlistInTx(..., "LEFT", "SYSTEM")` cho các lượt đang chờ (Edge Cases: người chờ xóa tài khoản). | Họp 16/10 |
| **B** | `_shared/firestore.ts` cần hỗ trợ `runQuery` **bên trong transaction** (REST `runQuery` có trường `transaction`) để lấy danh sách chờ trong cùng transaction (R3). | Họp 16/10 |
| Cả nhóm | Bật extension `pg_cron` và `pg_net` trên Supabase; thêm job `dropin-close` mỗi phút, lưu `CRON_SECRET` trong secrets. | Trước khi code |
| C (mình) | Cập nhật đặc tả CSDL theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd); gắn `promoteFromWaitlist` vào hook của spec 080. | Trước họp 16/10 |

## Project Structure

### Documentation (this feature)

```text
specs/081-drop-in-waitlist/
├── spec.md
├── plan.md                           # file này
├── research.md                       # Phase 0 (R1–R13)
├── data-model.md                     # Phase 1 (chỉ phần mở rộng so với spec 080)
├── quickstart.md                     # Phase 1
├── contracts/
│   ├── edge-functions-waitlist.md    # register/cancel mở rộng, dropin-close, _shared/waitlist.ts, test Q1–Q14, W1–W2
│   └── ui-screens.md                 # màn hình mở rộng + WaitlistEntryFragment
├── ui/                               # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                          # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

Chỉ liệt kê những gì thêm hoặc sửa so với [plan 080](../080-drop-in-session/plan.md#source-code-repository-root):

```text
app/src/main/java/com/example/booking_app/feature/dropin/
├── domain/
│   ├── model/WaitlistPosition.kt                 # mới
│   ├── model/ (DropInSession, DropInRegistration, RegistrationOutcome, RegisterRequest, CancelQuote)  # thêm trường
│   ├── RegistrationOutcomeResolver.kt            # đọc waitlistEndReason
│   └── usecase/JoinWaitlist.kt, LeaveWaitlist.kt # mới
├── data/DropInRegistrationRepositoryImpl.kt      # joinWaitlist, leaveWaitlist (expectWaitlisted)
└── ui/
    ├── waitlist/WaitlistEntryFragment.kt, WaitlistEntryViewModel.kt   # mới
    ├── detail/, confirm/, ticket/, mine/                               # mở rộng
app/src/main/res/layout/fragment_dropin_waitlist.xml                    # mới
app/src/main/res/values/strings_dropin.xml                              # thêm dropin_waitlist_*

supabase/functions/
├── _shared/waitlist.ts, waitlist.test.ts         # promoteFromWaitlist, closeWaitlistInTx, leaveWaitlistInTx
├── _shared/dropin.ts                             # afterSpotsReleased → promoteFromWaitlist; computeRefund thêm PROMOTED_GRACE
├── register-drop-in/index.ts                     # nhánh joinWaitlist
├── cancel-drop-in/index.ts                       # nhánh WAITLISTED + gửi DROPIN_SPOT_OPENED
└── dropin-close/index.ts, index.test.ts          # mới
supabase/migrations/<timestamp>_dropin_close_cron.sql   # lịch pg_cron gọi dropin-close

firestore.indexes.json                            # registrations(status, waitlistTicket), dropInSessions(status, endAt)
firestore-tests/test/dropInSessions.test.ts                 # W1, W2
scripts/seed/081-waitlist.ts
```

**Structure Decision**:
- **App:** cùng package `feature/dropin` với spec 080, vì hàng chờ là phần mở rộng của luồng đăng ký.
- **Server:** logic hàng chờ tách thành `_shared/waitlist.ts` để D (spec 112) và A (`delete-account`) gọi được mà không phụ thuộc phần còn lại của 080.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
