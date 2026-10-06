# Thỏa thuận làm việc nhóm (Team Contract)

Thỏa thuận này áp dụng cho 4 thành viên nhóm 6, dự án **SmashNow**, trong **10 tuần** từ 05/10/2026 đến 13/12/2026. Mọi thành viên đọc, đồng ý và ký tên ở [mục 8](#8-xác-nhận-của-thành-viên) trước buổi họp đầu tiên.

Lịch trong file này thay cho kế hoạch 12 tuần ở mục 11 của [Proposal](proposal/Proposal.html). Phân công nhóm tính năng giữ nguyên theo mục 10 của proposal.

## 1. Thành viên và phân công

| Mã | Họ tên | MSSV | GitHub | Nhóm tính năng phụ trách |
|---|---|---|---|---|
| A | | | | 01 Tài khoản · 02 Tìm kiếm sân · 03 Chi tiết sân |
| B | | | | 04 Đặt sân · 05 Thanh toán và khuyến mãi · 06 Quản lý lịch đặt |
| C | | | | 07 Đánh giá và yêu thích · 08 Đặt lịch vãng lai · 09 Cộng đồng và chat |
| D | | | | 10 Thông báo · 11 Quản lý sân (chủ sân) · 12 Quản trị (admin) |

Mỗi người chịu trách nhiệm trọn vẹn nhóm tính năng của mình: spec, giao diện, logic, kiểm thử, tài liệu và demo.

**Việc chung** có một người đứng đầu, luân phiên mỗi tuần theo thứ tự A → B → C → D:

| Việc chung | Nội dung |
|---|---|
| Thư ký họp | Ghi biên bản, tổng hợp số commit, viết báo cáo tuần |
| Tích hợp | Merge `develop`, giải quyết xung đột, kiểm tra build chạy được trước buổi họp |

| Tuần | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Thư ký | A | B | C | D | A | B | C | D | A | B |
| Tích hợp | B | C | D | A | B | C | D | A | B | C |

## 2. Họp hàng tuần

- **Thời gian:** tối thứ Sáu, 20:00 – 21:30 (cả nhóm có thể thống nhất đổi giờ, nhưng vẫn họp tối thứ Sáu).
- **Hình thức:** online (Google Meet/Discord) hoặc trực tiếp. Bật camera khi demo.
- **Hạn chót trước họp:** push code và mở PR trước **18:00 thứ Sáu**. Commit sau giờ này được tính vào tuần sau.

### Chương trình họp

| # | Nội dung | Thời lượng | Người chủ trì |
|---|---|---|---|
| 1 | Tổng hợp commit và PR trong tuần (xem [mục 3](#3-thống-kê-commit)) | 10 phút | Thư ký |
| 2 | Demo: mỗi người demo chức năng đã làm trên máy thật hoặc emulator, từ nhánh `develop` hoặc nhánh PR | 4 × 10 phút | Từng thành viên |
| 3 | Vấn đề gặp phải, việc bị chặn, cần ai hỗ trợ | 15 phút | Cả nhóm |
| 4 | Chốt kế hoạch tuần sau, phân công review PR | 15 phút | Thư ký |

Tuần 1 và 2 chưa có code, nên phần demo là trình bày spec, wireframe và sơ đồ CSDL.

### Sau buổi họp

Trước **23:59 Chủ nhật**, thư ký tạo `docs/reports/weekly/YYYY-Www.md` theo [mẫu](reports/weekly/_template.md), mở PR vào `develop` và nộp lên Moodle. Bảng ở [mục 4](#4-kế-hoạch-10-tuần) ghi sẵn tên file của từng tuần.

## 3. Thống kê commit

Chỉ tính commit trên nhánh `develop` và các nhánh `feature/*` đã mở PR. Không tính merge commit.

```bash
git fetch --all
# Số commit từng người trong tuần (ví dụ tuần 3)
git shortlog -sne --no-merges --all --since="2026-10-17 18:00" --until="2026-10-23 18:00"
```

Số liệu được đối chiếu với **GitHub Insights → Contributors** và ghi vào mục 2 của báo cáo tuần.

**Cam kết tối thiểu mỗi người mỗi tuần:**

- Ít nhất **5 commit có nội dung** theo [quy ước Git](guides/git-workflow.md). Commit chỉ sửa khoảng trắng, đổi tên biến hàng loạt hoặc chia nhỏ một thay đổi một cách giả tạo thì không được tính.
- Từ tuần 3: ít nhất **1 PR** được mở và **review ít nhất 1 PR** của người khác.
- Có **demo** trong buổi họp.
- Có **log AI** trong `docs/ai-log/` nếu tuần đó có dùng AI.

## 4. Kế hoạch 10 tuần

| Tuần | Thời gian | Họp T6 | Báo cáo | Mốc |
|---|---|---|---|---|
| 1 | 05/10 – 11/10 | 09/10 | `2026-W41.md` | |
| 2 | 12/10 – 18/10 | 16/10 | `2026-W42.md` | **M1: Đặc tả + UI/UX + CSDL hoàn tất** |
| 3 | 19/10 – 25/10 | 23/10 | `2026-W43.md` | |
| 4 | 26/10 – 01/11 | 30/10 | `2026-W44.md` | |
| 5 | 02/11 – 08/11 | 06/11 | `2026-W45.md` | **M2: Đăng nhập + dữ liệu sân + đặt sân cơ bản** |
| 6 | 09/11 – 15/11 | 13/11 | `2026-W46.md` | |
| 7 | 16/11 – 22/11 | 20/11 | `2026-W47.md` | |
| 8 | 23/11 – 29/11 | 27/11 | `2026-W48.md` | **M3: Đóng băng tính năng** |
| 9 | 30/11 – 06/12 | 04/12 | `2026-W49.md` | **M4: Bản beta** |
| 10 | 07/12 – 13/12 | 11/12 | `2026-W50.md` | **M5: Bản nộp cuối** |

### Tuần 1–2: Đặc tả, thiết kế UI/UX và CSDL

Mục tiêu: mỗi nhóm tính năng có đặc tả đầy đủ, wireframe và mô hình dữ liệu trước khi viết code. Mọi sản phẩm đặt trong `specs/NNN-<ten>/` ở gốc repo (ngoài `docs/`) và được sinh bằng [GitHub Spec Kit](https://github.com/github/spec-kit).

Mỗi người làm cho 3 nhóm tính năng của mình theo [quy trình tính năng](guides/feature-workflow.md), dừng ở bước sinh task:

| Bước | Lệnh Spec Kit | Sản phẩm trong `specs/NNN-<ten>/` |
|---|---|---|
| Viết đặc tả | `/speckit-specify` | `spec.md`: user story, luồng chính/phụ, tiêu chí chấp nhận |
| Làm rõ yêu cầu | `/speckit-clarify` | Mục *Clarifications* trong `spec.md` |
| Thiết kế UI/UX | (thủ công, Figma) | `ui/`: wireframe PNG đặt tên `NN_<nhom>_buocX_mo-ta.png`, link Figma trong `ui/README.md` |
| Thiết kế CSDL và kế hoạch kỹ thuật | `/speckit-plan` | `plan.md`, `data-model.md` (collection Firestore, trường, index, Rules), `contracts/` |
| Chia task | `/speckit-tasks` | `tasks.md` |
| Kiểm tra độ khớp | `/speckit-analyze` | Sửa các chỗ spec, plan, tasks mâu thuẫn nhau |

| Tuần | Việc của từng thành viên (A, B, C, D) | Việc chung |
|---|---|---|
| 1 | `spec.md` + `/speckit-clarify` cho cả 3 nhóm; chụp ảnh app tham khảo vào `docs/features/NN-.../screenshots/` | Rà lại [constitution](../.specify/memory/constitution.md); thống nhất design system (màu, font, component) trên Figma; tạo nhánh `develop` |
| 2 | Wireframe trong `ui/`; `/speckit-plan` để sinh `data-model.md`; `/speckit-tasks` + `/speckit-analyze` | Ghép các `data-model.md` thành sơ đồ ER chung ở `docs/design/database/`; tạo project Firebase (gói Spark) và project Supabase (gói Free); cập nhật cột *Spec* trong [features/README.md](features/README.md) |

**Tiêu chí hoàn thành M1 (họp 16/10):** đủ 12 thư mục spec có `spec.md`, `ui/`, `plan.md`, `data-model.md`, `tasks.md`; sơ đồ ER chung đã được cả nhóm duyệt; mỗi spec được ít nhất 1 thành viên khác review qua PR.

### Tuần 3–10: Triển khai

Thứ tự triển khai dựa trên phụ thuộc: tài khoản và dữ liệu sân phải có trước khi đặt sân, và đặt sân phải có trước khi làm đánh giá, thông báo, thống kê.

| Tuần | A | B | C | D | Việc chung |
|---|---|---|---|---|---|
| 3 | Khung app: `core/`, Hilt, Navigation, theme; điều hướng theo vai trò | Firebase Emulator, Supabase local, Security Rules nền; `_shared/firestore.ts` và thử nghiệm transaction giữ slot | Design system trong code (`themes.xml`, `colors.xml`, component dùng chung); script dữ liệu mẫu | Nối Firebase Auth với Supabase, bucket ảnh, `sign-upload`; 11: tạo cơ sở/sân, ảnh sân | CI GitHub Actions: `lint testDebugUnitTest assembleDebug`; job giữ Supabase không bị tạm dừng |
| 4 | 01: đăng ký, đăng nhập Email/Google, quên mật khẩu | 04: chọn ngày, lưới khung giờ | 07: yêu thích, danh sách sân yêu thích | 11: bảng giá theo khung giờ, khóa giờ | Nhập dữ liệu mẫu lên Firebase |
| 5 | 01: OTP, hồ sơ, chuyển chế độ, trạng thái hồ sơ chủ sân | 04: chọn nhiều slot, giữ chỗ 5 phút, xác nhận; **test 2 người đặt cùng slot** | 07: đánh giá sân (sau khi đã chơi), báo cáo đánh giá | 12: duyệt hồ sơ chủ sân, quản lý người dùng | Demo M2: đăng nhập → xem sân → đặt sân |
| 6 | 02: tìm theo tên/quận, bộ lọc, sắp xếp, lịch sử tìm kiếm | 05: phương thức thanh toán giả lập, đặt cọc, voucher | 08: danh sách và chi tiết buổi vãng lai, đăng ký | 11: duyệt đơn, lịch tổng hợp ngày/tuần | |
| 7 | 02: bản đồ marker, gợi ý sân gần; 03: thư viện ảnh, bảng giá | 06: lịch đặt sắp tới/đã qua, hủy theo chính sách, vé QR | 08: hàng chờ, hủy, QR check-in | 10: FCM, thông báo đặt/hủy/nhắc lịch | |
| 8 | 03: tiện ích, bản đồ nhúng, chỉ đường; nối chi tiết sân → đặt sân | 04: đặt cố định theo tuần; 06: hoàn thiện | 09: nhóm cộng đồng, chat realtime, báo cáo nội dung | 10: cài đặt thông báo; 11: thống kê doanh thu; 12: kiểm duyệt nội dung, thống kê hệ thống | **Đóng băng tính năng**: sau buổi họp 27/11 chỉ sửa lỗi |
| 9 | Kiểm thử chéo nhóm của B; Espresso cho luồng đăng nhập | Kiểm thử chéo nhóm của C; test Emulator cho đặt lịch | Kiểm thử chéo nhóm của D; Espresso cho đăng ký vãng lai | Kiểm thử chéo nhóm của A; Espresso cho duyệt chủ sân | Sửa lỗi; kiểm tra 4 trạng thái màn hình, chế độ tối, màn hình `w600dp`; merge `develop` → `main` làm bản beta |
| 10 | Báo cáo kỹ thuật: kiến trúc, nhóm 01–03 | Báo cáo kỹ thuật: giải pháp chống trùng lịch, kết quả kiểm thử | Hướng dẫn cài đặt; nhóm 07–09 | Kịch bản và quay video demo 5–7 phút (3 vai trò) | Sửa lỗi cuối; tag `v1.0`; tập dượt bảo vệ |

Mỗi nhóm tính năng phải đạt **Definition of Done** trong README của nhóm đó trước khi đổi trạng thái thành ✅ trong [features/README.md](features/README.md).

**Khi thiếu thời gian** (theo mục 15 proposal): ưu tiên nhóm 01–08 và 11; rút gọn phạm vi nhóm 09, 10, 12. Việc rút gọn phải được chốt trong buổi họp và ghi vào biên bản.

## 5. Quy tắc làm việc

- **Giao tiếp:** dùng nhóm chat chung. Trả lời tin nhắn liên quan đến công việc trong vòng **24 giờ**. Câu hỏi kỹ thuật nên đặt vào issue hoặc PR để có lưu vết.
- **Review PR:** người được giao review hoàn thành trong vòng **48 giờ**. Không tự merge PR của mình.
- **Báo trước khi chậm:** nếu không kịp việc trong tuần, báo cho cả nhóm **trước thứ Tư** để chia lại việc.
- **Vắng họp:** báo trước ít nhất 12 giờ, gửi video demo ngắn (≤ 3 phút) và danh sách việc đã làm cho thư ký trước giờ họp.
- **Dùng AI:** tuân theo mục 13 của proposal và nguyên tắc VII của constitution. Mỗi người phải giải thích được mọi đoạn mã mình nộp.
- **Bảo mật:** không commit `google-services.json`, `local.properties`, khóa API hay keystore.

## 6. Xử lý vi phạm

| Mức | Trường hợp | Cách xử lý |
|---|---|---|
| 1 | Không đạt cam kết tối thiểu ở [mục 3](#3-thống-kê-commit) trong 1 tuần, hoặc vắng họp không báo | Nhắc nhở, ghi vào biên bản họp |
| 2 | Lặp lại mức 1 trong 2 tuần liên tiếp | Họp riêng với thành viên đó; lập kế hoạch bù việc cụ thể cho tuần kế tiếp |
| 3 | Lặp lại 3 tuần, hoặc không hoàn thành nhóm tính năng được giao trước mốc M3 | Chia lại phần việc cho thành viên khác; ghi rõ mức đóng góp thực tế trong báo cáo cuối kỳ và báo giảng viên |

Mức đóng góp cuối kỳ được đánh giá dựa trên số commit và PR, các nhóm tính năng đã hoàn thành, số PR đã review, và mức tham gia họp.

## 7. Sửa đổi thỏa thuận

Thỏa thuận được sửa qua PR chỉ thay đổi file này, cần ít nhất **3/4 thành viên** đồng ý trong buổi họp hoặc approve trên PR. Mỗi lần sửa thì ghi vào bảng dưới đây.

| Ngày | Nội dung thay đổi | Người đề xuất |
|---|---|---|
| 03/10/2026 | Tạo bản đầu tiên | |
| 05/10/2026 | Chỉ dùng Firebase gói Spark; ảnh và logic server chuyển sang Supabase (constitution v1.1.0) | |
| 05/10/2026 | Thêm AI Assistant vào nhóm 02 (spec 022, A); ràng buộc LLM trong constitution v1.2.0 | |

## 8. Xác nhận của thành viên

Bằng việc ký tên dưới đây (hoặc approve PR thêm file này), tôi đồng ý thực hiện các điều khoản trên.

| Mã | Họ tên | Chữ ký / GitHub approve | Ngày |
|---|---|---|---|
| A | | | |
| B | | | |
| C | | | |
| D | | | |
