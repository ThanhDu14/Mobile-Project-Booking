---

description: "Danh sách task triển khai spec 022 – Tìm sân thông minh (AI Assistant)"
---

# Tasks: Tìm sân thông minh (AI Assistant)

**Input**: tài liệu thiết kế trong `specs/022-ai-court-search/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution IV bắt buộc test cho logic ViewModel/UseCase/Repository và Security Rules. Spec này có thêm FR-024 (bộ câu mẫu). Cụ thể:
- Edge Function: hàm thuần (`query`, `pii`, `normalize`, `validate`, `usage`, `prompt`), client LLM với `fetch` giả, `handler` với Firebase Emulator (E1–E18). **Test tự động chỉ dùng `FakeLlmClient`**;
- Rules AI-1…AI-8 trên Emulator;
- app: `PiiMasker` (vector dùng chung với Deno), `AiQueryInput`, `AiFilterMapper`, `ClarifyPlanner`, `RelaxationPlanner`, use case, `SupabaseAiSearchApi`, ViewModel (Turbine);
- bộ 40 câu mẫu chạy với LLM thật (SC-001, SC-002, SC-007).

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story (US1…US6). Phase 2 tách PR nhỏ để merge sớm:
- 2A `_shared/firestore.ts` (dùng chung với B);
- 2B Rules;
- 2C Edge Function `ai-search-parse`;
- 2D bộ câu mẫu;
- 2E sửa tài liệu 020;
- 2F nền phía app.

Phần server (2A–2D) **làm được ngay**, không chờ 020.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US6)
- **[ĐỘC LẬP]**: không cần code của 020, làm được ngay (research R1)
- **Phụ thuộc: 020 Txxx**: cần task đó của [tasks 020](../020-court-search/tasks.md) xong trước
- **[BLOCKED: X]**: cần người phụ trách X làm xong phần của họ mới chạy thật được; có phương án tạm ghi ngay trong task

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `SEARCH/` | `app/src/main/java/com/example/booking_app/feature/search/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/` |
| `FN/` | `supabase/functions/` |
| `AIFN/` | `supabase/functions/ai-search-parse/` |
| `FT/` | `firestore-tests/test/` |
| `EVAL/` | `scripts/ai-eval/` |
| `TD/` | `scripts/testdata/ai/` |

**Tình trạng ngày 2026-10-28:**
- Trên `main` đã có: `core/session` (`AuthNavigator`, `DeepLinkAuthNavigator`, `SessionManager`), `core/network` (`AppError`, `FunctionErrors`, `TaskAwait`, `IdTokenProvider`), `core/util/AppClock`, `core/util/Countdown`, `core/ui/LoginPromptView`, Supabase client (`functions-kt` 3.6.0). Server có `FN/_shared/auth.ts` (`requireUser`), `FN/_shared/firestore.ts` (bản rút gọn, `TODO(B)`), `FN/_shared/testing.ts`, `FN/on-signup/`.
- **Chưa có** gì của 020 (`feature/search`, `core/catalog`, `core/schedule`, `core/text`) và không có `ai-search-parse`.
- Plan 080 (C) đã chốt `DropInFilterArgs`, chưa có code.

---

## Phase 1: Setup

**Purpose**: xác nhận nhà cung cấp LLM, khóa, cấu hình và tài nguyên.

- [ ] T001 [ĐỘC LẬP] **Làm đầu tiên.** Đăng nhập Google AI Studio bằng tài khoản nhóm, mở trang Rate limit; đăng nhập console.groq.com, mở Rate limits. Ghi số RPM, RPD, TPM thật của mô hình Gemini **Flash-Lite** đang miễn phí và của `openai/gpt-oss-20b` (Groq) vào bảng ở [research R2](research.md#r2-chọn-nhà-cung-cấp-llm) (thay các số "theo nhiều nguồn"), kèm ngày xem. Kiểm tra Gemini có hỗ trợ `responseJsonSchema` cho mô hình đó. **Nếu không còn Flash-Lite miễn phí, hoặc gói miễn phí cần gắn thẻ: dừng lại báo nhóm**, không làm tiếp T002 trở đi
- [ ] T002 [ĐỘC LẬP] Người giữ tài khoản nhóm tạo `GEMINI_API_KEY` (AI Studio, **không** bật billing) và `GROQ_API_KEY`. Ghi vào `FN/.env` trên máy (file đã bị `.gitignore` chặn: `supabase/**/.env*`). Không gửi khóa qua chat, không dán vào prompt AI (constitution VII). Chưa đặt `supabase secrets` cho project thật (làm ở T109)
- [ ] T003 [P] [ĐỘC LẬP] Tạo `FN/.env.example` (được commit nhờ `!supabase/**/.env.example`) liệt kê đủ biến ở [contract §6](contracts/edge-function-ai-search-parse.md#6-secrets-và-cấu-hình), giá trị để trống hoặc mẫu: `FIREBASE_PROJECT_ID=`, `FIREBASE_SERVICE_ACCOUNT=`, `FIRESTORE_EMULATOR_HOST=host.docker.internal:8080`, `FIREBASE_AUTH_EMULATOR_HOST=host.docker.internal:9099`, `LLM_PROVIDER=gemini`, `LLM_MODEL=`, `GEMINI_API_KEY=`, `GROQ_API_KEY=`, `GROQ_MODEL=openai/gpt-oss-20b`, `AI_ENV=local`. Nếu đã có file `.env.example` của 010 thì bổ sung, không tạo trùng
- [ ] T004 [P] [ĐỘC LẬP] Thêm vào `supabase/config.toml`, ngay sau khối `[functions.on-signup]`: `[functions.ai-search-parse]` với `verify_jwt = false` (function tự xác minh Firebase ID token, giống `on-signup`)
- [ ] T005 [P] [ĐỘC LẬP] Tạo `RES/values/strings_ai.xml` với mọi khóa ở [contract app §6](contracts/app-ai-search.md#6-chuỗi-valuesstrings_aixml-tiền-tố-ai_) (tiền tố `ai_`), đúng nội dung tiếng Việt trong bảng; `ai_usage_remaining` là `plurals`; `ai_examples` là `string-array` 3 câu; thêm các khóa `contentDescription` ở contract §7 (`ai_chip_*_cd`, `ai_voice_start_cd`, `ai_voice_stop_cd`, `ai_voice_denied_cd`). Chưa tạo `strings_ai_policy.xml` (T079)
- [ ] T006 [P] [ĐỘC LẬP] Tạo drawable vector, màu từ theme attribute (chạy đúng sáng/tối): `RES/drawable/ic_ai_sparkle.xml` (biểu tượng lấp lánh như thẻ AI ở wireframe 020 bước 1), `RES/drawable/ic_mic.xml`, `RES/drawable/ic_mic_off.xml`
- [ ] T007 [P] [ĐỘC LẬP] Sửa `app/src/main/AndroidManifest.xml`: thêm `<uses-permission android:name="android.permission.RECORD_AUDIO"/>` và `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>` ([research R15](research.md#r15-màn-hình-khách-giọng-nói-và-trợ-năng)). Không thêm thư viện nào vào `gradle/libs.versions.toml` (plan: không có thư viện mới)

**Checkpoint**: `./gradlew assembleDebug` qua; `supabase functions serve` nhận cấu hình mới.

---

## Phase 2: Foundational (chặn mọi user story)

**Purpose**: server hoàn chỉnh cho cả 6 story, Rules, bộ câu mẫu, và nền phía app không phụ thuộc màn hình 020.

**⚠️ CRITICAL**: phần app của mọi user story cần 2F; phần server của mọi story nằm trong 2C.

### 2A. `_shared/firestore.ts`: transaction và query (PR riêng, cần B review)

- [ ] T008 [ĐỘC LẬP] Viết `FN/_shared/firestore.test.ts` (Deno test, Firestore Emulator, dùng `clearEmulators` của `FN/_shared/testing.ts`), phải fail trước T009:
  - `runTransaction` đọc rồi ghi; hai `runTransaction` song song tăng cùng một bộ đếm 10 lần mỗi bên → kết quả đúng 20 (có `ABORTED` và thử lại);
  - `batchGet` trả `null` cho document không có, đúng thứ tự `paths`;
  - `commit(writes, tx)` ngoài transaction vẫn chạy như cũ (không làm hỏng `on-signup`);
  - `rollback` sau `beginTransaction`;
  - `runQuery("districts", [isActive == true])`;
  - `setWrite` tạo mới và ghi đè; `deleteWrite`.
- [ ] T009 [ĐỘC LẬP] Bổ sung `FN/_shared/firestore.ts` đúng chữ ký ở [contract §3.2](contracts/edge-function-ai-search-parse.md#32-bổ-sung-_sharedfirestorets): `beginTransaction`, `batchGet(paths, transaction?)`, `commit(writes, transaction?)` (thêm tham số tùy chọn, không đổi hành vi cũ), `rollback`, `runTransaction(fn, maxAttempts = 5)` (thử lại khi `FirestoreError.code == "ABORTED"`, có lùi thời gian ngắn), `runQuery(collection, where, limit?)`, `setWrite`, `deleteWrite`. Giữ nguyên các hàm cũ và ghi chú `TODO(B)` ở đầu file; thêm dòng ghi chú chữ ký đã thống nhất với B. T008 xanh; `deno test FN/on-signup/` vẫn xanh. **[BLOCKED: B]** review chữ ký trước khi merge (plan, mục Phối hợp). Phương án tạm: nếu B chưa review, giữ PR ở draft nhưng vẫn tiếp tục 2C trên nhánh này
- [ ] T010 [P] [ĐỘC LẬP] Tạo `TD/pii-vectors.json`: mảng `{ "input", "masked" }` với ít nhất 24 ca ([research R10](research.md#r10-che-pii-fr-004-sc-007)):
  - **phải che**: email thường và có `+`/`.`; `0912345678`, `0912 345 678`, `0912.345.678`, `0912-345-678`, `+84 912 345 678`, `+84912345678`, `84912345678`, `0084912345678`, số bàn `028 3823 4567`, `02838234567`; dãy ≥ 9 chữ số sai định dạng `123456789`;
  - **không được che**: `100k`, `100.000`, `1 trăm`, `19:00`, `7h30`, `7h-9h`, `06/10`, `06/10/2026`, `q10`, `quận 10`, `dưới 120000`;
  - ca trộn: "tối mai q10 dưới 100k, sđt 0912 345 678" → "tối mai q10 dưới 100k, sđt [sđt]".
  
  Ký hiệu che: `[email]`, `[sđt]`. Dùng chung cho `AIFN/pii.test.ts` (T016) và `TEST/feature/search/domain/ai/PiiMaskerTest.kt` (T050)

### 2B. Security Rules

- [ ] T011 [P] [ĐỘC LẬP] Viết `FT/ai.test.ts` (Vitest + `@firebase/rules-unit-testing`, cùng `FT/setup.ts`) theo bảng AI-1…AI-8 ở [contracts/rules-and-tests.md §2](contracts/rules-and-tests.md#2-test-rules-emulator). Phải fail trước T012
- [ ] T012 [ĐỘC LẬP] Thêm vào `firestore.rules` đúng [contract §1](contracts/rules-and-tests.md#1-rules): `match /aiUsage/{day}` **bên trong** `match /users/{uid}` (`allow read: if isSelf(uid) || isAdmin(); allow write: if false;`), `match /aiSearchCache/{hash}` (`allow read, write: if false;`), `match /aiMetrics/{id}` (`allow read: if isAdmin(); allow write: if false;`). Dùng hàm `isSelf`, `isAdmin` đã có. T011 xanh; `npm test` trong `firestore-tests` không làm đỏ test cũ

### 2C. Edge Function `ai-search-parse` (phục vụ mọi story)

Hàm thuần trước (test trước), rồi LLM, rồi handler. Cấu trúc file theo [contract §3.1](contracts/edge-function-ai-search-parse.md#31-cấu-trúc-file). Hằng số đặt ở `AIFN/constants.ts`: `DAILY_LIMIT = 10`, `OFF_TOPIC_LIMIT = 3`, `LOCK_MINUTES = 5`, `MAX_QUERY = 200`, `LLM_TIMEOUT_MS = 6000`, `FALLBACK_TIMEOUT_MS = 3000`, `MAX_OUTPUT_TOKENS = 200`, `ADVANCE_DAYS = 30`, `PRICE_CEILING = 2_000_000`, `CATALOG_TTL_MS = 600_000`.

- [ ] T013 [P] [ĐỘC LẬP] Viết `AIFN/query.test.ts`: `validateQuery` (thiếu, không phải chuỗi, rỗng, chỉ khoảng trắng → lỗi; đúng 200 code point qua; 201 lỗi; emoji đếm 1); `normalize` (NFC, chữ thường, gộp khoảng trắng, `trim`, **không** bỏ dấu: "Tối  MAI" = "tối mai", "toi mai" ≠ "tối mai"); `sha256Hex` vector cố định; `cacheKey(text, "20261005")` ≠ `cacheKey(text, "20261006")`; `vnDay(now)` đổi ngày đúng lúc 00:00 giờ Việt Nam (17:00 UTC)
- [ ] T014 [ĐỘC LẬP] Cài đặt `AIFN/query.ts` và `AIFN/constants.ts` (SHA-256 bằng `crypto.subtle`). T013 xanh
- [ ] T015 [P] [ĐỘC LẬP] Viết `AIFN/pii.test.ts` đọc `TD/pii-vectors.json` (đường dẫn tương đối từ file test); mỗi ca `maskPii(input) == masked`; thêm hàm `containsPii(text)` cho metrics
- [ ] T016 [ĐỘC LẬP] Cài đặt `AIFN/pii.ts` theo luật ở [research R10](research.md#r10-che-pii-fr-004-sc-007) (email → `[email]`, số VN → `[sđt]`, dãy ≥ 9 chữ số → `[sđt]`, theo thứ tự đó). T015 xanh
- [ ] T017 [P] [ĐỘC LẬP] Viết `AIFN/normalize.test.ts` với `today` = Thứ Hai 2026-10-05, `now` = 10:00 (Asia/Ho_Chi_Minh) trừ khi ghi khác: **mọi dòng** của bảng §4.1 (ngày) và §4.2 (giờ, cả 11 vector) ở [llm-prompt-and-schema.md](contracts/llm-prompt-and-schema.md#4-quy-tắc-chuẩn-hóa-tất-định); §4.3: bí danh quận (`q10`, `quan 10`, `quan muoi`, `bt` → `binh-thanh`, `tb` → `tan-binh`) khớp `scripts/seed/catalog.json`; tiện ích sai id khớp theo tên bỏ dấu; `venueName` không có trong câu → bỏ; bỏ tiền tố "sân"; `NEAREST` + `districtId` mà câu không có "gần tôi/gần mình/gần đây/gần nhất/vị trí" → bỏ `sort`; `level` chỉ giữ khi `DROP_IN`; `type` null → `COURT`
- [ ] T018 [ĐỘC LẬP] Cài đặt `AIFN/normalize.ts`: `resolveDay`, `resolveTime` (đổi 12/24 giờ theo bảng, mặc định buổi tối cho 1–9 giờ không có buổi, làm tròn `start` xuống, `end` lên mốc 30 phút, chỉ có `start` thì `end = start + 2h`, chỉ có buổi thì mốc 05–11/11–13/13–18/18–22, hôm nay đã vào buổi thì cắt lên mốc 30 phút kế tiếp), `resolveDistrict`, `resolveAmenities`, `checkVenueName`, `checkSort`; bảng `BIDANH_QUAN` cho 24 quận của `catalog.json`; dùng chung một hàm `stripDiacritics` (đ → d). T017 xanh
- [ ] T019 [P] [ĐỘC LẬP] Viết `AIFN/validate.test.ts` theo [llm-prompt-and-schema.md §3, §4.3, §4.4](contracts/llm-prompt-and-schema.md#3-kiểm-tra-schema-ở-server-validatets-bước-12-của-r5): JSON hỏng → `unclear`, `SCHEMA_INVALID`, `schemaValid = false`; `intent` lạ → như trên; trường lạ (`"sql": "..."`) bị bỏ, `dropped` có `UNKNOWN_FIELD`, metrics có `UNKNOWN_FIELD:sql`; trường sai kiểu → `null` + `SCHEMA_INVALID`; `intent != search` → `filter` rỗng (giữ `faqTopic` khi `faq`); giá `≤ 0`, `> 2_000_000` hoặc `min > max` → bỏ cả khoảng, `INVALID_PRICE`; ngày quá khứ → `PAST_DATE`; > 30 ngày → `DATE_OUT_OF_RANGE`; `search` không còn điều kiện nào → hạ `unclear`, `searchValid = false`; `faqTopic` sai → `null`; response chỉ có các khóa ở [contract §2.1](contracts/edge-function-ai-search-parse.md#21-200-ok)
- [ ] T020 [ĐỘC LẬP] Cài đặt `AIFN/schema.ts` (JSON Schema ở [§2](contracts/llm-prompt-and-schema.md#2-json-schema-đầu-ra), mọi trường trong `required`, `additionalProperties: false`, kiểu TS tương ứng) và `AIFN/validate.ts` (`validateAndNormalize(raw, maskedQuery, now, catalog)` trả `{ result, schemaValid, searchValid, llmIntent, droppedForMetrics }`, gọi `normalize.ts`). Tự viết kiểm tra kiểu, không thêm `ajv`. T019 xanh
- [ ] T021 [P] [ĐỘC LẬP] Viết `AIFN/usage.test.ts` cho `reserve`, `refund`, `applyIntent`, `isLocked` ([contract §4](contracts/edge-function-ai-search-parse.md#4-quy-tắc-lượt-và-khóa), bảng chuyển trạng thái ở [data-model §2.1](data-model.md#21-usersuidaiusageyyyymmdd-)): document chưa có → `count = 1`; `count = 9` → 10; `count = 10` → `QUOTA_EXCEEDED`; `lockedUntil > now` → `LOCKED` (trước cả kiểm tra lượt); `refund` không xuống dưới 0; `off_topic` × 3 → `offTopicStreak = 0`, `lockedUntil = now + 5'`; `search` hợp lệ → `offTopicStreak = 0`; `faq`/`unclear` → không đổi; khóa ở document hôm qua lúc 23:58 vẫn chặn lúc 00:01 hôm nay, không chặn lúc 00:04; `expiresAt` = 00:00 ngày thứ 8 sau ngày của document
- [ ] T022 [ĐỘC LẬP] Cài đặt `AIFN/usage.ts` (thuần logic, không gọi Firestore). Trường `count` "Số lượt đã giữ trong ngày, 0–10", `offTopicStreak` "0–2", `lockedUntil` "Timestamp?", `updatedAt`, `expiresAt` đúng data-model §2.1. T021 xanh
- [ ] T023 [ĐỘC LẬP] Viết `AIFN/prompt.test.ts` rồi cài đặt `AIFN/prompt.ts`: `PROMPT_VERSION = "2026-10-28.1"`; `buildPrompt(maskedQuery, now, catalog)` trả `{ system, user }` theo [§1](contracts/llm-prompt-and-schema.md#1-system-prompt) (đủ 9 ý đánh số, `today`/thứ/`nowHHmm` theo giờ Việt Nam **lấy từ `now` của server**, danh mục dạng `id=tên`, 8–10 ví dụ ở §1); câu nằm trong `<user_query>…</user_query>`. Test: không có uid/email/tên trong `system`/`user`; có đủ id danh mục; ví dụ few-shot không trùng câu nào trong `EVAL/cases.json` (kiểm tra lại sau T040)
- [ ] T024 [P] [ĐỘC LẬP] Cài đặt `AIFN/llm/client.ts` (`LlmClient`, `LlmInput { system, user, schema, signal }`, `LlmOutput { json, inputTokens, outputTokens, provider, model, latencyMs }`, `LlmError` với `kind: TIMEOUT | QUOTA | UNAVAILABLE`) và `AIFN/llm/fake.ts` (`FakeLlmClient`: map câu → JSON, hoặc ném `LlmError` theo cấu hình; ghi lại mọi `LlmInput` để test kiểm tra PII)
- [ ] T025 [P] [ĐỘC LẬP] Viết `AIFN/llm/gemini.test.ts` (thay `fetch` bằng hàm giả) rồi cài đặt `AIFN/llm/gemini.ts`: `POST .../models/{LLM_MODEL}:generateContent` với `systemInstruction`, `generationConfig { responseMimeType: "application/json", responseJsonSchema, temperature: 0, maxOutputTokens: 200 }`, khóa ở header `x-goog-api-key` (không để trong URL, tránh lộ qua log); đọc `usageMetadata` ra token; `AbortSignal` hết giờ → `TIMEOUT`; HTTP 429 → `QUOTA`; 5xx, lỗi mạng → `UNAVAILABLE`; body sai → ném để handler coi là `SCHEMA_INVALID`
- [ ] T026 [P] [ĐỘC LẬP] Viết `AIFN/llm/groq.test.ts` rồi cài đặt `AIFN/llm/groq.ts`: `POST https://api.groq.com/openai/v1/chat/completions`, `model = GROQ_MODEL`, `response_format { type: "json_schema", json_schema { name: "court_search", strict: true, schema } }`, `temperature: 0`, `max_completion_tokens: 200`; token từ `usage`; ánh xạ lỗi như T025
- [ ] T027 [ĐỘC LẬP] Viết `AIFN/llm/failover.test.ts` rồi cài đặt `AIFN/llm/failover.ts`: gọi nhà cung cấp chính (`LLM_PROVIDER`, mặc định `gemini`) với 6 giây; `QUOTA`/`UNAVAILABLE` trả ngay → gọi dự phòng với `min(3000, thời gian còn lại)` nếu có `GROQ_API_KEY`; `TIMEOUT` → không chuyển; trả `fallbackUsed`. Test E18
- [ ] T028 [P] [ĐỘC LẬP] Cài đặt `AIFN/catalog.ts` + `AIFN/catalog.test.ts` (Emulator): `runQuery` `districts`, `amenities` với `isActive == true`, cache trong biến module 10 phút (`CATALOG_TTL_MS`), có hàm `__resetForTesting`
- [ ] T029 [P] [ĐỘC LẬP] Cài đặt `AIFN/cache.ts` (`readCache(key, now)` bỏ qua khi `expiresAt <= now` hoặc `promptVersion` khác; `cacheWrite(key, result, intent, searchValid, now)` trả `Write` với các trường ở [data-model §2.2](data-model.md#22-aisearchcachehash-), `expiresAt` = 00:00 ngày hôm sau giờ Việt Nam, **không** có câu, uid) và `AIFN/metrics.ts` (`metricsWrite(...)` trả `Write` cho `aiMetrics/{autoId}` đúng các trường ở [data-model §2.3](data-model.md#23-aimetricsautoid-mới), `expiresAt = createdAt + 90 ngày`, `env` từ `AI_ENV`; đồng thời `console.log(JSON.stringify(...))` cùng nội dung). Test: không có khóa hay giá trị nào chứa uid, câu, hash (E16)
- [ ] T030 [ĐỘC LẬP] Viết `AIFN/handler.test.ts` (Firebase Emulator + `FakeLlmClient`, `clock` giả, token bằng `signUpEmail`/`emulatorToken` của `FN/_shared/testing.ts`, seed danh mục từ `scripts/seed/catalog.json`) cho **E1–E18** ở [contract §5](contracts/edge-function-ai-search-parse.md#5-bảo-đảm-cần-test). E4, E5 chứng minh SC-005 và SC-008. E5: đặt `count = 5`, gửi 10 yêu cầu khác nhau bằng `Promise.all` → đúng 5 `PARSED`, 5 `QUOTA_EXCEEDED`, `FakeLlmClient` được gọi đúng 5 lần, `count == 10`. E13: câu "tối mai q10, mail a.b@x.com sđt 0912 345 678" → không `LlmInput` nào chứa `a.b@x.com`, `0912`, `345 678`; `aiMetrics` cũng vậy. Phải fail trước T031
- [ ] T031 [ĐỘC LẬP] Cài đặt `AIFN/handler.ts` theo luồng ở [contract §3](contracts/edge-function-ai-search-parse.md#3-luồng-xử-lý) (`deps`: `firestore`, `llm`, `catalog`, `clock`, `env`, `auth` mặc định `requireUser`) và `AIFN/index.ts` mỏng (`Deno.serve`). `warmup: true` → `204` trước khi xác thực. Hết lượt/khóa trả **200** với `outcome` (không phải 429, vì `FunctionErrors` của app gộp mọi 429). Lỗi LLM → hoàn lượt trong transaction rồi trả 502/504; ghi hoàn lượt lỗi → `aiMetrics.refundFailed = true`. Commit cuối gộp streak + `aiSearchCache` + `aiMetrics` trong cùng transaction T2. T030 xanh
- [ ] T032 [ĐỘC LẬP] Chạy toàn bộ `deno test --allow-env --allow-net --allow-read FN/`, `deno lint`, `deno fmt --check` cho `AIFN/` và `FN/_shared/`. Chạy thử local: `supabase functions serve ai-search-parse --env-file supabase/functions/.env` + Emulator, gọi bằng `curl` với token Emulator: một câu tìm sân, một câu lạc đề, `warmup`. Ghi lệnh vào `quickstart.md` §0 nếu khác

### 2D. Bộ câu mẫu (FR-024), làm trước màn hình để sửa prompt sớm

- [ ] T033 [P] [ĐỘC LẬP] Tạo `EVAL/cases.json` đúng định dạng [eval-suite §2](contracts/eval-suite.md#2-định-dạng-casesjson), `today` = `2026-10-05T10:00:00+07:00`: **25 câu tìm sân** và **15 câu ngoại lệ**, đủ từng nhóm ở [§3](contracts/eval-suite.md#3-nội-dung-tối-thiểu). Đáp án `expect.filter` tính tay theo bảng chuẩn hóa §4 (trường không ghi = phải trống). Không câu nào trùng ví dụ few-shot trong `AIFN/prompt.ts`. Câu chứa số điện thoại/email có `mustNotContain`
- [ ] T034 [ĐỘC LẬP] Cài đặt `EVAL/run-eval.ts` (Deno): import `handleAiSearchParse` từ `AIFN/handler.ts`; `deps.firestore` = bản giả trong bộ nhớ (cài `get`, `batchGet`, `runTransaction`, `commit`, `runQuery` trên `Map`); `deps.catalog` đọc `scripts/seed/catalog.json`; `deps.clock` theo `today`/`now` của từng câu; `deps.auth` trả một uid giả **mới cho mỗi câu** (không bao giờ chạm giới hạn 10 lượt; `index.ts` không đổi, vẫn luôn `requireUser`); `deps.llm` = `failover.ts` thật bọc thêm bộ ghi `LlmInput`. Câu 201 ký tự phải ra `400 INVALID_QUERY`. Tính các chỉ số ở [eval-suite §4](contracts/eval-suite.md#4-chấm-điểm-và-báo-cáo), in bảng câu sai kèm trường lệch, ghi `docs/reports/ai-eval/<yyyyMMdd>-<PROMPT_VERSION>.md` và `.json`. Tham số `--cases`, `--out`, `--only <id>`
- [ ] T035 [ĐỘC LẬP] Chạy `EVAL/run-eval.ts` với Gemini thật. **Mục tiêu**: nhóm tìm sân đúng ý định ≥ 95% và đúng toàn bộ điều kiện ≥ 85% (SC-001); nhóm ngoại lệ đúng nhánh 100%, 0 văn bản tự do (SC-002); 0 PII lọt (SC-007). Chưa đạt thì sửa system prompt / ví dụ / bảng bí danh trong `AIFN/prompt.ts`, `AIFN/normalize.ts`, tăng `PROMPT_VERSION`, chạy lại. **Không** sửa `cases.json` để cho đạt (chỉ sửa khi đáp án tính sai, ghi lý do trong báo cáo). Lưu mọi lần chạy trong `docs/reports/ai-eval/`. Chạy thêm một lần với `LLM_PROVIDER=groq` để biết chất lượng dự phòng
- [ ] T036 [P] [ĐỘC LẬP] Cài đặt `EVAL/export-metrics.mjs` (Node 20 + `firebase-admin`, Application Default Credentials) theo [eval-suite §5](contracts/eval-suite.md#5-xuất-số-liệu-thật-export-metricsmjs): đọc `aiMetrics` theo `day` trong `[--from, --to]`, xuất CSV và bảng tóm tắt Markdown vào `--out`. Có `--emulator` để thử với Firestore Emulator. Dùng `package.json` riêng trong `EVAL/` hoặc chung `scripts/seed/package.json` (đã có `firebase-admin`), không thêm phụ thuộc mới vào app

### 2E. Sửa tài liệu 020 (A tự làm, PR tài liệu riêng)

- [ ] T037 [ĐỘC LẬP] Sửa tài liệu 020 theo [research R1](research.md#r1-thứ-tự-làm-và-phụ-thuộc-vào-020-021-080), không đổi code:
  - [contracts/search-criteria.md](../020-court-search/contracts/search-criteria.md) §3: `suspend fun applyExternal(criteria, source): SanitizeResult`, không tự hiện snackbar khi `source == AI`; §4: thêm `countVariants(variants: List<SearchCriteria>, limit: Int = 20): List<Int>` (một query ứng viên rộng nhất, một lượt `slots` cho khung hợp, `VenueStaticCache`, chạy `VenueMatcher` + `AvailabilityEvaluator` từng biến thể) ([R13](research.md#r13-gợi-ý-nới-điều-kiện-fr-022));
  - [tasks 020](../020-court-search/tasks.md): **T030** `nav_search.xml` thêm `aiAssistantFragment` (argument `startVoice: Boolean = false`, `prefill: String? = null`) và dialog `aiPolicyBottomSheet` (argument `topic`); **T051** micro trong ô tìm kiếm là lối vào 022 (ẩn với khách và khi máy không hỗ trợ); **T052** trạng thái rỗng chừa vùng `@+id/ai_relaxations_container`; **T088** đổi kiểu trả `applyExternal`; thêm task mới sau **T065** cho `countVariants` + test.
  
  Ghi "do 022 yêu cầu, xem plan 022" ở mỗi chỗ sửa

### 2F. Nền phía app

- [ ] T038 [P] [ĐỘC LẬP] Viết `TEST/feature/search/domain/ai/PiiMaskerTest.kt`: đọc `File("../scripts/testdata/ai/pii-vectors.json")` (Gradle chạy unit test với thư mục làm việc `app/`), parse bằng kotlinx-serialization, mỗi ca `PiiMasker.mask(input) == masked`. Phải fail trước T039
- [ ] T039 [ĐỘC LẬP] Cài đặt `SEARCH/domain/ai/PiiMasker.kt` (`object`, cùng luật và thứ tự với `AIFN/pii.ts`). T038 xanh
- [ ] T040 [P] [ĐỘC LẬP] Viết `TEST/feature/search/domain/ai/AiQueryInputTest.kt` rồi cài đặt `SEARCH/domain/ai/AiQueryInput.kt`: `validate(text): InputState` (`EMPTY` khi rỗng/chỉ khoảng trắng, `TOO_LONG` khi > 200 **code point** sau `trim`, `OK`); `count(text)` cho bộ đếm (emoji đếm 1)
- [ ] T041 Tạo model trong `SEARCH/domain/ai/model/` theo [data-model §3.1](data-model.md#31-domainaimodel): `AiIntent`, `FaqTopic`, `SearchType`, `PartOfDay` (mốc `MORNING(05:00, 11:00)`, `NOON(11:00, 13:00)`, `AFTERNOON(13:00, 18:00)`, `EVENING(18:00, 22:00)`), `DropInLevel`, `ParsedFilter`, `AiParseResult`, `AiDropReason` (`UNKNOWN_DISTRICT`, `UNKNOWN_AMENITY`, `PAST_DATE`, `DATE_OUT_OF_RANGE`, `INVALID_TIME`, `INVALID_PRICE`, `UNKNOWN_FIELD`, `KEYWORD_NOT_IN_QUERY`, `SCHEMA_INVALID`), `AiUsage` (`used`, `limit`, `lockedUntil: Instant?`, `remaining`), `AiSubmitOutcome` (`Parsed`, `Locked`, `QuotaExceeded`), `VoiceEvent`, `AiUnavailable` (lớp lỗi riêng, **không** sửa `CORE/network/AppError.kt`). **Phụ thuộc: 020 T011** (`TimeOfDay`), **020 T026** (`SortOption`); không tạo bản tạm của hai kiểu này
- [ ] T042 [P] Tạo interface `SEARCH/domain/ai/AiSearchApi.kt`, `AiUsageRepository.kt`, `VoiceInput.kt` đúng [contract app §1](contracts/app-ai-search.md#1-data). **Phụ thuộc: T041**
- [ ] T043 [P] Viết `TEST/feature/search/data/ai/SupabaseAiSearchApiTest.kt` (MockK `SupabaseClient`/`FunctionErrors` hoặc tách `AiSearchHttp` để giả): parse đủ 3 `outcome`; `filter` đầy đủ; enum lạ → `UNCLEAR`; HTTP 502/504 → `AiUnavailable`; quá 10 giây → `AppError.Timeout`; 403 `ACCOUNT_LOCKED` → `AppError.Locked` (qua `FunctionErrors`); `warmUp` nuốt mọi lỗi. Phải fail trước T044
- [ ] T044 Cài đặt `SEARCH/data/ai/dto/AiSearchDto.kt` (`@Serializable`, `ignoreUnknownKeys`) và `SEARCH/data/ai/SupabaseAiSearchApi.kt` theo mẫu `feature/auth/data/OnSignupApi.kt`: `functionErrors.invokeWithAuthRetry { withTimeout(AI_TIMEOUT_MS = 10_000) { supabase.functions.invoke("ai-search-parse", body) } }`. T043 xanh
- [ ] T045 [P] Viết `TEST/feature/search/data/ai/AiUsageDayTest.kt` rồi cài đặt `SEARCH/data/ai/AiUsageDay.kt` (`yyyyMMdd` theo `Asia/Ho_Chi_Minh` từ `AppClock`, thời điểm 00:00 kế tiếp) và `SEARCH/data/ai/FirestoreAiUsageRepository.kt`: `callbackFlow` trên `users/{uid}/aiUsage/{day}` (uid qua `SessionManager`), `awaitClose { remove() }`, đổi document khi qua 00:00 (`flatMapLatest` trên luồng ngày), document chưa có → `AiUsage(used = 0)`, khách → flow rỗng
- [ ] T046 Tạo `SEARCH/di/AiSearchModule.kt`: bind `AiSearchApi`, `AiUsageRepository`; `VoiceInput` bind ở T090. Chạy `./gradlew testDebugUnitTest`

### Theo dõi việc của người khác

- [ ] T047 [P] Theo dõi (**B**, spec 050/060): văn bản chính sách 4 chủ đề (hủy sân, đặt cọc, voucher, thanh toán/ví giả lập), mỗi chủ đề 3–5 câu, khớp hằng số 050/060 (mốc 4 giờ, phạt 30%, cọc 30% làm tròn nghìn đồng); trang "chính sách đầy đủ" để liên kết. **Phương án tạm:** chuỗi tạm có `TODO(B)` ở T079, liên kết tới `LegalDocumentFragment` của 010. Cũng nhắc B review chữ ký T009
- [ ] T048 [P] Theo dõi (**C**, spec 080): deep link `smashnow://dropin/list?districtIds={csv}&date={yyyyMMdd}&timeSlot=&level=&maxPrice=` trong `nav_dropin.xml`, `DropInListFragment` đổi sang `DropInFilterArgs` (080 tasks T154); sửa câu chữ 080 FR-003 về mốc "Tối" (18–23 theo giờ bắt đầu buổi); `maxPrice` là giá mỗi lượt. **Phương án tạm:** T062 kiểm tra `hasDeepLink` và rơi về tìm sân thường
- [ ] T049 [P] Theo dõi (**D**, spec 111/112/120): giá/giờ lớn nhất chủ sân nhập được và giá/lượt lớn nhất của buổi vãng lai; danh mục ẩn bằng `isActive = false`, giữ ID slug. **Phương án tạm:** giữ `PRICE_CEILING = 2_000_000`; có số của D thì sửa `AIFN/constants.ts` và test T019

**Checkpoint**: `deno test FN/` xanh, `npm test` trong `firestore-tests` xanh, báo cáo eval đạt mục tiêu (hoặc ghi rõ còn thiếu bao nhiêu), `./gradlew testDebugUnitTest` xanh. Server dùng được cho mọi story.

---

## Phase 3: User Story 1 - Tìm sân bằng một câu tự nhiên (Priority: P1) 🎯 MVP

**Goal**: người chơi đã đăng nhập gõ câu, chip bộ lọc 020 hiện đúng, danh sách chạy như khi tự chọn; câu vãng lai mở danh sách 080.

**Independent Test**: với dữ liệu seed, gửi "tối mai 7h-9h sân Quận 10 dưới 100k có gửi xe" và "q10 duoi 100k co gui xe toi mai" → chip đúng từng giá trị, danh sách giống hệt khi tự chọn bộ lọc đó (quickstart K2–K8, K20).

**Phụ thuộc chung:** **020 Phase 2** (T011, T015, T026, T030), **020 US1** (T051–T052 `ai_entry_container`), **020 US4** (T084 sanitizer, T088 `applyExternal`).

### Tests for User Story 1 ⚠️

- [ ] T050 [P] [US1] Viết `TEST/feature/search/domain/ai/AiFilterMapperTest.kt`: `ParsedFilter` → `SearchCriteria` theo bảng [contract app §3](contracts/app-ai-search.md#3-aifiltermapper) (keyword thay toàn bộ; `date`+`start`+`end` → `timeWindow`; `sort` null → `RATING`; `level`, `partOfDay`, `dateChoices` bị bỏ); `DROP_IN` → URI `smashnow://dropin/list?...` (bỏ tham số null; `timeSlot` = `partOfDay` hoặc buổi chứa `start` theo mốc **080**: 05/11/13/18/23, ví dụ 22:30 → `EVENING`; `priceMax` → `maxPrice`); trả danh sách điều kiện bị bỏ cho vãng lai (`keyword`, `amenityIds`, `priceMin`, `sort`). **Phụ thuộc: T041, 020 T026**
- [ ] T051 [P] [US1] Viết `TEST/feature/search/domain/ai/usecase/SubmitAiQueryTest.kt` và `ResolveAiResultTest.kt` (API giả): `EMPTY`/`TOO_LONG` không gọi API; câu được che trước khi gọi API (API giả nhận `[sđt]`); `SEARCH` đủ điều kiện → `ApplyCourt`; `SEARCH` + `DROP_IN` → `OpenDropIn`
- [ ] T052 [P] [US1] Viết `TEST/feature/search/ui/ai/AiAssistantViewModelTest.kt` phần US1 (Turbine, `SavedStateHandle`, API giả): gửi → `sending = true` rồi sự kiện `ApplyCourt(criteria)`; `OpenDropIn(uri)`; câu trong `input` giữ trong `SavedStateHandle` và khôi phục được
- [ ] T053 [P] [US1] Bổ sung `TEST/feature/search/ui/SearchViewModelTest.kt` (của 020): `applyExternal(criteria, AI)` trả `SanitizeResult`, thay toàn bộ `criteria`, chạy tìm kiếm, ghi lịch sử, **không** phát snackbar khi `source == AI` (vẫn phát với `HISTORY`). **Phụ thuộc: 020 T088**

### Implementation for User Story 1

- [ ] T054 [P] [US1] Cài đặt `CORE/navigation/DropInLink.kt`: `build(districtIds, date, timeSlot, level, maxPrice): Uri` (chuỗi `timeSlot`/`level` là tên enum; không import `feature/dropin`). Test nằm trong T050
- [ ] T055 [US1] Cài đặt `SEARCH/domain/ai/AiFilterMapper.kt`. T050 xanh
- [ ] T056 [US1] Cài đặt `SEARCH/domain/ai/usecase/SubmitAiQuery.kt`, `ResolveAiResult.kt` (nhánh `ApplyCourt`, `OpenDropIn`; các nhánh khác để US2/US3 bổ sung nhưng phải có kiểu kết quả: `Clarify`, `Policy`, `OffTopic`). T051 xanh
- [ ] T057 [US1] (Sửa 020) Hai chỗ trong code 020: (1) `SEARCH/ui/SearchViewModel.kt`: `applyExternal` trả `SanitizeResult`, bỏ snackbar khi `source == AI`, T053 xanh (**Phụ thuộc: 020 T088**; nếu 020 T088 chưa làm thì làm luôn trong T088 theo contract đã sửa ở T037); (2) `RES/navigation/nav_search.xml` thêm destination `aiAssistantFragment` (argument `startVoice: Boolean = false`, `prefill: String? = null`) và dialog `aiPolicyBottomSheet` (argument `topic: string?`, tên enum `FaqTopic`) (**Phụ thuộc: 020 T030**)
- [ ] T058 [US1] Cài đặt `SEARCH/ui/ai/AiAssistantUiState.kt` theo [data-model §3.3](data-model.md#33-trạng-thái-màn-hình) và `SEARCH/ui/ai/AiAssistantViewModel.kt` phần US1: `onInputChanged`, `onSubmit` (một lần gửi, `Channel` sự kiện `ApplyCourt`, `OpenDropIn`), giữ `input` trong `SavedStateHandle`, `prefill` từ argument. T052 xanh
- [ ] T059 [US1] Tạo `RES/layout/fragment_ai_assistant.xml`: `TextInputLayout` nhiều dòng (`counterEnabled`, `counterMaxLength = 200`, hint `ai_input_hint`, IME "Tìm"), nút gửi, `ChipGroup` 3 câu ví dụ (`ai_examples`), chỗ cho micro (US4), dòng lượt còn lại (US5), vùng kết quả (`FrameLayout` cho hỏi lại / lạc đề / thông báo). `ConstraintLayout`, màu theo theme, vùng chạm 48dp
- [ ] T060 [US1] Cài đặt `SEARCH/ui/ai/AiAssistantFragment.kt`: `viewModels()` cho `AiAssistantViewModel`, `hiltNavGraphViewModels(R.id.nav_search)` cho `SearchViewModel`; thu `uiState` bằng `repeatOnLifecycle(STARTED)`; `ApplyCourt` → `searchViewModel.applyExternal(criteria, CriteriaSource.AI)` rồi `findNavController().popBackStack()` về `SearchFragment`; chip ví dụ điền vào ô (không tự gửi)
- [ ] T061 [US1] Bổ sung `AiAssistantFragment` nhánh vãng lai: `OpenDropIn(uri, dropped)` → nếu `findNavController().graph.hasDeepLink(uri)` thì `navigate(uri)` và hiện `ai_dropin_dropped` khi `dropped` không rỗng; nếu không thì hiện `ai_dropin_unavailable` và áp `SearchCriteria` tương ứng (đổi `type` thành `COURT`) như nhánh sân thường. **[BLOCKED: C]** cho đường chính (T048); phương án tạm chạy được ngay
- [ ] T062 [US1] Cài đặt `SEARCH/ui/ai/AiEntryCardViewModel.kt` (phần đã đăng nhập: hiện ví dụ) và `SEARCH/ui/ai/AiEntryCardFragment.kt` + `RES/layout/fragment_ai_entry_card.xml` (thẻ "Tìm bằng AI: <ví dụ>" như wireframe 020 bước 1, `ic_ai_sparkle`, chỗ cho "Còn X/10" bên phải); chạm → `aiAssistantFragment`. Gắn vào `ai_entry_container` trong `SEARCH/ui/SearchFragment.kt` (chỉ hiện ở chế độ trang chủ). **Phụ thuộc: 020 T051, T052**
- [ ] T063 [US1] Chạy quickstart K1–K8 và K20 (đường tạm nếu chưa có 080) trên Emulator với seed và function local

**Checkpoint**: US1 chạy độc lập; chip đúng, danh sách giống bộ lọc thủ công.

---

## Phase 4: User Story 2 - Câu thiếu thông tin hoặc không hiểu được (Priority: P1)

**Goal**: hỏi lại đúng một lần bằng chip, bấm chip điền trên máy không gọi AI; vẫn thiếu hoặc [Tự chọn] thì mở bộ lọc thủ công với phần đã hiểu.

**Independent Test**: gửi "tìm sân Quận 10" → "Bạn muốn chơi lúc nào?" + [Tối nay] [Tối mai] [Cuối tuần] [Tự chọn]; bấm [Tối mai] → chip ngày mai 18–22, lượt không giảm thêm (quickstart K9–K12).

Phần server (hạ `search` → `unclear`, `dateChoices` cho cuối tuần, `dropped`) đã xong ở T017–T020.

### Tests for User Story 2 ⚠️

- [ ] T064 [P] [US2] Viết `TEST/feature/search/domain/ai/ClarifyPlannerTest.kt` (clock giả, hôm nay Thứ Hai 05/10) theo bảng [research R11](research.md#r11-hỏi-lại-một-lần-fr-013-và-chip-chọn-nhanh): `UNCLEAR` hoặc `SEARCH` không ngày, không tên sân → `TIME` với [Tối nay] [Tối mai] [Cuối tuần] [Tự chọn]; [Cuối tuần] → Thứ Bảy 10/10 18:00–22:00; [Tối nay] **ẩn khi đã quá 21:30**; `dateChoices` → `DATE` với [Thứ Bảy 10/10] [Chủ Nhật 11/10] (chip giữ buổi đã hiểu, không có thì tối); có ngày không có giờ → `PART_OF_DAY` [Sáng] [Chiều] [Tối]; **có tên sân mà không có ngày → không hỏi**; `SCHEMA_INVALID` → `GENERIC`
- [ ] T065 [P] [US2] Bổ sung `AiAssistantViewModelTest.kt` phần US2: `Clarify` hiện câu hỏi; bấm chip → `ApplyCourt` và API **không** được gọi thêm; `clarifyUsed` → lần thiếu tiếp theo của cùng câu mở bảng lọc (`OpenManualFilter(criteria)`), không có câu hỏi thứ hai; [Tự chọn] → `OpenManualFilter`; `SanitizeResult.dropped` không rỗng sau khi áp → thông báo theo `AiDropReason`; gửi câu mới thì `clarifyUsed` đặt lại

### Implementation for User Story 2

- [ ] T066 [US2] Cài đặt `SEARCH/domain/ai/model/ClarifyQuestion.kt`, `ClarifyOption.kt` và `SEARCH/domain/ai/ClarifyPlanner.kt` (inject `AppClock`). T064 xanh
- [ ] T067 [US2] Bổ sung `ResolveAiResult.kt` nhánh `Clarify` (gọi `ClarifyPlanner`) và `AiAssistantViewModel.kt` (`onClarifyOption`, `clarifyUsed` trong `SavedStateHandle`, sự kiện `OpenManualFilter(criteria)`). T065 xanh
- [ ] T068 [P] [US2] Cài đặt `SEARCH/ui/ai/AiDropMessages.kt`: `AiDropReason`/`DroppedField` (020) → `@StringRes` (`ai_dropped_district`, `ai_dropped_amenity`, `ai_dropped_time_past`, `ai_dropped_date_range`, `ai_dropped_time`, `ai_dropped_price`); nhiều lý do thì ghép tối đa 2 câu
- [ ] T069 [US2] Tạo `RES/layout/view_ai_clarify.xml` (câu hỏi + `ChipGroup`, chip `contentDescription` đầy đủ) và hiển thị trong vùng kết quả của `AiAssistantFragment`. `OpenManualFilter` → `searchViewModel.applyExternal(criteria, AI)` rồi mở bottom sheet bộ lọc của 020 (`searchFilterSheet`). **Phụ thuộc: 020 T054, T066** (bottom sheet bộ lọc)
- [ ] T070 [US2] Chạy quickstart K9–K12

**Checkpoint**: US1 + US2 chạy; không bao giờ có câu hỏi thứ hai.

---

## Phase 5: User Story 3 - Câu hỏi chính sách và câu lạc đề (Priority: P2)

**Goal**: `faq` hiện văn bản chính sách có sẵn; `off_topic` hiện câu cố định; 3 câu lạc đề liên tiếp thì khóa 5 phút.

**Independent Test**: gửi bộ câu ngoại lệ (quickstart K13–K19): đúng nhánh, không văn bản do AI tự viết; câu lạc đề thứ 3 khóa ô AI kèm đếm ngược.

Phần server (`faqTopic`, bộ đếm lạc đề, `lockedUntil`, E9–E12) đã xong ở T021–T022, T030–T031.

### Tests for User Story 3 ⚠️

- [ ] T071 [P] [US3] Bổ sung `ResolveAiResultTest.kt`: `FAQ` + topic → `Policy(topic)`; `FAQ` + null → `Policy(null)`; `OFF_TOPIC` → `OffTopic`
- [ ] T072 [P] [US3] Bổ sung `AiAssistantViewModelTest.kt` phần US3: `Policy` phát sự kiện mở `aiPolicyBottomSheet`; `OffTopic` → `panel = OffTopic`; `Locked(until)` → `usage = Locked(secondsLeft)` giảm theo `Countdown` giả, nút gửi khóa, hết giờ tự mở lại; `Parsed` có `usage.lockedUntil` (câu lạc đề thứ 3) → chuyển sang khóa ngay
- [ ] T073 [P] [US3] Viết `TEST/feature/search/ui/ai/AiPolicyContentTest.kt`: mỗi `FaqTopic` có đủ cặp tiêu đề/nội dung; `null` → danh sách 4 chủ đề theo thứ tự hủy sân, đặt cọc, voucher, thanh toán

### Implementation for User Story 3

- [ ] T074 [US3] Bổ sung `ResolveAiResult.kt` nhánh `Policy`, `OffTopic`. T071 xanh
- [ ] T075 [US3] Bổ sung `AiAssistantViewModel.kt`: nhánh `Policy`, `OffTopic`, `Locked` với đếm ngược bằng `CORE/util/Countdown.kt`. T072 xanh
- [ ] T076 [P] [US3] Cài đặt `SEARCH/ui/ai/AiPolicyContent.kt` (`FaqTopic` → `@StringRes` tiêu đề/nội dung). T073 xanh
- [ ] T077 [US3] Cài đặt `SEARCH/ui/ai/AiPolicyBottomSheet.kt` + `RES/layout/bottom_sheet_ai_policy.xml`: một chủ đề (tiêu đề, nội dung, liên kết "Xem chính sách đầy đủ"), hoặc danh sách 4 chủ đề khi `topic == null` (chọn → đổi nội dung). Liên kết mở trang B chốt; chưa có thì `LegalDocumentFragment` của 010. **[BLOCKED: B]** (T047)
- [ ] T078 [US3] Tạo `RES/layout/view_ai_off_topic.xml` (câu `ai_off_topic` + 2 chip ví dụ điền vào ô) và `view_ai_notice.xml` (thông báo khóa kèm thời gian còn lại, nút "Dùng bộ lọc"); hiển thị trong vùng kết quả của `AiAssistantFragment`
- [ ] T079 [US3] Tạo `RES/values/strings_ai_policy.xml` với các khóa ở [contract app §6](contracts/app-ai-search.md#6-chuỗi-valuesstrings_aixml-tiền-tố-ai_) (`ai_policy_cancellation_*`, `ai_policy_deposit_*`, `ai_policy_voucher_*`, `ai_policy_payment_*`). **Task có điều kiện:** có văn bản của B (T047) thì dùng nguyên văn; chưa có thì viết chuỗi tạm theo Clarifications của spec (mốc 4 giờ, hoàn 100%, phạt 30%, cọc 30% làm tròn nghìn đồng), thêm comment `<!-- TODO(B): thay bằng văn bản chính thức -->` ở đầu file, và **không merge vào `develop`** khi còn `TODO(B)`
- [ ] T080 [US3] Chạy quickstart K13–K19

**Checkpoint**: US1–US3 chạy; không có văn bản tự do nào từ AI trên màn hình.

---

## Phase 6: User Story 4 - Nhập bằng giọng nói (Priority: P2)

**Goal**: micro nhận dạng tiếng Việt, điền vào ô để sửa trước khi gửi; quyền xin lúc bấm; từ chối hoặc máy không hỗ trợ thì vẫn gõ được.

**Independent Test**: cho phép micro, nói "tối mai sân quận mười dưới một trăm nghìn" → câu hiện trong ô, gửi ra chip như khi gõ; từ chối micro thì vẫn gõ được (quickstart V1–V5).

### Tests for User Story 4 ⚠️

- [ ] T081 [P] [US4] Bổ sung `AiAssistantViewModelTest.kt` phần US4 (`VoiceInput` giả): `isAvailable == false` → `voice = Hidden`; `Partial` cập nhật chữ tạm, `Final` điền `input` và **không** gửi; `Error(NO_MATCH | NETWORK)` → thông báo `ai_voice_no_match`, API không được gọi; quyền bị từ chối → `voice = Denied`; bấm micro khi `Denied` → sự kiện mở cài đặt; `startVoice = true` → tự bắt đầu nghe khi đã có quyền, chưa có quyền thì đi luồng giải thích

### Implementation for User Story 4

- [ ] T082 [US4] Cài đặt `SEARCH/data/ai/AndroidVoiceInput.kt`: `isAvailable = SpeechRecognizer.isRecognitionAvailable(context)`; `listen()` là `callbackFlow` tạo `SpeechRecognizer` trên main thread, `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` với `EXTRA_LANGUAGE = "vi-VN"`, `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_PARTIAL_RESULTS = true`; `ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` → `NO_MATCH`, `ERROR_NETWORK*` → `NETWORK`, `ERROR_RECOGNIZER_BUSY` → `BUSY`; `awaitClose { destroy() }`. Bind trong `SEARCH/di/AiSearchModule.kt`
- [ ] T083 [US4] Bổ sung `AiAssistantViewModel.kt`: `onMicClicked`, `onPermissionResult`, thu `VoiceInput.listen()` trong `viewModelScope` (hủy khi bấm lại hoặc rời màn hình). T081 xanh
- [ ] T084 [US4] Bổ sung `AiAssistantFragment.kt` + `fragment_ai_assistant.xml`: nút micro (`ic_mic`/`ic_mic_off`, `contentDescription` theo trạng thái, 48dp); `ActivityResultContracts.RequestPermission` cho `RECORD_AUDIO`; lần đầu (hoặc `shouldShowRequestPermissionRationale`) hiện `MaterialAlertDialog` với `ai_voice_rationale` rồi mới xin quyền; `Denied` bấm → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`; hiệu ứng "đang nghe"
- [ ] T085 [US4] (Sửa 020) Micro trong ô tìm kiếm của `RES/layout/fragment_search.xml` / `SEARCH/ui/SearchFragment.kt`: bấm → `aiAssistantFragment(startVoice = true)`; ẩn khi khách hoặc `VoiceInput.isAvailable == false`. **Phụ thuộc: 020 T051, T052**
- [ ] T086 [US4] Chạy quickstart V1–V5 trên máy thật hoặc Emulator có Google app (V5 trên Emulator không có dịch vụ nhận dạng)

**Checkpoint**: giọng nói dùng được; từ chối quyền không làm hỏng ô nhập.

---

## Phase 7: User Story 5 - Giới hạn lượt và quay về bộ lọc thủ công (Priority: P2)

**Goal**: hiện lượt còn lại; hết lượt, lỗi, quá giờ, mất mạng đều báo ngắn và mở bộ lọc thủ công, giữ câu; khách thấy ô AI khóa kèm lời mời đăng nhập.

**Independent Test**: dùng hết lượt của một tài khoản thử → ô khóa tới hết ngày, bộ lọc thủ công vẫn dùng; tắt mạng rồi gửi → thông báo, bộ lọc mở, câu còn trong ô (quickstart K21–K26, K29).

Phần server (giữ/hoàn lượt, chặn gửi thẳng, E1–E7) đã xong ở Phase 2.

### Tests for User Story 5 ⚠️

- [ ] T087 [P] [US5] Viết `TEST/feature/search/ui/ai/AiEntryCardViewModelTest.kt` (`SessionManager`, `AiUsageRepository`, `Countdown` giả): khách → `Guest`; còn lượt → `Remaining(10 - used)`; `used == 10` → `Exhausted`; `lockedUntil > now` → `Locked(secondsLeft)` giảm dần rồi về `Remaining`; qua 00:00 → `Remaining(10)`
- [ ] T088 [P] [US5] Bổ sung `AiAssistantViewModelTest.kt` phần US5: bấm gửi 5 lần liên tiếp khi đang gửi → API gọi **một** lần (US5-7); `AppError.Network`, `AppError.Timeout`, `AiUnavailable` → `panel = Notice(ai_busy)`, sự kiện `OpenManualFilter(criteria hiện tại)`, `input` **không đổi** (SC-006); `QuotaExceeded` → `usage = Exhausted`, nút gửi khóa; `usage` trong response cập nhật ngay trước khi listener báo; khách → nút gửi khóa, `onSubmit` không gọi API; `AppError.Locked` → không xử lý riêng (để `SessionManager` đăng xuất); mở màn hình khi đã đăng nhập → gọi `warmUp()` đúng một lần

### Implementation for User Story 5

- [ ] T089 [US5] Bổ sung `AiAssistantViewModel.kt`: chặn gửi trùng (`sending`), nhánh lỗi/hết lượt, gộp `usage` từ response và `AiUsageRepository.observe()`, `access` từ `SessionManager`, gọi `AiSearchApi.warmUp()` trong `init` khi đã đăng nhập. T088 xanh
- [ ] T090 [US5] Bổ sung `AiAssistantFragment.kt`: dòng "Còn X/10 lượt hôm nay" (`plurals`, `accessibilityLiveRegion="polite"`); trạng thái hết lượt / khóa trong `view_ai_notice.xml` kèm nút "Dùng bộ lọc" → mở bộ lọc 020; nút gửi khóa khi `EMPTY`, `TOO_LONG`, `sending`, `Exhausted`, `Locked`, `GUEST`; bộ đếm ký tự đổi màu `colorError` khi `TOO_LONG`
- [ ] T091 [US5] Bổ sung `AiEntryCardViewModel.kt` + `AiEntryCardFragment.kt`: bốn trạng thái của T087 trên thẻ ("Còn X/10", hết lượt, khóa mm:ss, khách "Đăng nhập để dùng trợ lý AI" bằng `LoginPromptView` hoặc kiểu thẻ khóa). Khách chạm → `AuthNavigator.openLogin(navController, returnTo = Uri.parse("smashnow://search"))`; nếu `nav_search` chưa có deep link `smashnow://search` thì gọi `openLogin` không `returnTo` (010 R15: `popBackStack` về `nav_search` còn nguyên `SearchViewModel`). T087 xanh
- [ ] T092 [US5] Chạy quickstart K21–K26 và K29 (dừng function local cho K22; dùng tài khoản thử riêng cho K25)

**Checkpoint**: mọi lỗi đều đưa về bộ lọc thủ công, câu còn nguyên; khách dùng bộ lọc thủ công bình thường.

---

## Phase 8: User Story 6 - Gợi ý nới điều kiện khi không có kết quả (Priority: P3)

**Goal**: bộ lọc AI cho kết quả rỗng thì hiện tối đa 3 gợi ý nới điều kiện kèm số kết quả, không gọi AI.

**Independent Test**: dữ liệu không có sân ≤100k lúc 19–21 nhưng có sân 120k → gửi câu tương ứng thấy "Nới giá lên 120k (N sân)"; bấm thì danh sách hiện các sân đó (quickstart K28).

### Tests for User Story 6 ⚠️

- [ ] T093 [P] [US6] Viết `TEST/feature/search/domain/ai/RelaxationPlannerTest.kt` (clock giả): thứ tự `PRICE_UP` (+20.000, chỉ khi có `priceMax`), `LATER` (+1 giờ), `EARLIER` (−1 giờ, bỏ khi `start − 1h` đã qua hoặc < 05:00), `DROP_AMENITY` (tiện ích cuối trước, tối đa 2); tối đa 5 biến thể; không có `timeWindow` thì không có `LATER`/`EARLIER`; khung `end + 1h > 24:00` thì bỏ `LATER`
- [ ] T094 [P] [US6] (Sửa 020) Viết test `countVariants` trong `TEST/feature/search/data/FirestoreVenueSearchEngineTest.kt` (fake `VenueDataSource`): kết quả từng biến thể bằng đúng `firstPage` của biến thể đó (cắt ở `limit`); chỉ **một** query ứng viên và **một** lượt `busySlots` mỗi cơ sở cho khung hợp; `priceRules`/`courts` lấy từ `VenueStaticCache`. **Phụ thuộc: 020 T065**
- [ ] T095 [P] [US6] Viết `TEST/feature/search/domain/ai/usecase/SuggestRelaxationsTest.kt` (`VenueSearchEngine` giả): chỉ giữ biến thể `count ≥ 1`, tối đa 3, giữ thứ tự ưu tiên; không gọi `AiSearchApi`
- [ ] T096 [P] [US6] Bổ sung `SearchViewModelTest.kt`: `lastAiCriteria` được ghi khi `applyExternal(_, AI)`; danh sách rỗng **và** `criteria == lastAiCriteria` → gọi `SuggestRelaxations`; người chơi sửa chip → không gọi; bấm gợi ý → `applyExternal(variant, AI)`

### Implementation for User Story 6

- [ ] T097 [US6] Cài đặt `SEARCH/domain/ai/model/Relaxation.kt` và `SEARCH/domain/ai/RelaxationPlanner.kt`. T093 xanh
- [ ] T098 [US6] (Sửa 020) Bổ sung `SEARCH/domain/VenueSearchEngine.kt` (`countVariants(variants, limit = 20): List<Int>`) và `SEARCH/data/FirestoreVenueSearchEngine.kt` theo [research R13](research.md#r13-gợi-ý-nới-điều-kiện-fr-022). T094 xanh. **Phụ thuộc: 020 T065**
- [ ] T099 [US6] Cài đặt `SEARCH/domain/ai/usecase/SuggestRelaxations.kt`. T095 xanh
- [ ] T100 [US6] (Sửa 020) Bổ sung `SEARCH/ui/SearchViewModel.kt` + `SearchUiState`: `lastAiCriteria`, `relaxations: List<Relaxation>` khi rỗng; `onRelaxationClicked`. T096 xanh
- [ ] T101 [US6] Cài đặt `SEARCH/ui/ai/AiRelaxationsView.kt` + `RES/layout/view_ai_relaxations.xml` (tối đa 3 nút "Nới giá lên 120k (3 sân)", "Đổi sang 20:00–22:00 (2 sân)", "Bỏ "Gửi xe" (4 sân)" theo `ai_relax_*`), đặt trong `ai_relaxations_container` của trạng thái rỗng 020; không có gợi ý thì ẩn, hiện trạng thái rỗng thường (US6-4). **Phụ thuộc: 020 T052**
- [ ] T102 [US6] Chạy quickstart K28 (thêm một cơ sở 120k/giờ vào dữ liệu Emulator nếu seed chưa đủ, chỉ nạp Emulator); ghi số lượt đọc thêm so với ước lượng ~25–100 ở R13

**Checkpoint**: cả 6 story chạy độc lập.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T103 Bật TTL trên project thật cho `expiresAt` của ba collection group `aiSearchCache`, `aiUsage`, `aiMetrics` theo [contracts/rules-and-tests.md §3](contracts/rules-and-tests.md#3-ttl) (người có quyền Owner chạy `gcloud`). Ghi kết quả vào research R7. **Nếu Spark không cho bật:** làm T104
- [ ] T104 (Chỉ khi T103 không được) Cài đặt `FN/ai-cache-cleanup/index.ts` + test: xóa document `expiresAt < now` của ba collection theo lô 300 bằng `runQuery` + `deleteWrite`; chỉ chạy khi có header bí mật `CRON_SECRET`; migration `supabase/migrations/<ts>_ai_cache_cleanup_cron.sql` tạo job `pg_cron` 00:30 (giờ Việt Nam = 17:30 UTC) gọi function qua `pg_net`
- [ ] T105 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) (5 mục: bảng collection thêm `aiMetrics`; trường `aiUsage`/`aiSearchCache`/`aiMetrics`; mục 7 phân quyền; mục 8a Edge Functions; secrets)
- [ ] T106 [P] Thêm một câu vào chính sách quyền riêng tư (tài liệu pháp lý của 011/010 mà `LegalDocumentFragment` hiển thị): câu gõ vào trợ lý AI, sau khi che số điện thoại và email, được gửi tới Google Gemini API (dự phòng Groq) để phân tích; không gửi tên hay thông tin tài khoản ([R16 #9](research.md#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác))
- [ ] T107 [P] Vẽ màn `AiAssistant` trên canvas thiết kế SmashNow (hàng mới dưới y≈28600, dùng design system và BottomNav có sẵn): ô nhập + chip ví dụ, đang nghe, hỏi lại, chính sách, lạc đề, hết lượt, khóa đếm ngược, thẻ AI của khách; sáng và tối. Xuất ảnh vào `specs/022-ai-court-search/ui/` (tên `02_ai-search_buocN_<mo-ta>.png`) kèm `README.md` như `specs/020-court-search/ui/README.md`. Chỉnh layout T059, T069, T078 cho khớp
- [ ] T108 [P] Tạo `RES/layout-w600dp/fragment_ai_assistant.xml` (cùng ID view; mở dạng dialog rộng 560dp); kiểm tra chế độ tối và TalkBack: bộ đếm ký tự, "Còn X/10", chip hỏi lại/ví dụ, micro, thẻ AI; vùng chạm ≥ 48dp (contract app §7)
- [ ] T109 Deploy lên project thật: `supabase secrets set --project-ref <ref>` cho `LLM_PROVIDER`, `LLM_MODEL`, `GEMINI_API_KEY`, `GROQ_API_KEY`, `GROQ_MODEL`, `AI_ENV=prod`; `supabase functions deploy ai-search-parse`; `firebase deploy --only firestore:rules`; làm quickstart §4 (6 bước kiểm tra trước demo)
- [ ] T110 [P] Espresso (nên có) `ATEST/feature/search/AiSearchFlowTest.kt` với Hilt test module thay `AiSearchApi` bằng bản giả: gõ câu → về `SearchFragment` có chip; API giả ném `AppError.Network` → thông báo và bảng lọc mở, câu còn trong ô
- [ ] T111 Chạy đủ kịch bản quickstart K1–K30 và V1–V5 trên Emulator với seed; chụp ảnh K2, K9, K13, K18, K25, K26 vào `docs/features/02-court-search/screenshots/`
- [ ] T112 Đo SC-004 theo [quickstart §5](quickstart.md#5-đo-thời-gian-sc-004) trên máy thật 4G với function đã deploy: 20 câu, ≥ 18 lần ≤ 4 giây; 3 lần cold start; đối chiếu `aiMetrics.latencyMs`, `llmLatencyMs`. Ghi kết quả vào `docs/reports/ai-eval/sc-004-<ngày>.md`
- [ ] T113 Thử SC-003 với 5 người ngoài nhóm theo quickstart §3 "Thử với người dùng"; ghi bảng thời gian vào `docs/reports/ai-eval/sc-003-<ngày>.md`
- [ ] T114 Chạy `EVAL/run-eval.ts` lần cuối với `PROMPT_VERSION` đang deploy (mục tiêu SC-001, SC-002, SC-007 như T035) và `EVAL/export-metrics.mjs` cho khoảng ngày thử nghiệm; đưa hai báo cáo vào báo cáo kỹ thuật
- [ ] T115 [P] Cập nhật `docs/features/02-court-search/README.md` (đánh dấu mục AI Assistant, link spec/plan/tasks 022, ảnh chụp) và cột Spec nhóm 02 trong `docs/features/README.md`
- [ ] T116 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `npm test` trong `firestore-tests`, `deno test --allow-env --allow-net --allow-read supabase/functions/ai-search-parse/` (và `FN/_shared/`); không còn cảnh báo Lint mức Error; không còn `TODO(B)` trong `strings_ai_policy.xml` trước khi merge vào `develop`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 trước mọi task khác (dừng nếu Flash-Lite không còn miễn phí). T003–T007 song song.
- **Foundational (Phase 2)**:
  - 2A (T008–T009) trước 2C phần cần Firestore (T028–T031).
  - 2B (T011–T012) độc lập.
  - 2C hàm thuần (T013–T027) không cần 2A; T030–T031 cần 2A.
  - 2D (T033–T036) sau T031.
  - 2E (T037) bất cứ lúc nào, nên làm trước khi code 020 US1.
  - 2F: T038–T040 độc lập; T041–T046 cần **020 T011, T026**.
- **User stories (Phase 3–8)**: cần Phase 2 và các task 020 ghi trong từng phase. Sau đó:
  - US1 trước (tạo `AiAssistantFragment`, `AiAssistantViewModel` mà US2–US5 bổ sung).
  - US2, US3, US4, US5 làm song song được sau US1 (cùng sửa `AiAssistantViewModel`/`Fragment` nên nên tuần tự nếu một người làm).
  - US6 cần US1 và **020 T065** (S2 engine), **020 T052**.
- **Polish (Phase 9)**: sau các story muốn đưa vào bản demo. T109 cần T031, T012.

### Phụ thuộc vào 020

| Task 022 | Cần task 020 |
|---|---|
| T041–T046 (model, interface, API, repo) | T011 (`TimeOfDay`), T026 (`SortOption`, `SearchCriteria`) |
| US1 T050, T055 | T026 |
| T053, T057 | T088 (`applyExternal`), T084 (sanitizer), T030 (`nav_search.xml`) |
| T062, T085 | T051, T052 (`ai_entry_container`, micro, chế độ trang chủ) |
| T069 | T054, T066 (bottom sheet bộ lọc) |
| T094, T098 | T065 (S2, `VenueStaticCache`, `AvailabilityEvaluator`) |
| T101 | T052 (trạng thái rỗng) |

### Within Each User Story

- Test viết trước và chạy thấy fail.
- Model → use case → ViewModel → layout/Fragment → chạy kịch bản quickstart.

### Parallel Opportunities

- Phase 1: T003, T004, T005, T006, T007.
- Phase 2: T010, T011, T013, T015, T017, T019, T021, T024, T025, T026, T028, T029, T033, T036, T038, T040 (file khác nhau); 2A, 2B, 2C hàm thuần, 2E là bốn luồng song song.
- Mỗi story: các task test có `[P]`.

---

## Parallel Example: Phase 2C (hàm thuần của Edge Function)

```bash
# Viết test song song (mỗi file một người/một lượt):
Task: "T013 AIFN/query.test.ts"
Task: "T015 AIFN/pii.test.ts"
Task: "T017 AIFN/normalize.test.ts (vector §4.1, §4.2)"
Task: "T019 AIFN/validate.test.ts"
Task: "T021 AIFN/usage.test.ts"

# Hai client LLM song song:
Task: "T025 AIFN/llm/gemini.ts + test"
Task: "T026 AIFN/llm/groq.ts + test"
```

## Parallel Example: User Story 1

```bash
Task: "T050 AiFilterMapperTest.kt"
Task: "T051 SubmitAiQueryTest.kt, ResolveAiResultTest.kt"
Task: "T052 AiAssistantViewModelTest.kt (US1)"
Task: "T053 SearchViewModelTest.kt (applyExternal trả SanitizeResult)"
Task: "T054 CORE/navigation/DropInLink.kt"
```

---

## Implementation Strategy

### Server trước, trong lúc chờ 020

1. Phase 1 (T001 trước tiên).
2. Phase 2A–2D: Edge Function đầy đủ, Rules, bộ câu mẫu đạt SC-001/SC-002. Đây là phần làm ngay được và là bằng chứng chính cho báo cáo (tỉ lệ trích đúng, chống bẻ prompt, che PII).
3. 2E: sửa tài liệu 020 trước khi code 020 US1, để 020 chừa sẵn chỗ cho 022.

### MVP (User Story 1)

1. Khi 020 xong Phase 2 + US1 + US4 (T088): làm 2F, rồi Phase 3.
2. **DỪNG và kiểm tra**: quickstart K1–K8 trên Emulator.
3. Demo được luồng chính "một câu → chip → danh sách".

### Incremental Delivery

1. US1 → MVP.
2. US2 (hỏi lại) + US5 (lượt, lỗi, khách): đủ để dùng thật, không bao giờ kẹt người dùng.
3. US3 (chính sách, lạc đề, khóa) khi có văn bản của B.
4. US4 (giọng nói).
5. US6 (gợi ý nới điều kiện) sau khi 020 có S2.
6. Polish: TTL, deploy, đo SC-003/SC-004, eval cuối.

---

## Notes

- `[P]` = file khác nhau, không phụ thuộc task chưa xong.
- Không commit khóa API; `.env` đã bị `.gitignore` chặn.
- Test tự động không gọi LLM thật và không chạy trên project Firebase/Supabase thật (constitution IV). Chỉ T035, T114 (eval) gọi LLM thật, với Firestore giả.
- Không tự sửa `firestore.rules` phần của người khác; chỉ thêm ba match của 022.
- Không sửa `seed.mjs` của C; dữ liệu thử thêm cho K28 chỉ nạp Emulator.
- Commit theo từng task hoặc nhóm task, scope `feat(search)` / `feat(ai)` / `chore(functions)`.
