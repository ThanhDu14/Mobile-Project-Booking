# Data Model: Tìm sân thông minh (AI Assistant)

**Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md)

Spec 022 không ghi dữ liệu nghiệp vụ (FR-010). Mọi document dưới đây chỉ do Edge Function `ai-search-parse` ghi (🔒). Câu gốc của người dùng **không được lưu** ở bất kỳ đâu.

## 1. Firestore: dữ liệu 022 đọc (của người khác)

| Document | Trường dùng | Ai ghi | Dùng để |
|---|---|---|---|
| `users/{uid}` | `accountStatus` | A (010), D (120) | `requireUser` từ chối `LOCKED` (FR-025) |
| `districts/{id}` | `name`, `isActive` | A (seed), admin (120) | Danh mục trong prompt và kiểm tra (R3, R6) |
| `amenities/{id}` | `name`, `isActive` | A (seed), admin (120) | Như trên |

Định nghĩa đầy đủ hai danh mục ở [data-model 020 §2.2](../020-court-search/data-model.md#22-districtsdistrictid-và-amenitiesamenityid).

## 2. Firestore: dữ liệu 022 sở hữu (A)

### 2.1. `users/{uid}/aiUsage/{yyyyMMdd}` 🔒

`yyyyMMdd` là ngày theo giờ Việt Nam của **server**.

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `count` | Int | Số lượt đã giữ trong ngày, 0–10. Tăng trước khi gọi LLM, giảm lại nếu LLM lỗi (R8) |
| `offTopicStreak` | Int | 0–2. Đạt 3 thì về 0 và đặt `lockedUntil` |
| `lockedUntil` | Timestamp? | `now + 5 phút` khi đủ 3 câu lạc đề liên tiếp |
| `updatedAt` | Timestamp | **[MỚI]** server timestamp |
| `expiresAt` | Timestamp | **[MỚI]** 00:00 ngày thứ 8 sau ngày của document (giờ Việt Nam). Dùng cho TTL (R7). Giữ 7 ngày để xem lại khi kiểm thử |

Hằng số: `DAILY_LIMIT = 10`, `OFF_TOPIC_LIMIT = 3`, `LOCK_MINUTES = 5`.

**Chuyển trạng thái** (mọi bước trong transaction, [R8](research.md#r8-lượt-dùng-khóa-tạm-hai-yêu-cầu-cùng-lúc-fr-016-đến-fr-019-sc-005-sc-008)):

```text
(không có document) ──giữ lượt──▶ count = 1
count = n (n < 10) ──giữ lượt──▶ count = n + 1
count = n ──LLM lỗi / quá giờ──▶ count = max(n - 1, 0)
count = 10 ──giữ lượt──▶ từ chối QUOTA_EXCEEDED (không đổi)

offTopicStreak = k ──off_topic──▶ k + 1; nếu = 3 → offTopicStreak = 0, lockedUntil = now + 5'
offTopicStreak = k ──search hợp lệ──▶ 0
offTopicStreak = k ──faq / unclear──▶ k (không đổi)
lockedUntil > now ──mọi yêu cầu──▶ từ chối LOCKED (không đổi gì)
```

Lượt còn lại hiện trên app: `10 - count`.

### 2.2. `aiSearchCache/{hash}` 🔒

`hash` = hex của SHA-256(`normalizedText` + `"|"` + `yyyyMMdd`). `normalizedText` là câu đã che PII, NFC, chữ thường, gộp khoảng trắng, `trim` ([R7](research.md#r7-cache-trong-ngày-fr-018)).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `intent` | String | `search`, `unclear`, `faq`, `off_topic` (sau kiểm tra) |
| `result` | Map | Đúng dạng `ParsedResult` trả cho app ([contract Edge Function §2](contracts/edge-function-ai-search-parse.md#2-response)), không có `usage`, `cached` |
| `searchValid` | Boolean | Câu tìm sân hợp lệ (để cập nhật bộ đếm khi cache trúng) |
| `promptVersion` | String | Khác phiên bản hiện tại thì bỏ qua cache |
| `createdAt` | Timestamp | server timestamp |
| `expiresAt` | Timestamp | 00:00 ngày hôm sau (giờ Việt Nam). TTL |

Không có câu gốc, câu đã che, uid. Không gắn với người dùng.

### 2.3. `aiMetrics/{autoId}` 🔒 [MỚI]

Một document cho mỗi lần gọi `ai-search-parse` (FR-023, [R9](research.md#r9-đo-lường-fr-023)).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `day` | String | `yyyyMMdd` (giờ Việt Nam) |
| `outcome` | String | `PARSED`, `CACHED`, `LOCKED`, `QUOTA_EXCEEDED`, `INVALID_QUERY`, `AI_TIMEOUT`, `AI_UNAVAILABLE`, `AI_QUOTA` |
| `intent` | String? | Sau kiểm tra; `null` khi không có kết quả |
| `llmIntent` | String? | Intent LLM trả trước khi bị hạ (để đo tỉ lệ hạ `search` → `unclear`) |
| `type` | String? | `COURT`, `DROP_IN` |
| `schemaValid` | Boolean? | JSON đúng schema |
| `searchValid` | Boolean? | |
| `fields` | Array\<String> | Tên điều kiện còn lại sau kiểm tra (`district`, `date`, `time`, `price`, `amenity`, `keyword`, `sort`, `level`), không có giá trị |
| `droppedFields` | Array\<String> | Mã lý do bị bỏ (`UNKNOWN_DISTRICT`, `PAST_DATE`, `UNKNOWN_FIELD:<tên>`, …) |
| `piiMasked` | Boolean | Server có che thêm gì không (lẽ ra luôn `false` vì app đã che) |
| `queryLength` | Int | Độ dài câu (không có nội dung) |
| `latencyMs` | Int | Toàn bộ request ở server |
| `llmLatencyMs` | Int? | |
| `inputTokens`, `outputTokens` | Int? | Lượng sử dụng của dịch vụ AI |
| `provider`, `model` | String? | `gemini` / `groq`, tên mô hình |
| `fallbackUsed` | Boolean | Đã chuyển sang dự phòng |
| `refundFailed` | Boolean | Hoàn lượt lỗi (R8) |
| `promptVersion` | String | |
| `env` | String | `prod`, `local`, `eval` |
| `createdAt` | Timestamp | |
| `expiresAt` | Timestamp | `createdAt + 90 ngày`. TTL |

Không có uid, hash của câu, câu gốc, câu đã che, IP, User-Agent (FR-023, SC-007).

### 2.4. Rules

| Collection | Người chơi | Admin | Server |
|---|---|---|---|
| `users/{uid}/aiUsage/{day}` | R khi `request.auth.uid == uid`; không ghi | R | Ghi toàn bộ |
| `aiSearchCache/{hash}` | Không đọc, không ghi | Không | Ghi toàn bộ |
| `aiMetrics/{id}` | Không đọc, không ghi | R | Ghi toàn bộ |

Rules cụ thể và test ở [contracts/rules-and-tests.md](contracts/rules-and-tests.md).

### 2.5. Xóa tài khoản

`delete-account` (011, A) đã có bước xóa `users/{uid}/aiUsage` (011 contract §1 bước 7). `aiSearchCache` và `aiMetrics` không gắn uid nên không phải xóa.

## 3. Mô hình phía app

Package `com.example.booking_app.feature.search` (cùng 020, 021).

### 3.1. `domain/ai/model`

| Kiểu | Trường | Ghi chú |
|---|---|---|
| `AiIntent` | `SEARCH`, `UNCLEAR`, `FAQ`, `OFF_TOPIC` | |
| `FaqTopic` | `CANCELLATION`, `DEPOSIT`, `VOUCHER`, `PAYMENT` | `null` = chưa xác định → danh sách chủ đề |
| `SearchType` | `COURT`, `DROP_IN` | |
| `PartOfDay` | `MORNING(05:00, 11:00)`, `NOON(11:00, 13:00)`, `AFTERNOON(13:00, 18:00)`, `EVENING(18:00, 22:00)` | Mốc theo FR-007 |
| `DropInLevel` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` | Cùng enum `users.level` |
| `ParsedFilter` | `type`, `keyword?`, `districtId?`, `date: LocalDate?`, `dateChoices: List<LocalDate>` (khi cuối tuần), `partOfDay?`, `start: TimeOfDay?`, `end: TimeOfDay?`, `priceMin?`, `priceMax?`, `amenityIds: Set`, `sort: SortOption?`, `level?` | Đã kiểm tra ở server. `start`/`end` đã ở dạng 24 giờ, thẳng mốc 30 phút |
| `AiParseResult` | `intent`, `faqTopic?`, `filter: ParsedFilter`, `dropped: List<AiDropReason>`, `cached: Boolean`, `usage: AiUsage` | Kết quả của `AiSearchApi` |
| `AiDropReason` | `UNKNOWN_DISTRICT`, `UNKNOWN_AMENITY`, `PAST_DATE`, `DATE_OUT_OF_RANGE`, `INVALID_TIME`, `INVALID_PRICE`, `UNKNOWN_FIELD`, `KEYWORD_NOT_IN_QUERY`, `SCHEMA_INVALID` | Mã lỗi từ server; chọn chuỗi thông báo ở app |
| `AiUsage` | `used`, `limit` (10), `lockedUntil: Instant?` | `remaining = limit - used` |
| `AiSubmitOutcome` | `Parsed(AiParseResult)`, `Locked(until, usage)`, `QuotaExceeded(usage)` | Kết quả hợp lệ của function (HTTP 200) |
| `ClarifyQuestion` | `kind: TIME / DATE / PART_OF_DAY / GENERIC`, `options: List<ClarifyOption>` | [R11](research.md#r11-hỏi-lại-một-lần-fr-013-và-chip-chọn-nhanh) |
| `ClarifyOption` | `labelKey`, `apply: (ParsedFilter) -> ParsedFilter` hoặc `Manual` | [Tự chọn] là `Manual` |
| `Relaxation` | `kind: PRICE_UP / LATER / EARLIER / DROP_AMENITY`, `criteria: SearchCriteria`, `count: Int` | [R13](research.md#r13-gợi-ý-nới-điều-kiện-fr-022) |
| `VoiceEvent` | `Ready`, `Partial(text)`, `Final(text)`, `Error(kind: NO_MATCH / NETWORK / BUSY / OTHER)` | |

### 3.2. Lớp thuần Kotlin (`domain/ai`)

| Lớp | Việc |
|---|---|
| `PiiMasker` | Che email, số điện thoại ([R10](research.md#r10-che-pii-fr-004-sc-007)) |
| `AiQueryInput` | `validate(text): InputState` (`EMPTY`, `TOO_LONG`, `OK`), đếm code point, giới hạn 200 |
| `AiFilterMapper` | `ParsedFilter` → `SearchCriteria` (020) hoặc `Uri` deep link vãng lai ([R12](research.md#r12-nhánh-buổi-vãng-lai-fr-012)) kèm danh sách điều kiện bị bỏ |
| `ClarifyPlanner` | Quyết định có hỏi lại không và hỏi gì (R11) |
| `RelaxationPlanner` | Sinh biến thể nới điều kiện (R13) |

### 3.3. Trạng thái màn hình

```text
AiAssistantUiState(
  input: String,                     // giữ trong SavedStateHandle, không xóa khi lỗi
  inputState: InputState,            // EMPTY | TOO_LONG | OK → khóa nút gửi, đổi màu bộ đếm
  sending: Boolean,
  usage: AiUsageUi,                  // Remaining(n) | Exhausted | Locked(secondsLeft) | Unknown
  access: Access,                    // SIGNED_IN | GUEST
  voice: VoiceUi,                    // Hidden | Idle | Listening | Denied
  panel: AiPanel                     // None | Clarify(question) | Policy(topic?) | OffTopic | Notice(messageKey)
)

AiEntryCardUiState(access, usage, exampleKey)
```

Sự kiện một lần (`Channel`): quay về màn hình tìm kiếm sau khi áp bộ lọc, mở bảng lọc thủ công (kèm `SearchCriteria` đã hiểu), mở deep link vãng lai, mở đăng nhập, xin quyền micro / hiện giải thích, mở cài đặt ứng dụng, snackbar.

### 3.4. Chuyển trạng thái của một lần gửi

```text
Idle ──gửi (OK, còn lượt, không khóa)──▶ Sending
Sending ──Parsed(SEARCH, đủ điều kiện)──▶ applyExternal → quay về SearchFragment
Sending ──Parsed(SEARCH thiếu / UNCLEAR)──▶ Clarify (clarifyUsed = true)
Clarify ──bấm chip──▶ applyExternal → quay về SearchFragment (không gọi AI)
Clarify ──[Tự chọn] / vẫn thiếu──▶ mở bảng lọc thủ công với điều kiện đã hiểu
Sending ──Parsed(FAQ)──▶ Policy(topic)
Sending ──Parsed(OFF_TOPIC)──▶ OffTopic
Sending ──Parsed(SEARCH, type DROP_IN)──▶ mở deep link vãng lai
Sending ──Locked / QuotaExceeded──▶ Notice + cập nhật usage
Sending ──Network / Timeout / AI_UNAVAILABLE──▶ Notice("Trợ lý đang bận…") + mở bảng lọc thủ công, giữ input
```

## 4. Index

Không có index mới: `aiUsage`, `aiSearchCache` chỉ đọc theo ID. TTL policy (không phải index) trên `expiresAt` của ba collection group `aiUsage`, `aiSearchCache`, `aiMetrics`.

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 2 (bảng collection):** thêm dòng `aiMetrics/{id}` — "Số liệu ẩn danh mỗi lần gọi AI Assistant 🔒 — A — 02 (AI)".
2. **Mục 3, phần AI Assistant:**
   - `aiUsage`: thêm `updatedAt`, `expiresAt` (TTL, giữ 7 ngày); ghi `count` là số lượt **đã giữ** (giữ trước, hoàn khi lỗi).
   - `aiSearchCache`: đổi `hash` thành "SHA-256 của câu đã che PII + chuẩn hóa + `|` + ngày"; thêm `intent`, `searchValid`, `promptVersion`, `createdAt`.
   - Thêm `aiMetrics` với các trường ở §2.3.
   - Ghi chú TTL trên `expiresAt` cho ba collection (hoặc `ai-cache-cleanup` nếu Spark không bật được TTL).
3. **Mục 7 (Phân quyền):** dòng `aiUsage`, `aiSearchCache` thêm `aiMetrics`: người chơi không đọc; admin R `aiUsage`, `aiMetrics`; không ai đọc `aiSearchCache` ngoài server.
4. **Mục 8a (Edge Functions):** `ai-search-parse` ghi rõ "giữ lượt bằng transaction, LLM Gemini (dự phòng Groq), ghi `aiMetrics`"; thêm `ai-cache-cleanup` (pg_cron, chỉ khi không dùng được TTL).
5. **Mục secrets/Edge Functions:** thêm `LLM_PROVIDER`, `LLM_MODEL`, `GEMINI_API_KEY`, `GROQ_API_KEY`, `GROQ_MODEL`, `AI_ENV` ([contract Edge Function §6](contracts/edge-function-ai-search-parse.md#6-secrets-và-cấu-hình)).
