# Kiểm thử thủ công spec 091 (chat) - 20/11/2026

Máy ảo Android 1280x2856 (480 dpi), Firebase Emulator `mobile-22e6c`, router Edge Function local, 1 máy ảo (bên kia dùng `send-as.sh` hoặc đổi tài khoản). Không sửa code. Ảnh nằm cùng thư mục này.
Ký hiệu: Đạt / Không đạt / Một phần / Không kiểm được.

## 1. Quickstart mục 3 (Q1-Q16)

| # | Kết quả | Bằng chứng | Ghi chú |
|---|---|---|---|
| Q1 | Một phần | q1-venue-detail, q1-chat-opened, q1-sent | Mở chat từ chi tiết sân V1, gửi được. Đầu màn hình hiện "Chủ sân 1" + avatar chữ "S1"; tên + ảnh V1 nằm ở thẻ "Đang hỏi về" (ảnh trống vì seed không có ảnh). Tổng ~37 giây (07:13:50 -> 07:14:27) nhưng bị kéo dài do lỗi bàn phím che ô nhập (L1), nên không kết luận SC-002. |
| Q2 | Đạt | q2-owner-list, q2-owner-chat, q2-owner-replied, q2-player3-sees-reply | owner1 (Quản lý sân > Tin nhắn khách hàng) thấy cuộc với "Người chơi 3" kèm "Hỏi về ... Cơ sở 1/2". Trả lời -> có trong Firestore sau ~0,5 s kể từ lúc bấm gửi; đăng nhập player3 thấy tin trả lời. SC-001 (<= 3 s trên 2 máy thật, 4G) không đo được. |
| Q3 | Đạt | q3-v2-same-chat | Nhắn từ V2: vẫn cuộc cũ (2 tin trong cùng cuộc), thẻ đổi sang "Cơ sở 2". |
| Q4 | Không đạt | q4-owner-own-venue, q4-owner-tap-chat | owner1 mở chi tiết V1 của mình: nút "Nhắn tin cho chủ sân" VẪN HIỆN (L2). Bấm thì snackbar "Đây là cơ sở của bạn" (không tạo chat). |
| Q5 | Đạt | q5-group-open, q5-group-received, q5-group-order | Mở chat nhóm G1: 30 tin sau ~2-3 s. Tin từ player1 (send-as) hiện trong lúc đang mở, đúng thứ tự, có tên người gửi (ảnh = chữ viết tắt "CY", seed không có avatar). Tin gửi từ player3 (UI) và player2 (send-as) đúng thứ tự. Thấy ngay ở lần dump đầu (<= ~2 s sau khi lệnh gửi trả về). Chỉ 1 máy nên không kiểm "2 máy khác cùng mở". |
| Q6 | Đạt | q6-joined-g1, q6-player7-group-chat, q6-player7-oldest, q6-player7-list | player7 vào G1 (deep link chi tiết nhóm), mở chat nhóm: cuộn lên thấy từ "Tin nhóm số 1" đến 35, danh sách không có huy hiệu chưa đọc. Xem L5. |
| Q7 | Đạt | q7-player4-kicked, q7-player4-list, q7-player4-reopen | player1 mời player4 ra khỏi nhóm; player4 đăng nhập: danh sách trống ("Bạn chưa có cuộc trò chuyện nào"), deep link chat nhóm không mở lại được. Chưa đo thời gian biến mất khi player4 đang mở sẵn (SC-003). |
| Q8 | Một phần | q8-owner-chat-read, q8-after-2-msgs, q8-opened-chat, q8-after-read | Đang ở danh sách, nhận 2 tin (send-as owner1): cuộc nhảy lên đầu, số "2"; mở cuộc -> số biến mất. "Huy hiệu giảm" không kiểm được: chế độ người chơi không có huy hiệu Tin nhắn ngoài danh sách (L3). |
| Q9 | Một phần | q9-menu, q9-muted, q9-list-unread | "Tắt thông báo" -> icon chuông gạch ở danh sách; tin mới vẫn tăng số chưa đọc ("1"). Thông báo đẩy: FCM không có ở local, không kiểm được. |
| Q10 | Đạt | q10-sending, q10-airplane-image, q10-image-failed | Bật máy bay, chọn ảnh, gửi: "Đang gửi" rồi sau ~35-40 s "Gửi lỗi" + "Gửi lại"/"Xóa" (banner "Đang mất kết nối mạng"). Firestore không có tin ảnh hỏng. Không bật máy bay đúng giữa lúc tải được (ảnh nhỏ, local) nên bật trước khi gửi. "Xóa" hoạt động. |
| Q11 | Đạt | q11-offline-pending, q11-after-reconnect | Máy bay, gửi chữ: "Đang gửi"; đợi 20 s, tắt máy bay: tự gửi, Firestore có đúng 1 tin. |
| Q12 | Đạt | q12-menu-recent, q12-recalled, q12-menu-old | Tin vừa gửi: menu giữ lâu có "Sao chép / Thu hồi" -> "Tin nhắn đã được thu hồi" (Firestore RECALLED, text rỗng nên bên kia thấy tương tự). Tin cũ > 15 phút (07:01): menu chỉ có "Sao chép". Chưa xem tận mắt trên máy thứ hai. |
| Q13 | Đạt | q13-report-sheet, q13-report-1, q13-report-2 | Lần 1: "Đã gửi báo cáo"; lần 2: "Bạn đã báo cáo tin này". Bấm gửi khi chưa chọn lý do: "Chọn một lý do báo cáo" (t055-report-no-reason). |
| Q14 | Đạt | q14-blocked-chat, q14-send-blocked, q14-owner-menu, q14-unblocked, q14-send-after-unblock | player2 gửi: tin "Gửi lỗi" và banner "Bạn không thể gửi tin trong cuộc trò chuyện này" (L6: chỉ hiện SAU lần gửi lỗi). owner1 "Bỏ chặn" (có hộp thoại xác nhận) -> player2 gửi được. |
| Q15 | Đạt | q15-attach-menu, q15-camera-prompt, q15-camera-denied, q15-picker | Thu hồi quyền CAMERA, bấm "Chụp ảnh", chọn "Don't allow": hộp thoại "Cần quyền camera - Không có quyền camera. Bạn vẫn có thể chọn ảnh từ thư viện." -> "Chọn ảnh" mở thư viện; nhắn chữ vẫn được. |
| Q16 | Một phần | xem T055 | Chế độ tối, w600dp, mất mạng: không crash, giữ nội dung soạn khi ở màn hình. Thiếu trạng thái "loading" và "lỗi + Thử lại" của danh sách; sheet báo cáo offline không báo lỗi (L7). |

## 2. T055

| Mục | Kết quả | Bằng chứng | Ghi chú |
|---|---|---|---|
| Danh sách: content | Đạt | q5-group-order, q2-owner-list, q9-list-unread | |
| Danh sách: empty | Đạt | q7-player4-list | "Bạn chưa có cuộc trò chuyện nào" + 2 nút. |
| Danh sách: loading | Không kiểm được | - | Tải < 1 s, không chụp được. |
| Danh sách: error + Thử lại | Không kiểm được | - | Offline vẫn đọc cache Firestore; không có cách ép lỗi mà không xóa dữ liệu. |
| Màn chat: content | Đạt | q5-group-order | |
| Màn chat: empty | Đạt | q1-chat-opened | "Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện!" |
| Màn chat: error + Thử lại | Đạt | t055-chat-error, t055-chat-error-coldlink | "Vui lòng đăng nhập để tiếp tục" + "Thử lại"; bấm Thử lại sau khi đăng nhập thì tải được (nguyên nhân: L4). |
| Màn chat: loading | Không kiểm được | - | Không chụp kịp. |
| Sheet báo cáo: content | Đạt | q13-report-sheet | |
| Sheet báo cáo: lỗi nhập | Đạt | t055-report-no-reason | |
| Sheet báo cáo: loading / error khi mất mạng | Không đạt | t055-report-loading, t055-report-offline-error, t055-report-after-reconnect | Gửi báo cáo khi máy bay: sau ~15 s không có thông báo lỗi, sheet vẫn mở (L7). |
| Mất mạng không crash, giữ nội dung đang soạn | Một phần | t055-offline-draft | Bật máy bay lúc đang soạn: nội dung còn nguyên, banner "Đang mất kết nối mạng...". Rời màn chat (Back) rồi mở lại thì ô nhập trống (L8, Thấp). `adb logcat -b crash`: không có crash của app. |
| Chế độ tối | Đạt | t055-dark-chat, t055-dark-list, t055-dark-report-sheet | Tương phản ổn, không lỗi màu. |
| w600dp (density 240) | Đạt | t055-w600-chat, t055-w600-list, t055-w600-long, t055-w600-report-sheet | Tin dài chiếm ~63% chiều ngang (bong bóng ~812/1280 px), < 70%. Sheet báo cáo thành hộp giữa màn hình, đọc được. |
| contentDescription | Một phần | ui dump | Có: "Ảnh trong tin nhắn", "Ảnh cơ sở ...", "Bỏ ảnh đã chọn", "Gửi ảnh", "Gửi tin nhắn", "Tùy chọn cuộc trò chuyện", "Quay lại", "Đóng". Thiếu: avatar (chỉ chữ viết tắt "CY"/"S1"), icon chuông tắt thông báo ở danh sách chưa thấy mô tả riêng (L9). |
| Vùng chạm >= 48dp | Đạt | ui dump | Mọi nút kiểm được (back, more, đính kèm, gửi, Gửi lại, Xóa, Xem sân, các lý do báo cáo, nút gửi báo cáo, nút chat ở chi tiết sân, đóng sheet) có bounds >= 144 px = 48dp ở 480 dpi. |

## 3. SC-006

| Phép đo | Kết quả | Ghi chú |
|---|---|---|
| Mở chat nhóm + 30 tin đầu | Đạt (~2,3-2,6 s) | 3 lần, từ lệnh `am start` đến lần dump đầu thấy tin (đã gồm ~0,5 s khởi chạy lệnh + ~0,8 s mỗi lần dump). Giá trị thực < 2,6 s. Emulator local, không phải 4G. |
| Quay lại danh sách trò chuyện | Đạt (~2,0 s) | 3 lần, cùng cách đo. |

SC-001 (4G, 2 máy thật) và SC-007 (5 người thật): không đo được.

## 4. Danh sách lỗi

| # | Mức | Lỗi | Bước tái hiện | Thực tế vs mong đợi | Ảnh |
|---|---|---|---|---|---|
| L1 | Cao | Bàn phím che ô nhập tin và nút gửi ở màn chat | Mở bất kỳ chat > chạm ô nhập | Ô nhập/nút gửi nằm sau bàn phím (bounds y 2580-2760, bàn phím từ y 1848): người dùng không thấy chữ đang gõ; Enter chèn xuống dòng. Mong đợi: layout co theo IME. Trong code, `ChatFragment.kt` dòng 58-59 chỉ lấy `WindowInsetsCompat.Type.systemBars()`, không xử lý `ime()`. Cần xác nhận trên máy thật. | q1-keyboard-covers |
| L2 | Thấp | Nút "Nhắn tin cho chủ sân" vẫn hiện ở cơ sở của chính chủ sân | owner1 mở `smashnow://venue/demo091-v1` | Mong đợi (AC 1.5): không có nút. Thực tế: có nút, bấm ra snackbar "Đây là cơ sở của bạn". `CourtDetailFragment.kt:464` chỉ ẩn khi `book == null`. | q4-owner-own-venue |
| L3 | Vừa | Chế độ người chơi không có lối vào danh sách "Tin nhắn" và huy hiệu chưa đọc | Đăng nhập người chơi, xem Trang chủ/Cộng đồng/Tài khoản | Không có icon/mục Tin nhắn; danh sách chỉ tới được bằng cách vào một chat rồi Back (`smashnow://chat` không phân giải bằng adb, `smashnow://chat/` mở chat rỗng rồi Back ra danh sách). Chủ sân có tab Tin nhắn. Mong đợi (US3): vào được danh sách + huy hiệu tổng. | q0-list-player3 |
| L4 | Cao | Mở deep link khi app tắt hẳn (cold start) bị đẩy sang màn Đăng nhập dù đã đăng nhập | player2 đã đăng nhập > `adb shell am force-stop` > `am start -d smashnow://chat/group/demo090-g1` | Thực tế: màn Đăng nhập; Back thì chat báo "Vui lòng đăng nhập để tiếp tục". Mở app bình thường (không deep link) vẫn đăng nhập (đã xác nhận). Nhiều khả năng ảnh hưởng đường mở từ thông báo đẩy khi app tắt; có thể là lỗi chung của nav gating, không riêng 091. Trước đó có 1 lần phiên mất sau khi mở lại app lúc đang bật máy bay (chưa tái hiện độc lập). | t055-chat-error-coldlink, t055-chat-error |
| L5 | Thấp | Chi tiết nhóm không làm mới sau "Tham gia" (nút vẫn "Tham gia", chưa có "Chat nhóm") dù số thành viên tăng | player7 mở chi tiết G1 > Tham gia | Phải thoát và mở lại mới thấy "Chat nhóm". Thuộc spec 090. | q6-joined-g1 |
| L6 | Thấp | Trạng thái chặn chỉ báo sau khi gửi lỗi; sau khi "Xóa" tin lỗi, ô nhập mở lại không có banner | player2 mở chat với owner1 (đã chặn) | Mong đợi: ô nhập khóa kèm banner ngay khi mở. Thực tế: ô nhập mở, gửi -> "Gửi lỗi" + banner. | q14-send-blocked |
| L7 | Vừa | Báo cáo tin khi mất mạng không có phản hồi | Mở sheet báo cáo > chọn lý do > bật máy bay > Gửi báo cáo | Sau ~15 s: không snackbar lỗi/loading, sheet vẫn mở; người dùng không biết kết quả. | t055-report-offline-error |
| L8 | Thấp | Nháp tin không giữ khi rời màn chat | Gõ chữ > Back > mở lại chat | Ô nhập trống. (Spec chỉ đòi giữ khi mất mạng; phần đó đạt.) | - |
| L9 | Thấp | Avatar/icon thiếu contentDescription | Xem ui dump | Avatar chỉ có chữ viết tắt; TalkBack đọc ký tự vô nghĩa. | q5-group-open |
| L10 | Thấp (seed) | Tên người gửi hiện "Người chơi yer1/yer2..." trong khi hồ sơ là "Người chơi 1/2" | Mở chat nhóm | Nhiều khả năng do seed 091 (`senderSnapshot`), không phải lỗi app. | q5-group-open |

## 5. Không kiểm được

Thông báo đẩy FCM (Q9), SC-001 (2 máy thật, 4G), SC-007 (5 người thật), "2 máy khác cùng mở" (Q5), trạng thái loading (không chụp kịp), error + Thử lại của danh sách, ảnh thật trên Storage (seed không có object ảnh).

## 6. Khôi phục

Máy bay tắt, `uimode night no`, `wm density reset` (480). Đang đăng nhập player3. Dữ liệu Emulator giữ nguyên nhưng đã thay đổi: thêm tin Q5/Q8/Q9/Q11/Q12 và 1 tin thu hồi, player7 vào G1, player4 bị mời ra G1, owner1 bỏ chặn player2, player1 đã tắt thông báo chat nhóm G1 (Q9), quyền CAMERA đã thu hồi.

## Kiểm thử lại sau sửa (vòng 2)

Bản debug mới (commit `a8fc555`, `97d9310`). Lưu ý: lúc bắt đầu app đang ở trạng thái khách (phiên không còn dù "dữ liệu giữ nguyên"), phải đăng nhập lại player3; mọi phép thử L4 bên dưới đều làm khi đã đăng nhập. Ảnh `r2-*.png`.

| Mục | Kết quả | Ảnh | Ghi chú |
|---|---|---|---|
| L1 chat nhóm | Đạt | r2-l1-group-keyboard, r2-l1-group-sent | Bàn phím lên (từ y 1848), ô nhập/nút gửi bounds y 1680-1824 nằm ngay trên bàn phím; gõ, gửi được; tin cuối vẫn thấy. |
| L1 chat riêng | Đạt | r2-l1-direct-keyboard, r2-l1-direct-sent | Như trên. |
| L1 w600dp (density 240) | Đạt | r2-l1-w600-keyboard | Ô nhập sát trên bàn phím, thấy tin cuối. |
| L3 mục Nhóm/Tin nhắn | Đạt | r2-l3-community, r2-l3-messages | Có "Nhóm \| Tin nhắn"; Tin nhắn hiện chat nhóm G1 + chat với chủ sân, không có cuộc nào mình là chủ. Chân trang chú thích. |
| L3 huy hiệu | Đạt | r2-l3-unread-list, r2-l3-unread-badge-groups, r2-l3-after-read | send-as: 2 tin owner + 1 tin nhóm -> badge tab "2" (đếm số cuộc), từng cuộc "2" và "1"; mở 1 cuộc -> tab "1". Badge hiện cả khi đang ở mục Nhóm. |
| L3 chuyển qua lại / Back / xoay / tối | Đạt | r2-l3-dark, r2-l3-rotate | Giữ mục Tin nhắn khi bật tối và xoay ngang; chuyển qua lại nhiều lần OK; Back ra Trang chủ; không crash. |
| L4 cold start `chat/<chatId>` | Đạt | r2-l4-direct | Vào đúng màn chat, không sang Đăng nhập. |
| L4 cold start `chat/group/demo090-g1` | Đạt | r2-l4-group | Đúng. |
| L4 cold start `chat/owner` (owner1) | Đạt | r2-l4-owner | Vào "Tin nhắn khách hàng". `chat/<id>` của owner1 cũng đúng (r2-l4-owner-direct). |
| L4 so sánh `venue/demo091-v1` cold | Đạt | r2-l4-venue, r2-q4-owner-venue | Vào chi tiết sân; không còn lỗi chung ở các deep link thử. |
| L7 báo cáo khi máy bay | Đạt | r2-l7-offline-3s, r2-l7-offline-17s, r2-l7-retry-ok | Sau 3 s và 17 s vẫn hiện khung lỗi "Không có kết nối mạng. Kiểm tra mạng rồi thử lại." + nút đổi thành "Thử lại". Tắt máy bay, bấm Thử lại: sheet đóng, Firestore có `reports/MESSAGE_...` mới. |
| Q4/L2 | Đạt | r2-q4-owner-venue, r2-q4-player-venue | owner1 ở V1 chỉ có "Đặt sân", không có nút chat; player3 vẫn thấy "Nhắn tin cho chủ sân". |
| L9 avatar | Đạt | (ui dump) | Avatar chữ có content-desc: "Ảnh đại diện của Người chơi yer2", "Ảnh đại diện của CLB Cầu Lông Quận 7"; tài khoản "Tài khoản". |
| L5 Tham gia nhóm | Đạt | r2-l5-before-join, r2-l5-after-join | player4 vào "Pickleball Cuối Tuần 4": chi tiết tự đổi sang có "Chat nhóm", "Rời nhóm", "Đăng bài" (2 thành viên). Xem N2. |
| Q1/SC-002 | Đạt | r2-q1-timing | Lần chạy script: mở chi tiết V1 -> bấm chat -> gõ -> gửi, tin hiện sau tổng ~5 s (đã gồm các sleep). Thực tế người dùng ước < 30 s. |
| T055 loading | Không kiểm được | - | Chụp ngay sau force-stop chỉ ra màn khác/đã tải xong. |
| T055 danh sách error + Thử lại | Không kiểm được | r2-t055-list-offline | Máy bay + cold start: app dừng ở màn "Chưa chuẩn bị xong tài khoản... Thử lại" (màn phiên, không phải của danh sách chat); Thử lại sau khi có mạng vào được. Không ép được lỗi danh sách (Firestore đọc cache). |
| Hồi quy | Đạt | r2-reg-image-preview, r2-reg-image-sent, r2-reg-recalled | Gửi chữ (nhóm+riêng), gửi ảnh (nhóm), thu hồi ảnh, chat nhóm hoạt động. `logcat -b crash`: không có crash. |

### Lỗi mới / còn lại

| # | Mức | Mô tả | Tái hiện |
|---|---|---|---|
| N1 | Thấp-Vừa (chưa chắc) | Sau khi cold start deep link chat rồi đổi `wm density reset` (activity tạo lại) và nhấn Back 2 lần, app kẹt ở màn splash "SmashNow - Đang tải" (r2-n1-back-stuck-launcher). Tái hiện lại không đổi density: Back từ chat cold-link ra danh sách Tin nhắn, bình thường. Có thể do recreate; cần kiểm lại. | force-stop > mở `smashnow://chat/<id>` > đổi density (recreate) > Back x2 |
| N2 | Thấp (môi trường) | Nhóm "Pickleball Cuối Tuần 4" chưa có `chats/group_...` (trigger tạo chat nhóm không chạy ở local). Sau khi Tham gia, bấm "Chat nhóm" ra snackbar "Bạn không còn trong cuộc trò chuyện". Danh sách Khám phá còn hiện "1 thành viên" (cũ) sau khi vào. | player4 Tham gia nhóm mới > Chat nhóm |
| L6, L8, L10 | - | Không sửa theo báo cáo sửa; không kiểm lại. | |

Khôi phục: máy bay tắt, night no, density 480, xoay tự động bật. Đang đăng nhập player3. Dữ liệu thêm: tin r2/L1/timing, 1 ảnh thu hồi, 1 báo cáo mới, player4 vào "Pickleball Cuối Tuần 4".
