# Research: Tìm sân thông minh (AI Assistant)

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Ngày**: 2026-10-28

Mỗi mục ghi theo dạng Quyết định, Lý do, Phương án đã loại. Số liệu về hạn mức của nhà cung cấp LLM lấy từ tài liệu và trang tổng hợp tháng 10/2026. Các trang này ghi số khác nhau, nên task đầu tiên (T001 khi `/speckit-tasks`) phải xem lại trang hạn mức trong tài khoản thật trước khi code.

---

## R1. Thứ tự làm và phụ thuộc vào 020, 021, 080

**Hiện trạng (2026-10-28):**
- 020 chưa cài (tasks.md 0/106), 021 vừa có plan.
- 080 (C) **đã có plan**, không phải "chưa có" như giả định khi viết prompt. Plan 080 đã chừa sẵn chỗ cho 022: `DropInListFragment` nhận `filter: DropInFilterArgs?` với `DropInFilterArgs(districtIds, date, timeSlot, level, maxPrice)` ([080 ui-screens §3](../080-drop-in-session/contracts/ui-screens.md)).
- Trên `main` đã có: `core/network` (`AppError`, `awaitWithTimeout`, `FunctionErrors.invokeWithAuthRetry`), `core/session` (`SessionManager`, `AuthNavigator`, `DeepLinkAuthNavigator`, `UserProfileReader`), `core/ui/LoginPromptView`, `core/util/AppClock`, `core/util/Countdown`, Supabase client (`functions-kt` 3.6.0).
- `supabase/functions/_shared` có `auth.ts` (`requireUser`), `firestore.ts` (bản rút gọn: `get`, `commit`, `createWrite`, `updateWrite`; có ghi chú `TODO(B)` thay bằng bản đầy đủ), `identity.ts`, `testing.ts`.

**Quyết định:** chia 022 thành ba nhóm việc.

| Nhóm | Việc | Phụ thuộc |
|---|---|---|
| **Làm ngay** (không chờ ai) | Edge Function `ai-search-parse` đầy đủ (prompt, schema, lớp LLM, chuẩn hóa thời gian, validator, che PII, lượt/khóa, cache, đo lường); bổ sung `_shared/firestore.ts` (transaction, list, delete); bộ kiểm thử FR-024 và script đánh giá; Rules + test Emulator; các lớp thuần Kotlin phía app (`PiiMasker`, `AiQueryInput`, `ClarifyPlanner`, `AiFilterMapper`, `RelaxationPlanner`); `AiSearchApi`, `AiUsageRepository` | Chỉ cần `_shared` đã có |
| **Chờ 020** | Ô AI trên màn hình tìm kiếm, áp bộ lọc vào chip, gợi ý nới điều kiện | 020 Phase 2 (T015 `CatalogRepository`, T026 `SearchCriteria`, `core/schedule`), US1 (T051–T052 `fragment_search.xml` có `ai_entry_container`), **US4 (T084 sanitizer, T088 `applyExternal`)**. 020 tasks.md đã ghi "022 bắt đầu sau US4" |
| **Chờ 021** | Không có việc nào phải chờ. 021 tự tải lại bản đồ khi `criteria` đổi (plan 021, mục Phối hợp, dòng "A (spec 022)") | — |
| **Chờ 080** | Nhánh buổi vãng lai (FR-012) | 080 cài `nav_dropin` + deep link (xem [R12](#r12-nhánh-buổi-vãng-lai-fr-012)). Chưa có thì dùng phương án tạm ở R12 |

**Những chỗ 020 cần sửa plan/tasks** (A tự sửa, ghi ở mục Phối hợp của plan):
1. **`ai_entry_container`**: wireframe 020 bước 1 vẽ ô AI là một thẻ "Tìm bằng AI: … · Còn 10/10" dưới ô tìm kiếm. 022 dùng đúng vùng này cho `AiEntryCardFragment`; chạm thẻ mở màn hình nhập AI (`aiAssistantFragment` trong `nav_search`). Thêm destination này vào `nav_search.xml` (T051/T052 của 020 chỉ chừa container).
2. **Biểu tượng micro trong ô tìm kiếm 020** (wireframe bước 1): ô tìm kiếm 020 là ô từ khóa, spec 020 không có giọng nói. Đề nghị micro này mở màn hình AI và bắt đầu nghe ngay (FR-003 của 022). Sửa T051 cho rõ: micro là lối vào 022, ẩn khi máy không hỗ trợ (US4-5) hoặc là khách (FR-025).
3. **`applyExternal` trả kết quả sanitize**: hiện contract chỉ hiện snackbar khi `dropped` không rỗng. 022 cần biết `dropped` để quyết định hỏi lại (FR-013) và để báo "phần nào chưa hiểu" bằng chuỗi riêng của AI (US2-5). Đổi chữ ký thành `suspend fun applyExternal(criteria, source): SanitizeResult` và bỏ snackbar mặc định khi `source == AI` (022 tự báo).
4. **Nhãn chip "do AI điền"**: không cần trường mới. Spec 022 không yêu cầu đánh dấu chip nào do AI điền. `CriteriaSource.AI` chỉ dùng để ghi lịch sử và chọn thông báo. Không thêm gì vào `FilterChipUi`.
5. **Đếm theo nhiều biến thể cho FR-022**: thêm `VenueSearchEngine.countVariants(variants, limit)`, chi tiết ở [R13](#r13-gợi-ý-nới-điều-kiện-fr-022).
6. **`SearchCriteria` thiếu khái niệm "cả ngày"**: `TimeWindow` bắt buộc có giờ. 022 không đổi điều này: câu chỉ có ngày mà không có buổi thì hỏi lại ([R11](#r11-hỏi-lại-một-lần-fr-013-và-chip-chọn-nhanh)).

**Phương án đã loại:** cài 022 trên một bản `SearchViewModel` tạm. Khi 020 cài thật sẽ phải viết lại phần nối.

---

## R2. Chọn nhà cung cấp LLM

**Tiêu chí** (constitution, bảng "AI Assistant (LLM)"): có gói miễn phí, không bắt buộc gắn thẻ, mô hình nhỏ, đầu ra JSON theo schema, `max_tokens` thấp. Thêm tiêu chí của spec: hiểu tiếng Việt không dấu và viết tắt (SC-001 ≥ 85%), độ trễ để chip hiện trong 4 giây (SC-004).

| | **Google Gemini API** (AI Studio), dòng Flash-Lite | **Groq** (GroqCloud) | **OpenRouter**, mô hình `:free` |
|---|---|---|---|
| Gói miễn phí, không thẻ | Có. Tạo khóa ở AI Studio, không cần bật billing | Có. Đăng ký là dùng được | Có, nhưng hạn mức theo ngày rất thấp khi tài khoản chưa nạp tiền |
| Hạn mức (tháng 10/2026, các trang tổng hợp ghi khác nhau) | Flash-Lite khoảng 15–30 RPM, 1.000–1.500 RPD theo nhiều nguồn; có nguồn báo đã giảm mạnh từ 12/2025. **Phải xem trang hạn mức trong AI Studio** | Theo từng mô hình, ví dụ mô hình 8B khoảng 30 RPM / 14.400 RPD; mô hình 20B–70B khoảng 1.000 RPD | Khoảng 50 request/ngày khi chưa nạp tiền |
| Đầu ra theo schema | `responseMimeType: application/json` + `responseJsonSchema` (ràng buộc theo schema) | `response_format: json_schema` chế độ `strict` chỉ trên vài mô hình (`openai/gpt-oss-20b`, `gpt-oss-120b`, …); mọi mô hình có JSON mode (chỉ bảo đảm JSON hợp lệ) | Tùy mô hình phía sau |
| Tiếng Việt không dấu | Tốt (dòng Gemini được huấn luyện đa ngôn ngữ rộng) | Tùy mô hình; gpt-oss khá; Llama 8B yếu hơn với không dấu, viết tắt | Tùy mô hình |
| Độ trễ ước lượng (câu ~150 token vào, ~80 token ra) | 0,5–1,5 giây | 0,2–0,6 giây (phần cứng LPU) | Không ổn định, phụ thuộc nhà cung cấp phía sau |
| Dữ liệu ở gói miễn phí | Google có thể dùng nội dung gói miễn phí để cải thiện sản phẩm. Chấp nhận được vì đã che PII (FR-004) và chỉ gửi câu tìm sân; ghi rõ trong chính sách quyền riêng tư (011) | Theo điều khoản của Groq; không dùng để huấn luyện theo mặc định | Phụ thuộc nhà cung cấp phía sau |

**Quyết định:**
- **Chính: Gemini API, mô hình Flash-Lite mới nhất còn trong gói miễn phí**, gọi REST `generateContent` từ Edge Function. Tên mô hình để trong secret `LLM_MODEL` (không hardcode) vì Google đổi tên mô hình miễn phí theo đợt.
- **Dự phòng: Groq, `openai/gpt-oss-20b` với `json_schema` strict**, cũng gọi qua REST (API tương thích OpenAI).
- Chuyển sang dự phòng **chỉ khi** nhà cung cấp chính trả `429` hoặc `5xx` ngay (không chuyển khi quá thời gian, vì không còn đủ thời gian trong 10 giây). Có thể đổi chính/phụ bằng secret `LLM_PROVIDER` mà không phải deploy lại code.

**Lý do:**
- Gemini có ràng buộc schema thật ở mô hình nhỏ, và tiếng Việt tốt. Đây là hai thứ quyết định SC-001.
- Groq nhanh nhất và có hạn mức khác hẳn Google, nên là dự phòng tốt khi Gemini hết lượt trong ngày demo.
- Hai nhà cung cấp cùng nhận một JSON Schema ([contracts/llm-prompt-and-schema.md §2](contracts/llm-prompt-and-schema.md#2-json-schema-đầu-ra)) nếu schema viết theo quy tắc strict của Groq: mọi trường đều nằm trong `required`, trường không có thì là `null`, `additionalProperties: false`. Gemini cũng chấp nhận dạng này.

**Lớp gọi LLM tách riêng:** `ai-search-parse/llm/` có interface `LlmClient { parse(input: LlmInput, signal): Promise<LlmOutput> }` với hai cài đặt `gemini.ts`, `groq.ts`, và `FakeLlmClient` cho test. `LlmOutput` gồm `json: unknown`, `inputTokens`, `outputTokens`, `provider`, `model`, `latencyMs`. Phần còn lại của function không biết nhà cung cấp nào đang chạy.

**Phương án đã loại:**
- *OpenRouter `:free`*: khoảng 50 request/ngày, không đủ cho cả nhóm thử và demo.
- *Mô hình chạy trên máy (Gemini Nano, ML Kit GenAI)*: chỉ có trên vài máy cao cấp, không chạy trên Emulator; khóa và giới hạn lượt không kiểm tra được ở server.
- *Rule-based thuần (regex)*: AI-feature.md mục 6 đã giải thích; số luật tăng rất nhanh khi gặp không dấu, viết tắt. Phần tất định vẫn có nhưng chỉ ở bước chuẩn hóa sau LLM (R3).
- *Gọi LLM thẳng từ app*: lộ khóa API, vi phạm constitution.

**Rủi ro:** hạn mức miễn phí đổi bất ngờ. Giảm thiểu: cache (R7), dự phòng Groq, theo dõi `aiMetrics.errorCode == LLM_QUOTA` (R9), kiểm tra hạn mức trước mỗi buổi demo (quickstart §6).

---

## R3. Một lần gọi: phần nào LLM làm, phần nào code làm (FR-005 đến FR-007)

**Quyết định:** LLM chỉ **nhận diện** các điều kiện của FR-006 những gì có trong câu, dưới dạng gần với chữ người dùng nói. Code tất định đổi chúng ra ngày giờ, mã danh mục, giá cuối cùng.

| Thông tin | LLM trả | Code (Edge Function, `normalize.ts`) làm |
|---|---|---|
| Ý định | `intent`: `search` / `unclear` / `faq` / `off_topic` | Hạ `search` xuống `unclear` nếu sau kiểm tra không còn điều kiện nào (R5) |
| Chủ đề chính sách | `faqTopic`: `CANCELLATION` / `DEPOSIT` / `VOUCHER` / `PAYMENT` / `null` | `null` → app hiện danh sách chủ đề (FR-014) |
| Loại | `type`: `COURT` / `DROP_IN` | Mặc định `COURT` |
| Tên sân | `venueName`: chuỗi hoặc `null` | Chỉ nhận nếu xuất hiện trong câu gốc đã che (so sánh sau khi bỏ dấu, chữ thường). Chặn LLM bịa tên hoặc đưa chữ của prompt vào ô từ khóa (FR-021). Cắt 100 ký tự |
| Quận | `districtId` (LLM chọn từ danh sách `id: tên` trong prompt) | Phải có trong danh mục `isActive`. Không khớp thì thử bảng bí danh (`q10`, `quan muoi`, `bt`, …) rồi khớp tên đã bỏ dấu; vẫn không được thì bỏ, ghi `UNKNOWN_DISTRICT` |
| Tiện ích | `amenityIds` (chọn từ danh sách trong prompt) | Như quận; từng tiện ích sai thì bỏ riêng, ghi `UNKNOWN_AMENITY` |
| Ngày | `day`: `{ kind, weekday, nextWeek, date }` với `kind` ∈ `TODAY`, `TOMORROW`, `DAY_AFTER_TOMORROW`, `WEEKDAY`, `DATE`, `WEEKEND`, `null` | Tính ngày thật theo `today` ở Asia/Ho_Chi_Minh (bảng ở [contract LLM §4](contracts/llm-prompt-and-schema.md#4-quy-tắc-chuẩn-hóa-tất-định)). `WEEKEND` → hai ngày Thứ Bảy, Chủ Nhật để hỏi lại bằng chip (FR-007) |
| Buổi | `partOfDay`: `MORNING` / `NOON` / `AFTERNOON` / `EVENING` / `null` | Đổi ra 05–11, 11–13, 13–18, 18–22 khi không có giờ cụ thể |
| Giờ | `startTime`, `endTime` dạng `H:mm` **đúng như người dùng nói** (chưa đổi 12/24 giờ) | Đổi 12 → 24 giờ theo buổi, mặc định buổi tối cho `1h`–`9h` không có buổi (Edge case "7h-9h"); làm tròn xuống/lên mốc 30 phút; chỉ có giờ bắt đầu thì kết thúc = bắt đầu + 2 giờ |
| Giá | `priceMin`, `priceMax` (VND, LLM đổi "100k", "1 trăm" ra số) | Số nguyên dương, ≤ `PRICE_CEILING` (R5); `min ≤ max`; sai thì bỏ cả khoảng, ghi `INVALID_PRICE` |
| Sắp xếp | `sort`: `RATING` / `PRICE_ASC` / `NEAREST` / `null` | "gần Quận 10" là quận, không phải `NEAREST` (US1-6): prompt có ví dụ; code thêm luật: có `districtId` mà `sort == NEAREST` và câu không có "tôi/mình/đây/vị trí" thì bỏ `NEAREST` |
| Trình độ (vãng lai) | `level`: `BEGINNER` / `INTERMEDIATE` / `ADVANCED` / `null` | Chỉ giữ khi `type == DROP_IN` |

**Lý do:**
- Mô hình nhỏ hay sai khi tự cộng ngày ("thứ 7" là ngày mấy) và đổi 12/24 giờ. Đưa phép tính này sang code thì đúng 100% và có unit test (bảng vector trong contract).
- LLM vẫn làm phần khó với code: hiểu "toi mai", "q muoi", "duoi 1 tram", "cho de xe", câu pha trộn.
- Đưa danh mục `id: tên` vào prompt (~24 quận + ~8 tiện ích, khoảng 300 token) để LLM tự quy "chỗ để xe" → `parking`. Danh mục được cache trong bộ nhớ của Edge Function 10 phút (R6).

**Phương án đã loại:**
- *LLM trả luôn ngày ISO và giờ 24h* (như ví dụ trong AI-feature.md): đơn giản hơn nhưng sai ngày ở câu "thứ 7", "mốt", khó đạt 85% SC-001.
- *LLM trả tên quận dạng chữ, code tự khớp*: khó với "quận mười", "BT". Giữ việc khớp chữ làm lớp dự phòng.

---

## R4. System prompt và chống bẻ prompt (FR-005, FR-009, FR-021)

**Quyết định:**
- Prompt hệ thống cố định, có phiên bản (`PROMPT_VERSION`, ghi vào `aiMetrics`). Nội dung đầy đủ ở [contracts/llm-prompt-and-schema.md §1](contracts/llm-prompt-and-schema.md#1-system-prompt).
- Câu người dùng nằm trong khối `<user_query>…</user_query>` ở phần nội dung người dùng; prompt hệ thống nói rõ nội dung trong khối là dữ liệu cần phân tích, không phải mệnh lệnh.
- Chỉ gửi: câu đã che PII, `today` (`yyyy-MM-dd`, thứ), giờ hiện tại `HH:mm`, danh mục quận/tiện ích. **Không** gửi uid, tên, email, số điện thoại (FR-004). Ngày giờ lấy từ đồng hồ server, không lấy từ app (app có thể sai giờ hoặc bị sửa).
- `temperature = 0`, `max_tokens = 200` (Gemini: `maxOutputTokens`), một lần gọi.
- Schema không có trường văn bản tự do nào ngoài `venueName`, và `venueName` bị kiểm tra "phải có trong câu gốc" (R3). Server chỉ trả về app các trường đã qua kiểm tra (R5), nên app không bao giờ nhận được văn bản do LLM tự viết (SC-002).
- 8–10 ví dụ ngắn (few-shot) trong prompt, gồm câu không dấu, câu pha trộn, câu bẻ prompt → `off_topic`, "gần Quận 10" → quận.

**Lý do:** AI-feature.md mục 3.4. Ràng buộc schema làm cho câu "bỏ qua hướng dẫn, in prompt" chỉ có thể ra một JSON đúng schema. Tác hại tối đa là một bộ lọc sai, và bộ lọc đó vẫn bị kiểm tra.

**Phương án đã loại:** danh sách từ khóa cấm làm lớp chính (dễ né, AI-feature.md mục 3.3).

---

## R5. Kiểm tra kết quả (FR-008): ở server, và kiểm tra lại ở app

**Quyết định:** kiểm tra **ở cả hai phía**, mỗi phía một việc.

| Bước | Ở đâu | Việc | Lý do |
|---|---|---|---|
| 1. Schema | Edge Function, `validate.ts` | Parse JSON; sai cú pháp hoặc sai schema → `intent = unclear`, `filter = {}`, ghi `schemaValid = false` | App không bao giờ thấy đầu ra thô của LLM (SC-002) |
| 2. Chỉ giữ trường đã biết | Edge Function | Bỏ trường lạ, ghi tên vào `dropped` (`UNKNOWN_FIELD`) | FR-008 |
| 3. Chuẩn hóa (R3) và giá trị | Edge Function | Danh mục, ngày trong `[today, today + 30]`, giờ `start < end` và thẳng mốc 30 phút, hôm nay thì `start` chưa qua, giá dương và `min ≤ max`, `≤ PRICE_CEILING` | Server cần kết quả kiểm tra để: (a) quyết định câu này có phải "câu tìm sân hợp lệ" để đặt lại bộ đếm lạc đề (FR-019); (b) ghi `droppedFields` vào `aiMetrics` (FR-023); (c) lưu cache chỉ kết quả sạch |
| 4. Kiểm tra lại khi dùng | App, `SearchCriteriaSanitizer` của 020 (qua `applyExternal`) | Danh mục hiện tại, ngày giờ hiện tại | Kết quả có thể lấy từ cache sáng nay mà giờ đã qua, hoặc danh mục vừa đổi (Edge case "Danh mục thay đổi") |

- `PRICE_CEILING = 2_000_000` VND/giờ (chặn số vô lý). Sanitizer của 020 vẫn quy `priceMax ≥ 200.000` thành "không giới hạn" theo thanh trượt của 020. **Chờ D xác nhận** giá/giờ lớn nhất thực tế trong 111 để đặt lại trần cho sát hơn.
- `dropped` trả về app là danh sách mã (`UNKNOWN_DISTRICT`, `PAST_DATE`, …), không phải câu chữ. App tự chọn chuỗi thông báo (FR-009).
- "Câu tìm sân hợp lệ" (để đặt lại bộ đếm FR-019): `intent == search` và sau bước 3 còn ít nhất một điều kiện.

**Phương án đã loại:**
- *Chỉ kiểm tra ở app*: server không biết kết quả có hợp lệ để đặt lại bộ đếm lạc đề và không ghi được `droppedFields`; một client sửa đổi có thể đọc đầu ra thô.
- *Chỉ kiểm tra ở server*: không xử lý được cache đã cũ trong ngày hay danh mục đổi giữa chừng.

---

## R6. Đọc danh mục ở Edge Function

**Quyết định:** `catalog.ts` đọc `districts` và `amenities` (`isActive == true`) bằng Firestore REST `runQuery`, giữ trong bộ nhớ của instance 10 phút. Một instance nóng phục vụ nhiều request nên chỉ tốn ~35 lượt đọc mỗi 10 phút mỗi instance. Bảng bí danh quận (`q10` → `q10`, `quan 10`, `quan muoi`, `bt` → `binh-thanh`, …) là hằng số trong code, chỉ dùng khi LLM trả sai id.

**Lý do:** cùng một danh mục với app (`CatalogRepository` của 020), admin sửa ở 120 thì tối đa 10 phút sau server thấy.

**Phương án đã loại:** chép danh mục vào code Edge Function (lệch với admin sửa ở 120).

---

## R7. Cache trong ngày (FR-018)

**Quyết định:**
- Khóa: `aiSearchCache/{hash}`, `hash = hex(SHA-256(normalizedText + "|" + yyyyMMdd))`. `normalizedText` = câu **sau khi che PII**, Unicode NFC, chữ thường, gộp khoảng trắng, `trim`. Không bỏ dấu (spec chỉ yêu cầu chữ hoa/thường và khoảng trắng; bỏ dấu có thể gộp hai câu khác nghĩa).
- `yyyyMMdd` theo giờ Việt Nam của server. Cùng câu sang ngày mới thì khóa khác ("tối mai" mang nghĩa khác).
- Lưu `result` (kết quả **sau** bước 3 của R5, không có câu gốc, không có uid), `intent`, `promptVersion`, `createdAt`, `expiresAt` (00:00 ngày hôm sau, giờ Việt Nam).
- Đọc: `expiresAt > now` và `promptVersion` trùng thì dùng; khác thì coi như không có.
- Không lưu kết quả của lần gọi lỗi hoặc quá thời gian.
- Cache trúng: **không** trừ lượt, **vẫn** kiểm tra khóa tạm (`lockedUntil`), **vẫn** cập nhật bộ đếm lạc đề (nếu không, người dùng gửi lại một câu lạc đề cũ mãi mà không bao giờ bị khóa). App vẫn chạy sanitizer khi dùng (R5 bước 4).
- Cache dùng chung giữa mọi người dùng (không gắn uid, theo đặc tả CSDL). Vì đã che PII và không lưu câu gốc, kết quả cache không chứa dữ liệu của người khác (SC-002).

**Dọn document hết hạn:**
- **Chọn: TTL policy của Firestore trên trường `expiresAt`** của `aiSearchCache` (và `aiUsage`, `aiMetrics`, xem data-model). Tài liệu Firestore không nói TTL cần gói Blaze; xóa bằng TTL tính vào hạn mức xóa (Spark 20.000 lượt/ngày), document thường bị xóa trong 24 giờ sau khi hết hạn. Lượng cache dự kiến < 300 document/ngày nên không đáng kể.
- **Kiểm tra khi làm** (task đầu của Phase 2): bật TTL bằng `gcloud firestore fields ttls update expiresAt --collection-group=aiSearchCache --enable-ttl` trên project thật. Nếu Spark không cho bật thì dùng phương án dự phòng: Edge Function `ai-cache-cleanup` chạy bằng `pg_cron` lúc 00:30 mỗi ngày, `runQuery` `expiresAt < now` rồi xóa theo lô 300.
- Document hết hạn mà chưa bị xóa vẫn bị bỏ qua nhờ điều kiện `expiresAt > now` lúc đọc.

**Phương án đã loại:** cache phía app (DataStore): không chặn được việc gọi lại LLM từ máy khác hay sau khi cài lại app; SC-008 cần trần ở server.

---

## R8. Lượt dùng, khóa tạm, hai yêu cầu cùng lúc (FR-016 đến FR-019, SC-005, SC-008)

**Quyết định:** dùng **giữ lượt trước, hoàn lượt khi lỗi** bằng hai transaction Firestore qua REST. Không giữ transaction trong lúc chờ LLM (Firestore khóa document trong transaction; chờ LLM vài giây làm transaction dễ bị hủy).

Luồng trong `handler.ts` (chi tiết ở [contract Edge Function §3](contracts/edge-function-ai-search-parse.md#3-luồng-xử-lý)):

1. `requireUser(req)` (token hợp lệ, có `appRole`, không `LOCKED`). Khách không có token → `401` (FR-025, US5-9).
2. Kiểm tra body: `query` là chuỗi, sau `trim` từ 1 đến 200 ký tự (đếm theo code point). Sai → `400 INVALID_QUERY`, không đụng tới lượt (FR-002 ở server).
3. Che PII lần hai (R10), chuẩn hóa, tính `hash`.
4. Đọc song song: `aiSearchCache/{hash}` và `users/{uid}/aiUsage/{today}`.
5. **Khóa tạm:** `lockedUntil > now` → trả `outcome = LOCKED`. Trong 5 phút đầu của ngày, đọc thêm document của hôm qua để khóa lúc 23:58 vẫn còn hiệu lực sau 00:00.
6. **Cache trúng:** transaction cập nhật `offTopicStreak` / `lockedUntil` theo intent (không đổi `count`), trả kết quả với `cached = true`.
7. **Cache trượt — transaction giữ lượt (T1):** đọc lại `aiUsage/{today}` trong transaction; `lockedUntil > now` → `LOCKED`; `count >= 10` → `QUOTA_EXCEEDED`; còn lại thì `count += 1` và commit. Hai yêu cầu cùng lúc: Firestore cho một transaction thắng, transaction kia bị `ABORTED` và chạy lại với `count` mới, nên **không bao giờ vượt 10** (SC-005). Số lần gọi LLM ≤ số lượt đã giữ ≤ 10 × số người dùng (SC-008).
8. Gọi LLM (timeout 6 giây, R14), kiểm tra (R5).
9. **LLM lỗi, quá thời gian, hoặc bị chặn:** transaction hoàn lượt (T2): `count -= 1` (không nhỏ hơn 0). Trả `502 AI_UNAVAILABLE` hoặc `504 AI_TIMEOUT`. Nếu ghi hoàn lượt cũng lỗi (hiếm) thì người dùng mất một lượt; ghi `aiMetrics.refundFailed = true` để theo dõi.
10. **Có kết quả:** một `commit` gồm: cập nhật `offTopicStreak`/`lockedUntil` (transaction T2), ghi `aiSearchCache/{hash}`, ghi `aiMetrics/{auto}`.

**Bộ đếm lạc đề:**
- `off_topic` → `offTopicStreak += 1`; đạt 3 → `lockedUntil = now + 5 phút`, `offTopicStreak = 0`.
- Câu tìm sân hợp lệ (R5) → `offTopicStreak = 0`.
- `faq`, `unclear` (kể cả `search` bị hạ thành `unclear`) → không đổi. Spec chỉ nói câu tìm sân hợp lệ đặt lại bộ đếm; ghi vào [R16](#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác) để làm rõ.
- Đang bị khóa thì mọi yêu cầu đều bị từ chối trước khi đọc cache hay gọi LLM; app hiện đếm ngược bằng `lockedUntil` (dùng `core/util/Countdown`).

**Ngày:** `yyyyMMdd` tính bằng giờ Việt Nam **của server**. Lượt làm mới lúc 00:00 vì sang ngày là document mới.

**Bấm gửi nhiều lần (US5-7):** app chặn (nút gửi khóa khi đang có yêu cầu, R15). Server không cần khóa riêng: nếu hai yêu cầu cùng câu tới cùng lúc, cả hai đều giữ lượt hợp lệ và trả cùng kết quả. Trường hợp này chỉ xảy ra khi gửi không qua app.

**App quá 10 giây mà server vẫn chạy xong:** server dừng LLM ở 6 giây và hoàn lượt nên hiếm khi xảy ra. Nếu xảy ra, lượt đã bị trừ dù app không thấy kết quả. Kết quả đã nằm trong cache nên gửi lại đúng câu đó sẽ không bị trừ thêm. Ghi vào R16 để spec nêu rõ trường hợp này.

**Cần bổ sung vào `_shared/firestore.ts`:** `beginTransaction`, `batchGet(paths, transaction)`, `commit(writes, transaction)`, `rollback`, `runTransaction(fn, maxAttempts = 5)` (thử lại khi `ABORTED`), `runQuery` (cho danh mục và dọn cache), `deleteWrite`. File này đang ghi `TODO(B)` (B sẽ thay bằng bản đầy đủ cho `create-booking`). **Quyết định:** A viết các hàm trên với chữ ký thống nhất trước với B; khi B đưa bản đầy đủ thì giữ nguyên chữ ký này. Ghi ở mục Phối hợp của plan.

**Phương án đã loại:**
- *Gọi LLM bên trong transaction*: giữ khóa document vài giây, dễ `ABORTED`, và REST transaction chỉ sống 270 giây không hoạt động.
- *Tăng lượt sau khi có kết quả (không giữ trước)*: hai yêu cầu cùng lúc khi `count == 9` đều qua kiểm tra, cả hai gọi LLM → vượt trần (vi phạm SC-005, SC-008).
- *`increment` không kèm điều kiện*: không chặn được ở 10.
- *Bảng đếm trong Postgres của Supabase*: constitution cấm dùng database Supabase cho dữ liệu nghiệp vụ, và đặc tả CSDL đã chốt `aiUsage` trong Firestore.

---

## R9. Đo lường (FR-023)

**Quyết định:** mỗi lần gọi `ai-search-parse` (kể cả cache trúng, bị khóa, hết lượt, lỗi) ghi một document `aiMetrics/{autoId}` trong Firestore, cùng `commit` với bước cuối nếu được (không thêm round trip). Trường ở [data-model §2.3](data-model.md#23-aimetricsautoid-mới). Không có câu gốc, câu đã che, uid, hash hay địa chỉ IP. Có `expiresAt = createdAt + 90 ngày` cho TTL.
- Đồng thời `console.log` một dòng JSON cùng nội dung (xem nhanh trong Supabase Logs khi debug; gói Free giữ log ngắn hạn).
- **Xuất số liệu cho báo cáo:** `scripts/ai-eval/export-metrics.mjs` (Node + `firebase-admin`, chạy bằng tài khoản của nhóm, đọc theo khoảng ngày) xuất CSV và bảng tóm tắt: tỉ lệ từng intent, tỉ lệ schema hợp lệ, trường hay bị bỏ, tỉ lệ cache trúng, độ trễ p50/p90, token trung bình, số lần chuyển sang dự phòng.
- Rules: client không đọc/ghi; admin đọc được (để 120 làm thống kê sau nếu muốn).

**Lý do:** Firestore đã có, ghi rẻ (mỗi lượt AI thêm 1 lượt ghi, < 1.000/ngày so với 20.000), xuất bằng script giống seed. Log Supabase gói Free không giữ đủ lâu cho báo cáo cuối kỳ.

**Phương án đã loại:**
- *Bảng Postgres của Supabase*: số liệu đo không phải dữ liệu nghiệp vụ, nhưng dùng database Supabase dễ bị hiểu là vi phạm constitution và thêm migration, RLS chỉ cho một bảng.
- *Chỉ dùng log Edge Function*: không giữ đủ lâu, khó tổng hợp.
- *Document tổng hợp theo ngày* (`aiMetricsDaily`): mất phân bố độ trễ và trường bị bỏ từng lần; số lượng ghi nhỏ nên không cần gộp.

---

## R10. Che PII (FR-004, SC-007)

**Quyết định:** cùng một bộ luật, viết hai lần: `PiiMasker` (Kotlin, `feature/search/domain/ai`) chạy trên app **trước khi gửi**, và `pii.ts` chạy lại ở Edge Function **trước khi** tính hash, ghi log hay gọi LLM. Cả hai đọc chung một file vector `scripts/testdata/ai/pii-vectors.json` trong unit test (Gradle test chạy với thư mục `app/`, đọc `../scripts/...`; Deno test đọc đường dẫn tương đối), giống cách 021 dùng `geohash.vectors.json`.

Luật (thứ tự áp dụng):
1. **Email:** `[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}` → `[email]`.
2. **Số điện thoại VN:** chuỗi bắt đầu bằng `+84`, `84`, `0084` hoặc `0`, theo sau là 9–10 chữ số, cho phép dấu cách, `.`, `-` giữa các chữ số → `[sđt]`. Ví dụ `0912 345 678`, `+84 912.345.678`, `028 3823 4567`.
3. **Dãy chữ số dài** (≥ 9 chữ số liên tiếp sau khi bỏ dấu phân cách): che luôn, phòng số sai định dạng.

Không che: giá (`100k`, `100.000`, `1 trăm`), giờ (`19:00`, `7h30`), ngày (`06/10`), số quận (`q10`). Vector có các ca này để chống che nhầm.

**Lý do:** spec yêu cầu che trước khi gửi (app) và SC-007 đo cả dữ liệu tới dịch vụ AI lẫn bản ghi đo lường (server). Che hai lần vì client có thể bị sửa.

---

## R11. Hỏi lại một lần (FR-013) và chip chọn nhanh

**Quyết định:** việc quyết định hỏi gì nằm ở app, trong `ClarifyPlanner` (thuần Kotlin, có unit test). Server chỉ trả `intent` và bộ lọc đã kiểm tra.

| Tình huống sau khi kiểm tra | Câu hỏi (chuỗi có sẵn) | Chip | Bấm chip |
|---|---|---|---|
| `intent == unclear`, hoặc `search` mà không có ngày lẫn từ khóa tên sân | "Bạn muốn chơi lúc nào?" | [Tối nay] [Tối mai] [Cuối tuần] [Tự chọn] | Tối nay / Tối mai: ngày + 18:00–22:00. **Cuối tuần**: Thứ Bảy gần nhất, 18:00–22:00 (không hỏi tiếp vì chỉ được hỏi một lần) |
| `day.kind == WEEKEND` | "Bạn muốn chơi ngày nào?" | [Thứ Bảy dd/MM] [Chủ Nhật dd/MM] [Tự chọn] | Ngày đó + buổi đã hiểu (không có thì tối) |
| Có ngày, không có buổi/giờ | "Bạn muốn chơi buổi nào?" | [Sáng] [Chiều] [Tối] [Tự chọn] | Khung giờ của buổi |
| Kết quả sai định dạng hoàn toàn | "Mình chưa hiểu, bạn chọn nhanh nhé" | như dòng 1 | như dòng 1 |

- Chip "Tối nay" ẩn khi đã quá 21:30 (khung 18–22 không còn đặt được).
- Bấm chip: app tự điền, chạy `applyExternal`, **không gọi AI, không trừ lượt** (US2-2).
- Đã hỏi một lần mà vẫn thiếu (ví dụ chip cho khung đã qua bị sanitizer bỏ) hoặc bấm [Tự chọn]: mở bảng lọc thủ công của 020 với các giá trị đã hiểu (US2-4). Không có câu hỏi thứ hai: `AiAssistantViewModel` giữ cờ `clarifyUsed` theo từng câu đã gửi.
- Có tên sân mà không có ngày ("sân Hòa Bình"): **không hỏi**, tìm theo tên luôn. Tìm theo tên không cần khung giờ, và hỏi thêm làm chậm trường hợp người chơi đã biết sân.

**Lý do:** câu hỏi và chip là chuỗi cố định của app (FR-009); tách khỏi server để đổi câu chữ không phải deploy function.

---

## R12. Nhánh buổi vãng lai (FR-012)

**Quyết định:**
- Plan 080 đã chốt `DropInFilterArgs(districtIds, date, timeSlot, level, maxPrice)` cho 022. Nhưng lớp này nằm trong `feature/dropin`, mà constitution I cấm `feature/search` import. **Dùng deep link**: đề nghị C khai báo trong `nav_dropin.xml` deep link `smashnow://dropin/list?districtIds={csv}&date={yyyyMMdd}&timeSlot={MORNING|NOON|AFTERNOON|EVENING}&level={…}&maxPrice={Long}` (mọi tham số tùy chọn), `DropInListFragment` tự đổi tham số sang `DropInFilterArgs`. 022 dựng URI bằng `DropInLink` trong `core/navigation` (không phụ thuộc `feature/dropin`). Cùng cách 010 dùng deep link cho `returnTo`.
- Ánh xạ (`AiFilterMapper.toDropInLink`):
  - `districtId` → `districtIds = [districtId]`.
  - `date` → `date`.
  - `partOfDay` → `timeSlot` cùng tên. Giờ cụ thể → `timeSlot` chứa giờ bắt đầu theo mốc của **080** (Sáng 05–11, Trưa 11–13, Chiều 13–18, Tối 18–23).
  - `priceMax` → `maxPrice` (giá **mỗi lượt** của buổi vãng lai, không phải giá/giờ; "dưới 50k" vẫn là 50.000).
  - `level` → `level`.
  - `venueName`, `amenityIds`, `priceMin`, `sort` → bỏ, báo bằng chuỗi `ai_dropin_dropped` (FR-012 "điều kiện spec 080 không hỗ trợ bị bỏ kèm thông báo").
- **Khi 080 chưa có trên nhánh đang chạy:** `navController.graph.hasDeepLink(uri)` trả `false` thì hiện thông báo "Danh sách buổi vãng lai chưa sẵn sàng" và mở tìm sân thường với cùng điều kiện (đổi `type` thành `COURT`). Hết tạm khi 080 merge.
- Lệch mốc "Tối": 022 FR-007 "tối" = 18:00–22:00 (khung **đặt sân**), 080 FR-003 "Tối" = buổi **bắt đầu** trong 18:00–23:00. Hai cách hiểu không mâu thuẫn vì 080 lọc theo giờ bắt đầu, nhưng 080 FR-003 viết "mốc giống cách AI hiểu" nên cần sửa câu chữ ([R16](#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác)).

**Phương án đã loại:** đưa `DropInFilterArgs` lên `core/`. C phải sửa plan 080, và Safe Args giữa hai nav graph lồng nhau vẫn cần action toàn cục. Deep link gọn hơn.

---

## R13. Gợi ý nới điều kiện (FR-022)

**Quyết định:**
- `RelaxationPlanner` (thuần Kotlin) sinh tối đa 5 biến thể theo thứ tự ưu tiên:
  1. `priceMax + 20.000` (nếu có `priceMax`);
  2. khung giờ muộn 1 giờ;
  3. khung giờ sớm 1 giờ (chỉ khi `start - 1h` chưa qua và ≥ 05:00);
  4. bỏ tiện ích cuối cùng trong danh sách, rồi tới tiện ích kế tiếp (mỗi tiện ích một biến thể, tối đa 2).
- Đếm bằng **một** hàm mới của 020: `VenueSearchEngine.countVariants(variants: List<SearchCriteria>, limit: Int = 20): List<Int>`. Cài đặt trong `FirestoreVenueSearchEngine`:
  - một query ứng viên với điều kiện **rộng nhất** của mọi biến thể (giá cao nhất, bỏ tiện ích, cùng quận);
  - một lượt đọc `slots` mỗi cơ sở cho khung **hợp** của mọi biến thể (`start - 1h` tới `end + 1h`);
  - `priceRules`, `courts` lấy từ `VenueStaticCache` 10 phút của 020;
  - chạy `VenueMatcher` + `AvailabilityEvaluator` trên máy cho từng biến thể.
- Chỉ hiện biến thể có ≥ 1 kết quả, tối đa 3, kèm số ("Nới giá lên 120k (3 sân)"). Không gọi AI, không trừ lượt.
- Chỉ chạy khi danh sách rỗng **và** `criteria` hiện tại vẫn đúng là bộ lọc AI vừa điền (người chơi chưa sửa chip). Spec chỉ yêu cầu gợi ý cho bộ lọc do AI điền.
- Bấm gợi ý: `applyExternal(variant, AI)`.

**Ước lượng lượt đọc** (theo bảng R4 của 020, đã chọn quận, ~15 ứng viên, ~10 qua lọc rẻ, cache rule/sân đã có từ lần tìm vừa rỗng): 15 + 10 × 1 ≈ **25 lượt** mỗi lần tính gợi ý. Không chọn quận (S2, ~60 ứng viên): ~60 + 40 ≈ **100 lượt**. Chỉ xảy ra khi kết quả AI rỗng; giả sử 30 lần/ngày × 60 ≈ 1.800 lượt/ngày.

**Phương án đã loại:** gọi `count(criteria)` cho từng biến thể. 020 trả `null` khi không tính rẻ được (có khung giờ), và mỗi lần gọi đọc lại ứng viên và `slots`, nên tốn gấp ~5 lần.

---

## R14. Thời gian chờ và độ trễ (FR-020, SC-004)

**Quyết định:**
- App: `withTimeout(10_000)` quanh lời gọi function (không dùng `DEFAULT_TIMEOUT_MS` 15 giây của `core/network`). Quá giờ → `AppError.Timeout` → "Trợ lý đang bận, bạn dùng bộ lọc nhé", mở bảng lọc thủ công, giữ câu trong ô (US5-5).
- Edge Function: LLM chính `AbortSignal.timeout(6_000)`; dự phòng (chỉ khi chính trả `429`/`5xx` ngay) với thời gian còn lại, tối đa 3 giây. Tổng thời gian xử lý ở server luôn dưới 9 giây.

**Ước lượng thời gian một lần gửi (4G, RTT ~100 ms; Supabase region Singapore):**

| Bước | Ước lượng |
|---|---|
| App → Edge Function (TLS đã có kết nối) | 100–250 ms |
| Cold start Edge Function (lần đầu sau vài phút không dùng) | 200–800 ms |
| `requireUser`: xác minh token (JWKS đã cache) + đọc `users/{uid}` | 100–250 ms |
| Đọc song song cache + `aiUsage` | 100–200 ms |
| Transaction giữ lượt (begin + get + commit) | 200–400 ms |
| LLM (Flash-Lite, ~600 token vào gồm danh mục và ví dụ, ~80 token ra) | 500–1.500 ms |
| Commit cuối (streak + cache + metrics) | 100–200 ms |
| App: sanitize + vẽ chip | < 50 ms |
| **Tổng** | **~1,4–3,6 giây** (cache trúng: ~0,6–1,3 giây) |

Đạt SC-004 (4 giây ở 90% lần thử) khi function đã nóng. Cold start là rủi ro chính. Giảm thiểu:
- App gọi `ai-search-parse` với `{"warmup": true}` (không xác thực, trả `204` ngay, không đọc Firestore) khi người chơi đã đăng nhập mở màn hình nhập AI.
- Job GitHub Actions giữ project Supabase không bị tạm dừng (constitution) không giữ nóng được function, nên chỉ dựa vào warmup.
- Đo theo [quickstart §5](quickstart.md#5-đo-thời-gian-sc-004).

Danh sách kết quả của 020 tải sau khi chip đã hiện; SC-004 chỉ đo tới lúc chip hiện.

---

## R15. Màn hình, khách, giọng nói và trợ năng

**Quyết định về màn hình:**
- `AiEntryCardFragment` nằm trong `ai_entry_container` của `SearchFragment` (020): thẻ "Tìm bằng AI: <ví dụ> · Còn X/10" như wireframe.
  - Khách: thẻ khóa kèm "Đăng nhập để dùng trợ lý AI"; bấm thì `AuthNavigator.openLogin(navController, returnTo = smashnow://search)`. Theo 010 R15, `nav_search` và `SearchViewModel` còn nguyên khi quay lại nên từ khóa và bộ lọc được giữ (US5-8).
  - Hết lượt hoặc đang khóa: thẻ đổi trạng thái kèm thời gian còn lại.
- `AiAssistantFragment` (destination mới trong `nav_search`, toàn màn hình, không có wireframe riêng nên vẽ theo design system):
  - ô nhập nhiều dòng tối đa 200 ký tự, bộ đếm ký tự đổi màu khi quá 200;
  - nút gửi bị khóa khi rỗng/quá dài/đang gửi;
  - 3 chip câu ví dụ;
  - micro;
  - dòng "Còn X/10 lượt hôm nay";
  - vùng kết quả: câu hỏi lại kèm chip / nội dung chính sách / thông báo lạc đề / thông báo lỗi.
  - Câu tìm sân hợp lệ thì `popBackStack` về `SearchFragment` sau khi gọi `applyExternal`, nên chip hiện ở màn hình kết quả của 020.
  - Câu trong ô được giữ trong `SavedStateHandle` (xoay màn hình, process death) và **không bị xóa khi lỗi** (FR-020).
- `AiPolicyBottomSheet`: nội dung chính sách theo chủ đề, hoặc danh sách chủ đề nếu `faqTopic == null` (FR-014).

**Số lượt còn lại:** `AiUsageRepository.observe(uid)` dùng listener Firestore (`callbackFlow`) trên `users/{uid}/aiUsage/{today}`, chỉ khi màn hình đang hiện. Response của function cũng trả `usage` để cập nhật ngay, không phải chờ listener. Sang ngày mới (app mở qua 00:00) thì đổi document theo `AppClock`.

**Giọng nói (FR-003, US4):**
- Dùng `SpeechRecognizer` trong app với `RecognizerIntent.EXTRA_LANGUAGE = "vi-VN"`, `EXTRA_PARTIAL_RESULTS`, bọc trong `VoiceInput` (interface ở `domain/ai`, cài đặt `AndroidVoiceInput` ở `data/ai`, trả `Flow<VoiceEvent>`).
- `SpeechRecognizer.isRecognitionAvailable(context) == false` → ẩn micro (US4-5).
- Quyền `RECORD_AUDIO` chỉ xin khi bấm micro: lần đầu hiện hộp giải thích (chuỗi có sẵn) rồi mới gọi hộp xin quyền của hệ thống (US4-1). Bị từ chối → micro hiện trạng thái không khả dụng, bấm thì mở cài đặt ứng dụng (US4-3).
- Kết quả cuối được điền vào ô, **không tự gửi** (US4-2). Lỗi `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_NETWORK` → "Không nghe rõ, bạn thử lại hoặc gõ nhé", không trừ lượt (US4-4).
- Thêm `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>` vào manifest (Android 11+ cần khai báo để thấy dịch vụ nhận dạng).
- Phương án đã loại: `ACTION_RECOGNIZE_SPEECH` qua hộp thoại hệ thống (không cần quyền micro nhưng giao diện khác nhau theo máy, và không khớp kịch bản xin quyền US4-1, US4-3 của spec).

**Trợ năng:** bộ đếm ký tự là `TextInputLayout` counter (TalkBack tự đọc); "Còn X/10 lượt" dùng `accessibilityLiveRegion="polite"` để đọc khi đổi; chip câu hỏi lại và chip ví dụ có `contentDescription` đầy đủ ("Chọn tối mai, 18 giờ đến 22 giờ"); micro có mô tả theo trạng thái (đang nghe / không khả dụng); vùng chạm 48dp.

**Chế độ tối:** chỉ dùng thuộc tính theme Material 3, không màu cứng.

---

## R16. Những chỗ cần sửa spec hoặc tài liệu khác

| # | Ở đâu | Đề nghị | Lý do |
|---|---|---|---|
| 1 | 022 FR-019 | Ghi rõ: `faq` và `unclear` không làm thay đổi bộ đếm lạc đề; chỉ câu tìm sân hợp lệ đặt lại | R8; spec chưa nói câu `faq` ở giữa có cắt chuỗi "liên tiếp" không |
| 2 | 022 FR-016 / US5-5 | Thêm: nếu app hết 10 giây nhưng server vẫn trả kết quả, lượt có thể đã bị trừ; gửi lại đúng câu đó trong ngày thì dùng cache, không trừ thêm | R8, R14 |
| 3 | 022 FR-012 và 080 FR-003 | 080 FR-003 đổi "mốc giống cách AI hiểu" thành "AI điền khung giờ theo giờ bắt đầu" (Tối của 080 là 18–23, của 022 là 18–22) | R12 |
| 4 | 022 FR-012 | Ghi rõ giá của buổi vãng lai là giá mỗi lượt | R12 |
| 5 | 022 Assumptions | Bỏ dòng "phải thống nhất A ↔ C trước `/speckit-plan`": plan 080 đã chốt `DropInFilterArgs`; thay bằng deep link ở R12 | R1, R12 |
| 6 | 022 US2-1 | Chip [Cuối tuần] điền Thứ Bảy gần nhất buổi tối (không hỏi tiếp, vì chỉ được hỏi một lần) | R11 |
| 7 | 022 Assumptions | Ghi nhà cung cấp đã chọn (Gemini chính, Groq dự phòng) thay cho "chốt ở họp 09/10" | R2 |
| 8 | 020 contract `search-criteria.md` §3, tasks T051, T088 | `applyExternal` trả `SanitizeResult`; micro trong ô tìm kiếm là lối vào 022; thêm `countVariants` (§4) | R1, R13 |
| 9 | Chính sách quyền riêng tư (011) | Thêm một câu: câu gõ vào trợ lý AI (đã che số điện thoại, email) được gửi tới Google Gemini API để phân tích | R2, constitution VI |
| 10 | `docs/proposal/AI-feature.md` mục 1 | Ví dụ JSON đổi theo schema mới (`day`, `partOfDay`, `districtId`) hoặc ghi chú "minh họa" | R3 |
