# Contract: Phía app (API, ViewModel, màn hình, chuỗi)

**Package:** `com.example.booking_app.feature.search` (nhóm 02, cùng 020, 021). Lý do thiết kế: [research R1, R11–R15](../research.md).

## 1. Data

```kotlin
// domain/ai/AiSearchApi.kt
interface AiSearchApi {
    /** Ném AppError: Network, Timeout (10 s), NotSignedIn, Locked; AiUnavailable cho 502/504. */
    suspend fun parse(maskedQuery: String): AiSubmitOutcome
    /** Gọi warmup, bỏ qua mọi lỗi. */
    suspend fun warmUp()
}

// domain/ai/AiUsageRepository.kt
interface AiUsageRepository {
    /** Listener users/{uid}/aiUsage/{today}; đổi document khi qua 00:00 (AppClock). Khách → flow rỗng. */
    fun observe(): Flow<AiUsage>
}

// domain/ai/VoiceInput.kt
interface VoiceInput {
    val isAvailable: Boolean                 // SpeechRecognizer.isRecognitionAvailable
    fun listen(): Flow<VoiceEvent>           // hủy collect = dừng nghe
}
```

- `SupabaseAiSearchApi` (data/ai): giống `SupabaseOnSignupApi`. Dùng `FunctionErrors.invokeWithAuthRetry` (401 → làm mới token, thử lại một lần; 403 `ACCOUNT_LOCKED` → `AppError.Locked`). Bọc `withTimeout(AI_TIMEOUT_MS = 10_000)`. Map thêm: HTTP 502/504 → `AiUnavailable` (lớp lỗi riêng trong `domain/ai`, kế thừa `Exception`, không sửa `AppError` dùng chung); 400 `INVALID_QUERY` → `IllegalArgumentException` (không xảy ra nếu app đã chặn).
- DTO `@Serializable` với `ignoreUnknownKeys = true`. Enum lạ (server mới hơn app) → `UNCLEAR`.
- `FirestoreAiUsageRepository`: `callbackFlow` + `awaitClose { registration.remove() }`; thu bằng `repeatOnLifecycle(STARTED)` nên tự gỡ khi màn hình ẩn (constitution).
- `AndroidVoiceInput`: tạo `SpeechRecognizer` trên main thread, `EXTRA_LANGUAGE = "vi-VN"`, `EXTRA_PARTIAL_RESULTS = true`, `destroy()` khi flow kết thúc.

Hilt: `AiSearchModule` trong `feature/search/di` bind ba interface.

## 2. Use case

| Use case | Việc |
|---|---|
| `SubmitAiQuery` | `AiQueryInput.validate` → `PiiMasker.mask` → `AiSearchApi.parse`. Không gửi khi `EMPTY`/`TOO_LONG` (FR-002) |
| `ResolveAiResult` | Từ `AiParseResult` ra một trong: `ApplyCourt(criteria)`, `OpenDropIn(uri, droppedKeys)`, `Clarify(question)`, `Policy(topic?)`, `OffTopic` — dùng `ClarifyPlanner`, `AiFilterMapper` |
| `SuggestRelaxations` | `RelaxationPlanner` → `VenueSearchEngine.countVariants` (020) → tối đa 3 `Relaxation` có `count ≥ 1` |

## 3. `AiFilterMapper`

`ParsedFilter` → `SearchCriteria` (020), điền vào bộ lọc dùng chung và chip, người chơi sửa/bỏ từng chip không gọi AI (FR-011):

| `ParsedFilter` | `SearchCriteria` |
|---|---|
| `keyword` | `keyword` (thay toàn bộ, kể cả từ khóa người chơi tự gõ trước đó; Edge case "câu mới thay toàn bộ") |
| `districtId` | `districtId` |
| `date` + `start` + `end` | `timeWindow` |
| `priceMin`, `priceMax` | giữ nguyên (sanitizer 020 làm tròn) |
| `amenityIds` | `amenityIds` |
| `sort` | `sort ?: SortOption.RATING`. `NEAREST` thì `SearchViewModel` tự xin quyền vị trí như 020 (US1-5) |
| `level`, `partOfDay`, `dateChoices` | không dùng cho sân thường |

`ParsedFilter` (`type == DROP_IN`) → `DropInLink.build(...)` ([R12](../research.md#r12-nhánh-buổi-vãng-lai-fr-012)). `DropInLink` ở `core/navigation`:

```text
smashnow://dropin/list?districtIds=q10&date=20261010&timeSlot=EVENING&level=BEGINNER&maxPrice=50000
```

Tham số không có thì bỏ khỏi URI. `timeSlot` = `partOfDay`, hoặc buổi chứa `start` theo mốc 080 (05/11/13/18/23).

## 4. `SearchViewModel` (020) dùng ở 022

```kotlin
suspend fun applyExternal(criteria: SearchCriteria, source: CriteriaSource): SanitizeResult   // đổi kiểu trả (R1 mục 3)
val criteria: StateFlow<SearchCriteria>
```

`AiAssistantViewModel` không giữ tham chiếu `SearchViewModel`. `AiAssistantFragment` lấy cả hai bằng `hiltNavGraphViewModels(R.id.nav_search)` / `viewModels()` và chuyển `ApplyCourt` sang `searchViewModel.applyExternal(...)`, rồi đưa `SanitizeResult.dropped` lại cho `AiAssistantViewModel` để chọn thông báo.

## 5. Màn hình

| Fragment | Layout | Vị trí | Vào từ |
|---|---|---|---|
| `AiEntryCardFragment` | `fragment_ai_entry_card.xml` | Fragment con trong `ai_entry_container` của `SearchFragment` (020) | Tự hiện khi `criteria` trống (chế độ trang chủ của 020) |
| `AiAssistantFragment` | `fragment_ai_assistant.xml` (+ `item_ai_chip` dùng `Chip` Material) | Destination `aiAssistantFragment` trong `nav_search`; argument `startVoice: Boolean = false`, `prefill: String? = null` | Chạm thẻ AI; micro trong ô tìm kiếm 020 (`startVoice = true`); chip ví dụ trên thẻ (`prefill`) |
| `AiPolicyBottomSheet` | `bottom_sheet_ai_policy.xml` | Dialog destination trong `nav_search`, argument `topic: FaqTopic?` | `AiAssistantFragment` |
| (vùng gợi ý nới điều kiện) | `view_ai_relaxations.xml` | Trong trạng thái rỗng của danh sách 020 | `SearchFragment` khi `criteria` là bộ lọc AI vừa điền và kết quả rỗng |

### 5.1. Trạng thái và thao tác

| Thành phần | Trạng thái | Thao tác |
|---|---|---|
| Thẻ AI (FR-017) | Khách (khóa, "Đăng nhập để dùng trợ lý AI"); Còn X/10; Hết lượt; Đang khóa (mm:ss) | Chạm → đăng nhập (khách) hoặc `aiAssistantFragment` |
| Ô nhập | Rỗng (nút gửi khóa); OK; Quá 200 (bộ đếm màu `colorError`, nút gửi khóa); Đang gửi (nút → vòng tải, ô vẫn sửa được nhưng không gửi được) | Gửi bằng nút hoặc phím IME "Tìm" |
| Micro | Ẩn (máy không hỗ trợ / khách); Sẵn sàng; Đang nghe (hiệu ứng, chữ tạm); Bị từ chối (biểu tượng gạch, bấm mở cài đặt) | Lần đầu: hộp giải thích → hộp quyền hệ thống |
| Vùng kết quả | Trống; Hỏi lại (câu + chip + [Tự chọn]); Lạc đề (câu cố định + 2 chip ví dụ, FR-015); Thông báo (lỗi / hết lượt / khóa, kèm nút "Dùng bộ lọc") | Chip hỏi lại → áp bộ lọc, quay về; "Dùng bộ lọc" → bảng lọc thủ công của 020 |
| Bottom sheet chính sách | Một chủ đề (tiêu đề, nội dung, liên kết "Xem chính sách đầy đủ"); Danh sách 4 chủ đề | Chọn chủ đề → đổi nội dung |

Mở **bảng lọc thủ công**: điều hướng tới `filterBottomSheet` của 020 với `SearchCriteria` đã hiểu (điền trước qua `applyExternal` rồi mở bảng). Câu trong ô AI được giữ trong `SavedStateHandle` của `AiAssistantViewModel`, nên quay lại vẫn còn.

### 5.2. Máy tính bảng (`w600dp`)

Theo layout hai ô của 020/021: `AiAssistantFragment` mở dạng dialog rộng 560dp thay vì toàn màn hình (`layout-w600dp/fragment_ai_assistant.xml` cùng ID view).

## 6. Chuỗi (`values/strings_ai.xml`, tiền tố `ai_`)

Mọi câu người dùng thấy nằm ở đây (FR-009). Một số chuỗi chính:

| Key | Nội dung |
|---|---|
| `ai_entry_title` | Tìm bằng AI |
| `ai_entry_example` | tối mai sân Quận 10 dưới 100k có gửi xe |
| `ai_entry_guest` | Đăng nhập để dùng trợ lý AI |
| `ai_input_hint` | Ví dụ: tối mai sân Quận 10 dưới 100k có gửi xe |
| `ai_examples` (string-array) | "tối mai sân Quận 10 dưới 100k", "sân gần tôi chiều nay có gửi xe", "thứ 7 có buổi vãng lai nào ở Q3" |
| `ai_usage_remaining` (plurals) | Còn %1$d/%2$d lượt hôm nay |
| `ai_usage_exhausted` | Bạn đã dùng hết lượt hôm nay, thử lại vào ngày mai |
| `ai_locked` | Trợ lý tạm khóa, thử lại sau %1$s |
| `ai_busy` | Trợ lý đang bận, bạn dùng bộ lọc nhé |
| `ai_off_topic` | Mình chỉ hỗ trợ tìm và đặt sân cầu lông. Bạn thử gõ ví dụ: tối mai sân Quận 10 dưới 100k nhé. |
| `ai_clarify_time` | Bạn muốn chơi lúc nào? |
| `ai_clarify_date` | Bạn muốn chơi ngày nào? |
| `ai_clarify_part` | Bạn muốn chơi buổi nào? |
| `ai_clarify_generic` | Mình chưa hiểu, bạn chọn nhanh nhé |
| `ai_chip_tonight`, `ai_chip_tomorrow_evening`, `ai_chip_weekend`, `ai_chip_manual` | Tối nay / Tối mai / Cuối tuần / Tự chọn |
| `ai_dropped_district`, `ai_dropped_amenity`, `ai_dropped_time_past`, `ai_dropped_date_range`, `ai_dropped_time`, `ai_dropped_price` | Câu ngắn cho từng `AiDropReason`, ví dụ "Chưa hiểu quận bạn nói, bạn chọn trong bộ lọc nhé"; "Giờ này đã qua, bạn chọn giờ khác hoặc ngày mai nhé"; "Chỉ đặt trước được trong 30 ngày" |
| `ai_dropin_dropped` | Danh sách vãng lai không lọc được theo %1$s nên mình đã bỏ |
| `ai_dropin_unavailable` | Danh sách buổi vãng lai chưa sẵn sàng, mình tìm sân thường cho bạn nhé |
| `ai_voice_rationale` | SmashNow cần micro để nghe câu tìm sân của bạn. Bạn vẫn gõ được nếu không cho phép. |
| `ai_voice_no_match` | Không nghe rõ, bạn thử lại hoặc gõ nhé |
| `ai_relax_price` | Nới giá lên %1$s (%2$d sân) |
| `ai_relax_later`, `ai_relax_earlier` | Đổi sang %1$s (%2$d sân) |
| `ai_relax_drop_amenity` | Bỏ "%1$s" (%2$d sân) |

`values/strings_ai_policy.xml` (nội dung chính sách, **B soạn**, A đưa vào):

| Key | Chủ đề |
|---|---|
| `ai_policy_cancellation_title`, `ai_policy_cancellation_body` | Hủy sân: mốc 4 giờ, hủy sớm hoàn 100%, hủy trễ mất cọc hoặc phạt 30% với ví giả lập |
| `ai_policy_deposit_title`, `ai_policy_deposit_body` | Đặt cọc 30% tổng đơn, làm tròn tới nghìn đồng |
| `ai_policy_voucher_title`, `ai_policy_voucher_body` | Voucher |
| `ai_policy_payment_title`, `ai_policy_payment_body` | Thanh toán và ví giả lập (ghi rõ phiên bản học tập, constitution VI) |

Đặt trong `strings.xml` (không phải asset) vì constitution V yêu cầu chuỗi trong resource và nội dung ngắn (mỗi chủ đề 3–5 câu). Liên kết "Xem chính sách đầy đủ" mở trang chính sách mà B chốt ở 050/060 (mặc định `LegalDocumentFragment` của 010 nếu B chưa có trang riêng).

## 7. Trợ năng

- `TextInputLayout` bật `counterEnabled`, `counterMaxLength = 200`; TalkBack đọc "x trên 200 ký tự".
- `ai_usage_remaining` trong `TextView` có `android:accessibilityLiveRegion="polite"`.
- Chip hỏi lại/ví dụ: `contentDescription` đầy đủ (`ai_chip_tomorrow_evening_cd` = "Chọn tối mai, từ 18 giờ đến 22 giờ").
- Micro: `contentDescription` theo trạng thái (`ai_voice_start_cd`, `ai_voice_stop_cd`, `ai_voice_denied_cd`).
- Mọi nút, chip, micro ≥ 48dp.
