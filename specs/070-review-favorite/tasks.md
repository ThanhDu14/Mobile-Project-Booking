---

description: "Danh sách task triển khai spec 070 – Đánh giá sân và sân yêu thích"
---

# Tasks: Đánh giá sân và sân yêu thích

**Input**: Tài liệu thiết kế trong `specs/070-review-favorite/` — [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho ViewModel/UseCase, Security Rules trên Firebase Emulator, chính sách Storage trên Supabase local và thao tác đồng thời. Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: Nhóm theo user story để mỗi story làm và kiểm thử độc lập được.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: User story của spec (US1…US6)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `REV/` | `app/src/main/java/com/example/booking_app/feature/review/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/feature/review/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/feature/review/` |
| `FN/` | `supabase/functions/` |

---

## Phase 1: Setup (hạ tầng dùng chung)

**Purpose**: Thư viện và cấu hình chung. **Nếu PR `chore(core)` của nhóm đã có phần nào thì đánh dấu xong và dùng lại, không làm trùng** (research R12).

- [ ] T001 Thêm vào `gradle/libs.versions.toml` các phiên bản và thư viện: Hilt (+ KSP), Firebase BoM (`firebase-auth`, `firebase-firestore`), `supabase-kt` (`storage-kt`, `functions-kt`), `ktor-client-okhttp`, `kotlinx-serialization-json`, Coil, `lifecycle-viewmodel-ktx`, `lifecycle-runtime-ktx`, `kotlinx-coroutines-android`, plugin Navigation Safe Args; test: MockK, Turbine, `kotlinx-coroutines-test`
- [ ] T002 Áp dụng plugin (Hilt, KSP, kotlin serialization, Safe Args, google-services) và khai báo dependency trong `app/build.gradle.kts` và `build.gradle.kts`
- [ ] T003 Tạo `APP/SmashNowApp.kt` (`@HiltAndroidApp`), khai báo trong `app/src/main/AndroidManifest.xml`; gắn `@AndroidEntryPoint` cho `APP/MainActivity.kt`
- [ ] T004 [P] Tạo `APP/core/di/FirebaseModule.kt` cung cấp `FirebaseAuth`, `FirebaseFirestore` (build debug trỏ Emulator `10.0.2.2:9099/8080`, bật offline cache)
- [ ] T005 [P] Tạo `APP/core/di/SupabaseModule.kt` cung cấp `SupabaseClient` (Storage, Functions) với `SUPABASE_URL`, `SUPABASE_ANON_KEY` đọc từ `local.properties` qua `BuildConfig`; `accessToken` = Firebase ID token (`getIdToken(false)`)
- [ ] T006 [P] Tạo `APP/core/ui/UiState.kt` (`Loading`, `Content<T>`, `Empty`, `Error(message, retry)`) và `APP/core/ui/StateViews.kt` (helper hiển thị 4 trạng thái cho layout có `include_state_views.xml`) + `RES/layout/include_state_views.xml`
- [ ] T007 [P] Tạo `APP/core/model/VenueSummary.kt` (`venueId`, `name`, `photoPath`, `address`)
- [ ] T008 [P] Khởi tạo `firebase.json` (emulator auth 9099, firestore 8080), `firestore.rules`, `firestore.indexes.json` nếu chưa có; khởi tạo thư mục `firestore-tests/` (Node 20, `@firebase/rules-unit-testing`, `vitest`) với `firestore-tests/package.json`
- [ ] T009 [P] Khởi tạo Supabase local (`supabase init` → `supabase/config.toml`), thêm `supabase/.env.local.example` (FIREBASE_PROJECT_ID, FIREBASE_SERVICE_ACCOUNT_JSON giả) và đảm bảo `.gitignore` chặn `supabase/.env.local`, `local.properties`

**Checkpoint**: `./gradlew assembleDebug` chạy được; `firebase emulators:start` và `supabase start` chạy được.

---

## Phase 2: Foundational (chặn mọi user story)

**Purpose**: Mô hình, interface, Rules, khung Edge Function, điều hướng, chuỗi.

**⚠️ CRITICAL**: Chưa xong phase này thì chưa làm user story nào.

### Domain và data dùng chung

- [ ] T010 [P] Tạo model domain trong `REV/domain/model/`: `Review.kt`, `ReviewAuthor.kt`, `Ratings.kt` (mỗi tiêu chí `Int` 1–5), `OwnerReply.kt`, `VenueRatingSummary.kt`, `FavoriteVenue.kt` (`availability`: `ACTIVE`, `SUSPENDED`, `GONE`), `ReviewDraft.kt`, `DraftPhoto.kt` (`state`: `PENDING`, `UPLOADING`, `UPLOADED`, `FAILED`) đúng bảng mục 6 của [data-model.md](data-model.md)
- [ ] T011 [P] Tạo interface `REV/domain/ReviewRepository.kt` (`observeSummary`, `observeMyReview`, `observeVenueReviews`, `observeOwnerReviews`, `create`, `update`, `delete`, `reply`, `discardPhotos`) và `REV/domain/FavoriteRepository.kt` (`observeIsFavorite`, `observeFavorites`, `setFavorite`) theo [contracts/ui-screens.md](contracts/ui-screens.md)
- [ ] T012 [P] Tạo `REV/data/dto/ReviewDto.kt`, `FavoriteDto.kt` và mapper sang domain trong `REV/data/dto/Mappers.kt`: `authorDeleted == true` → `ReviewAuthor.isDeleted`; `editedAt != null` → `isEdited`; `photoPaths` → URL public bằng `REV/data/ReviewPhotoUrls.kt` (`{SUPABASE_URL}/storage/v1/object/public/review-photos/{path}`)
- [ ] T013 [P] Tạo `REV/data/dto/ReviewFunctionDtos.kt` (`@Serializable` request/response cho 5 action và body lỗi `{ error: { code, message } }`) và `REV/domain/ReviewError.kt` ánh xạ mã lỗi: `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `NOT_OWNER`, `OWN_VENUE`, `ACCOUNT_LOCKED`, `NOT_FOUND`, `BOOKING_NOT_COMPLETED`, `REVIEW_WINDOW_CLOSED`, `ALREADY_REVIEWED`, `PHOTO_MISSING`, `INTERNAL`, `NETWORK`
- [ ] T014 Tạo khung `REV/data/FirestoreReviewRepository.kt` (đọc Firestore bằng `callbackFlow`, gọi `supabase.functions.invoke("review")`; khi `401` làm mới token `getIdToken(true)` và thử lại 1 lần) và `REV/data/FirestoreFavoriteRepository.kt`; bind trong `REV/data/di/ReviewModule.kt` (Hilt)
- [ ] T015 [P] Tạo `RES/values/strings_review.xml` với toàn bộ key ở mục 3 của [contracts/ui-screens.md](contracts/ui-screens.md) và thông báo cho từng mã lỗi ở T013
- [ ] T016 [P] Tạo `RES/navigation/nav_review.xml` (nested graph) với 6 destination và argument Safe Args theo [contracts/ui-screens.md](contracts/ui-screens.md) mục 1, deep link `smashnow://review/write/{bookingId}`, `smashnow://review/venue/{venueId}`; `include` vào graph chính `RES/navigation/nav_graph.xml`
- [ ] T017 [P] Tạo `RES/layout/item_review.xml` (avatar, tên, điểm tổng + 3 tiêu chí, bình luận, lưới ảnh, ngày, nhãn "Đã chỉnh sửa", nhãn "Đánh giá đã bị ẩn", khối "Phản hồi của chủ sân") và `REV/ui/common/ReviewAdapter.kt` (`ListAdapter` + `DiffUtil`; tác giả đã xóa → `review_deleted_user` + ảnh mặc định; `contentDescription` đọc số sao)

### Server dùng chung

- [ ] T018 [P] Viết test `FN/_shared/reviews.test.ts` cho hàm tính tổng điểm: create/update/delete/ẩn/hiện theo bảng "Ảnh hưởng tới tổng điểm" trong [data-model.md](data-model.md); `overall` = trung bình 3 tiêu chí làm tròn 1 chữ số; `ratingAvg = ratingSums.overall10 / 10 / ratingCount`; `ratingCount == 0` → `ratingAvg = 0`
- [ ] T019 Cài đặt `FN/_shared/reviews.ts`: `computeOverall()`, `starBucket()`, `applyDelta(venue, oldReview?, newReview?)` để T018 qua
- [ ] T020 [P] Tạo `FN/_shared/auth.ts`: xác thực Firebase ID token (JWKS của Google, kiểm `aud`, `iss`, `exp`), trả `{ uid, appRole }`; dùng `FN/_shared/firestore.ts` của B cho REST/transaction (nếu chưa có thì tạo bản tối thiểu `beginTransaction`, `get`, `commit`, thử lại 5 lần khi `ABORTED`, và báo B)
- [ ] T021 Tạo khung `FN/review/index.ts`: parse `action`, gọi `auth.ts`, kiểm `users/{uid}.accountStatus == ACTIVE` (`403 ACCOUNT_LOCKED`), router tới các handler trống, chuẩn hóa body lỗi theo [contracts/edge-function-review.md](contracts/edge-function-review.md)

### Quyền truy cập và index

- [ ] T022 [P] Viết test Rules `firestore-tests/reviews.test.ts` (R1–R9) và `firestore-tests/favorites.test.ts` (F1–F4) theo [contracts/security-rules.md](contracts/security-rules.md); chạy thấy fail
- [ ] T023 Thêm Rules cho `reviews` và `users/{uid}/favorites` vào `firestore.rules` đúng [contracts/security-rules.md](contracts/security-rules.md) để T022 qua
- [ ] T024 [P] Thêm 5 index mới vào `firestore.indexes.json` theo mục 5 của [data-model.md](data-model.md) (`reviews`: `starBucket`, `hasPhotos`, `hasOwnerReply`, `userId`; collection group `favorites.venueId`)
- [ ] T025 [P] Tạo script dữ liệu demo `scripts/seed/070-review.ts` (player1, player2, owner1, owner2, admin1; venueA ACTIVE với 25 đánh giá, venueB HIDDEN; đơn `b-done`, `b-old`, `b-confirmed`, `b-own`) theo [quickstart.md](quickstart.md) mục 1

**Checkpoint**: Rules test qua; `deno test FN/_shared` qua; app build được với nav graph mới.

---

## Phase 3: User Story 1 – Đánh giá sân sau khi chơi (Priority: P1) 🎯 MVP

**Goal**: Người chơi có đơn `COMPLETED` (≤ 7 ngày) chấm 3 tiêu chí + bình luận và gửi; điểm sân cập nhật.

**Independent Test**: Kịch bản 1, 2, 3, 8 trong [quickstart.md](quickstart.md); test đồng thời 10 lời gọi `create`.

### Tests cho US1 ⚠️

- [ ] T026 [P] [US1] Viết `FN/review/create.test.ts`: thành công (201, tổng điểm +1); đơn không phải của mình → `403 NOT_OWNER`; đơn `CONFIRMED` → `409 BOOKING_NOT_COMPLETED`; `completedAt` 8 ngày trước → `409 REVIEW_WINDOW_CLOSED`; chủ sân đánh giá cơ sở của mình → `403 OWN_VENUE`; thiếu tiêu chí / sao ngoài 1–5 / bình luận > 1000 ký tự → `400 INVALID_ARGUMENT`; gửi lại cùng nội dung → `200` không tạo thêm; gửi lại khác nội dung → `409 ALREADY_REVIEWED`; **10 lời gọi song song cho 10 đơn của cùng venue → `ratingCount` +10 và `ratingSums` đúng tổng** (SC-005)
- [ ] T027 [P] [US1] Viết `TEST/domain/ReviewEligibilityTest.kt`: `COMPLETED` + chưa đánh giá + ≤ 7 ngày → `CAN_WRITE`; đã có đánh giá → `CAN_VIEW`; quá 7 ngày chưa đánh giá → `EXPIRED`; trạng thái khác → `NOT_ELIGIBLE`
- [ ] T028 [P] [US1] Viết `TEST/ui/write/WriteReviewViewModelTest.kt` (Turbine, fake repository): thiếu tiêu chí → lỗi chỉ rõ tiêu chí, không gọi repository; gửi thành công → sự kiện điều hướng + điểm tổng 4,0 cho 5/4/3; lỗi `NETWORK` → giữ nguyên bản nháp, hiện "Thử lại"; bấm "Gửi" 3 lần liên tiếp → chỉ 1 lời gọi `create`

### Implementation cho US1

- [ ] T029 [US1] Cài đặt handler `create` trong `FN/review/index.ts` (gọi `FN/_shared/reviews.ts`): transaction đọc `bookings/{bookingId}`, `venues/{venueId}`, `reviews/{bookingId}`; kiểm tra theo bảng "Quy tắc kiểm tra" trong [data-model.md](data-model.md); ghi review (`status = VISIBLE`, `starBucket`, `hasPhotos`, `hasOwnerReply = false`, `completedAt` sao chép từ đơn, `userSnapshot` từ `users/{uid}`); cập nhật `venues.ratingSums`, `ratingCount`, `ratingAvg`; trả `canEditUntil = completedAt + 7 ngày` → T026 qua
- [ ] T030 [US1] Sau commit trong handler `create`, gọi `send-notification` loại `REVIEW_NEW` tới `venues.ownerId`; lỗi chỉ ghi log, không trả lỗi cho client (research R8) trong `FN/review/index.ts`
- [ ] T031 [P] [US1] Cài đặt `REV/domain/ReviewEligibility.kt` (`of(bookingStatus, completedAt, existingReview, now)`) → T027 qua
- [ ] T032 [US1] Cài đặt `create`, `observeMyReview(bookingId)` (listener `reviews/{bookingId}`) và `observeSummary(venueId)` (listener `venues/{venueId}`: `ratingAvg`, `ratingCount`, trung bình từng tiêu chí = `ratingSums.<tiêu chí> / ratingCount`) trong `REV/data/FirestoreReviewRepository.kt`
- [ ] T033 [P] [US1] Cài đặt `REV/domain/usecase/SubmitReviewUseCase.kt` (kiểm tra ở client: đủ 3 tiêu chí 1–5, bình luận ≤ 1000 ký tự sau khi trim)
- [ ] T034 [US1] Cài đặt `REV/ui/write/WriteReviewViewModel.kt` (`StateFlow<WriteReviewUiState>`, sự kiện một lần qua `Channel`, chặn gửi trùng khi đang gửi) → T028 qua
- [ ] T035 [P] [US1] Tạo `RES/layout/fragment_review_write.xml`: 3 `RatingBar` (bước 1, có nhãn tiêu chí), `TextInputLayout` bình luận có bộ đếm 1000, nút "Gửi", vùng ảnh (để trống cho US2), `include_state_views`
- [ ] T036 [US1] Cài đặt `REV/ui/write/WriteReviewFragment.kt` (ViewBinding, `repeatOnLifecycle(STARTED)`, hộp thoại "Bỏ đánh giá đang viết?" khi bấm quay lại lúc có thay đổi, snackbar lỗi theo `ReviewError`)
- [ ] T037 [US1] Cung cấp điểm nối cho spec 060: ghi chú cách dùng `ReviewEligibility` + `observeMyReview` + action tới `WriteReviewFragment(bookingId, mode = CREATE)` trong `REV/README.md` (để B gắn nút "Đánh giá"/"Xem đánh giá của bạn")

**Checkpoint**: Chạy kịch bản 1, 2, 3, 8 của quickstart; US1 dùng được độc lập (MVP).

---

## Phase 4: User Story 2 – Đính kèm ảnh (Priority: P2)

**Goal**: Đính kèm 0–3 ảnh (chụp hoặc chọn), nén, tải lên, xử lý ảnh lỗi.

**Independent Test**: Kịch bản 4 trong [quickstart.md](quickstart.md); RLS S1–S6.

### Tests cho US2 ⚠️

- [ ] T038 [P] [US2] Viết test RLS `supabase/tests/review_photos.test.sql` (S1–S6 trong [contracts/security-rules.md](contracts/security-rules.md)); chạy thấy fail
- [ ] T039 [P] [US2] Bổ sung `FN/review/create.test.ts`: > 3 ảnh → `400`; path không có tiền tố `{uid}/{bookingId}/` → `400`; path chưa có trong Storage → `409 PHOTO_MISSING`; và `FN/review/discard.test.ts`: chỉ xóa path của mình chưa thuộc đánh giá đã lưu
- [ ] T040 [P] [US2] Viết `TEST/ui/write/WriteReviewPhotosTest.kt`: chọn ảnh thứ 4 → lỗi `review_photo_limit`; ảnh `FAILED` → nút "Gửi" bị khóa cho tới khi thử lại thành công hoặc bỏ ảnh (FR-006); bỏ ảnh đã tải → gọi `discardPhotos`

### Implementation cho US2

- [ ] T041 [US2] Tạo migration `supabase/migrations/<timestamp>_review_photos.sql` (bucket public, `file_size_limit` 2097152, MIME `image/jpeg`, `image/png`, `image/webp`, policy insert theo thư mục `sub`) → T038 qua
- [ ] T042 [US2] Kiểm tra `photoPaths` trong handler `create` (≤ 3, tiền tố `{uid}/{bookingId}/`, object tồn tại) và cài đặt handler `discardPhotos` trong `FN/review/index.ts` → T039 qua
- [ ] T043 [P] [US2] Cài đặt `REV/data/ReviewPhotoUploader.kt`: đọc `Uri`, thu nhỏ cạnh dài ≤ 1600 px, nén JPEG chất lượng 80; > 2 MB sau nén → lỗi cho ảnh đó; tải lên `review-photos/{uid}/{bookingId}/{uuid}.jpg`; trả `Flow` trạng thái từng ảnh
- [ ] T044 [US2] Mở rộng `WriteReviewViewModel.kt` với danh sách `DraftPhoto` (thêm, bỏ, thử lại, tối đa 3) và chỉ gọi `create` khi mọi ảnh `UPLOADED` → T040 qua
- [ ] T045 [US2] Thêm vào `fragment_review_write.xml` và `WriteReviewFragment.kt`: Photo Picker `PickMultipleVisualMedia(3 - số ảnh)`, chụp ảnh `TakePicture` + `FileProvider` (`RES/xml/file_paths.xml`, khai báo provider trong manifest), xin quyền `CAMERA` lúc bấm kèm giải thích khi bị từ chối; lưới ảnh có badge lỗi/nút thử lại; layout `RES/layout/item_review_draft_photo.xml`
- [ ] T046 [P] [US2] Tạo `REV/ui/photo/PhotoViewerFragment.kt` + `RES/layout/fragment_review_photo_viewer.xml` (ViewPager2, Coil) và mở từ lưới ảnh trong `ReviewAdapter.kt`

**Checkpoint**: Kịch bản 4 qua; US1 vẫn chạy khi không chọn ảnh.

---

## Phase 5: User Story 3 – Sân yêu thích (Priority: P2)

**Goal**: Lưu/bỏ yêu thích bằng trái tim (đổi ngay, chạy offline), xem danh sách có ghi chú sân tạm ngừng/không còn.

**Independent Test**: Kịch bản 9, 10, 11 trong [quickstart.md](quickstart.md); Rules F1–F4.

### Tests cho US3 ⚠️

- [ ] T047 [P] [US3] Viết `TEST/ui/favorite/FavoriteVenuesViewModelTest.kt`: danh sách rỗng → `Empty`; venue `HIDDEN`/`LOCKED`/`DELETED` → `SUSPENDED`; venue không tồn tại → `GONE`; bỏ yêu thích xóa khỏi danh sách
- [ ] T048 [P] [US3] Viết `TEST/domain/ToggleFavoriteUseCaseTest.kt`: trạng thái đổi ngay (lạc quan); repository trả lỗi → trả về trạng thái cũ và phát sự kiện snackbar (FR-021)

### Implementation cho US3

- [ ] T049 [US3] Cài đặt `REV/data/FirestoreFavoriteRepository.kt`: `setFavorite` ghi `users/{uid}/favorites/{venueId}` với `venueId`, `venueSnapshot` (`name`, `photoPath`, `address`), `createdAt = serverTimestamp()` hoặc xóa; `observeIsFavorite` (listener 1 document); `observeFavorites` (order `createdAt desc`, trang 20) + `get venues/{venueId}` từng sân để ra `availability` (research R6)
- [ ] T050 [P] [US3] Cài đặt `REV/domain/usecase/ToggleFavoriteUseCase.kt` → T048 qua
- [ ] T051 [US3] Cài đặt `REV/ui/favorite/FavoriteVenuesViewModel.kt` (phân trang khi cuộn cuối) → T047 qua
- [ ] T052 [P] [US3] Tạo `RES/layout/fragment_favorite_list.xml`, `RES/layout/item_favorite_venue.xml` (ảnh, tên, địa chỉ, điểm; nhãn `favorite_venue_suspended`/`favorite_venue_gone`; nút trái tim bỏ yêu thích) và `REV/ui/favorite/FavoriteVenueAdapter.kt`
- [ ] T053 [US3] Cài đặt `REV/ui/favorite/FavoriteVenuesFragment.kt` (bấm sân `ACTIVE` → chi tiết sân spec 030; sân `SUSPENDED`/`GONE` không mở đặt sân; trạng thái rỗng có nút "Tìm sân")
- [ ] T054 [P] [US3] Tạo view dùng lại `REV/ui/favorite/FavoriteToggleButton.kt` (+ `RES/drawable/ic_favorite_*.xml`, `contentDescription` "Thêm/Bỏ yêu thích", vùng chạm 48dp) và ghi hướng dẫn gắn vào chi tiết sân (spec 030) và mục "Sân yêu thích" (spec 011) trong `REV/README.md`

**Checkpoint**: Kịch bản 9–11 qua; Rules F1–F4 vẫn qua.

---

## Phase 6: User Story 4 – Chủ sân phản hồi đánh giá (Priority: P2)

**Goal**: Chủ sân xem đánh giá các cơ sở của mình, lọc "Chưa phản hồi", tạo/sửa (không xóa) phản hồi.

**Independent Test**: Kịch bản 6, 7 trong [quickstart.md](quickstart.md).

### Tests cho US4 ⚠️

- [ ] T055 [P] [US4] Viết `FN/review/reply.test.ts`: chủ đúng cơ sở tạo phản hồi (`hasOwnerReply = true`, gửi `REVIEW_REPLY`); sửa lần 2 → `editedAt` có giá trị, không gửi thông báo lại; chủ cơ sở khác → `403 NOT_OWNER`; người chơi → `403 NOT_OWNER`; text rỗng hoặc > 500 ký tự → `400`; `ratingSums` không đổi
- [ ] T056 [P] [US4] Viết `TEST/ui/owner/OwnerReviewsViewModelTest.kt`: lọc theo cơ sở và "Chưa phản hồi"; gửi phản hồi rỗng → lỗi, không gọi repository

### Implementation cho US4

- [ ] T057 [US4] Cài đặt handler `reply` trong `FN/review/index.ts` (kiểm `appRole == OWNER`, `users.ownerStatus == APPROVED`, `venues.ownerId == uid`; text 1–500 ký tự; tạo `ownerReply.repliedAt` hoặc cập nhật `ownerReply.editedAt`; gửi `REVIEW_REPLY` lần đầu nếu `userId != null`) → T055 qua
- [ ] T058 [US4] Cài đặt `observeOwnerReviews(venueIds, unrepliedOnly)` (`venueId in [≤ 30]`, `status == VISIBLE`, order `createdAt desc`, trang 20) và `reply` trong `REV/data/FirestoreReviewRepository.kt`
- [ ] T059 [US4] Cài đặt `REV/ui/owner/OwnerReviewsViewModel.kt` (lấy danh sách cơ sở của chủ sân qua interface của spec 111, lọc theo cơ sở/"Chưa phản hồi") → T056 qua
- [ ] T060 [P] [US4] Tạo `RES/layout/fragment_review_owner_list.xml` (chip lọc cơ sở, chip "Chưa phản hồi", danh sách dùng `ReviewAdapter` có nút "Phản hồi"/"Sửa phản hồi") và `RES/layout/bottom_sheet_review_reply.xml` (ô nhập có bộ đếm 500, không có nút xóa)
- [ ] T061 [US4] Cài đặt `REV/ui/owner/OwnerReviewsFragment.kt` và `REV/ui/owner/ReplyReviewBottomSheet.kt`; ghi hướng dẫn gắn mục "Đánh giá của khách" vào menu chủ sân (spec 111/112) trong `REV/README.md`

**Checkpoint**: Kịch bản 6, 7 qua.

---

## Phase 7: User Story 5 – Sửa hoặc xóa đánh giá của mình (Priority: P3)

**Goal**: Sửa trong hạn 7 ngày kể từ khi đơn hoàn thành; xóa bất cứ lúc nào; điểm sân tính lại.

**Independent Test**: Kịch bản 5, 13 trong [quickstart.md](quickstart.md).

### Tests cho US5 ⚠️

- [ ] T062 [P] [US5] Viết `FN/review/update-delete.test.ts`: sửa trong hạn → `editedAt` đặt, tổng điểm = − cũ + mới, `ownerReply` giữ nguyên; sửa quá hạn → `409 REVIEW_WINDOW_CLOSED`; người khác sửa/xóa → `403 NOT_OWNER`; xóa `VISIBLE` → `ratingCount` −1 và ảnh trong `{uid}/{reviewId}/` bị xóa; xóa `HIDDEN` → tổng điểm không đổi; xóa quá hạn vẫn được; sau khi xóa, tạo lại trong hạn được
- [ ] T063 [P] [US5] Viết `TEST/ui/write/EditReviewViewModelTest.kt`: `mode = EDIT` nạp sẵn dữ liệu; quá hạn → chỉ còn "Xóa"; xóa cần xác nhận

### Implementation cho US5

- [ ] T064 [US5] Cài đặt handler `update` và `delete` trong `FN/review/index.ts` theo [contracts/edge-function-review.md](contracts/edge-function-review.md) (ảnh bị bỏ khi sửa → xóa sau commit) → T062 qua
- [ ] T065 [US5] Cài đặt `update`, `delete` trong `REV/data/FirestoreReviewRepository.kt` và `REV/domain/usecase/UpdateReviewUseCase.kt`, `DeleteReviewUseCase.kt`
- [ ] T066 [US5] Mở rộng `WriteReviewViewModel.kt` và `WriteReviewFragment.kt` cho `mode = EDIT` (nạp đánh giá, nút "Lưu", nút "Xóa đánh giá" + hộp thoại xác nhận, ẩn nút sửa khi quá hạn) → T063 qua

**Checkpoint**: Kịch bản 5, 13 qua.

---

## Phase 8: User Story 6 – Xem tất cả đánh giá của một sân (Priority: P3)

**Goal**: Danh sách đầy đủ, mới nhất trước, trang 20, lọc theo sao và "Có ảnh", điểm trung bình từng tiêu chí.

**Independent Test**: Kịch bản 12, 14, 15 trong [quickstart.md](quickstart.md).

### Tests cho US6 ⚠️

- [ ] T067 [P] [US6] Viết `TEST/ui/list/VenueReviewsViewModelTest.kt`: trang đầu 20, tải thêm khi cuộn cuối; lọc "4 sao" dùng `starBucket == 4`; lọc "Có ảnh"; sân chưa có đánh giá → `Empty`; lỗi → `Error` có thử lại

### Implementation cho US6

- [ ] T068 [US6] Cài đặt `observeVenueReviews(venueId, starFilter?, withPhotos)` trong `REV/data/FirestoreReviewRepository.kt` (`status == VISIBLE`, order `createdAt desc`, phân trang bằng `startAfter`, limit 20; gỡ listener khi không còn thu thập)
- [ ] T069 [US6] Cài đặt `REV/ui/list/VenueReviewsViewModel.kt` → T067 qua
- [ ] T070 [P] [US6] Tạo `RES/layout/fragment_review_list.xml` (khối tổng quan: điểm trung bình, số lượt, 3 thanh điểm tiêu chí; chip lọc 1–5 sao, "Có ảnh"; `RecyclerView`; `include_state_views`)
- [ ] T071 [US6] Cài đặt `REV/ui/list/VenueReviewsFragment.kt` (Safe Args `venueId`, `venueName`); ghi hướng dẫn gắn nút "Xem tất cả" ở chi tiết sân (spec 030) trong `REV/README.md`

**Checkpoint**: Mọi user story chạy độc lập.

---

## Phase 9: Polish & Cross-Cutting

- [ ] T072 [P] Viết `FN/_shared/reviews.visibility.test.ts` và cài đặt `applyReviewVisibility(tx, reviewId, visible)` trong `FN/_shared/reviews.ts` cho spec 120 (D) (research R10)
- [ ] T073 [P] Cập nhật `docs/design/database/README.md` theo 9 mục ở cuối [data-model.md](data-model.md) (chỉ phần của C; ghi chú phần của B, D, A để họ xác nhận)
- [ ] T074 [P] Gửi D danh sách loại thông báo `REVIEW_NEW`, `REVIEW_REPLY`, `REVIEW_REMINDER` và deep link (research R8) — ghi vào `docs/features/07-review-favorite/README.md` mục "Ghi chú thiết kế / quyết định"
- [ ] T075 [P] Viết Espresso `ATEST/WriteReviewFlowTest.kt` cho luồng viết đánh giá (chấm sao → bình luận → gửi → thấy "Xem đánh giá của bạn") trên Emulator
- [ ] T076 Kiểm tra chế độ tối, `w600dp` (thêm `RES/layout-w600dp/` nếu cần), TalkBack cho `RatingBar` và nút trái tim, vùng chạm 48dp trên mọi màn hình của spec
- [ ] T077 Chạy toàn bộ [quickstart.md](quickstart.md) (test tự động + 15 kịch bản), chụp minh chứng vào `docs/features/07-review-favorite/screenshots/`
- [ ] T078 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, sửa hết lỗi Lint mức Error; cập nhật trạng thái nhóm 07 trong `docs/features/README.md` và `docs/features/07-review-favorite/README.md`

---

## Dependencies & Execution Order

### Phase

- **Setup (Phase 1)** → **Foundational (Phase 2)** → các user story (Phase 3–8) → **Polish (Phase 9)**.
- Phase 1 có thể đã được PR `chore(core)` của nhóm làm; chỉ làm phần còn thiếu.

### User story

| Story | Phụ thuộc | Ghi chú |
|---|---|---|
| US1 (P1) | Phase 2 | MVP |
| US2 (P2) | US1 (dùng màn hình viết đánh giá và handler `create`) | |
| US3 (P2) | Phase 2 | Độc lập hoàn toàn với đánh giá |
| US4 (P2) | Phase 2; dữ liệu đánh giá (seed hoặc US1) | Cần interface "cơ sở của chủ sân" từ spec 111 |
| US5 (P3) | US1 | |
| US6 (P3) | Phase 2 | Dùng `ReviewAdapter` (T017); dữ liệu từ seed |

### Phụ thuộc ngoài nhóm

- `bookings.completedAt` (B) trước T029; `venues.ratingSums` và Rules `get`/`list` của `venues` (D) trước T029, T049; `send-notification` (D) trước T030, T057 (nếu chưa có thì để TODO và bỏ qua lỗi).

### Trong mỗi story

Test (fail) → server handler → repository → use case → ViewModel → layout → Fragment.

---

## Parallel Example

```text
# Phase 2, cùng lúc:
T010 model domain | T011 interface | T012 DTO | T013 DTO hàm + lỗi | T015 strings | T016 nav | T017 item_review
T018 test tổng điểm | T020 auth.ts | T022 test Rules | T024 index | T025 seed

# US1, cùng lúc:
T026 test Edge Function create | T027 test ReviewEligibility | T028 test ViewModel
rồi T031 ReviewEligibility | T033 use case | T035 layout

# Sau Phase 2, có thể làm song song theo story:
US1 → US2 → US5   (luồng viết đánh giá)
US3               (yêu thích)
US6 → US4         (danh sách, chủ sân)
```

---

## Implementation Strategy

### MVP (chỉ US1)

1. Phase 1 + Phase 2.
2. Phase 3 (US1) → chạy kịch bản 1, 2, 3, 8 → **demo được**: người chơi đánh giá sau khi chơi, điểm sân cập nhật đúng kể cả khi nhiều người gửi cùng lúc.

### Giao dần

1. US1 (MVP) → US3 (yêu thích, độc lập, dễ demo) → US2 (ảnh) → US6 (xem tất cả) → US4 (chủ sân) → US5 (sửa/xóa).
2. Mỗi story xong: chạy test + kịch bản quickstart tương ứng, commit `feat(review): ...`.

### Lịch gợi ý theo team contract

- Tuần 5: US1 + phần đầu US3 (khớp mục "07: đánh giá sân (sau khi đã chơi)").
- Tuần sau: US2, US4, US5, US6, Polish trước mốc đóng băng 27/11.
