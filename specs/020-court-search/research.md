# Research: Tìm kiếm sân (spec 020)

**Ngày:** 2026-10-19 · **Người làm:** A · **Nguồn kiểm chứng:** tài liệu Firestore chính thức tra qua MCP `developerknowledge` (`documents/firebase.google.com/docs/firestore/query-data/queries`, `.../order-limit-data`, `.../multiple-range-fields`, `.../pricing`), đặc tả CSDL, plan 010, 040, 070, 111.

Mỗi mục ghi: quyết định, lý do, phương án đã loại.

---

## R1. Giới hạn truy vấn Firestore áp vào bộ lọc của spec 020

**Đã kiểm chứng (tài liệu Firestore, Standard edition):**

| Giới hạn | Ảnh hưởng tới spec 020 |
|---|---|
| Tối đa **một** `array-contains` mỗi nhánh OR; không ghép `array-contains` với `array-contains-any` | `nameKeywords` (tên) và `amenityIds` (tiện ích) không lọc chung được trong một query |
| `array-contains-any` là phép **OR** (tối đa 30 giá trị) | FR-008 cần "có **đủ tất cả** tiện ích" (AND), nên `array-contains-any` cho sai kết quả |
| `orderBy(field)` chỉ trả document **có** trường đó; lọc bất đẳng thức cũng ngầm `orderBy` và lọc tồn tại | Cơ sở thiếu `ratingAvg` hoặc `minPricePerHour` sẽ **biến mất** khỏi kết quả sắp xếp (vi phạm FR-011: "chưa có đánh giá/giá đứng cuối") |
| Lọc khoảng trên nhiều trường được hỗ trợ (tối đa 10 trường), index theo thứ tự: trường bằng → `orderBy` → các trường khoảng còn lại | Lọc giá (`minPricePerHour` khoảng) **kết hợp được** với sắp xếp theo đánh giá, cần composite index riêng |
| Query trả 0 kết quả vẫn tính **1 lượt đọc**; `count()` tính 1 lượt mỗi 1.000 index entry | Mỗi lần kiểm tra giờ trống của một cơ sở tốn ít nhất 1 lượt, kể cả khi trống hoàn toàn |
| Spark: **50.000 lượt đọc/ngày** | Xem ước lượng ở R4 |

## R2. Cách chạy tìm kiếm: (a) query một điều kiện chính + lọc trên máy

**Quyết định:** chọn **(a)**. Firestore lọc những điều kiện biểu diễn được trong một query; phần còn lại lọc trên máy trong một pipeline thuần Kotlin (`VenueMatcher`, `AvailabilityEvaluator`).

| Điều kiện | Chạy ở đâu |
|---|---|
| `status == ACTIVE` (FR-004) | Firestore (bắt buộc, Rules không phải bộ lọc nên query phải có điều kiện này) |
| Quận `districtId ==` | Firestore |
| Từ khóa: **một** token dài nhất `nameKeywords array-contains` | Firestore |
| Các token còn lại của từ khóa | Trên máy (so với tên đã chuẩn hóa) |
| Khoảng giá khi **không** có khung giờ (`minPricePerHour`) | Firestore |
| Khoảng giá khi **có** khung giờ | Firestore cắt bớt bằng `minPricePerHour <= priceMax` (giá khung luôn ≥ giá thấp nhất), rồi máy tính giá khung chính xác |
| Tiện ích (đủ tất cả) | Trên máy (`amenityIds.containsAll`) |
| Khung giờ trong giờ mở cửa | Trên máy (`openTime`, `closeTime` có sẵn trong document) |
| Khung giờ còn trống | Trên máy, sau khi đọc `slots` (R4) |
| Sắp xếp đánh giá / giá không có khung giờ | Firestore `orderBy` |
| Sắp xếp gần nhất / giá có khung giờ | Trên máy (R3) |

**Lý do:**
- Dữ liệu demo nhỏ (vài chục cơ sở, mỗi quận dưới 30). Lọc trên máy rẻ và dễ test bằng unit test (nguyên tắc IV).
- Không đổi schema của D, không thêm Edge Function, không thêm độ trễ khởi động Edge Function.
- Cùng một pipeline dùng lại được cho spec 021 (bản đồ, thêm điều kiện vùng) và 022 (AI điền `SearchCriteria`).

**Phương án đã loại:**
- **(b) đổi `amenityIds` thành map `amenities.{id} = true`** để lọc bằng nhiều điều kiện `==`: đúng ngữ nghĩa AND, nhưng mỗi tổ hợp tiện ích × sắp xếp × quận × từ khóa cần một composite index riêng (số index tăng theo tổ hợp, Spark giới hạn 200 index). Phải đổi schema của D và Rules của 111. Lợi ích nhỏ vì số cơ sở mỗi truy vấn đã ít. Giữ lại làm hướng mở rộng nếu dữ liệu lớn.
- **(c) Edge Function `search-venues`**: một lần gọi làm được cả lọc giờ trống, nhưng Edge Function phải đọc Firestore qua REST bằng service account, **lượt đọc vẫn tính như cũ**; thêm độ trễ khởi động lạnh (Supabase Free) và một lần nhảy mạng; khách chưa đăng nhập phải gọi được nên phải tắt `verify_jwt` và tự chống lạm dụng. Tìm kiếm không phải thao tác nhạy cảm (nguyên tắc II chỉ bắt buộc Edge Function cho thao tác nhạy cảm). Bị loại.

## R3. Hai chiến lược phân trang (FR-016) khi có lọc trên máy

Lọc trên máy làm một trang Firestore 20 document có thể còn ít hơn 20 kết quả. Pipeline có hai chiến lược, chọn tự động theo `SearchCriteria`:

**S1. Firestore sắp xếp (`SERVER_ORDERED`)**: khi sắp xếp là "Đánh giá cao nhất", hoặc "Giá thấp đến cao" mà **không** có khung giờ.
- Query có `orderBy` theo khóa sắp xếp, đọc từng lô **25** document bằng `startAfter(lastSnapshot)`.
- Mỗi lô qua `VenueMatcher` (và `AvailabilityEvaluator` nếu có khung giờ). Đọc tiếp cho tới khi đủ **20** kết quả, hết dữ liệu, hoặc đã đọc **4 lô** (100 document) trong lần tải đó.
- Chạm giới hạn 4 lô mà chưa đủ 20: trả những gì đã có, lần cuộn tiếp theo đọc tiếp từ con trỏ. Giới hạn này giữ SC-001 (3 giây) và quota.
- Con trỏ (`lastSnapshot`) nằm trong `SearchSession`; đổi điều kiện thì tạo phiên mới.

**S2. Sắp xếp trên máy (`CLIENT_ORDERED`)**: khi sắp xếp là "Gần nhất", hoặc "Giá thấp đến cao" **có** khung giờ (giá khung chỉ biết sau khi tính).
- Đọc toàn bộ document `venues` của tập ứng viên (theo `ratingAvg` để có thứ tự ổn định), **tối đa 150** cơ sở (`limit(151)` để biết có vượt không). Bước này chỉ tốn 1 lượt đọc mỗi cơ sở.
- Chạy các bộ lọc rẻ (`VenueMatcher`), rồi tính **cận dưới** của khóa sắp xếp cho từng cơ sở: "Gần nhất" là khoảng cách (chính xác, tính ngay); giá có khung giờ là `minPricePerHour` (giá khung luôn ≥ giá thấp nhất mỗi giờ, cơ sở `null` đứng cuối). Sắp xếp theo cận dưới.
- **Kiểm tra giờ trống lười theo thứ tự cận dưới**: `AvailabilityEvaluator` chạy từng đợt 6 cơ sở; một kết quả đã xác nhận được đưa ra trang khi khóa chính xác của nó ≤ cận dưới của cơ sở kế tiếp chưa kiểm tra. Dừng khi đủ 20. Thứ tự cuối cùng giống hệt cách "kiểm tra hết rồi sắp xếp" nhưng chỉ đọc `slots`/`priceRules`/`courts` cho số cơ sở cần.
- Phần còn lại giữ trong `SearchSession` để chia trang 20 tiếp theo (vẫn tải thêm khi cuộn, FR-016).
- Vượt 150: hiện thông báo "Có nhiều sân, hãy chọn quận để kết quả đầy đủ hơn". Với quy mô đồ án điều này không xảy ra; ghi thành giả định cần thêm vào spec ([R15](#r15-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác)).

**Số đếm "N sân phù hợp" và nút "Xem N sân":**
- S2 không có khung giờ ("Gần nhất" chỉ với bộ lọc rẻ): biết chính xác sau khi lọc.
- S2 có khung giờ: kiểm tra lười nên chưa biết tổng; xử lý như S1 có lọc trên máy ("Kết quả phù hợp", "Xem kết quả").
- S1 không có điều kiện lọc trên máy (không tiện ích, từ khóa ≤ 1 token, không khung giờ): dùng `count()` của cùng query (1 lượt đọc).
- S1 có lọc trên máy: chưa biết tổng tới khi đọc hết. Tiêu đề hiện "Kết quả phù hợp" (không có số), nút trong bảng lọc hiện "Xem kết quả" thay cho "Xem N sân". Lệch nhỏ so với wireframe, không trái spec (spec không yêu cầu số đếm).

**Phương án đã loại:** luôn đọc toàn bộ ứng viên rồi phân trang trong bộ nhớ: đơn giản hơn nhưng mỗi lần đổi điều kiện đọc lại toàn bộ cơ sở; trái ràng buộc "phân trang danh sách" của constitution khi dữ liệu tăng.

## R4. Lọc khung giờ còn trống (FR-006) và giá theo khung (FR-005)

**Quyết định:** với mỗi cơ sở đã qua các bộ lọc rẻ (từ khóa, tiện ích, giờ mở cửa, cắt giá), `AvailabilityEvaluator` làm:

1. **Bảng giá** `venues/{id}/priceRules` → tính giá khung bằng `SlotPricing` (R5). Thiếu rule cho bất kỳ khung 30 phút nào thì giá "không xác định" → không khớp bộ lọc giá, đứng cuối khi sắp xếp theo giá, thẻ hiện "Liên hệ" (Edge case của spec). Giá ngoài khoảng → loại, **không** đọc tiếp.
2. **Sân con** `venues/{id}/courts` với `isActive == true`.
3. **Khung đang bận** `slots` với `venueId ==`, `date ==`, `startAt >= windowStart`, `startAt < windowEnd`. Mỗi slot dài 30 phút và thẳng mốc, nên mọi slot chồng lên khung đều có `startAt` nằm trong khung.
4. Một slot là **bận** khi `status` là `BOOKED` hoặc `BLOCKED`, hoặc `HOLD` với `holdExpiresAt > now` (HOLD hết hạn coi là trống, constitution III). Sân con trống = sân hoạt động không có slot bận nào trong khung. Khớp khi có ít nhất 1 sân trống. Số sân trống hiện trên thẻ ("3 sân trống khung này" như wireframe).

**Giảm lượt đọc:**
- `priceRules` và `courts` ít đổi: lưu trong bộ nhớ (`VenueStaticCache`) 10 phút cho mỗi phiên app. Lần tìm sau chỉ đọc `slots`.
- Chạy song song tối đa **6** cơ sở cùng lúc (`Semaphore`), mỗi cơ sở 2–3 lời gọi.
- Trong S1 chỉ kiểm tra cơ sở của lô đang cần (lười), không kiểm tra toàn bộ.
- `now` lấy theo giờ máy. Lệch giờ vài giây không đáng kể vì spec chỉ cam kết trạng thái lúc tìm; kiểm tra cuối cùng ở transaction đặt sân (spec 040, Edge case "Kết quả thay đổi sau khi tìm").

**Ước lượng lượt đọc** (lô 25, mỗi cơ sở ~3 rule, ~4 sân con, ~2 slot bận trong khung):

| Kịch bản | Lần đầu | Lần sau (đã cache rule/sân) |
|---|---|---|
| Không khung giờ, 1 trang | 25 (+1 `count`) | 25 |
| Có khung giờ, chọn quận (~15 ứng viên, ~10 qua lọc rẻ) | 15 + 10 × (3 + 4 + 2) ≈ **105** | 15 + 10 × 2 ≈ **35** |
| Có khung giờ, không chọn quận, S2 lười (~60 cơ sở, ~40 qua lọc rẻ, kiểm tra ~25 tới khi đủ 20) | 60 + 25 × 9 ≈ 285 | 60 + 25 × 2 ≈ 110 |
| Tệ nhất S2 (150 ứng viên, ít cơ sở còn trống nên phải kiểm tra hết) | 150 + 150 × 9 = 1.500 | 150 + 150 × 2 = 450 |
| Danh mục quận/tiện ích, mỗi phiên app | ~30 | 0 |

Giả sử cả nhóm thử và demo 300 lần tìm/ngày, trung bình 80 lượt → **24.000 lượt/ngày**, dưới hạn 50.000 nhưng chung quota với các tính năng khác. Biện pháp: cache ở trên, debounce gõ phím (R10), không dùng listener realtime cho kết quả tìm kiếm (chỉ `get()` một lần), và ghi số lượt đọc thực tế khi chạy [quickstart §4](quickstart.md#4-đo-thời-gian-và-lượt-đọc-sc-001-quota).

**Ước lượng thời gian (SC-001, 4G ~100 ms RTT):** query venues 300–600 ms; mỗi lượt kiểm tra cơ sở 2–3 lời gọi song song qua cùng kết nối gRPC ~200–400 ms; 10 cơ sở / 6 luồng ≈ 2 đợt → **~1,2–1,8 giây** cho trang đầu có khung giờ, lần sau nhanh hơn nhờ cache. Đo lại theo quickstart §4.

**Tệ nhất (S2, 150 ứng viên, gần như kín lịch):** 150 cơ sở / 6 luồng = 25 đợt × ~300 ms ≈ 7–8 giây lần đầu, vượt SC-001. Giảm thiểu: (1) kiểm tra lười theo cận dưới ở trên nên thường dừng sớm; (2) hiện ngay các kết quả đã xác nhận, phần sau tải tiếp khi cuộn (FR-016 cho phép trang ít hơn 20); (3) quá 4 đợt (24 cơ sở) mà chưa đủ 20 thì trả trang hiện có như S1; (4) giới hạn 150 kèm gợi ý chọn quận. Quy mô đồ án dưới 100 cơ sở; kịch bản 6 ở quickstart §4 đo riêng trường hợp này.

**Liên hệ constitution III:** tìm kiếm chỉ đọc `slots`, không ghi và không giữ chỗ. Kết quả "còn trống" là ảnh chụp lúc tìm; chống đặt trùng vẫn do transaction `create-booking` của spec 040 bảo đảm, nên coi HOLD hết hạn là trống ở đây không tạo rủi ro đặt trùng.

**Phương án đã loại:**
- **Bản tóm tắt trống/bận theo ngày** (`venues/{id}/days/{yyyyMMdd}` với bitmask mỗi sân) do transaction đặt sân của B cập nhật: chỉ 1 lượt đọc mỗi cơ sở, nhưng tạo document "nóng" mà mọi transaction đặt sân của cơ sở cùng ghi vào (tăng tranh chấp, đụng nguyên tắc III), B phải sửa `create-booking`, `expire-holds`, khóa slot của 111/112. Bị loại ở giai đoạn này, ghi lại làm hướng tối ưu.
- **Sao chép `priceRules` và danh sách sân con vào document `venues`**: bớt 2 lượt đọc mỗi cơ sở lần đầu, nhưng D phải giữ hai bản đồng bộ. Cache 10 phút đạt gần như cùng hiệu quả. Bị loại.

## R5. Logic giá và lưới giờ dùng chung với spec 040 (B)

**Quyết định:** đưa phần thuần Kotlin của 040 lên `core/` để 020, 021, 030, 040 cùng dùng, thay vì mỗi spec tự viết:

| Thành phần | Package | Việc |
|---|---|---|
| `PriceRule` (model) | `core/pricing` | Như data-model của 040, kèm `pricePerSlot = pricePerHour / 2` |
| `SlotPricing.priceForWindow(rules, date, start, end): Long?` | `core/pricing` | Cộng giá từng khung 30 phút; `null` khi có khung không có rule. 040 dùng để tính tổng tiền (thay `CalculateTotalPriceUseCase` tự viết) |
| `SlotPricing.perHour(total, start, end): Long` | `core/pricing` | Quy ra đ/giờ cho FR-005: `total × 60 / phút`, làm tròn xuống tới đồng (luôn chia hết khi giá chia hết cho 2.000) |
| `TimeOfDay` (`HHmm` ⇄ phút), `SlotMath` (bước 30 phút, căn mốc, liệt kê khung) | `core/schedule` | Dùng chung cho bộ chọn giờ, lưới giờ 040, khóa slot 111 |
| `BookingWindow.ADVANCE_DAYS = 30`, `ZONE = Asia/Ho_Chi_Minh` | `core/schedule` | FR-006, FR-007 và 040 FR-001 |
| `SlotId.of(courtId, date, start)` | `core/schedule` | ID tất định `{courtId}_{yyyyMMdd}_{HHmm}` |

**Lý do:** spec 020 (Assumptions) yêu cầu giá trên thẻ bằng giá ở màn hình đặt sân. Hai bản code riêng sẽ lệch nhau. Constitution I cấm import `feature/booking/domain` từ `feature/search`.

**Điểm phải chốt với B, D trước khi code:** quy ước `daysOfWeek`. Research R4 của 040 viết "1 = Chủ nhật … 7 = Thứ bảy" (kiểu `java.util.Calendar`), đặc tả CSDL chỉ ghi "1–7". Đề xuất dùng **ISO-8601 (1 = Thứ hai … 7 = Chủ nhật)** để khớp `java.time.DayOfWeek.value` và TypeScript dễ đổi. Chốt một quy ước và ghi vào mục 4.3 đặc tả CSDL.

## R6. Tìm theo tên: dùng `nameKeywords` (chốt mục 9 điểm 6 đặc tả CSDL)

**Quyết định:** dùng `nameKeywords`, **không** dùng dịch vụ tìm kiếm ngoài.

**Lý do:** dịch vụ ngoài (Algolia, Typesense, Meilisearch) cần tài khoản riêng, đồng bộ dữ liệu bằng trigger (Spark không có) hoặc Edge Function, và nhiều dịch vụ yêu cầu thẻ: trái ràng buộc "không thêm dịch vụ trả phí hoặc bắt buộc gắn thẻ". FR-001 chỉ cần khớp phần đầu của từ, `nameKeywords` làm được.

**Cách sinh (`KeywordGenerator`, `core/text`):**
1. Chuẩn hóa (`VietnameseNormalizer.normalize`): chữ thường theo `Locale.ROOT`; `đ`/`Đ` → `d`; tách dấu bằng Unicode NFD rồi bỏ ký tự dấu kết hợp (`\p{Mn}`); mọi ký tự không phải chữ cái Latin hoặc chữ số thành khoảng trắng; gộp khoảng trắng.
2. Tách thành từ. Với mỗi từ, sinh mọi tiền tố dài từ **2** tới **min(độ dài từ, 15)** ký tự. Từ 1 ký tự giữ nguyên.
3. Bỏ trùng, sắp xếp, tối đa **60** phần tử (tên dài hơn thì cắt phần cuối).

Ví dụ "Sân cầu lông Hòa Bình" → từ `san`, `cau`, `long`, `hoa`, `binh` → `["bi","bin","binh","ca","cau","ho","hoa","lo","lon","long","sa","san"]`.

**Cách tìm (`KeywordQuery`):** chuẩn hóa từ khóa người chơi gõ bằng cùng hàm. Tổng độ dài (bỏ khoảng trắng) < 2 thì bỏ qua tìm theo tên (FR-001). Token dài nhất (cắt còn 15 ký tự nếu dài hơn) dùng cho `array-contains`. Mọi token đều phải là tiền tố của một từ trong tên đã chuẩn hóa (`VenueMatcher`), nên "oa bi" không khớp (US1-2) còn "hoa binh", "HÒA", "binh" khớp (US1-1).

**Hai bản cài đặt, một bộ vector test:**
- Kotlin: `core/text/KeywordGenerator.kt`. D gọi khi chủ sân lưu cơ sở (spec 111).
- TypeScript: `supabase/functions/_shared/search-keywords.ts`. D dùng trong `review-owner-application` khi tạo cơ sở từ hồ sơ (spec 110/120).
- Cả hai chạy chung file `supabase/functions/_shared/search-keywords.vectors.json` (đầu vào → đầu ra mong đợi) để không lệch nhau. Xem [contracts/keyword-normalization.md](contracts/keyword-normalization.md).

**Rủi ro chấp nhận:** `nameKeywords` không phải trường 🔒, chủ sân về lý thuyết có thể ghi từ khóa sai cho **cơ sở của mình**. Hậu quả chỉ là cơ sở đó khó tìm, không ảnh hưởng người khác. Rules của D kiểm tra kiểu (`list`, ≤ 60 phần tử). Không cần Edge Function.

## R7. Sắp xếp theo đánh giá và giá khi thiếu trường (FR-011)

**Quyết định:**
- **Đánh giá:** `orderBy(ratingAvg DESC).orderBy(ratingCount DESC)`. Mọi cơ sở **phải có** `ratingAvg = 0.0`, `ratingCount = 0` ngay khi tạo (D ghi). Điểm hợp lệ là 1–5, nên cơ sở chưa có đánh giá (0) tự đứng cuối.
- **Giá (không khung giờ):** mọi cơ sở **phải có** trường `minPricePerHour`: số khi có bảng giá, `null` khi chưa có. Query hai pha:
  - Pha 1: `minPricePerHour >= priceMin ?: 0` (và `<= priceMax` nếu có), `orderBy(minPricePerHour ASC)`. Lọc khoảng chỉ khớp kiểu số nên `null` không vào.
  - Pha 2 (chỉ khi **không** có bộ lọc giá): `minPricePerHour == null`, theo `ratingAvg DESC`. Chạy sau khi pha 1 hết dữ liệu, nên cơ sở "Liên hệ" đứng cuối.
- **Đánh giá + lọc giá:** lọc khoảng `minPricePerHour` cùng `orderBy(ratingAvg)`. Firestore cho phép (lọc khoảng nhiều trường), cần index có `minPricePerHour` sau các trường `orderBy` (R8).

**Lý do:** tài liệu Firestore xác nhận `orderBy` loại document thiếu trường. Gán giá trị mặc định là cách tài liệu khuyến nghị.

**Phương án đã loại:** thêm trường `hasPrice`/`priceSortKey` (thêm trường 🔒 mà D phải giữ đồng bộ; `null` đã đủ); dùng giá trị canh (`Long.MAX_VALUE`) cho cơ sở chưa có giá (dễ lọt vào bộ lọc "≥ X" và lộ ra giao diện nếu quên kiểm tra).

**Phụ thuộc D:** ai cập nhật `minPricePerHour` 🔒 khi chủ sân sửa bảng giá đang **chưa chốt** (data-model 111 ghi "quyết định dữ liệu còn mở"). Spark không có trigger, nên phải là Edge Function (ví dụ D gom việc ghi `priceRules` vào một Edge Function, hoặc thêm `recompute-venue-price` app gọi sau khi lưu). Khi trường chưa đúng, thẻ có thể hiện sai giá "từ X". Đây là việc phối hợp ở plan.

## R8. Index cần tạo

Thứ tự trường theo tài liệu Firestore: trường bằng → trường `orderBy` → trường khoảng còn lại. `nameKeywords` dùng `arrayConfig: CONTAINS`. Viết vào `firestore.indexes.json` (hiện rỗng).

| # | Collection | Trường | Query |
|---|---|---|---|
| V1 | `venues` | `status`, `ratingAvg DESC`, `ratingCount DESC` | Mặc định, đánh giá; tập ứng viên S2 |
| V2 | `venues` | `status`, `districtId`, `ratingAvg DESC`, `ratingCount DESC` | + quận |
| V3 | `venues` | `status`, `nameKeywords CONTAINS`, `ratingAvg DESC`, `ratingCount DESC` | + từ khóa |
| V4 | `venues` | `status`, `districtId`, `nameKeywords CONTAINS`, `ratingAvg DESC`, `ratingCount DESC` | + quận + từ khóa |
| V5–V8 | `venues` | như V1–V4, thêm `minPricePerHour ASC` ở cuối | Đánh giá + lọc giá; S2 có cắt giá |
| V9 | `venues` | `status`, `minPricePerHour ASC` | Giá thấp → cao (pha 1) |
| V10 | `venues` | `status`, `districtId`, `minPricePerHour ASC` | + quận |
| V11 | `venues` | `status`, `nameKeywords CONTAINS`, `minPricePerHour ASC` | + từ khóa |
| V12 | `venues` | `status`, `districtId`, `nameKeywords CONTAINS`, `minPricePerHour ASC` | + quận + từ khóa |
| S1 | `slots` | `venueId`, `date`, `startAt ASC` | Giờ trống của một cơ sở trong khung (mở rộng index `venueId, date` của 040, một index phục vụ cả hai) |
| H1 | `searchHistory` (collection, không group) | `searchedAt DESC` | Single-field mặc định, không cần khai báo |

Pha 2 của sắp xếp giá (`minPricePerHour == null` + `ratingAvg DESC`) dùng lại V1–V4 nhờ index merging cho trường bằng; nếu Firestore báo thiếu index thì thêm theo link trong log lỗi. Emulator **không** kiểm tra index, nên quickstart §3 có bước chạy với project thật để bắt lỗi thiếu index trước demo. Tổng 13 index, xa hạn 200.

Đặc tả CSDL mục 6 hiện có index `status, districtId, minPricePerHour` và `status, ratingAvg desc`: thay bằng bảng trên. Dòng `venues: geohash` để spec 021 quyết định.

## R9. Sắp xếp "Gần nhất" và vị trí (FR-011 đến FR-014)

**Quyết định:**
- Dùng S2: lấy tập ứng viên đã lọc, tính khoảng cách đường chim bay bằng haversine trên máy (`core/location/GeoDistance`), sắp xếp tăng dần.
- Lấy vị trí bằng `LocationManagerCompat.getCurrentLocation` của `androidx.core` (đã có trong app), provider `NETWORK` rồi `GPS`, timeout 10 giây. **Không thêm** `play-services-location`.
- Xin cả `ACCESS_COARSE_LOCATION` và `ACCESS_FINE_LOCATION` cùng lúc (Android 12+ cho người dùng chọn "Gần đúng"); chỉ cần một trong hai được cấp (FR-012).
- Luồng: chọn "Gần nhất" → nếu chưa có quyền thì hiện hộp giải thích ngắn (nguyên tắc VI) → hộp xin quyền của hệ thống → kết quả:

| Kết quả | Hành vi |
|---|---|
| Được cấp, lấy được vị trí | Áp dụng "Gần nhất", thẻ hiện khoảng cách |
| Từ chối | Trở về cách sắp xếp trước, snackbar "Cần quyền vị trí để xếp theo khoảng cách" kèm nút "Cài đặt" (mở `ACTION_APPLICATION_DETAILS_SETTINGS`) (US3-5) |
| Được cấp nhưng tắt vị trí hoặc quá 10 giây | Giữ cách sắp xếp trước, báo "Không lấy được vị trí" (US3-6) |

- Vị trí chỉ nằm trong `SearchViewModel` (bộ nhớ). Không đưa vào `SearchCriteria`, không ghi lịch sử, không gửi server (FR-014). Khi đã có quyền từ trước, mọi chế độ sắp xếp đều hiện khoảng cách (FR-013).
- Định dạng (`DistanceFormatter`): < 1.000 m → "850 m" (làm tròn tới 10 m); ≥ 1 km → "1,2 km" (1 chữ số thập phân, dấu phẩy kiểu Việt Nam).
- `LocationProvider` và `GeoDistance` đặt ở `core/location` để spec 021 dùng lại.

**Phương án đã loại:**
- **Query geohash (GeoFire utils):** cần bán kính, mà "Gần nhất" của spec 020 là sắp xếp mọi kết quả chứ không giới hạn bán kính. Query geohash là lọc khoảng trên `geohash`, không ghép được với sắp xếp theo đánh giá/giá mà không thêm index; thêm thư viện `geofire-android-common`. Để spec 021 dùng cho bản đồ theo vùng.
- **`play-services-location` (Fused):** chính xác và tiết kiệm pin hơn, nhưng thêm phụ thuộc. Spec 021 có thể thêm nếu cần; khi đó chỉ thay cài đặt của `LocationProvider`.

## R10. Cập nhật theo điều kiện mới nhất (FR-018), giữ trạng thái (FR-017), mất mạng (FR-020)

**Quyết định:**
- `SearchViewModel` giữ `criteria: MutableStateFlow<SearchCriteria>`. Ô gõ tên đi qua `debounce(300 ms)` rồi mới cập nhật `criteria`; các thao tác khác (chip, bảng lọc, sắp xếp) cập nhật ngay.
- `criteria.distinctUntilChanged().flatMapLatest { engine.firstPage(it) }`: điều kiện mới hủy coroutine của điều kiện cũ, nên kết quả cũ không bao giờ ghi đè (FR-018).
- Tải thêm (`loadMore`) gắn với `sessionId` của `SearchSession` hiện tại; phiên đã bị thay thì kết quả bị bỏ.
- **FR-017:** `SearchViewModel` scope theo nav graph `nav_search` (`hiltNavGraphViewModels`), nên mở chi tiết sân rồi quay lại vẫn còn danh sách và con trỏ trang. Khách đăng nhập giữa chừng cũng giữ được, vì spec 010 mở `nav_auth` chồng lên rồi `popBackStack` (010 research R15, bổ sung sau `/speckit-analyze` mục G1). `RecyclerView` dùng `StateRestorationPolicy.PREVENT_WHEN_EMPTY` để giữ vị trí cuộn. `SearchCriteria` được lưu vào `SavedStateHandle` (JSON qua kotlinx-serialization) để sống qua khi hệ thống hủy tiến trình.
- **FR-020:** dùng `get()` mặc định của Firestore (cache offline bật sẵn trên Android). Mất mạng thì SDK trả kết quả trong cache nếu có, `metadata.isFromCache == true` → hiện thông báo "Không có kết nối, kết quả có thể chưa cập nhật". Không có cache hoặc lỗi → trạng thái lỗi kèm "Thử lại". Mọi lời gọi bọc `awaitWithTimeout(15 s)` của `core/network` (plan 010 R13); lỗi đổi thành `AppError`.
- Kết quả tìm kiếm **không** dùng listener realtime (spec chỉ cam kết trạng thái lúc tìm). Đỡ quota và không phải gỡ listener.

## R11. Lịch sử tìm kiếm (FR-021 đến FR-025)

**Quyết định:**
- **ID tất định** = 20 ký tự hex đầu của SHA-256 trên chuỗi chuẩn hóa của `SearchCriteria` (từ khóa đã chuẩn hóa, quận, khoảng giá, ngày + giờ, tiện ích đã sắp xếp, cách sắp xếp). Lặp lại cùng tổ hợp thì `set()` ghi đè đúng document đó với `searchedAt` mới → tự lên đầu, không trùng (FR-022, US4-4).
- **Khi nào lưu (một "lần tìm"):** bấm tìm trên bàn phím, áp dụng bảng lọc, chọn quận, bỏ chip, đổi sắp xếp, chọn một mục lịch sử, hoặc mở một thẻ kết quả. **Không** lưu theo từng ký tự gõ. Chỉ lưu khi có từ khóa hợp lệ hoặc ít nhất một bộ lọc (sắp xếp đơn thuần không tính là bộ lọc) và người dùng đã đăng nhập (`SessionState.SignedIn`).
- **Giữ 10 mục:** đọc `orderBy(searchedAt DESC).limit(10)`. Sau khi ghi, app đọc mục thứ 11 trở đi (`startAfter` mục thứ 10, `limit(5)`) và xóa. Nếu bước dọn lỗi (mất mạng) thì lần sau dọn tiếp; danh sách hiển thị luôn chỉ 10 mục vì có `limit(10)`.
- **Khôi phục (FR-023):** chạy `SearchCriteriaSanitizer` (R12). Phần bị bỏ hiện snackbar ngắn ("Ngày đã qua, vui lòng chọn lại" cho khung giờ).
- **Xóa:** từng mục `delete()`; "Xóa tất cả" có hộp xác nhận, xóa bằng `WriteBatch` (≤ 10 document).
- **Rules:** chỉ chủ tài khoản đã khởi tạo (`isSelf(uid) && isInitialized()`) được đọc/ghi/xóa; kiểm tra danh sách trường, kiểu, độ dài, và `searchedAt == request.time` ([contracts/security-rules-and-indexes.md](contracts/security-rules-and-indexes.md)).
- **Xóa tài khoản:** đã có. Contract của spec 011 bước 7 `deleteSubcollections` xóa `searchHistory` (đã đối chiếu `specs/011-account-profile/contracts/edge-functions-account.md`). Spec 020 chỉ thêm một test kiểm chứng trong quickstart.
- **Khách (FR-027):** không gọi repository lịch sử, ô tìm kiếm hiện gợi ý "Đăng nhập để lưu lịch sử tìm kiếm".

**Phương án đã loại:** lưu lịch sử trong DataStore trên máy: không theo tài khoản, không xóa được cùng tài khoản khi đổi máy, và đặc tả CSDL đã chốt `users/{uid}/searchHistory`. Lưu cả danh sách trong một document (mảng 10 phần tử): tránh được bước dọn nhưng Rules khó kiểm tra từng phần tử và mỗi lần ghi phải đọc–sửa–ghi cả mảng.

## R12. `SearchCriteria` dùng chung với spec 022 và 021 (FR-010)

**Quyết định:**
- `SearchCriteria` (data class bất biến, `@Serializable`) và `SearchCriteriaSanitizer` đặt ở `feature/search/domain`. Spec 021, 022 cùng nhóm tính năng 02 nên cùng package `feature/search`, không vi phạm quy tắc cấm import chéo (nguyên tắc I).
- 022 không gọi Firestore để tìm. 022 tạo `SearchCriteria` từ JSON của AI rồi gọi `SearchViewModel.applyExternal(criteria, source = AI)`. Hàm này chạy sanitizer, cập nhật chip, chạy tìm kiếm như khi người dùng tự chọn.
- Sanitizer bỏ từng giá trị sai, không làm hỏng phần còn lại, và trả danh sách phần bị bỏ để giao diện báo: ngày đã qua hoặc quá 30 ngày; giờ không thẳng mốc 30 phút, kết thúc ≤ bắt đầu, giờ bắt đầu đã qua (hôm nay); quận/tiện ích không có hoặc `isActive == false` trong danh mục; khoảng giá ngược, âm, không chia hết 10.000 (làm tròn), `priceMax ≥ 200.000` hiểu là "không giới hạn trên"; từ khóa > 100 ký tự thì cắt.
- Spec 030 nhận `venueId` và tùy chọn `date`, `startTime`, `endTime` qua Safe Args (spec 021 yêu cầu mang theo khung giờ). Không truyền `SearchCriteria` sang 030.

Chi tiết: [contracts/search-criteria.md](contracts/search-criteria.md).

## R13. Quyền đọc cho khách chưa đăng nhập (FR-026, FR-027)

**Quyết định:**

| Dữ liệu | Rules đề xuất | Lý do |
|---|---|---|
| `venues/{id}` | Mọi người đọc khi `resource.data.status == 'ACTIVE'`; chủ cơ sở và admin đọc mọi trạng thái | FR-004. Query của app luôn có `status == 'ACTIVE'` để Rules chứng minh được |
| `venues/{id}/courts`, `priceRules` | **Mọi người đọc**, không kiểm tra trạng thái cơ sở cha | Không có dữ liệu cá nhân. Kiểm tra cha cần `get()` trong Rules, mỗi lần tốn thêm 1 lượt đọc. Cơ sở ẩn vẫn không xuất hiện vì `venues` đã chặn |
| `slots/{id}` | **Mọi người đọc** | Xem dưới |
| `districts`, `amenities` | Mọi người đọc; chỉ admin ghi | Danh mục công khai |

**Về FR-026 "không lộ ai đã đặt":** Rules không giấu được từng trường, nên document `slots` mở công khai cũng lộ `holdBy` (uid) và `bookingId`. Đây là **ID mờ**:
- `users/{uid}` chỉ chủ tài khoản và admin đọc (plan 010 R10), nên không tra ra tên hay số điện thoại từ uid.
- `bookings` chỉ chủ đơn và chủ sân đọc.
- Hiện đặc tả CSDL mục 7 đã cho **mọi người chơi đã đăng nhập** đọc `slots`, mà đăng ký tài khoản là miễn phí, nên mở cho khách không làm tăng mức lộ thực tế.

Kết luận: chấp nhận, kèm đề xuất sửa câu chữ FR-026 thành "không lộ thông tin cá nhân của người đã đặt" ([R15](#r15-những-chỗ-cần-sửa-spec-hoặc-tài-liệu-khác)). Nếu nhóm muốn chặt hơn, B có thể bỏ `holdBy`/`bookingId` khỏi document `slots` và đọc qua `bookings` trong transaction; báo B quyết.

**Phương án đã loại:** collection chiếu công khai riêng (`publicSlots`) do transaction của B ghi song song: mọi luồng ghi slot của B, C (vãng lai), D (khóa slot) phải ghi hai nơi.

Viết Rules cho `venues`, `courts`, `priceRules`, `slots` là việc của D, B (người phụ trách collection). Spec 020 gửi bảng trên làm yêu cầu, và A tự viết Rules cho `districts`, `amenities`, `searchHistory` (A phụ trách theo đặc tả CSDL mục 2), kèm test Emulator cho cả quyền đọc của khách.

## R14. Danh mục quận/huyện và tiện ích

**Quyết định:**
- A phụ trách hai collection này (đặc tả CSDL mục 2), nên spec 020 định nghĩa schema (plan 120 ghi "chưa có schema"): `districts/{id}`: `name`, `cityName`, `sortOrder`, `isActive`; `amenities/{id}`: `name`, `iconKey`, `sortOrder`, `isActive`. ID là slug ổn định (`q10`, `tan-binh`, `parking`, `drinks`, `shower`, `racket-rental`) để AI (022) và dữ liệu demo dùng chung.
- `CatalogRepository` ở `core/catalog` (020, 021, 022, 030, 111, 120 dùng): đọc một lần mỗi phiên app (~30 lượt), giữ trong `StateFlow`, có cache offline của Firestore.
- Dữ liệu ban đầu: `scripts/seed/catalog.json` (24 quận/huyện TP.HCM, 4 tiện ích của spec) và script seed cho Emulator/project thật.
- `isActive == false` (admin ẩn) tương đương "không còn trong danh mục" khi khôi phục lịch sử và khi kiểm tra bộ lọc của AI.

## R15. Những chỗ cần sửa spec hoặc tài liệu khác

| # | Ở đâu | Sửa gì | Lý do |
|---|---|---|---|
| 1 | spec 020, Assumptions | Thêm: "Với sắp xếp Gần nhất hoặc sắp xếp giá có khung giờ, mỗi lần tìm xét tối đa 150 cơ sở; vượt quá thì app gợi ý chọn quận" | R3, giới hạn của cách lọc trên máy |
| 2 | spec 020, FR-016 | Thêm: "một trang có thể ít hơn 20 khi cần đọc thêm quá nhiều dữ liệu; cuộn tiếp sẽ tải phần còn lại" | R3, S1 giới hạn 4 lô mỗi lần tải |
| 3 | spec 020, FR-026 | "không lộ ai đã đặt" → "không lộ thông tin cá nhân của người đã đặt" | R13 |
| 4 | Đặc tả CSDL mục 4.3 | `ratingAvg = 0`, `ratingCount = 0`, `minPricePerHour = null` bắt buộc có từ khi tạo cơ sở; `nameKeywords` theo contract keyword; quy ước `daysOfWeek` | R5, R6, R7 |
| 5 | Đặc tả CSDL mục 6, 7, 9 | Bảng index R8; Rules R13; đóng câu 6 của mục 9 | R6, R8, R13 |
| 6 | Wireframe bước 3 | Nút "Xem 12 sân" có thể hiện "Xem kết quả" khi chưa biết số đếm | R3 |
| 7 | spec 020, FR-009 | "Xóa bộ lọc" giữ cả từ khóa và quận (khớp US2-8) | Mâu thuẫn FR-009 ↔ US2-8 |

Các dòng 1, 2, 3, 6, 7 đã sửa ngay trong lần `/speckit-analyze` ngày 19/10/2026 (spec.md và `ui/README.md`); dòng 4, 5 vào docs CSDL (T102).
