# Phân công tuần 1–2: đặc tả tính năng và thiết kế CSDL

Áp dụng từ 05/10 đến 16/10/2026 (mốc **M1**). Phân công nhóm tính năng giữ nguyên theo [team contract](../team-contract.md#1-thành-viên-và-phân-công). File này chia nhỏ hai việc của tuần 1–2:

1. **Đặc tả các nhóm tính năng:** mỗi người viết `spec.md` cho các nhóm của mình bằng Spec Kit.
2. **Thiết kế CSDL:** mỗi người phụ trách một phần collection trong [đặc tả CSDL chung](../design/database/README.md) và viết `data-model.md` cho spec của mình.

## 1. Danh sách spec và thư mục được cấp sẵn

Spec Kit tự tăng số thứ tự, nên khi 4 người tạo spec cùng lúc trên các nhánh khác nhau sẽ bị trùng số. Để tránh việc này, mỗi spec được **cấp sẵn thư mục** theo quy tắc `NN0`–`NN9` (NN là mã nhóm tính năng). Khi chạy `/speckit-specify`, ghi rõ thư mục ở đầu mô tả:

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/040-booking-slot-grid <mô tả tính năng>
```

### A — Tài khoản, tìm kiếm sân, chi tiết sân

| Thư mục spec | Phạm vi | Nhóm |
|---|---|---|
| `specs/010-account-auth` | Đăng ký (đồng ý điều khoản, không chọn vai trò), điều hướng theo vai trò sau đăng nhập, đăng nhập Email/SĐT/Google, OTP, quên và đặt lại mật khẩu | 01 |
| `specs/011-account-profile` | Xem/sửa hồ sơ, đổi mật khẩu, đăng xuất, xóa tài khoản, chuyển chế độ người chơi/quản lý sân, xem trạng thái hồ sơ chủ sân | 01 |
| `specs/020-court-search` | Tìm theo tên/quận, bộ lọc (giá, giờ trống, tiện ích), sắp xếp, lịch sử tìm kiếm | 02 |
| `specs/021-court-map` | Bản đồ với marker quả cầu lông, thẻ tóm tắt, gợi ý sân gần vị trí hiện tại | 02 |
| `specs/022-ai-court-search` | **Tìm sân thông minh (AI Assistant):** nhập câu tự do hoặc giọng nói, AI phân loại ý định (`search`/`faq`/`off_topic`/`unclear`) và trích bộ lọc, điền chip bộ lọc cho người dùng sửa, hỏi lại tối đa 1 lượt, giới hạn lượt dùng mỗi ngày, quay về bộ lọc thủ công khi lỗi. Đặc tả nghiệp vụ gốc: [AI-feature.md](../proposal/AI-feature.md) | 02 |
| `specs/030-court-detail` | Thư viện ảnh, bảng giá, tiện ích, giờ mở cửa, liên hệ, bản đồ nhúng, chỉ đường, đánh giá tổng quan, nút "Đặt sân" | 03 |

### B — Đặt sân, thanh toán và khuyến mãi, quản lý lịch đặt

| Thư mục spec | Phạm vi | Nhóm |
|---|---|---|
| `specs/040-booking-slot-grid` | Chọn ngày, lưới khung giờ theo sân, chọn nhiều sân/khung giờ, giữ chỗ 5 phút, màn hình xác nhận, chống trùng lịch | 04 |
| `specs/041-weekly-recurring-booking` | Đặt cố định theo tuần, xử lý khi một tuần bị trùng | 04 |
| `specs/050-payment-voucher` | Chọn phương thức (tại sân, đặt cọc, ví giả lập), áp voucher, hóa đơn điện tử, tự hủy đơn quá hạn | 05 |
| `specs/060-my-bookings` | Danh sách theo trạng thái, chi tiết đơn, hủy theo chính sách, đề nghị đổi lịch, mã QR check-in, đặt lại nhanh | 06 |

### C — Đánh giá và yêu thích, đặt lịch vãng lai, cộng đồng và chat

| Thư mục spec | Phạm vi | Nhóm |
|---|---|---|
| `specs/070-review-favorite` | Chấm sao theo tiêu chí, bình luận kèm ảnh, chỉ đánh giá sau khi hoàn thành đơn, chủ sân phản hồi, sân yêu thích | 07 |
| `specs/080-drop-in-session` | Danh sách, bộ lọc, đăng ký theo lượt (kèm bạn), thanh toán theo lượt, vé QR, hủy, lịch sử, đánh giá buổi chơi | 08 |
| `specs/081-drop-in-waitlist` | Hàng chờ khi đủ người, tự đẩy người lên khi có người hủy, thông báo | 08 |
| `specs/090-community-group` | Tạo/tham gia nhóm, bảng tin (bài đăng, sự kiện), báo cáo bài đăng | 09 |
| `specs/091-chat` | Chat nhóm realtime, chat riêng với chủ sân, gửi ảnh, báo cáo tin nhắn | 09 |

### D — Thông báo, quản lý sân (chủ sân), quản trị

| Thư mục spec | Phạm vi | Nhóm |
|---|---|---|
| `specs/100-notification` | FCM, các loại thông báo, nhắc lịch, trung tâm thông báo, đánh dấu đã đọc, bật/tắt từng loại | 10 |
| `specs/110-owner-onboarding` | Nộp hồ sơ chủ sân (thông tin cơ sở, ghim bản đồ, giấy tờ), theo dõi trạng thái, mở khóa chức năng sau khi duyệt | 11 |
| `specs/111-owner-venue-setup` | Thêm/sửa/ẩn cơ sở, quản lý sân con, bảng giá theo khung giờ và thứ, khóa giờ | 11 |
| `specs/112-owner-operations` | Duyệt/từ chối đơn, lịch tổng hợp ngày/tuần, mở buổi vãng lai và check-in QR, tạo voucher, thống kê doanh thu | 11 |
| `specs/120-admin` | Duyệt/từ chối hồ sơ chủ sân, thu hồi quyền, khóa cơ sở, quản lý người dùng, xử lý báo cáo, danh mục, thống kê, thông báo hệ thống | 12 |

> **Về spec 022:** AI chỉ hiểu câu và điền bộ lọc, không tự đặt sân; kết quả vẫn đi qua truy vấn của spec 020 (và 080 nếu `type` là buổi vãng lai). Phần gọi LLM chạy bằng **Supabase Edge Function** `ai-search-parse` (constitution v1.2.0), API key chỉ nằm trong secrets của Edge Function.

> Nếu một spec quá lớn khi viết, được tách thêm trong dải số của nhóm (ví dụ `112` → `112` + `113-owner-statistics`) và báo trong nhóm chat. Mọi spec của một nhóm phải được liệt kê trong `docs/features/NN-.../README.md`.

## 2. Phần CSDL của từng người

Bảng đầy đủ ở [mục 2 của đặc tả CSDL](../design/database/README.md#2-danh-sách-collection-và-người-phụ-trách). Người phụ trách viết bảng trường, index, Rules và danh sách test Emulator cho collection của mình.

| Người | Collection phụ trách | Việc chung về CSDL |
|---|---|---|
| **A** | `users`, `searchHistory`, `districts`, `amenities`, `aiUsage`, `aiSearchCache` | Chốt quy ước chung (mục 1); cách sinh `nameKeywords` và `geohash` cho tìm kiếm; luồng xóa tài khoản; nối Firebase Auth với Supabase (`on-signup`); **AI Assistant:** Edge Function `ai-search-parse`, `aiUsage` (lượt dùng mỗi ngày) và `aiSearchCache` (cache câu đã hỏi) |
| **B** | `slots`, `bookings`, `vouchers` (+ `redemptions`) | Thiết kế transaction giữ chỗ/đặt sân, máy trạng thái đơn, chiến lược dọn slot hết hạn (`expire-holds`); module `_shared/firestore.ts` cho Edge Functions |
| **C** | `reviews`, `favorites`, `dropInSessions` (+ `registrations`), `groups`, `chats` | Transaction đăng ký vãng lai và hàng chờ; cập nhật `ratingAvg`; bucket `review-photos`, `group-media`, `chat-media` |
| **D** | `ownerApplications`, `venues` (+ `courts`, `priceRules`, `dailyStats`), `reports`, `notifications`, `systemStats` | Luồng duyệt chủ sân (custom claim); bảng phân quyền mục 7; bucket Supabase và Edge Functions `sign-upload`/`sign-download` mục 8 |

### Các điểm giao giữa hai người

Những chỗ này phải được hai bên thống nhất **trước khi chạy `/speckit-plan`**, ghi kết quả vào mục *Clarifications* của cả hai spec.

| Điểm giao | Người | Cần thống nhất |
|---|---|---|
| Trường của `venues` dùng cho tìm kiếm | A ↔ D | `districtId`, `amenityIds`, `minPricePerHour`, `ratingAvg`, `geohash`, `nameKeywords`: ai ghi, ghi lúc nào |
| Hồ sơ chủ sân hiển thị trong tài khoản | A ↔ D | Trạng thái lấy từ `users.ownerStatus` hay `ownerApplications` |
| Tính giá từ bảng giá | B ↔ D | Định dạng `priceRules`, cách tính khi khung giờ nằm giữa hai rule |
| Khóa giờ, mở buổi vãng lai | B ↔ D ↔ C | Ghi `slots` với `status = BLOCKED`, `blockReason` |
| Duyệt đơn, voucher của chủ sân | B ↔ D | Chuyển trạng thái `PENDING → CONFIRMED/REJECTED`; schema `vouchers` |
| Chỉ đánh giá sau khi chơi | B ↔ C | Điều kiện `bookings.status == COMPLETED`, ai chuyển sang `COMPLETED` |
| Báo cáo vi phạm | C ↔ D | `reports.targetType`, `targetPath` cho bài đăng, tin nhắn, đánh giá |
| AI tìm cả buổi vãng lai (`type`) | A ↔ C | Bộ lọc của `dropInSessions` mà AI được phép điền (khu vực, ngày, giờ, trình độ, giá); màn hình kết quả nào hiển thị |
| Nội dung FAQ cho AI (`intent = faq`) | A ↔ B | Văn bản chính sách hủy, đặt cọc, voucher lấy từ spec 050, 060 để hiển thị cố định, không do AI sinh |
| Danh mục cho AI kiểm tra đầu ra | A ↔ D | Tên quận và tiện ích hợp lệ lấy từ `districts`, `amenities`; khung giá hợp lệ |
| Thông báo | Cả nhóm → D | Mỗi người gửi D danh sách `type` thông báo mình phát sinh trước **14/10** |

## 3. Lịch làm việc

| Hạn | Việc | A | B | C | D |
|---|---|---|---|---|---|
| **T4 07/10** | Đọc đặc tả CSDL chung, comment vào PR nếu thấy thiếu | ✔ | ✔ | ✔ | ✔ |
| **T5 08/10** | `spec.md` cho các spec của mình (bản đầu) | 010, 011, 020, 021, **022**, 030 | 040, 041, 050, 060 | 070, 080, 081, 090, 091 | 100, 110, 111, 112, 120 |
| **T6 09/10** (họp) | Trình bày spec; chốt câu hỏi mục 9 của đặc tả CSDL (độ dài slot, duyệt đơn...); **chọn nhà cung cấp LLM cho AI Assistant** (phải có gói miễn phí, không bắt buộc gắn thẻ, theo constitution v1.2.0) | ✔ | ✔ | ✔ | ✔ |
| **CN 11/10** | `/speckit-clarify` xong; thống nhất các điểm giao ở mục 2; ảnh tham khảo vào `docs/features/NN-.../screenshots/` | ✔ | ✔ | ✔ | ✔ |
| **T3 13/10** | Wireframe `ui/`; `/speckit-plan` sinh `data-model.md` khớp với đặc tả CSDL chung | ✔ | ✔ | ✔ | ✔ |
| **T4 14/10** | Cập nhật bảng trường, index, Rules phần mình vào `docs/design/database/README.md`; gửi D danh sách loại thông báo | ✔ | ✔ | ✔ | ✔ |
| **T5 15/10** | `/speckit-tasks` + `/speckit-analyze`; người tích hợp tuần 2 (**C**) ghép sơ đồ ER chung | ✔ | ✔ | ✔ + ER | ✔ |
| **T6 16/10** (họp M1) | Duyệt sơ đồ ER và đặc tả CSDL; cập nhật cột *Spec* trong [features/README.md](../features/README.md) | ✔ | ✔ | ✔ | ✔ |

## 4. Review chéo

Mỗi spec cần ít nhất một người khác review qua PR (tiêu chí M1). Phân công cố định để không bỏ sót:

| Người viết | Người review spec | Người review phần CSDL |
|---|---|---|
| A | B | D (vì A đọc dữ liệu `venues` của D) |
| B | C | D (vì B dùng `priceRules`, khóa giờ của D) |
| C | D | B (vì C phụ thuộc `bookings`) |
| D | A | B (vì D duyệt đơn và tạo voucher của B) |

Khi review spec, kiểm tra: có user story và tiêu chí chấp nhận đo được; có luồng lỗi (mất mạng, hết slot, bị từ chối); có trạng thái rỗng/lỗi; không trái [constitution](../../.specify/memory/constitution.md), nhất là nguyên tắc II và III.

## 5. Gợi ý mô tả cho `/speckit-specify`

Mô tả nên nêu: vai trò người dùng, các chức năng trong phạm vi (lấy từ `docs/features/NN-.../README.md`), những gì **không** làm, và các collection liên quan. Ví dụ:

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/040-booking-slot-grid
Người chơi đặt sân cầu lông theo khung giờ. Từ chi tiết sân, người chơi chọn ngày trên lịch,
xem lưới khung giờ của từng sân con (trống / đã đặt / bị khóa), chọn nhiều sân và nhiều khung
giờ liên tiếp trong một đơn, xem màn hình xác nhận với tổng tiền tính theo bảng giá của sân.
Khi xác nhận, các khung giờ được giữ 5 phút trong lúc thanh toán; hết hạn thì tự nhả. Hai người
đặt cùng một khung giờ thì chỉ một người thành công, người kia nhận thông báo và lưới được làm
mới. Bấm xác nhận nhiều lần không tạo đơn trùng. Luồng đặt không quá 5 bước.
Không bao gồm: đặt cố định theo tuần (spec 041), thanh toán và voucher (spec 050).
Dữ liệu: slots, bookings, venues/courts/priceRules theo docs/design/database/README.md.
```

Ví dụ cho spec AI Assistant:

```
/speckit-specify SPECIFY_FEATURE_DIRECTORY=specs/022-ai-court-search
Người chơi tìm sân bằng một câu tự nhiên (gõ hoặc nói), ví dụ "tối mai 7h-9h sân gần Quận 10
dưới 100k có gửi xe". AI phân loại ý định (tìm sân, hỏi chính sách, lạc đề, thiếu thông tin) và
trích bộ lọc: quận, ngày, giờ bắt đầu/kết thúc, giá tối đa, tiện ích, loại (sân thường hay buổi
vãng lai). Kết quả hiện thành chip bộ lọc để người dùng sửa trước khi tìm; dùng lại truy vấn của
spec 020. Câu thiếu thông tin: hỏi lại 1 lần bằng chip chọn nhanh. Câu hỏi chính sách: hiện nội
dung FAQ có sẵn. Câu lạc đề: hiện thông báo cố định. Giới hạn số lượt mỗi người mỗi ngày; câu
rỗng hoặc quá 200 ký tự bị chặn ngay trên app; che số điện thoại/email trước khi gửi; lỗi hoặc
mất mạng thì quay về bộ lọc thủ công. AI không tự đặt sân.
Không bao gồm: bộ lọc thủ công và danh sách kết quả (spec 020), bản đồ (spec 021).
Dữ liệu và quy tắc: docs/proposal/AI-feature.md; districts, amenities, venues theo
docs/design/database/README.md.
```
