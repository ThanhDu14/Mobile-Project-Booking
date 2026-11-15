# Log AI: Sửa lỗi RAG (US7 của 022) và đồng bộ tài liệu 020/021/022 với code

| Trường | Nội dung |
|---|---|
| Ngày | 2026-11-14 |
| Người thực hiện | Lê Quốc Hưng (thành viên 2) |
| Nhóm tính năng liên quan | 02 – Tìm kiếm sân (spec 020, 021, 022) |
| Công cụ AI | Claude Code (Claude Opus 5.5) |
| Mục đích | Review code RAG so với spec, sửa lỗi, cập nhật tài liệu theo các quyết định đã chốt |

## Prompt đã dùng

```text
Tôi muốn build tính năng RAG của feature AI liên quan đến search và hoạt động được, hãy làm rõ những điểm mâu thuẫn và cần thống nhất giúp tôi.

Hãy giải quyết A, B.1 (1), B.2 theo đề xuất, B.3 theo đề xuất, B.4 theo đề xuất, B.5 theo đề xuất theo những gì bạn cho là phù hợp, C. Hãy cập nhật theo đề xuất.
```

## Tóm tắt phản hồi của AI

AI đối chiếu code `supabase/functions/ai-search-parse/` và `feature/search/` với spec 020/022 và tìm ra:

- **Lỗi:** (A1) phiên bản nguồn băm `updatedAt` nên chủ sân phản hồi đánh giá làm nguồn hết hiệu lực; (A2) truy vấn đánh giá không lọc `targetType`, đánh giá vãng lai có thể chiếm hết 10 chỗ; (A3) `RECOMMEND` đọc tới khoảng 340 document mỗi lần mà không có trần; (A4) bộ đếm "Còn X/10" lệch với mô tả hoàn lượt cũ.
- **Điểm cần chốt:** B1 bước giải thích có dùng LLM không; B2 phạm vi sở thích; B3 hai trần lượt; B4 cách chọn 30 sân ứng viên; B5 code hay plan là chuẩn.
- **Tài liệu lỗi thời:** contract Edge Function cũ, tên lớp trong plan, ghi chú tasks.

Quyết định đã chốt: B1 bỏ LLM ở `EXPLAIN`; B2 danh sách đóng 7 mã; B3 giữ hai trần, hiển thị theo trần chặt hơn; B4 ghi đúng hành vi code; B5 code là chuẩn.

## Phần đã sử dụng / chỉnh sửa / bỏ

| Phần | Quyết định | Lý do |
|---|---|---|
| `evidence.ts` `reviewVersion` bỏ `updatedAt`, thêm `targetType` | Dùng | Sửa A1 |
| `evidence.ts` `rankReviews` thêm điều kiện `targetType == VENUE`; `firestore.indexes.json` thêm index `reviews (venueId, targetType, status, createdAt desc)` | Dùng | Sửa A2 |
| `handler.ts` `RECOMMEND`: trần `recommendCount` 20/ngày, bỏ sân `ratingCount == 0`, bỏ bước đọc lại nguồn thừa | Dùng | Sửa A3 |
| `evidence.ts` `buildExplanation` thay `explanationSchema`/`validateExplanation`; `handler.ts` `EXPLAIN` không gọi LLM, không tính lượt | Dùng | Quyết định B1 (1) |
| App: `AiExplanationResponse.usage` tùy chọn, nút "Vì sao phù hợp?" không phụ thuộc lượt/khóa, nhãn `search_rag_summary_label` | Dùng | Theo B1 |
| `handler.test.ts`: test phiên bản nguồn, bộ lọc truy vấn, lời giải thích tất định; test Emulator viết lại cho nguồn bị ẩn sau khi chủ sân phản hồi, thêm test trần `RECOMMEND` | Dùng | Kiểm chứng A1–A3, B1 |
| Spec 022 (Clarifications 2026-11-14, FR liên quan, US5-2, US7), `review-rag.md`, banner thay thế cho `edge-function-ai-search-parse.md`, plan/tasks/quickstart 022 | Dùng | B1–B3, C |
| Spec 020 FR-034, SC-009, `search-criteria.md` §3.1, plan 020 bảng "Đồng bộ với code"; plan 021 ghi chú chưa hiện thực | Dùng | B4, B5 |
| Tick hàng loạt T001–T116 của 022 | Bỏ | Không kiểm chứng từng task; thay bằng ghi chú và đề nghị chạy `/speckit-converge` |
| Deploy index và Edge Function | Chưa làm | Thao tác lên project thật, để người phụ trách chạy (task T126) |

## Cách kiểm chứng

- `deno test -A ai-search-parse/` dưới `firebase emulators:exec --only auth,firestore` (JDK 21+): 10/10 test qua, gồm 4 test Emulator trước đây bị bỏ qua.
- `./gradlew testDebugUnitTest --tests "com.example.booking_app.feature.search.*"`: 17/17 test qua.
- `deno check` cho `handler.ts` và `handler.test.ts`.
- Chưa kiểm thử trên thiết bị và project thật (task T126, T127).

## Bổ sung 2026-11-15: kiểm thử trên máy thật và sửa sau kiểm thử

| Phần | Quyết định | Lý do |
|---|---|---|
| Nút xóa ô AI và lịch sử câu hỏi AI trên máy (FR-031, T128) | Dùng | Người dùng đề xuất khi test |
| `SearchNotice.INVALID_DATE` và `pastDayInQuery` ("hôm qua" luôn bỏ ngày), `version` `court-search-rag-3` (T130) | Dùng | Model không nhận diện ổn định ngày đã qua |
| `AI_UNLIMITED_UIDS` (bỏ trần lượt cho tài khoản test) | Code có, secret chưa đặt | Tài khoản không có quyền ghi secret Supabase |
| Kiểm thử bằng adb trên máy thật | Dùng | Ghi kết quả vào T127, T129 trong `tasks.md` |

Kiểm chứng: `deno test -A ai-search-parse/` 8 passed (4 test Emulator bỏ qua khi không chạy Emulator); Android search unit test pass; deploy `ai-search-parse` 2026-11-15.
