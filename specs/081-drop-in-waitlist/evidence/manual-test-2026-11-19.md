# Kiểm thử thủ công spec 081 (2026-11-19)

Máy ảo `emulator-5554` (Pixel 9 Pro, Android 17/API 37), app debug `com.example.booking_app`, Firebase Emulator `mobile-22e6c` + router Deno `:8787`. Chỉ 1 máy: app đăng nhập lần lượt player3, player5, player6, player8; "máy khác" giả lập bằng curl gọi Edge Function với ID token của Auth Emulator.

## Chuẩn bị môi trường (không phải lỗi của 081)
- App không gọi được Emulator (Auth báo "Không có kết nối mạng") vì quyền `android.permission.ACCESS_LOCAL_NETWORK` (Android 17, targetSdk 37) chưa cấp. Đã `adb shell pm grant com.example.booking_app android.permission.ACCESS_LOCAL_NETWORK`. Mọi máy ảo mới đều cần bước này (nên ghi vào quickstart 080/081 mục 1; có thể khai báo permission trong manifest debug).
- Máy ảo ban đầu đăng nhập chế độ chủ sân (tài khoản duckdee); đã đăng xuất, dùng tài khoản test.
- Seed `081-waitlist.ts` chạy lại (mặc định project `demo-smashnow`, phải đặt `FIREBASE_PROJECT_ID=mobile-22e6c`) vì W3 đã qua mốc T-30. Lần chạy lại thứ 2 in stack trace ở `commit` một lần nhưng dữ liệu vẫn đúng; lần thứ 3 sạch. Chưa rõ nguyên nhân (nghi chạy trùng với đọc/ghi khác).
- Tạo tài khoản Auth `guest001/003/004@test.local` (localId `demo081-guest-00N`, claim appRole=PLAYER) để hủy thay người đăng ký giả.

## Kết quả

| # | Kết quả | Mô tả | Ảnh |
|---|---|---|---|
| M1 | Đạt (giả lập một phần) | player3 vào hàng chờ trên app (vị trí 1); player4, player5 vào bằng curl. Màn của player3 tự đổi 1/1 thành 1/3 không cần tải lại. Buổi vẫn "Còn 0 chỗ". Có dòng "Chưa giữ chỗ, chưa thanh toán" và "Số tiền sẽ ghi nhận khi được nhận: 60.000 đ". Chi tiết buổi hiện "Bạn đang ở vị trí thứ 1". | M1-01..05 |
| M2 | Đạt (giả lập một phần) | Đăng nhập player5 (vị trí 3). player4 rời bằng curl `expectWaitlisted`: refundAmount 0, `waitlistEndReason: LEFT`. Màn "Lượt chờ" của player5 đổi sang 2/2 (kiểm khi mở màn). Xem LỖI 1 về thẻ trong "Lượt vãng lai của tôi". | M2-01..03 |
| M3 | Đạt (giả lập một phần), 2 phần không kiểm được | guest-003 (2 người) hủy bằng curl, `promotedCount: 2` (player3 và player5 cùng vừa chỗ). Máy của player5 thấy vé "Đã đăng ký · 1 người" + nhãn "Được nhận từ hàng chờ" khi dump sau cancel khoảng 3 s. Thông báo "Đã có chỗ cho bạn": không kiểm được (`send-notification` của D không có local, người chơi không có FCM token, không thấy bản ghi thông báo). Không thử ca chỉ người đầu được nhận vì seed không có đăng ký 1 người. | M3-01 |
| M4 | Đạt | player6 chờ W1 và W2; guest-001 (4 người) hủy bằng curl; player6 thành REGISTERED ở W1, W2 thành `CANCELLED` + `AUTO_LEFT_OVERLAP`. Trên app tab "Đã qua" hiện "Đã rời hàng chờ (trùng giờ)". Xem LỖI 2. | M4-01 |
| M5 | Đạt (gọi tay cron) | `dropin-close` với `x-cron-secret: local-cron` trả `closedSessions 1, closedEntries 2`. Lượt chờ W3 của player6 chuyển "Không được nhận chỗ" ở "Đã qua". Chi tiết W3: không có nút "Vào hàng chờ", chỉ nút xám "Đã đủ người". Thông báo `DROPIN_WAITLIST_CLOSED`: không kiểm được (như M3). Xem LỖI 2, 4. | M5-01..05 |
| M6 | Đạt | Buổi còn chỗ (Còn 10 chỗ): chỉ có nút "Đăng ký", không có "Vào hàng chờ". | M6-01 |
| M7 | Đạt (snackbar không có trong ảnh chụp: biến mất trước khi chụp; nội dung lấy từ uiautomator dump lúc chạy, cần quay video để có minh chứng hình) | Màn vào hàng chờ chọn 3 người, bật chế độ máy bay, bấm xác nhận: snackbar "Không có kết nối mạng. Lựa chọn của bạn vẫn được giữ, hãy thử lại." + nút "Thử lại"; số người vẫn 3. Tắt máy bay, bấm lại: vào hàng chờ vị trí 1/1; `waitlistCount` = 1 (1 lượt). Không crash. | M7-01..04 |
| M8 | Đạt | Tối (chi tiết buổi, màn vào hàng chờ, màn lượt chờ) và w600dp (1600x2560, density 320) đều đọc được, không vỡ bố cục. Màn "Lượt chờ" w600 căn giữa, giới hạn bề rộng. Xem LỖI 5 (nhỏ). | M8-01..08 |
| T050 | Đạt một phần | Xem bảng dưới. | T050-* |

### T050 chi tiết

| Hạng mục | Kết quả |
|---|---|
| Content | Đạt (màn vào hàng chờ, màn lượt chờ, thẻ hàng chờ trong chi tiết buổi) |
| Empty | Đạt. Chi tiết: "Chưa có ai trong hàng chờ" + gợi ý. Màn lượt chờ khi không có lượt (xóa doc bằng REST): "Bạn không có lượt chờ ở buổi này." + "Tìm buổi khác". Sau khi rời: "Bạn đã rời hàng chờ. Không có khoản nào bị trừ." |
| Error + "Thử lại" | Không kiểm được trên máy ảo: lỗi chỉ phát sinh khi flow Firestore ném exception, ngoại tuyến thì đọc cache. Qua code: cả hai Fragment dùng `UiState.Error(..., viewModel::retry)`. Lỗi mạng khi xác nhận (M7) có "Thử lại" và hoạt động. |
| Loading | Không chụp được (quá nhanh). Qua code có `Loading`. |
| Mất mạng | Mở chi tiết buổi khi máy bay: hiện dữ liệu cache, không crash. M7 giữ lựa chọn. `adb logcat -b crash`: rỗng. |
| Tối / w600dp | Đạt (M8). |
| Vùng chạm >= 48dp | Đạt cho các control đo được trong uiautomator: nút −/+ 144 px = 48 dp (480 dpi); "Vào hàng chờ", "Xem lượt chờ", "Rời hàng chờ", "Thử lại" snackbar, "Chỉ đường", tab: cao >= 144 px; hộp thoại "Ở lại hàng chờ"/"Rời hàng chờ" 96 px = 48 dp ở 320 dpi. Nút Quay lại 168 px (56 dp). |

## Đo SC
- SC-001: tự động hóa (mở card FULL, "Vào hàng chờ", xác nhận, thấy "Vị trí") mất khoảng 5,8 s gồm các sleep cố định. Người thật khoảng 3 lần bấm, rõ ràng < 30 s. Đạt. Với người đã trong hàng chờ, mở chi tiết thấy "Bạn đang ở vị trí thứ N" ngay (~1-2 s).
- SC-002: curl hủy xong lúc t+0,27 s; dump máy sau đó ~3,6 s tổng thì màn "Lượt chờ" đã tự chuyển sang "Vé vãng lai" (ảnh SC002-after). Ca M3 tương tự khoảng 3 s. Đạt (<= 10 s).
- SC-006 chưa làm (cần người thật).

## Lỗi và nhận xét

1. (Trung bình) Thẻ "Đang chờ – vị trí N" trong "Lượt vãng lai của tôi" không cập nhật khi vị trí đổi. Tái hiện: player5 chờ ở vị trí 3, mở "Lượt vãng lai của tôi", player4 rời hàng chờ; thẻ vẫn ghi vị trí 3 sau >= 7 s và cả khi thoát vào lại màn đó; màn "Lượt chờ" thì ghi 2/2. Nghi ngờ: `LoadMyDropInsImpl.upcoming()` (`feature/dropin/domain/usecase/LoadMyDropIns.kt`) chỉ `mapLatest` theo danh sách đăng ký và đọc buổi một lần qua `sessionLookup.getByIds` nên `session.waitlistTickets` bị cũ. Kỳ vọng: theo dõi cả buổi (listener), hoặc bỏ số vị trí khỏi thẻ.
2. (Thấp) Tab "Đã qua" của "Lượt vãng lai của tôi" không tự làm mới (`past()` phân trang một lần): lượt W3 bị đóng chỉ xuất hiện sau khi thoát vào lại màn. Tab "Sắp tới" thì realtime. Có thể chấp nhận, ghi rõ.
3. (Thấp) Lượt `AUTO_LEFT_OVERLAP` mở từ "Đã qua" đi tới "Vé vãng lai" với "Lượt này không còn hiệu lực" (có số tiền, QR) thay vì chi tiết lượt chờ; có thể gây hiểu nhầm.
4. (Thấp) Hàng chờ đã đóng (W3, người không có lượt): chi tiết chỉ có nút xám "Đã đủ người", không có chữ "Hàng chờ đã đóng". Ghi nhận cho UX, spec chỉ yêu cầu nút biến mất.
5. (Rất thấp) w600dp: màn "Vào hàng chờ" trải full chiều ngang (800 dp) còn màn "Lượt chờ" giới hạn bề rộng; nút "Rời hàng chờ" ở dưới rộng hơn cột nội dung. Không vỡ nhưng chưa đồng nhất.
6. Ghi chú: xóa tay lượt chờ bằng REST (mục T050 empty) làm `waitlistTickets` của buổi còn "vé ma" (đếm 1 người chờ). Chỉ do thao tác kiểm thử, không phải lỗi; đã seed lại để dọn.
7. Lệch nhỏ: Seed hiện giờ buổi "23:30" trong khi quickstart/nhiệm vụ ghi 23:00 — không phải lỗi app.

## Giả lập bằng curl (không thao tác trên app)
M1: player4, player5 vào hàng chờ. M2: player4 rời. M3: guest-003 hủy. M4: player6 vào W1 và W2 (vào hàng chờ), guest-001 hủy. M5: `dropin-close`. M7 và các thao tác khác của app đều trên máy ảo. SC-002: guest-004 hủy bằng curl.

## Trạng thái máy ảo sau test
airplane tắt, uimode night no, `wm size/density reset` (1280x2856 / 480), wifi bật lại; đã cấp quyền ACCESS_LOCAL_NETWORK. Tài khoản đang đăng nhập: player8. Dữ liệu 081 đã seed lại sạch (hàng chờ trống); W3 bắt đầu sau 35 phút kể từ lần seed cuối (đã qua mốc T-30 sau ~5 phút).
