# Contract: `SearchCriteria`, sanitizer và cửa vào tìm kiếm (dùng chung 020, 021, 022)

**Package:** `com.example.booking_app.feature.search.domain`. **Người dùng contract:** spec 021 (bản đồ dùng chung bộ lọc), spec 022 (AI điền bộ lọc). Lý do thiết kế: [research R12](../research.md#r12-searchcriteria-dùng-chung-với-spec-022-và-021-fr-010).

## 1. Kiểu dữ liệu

```kotlin
@Serializable
data class SearchCriteria(
    val keyword: String = "",                 // chữ người dùng gõ, chưa chuẩn hóa
    val districtId: String? = null,
    val priceMin: Long? = null,               // VND/giờ, bội số 10_000
    val priceMax: Long? = null,               // null = không giới hạn trên
    val timeWindow: TimeWindow? = null,
    val amenityIds: Set<String> = emptySet(),
    val sort: SortOption = SortOption.RATING,
) {
    /** Có từ khóa hợp lệ hoặc ít nhất một bộ lọc. Sắp xếp không tính. Dùng cho FR-021. */
    fun hasAnyCondition(): Boolean

    /** Bỏ mọi bộ lọc, giữ từ khóa, quận và cách sắp xếp (FR-009, US2-8). */
    fun clearFilters(): SearchCriteria

    /** Chuỗi ổn định để băm ID lịch sử (research R11). */
    fun canonicalKey(): String
}

@Serializable
data class TimeWindow(val date: LocalDate, val start: TimeOfDay, val end: TimeOfDay)

enum class SortOption { RATING, PRICE_ASC, NEAREST }
```

`canonicalKey()` nối các phần theo thứ tự cố định, phân cách `|`: `normalize(keyword)`, `districtId ?: ""`, `priceMin ?: ""`, `priceMax ?: ""`, `yyyyMMdd`, `HHmm`, `HHmm` (rỗng nếu không có khung), `amenityIds` sắp xếp nối bằng `,`, `sort.name`.

**Ghi chú về "Xóa bộ lọc":** spec FR-009 nói giữ từ khóa; US2-8 nói giữ "từ khóa và quận đã chọn". `clearFilters()` theo US2-8 (giữ cả quận), vì quận được chọn ở ô tìm kiếm cùng chỗ với từ khóa. Chip quận vẫn bỏ riêng được.

## 2. Sanitizer

```kotlin
class SearchCriteriaSanitizer @Inject constructor(
    private val catalog: CatalogRepository,
    private val clock: Clock,                  // Asia/Ho_Chi_Minh
) {
    suspend fun sanitize(raw: SearchCriteria): SanitizeResult
}

data class SanitizeResult(val criteria: SearchCriteria, val dropped: List<DroppedField>)
```

| Kiểm tra | Xử lý khi sai | `DroppedField` |
|---|---|---|
| `keyword` > 100 ký tự | Cắt còn 100 | `KEYWORD_TRUNCATED` |
| `districtId` không có hoặc `isActive == false` | Bỏ quận | `UNKNOWN_DISTRICT` |
| Mỗi `amenityId` không có hoặc ẩn | Bỏ riêng tiện ích đó | `UNKNOWN_AMENITY` |
| Giá âm; `priceMin > priceMax` | Bỏ cả khoảng giá | `INVALID_PRICE` |
| Giá không chia hết 10.000 | Làm tròn: `priceMin` xuống, `priceMax` lên | (không báo) |
| `priceMax ≥ 200.000` | `priceMax = null` (không giới hạn trên, khớp thanh trượt "200.000 đ+") | (không báo) |
| `priceMin == 0` | `priceMin = null` | (không báo) |
| `date` trước hôm nay | Bỏ khung giờ | `PAST_DATE` |
| `date` sau hôm nay + 30 ngày (`BookingWindow.ADVANCE_DAYS`) | Bỏ khung giờ | `DATE_OUT_OF_RANGE` |
| Giờ không thẳng mốc 30 phút, `end ≤ start`, hoặc hôm nay mà `start` đã qua | Bỏ khung giờ | `INVALID_TIME` |

Sanitizer chạy ở **ba chỗ**: khôi phục mục lịch sử (FR-023), nhận bộ lọc từ AI (FR-010), và khôi phục `SavedStateHandle` (ngày có thể đã qua khi mở lại app). Bảng lọc thủ công không cho nhập giá trị sai ngay từ giao diện (FR-007, Edge case "Khoảng giá ngược"), nên không cần sanitize.

## 3. Cửa vào cho spec 022 và 021

```kotlin
// SearchViewModel (scope nav graph nav_search)
fun applyExternal(criteria: SearchCriteria, source: CriteriaSource)   // source: AI, HISTORY, DEEP_LINK
val criteria: StateFlow<SearchCriteria>
```

- `applyExternal` gọi sanitizer, thay toàn bộ `criteria`, chạy tìm kiếm như người dùng tự chọn, và lưu lịch sử (đúng định nghĩa "một lần tìm" ở research R11).
- `dropped` không rỗng → snackbar ngắn liệt kê phần bị bỏ, người chơi thấy các chip còn lại để tự chỉnh (Edge case "Bộ lọc do spec 022 điền vào").
- 022 có ý "gần tôi" thì đặt `sort = NEAREST`; ViewModel tự chạy luồng xin quyền vị trí như khi người chơi chọn (US1-5 của 022).
- 022 **không** gọi `VenueSearchEngine` trực tiếp, trừ gợi ý nới điều kiện (US6 của 022), khi đó dùng `VenueSearchEngine.count(criteria)` ở §4.
- 021 đọc `criteria` để vẽ marker và gọi `VenueSearchEngine` với thêm vùng bản đồ (spec 021 mở rộng; 020 không làm).

## 4. Engine tìm kiếm

```kotlin
interface VenueSearchEngine {
    /** Mở phiên mới cho criteria; trang đầu. Hủy coroutine = hủy phiên. */
    suspend fun firstPage(criteria: SearchCriteria, origin: GeoPoint?): Pair<SearchSession, SearchPage>
    /** Trang kế của đúng phiên đó. */
    suspend fun nextPage(session: SearchSession): SearchPage
    /** Số kết quả nếu tính được rẻ (research R3), ngược lại null. */
    suspend fun count(criteria: SearchCriteria): Int?
}
```

Cài đặt: `FirestoreVenueSearchEngine` trong `feature/search/data`, gồm `VenueQueryBuilder` (chọn query và index theo criteria), `VenueMatcher` (lọc trên máy), `AvailabilityEvaluator` (giờ trống, giá khung), `ResultSorter` (S2). Ba lớp cuối là thuần Kotlin, test bằng dữ liệu giả.

Chọn chiến lược:

| Điều kiện | Chiến lược | Query |
|---|---|---|
| `sort == RATING` | S1 | V1–V4 (+ V5–V8 nếu có lọc giá) |
| `sort == PRICE_ASC`, không khung giờ | S1, hai pha | V9–V12, rồi pha `minPricePerHour == null` nếu không lọc giá |
| `sort == PRICE_ASC`, có khung giờ | S2 | V1–V8, cắt `minPricePerHour <= priceMax` nếu có |
| `sort == NEAREST` (có `origin`) | S2 | V1–V8 |

## 5. Nhãn chip và tóm tắt lịch sử

| Bộ lọc | Nhãn chip | Ví dụ |
|---|---|---|
| Quận | tên trong danh mục | "Quận 10" |
| Giá | "≤100k/giờ", "≥50k/giờ", "50k–100k/giờ" | |
| Khung giờ | "Hôm nay", "Mai", hoặc "T2"…"CN dd/MM" + " HH:mm–HH:mm" | "Mai 19:00–21:00" |
| Tiện ích | tên trong danh mục, mỗi tiện ích một chip | "Gửi xe" |

Mục lịch sử (wireframe bước 4): dòng 1 = từ khóa, nếu không có thì tên quận, nếu không có thì "Tất cả sân"; dòng 2 = các nhãn còn lại nối bằng " · " cộng cách sắp xếp khác mặc định ("Giá thấp đến cao"), hoặc "Không có bộ lọc". Nhãn ngày tương đối ("Mai") tính lại lúc hiển thị.
