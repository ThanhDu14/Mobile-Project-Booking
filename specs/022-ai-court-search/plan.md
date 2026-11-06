# Implementation Plan: Tìm sân thông minh (AI Assistant)

**Branch**: `feature/02-ai-court-search-plan` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/022-ai-court-search/spec.md`

## Summary

Người chơi đã đăng nhập gõ hoặc nói một câu tiếng Việt tự do ("q10 duoi 100k co gui xe toi mai"). App đổi câu đó thành chip bộ lọc của 020, hoặc mở danh sách vãng lai của 080, hỏi lại một lần, hiện chính sách có sẵn, hay báo lạc đề. AI chỉ điền bộ lọc: không viết chữ cho người dùng xem, không đặt sân, không đọc dữ liệu nghiệp vụ.

**Cách làm:**
- **Một Edge Function `ai-search-parse`** ([contract](contracts/edge-function-ai-search-parse.md)): xác thực Firebase ID token bằng `_shared/auth.ts` → che PII lần hai → kiểm tra khóa tạm → cache trong ngày → giữ lượt bằng transaction → gọi LLM → kiểm tra và chuẩn hóa → cập nhật bộ đếm lạc đề, ghi cache và `aiMetrics` trong một commit.
- **LLM: Gemini Flash-Lite (chính), Groq `gpt-oss-20b` (dự phòng)** ([R2](research.md#r2-chọn-nhà-cung-cấp-llm)). Cả hai gói miễn phí, không cần thẻ, ràng buộc đầu ra bằng cùng một JSON Schema. Lớp `LlmClient` tách riêng; đổi nhà cung cấp bằng secret.
- **LLM nhận diện, code tính** ([R3](research.md#r3-một-lần-gọi-phần-nào-llm-làm-phần-nào-code-làm-fr-005-đến-fr-007)):
  - LLM trả `day.kind` ("mai", "thứ 7"), `partOfDay`, giờ như người dùng nói, id quận/tiện ích chọn từ danh mục trong prompt.
  - Code đổi ra ngày thật, giờ 24h (mặc định buổi tối cho "7h-9h"), làm tròn 30 phút. Có bảng vector test ([contract §4](contracts/llm-prompt-and-schema.md#4-quy-tắc-chuẩn-hóa-tất-định)).
- **Kiểm tra ở hai phía** ([R5](research.md#r5-kiểm-tra-kết-quả-fr-008-ở-server-và-kiểm-tra-lại-ở-app)):
  - Server: schema, trường lạ, danh mục, ngày giờ, giá. `venueName` phải là chuỗi con của câu. App không bao giờ thấy đầu ra thô của LLM.
  - App: chạy lại `SearchCriteriaSanitizer` của 020 qua `applyExternal`, vì kết quả cache có thể đã cũ trong ngày.
- **Lượt và khóa** ([R8](research.md#r8-lượt-dùng-khóa-tạm-hai-yêu-cầu-cùng-lúc-fr-016-đến-fr-019-sc-005-sc-008)): giữ lượt trước trong transaction Firestore REST, hoàn lượt nếu LLM lỗi/quá giờ. Hai yêu cầu song song không vượt 10 (SC-005, SC-008). Khóa 5 phút sau 3 câu lạc đề liên tiếp, kể cả khi cache trúng.
- **Cache** ([R7](research.md#r7-cache-trong-ngày-fr-018)): `SHA-256(câu đã che + chuẩn hóa + ngày)`; trúng thì không trừ lượt. Dọn bằng TTL policy (dự phòng `pg_cron`).
- **App** ([contract](contracts/app-ai-search.md)):
  - thẻ AI trong `ai_entry_container` của 020;
  - màn hình `AiAssistantFragment` trong `nav_search`;
  - `ClarifyPlanner` hỏi lại một lần bằng chip, không gọi AI;
  - chính sách là chuỗi do B soạn;
  - giọng nói bằng `SpeechRecognizer` (vi-VN), xin micro lúc bấm;
  - vãng lai qua deep link `smashnow://dropin/list?...` (không import `feature/dropin`).
- **Gợi ý nới điều kiện** ([R13](research.md#r13-gợi-ý-nới-điều-kiện-fr-022)): tối đa 5 biến thể, đếm bằng một hàm mới `VenueSearchEngine.countVariants` của 020 (một lượt đọc ứng viên + `slots` cho mọi biến thể).
- **Đo lường và bộ câu mẫu** ([R9](research.md#r9-đo-lường-fr-023), [eval-suite](contracts/eval-suite.md)):
  - `aiMetrics` ẩn danh trong Firestore, xuất bằng script.
  - 40 câu mẫu chạy thẳng vào handler với Firestore giả và LLM thật: không vướng giới hạn lượt, không cần cờ bỏ giới hạn trên bản deploy.

## Technical Context

**Language/Version**: Kotlin (bản đi kèm AGP 9.4), JVM target 11; TypeScript trên Deno (Supabase Edge Runtime) cho Edge Function và script đánh giá; Node 20 cho script xuất số liệu; TypeScript (Vitest) cho test Rules.

**Primary Dependencies**:
- Đã có trên `main`: Fragment/Navigation + Safe Args, ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt (KSP), Firebase BoM (Auth, Firestore), kotlinx-serialization, `supabase-kt` `functions-kt` 3.6.0 + Ktor OkHttp; Edge Function: `jose` (qua `_shared/auth.ts`).
- Đã chốt ở 020 (chưa cài): `core/catalog`, `core/schedule`, `core/text` (`VietnameseNormalizer`), `SearchCriteria`, `SearchCriteriaSanitizer`, `VenueSearchEngine`, `SearchViewModel`.
- Android SDK: `android.speech.SpeechRecognizer` (không thêm thư viện).
- Edge Function: gọi Gemini và Groq bằng `fetch` REST, **không** thêm SDK. SHA-256 bằng Web Crypto `crypto.subtle`. Kiểm tra schema tự viết trong `validate.ts` (schema nhỏ, ~14 trường), không thêm `ajv`.
- **Không có thư viện mới** ở app hay function.

**Storage**:
- Firestore (A sở hữu, chỉ Edge Function ghi): `users/{uid}/aiUsage/{yyyyMMdd}`, `aiSearchCache/{hash}`, `aiMetrics/{id}` (mới). TTL trên `expiresAt`.
- Firestore đọc: `users/{uid}.accountStatus`, `districts`, `amenities`.
- Trên máy: `SavedStateHandle` (câu đang gõ, `clarifyUsed`); không lưu câu ở DataStore hay lịch sử AI riêng. Lịch sử tìm kiếm vẫn do 020 ghi khi `applyExternal`.
- Không lưu câu gốc ở bất kỳ đâu.

**Testing**:
- Deno test + Firebase Emulator cho `ai-search-parse` (E1–E18), LLM giả.
- Vitest + `@firebase/rules-unit-testing` cho Rules (AI-1…AI-8).
- JUnit4, MockK, Turbine, coroutines-test: `PiiMasker` (vector dùng chung với Deno), `AiQueryInput`, `AiFilterMapper`, `ClarifyPlanner`, `RelaxationPlanner`, `SuggestRelaxations`, `SupabaseAiSearchApi`, `AiAssistantViewModel`, `AiEntryCardViewModel`.
- Script đánh giá 40 câu với LLM thật (SC-001, SC-002, SC-007).
- Kịch bản thủ công K1–K30, V1–V5, đo SC-004 ([quickstart](quickstart.md)).
- Espresso (nên có): `AiSearchFlowTest` với `AiSearchApi` giả.

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; điện thoại và `w600dp`; sáng và tối. Supabase Edge Runtime (Deno), region Singapore.

**Project Type**: mobile-app (một module `app`, package theo tính năng) + một Supabase Edge Function.

**Performance Goals**:
- Chip hiện ≤ 4 giây ở ≥ 90% lần gửi trên 4G (SC-004). Ước lượng 1,4–3,6 giây khi function nóng, 0,6–1,3 giây khi cache trúng ([R14](research.md#r14-thời-gian-chờ-và-độ-trễ-fr-020-sc-004)). Gọi warmup khi mở ô AI.
- Gợi ý nới điều kiện thêm ~25–100 lượt đọc, chỉ khi kết quả AI rỗng.

**Constraints**:
- Gói miễn phí: Gemini (RPD theo AI Studio), Groq dự phòng; Firestore Spark 50.000 đọc / 20.000 ghi mỗi ngày. Mỗi lượt AI: ~3–4 đọc, ~3 ghi. Trần gọi LLM = 10 × số người dùng hoạt động (SC-008).
- Timeout: app 10 giây; LLM 6 giây, dự phòng ≤ 3 giây.
- Câu ≤ 200 ký tự. `max_tokens` 200, `temperature` 0.
- Không gửi uid, tên, email, số điện thoại tới LLM; ngày giờ lấy từ server.
- Mọi chữ hiển thị là resource của app.

**Scale/Scope**:
- 1 Edge Function (~12 file, 2 client LLM), bổ sung `_shared/firestore.ts` (transaction, query).
- 2 Fragment + 1 bottom sheet, 2 ViewModel; ~5 lớp thuần Kotlin.
- 3 collection, 8 test Rules, 18 nhóm test function.
- 40 câu mẫu, 2 script.
- Quy mô đồ án: 4 người thử, < 30 người dùng demo, < 300 lượt AI/ngày.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Fragment → `AiAssistantViewModel` → use case (`SubmitAiQuery`, `ResolveAiResult`, `SuggestRelaxations`) → `AiSearchApi` / `AiUsageRepository` / `VoiceInput` (interface ở `domain/ai`) → Supabase Functions / Firestore / `SpeechRecognizer`. `StateFlow<AiAssistantUiState>`, sự kiện qua `Channel`. Cùng package `feature/search` với 020/021 nên dùng `SearchCriteria` hợp lệ. Vãng lai qua deep link, **không** import `feature/dropin`. Listener `aiUsage` bọc `callbackFlow`, gỡ khi không thu |
| II. Bảo mật phía server | ✅ | Lượt, khóa tạm, cache, kiểm tra đầu ra đều ở Edge Function; ẩn nút trên app chỉ để trải nghiệm. `aiUsage` client chỉ đọc của mình; `aiSearchCache`, `aiMetrics` client không truy cập. Khóa LLM chỉ trong secrets. Test Rules AI-1…AI-8 trên Emulator; test function E1–E18 trên Emulator |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ (không đụng) | Không ghi slot hay đơn. AI không đặt sân (FR-010). Đếm lượt dùng transaction Firestore để không vượt trần, cùng tinh thần "kiểm tra rồi ghi trong một bước". Giờ: server dùng giờ Việt Nam, lưu `Timestamp` UTC; giá `Long`/integer VND |
| IV. Kiểm thử có trọng tâm | ✅ | Bắt buộc: logic lượt/khóa (gồm test song song E5), validator, chuẩn hóa thời gian, che PII, ViewModel. Không chạy test trên project thật; bộ đánh giá dùng Firestore giả và chỉ gọi LLM thật (thứ cần đo) |
| V. Giao diện XML nhất quán | ✅ | XML + Material 3 DayNight; `strings_ai.xml`, `strings_ai_policy.xml` tiền tố `ai_`; đủ trạng thái (đang gửi, kết quả, hết lượt, khóa, lỗi kèm "Dùng bộ lọc"); mất mạng không crash; `w600dp`; TalkBack cho bộ đếm, lượt còn lại, chip, micro; 48dp |
| VI. Quyền riêng tư | ✅ | Che email/số điện thoại hai lần; không gửi thông tin tài khoản; không lưu câu gốc; `aiMetrics` ẩn danh. Micro xin lúc bấm, có giải thích; từ chối vẫn gõ được. Đề nghị thêm một câu vào chính sách quyền riêng tư (011) về việc gửi câu đã che tới Google Gemini ([R16 #9](research.md#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác)) |
| VII. Đơn giản, minh bạch AI | ✅ | Không thêm thư viện, module Gradle hay tầng trừu tượng ngoài interface repository bắt buộc. `LlmClient` có hai cài đặt vì có phương án dự phòng thật (R2), ghi ở Complexity Tracking. Collection mới `aiMetrics` phục vụ FR-023. Log AI ở `docs/ai-log/AI_log_1.md` |

**Kết quả:** đạt. Hai bổ sung ghi ở Complexity Tracking (nhà cung cấp LLM thứ hai, collection `aiMetrics`). Không có vi phạm phải xin ngoại lệ.

**Re-check sau Phase 1:** vẫn đạt. Thay đổi ảnh hưởng nhóm khác:
1. `_shared/firestore.ts` thêm transaction và query: file chung, cần B đồng ý chữ ký.
2. 020: `applyExternal` trả `SanitizeResult`; thêm `countVariants`; destination `aiAssistantFragment` trong `nav_search`.
3. 080: thêm deep link `smashnow://dropin/list`.

Không mục nào nới lỏng nguyên tắc.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **B** (spec 050, 060) | 1. **Soạn văn bản chính sách** cho 4 chủ đề (hủy sân, đặt cọc, voucher, thanh toán/ví giả lập), mỗi chủ đề 3–5 câu, khớp hằng số chính sách trong 050/060 (mốc 4 giờ, phạt 30%, cọc 30% làm tròn nghìn đồng). A đưa vào `strings_ai_policy.xml` ([contract §6](contracts/app-ai-search.md#6-chuỗi-valuesstrings_aixml-tiền-tố-ai_)). Đổi chính sách thì B sửa file này trong cùng PR.<br>2. Cho biết trang "chính sách đầy đủ" để liên kết (mặc định `LegalDocumentFragment` của 010).<br>3. **`_shared/firestore.ts`**: đồng ý chữ ký `beginTransaction`, `batchGet`, `commit(writes, tx?)`, `rollback`, `runTransaction`, `runQuery`, `setWrite`, `deleteWrite` ([contract §3.2](contracts/edge-function-ai-search-parse.md#32-bổ-sung-_sharedfirestorets)). A viết trước; khi B thay bằng bản đầy đủ cho `create-booking` thì giữ nguyên chữ ký. | Trước khi A code Edge Function |
| **C** (spec 080) | 1. Thêm **deep link** `smashnow://dropin/list?districtIds={csv}&date={yyyyMMdd}&timeSlot=&level=&maxPrice=` vào `nav_dropin.xml`; `DropInListFragment` đổi tham số sang `DropInFilterArgs` (thay cho việc 022 import `DropInFilterArgs`, vì constitution I cấm) ([R12](research.md#r12-nhánh-buổi-vãng-lai-fr-012)).<br>2. Sửa câu chữ 080 FR-003 về mốc "Tối" (18–23 theo giờ bắt đầu của buổi; 022 dùng 18–22 cho khung đặt sân) ([R16 #3](research.md#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác)).<br>3. Xác nhận `maxPrice` là giá mỗi lượt. | Khi C code 080 US1 (T154 "nhận `DropInFilterArgs` từ AI") |
| **D** (spec 111, 120) | 1. Xác nhận **khoảng giá hợp lệ**: giá/giờ lớn nhất chủ sân nhập được ở 111 (để đặt `PRICE_CEILING` sát hơn 2.000.000) và giá/lượt lớn nhất của buổi vãng lai ở 112.<br>2. Danh mục quận/tiện ích admin quản lý ở 120: giữ ID slug ổn định (`q10`, `parking`…), ẩn bằng `isActive = false` thay vì xóa (bảng bí danh quận và cache AI dựa vào ID).<br>3. (Tùy chọn) màn thống kê admin đọc `aiMetrics` (Rules đã cho admin đọc). | Trước khi D code màn danh mục 120 |
| **A** (spec 020, của mình) | Sửa plan/tasks 020 theo [R1](research.md#r1-thứ-tự-làm-và-phụ-thuộc-vào-020-021-080):<br>1. `applyExternal` trả `SanitizeResult`, không tự hiện snackbar khi `source == AI` (T088, contract §3).<br>2. `VenueSearchEngine.countVariants(variants, limit)` (contract §4) + test.<br>3. `nav_search.xml` thêm `aiAssistantFragment`, `aiPolicyBottomSheet`; micro trong ô tìm kiếm (T051) mở `aiAssistantFragment(startVoice = true)`, ẩn với khách / máy không hỗ trợ.<br>4. Trạng thái rỗng của danh sách chừa vùng gợi ý nới điều kiện. | Trước khi code 020 US1 (T051) và US4 (T088) |
| **A** (spec 011, của mình) | 1. `delete-account` đã xóa `aiUsage` (011 contract bước 7): giữ nguyên.<br>2. Thêm câu về AI vào chính sách quyền riêng tư. | Trước khi phát hành bản demo |
| **A** (spec 021, của mình) | Không cần thay đổi: bản đồ tự tải lại khi `criteria` đổi qua `applyExternal`. | — |
| **Cả nhóm** | 1. Ai giữ tài khoản Google/Groq của nhóm thì tạo khóa và đặt `supabase secrets` (không gửi khóa qua chat).<br>2. Review PR `_shared/firestore.ts`. | Trước khi deploy function |
| A (mình) | 1. Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd).<br>2. Sửa spec theo [R16](research.md#r16-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác).<br>3. Vẽ màn `AiAssistant` (ô nhập, hỏi lại, chính sách, lạc đề, hết lượt, khóa) trên canvas thiết kế, xuất ảnh vào `specs/022-ai-court-search/ui/` (hiện chưa có wireframe riêng, chỉ có thẻ AI trong wireframe 020 bước 1).<br>4. Bật TTL hoặc tạo `ai-cache-cleanup`. | Trước khi code màn hình |

## Project Structure

### Documentation (this feature)

```text
specs/022-ai-court-search/
├── spec.md
├── plan.md                              # file này
├── research.md                          # Phase 0 (R1–R16)
├── data-model.md                        # Phase 1
├── quickstart.md                        # Phase 1
├── contracts/
│   ├── edge-function-ai-search-parse.md # request/response, luồng, lượt/khóa, test E1–E18, secrets
│   ├── llm-prompt-and-schema.md         # system prompt, JSON Schema, chuẩn hóa tất định + vector
│   ├── app-ai-search.md                 # API, use case, mapper, màn hình, chuỗi, trợ năng
│   ├── rules-and-tests.md               # Rules, TTL, test Rules và test app
│   └── eval-suite.md                    # 40 câu mẫu, script đánh giá, xuất số liệu
├── checklists/requirements.md
├── ui/                                  # cần vẽ (xem Phối hợp)
└── tasks.md                             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── core/navigation/DropInLink.kt                       # mới: dựng URI deep link vãng lai
└── feature/search/                                     # của 020, thêm phần 022
    ├── domain/ai/
    │   ├── model/ AiIntent.kt, FaqTopic.kt, SearchType.kt, PartOfDay.kt, DropInLevel.kt, ParsedFilter.kt,
    │   │          AiParseResult.kt, AiDropReason.kt, AiUsage.kt, AiSubmitOutcome.kt, ClarifyQuestion.kt,
    │   │          Relaxation.kt, VoiceEvent.kt, AiUnavailable.kt
    │   ├── AiSearchApi.kt, AiUsageRepository.kt, VoiceInput.kt            # interface
    │   ├── PiiMasker.kt, AiQueryInput.kt, AiFilterMapper.kt, ClarifyPlanner.kt, RelaxationPlanner.kt  # thuần Kotlin
    │   └── usecase/ SubmitAiQuery.kt, ResolveAiResult.kt, SuggestRelaxations.kt
    ├── data/ai/
    │   ├── SupabaseAiSearchApi.kt, dto/AiSearchDto.kt
    │   ├── FirestoreAiUsageRepository.kt
    │   └── AndroidVoiceInput.kt
    ├── ui/ai/
    │   ├── AiEntryCardFragment.kt, AiEntryCardViewModel.kt
    │   ├── AiAssistantFragment.kt, AiAssistantViewModel.kt, AiAssistantUiState.kt
    │   ├── AiPolicyBottomSheet.kt
    │   └── AiRelaxationsView.kt
    └── di/AiSearchModule.kt

app/src/main/res/
├── layout/ fragment_ai_entry_card.xml, fragment_ai_assistant.xml, bottom_sheet_ai_policy.xml, view_ai_relaxations.xml
├── layout-w600dp/fragment_ai_assistant.xml
├── navigation/nav_search.xml                           # của 020: + aiAssistantFragment, aiPolicyBottomSheet
├── drawable/ ic_ai_sparkle.xml, ic_mic.xml, ic_mic_off.xml
└── values/ strings_ai.xml, strings_ai_policy.xml
app/src/main/AndroidManifest.xml                        # + RECORD_AUDIO, <queries> RecognitionService

app/src/test/java/com/example/booking_app/feature/search/
├── domain/ai/ PiiMaskerTest, AiQueryInputTest, AiFilterMapperTest, ClarifyPlannerTest, RelaxationPlannerTest,
│              SuggestRelaxationsTest
├── data/ai/SupabaseAiSearchApiTest
└── ui/ai/ AiAssistantViewModelTest, AiEntryCardViewModelTest
app/src/androidTest/java/com/example/booking_app/feature/search/AiSearchFlowTest.kt   # nên có

supabase/functions/
├── _shared/firestore.ts                                # + transaction, batchGet, runQuery, setWrite, deleteWrite
├── ai-search-parse/                                    # mới, cấu trúc ở contract §3.1
│   ├── index.ts, handler.ts, query.ts, pii.ts, prompt.ts, schema.ts, normalize.ts, validate.ts,
│   ├── usage.ts, cache.ts, metrics.ts, catalog.ts, llm/{client,gemini,groq,failover,fake}.ts
│   └── *.test.ts
└── ai-cache-cleanup/index.ts                           # chỉ khi không bật được TTL
supabase/config.toml                                    # + [functions.ai-search-parse] verify_jwt = false

firestore.rules                                         # + aiUsage, aiSearchCache, aiMetrics
firestore-tests/test/ai.test.ts                         # AI-1…AI-8
scripts/testdata/ai/pii-vectors.json                    # dùng chung Kotlin + Deno
scripts/ai-eval/ cases.json, run-eval.ts, export-metrics.mjs
docs/reports/ai-eval/                                   # kết quả các lần chạy đánh giá
```

**Structure Decision**:
- **App:** 022 nằm trong `feature/search` cùng 020, 021 (nhóm 02), với gói con `ai/` ở cả ba tầng. Dùng `SearchCriteria`, `SearchViewModel`, `VenueSearchEngine` mà không vi phạm quy tắc import chéo. Thẻ AI là Fragment con trong `SearchFragment`; màn hình nhập AI là destination trong `nav_search` để dùng chung `SearchViewModel` (scope nav graph). Buổi vãng lai mở bằng deep link, dựng URI ở `core/navigation`.
- **Server:** một Edge Function mới, theo đúng mẫu `on-signup` (handler nhận `deps`, `index.ts` mỏng, test Deno với Emulator). Phần dùng chung (transaction, query) đặt ở `_shared/firestore.ts`.

## Complexity Tracking

| Vi phạm / bổ sung | Vì sao cần | Phương án đơn giản hơn bị loại vì |
|---|---|---|
| **Hai nhà cung cấp LLM** (`LlmClient` + `gemini.ts`, `groq.ts`, `failover.ts`) | Hạn mức miễn phí của Gemini có thể hết hoặc bị giảm bất ngờ (đã xảy ra 12/2025). Nếu không có dự phòng, ô AI chết đúng buổi demo. Cùng một JSON Schema, nên mỗi client chỉ ~60 dòng ([R2](research.md#r2-chọn-nhà-cung-cấp-llm)) | Một nhà cung cấp: không có đường lui khi hết hạn mức. Thư viện trung gian (LangChain, Vercel AI SDK): nặng, thêm phụ thuộc cho hai lời gọi `fetch` |
| **Collection mới `aiMetrics`** | FR-023 cần số liệu theo từng lần gọi (độ trễ, token, trường bị bỏ) giữ đủ lâu cho báo cáo cuối kỳ ([R9](research.md#r9-đo-lường-fr-023)) | Log Edge Function: gói Free giữ ngắn hạn, khó tổng hợp. Bảng Postgres Supabase: dễ bị hiểu là trái constitution (không dùng database Supabase), thêm migration và RLS |
| **Hàm transaction trong `_shared/firestore.ts`** | Giữ lượt không vượt trần khi gửi song song (SC-005) cần transaction Firestore REST; bản `_shared` hiện chỉ có `get`/`commit` ([R8](research.md#r8-lượt-dùng-khóa-tạm-hai-yêu-cầu-cùng-lúc-fr-016-đến-fr-019-sc-005-sc-008)) | `increment` không điều kiện: vượt 10 khi hai yêu cầu cùng lúc. Bảng đếm trong Postgres: trái đặc tả CSDL đã chốt `aiUsage` trong Firestore |
