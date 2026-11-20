# Contract: Đề xuất sân theo đánh giá (US7)

**Trạng thái:** contract chính thức cho Edge Function `ai-search-parse`, khớp code hiện tại (cập nhật 2026-11-14). [edge-function-ai-search-parse.md](edge-function-ai-search-parse.md) chỉ còn là thiết kế ban đầu của nhánh `PARSE`.

**Code:** `supabase/functions/ai-search-parse/` gồm `handler.ts` (định tuyến thao tác, lượt, cache, đo lường), `criteria.ts` (prompt, schema, chuẩn hóa ngày giờ, từ điển sở thích), `evidence.ts` (truy xuất, xếp hạng, phiên bản nguồn, lời giải thích), `llm.ts` (Gemini chính, Groq dự phòng). App gọi qua `CourtAiApi` / `SupabaseCourtAiApi` (`feature/search`).

Các thao tác đi qua `POST /functions/v1/ai-search-parse` với Firebase ID token. Edge Function tự xác thực phiên, tài khoản khóa hoặc đang xóa (`_shared/auth.ts`).

| `action` | Input | Kết quả thành công | LLM | Lượt AI |
|---|---|---|---|---|
| `PARSE` (mặc định) | `query` đã che liên hệ, 1–200 ký tự | `result`, `cached`, `usage`. `result.preference` là mã đặc điểm hoặc `null`. Ngày là `yyyyMMdd`, giờ là `HHmm`. | Có | Có |
| `USAGE` | Không có trường bổ sung | `usage` gồm `used`, `attempts`, `limit`, `lockedUntil`. | Không | Không |
| `RECOMMEND` | `preference`, tối đa 30 `venueIds` từ kết quả 020 | `recommendations`: tối đa ba mục `{venueId, sources, quotes}`. `quotes`: `{reviewId, text, supports}`, `text` là câu khớp trong đánh giá, đã che liên hệ, tối đa 140 ký tự. | Không | Không, trần riêng 20/ngày |
| `SOURCE` | `venueId`, một `source` | `comment`: bình luận gốc đang công khai. | Không | Không |
| `EXPLAIN` | `venueId`, `preference`, một đến ba `sources` | `claims`: tối đa hai mục `{source, excerpt, supports}`. Không có `usage`. | **Không** | Không |

## 1. Sở thích (danh sách đóng)

`GRIP`, `HIGH_CEILING`, `QUIET`, `CLEAN`, `GOOD_LIGHT`, `VENTILATED`, `GOOD_SERVICE`. Mỗi mã có danh sách cụm khớp (`match`) và cụm trái nghĩa (`counter`) đã bỏ dấu trong `criteria.ts`, cùng cặp nhãn hiển thị (thuận/trái) trong `evidence.ts`. Thêm mã mới phải sửa cả hai file, enum `ReviewPreference` và chuỗi `search_rag_*` của app.

## 2. Nguồn và phiên bản

Nguồn `{reviewId, version}`. `version = SHA-256(JSON [venueId, targetType, status, createdAt, comment])`.

- Không băm `updatedAt`: phản hồi của chủ sân (`ownerReply`) và sửa số sao cập nhật `updatedAt` nhưng không đổi nội dung làm bằng chứng, nên nguồn vẫn hợp lệ.
- Sửa bình luận, ẩn (`HIDDEN`), xóa, hoặc đánh giá không thuộc sân đều làm nguồn hết hiệu lực → `404 SOURCE_UNAVAILABLE`. App bỏ đề xuất của sân đó và giữ danh sách chính.

Mọi nguồn thuộc sân `ACTIVE`, có `status == VISIBLE` và `targetType == VENUE`.

## 3. `RECOMMEND`

1. Kiểm tra `preference` và `venueIds` (≤ 30, bỏ trùng).
2. Giữ một lần truy xuất trong `users/{uid}/aiUsage/{yyyyMMdd}.recommendCount` bằng transaction. Đạt 20 → `409 RECOMMEND_LIMIT` (app bỏ qua, chỉ không hiện đề xuất).
3. Đọc `venues/{id}`; chỉ giữ sân `ACTIVE` và bỏ sân có `ratingCount == 0` (không có đánh giá nào).
4. Theo đợt 6 sân: truy vấn `reviews` với `venueId == X`, `targetType == VENUE`, `status == VISIBLE`, `orderBy createdAt desc`, `limit 10`. Cần index `reviews (venueId, targetType, status, createdAt desc)` trong `firestore.indexes.json`.
5. Mỗi đánh giá tách theo câu và "nhưng", khớp cụm thuận/trái (có xét phủ định "không …"). Điểm sân = `2 × số quan sát thuận − số quan sát trái`; sân không có quan sát thuận bị bỏ. Mỗi sân giữ tối đa 2 nguồn thuận và 1 nguồn trái.
6. Trả top 3 theo điểm. Nguồn vừa được đọc trong chính yêu cầu này nên không đọc lại lần hai.
7. Mỗi quan sát được chọn kèm `quote`: mệnh đề gốc đầu tiên khớp (tách theo câu và "nhưng"), che email/số điện thoại bằng `maskQuery`, cắt 140 ký tự. App hiện quote thuận đầu tiên trên thẻ đề xuất. Nội dung đầy đủ vẫn chỉ hiện qua `SOURCE` (có kiểm tra phiên bản); quote có thể cũ nếu bình luận bị sửa sau lần truy xuất, và lần mở nguồn kế tiếp sẽ bỏ đề xuất đó.

Chi phí đọc tối đa mỗi lần: khoảng 30 (`venues`) + 10 × số sân có đánh giá (`reviews`) + 2 (lượt). Trần 20 lần/người/ngày giữ chi phí trong hạn mức Spark.

## 4. `SOURCE`

Đọc lại sân và đánh giá, so `version`. Hợp lệ thì trả `comment` để app hiển thị nguyên văn; không hợp lệ → `404 SOURCE_UNAVAILABLE`.

## 5. `EXPLAIN` (không dùng LLM)

1. Kiểm tra sân `ACTIVE` và đọc lại từng nguồn, so `version`.
2. Tính lại quan sát từ bình luận hiện tại.
3. `buildExplanation`: lấy quan sát thuận đầu tiên và, nếu có, quan sát trái đầu tiên. `excerpt` là nhãn cố định của sở thích (ví dụ "sàn ít trơn hoặc có độ bám tốt"), không phải trích dẫn bình luận.
4. Không có quan sát thuận → `404 SOURCE_UNAVAILABLE`.
5. Ghi `aiMetrics` với `outcome = EXPLAINED` (không có provider).

Vì không gửi nội dung đánh giá tới nhà cung cấp và không có văn bản sinh tự do, thông tin liên hệ và câu ra lệnh trong đánh giá không thể lọt vào lời giải thích. App hiển thị với nhãn `search_rag_summary_label` ("Tổng hợp từ đánh giá"); chạm từng nhận định mở nguồn qua `SOURCE`.

## 6. Lượt và trần

- `PARSE`: trước mỗi lần gọi nhà cung cấp (kể cả dự phòng), transaction giữ một `attempts`. Kết quả hợp lệ tăng `count` (`used`). Cả hai có trần 10/ngày. Lời gọi lỗi hoặc quá giờ vẫn tính vào `attempts` (không hoàn).
- Thứ tự nhà cung cấp: Gemini (`GEMINI_API_KEY`) → Gemini key thứ hai nếu có (`GEMINI_API_KEY_2`) → Groq (`GROQ_API_KEY`); chuyển tiếp khi lỗi 429/5xx, hoặc khi nhà cung cấp trước quá giờ mà còn ít nhất 1,5 giây trong ngân sách 8,5 giây (lần gọi đầu tối đa 6 giây). Mỗi lần thử giữ một `attempts`, nên một câu có thể tốn tối đa 3 lần gọi.
- App hiển thị `remaining = 10 − max(used, attempts)`.
- Hết trần hoặc đang khóa → `409 QUOTA_EXCEEDED` / `409 AI_LOCKED` (chỉ áp dụng cho `PARSE`).
- `RECOMMEND`: trần riêng 20/ngày (`recommendCount`). `SOURCE`, `EXPLAIN`, `USAGE`: không tính lượt.

## 7. Kiểm thử

`supabase/functions/ai-search-parse/handler.test.ts`: test thuần (phân tích, quan sát, phiên bản nguồn, bộ lọc truy vấn, 10 ví dụ có nhãn) và test Emulator (giữ lượt cả dự phòng, tranh chấp lượt cuối, nguồn bị ẩn sau khi chủ sân phản hồi, trần `RECOMMEND`). Chạy:

```bash
JAVA_HOME=<JDK 21+> npx firebase-tools@15 emulators:exec --project demo-smashnow --only auth,firestore \
  "cd supabase/functions && FIREBASE_PROJECT_ID=demo-smashnow deno test -A ai-search-parse/"
```

Xem thêm [quickstart](../quickstart.md).
