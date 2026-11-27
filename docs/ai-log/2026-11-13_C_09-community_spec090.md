# Log AI: triển khai spec 090 "Nhóm/CLB và bảng tin cộng đồng"

> Ghi ở file riêng (theo quy ước tên file của `docs/ai-log/README.md`) thay vì thêm "Mục 39" vào `AI_log_3.md`, vì
> một session khác đang làm spec 081 song song và có thể cũng ghi vào cuối `AI_log_3.md`. Khi gộp nhánh có thể
> chuyển nội dung này vào `AI_log_3.md`.

| Trường | Nội dung |
|---|---|
| Ngày | 2026-11-13 |
| Người thực hiện | Nguyễn Đức Duy |
| Nhóm tính năng liên quan | 09 – Cộng đồng và chat (spec 090) |
| Công cụ AI | Claude Code (Claude Opus 5.5 điều phối và review; subagent Claude Sonnet cài đặt và viết test) |
| Mục đích | Cài đặt spec 090 theo `tasks.md`: Setup, Foundational, rồi US1 (tạo nhóm) làm MVP, tiếp theo các story còn lại |
| Nhánh Git | `feature/090-community-group` |

## Prompt đã dùng

**Prompt 1: Bắt đầu triển khai spec 090**

```
Bối cảnh: spec 090 (Nhóm/CLB và bảng tin cộng đồng) đã có đủ spec.md, plan.md, research.md, data-model.md,
contracts/, ui/, quickstart.md và tasks.md (75 task, chưa task nào xong). Repo đang ở main đã cập nhật. Một
session khác đang làm spec 081 ở worktree riêng và đang dùng bộ Firebase Emulator duy nhất của máy (cổng 8080,
9099, 4000) cùng máy ảo Android.

Mục tiêu: lần lượt hoàn thành các phase trong tasks.md, ưu tiên Setup, Foundational, rồi US1 (tạo nhóm) làm MVP.

Yêu cầu:
- Tạo nhánh feature/090-community-group từ main; đọc memory bàn giao của 081 và đặc tả CSDL mục 4.10, 4.12, 8.
- AI chính đóng vai quản lý và review, chịu trách nhiệm cuối; giao cài đặt và chạy test cho subagent dùng model
  rẻ hơn (Sonnet), mỗi subagent một nhóm task rõ ràng (ID task, file, tiêu chí xong, lệnh test). Chỉ chạy song
  song khi không sửa chung file và không cùng cần emulator.
- Subagent không commit, không tick tasks.md, không push/deploy. AI chính đọc diff, đối chiếu spec/contract, tự
  chạy lại test rồi mới commit (message tiếng Việt kiểu feat(community): ... (T0xx–T0yy)) và tick task.
- Trước khi bật emulator hoặc chạy test cần emulator phải kiểm tra cổng; nếu đang bận thì làm việc khác, không
  kill process của session kia, không đụng worktree và nhánh của 081.
- Spec mâu thuẫn, thiếu, hoặc cần đổi đặc tả CSDL của người khác thì dừng lại hỏi.

Ràng buộc: hỏi trước khi push, mở PR, deploy (Rules, index, Edge Function, bucket). Cuối phiên cập nhật memory
bàn giao handoff-090.

Kết quả mong muốn: mỗi phase có commit riêng, test xanh, báo cáo ngắn sau mỗi phase.
```

**Prompt 2: Làm tiếp spec 090 và chuyển sang spec 091**

```
Bối cảnh: spec 090 đang làm dở (đã xong phần nền app và bucket Storage); session 081 vẫn chạy song song.
Yêu cầu: tiếp tục các phase còn lại của spec 090 theo đúng cách làm ở prompt 1, tránh xung đột với nhánh 081
(file dùng chung như _shared/firestore.ts, AI log). Xong spec 090 thì làm tiếp spec 091 (chat) theo cùng quy trình.
Ràng buộc: không push, không mở PR, không deploy khi chưa hỏi.
Kết quả mong muốn: commit theo phase, tasks.md được tick, test xanh.
```

## Tóm tắt phản hồi của AI

_(cập nhật dần trong phiên)_

- **Chuẩn bị:** checklist `requirements.md` 16/16 đạt. Phần nền (T001) đã có đủ; tab "Cộng đồng" đã có trong bottom
  nav người chơi nên không cần thống nhất lại vị trí với A.
- **T004:** `_shared/firestore.ts` đã có truy vấn trong transaction (`tx.query`). AI chép đúng y hệt phần mở rộng
  (`QueryFilter` có op so sánh, `orderBy`) mà nhánh 081 đã viết, để khi gộp hai nhánh git không báo xung đột.
  Backoff 8 lần dùng lại `runWithBackoff` của spec 080 thay vì viết bản thứ hai.
- **Lượt 1 (4 subagent song song, mỗi agent một vùng file):** server dùng chung (`_shared/groups.ts`, `keywords.ts`,
  fixtures, khung 3 function), Rules + index, app (model, quyền, DTO, lỗi, interface, `ImageCompressor`), seed +
  bucket Storage.
- **Lượt 2:** server `group-manage` (create, update kèm tự duyệt khi đổi sang công khai, join, cancelRequest,
  approve, reject, leave) và app US1 (form tạo/sửa nhóm, "Nhóm của tôi", trang nhóm, `nav_community`, nối tab
  "Cộng đồng" trong `MainActivity`).
- **Review của AI chính:** chạy lại test trên Emulator thì ca G3 (giới hạn 5 nhóm) đỏ. AI đọc lại và thấy lỗi nằm ở
  test: chỉ seed 3 nhóm được tính nên lần tạo "thứ 6" thực ra là nhóm thứ 5. Sửa test (thêm 1 nhóm ACTIVE), code
  giữ nguyên. Agent server cũng phát hiện `runWithBackoff` chỉ commit mảng `writes` trả về, nên hook
  `onGroupMembershipChanged` (spec 091) được truyền một transaction bọc để mọi `tx.write` của hook vào cùng commit.
- **Lượt 3:** app US2 (tìm nhóm, nút tham gia, duyệt yêu cầu) và server US3 (`group-media-url`, bài đăng).

## Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| Viết `nameKeywords` mới (tiền tố 1–20 ký tự) thay vì dùng `generateKeywords` của A (2–15 ký tự) | Dùng, báo A | Spec 090 cần tiền tố 1 ký tự; đổi hàm của A sẽ đổi dữ liệu `venues` của spec 020 |
| Seed chạy bằng `deno run -A` thay cho `npx tsx` ghi trong quickstart | Sửa | Seed import module Deno của `_shared/` |
| Hàm trả `Result<T>` (giống feature/dropin) thay vì ném exception (giống feature/review) | Dùng | Gần với cách 090 cần xử lý lỗi theo mã |

## Cách kiểm chứng

_(cập nhật dần trong phiên)_

- `./gradlew testDebugUnitTest` (community, core.image, review): 117 test, 0 lỗi; `assembleDebug lintDebug` xanh.
- `npx supabase test db`: PASS 23/23 (gồm S1–S5 của bucket nhóm).
- `deno test _shared/keywords.test.ts`: 5/5.
- Firebase Emulator (`emulators:exec`, tự tắt sau khi chạy): Rules `groups.test.ts` GR1–GR12 24/24; cả bộ
  `firestore-tests` 244/248, 4 ca đỏ là `court-detail.test.ts` của spec 030 (nhãn `[BLOCKED: D]`, có từ trước).
- Deno `group-manage/`, `_shared/groups.test.ts`, `keywords.test.ts`: 28/28; G5 (20 người tham gia cùng lúc, lặp
  10 lần) in `runs=10 expected=20 actual=20 duplicates=0`.
- Seed `090-community.ts` chạy 2 lần liên tiếp trên Emulator: 26 tài khoản, 8 nhóm, 11 thành viên, 2 yêu cầu, 25 bài.
- App US1: 53 unit test community xanh, `assembleDebug` xanh.
