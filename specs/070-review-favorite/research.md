# Research: Đánh giá sân và sân yêu thích (spec 070)

**Ngày**: 2026-10-05 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Mỗi mục ghi: **Quyết định** · **Lý do** · **Phương án đã cân nhắc**.

---

## R1. Cập nhật điểm trung bình của sân khi không có trigger Firestore

**Quyết định:** Mọi thao tác **tạo / sửa / xóa đánh giá** và **tạo / sửa phản hồi của chủ sân** đi qua một Edge Function `review` (thay cho `on-review-created` trong đặc tả CSDL mục 8a). Edge Function chạy **một Firestore transaction** (REST API, service account) gồm: đọc đơn, đọc cơ sở, đọc đánh giá hiện có → kiểm tra điều kiện → ghi đánh giá → cộng/trừ tổng điểm của cơ sở. Client chỉ được **đọc** `reviews`.

**Lý do:**
- Firebase gói Spark không có Cloud Functions, nên không có trigger "khi document được tạo". Supabase Edge Function chỉ chạy khi được gọi.
- Nếu client tự ghi đánh giá rồi gọi hàm cập nhật điểm sau, lần gọi thứ hai có thể không xảy ra (mất mạng, tắt app) → điểm trung bình sai vĩnh viễn.
- Ghi đánh giá và cập nhật tổng trong cùng transaction thì 10 người gửi cùng lúc vẫn đúng (SC-005): transaction thua sẽ được thử lại.
- Điều kiện "hoàn thành ≤ 7 ngày", "không phải chủ của cơ sở" cần so sánh thời gian và đọc nhiều document; viết trong Edge Function dễ test hơn Rules.

**Phương án đã cân nhắc:**
- *Rules cho client tạo đánh giá + client gọi `on-review-created` để tính lại:* bị loại vì có thể bỏ sót bước 2 và hai lần tính lại song song có thể ghi đè nhau.
- *Tính lại toàn bộ từ đầu (query mọi đánh giá) mỗi lần:* đúng nhưng tốn lượt đọc theo số đánh giá; bị loại vì hạn mức gói miễn phí.
- *Tính điểm trung bình ở client khi hiển thị:* bị loại vì nguyên tắc II (`ratingAvg` 🔒) và tìm kiếm/sắp xếp theo điểm (spec 020) cần trường có sẵn.

## R2. Lưu tổng điểm để tính trung bình từng tiêu chí

**Quyết định:** Thêm vào `venues/{venueId}` trường 🔒 `ratingSums` (Map: `court`, `cleanliness`, `service`, `overall10` — kiểu Long; `overall10` là tổng điểm tổng ×10) bên cạnh `ratingAvg`, `ratingCount` đã có. `ratingAvg` = `ratingSums.overall10 / 10 / ratingCount`, làm tròn 1 chữ số. Điểm trung bình từng tiêu chí (FR-012) = `ratingSums.<tiêu chí> / ratingCount`.

**Lý do:** Cộng/trừ tổng là O(1) mỗi lần ghi; sửa đánh giá = trừ điểm cũ, cộng điểm mới. Lưu `overall10` dạng số nguyên để tránh sai số cộng dồn của `Double`.

**Phương án đã cân nhắc:** Lưu `ratingAvg` rồi cập nhật bằng công thức trung bình trượt → sai số tích lũy khi sửa/xóa; bị loại.

**Phối hợp:** `venues` do D phụ trách → D thêm `ratingSums` 🔒 vào đặc tả CSDL mục 4.3.

## R3. Hạn 7 ngày cần thời điểm hoàn thành đơn

**Quyết định:** Dùng trường `bookings.completedAt` 🔒 (Timestamp) do server ghi khi đơn chuyển sang `COMPLETED`. Edge Function `review` từ chối tạo/sửa khi `now > completedAt + 7 ngày`.

**Lý do:** Không dùng giờ kết thúc khung giờ cuối (`items[].endAt`) vì đơn có thể được chuyển `COMPLETED` muộn hơn (tác vụ định kỳ); hạn tính từ lúc người chơi thấy nút "Đánh giá" là công bằng nhất.

**Phối hợp:** `bookings` do B phụ trách → B thêm `completedAt` 🔒 vào mục 4.5 (đã báo nhóm).

## R4. Ảnh đánh giá và quyền ghi bucket `review-photos`

**Quyết định:** Đổi đường dẫn object thành **`{uid}/{reviewId}/{uuid}.jpg`** (đặc tả CSDL mục 8 đang ghi `{reviewId}/{tên file}`). Chính sách RLS: chỉ cho `INSERT` khi thư mục đầu tiên = `auth.jwt() ->> 'sub'`; ai cũng đọc (bucket public); `DELETE` chỉ dành cho service role (Edge Function). Edge Function `review` kiểm tra mọi `photoPaths` có tiền tố `{uid}/{bookingId}/` và tồn tại trước khi lưu.

**Lý do:**
- RLS chỉ đọc được JWT, không đọc được Firestore → không kiểm tra được "uid có sở hữu bookingId không". Đặt `uid` ở đầu đường dẫn thì RLS kiểm tra được mà không cần Edge Function `sign-upload` của D.
- `reviewId = bookingId` biết trước khi gửi, nên ảnh tải lên trước rồi mới gọi Edge Function.

**Phương án đã cân nhắc:** Dùng `sign-upload` của D để cấp signed URL sau khi kiểm tra đơn → thêm một lần gọi mạng và phụ thuộc vào D; bị loại vì không cần thiết.

**Ảnh mồ côi:** ảnh đã tải lên nhưng đánh giá không được gửi (người dùng bỏ dở). Khi người dùng bấm "Bỏ đánh giá", client gọi `review` với action `discardPhotos` để xóa. Tác vụ dọn ảnh mồ côi định kỳ không làm ở spec này (ghi vào backlog).

**Nén ảnh:** client thu nhỏ cạnh dài ≤ 1600 px, nén JPEG chất lượng 80 bằng `Bitmap.compress` của Android (không thêm thư viện); nếu vẫn > 2 MB thì báo lỗi ảnh đó (FR-005).

## R5. Chọn ảnh và quyền truy cập

**Quyết định:** Dùng **Android Photo Picker** (`ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3 - số ảnh đã chọn)`) cho thư viện — không cần quyền đọc bộ nhớ. Chụp ảnh dùng `ActivityResultContracts.TakePicture` với `FileProvider`; xin quyền `CAMERA` lúc bấm (runtime).

**Lý do:** Photo Picker có bản backport cho `minSdk 29` qua Google Play system update; tránh phải xin `READ_MEDIA_IMAGES` (nguyên tắc VI: xin quyền tối thiểu).

**Phương án đã cân nhắc:** Thư viện chọn ảnh bên thứ ba → thêm phụ thuộc không cần thiết (nguyên tắc VII).

## R6. Danh sách yêu thích: đọc trạng thái cơ sở bị ẩn/khóa

**Quyết định:** Mỗi `favorites` lưu `venueSnapshot`; khi hiển thị, app đọc thêm từng `venues/{venueId}` bằng `get` (theo trang 20). Đề xuất D tách Rules của `venues` thành `allow get: if isSignedIn()` (đọc một cơ sở theo ID ở mọi trạng thái) và `allow list: if resource.data.status == 'ACTIVE'` (danh sách/tìm kiếm chỉ thấy cơ sở đang hoạt động).
- `status == ACTIVE` → hiển thị bình thường, lấy điểm mới nhất.
- `HIDDEN` / `LOCKED` / `DELETED` → "Sân tạm ngừng hoạt động" (FR-024).
- Không tồn tại → "Sân không còn tồn tại", hiển thị từ `venueSnapshot`.

**Lý do:** Cần phân biệt ẩn và không tồn tại (FR-024), và thông tin cơ sở vốn là thông tin công khai. Đọc theo trang 20 giữ trong hạn mức miễn phí.

**Phương án đã cân nhắc:** Chỉ dùng `venueSnapshot` → không biết sân đã bị ẩn và điểm đánh giá bị cũ; bị loại.

**Phối hợp:** D xác nhận cách tách `get`/`list` cho `venues` (mục 7).

## R7. Yêu thích khi mất mạng

**Quyết định:** Client ghi trực tiếp `users/{uid}/favorites/{venueId}` (set/delete) qua Firestore SDK; cache offline của Firestore giữ thay đổi và gửi khi có mạng. Giao diện đổi ngay (lạc quan); nếu ghi bị từ chối (Rules) thì ViewModel trả trạng thái cũ và hiện snackbar (FR-021). ID document = `venueId` nên ghi hai lần không tạo bản trùng (FR-022).

**Lý do:** Không có logic nhạy cảm (chỉ chủ đọc/ghi danh sách của mình), Rules là đủ (nguyên tắc II); không cần Edge Function.

## R8. Thông báo (FR-026)

**Quyết định:** Edge Function `review` gọi `send-notification` (của D) sau khi transaction thành công:
- `REVIEW_NEW` → chủ cơ sở, khi có đánh giá mới.
- `REVIEW_REPLY` → người viết, khi chủ sân phản hồi lần đầu.
- `REVIEW_REMINDER` → người chơi, do `booking-reminders` (D) gửi khi đơn sang `COMPLETED` (và nhắc lại 1 lần sau 5 ngày nếu chưa đánh giá).

Lỗi gửi thông báo không làm hỏng việc ghi đánh giá (ghi log và bỏ qua).

**Phối hợp:** gửi D 3 loại trên trước 14/10.

## R9. Đánh giá của người đã xóa tài khoản

**Quyết định:** Edge Function `delete-account` (A) cập nhật mọi đánh giá của người dùng: `userId` → `null`, `userSnapshot` → `{ displayName: null, avatarPath: null }`, `authorDeleted = true`. App hiển thị "Người dùng đã xóa" + ảnh mặc định khi `authorDeleted == true` (FR-016). Tổng điểm của cơ sở không đổi (đánh giá vẫn được tính).

**Phối hợp:** A thêm bước này vào `delete-account` (spec 011 FR-020 đã yêu cầu ẩn danh hóa).

## R10. Admin ẩn/hiện đánh giá (spec 120)

**Quyết định:** Việc ẩn/hiện đánh giá (D, spec 120) PHẢI dùng hàm dùng chung `applyReviewVisibility()` trong `supabase/functions/_shared/reviews.ts` (C viết) để trừ/cộng lại `ratingSums`, `ratingCount` trong cùng transaction. Đánh giá `HIDDEN` không được tính vào điểm (FR-011).

**Phương án đã cân nhắc:** Admin ghi `status` trực tiếp bằng Rules → điểm trung bình không được cập nhật; bị loại.

## R11. Truy vấn và index

**Quyết định:**
- Danh sách đánh giá của sân: `reviews` where `venueId == X` and `status == VISIBLE` order by `createdAt desc`, phân trang 20 (index đã có ở mục 6).
- Lọc theo sao: thêm trường 🔒 `starBucket` (Int 1–5, = `round(overall)`) → index `venueId`, `status`, `starBucket`, `createdAt desc`.
- Lọc "Có ảnh": thêm trường 🔒 `hasPhotos` (Boolean) → index `venueId`, `status`, `hasPhotos`, `createdAt desc`.
- "Đánh giá của khách" (chủ sân): where `venueId in [≤ 30 cơ sở của chủ]` order by `createdAt desc`; lọc "Chưa phản hồi" bằng 🔒 `hasOwnerReply == false` → index `venueId`, `hasOwnerReply`, `createdAt desc`.
- Đánh giá của tôi cho một đơn: `get reviews/{bookingId}` (không cần index).
- Yêu thích: `users/{uid}/favorites` order by `createdAt desc` (index một trường tự động).

**Lý do:** Firestore không lọc được theo giá trị tính toán hay độ dài mảng, nên lưu sẵn các trường lọc do server ghi.

## R12. Thư viện cần thêm (dùng chung cả nhóm)

App hiện chỉ có AndroidX cơ bản. Spec này chỉ dùng các thư viện đã có trong constitution:

| Mục đích | Thư viện |
|---|---|
| DI | Hilt (`hilt-android`, `hilt-compiler` qua KSP) |
| Firebase | Firebase BoM: `firebase-auth`, `firebase-firestore` |
| Supabase | `supabase-kt`: `storage-kt`, `functions-kt` + Ktor engine `ktor-client-okhttp`, `kotlinx-serialization-json` |
| Ảnh | Coil |
| Lifecycle / async | `lifecycle-viewmodel-ktx`, `lifecycle-runtime-ktx`, `kotlinx-coroutines-android` |
| Điều hướng | Navigation Safe Args plugin |
| Test | MockK, Turbine, `kotlinx-coroutines-test`, Firebase Emulator Suite (`@firebase/rules-unit-testing` cho test Rules) |

**Phối hợp:** phần cấu hình chung (`core/`, Hilt, Firebase, Supabase client) chỉ nên làm **một lần** cho cả nhóm. Người làm trước tạo PR riêng `chore(core): ...` vào `develop`; spec này dùng lại, không tự cấu hình riêng.
