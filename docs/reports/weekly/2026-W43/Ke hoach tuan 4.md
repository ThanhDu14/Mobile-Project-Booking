# Kế hoạch triển khai tuần 4 (26/10 – 01/11/2026) – Nhóm 6 SmashNow

Lập từ [team contract](../../../team-contract.md#4-kế-hoạch-10-tuần) (mục 4), mục 4 của Báo cáo tuần 3 và `tasks.md` của từng spec. Hạn push code và mở PR: **18:00 thứ Sáu 30/10**. Họp và demo: 20:00 thứ Sáu 30/10.

Mã task (T…) là mã trong `specs/<spec>/tasks.md` tương ứng.

## 1. Đầu tuần (thứ 2–3): việc phải xong trước

Những việc này đang chặn việc của người khác, nên làm xong đầu tuần.

| Người | Việc | Ai đang chờ |
|---|---|---|
| C – Duy | Script dữ liệu mẫu (sân, `courts`, `priceRules`) và nhập lên Firebase | B cần sân để vẽ lưới slot; C cần sân để yêu thích |
| B – Hưng | `_shared/firestore.ts` và thử nghiệm transaction giữ slot | Phase 3 của spec 040 |
| D – Nhựt | Trả lời các câu hỏi chặn ở spec 111: T003 (lấy `ownerStatus` đã duyệt từ đâu), T007 (khóa/mở giờ thế nào, chốt cùng B), T008 (ai cập nhật `minPricePerHour`) | Toàn bộ việc ghi dữ liệu của spec 111 |
| Việc chung (tích hợp tuần 4: A) | CI GitHub Actions `lint testDebugUnitTest assembleDebug` trên `main` | Mọi PR từ tuần này |

## 2. Việc chính của từng người

| Người | Spec | Task | Kết quả để demo 30/10 |
|---|---|---|---|
| A – Dự | 010 | Merge PR #39 (cần người review); chạy kịch bản thủ công M7, M9, M10; kiểm tra tay T077 (chế độ tối, TalkBack, `w600dp`, mất mạng); sửa layout dòng điều khoản ở `fragment_auth_register.xml` | Xong spec 010 |
| | 011 | Phase 1 (T001–T005); Phase 2A, mở ngay PR `chore(core)`; sau đó US1 sửa hồ sơ (T031–T051) và US2 đăng xuất (T052–T056) | Trang Tài khoản: sửa hồ sơ, đăng xuất |
| | 021, 022, 030 | `/speckit-plan` và `/speckit-tasks`; wireframe spec 022 | Đủ 20/20 `tasks.md` |
| B – Hưng | 040 | Phase 1–3 (T001–T022); Phase 4A–4D (T023–T034) nếu kịp | Chọn ngày và thấy lưới khung giờ từ dữ liệu mẫu |
| C – Duy | 070 | Phase 1 (T001–T009), Phase 2 (T010–T025), US3 yêu thích (T047–T054) | Bấm tim một sân và xem danh sách sân yêu thích |
| | — | Component dùng chung của design system (việc tuần 3 còn dở) | |
| D – Nhựt | 111 | T001–T008. Làm phần không bị chặn trước: kiểm tra quy tắc giá T021, kiểm tra khoảng giờ và khung màn hình US4. T022/T023 chờ T008; T028–T030 và T032 chờ T003/T007 | Màn bảng giá và khóa giờ, có validate |
| | — | Bucket ảnh và `sign-upload` (việc tuần 3 còn dở) | |

## 3. Review chéo

Mỗi người review ít nhất 1 PR của người khác, trong vòng 48 giờ kể từ khi PR mở.

| Người review | PR cần review |
|---|---|
| A – Dự | B (spec 040) |
| B – Hưng | C (spec 070) |
| C – Duy | D (spec 111) |
| D – Nhựt | A (PR #39 và `chore(core)`) |

## 4. Rủi ro

1. **Phần của D dễ không xong trong tuần.** Bảng giá và khóa giờ nằm ở US3, US4 của spec 111, nhưng phải có venue và court trước (US1, US2, việc tuần 3 còn dở) và còn vướng T003/T007/T008. Nếu đến thứ Tư 28/10 chưa chốt được T007 thì rút mục tiêu demo xuống "bảng giá, chưa có khóa giờ" và ghi vào biên bản.
2. **Dữ liệu mẫu là việc gấp nhất.** Nếu tới thứ Ba 27/10 C chưa xong, B và C tạm dùng dữ liệu nạp vào Firestore Emulator để không phải đứng chờ.
3. **Cần chốt cách xử lý M20 ngay buổi họp 23/10**, vì A cần quyết định đó để đóng spec 010 (chi tiết ở `specs/010-account-auth/research.md`, R9).

Theo team contract, ai thấy không kịp việc thì báo cả nhóm **trước thứ Tư 28/10** để chia lại.
