# Feature 100: gói quyết định backend/FCM, bản 1 (23/11/2026)

Trạng thái: **ĐÃ DUYỆT D1, D2-W1, T1, C1, P1, S1 ngày 23/11/2026** qua ba câu trả lời của người dùng: “Duyệt D1 + D2-W1”, “Duyệt T1 + P1 với giới hạn đã nêu”, “Duyệt C1 + P1 + S1”. Các mục dưới giữ nguyên mô tả phương án đã trình để truy nguyên phạm vi phê duyệt. Các producer ngoài W1, source outbox/generation, deploy và migration dữ liệu thật chưa được duyệt. Phê duyệt không đồng nghĩa đã triển khai hoặc kiểm chứng.

## Những phần đã chốt, không hỏi lại

- T009: SYSTEM dùng scan user ACTIVE, không deletionRequestedAt; ID theo admin UID/requestId; retry giữ thông báo khớp và bổ sung bản còn thiếu. Nguồn: 120/contracts/admin-repairs.md và admin-broadcast-notification/handler.ts.
- T010: inbox 30 mục, createdAt DESC, snapshot cursor, đọc một lần và refresh. T012/T039: Firestore là nguồn chuẩn; cache DataStore theo UID, chỉ đọc khi offline, cleanup/epoch khi logout/xóa tài khoản.
- GROUP_ACTIVITY: chỉ routing Android đã duyệt. REVIEW_REPLIED là tên chuẩn; REVIEW_REPLY cũ chỉ là alias đọc/routing. BOOKING_NEW luôn push-eligible; PROMOTION thiếu key mặc định tắt. Tắt push không ngăn lưu inbox.
- 011 research R5/P15: tái sử dụng FcmSignOutHook arrayRemove token hiện tại rồi deleteToken trước khi mất auth. Hook chỉ best effort, tổng ngân sách năm giây; không suy ra rotation/provider invalidation luôn thành công.

## D1. Delivery job và receipt từng token

Đề xuất: duyệt mô hình trong delivery-state-proposal.md, với các lựa chọn được cố định dưới đây. Thay đổi CSDL mới là server-only; mọi client, kể cả Admin client, bị từ chối đọc/ghi job và receipt.

| Path/field | Kiểu và ý nghĩa đề xuất |
|---|---|
| notificationDeliveries/{jobId} | jobId = SHA-256 của JSON array các string [producerFamily, eventIdentity, recipientUid, canonicalType] |
| recipientUid, notificationId | String; tham chiếu đúng inbox của user; không sao chép body/token/QR vào job |
| state | PENDING / LEASED / SENT / SKIPPED / FAILED |
| attemptCount | Integer đếm lượt claim để chẩn đoán, không phải ngân sách gửi của từng token |
| nextAttemptAt, createdAt, updatedAt | Timestamp UTC; thời gian của trusted server |
| leaseOwner, leaseUntil | Nonce mới cho mỗi lần claim; Timestamp hoặc null; lease hai phút, heartbeat/complete phải kiểm nonce và hạn |
| lastErrorCode | String allowlist hoặc null; không lưu raw response/token |
| targetHashes | Array<String> hoặc null; danh sách SHA-256 token được chụp một lần, không có raw token |
| initializedThrough | Integer cursor khởi tạo receipt |
| targetsInitializedAt | Timestamp hoặc null; chỉ bắt đầu gửi khi đủ receipt |
| notificationDeliveries/{jobId}/tokens/{tokenHash} | Receipt server-only; tokenHash không phải quyền truy cập |
| receipt state | PENDING / SENDING / SENT / SKIPPED / FAILED |
| receipt attemptCount, nextAttemptAt | Integer và Timestamp/null; tối đa sáu attempt đã reserve cho token |
| receipt attemptId, acceptedAt, lastErrorCode, updatedAt | String/null, Timestamp/null, String/null và Timestamp; acceptedAt chỉ xác nhận FCM nhận request |

Chốt phương án khởi tạo theo trang, không đặt giới hạn mới 100 token/user: đóng băng targetHashes trong transaction đầu, sau đó tạo tối đa 100 receipt và cập nhật cursor trong mỗi transaction. Giữ cùng snapshot khi resume; token đăng ký sau không được thêm vào event cũ. Kiểm ngân sách kích thước document/commit; quá giới hạn thì FAILED có mã lỗi, giữ inbox, không truncate token âm thầm. Danh sách profile vốn bị giới hạn bởi Firestore; cần test cả dữ liệu vượt ngân sách job.

Worker cron mỗi phút, tối đa 50 claim và 50 FCM request trong một invocation; dừng/resume công việc còn lại. Mỗi send phải reserve attempt trong transaction dưới lease đang sở hữu. Retry sau 1/5/15/60/180 phút, tôn trọng Retry-After dài hơn. SENDING của lease hết hạn là outcome không biết: reserve attempt mới nếu còn ngân sách, FAILED khi đã dùng sáu lần; không reset counter. SENT token không bị gửi lại khi token khác lỗi. UNREGISTERED gỡ đúng raw token còn khớp hash khỏi profile, giữ token khác; lỗi payload/auth/config không xóa token.

SENT của parent nghĩa là có ít nhất một token accepted và mọi receipt còn lại SENT/SKIPPED; toàn bộ skipped thì SKIPPED; có terminal failure thì FAILED khi không còn retry/recoverable SENDING. Job/receipt terminal giữ bảy ngày tính từ terminal updatedAt, sau đó xóa child trước parent; không TTL-drop job còn pending. Sau success rồi crash vẫn có thể trùng push; không cam kết exactly-once hoặc device đã hiển thị.

Enqueue phải đọc recipient tồn tại/ACTIVE/không đang xóa trong cùng transaction ghi inbox/job. Claim, init và complete dùng update có guard, không set/merge tạo lại parent đã bị xóa. Account deletion chặn ghi mới qua profile, xóa receipt theo batch rồi parent, final sweep idempotent. Không thu hồi được request FCM đã bay ra trước khi xóa tài khoản.

Ảnh hưởng: CSDL thêm hai path trên, server-only Rules, indexes state/nextAttemptAt, state/leaseUntil và recipientUid nếu query cần; worker HTTP v1 và cleanup. Index được chốt từ query triển khai, không thêm theo đoán. Mở T006 và phần retry/delivery của T020/T025/T048/T049; các phần đó còn cần D2, T1, C1/P1/S1 tương ứng. T054 phải bổ sung test cho storage mới, không dùng kết quả cleanup inbox cũ để nghiệm thu job.

Lựa chọn khác: chỉ lưu aggregate job, chấp nhận gửi lại token đã thành công khi token khác lỗi. Không khuyến nghị vì khó phục hồi partial delivery đúng từng thiết bị.

## D2-W1. Identity và enqueue đợt đầu, giữ inbox hiện có

Đề xuất duyệt **chỉ các dòng W1** dưới đây để bắt đầu shared writer và producer đã có transactional inbox. Helper server chạy trong transaction của producer, không mở transaction lồng và không tin recipient do client gửi. Không tạo thêm một inbox bên cạnh inbox mà producer đã ghi.

| Producer | Identity đề xuất | Read/write boundary và phạm vi |
|---|---|---|
| W1: owner booking command và booking-timeout | family booking, eventIdentity = bookingId + canonical terminal type | Booking chuyển một chiều từ PENDING/CONFIRMED theo state machine. Owner reject và timeout cùng dùng key BOOKING_REJECTED/bookingId; một transition chỉ commit một lần. Recipient lấy từ booking; bỏ user tự hủy. Thêm job ngay cạnh inbox UUID hiện tại trong source transaction |
| W1: waitlist promotion/closure | family waitlist, sessionId + uid + waitlistTicket + type | register-drop-in cấp waitlistNextTicket cho mỗi lần vào hàng; helper có ticket trong read set. Thêm job trong transaction promotion/closure; bỏ callback sau commit đang lặp cùng thông báo. Thiếu ticket legacy thì giữ inbox, không đoán generation để gửi push |
| W1: admin review application | family owner-application, applicationId + submittedAt UTC chuẩn hóa + decision | Source kiểm version trong transaction. Giữ inbox UUID và chèn job cùng commit; pending resubmission có submittedAt mới. Retry trạng thái đã quyết định không phát lại event. Không đổi bước claim recovery |
| W1: SYSTEM | Giữ inbox sys_SHA256(adminUid:requestId); family system, adminUid + requestId | Giảm chunk thành tối đa 100 recipient/transaction, đọc current profile và inbox/job trong transaction. Payload khác cùng requestId trả conflict; giữ isRead/createdAt của inbox khớp. T009 recipient predicate giữ nguyên |

Shared helper kiểm canonical type, identity không rỗng, path/recipient từ server và payload đúng sáu field inbox hiện hành. Với nguồn mới không có inbox writer, có thể dùng ID n_{jobId}; với writer UUID hiện có, job lưu notificationId đó. API helper dùng internal call đã được typed/validated; **không phê duyệt HTTP event envelope chung cho các callback legacy** trong W1.

Quy tắc dedup sau retention: inbox tồn tại thì không tự tạo lại job đã mất. Vì inbox/job mới commit cùng nhau, trường hợp chỉ có inbox có thể là dữ liệu legacy hoặc job terminal đã dọn; không phân biệt bằng suy đoán. Không backfill push cho inbox cũ, không đổi ID/read state, không dựng job mới khi caller replay sau bảy ngày. Repair/manual resend là thao tác riêng cần duyệt, không nằm trong W1. Quy tắc này thay đề xuất cũ tự phục hồi mọi inbox có job bị thiếu.

Mở T004/T005/T017 cho contract/helper của W1 và phần tương ứng T015/T018/T023/T044/T045/T046/T049. Không đóng cả task adapter nếu các type ngoài W1 còn thiếu; reminder scheduler và producer dưới đây vẫn chặn.

### Các producer chưa nằm trong D2-W1

| Producer | Đề xuất tiếp theo, tác động code/schema | Giới hạn và task còn chặn |
|---|---|---|
| BOOKING_NEW | Chọn source commit PENDING (payment/booking flow), key bookingId/PENDING; enqueue trong chính commit đó | Không dùng callback sau commit. Cần xác nhận source-owner trước T018 phần NEW |
| BOOKING_REMINDER | bookingId + earliest items.startAt + reminder kind, một lần khi còn CONFIRMED, hai giờ trước giờ chơi | Chốt query/marker và cách dedup trước T016/T019; không coi D1 là phê duyệt marker |
| REVIEW_REMINDER | bookingId + completed milestone + reminder kind | 070 research có nhắc lại sau năm ngày trong khi 100 yêu cầu một lần; cần chốt nghiệp vụ trước T046 phần reminder |
| OWNER_REVOKED | Thêm requestId ổn định vào API/UI admin; ID theo adminUid/requestId/targetUid; kiểm replay trước đổi role để request cũ không revoke lại sau reapproval | Chưa duyệt API thay đổi; legacy UID/reason không đủ identity cho lần revoke tiếp theo; T046 phần revoked |
| Review create/reply | Đề xuất notificationGeneration UUID server-only trên review, dùng cho create/first-reply; chuyển enqueue vào review transaction | Delete/recreate dùng cùng reviewId, createdAt theo mili-giây không đủ cam kết uniqueness. Cần duyệt field 070 và boundary; T046 review |
| GROUP_ACTIVITY | Request/operation UUID cho hành động lặp, bền vững cùng membership/post transition; source outbox nếu fanout lớn | Approval routing không duyệt operation ID, outbox fields hoặc recipient snapshot. T047 còn chặn |
| CHAT_MESSAGE | chatId/messageId/recipient/type, enqueue trước commit message; replay message có sẵn không gửi lại | messageId đã là requestId trong 091. Duyệt C1 chỉ xác định suppression; cần đồng ý transaction fanout/outbox và quy mô nhóm trước T048 |
| DROPIN_REGISTERED/CHANGED | sessionId + registration/change generation; recipient từ registration hiện hành; enqueue cùng source transition | sessionId đơn lẻ không phân biệt rejoin/chỉnh sửa; T023 phần tương ứng còn chặn |
| PROMOTION | voucher publication generation + eligible follower UID; frozen recipient plan/outbox nếu lớn | Chưa có publication identity, snapshot thời điểm eligibility và fanout boundary; T022/T024/T025 phần promotion |

Outbox cho nguồn fanout lớn chưa có path/fields được duyệt trong D2-W1. Cần snapshot nội dung đủ để tạo title/body nếu source bị xóa trước fanout; không suy ra rằng chỉ lưu sourceId là đủ. Chưa thay state machine hay yêu cầu nghiệp vụ của feature nguồn để phục vụ notification.

## T1. Token lifecycle dùng schema fcmTokens hiện có

Đề xuất tối thiểu: giữ raw token chỉ trong Firebase SDK và users/{uid}.fcmTokens; không thêm registry thiết bị server. Coordinator thuộc Feature 100, bắt đầu từ SignedIn, register/refresh bằng transaction trên đúng user profile, không queue offline. Bảo toàn token máy khác. Local file notification_device_prefs chỉ lưu lastRegisteredUid, tokenHash và rotationRequired; không lưu raw token/mật khẩu. Metadata này không phải credential hay quyền đọc inbox.

- Guest/setup chưa hoàn tất: không register. onNewToken chỉ xử lý khi session hiện tại và OS cho phép thông báo; trước/sau await đều kiểm UID. Callback cũ không được ghi metadata thành công cho tài khoản mới.
- UID mới hoặc rotationRequired: deleteToken và lấy token mới thành công trước khi register; không hoàn tất thì giữ đăng nhập/inbox hoạt động nhưng hoãn push registration. Retry khi có mạng, app resume hoặc callback token mới; không retry vô hạn trong một invocation.
- Cùng UID refresh: thêm token hiện tại và gỡ token cũ có hash khớp local metadata trong cùng transaction, giữ token còn lại. Callback lặp idempotent. Local metadata chỉ đổi sau server ack.
- Logout giữ FcmSignOutHook của 011, không tạo hook gỡ token thứ hai. Coordinator ghi rotationRequired khi SignedOut; giữ lastRegisteredUid để phát hiện đổi UID qua process restart. ACCOUNT_DELETED xóa last UID/hash và giữ rotationRequired=true, server vẫn dọn token/profile.
- OS từ chối/tắt: không register mới, best effort detach token của UID đang dùng; không ảnh hưởng inbox/preferences. FCM data tới trễ vẫn phải qua OS permission và UID guard. Cấp lại quyền/resume: lấy current token và register lại khi session phù hợp.

Rules đề xuất: giữ P15 own-user removal; chỉ owner sửa token list, fields khác vẫn được bảo vệ. Kiểm list, diff chỉ thêm tối đa một token String không rỗng dài tối đa 4096 ký tự và gỡ tối đa một token khác mỗi mutation, cho phép rotate add/remove trong một lần. Đây là giới hạn token entry mới, không giới hạn số thiết bị. Removal-only không buộc dữ liệu legacy phải vượt qua regex/chuẩn mới; không tự migration token list. New registration chuẩn hóa duplicate trong dữ liệu hợp lệ; invalid legacy values cần xử lý riêng, không silently sửa dữ liệu nguồn. Server UNREGISTERED cleanup kiểm đúng token, không xóa token mới.

**Giới hạn cần chấp nhận:** khi offline/timeout, token cũ có thể còn trong profile cũ và provider có thể chuyển data message cũ. P1 bắt buộc data-only, không có title/body nguồn; client bỏ message sai UID trước khi hiển thị/đọc inbox. Không hứa tuyệt đối không còn network traffic cho UID cũ. Nếu cần cắt traffic ở server ngay khi đổi account, phải thiết kế/duyệt registry binding và endpoint handoff riêng; không tự thêm vào T1.

Ảnh hưởng: thêm coordinator/service token, local metadata và Rules diff tests; không thêm server collection cho token. Sửa data-model/011 handoff để ghi đúng guarantee. Mở T011/T051/T052 khi đi cùng P1; không đóng chúng chỉ vì đã có logout hook.

## C1. Chat foreground trên nhiều thiết bị

Đề xuất chọn **bất kỳ thiết bị đang xem chat thì suppress push của cả tài khoản cho chat đó**. Inbox vẫn tạo cho mọi recipient hợp lệ trừ sender, kể cả muted/viewing. Không suy ra suppression từ unreadCounts.

Schema mới đề xuất: chats/{chatId}/participants/{uid}/presence/{installationId}, một field viewingUntil Timestamp. installationId là UUID cục bộ ngẫu nhiên trong notification_device_prefs, không phải FCM token hay khóa phân quyền. Mở chat foreground ghi lease 60 giây theo ước lượng server time, renew mỗi 45 giây; rời màn hình chỉ xóa lease của installation đó, crash chờ expiry. Rules chỉ user đang là chat member được ghi path UID mình, đúng field Timestamp và không quá request.time + 10 phút; deny UID khác. Server kiểm lease còn hạn bằng giờ server trước send và vẫn xét participants.muted riêng.

Lý do cần schema: viewingUntil hiện tại là một field dùng chung, một máy rời chat có thể xóa lease của máy đang xem. Per-device lease tránh overwrite đó. Đổi chat client/repository và cleanup participant/account để dọn presence children. New worker dựa presence mới; viewingUntil aggregate cũ được giữ để tương thích code cũ nhưng không dùng làm guarantee nhiều thiết bị. Client cũ chưa được nâng cấp có thể nhận push khi đang xem; rollout không được mô tả là hỗ trợ nhiều thiết bị đầy đủ cho client cũ.

Lựa chọn khác: giữ field aggregate hiện tại và chấp nhận best effort trên nhiều máy, không đổi schema 091. Cần ghi rõ giới hạn nếu chọn. Per-token/device suppression cần binding token/installation server; không thuộc phương án account-wide này.

Ảnh hưởng/mở khóa: CSDL/Rules 091 và ChatRepository.setViewing, client presence cleanup, chat-send bỏ lọc muted/viewing trước inbox; T008 và suppression của T045/T048. Durable chat enqueue vẫn cần source transaction/fanout decision ở D2 ngoài W1.

## P1 và S1. Privacy payload và quyền SYSTEM

**P1 đề xuất:** FCM data-only với chính xác recipientUid và notificationId (String), không notification block, title/body nguồn, QR, token hay source payload. Android chỉ post khi current signed-in UID khớp, OS cho phép; không xác minh được session thì không post, inbox vẫn còn. Nội dung local là tên SmashNow và câu chung "Bạn có thông báo mới"; visibility PRIVATE, public version cũng chỉ có câu chung. Tap tải lại đúng inbox của UID hiện tại rồi dùng resolver đã kiểm quyền; sai account/source mất quyền dùng inbox hoặc saved detail, không mở URI tùy ý từ FCM.

Message chỉ có generic ID nên handler không cần fetch body để hiện push. FCM data-only khi app bị force-stop hoặc bị hạn chế nền có thể trễ/mất; không cam kết push luôn hiển thị trong mọi trạng thái OS. Server acceptance không phải displayed receipt. Channel local smashnow_notifications có DEFAULT importance; user có toàn quyền tắt/đổi, BOOKING_NEW không bypass OS/DND. T014/P1 không thay quyền runtime T050 đã có.

**S1 đề xuất:** sửa CSDL §7 Admin C thành "gửi SYSTEM qua Edge Function; client không create inbox" cho khớp §4.13 và Feature 120. Giữ verified ADMIN claim + current ACTIVE ADMIN profile ở server; không grant thêm quyền Rules cho Admin client. Recipient predicate/idempotent requestId của T009 giữ nguyên, không hỏi duyệt lại.

P1 mở T014 và payload phần T053; T1 cần P1 để bảo vệ account switch. S1 mở T007/phần authorization T049. C1/P1/S1 đều cần cập nhật contract nguồn trước code phụ thuộc; không phải permission để gửi push thật/deploy.

## Kế hoạch sau khi được duyệt

1. Ghi rõ ID được duyệt, cập nhật CSDL/contracts/decision ledger trước code. Giữ mọi ID chưa được duyệt ở trạng thái pending.
2. D1 + D2-W1: shared tx writer, fake sender/worker với tests idempotency, partial token failure, expired lease/SENDING, retention replay và deletion race. Adapt từng producer W1 theo dependency; không đóng task chứa type chưa hỗ trợ.
3. T1 + P1: coordinator/Rules, payload validation và local handler/channel integration; test logout/refresh/UID switch/OS denial bằng fake + emulator, không FCM thật.
4. C1/S1: cập nhật nguồn 091 và quyền SYSTEM theo đúng lựa chọn. Inbox luôn bền vững độc lập suppression/FCM.

Mọi phần triển khai sau duyệt dùng demo Firebase/Auth emulator, fake FCM và Supabase local khi cần; không tự commit, push, deploy hoặc migration dữ liệu thật. Gói này không duyệt source outbox hoặc các field generation/request mới trong các dòng ngoài W1.

## Phê duyệt tiếp nối ngày 24/11/2026

Người dùng đã duyệt B1/B2 theo booking-decision-package-2026-11-24.md, sau đó duyệt riêng D3/R1/H1 theo continuation-decisions-2026-11-24.md. Các dòng ngoài W1 ở trên chỉ được thay thế cho đúng phần REGISTERED, review create/first reply và DIRECT chat trong gói mới. GROUP chat, GROUP_ACTIVITY, PROMOTION, DROPIN_CHANGED, OWNER_REVOKED và REVIEW_REMINDER vẫn chờ contract riêng. B1 chưa có transaction payment nguồn; không tạo flow thanh toán thay thế.
