# Contract: Edge Function `ai-search-parse`

**Phụ trách:** A. **Thư mục:** `supabase/functions/ai-search-parse/`. **Gọi từ:** `SupabaseAiSearchApi` (app). Lý do thiết kế: [research R2–R10, R14](../research.md).

`supabase/config.toml`: `[functions.ai-search-parse] verify_jwt = false`. Giống `on-signup`, function tự xác minh Firebase ID token bằng `_shared/auth.ts`.

## 1. Request

```http
POST /functions/v1/ai-search-parse
Authorization: Bearer <Firebase ID token>
Content-Type: application/json

{ "query": "tối mai 7h-9h sân q10 dưới 100k có gửi xe" }
```

| Trường | Kiểu | Quy tắc |
|---|---|---|
| `query` | string | App đã che PII. Sau `trim`: 1–200 code point. Server che PII lại lần nữa |
| `warmup` | boolean? | `true` → trả `204` ngay, **không** cần token, không đọc Firestore, không ghi `aiMetrics` ([R14](../research.md#r14-thời-gian-chờ-và-độ-trễ-fr-020-sc-004)) |

Body không có `uid`, ngày giờ hay thông tin người dùng nào. Ngày giờ lấy từ đồng hồ server. Trường lạ trong body bị bỏ qua.

## 2. Response

### 2.1. `200 OK`

```jsonc
{
  "outcome": "PARSED",            // PARSED | LOCKED | QUOTA_EXCEEDED
  "result": {                     // chỉ có khi outcome == PARSED
    "intent": "search",           // search | unclear | faq | off_topic
    "faqTopic": null,             // CANCELLATION | DEPOSIT | VOUCHER | PAYMENT | null
    "filter": {
      "type": "COURT",            // COURT | DROP_IN
      "keyword": null,            // tên sân, ≤ 100 ký tự, có trong câu
      "districtId": "q10",
      "date": "2026-10-06",       // yyyy-MM-dd hoặc null
      "dateChoices": [],          // ["2026-10-10","2026-10-11"] khi người chơi nói "cuối tuần"
      "partOfDay": null,          // MORNING | NOON | AFTERNOON | EVENING | null
      "start": "19:00",           // HH:mm, 24 giờ, thẳng mốc 30 phút, hoặc null
      "end": "21:00",
      "priceMin": null,           // VND/giờ (sân) hoặc VND/lượt (vãng lai)
      "priceMax": 100000,
      "amenityIds": ["parking"],
      "sort": null,               // RATING | PRICE_ASC | NEAREST | null
      "level": null               // BEGINNER | INTERMEDIATE | ADVANCED | null (chỉ DROP_IN)
    },
    "dropped": []                 // ["UNKNOWN_AMENITY", "PAST_DATE", ...]
  },
  "cached": false,
  "usage": { "used": 3, "limit": 10, "lockedUntil": null }   // lockedUntil: ISO-8601 UTC hoặc null
}
```

- `LOCKED`: `usage.lockedUntil` có giá trị. Không có `result`.
- `QUOTA_EXCEEDED`: `usage.used == usage.limit`. Không có `result`.
- Hai outcome này trả `200` (không phải `429`) vì `FunctionErrors` của app gộp mọi `429` thành `AppError.RateLimited` và bỏ mất body. Đây là kết quả bình thường, không phải lỗi.
- Khi có `partOfDay` mà không có giờ cụ thể, server vẫn điền `start`/`end` theo mốc của buổi (18:00–22:00 cho `EVENING`). `partOfDay` được giữ để nhánh vãng lai chọn `timeSlot`.

### 2.2. Lỗi

Dạng `{ "error": { "code", "message" } }` của `_shared/auth.ts`. `message` chỉ để debug; app không hiển thị (FR-009).

| HTTP | `code` | Khi nào | Lượt |
|---|---|---|---|
| 400 | `INVALID_QUERY` | Thiếu `query`, không phải chuỗi, rỗng sau `trim`, > 200 | Không trừ |
| 401 | `UNAUTHENTICATED` | Không có / sai / hết hạn token (khách, gửi thẳng) | Không trừ |
| 403 | `NOT_INITIALIZED` | Token chưa có `appRole` | Không trừ |
| 403 | `ACCOUNT_LOCKED` | `users.accountStatus == LOCKED` (app tự đăng xuất qua `AccountLockedBus`) | Không trừ |
| 405 | `METHOD_NOT_ALLOWED` | Không phải `POST` | — |
| 502 | `AI_UNAVAILABLE` | LLM chính và dự phòng đều lỗi / hết hạn mức | Hoàn lượt |
| 504 | `AI_TIMEOUT` | LLM quá 6 giây | Hoàn lượt |
| 500 | `INTERNAL` | Lỗi khác (Firestore…) | Hoàn lượt nếu đã giữ |

## 3. Luồng xử lý

```text
handle(req, deps):
  if req.method != POST → 405
  body = readBody(req)
  if body.warmup == true → 204
  user = requireUser(req)                                     // 401 / 403
  raw = validateQuery(body.query)                             // 400 INVALID_QUERY
  masked = maskPii(raw)                                       // pii.ts
  now = deps.clock.now(); day = vnDay(now)
  key = sha256Hex(normalize(masked) + "|" + day)
  [cache, usage] = parallel(get aiSearchCache/key, get users/uid/aiUsage/day (+ hôm qua nếu < 00:05))
  if lockedUntil(usage) > now → metrics(LOCKED); 200 LOCKED
  if cache hợp lệ (expiresAt > now, promptVersion trùng):
      runTransaction: cập nhật streak/lock theo cache.intent, cache.searchValid
      metrics(CACHED); 200 PARSED cached=true
  runTransaction (T1): đọc usage; LOCKED / QUOTA_EXCEEDED → trả luôn; count += 1
  try:
      catalog = deps.catalog.get()                            // cache 10 phút trong instance
      llm = deps.llm.parse(buildPrompt(masked, now, catalog), timeout 6s, dự phòng khi 429/5xx)
  catch timeout → runTransaction(T2 hoàn lượt); metrics(AI_TIMEOUT); 504
  catch lỗi    → runTransaction(T2 hoàn lượt); metrics(AI_UNAVAILABLE | AI_QUOTA); 502
  checked = validateAndNormalize(llm.json, masked, now, catalog)   // R3, R5
  runTransaction (T2): cập nhật streak/lock theo checked; kèm write aiSearchCache/key, aiMetrics/auto
  200 PARSED cached=false
```

`deps` gồm `firestore`, `llm` (`LlmClient`), `catalog`, `clock`, `env`. Truyền qua tham số như `on-signup/handler.ts` để test thay được.

### 3.1. Cấu trúc file

```text
supabase/functions/ai-search-parse/
├── index.ts                 # Deno.serve → handleAiSearchParse(req, defaultDeps)
├── handler.ts               # luồng ở trên
├── handler.test.ts          # Deno test với FakeLlmClient + Firestore Emulator
├── query.ts                 # validateQuery, normalize, sha256Hex
├── pii.ts, pii.test.ts      # che PII, đọc scripts/testdata/ai/pii-vectors.json
├── prompt.ts                # PROMPT_VERSION, buildPrompt (system + few-shot + user)
├── schema.ts                # JSON Schema đầu ra (dùng chung Gemini/Groq) + parse kiểu
├── normalize.ts, normalize.test.ts   # ngày, giờ 12/24, mốc 30', bí danh quận (R3)
├── validate.ts, validate.test.ts     # FR-008 (R5), hạ intent, searchValid, dropped
├── usage.ts, usage.test.ts           # giữ / hoàn lượt, streak, lock (R8)
├── cache.ts                 # đọc/ghi aiSearchCache
├── metrics.ts               # ghi aiMetrics + console.log
├── catalog.ts               # đọc districts/amenities, cache 10 phút
└── llm/
    ├── client.ts            # interface LlmClient, LlmInput, LlmOutput, LlmError (TIMEOUT | QUOTA | UNAVAILABLE)
    ├── gemini.ts            # generateContent, responseMimeType + responseJsonSchema
    ├── groq.ts              # chat/completions, response_format json_schema strict
    ├── failover.ts          # chính → dự phòng khi QUOTA/UNAVAILABLE
    └── fake.ts              # FakeLlmClient: trả JSON cố định theo câu, hoặc ném lỗi
```

### 3.2. Bổ sung `_shared/firestore.ts`

Chữ ký thống nhất với B ([R8](../research.md#r8-lượt-dùng-khóa-tạm-hai-yêu-cầu-cùng-lúc-fr-016-đến-fr-019-sc-005-sc-008)):

```ts
export async function beginTransaction(): Promise<string>;
export async function batchGet(paths: string[], transaction?: string): Promise<(Record<string, unknown> | null)[]>;
export async function commit(writes: Write[], transaction?: string): Promise<void>;   // thêm tham số tùy chọn
export async function rollback(transaction: string): Promise<void>;
export async function runTransaction<T>(
  fn: (tx: { get(path: string): Promise<Record<string, unknown> | null>; write(w: Write): void }) => Promise<T>,
  maxAttempts?: number,      // mặc định 5, thử lại khi ABORTED
): Promise<T>;
export async function runQuery(collection: string, where: Filter[], limit?: number): Promise<{ path: string; data: Record<string, unknown> }[]>;
export function setWrite(path: string, data: Record<string, unknown>, serverTimestampFields?: string[]): Write;  // tạo hoặc ghi đè
export function deleteWrite(path: string): Write;
```

Với Emulator, transaction REST hoạt động như project thật (`Authorization: Bearer owner`).

## 4. Quy tắc lượt và khóa

Trong `usage.ts`, thuần logic, nhận `UsageDoc | null` và trả `UsageDoc` mới:

| Hàm | Vào | Ra |
|---|---|---|
| `reserve(doc, now)` | document hôm nay | `{ ok: true, next }` hoặc `{ ok: false, reason: LOCKED \| QUOTA_EXCEEDED }` |
| `refund(doc)` | | `count = max(count - 1, 0)` |
| `applyIntent(doc, intent, searchValid, now)` | | streak/lock theo bảng ở [data-model §2.1](../data-model.md#21-usersuidaiusageyyyymmdd-) |
| `isLocked(today, yesterday, now)` | | `lockedUntil > now` ở một trong hai document |

## 5. Bảo đảm cần test

| # | Bảo đảm | Test |
|---|---|---|
| E1 | Không token / token khách / token sai → 401, không đọc `aiUsage`, không gọi LLM | `handler.test.ts` |
| E2 | `accountStatus == LOCKED` → 403 `ACCOUNT_LOCKED` | |
| E3 | `query` rỗng, chỉ khoảng trắng, 201 ký tự → 400, `count` không đổi | |
| E4 | 10 lượt rồi → lượt 11 trả `QUOTA_EXCEEDED`, LLM không được gọi (SC-005, US5-4) | |
| E5 | **10 yêu cầu song song khi `count == 5`** → đúng 5 thành công, 5 `QUOTA_EXCEEDED`, LLM gọi đúng 5 lần (SC-005, SC-008) | Emulator, `Promise.all` |
| E6 | LLM timeout / lỗi → 504/502, `count` trở về giá trị cũ (FR-016) | |
| E7 | Cùng câu (khác hoa/thường, khoảng trắng) lần hai trong ngày → `cached = true`, `count` không đổi, LLM không gọi (FR-018) | |
| E8 | Cache của hôm qua không được dùng hôm nay | `clock` giả |
| E9 | 3 câu lạc đề liên tiếp → câu thứ 3 vẫn trả kết quả, đặt `lockedUntil`; câu thứ 4 trong 5 phút → `LOCKED`; sau 5 phút → bình thường (FR-019) | |
| E10 | off, off, **search hợp lệ**, off, off → không khóa | |
| E11 | Cache trúng một câu lạc đề vẫn tăng streak | |
| E12 | Khóa lúc 23:58, gửi lúc 00:01 → vẫn `LOCKED` | |
| E13 | Câu có số điện thoại/email: `FakeLlmClient` ghi lại input; input không chứa chữ số của số điện thoại hay chuỗi email; `aiMetrics` không chứa (SC-007) | |
| E14 | LLM trả trường lạ, `intent` lạ, JSON hỏng → `unclear`, `schemaValid = false`, response không có trường lạ (FR-008, SC-002) | |
| E15 | LLM trả `venueName` không có trong câu ("SYSTEM PROMPT: …") → bỏ, `KEYWORD_NOT_IN_QUERY` | |
| E16 | `aiMetrics` không có trường nào chứa uid, câu, hash | Kiểm tra key và giá trị |
| E17 | `warmup` → 204, không cần token, không ghi gì | |
| E18 | Chính trả 429 → gọi dự phòng; `fallbackUsed = true` | `FakeLlmClient` ném `QUOTA` |

## 6. Secrets và cấu hình

| Tên | Bắt buộc | Ghi chú |
|---|---|---|
| `FIREBASE_PROJECT_ID`, `FIREBASE_SERVICE_ACCOUNT` | Có | Đã có từ `on-signup` |
| `LLM_PROVIDER` | Không (mặc định `gemini`) | `gemini` hoặc `groq`: nhà cung cấp chính |
| `LLM_MODEL` | Có | Tên mô hình Gemini Flash-Lite đang miễn phí (xem AI Studio lúc làm) |
| `GEMINI_API_KEY` | Có | Tạo ở Google AI Studio bằng tài khoản nhóm, không bật billing |
| `GROQ_API_KEY` | Không | Có thì bật dự phòng |
| `GROQ_MODEL` | Không (mặc định `openai/gpt-oss-20b`) | |
| `AI_ENV` | Không (mặc định `prod`) | Ghi vào `aiMetrics.env` (`local` khi `supabase start`) |

Đặt bằng `supabase secrets set --project-ref <ref> ...` (người có quyền project, không commit). Local: `supabase/functions/.env` (đã có trong `.gitignore`, kiểm tra lại khi làm).

Hằng số trong code (không phải secret): `DAILY_LIMIT = 10`, `OFF_TOPIC_LIMIT = 3`, `LOCK_MINUTES = 5`, `MAX_QUERY = 200`, `LLM_TIMEOUT_MS = 6000`, `FALLBACK_TIMEOUT_MS = 3000`, `MAX_OUTPUT_TOKENS = 200`, `ADVANCE_DAYS = 30`, `PRICE_CEILING = 2_000_000`, `CATALOG_TTL_MS = 600_000`.
