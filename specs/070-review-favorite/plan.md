# Implementation Plan: Đánh giá sân và sân yêu thích

**Branch**: `feature/07-review-favorite` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/070-review-favorite/spec.md`

## Summary

Người chơi đánh giá cơ sở (3 tiêu chí 1–5 sao, bình luận, tối đa 3 ảnh) sau khi đơn hoàn thành, trong hạn 7 ngày; sửa trong hạn, xóa bất cứ lúc nào. Chủ sân phản hồi (sửa được, không xóa). Người chơi lưu sân yêu thích (= theo dõi).

Cách làm: mọi thao tác ghi đánh giá và phản hồi đi qua **một Supabase Edge Function `review`** chạy **Firestore transaction**, vừa kiểm tra điều kiện (đơn của mình, `COMPLETED`, ≤ 7 ngày, không phải chủ sân) vừa cộng/trừ tổng điểm của cơ sở. Cách này thay cho trigger `on-review-created`, vì gói Spark không có trigger Firestore ([research R1](research.md#r1-cập-nhật-điểm-trung-bình-của-sân-khi-không-có-trigger-firestore)). Client chỉ đọc `reviews`. Ảnh tải thẳng lên bucket `review-photos` theo đường dẫn `{uid}/{reviewId}/...` để RLS tự kiểm tra (R4). Danh sách yêu thích do client ghi trực tiếp qua Firestore SDK, có cache offline, Rules chỉ cho chủ tài khoản (R7).

## Technical Context

**Language/Version**: Kotlin (Kotlin tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function

**Primary Dependencies**: AndroidX Fragment/Navigation (+ Safe Args), ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt, Firebase BoM (Auth, Firestore), supabase-kt (Storage, Functions) + Ktor OkHttp, Coil, Android Photo Picker ([research R12](research.md#r12-thư-viện-cần-thêm-dùng-chung-cả-nhóm))

**Storage**: Cloud Firestore (`reviews`, `users/{uid}/favorites`, đọc/ghi tổng điểm của `venues`); Supabase Storage bucket `review-photos` (public)

**Testing**: JUnit4, MockK, Turbine, kotlinx-coroutines-test (unit); Firebase Emulator + `@firebase/rules-unit-testing` (Rules); `deno test` + Supabase local (Edge Function, RLS); Espresso (luồng viết đánh giá, nên có)

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime

**Project Type**: Mobile app (Android, single Activity) + server function (Supabase)

**Performance Goals**: Trang đầu danh sách đánh giá/yêu thích < 3 s trên 4G (SC-007); điểm sân cập nhật < 10 s sau khi gửi (SC-004, thực tế ngay khi transaction commit); trái tim đổi < 0,5 s (SC-006)

**Constraints**: Gói miễn phí (Firebase Spark, Supabase Free): phân trang 20, gỡ listener khi rời màn hình, ảnh ≤ 2 MB và ≤ 3 ảnh/đánh giá; offline: yêu thích hoạt động offline, gửi đánh giá cần mạng (giữ nội dung, cho thử lại)

**Scale/Scope**: Quy mô đồ án (vài chục người dùng demo, vài cơ sở, ~100 đánh giá); 6 màn hình + 1 bottom sheet; 1 Edge Function (5 action); Rules cho 2 collection; 1 bucket

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | `feature/review/{ui,data,domain}`; Fragment → ViewModel → UseCase → Repository → Firestore/Supabase; `StateFlow<UiState>`; nhóm khác dùng `FavoriteRepository`/`ReviewRepository` qua interface ở `domain/` ([ui-screens.md](contracts/ui-screens.md)) |
| II. Bảo mật phía server | ✅ | Client không ghi `reviews`; Edge Function kiểm tra quyền và điều kiện; `ratingAvg`/`ratingSums` 🔒; RLS chặn ghi ảnh ngoài thư mục `uid`; Rules favorites chỉ chủ tài khoản ([security-rules.md](contracts/security-rules.md)) |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ (áp dụng tinh thần) | Không đụng tới giữ chỗ/đặt sân. Cập nhật tổng điểm dùng transaction; `reviewId = bookingId` để idempotent; thời gian dùng server timestamp |
| IV. Kiểm thử có trọng tâm | ✅ | Unit test ViewModel/UseCase; test Rules R1–R9, F1–F4; test RLS S1–S6; test Edge Function gồm 10 lời gọi đồng thời ([quickstart.md](quickstart.md)) |
| V. Giao diện XML nhất quán | ✅ | XML + Material 3, DayNight; chuỗi trong `strings.xml` tiền tố `review_`/`favorite_`; 4 trạng thái mỗi màn hình; `ListAdapter` + `DiffUtil`; `w600dp`; `contentDescription` cho sao/trái tim |
| VI. Quyền riêng tư | ✅ | Photo Picker không cần quyền bộ nhớ; camera xin lúc dùng; đánh giá của người xóa tài khoản được ẩn danh (R9); đánh giá báo cáo được (FR-027, spec 120) |
| VII. Đơn giản, minh bạch AI | ✅ | Không thêm thư viện ngoài danh sách constitution; một Edge Function thay vì nhiều; log AI trong `docs/ai-log/AI_log_3.md` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thiết kế có thêm trường 🔒 (`starBucket`, `hasPhotos`, `hasOwnerReply`, `ratingSums`) chỉ để phục vụ truy vấn và tính điểm, không thêm tầng hay module mới.

## Phối hợp với nhóm (phải xong trước khi code phần liên quan)

| Với | Việc | Hạn |
|---|---|---|
| B | Thêm `bookings.completedAt` 🔒 (R3) | 14/10 |
| D | Thêm `venues.ratingSums` 🔒; tách Rules `venues` thành `get` mọi trạng thái / `list` chỉ `ACTIVE` (R6); dùng `applyReviewVisibility()` khi ẩn đánh giá (R10); nhận 3 loại thông báo `REVIEW_NEW`, `REVIEW_REPLY`, `REVIEW_REMINDER` (R8) | 14/10 |
| A | `delete-account` ẩn danh hóa đánh giá (R9); đặt nút trái tim, "Xem tất cả" ở spec 030; mục "Sân yêu thích" ở spec 011 | 14/10 |
| Cả nhóm | PR chung `chore(core)`: Hilt, Firebase, Supabase client, `core/model/VenueSummary` (R12) | Trước khi bắt đầu code |
| C (mình) | Cập nhật đặc tả CSDL theo danh sách cuối [data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd) | 14/10 |

## Project Structure

### Documentation (this feature)

```text
specs/070-review-favorite/
├── spec.md
├── plan.md                         # file này
├── research.md                     # Phase 0
├── data-model.md                   # Phase 1
├── quickstart.md                   # Phase 1
├── contracts/
│   ├── edge-function-review.md     # API Edge Function `review`
│   ├── security-rules.md           # Firestore Rules + RLS bucket review-photos
│   └── ui-screens.md               # Màn hình, Safe Args, điểm nối với spec khác
├── checklists/requirements.md
└── tasks.md                        # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── core/
│   └── model/VenueSummary.kt                  # dùng chung (PR core)
└── feature/review/
    ├── domain/
    │   ├── model/                             # Review, Ratings, OwnerReply, VenueRatingSummary, FavoriteVenue, ReviewDraft
    │   ├── ReviewRepository.kt                # interface
    │   ├── FavoriteRepository.kt              # interface
    │   ├── ReviewEligibility.kt               # CAN_WRITE / CAN_VIEW / EXPIRED / NOT_ELIGIBLE
    │   └── usecase/                           # SubmitReview, UpdateReview, DeleteReview, ReplyToReview, ToggleFavorite, ...
    ├── data/
    │   ├── FirestoreReviewRepository.kt       # đọc reviews; gọi Edge Function `review`
    │   ├── FirestoreFavoriteRepository.kt
    │   ├── ReviewPhotoUploader.kt             # nén + tải lên Supabase Storage
    │   ├── dto/                               # ReviewDto, FavoriteDto, request/response Edge Function
    │   └── di/ReviewModule.kt                 # Hilt bindings
    └── ui/
        ├── write/WriteReviewFragment.kt, WriteReviewViewModel.kt
        ├── list/VenueReviewsFragment.kt, VenueReviewsViewModel.kt, ReviewAdapter.kt
        ├── owner/OwnerReviewsFragment.kt, OwnerReviewsViewModel.kt, ReplyReviewBottomSheet.kt
        ├── favorite/FavoriteVenuesFragment.kt, FavoriteVenuesViewModel.kt, FavoriteVenueAdapter.kt
        └── photo/PhotoViewerFragment.kt

app/src/main/res/
├── layout/   fragment_review_write.xml, fragment_review_list.xml, item_review.xml,
│             fragment_review_owner_list.xml, bottom_sheet_review_reply.xml,
│             fragment_favorite_list.xml, item_favorite_venue.xml, fragment_review_photo_viewer.xml
├── navigation/nav_review.xml
└── values/strings_review.xml

app/src/test/java/com/example/booking_app/feature/review/     # unit test ViewModel, UseCase, ReviewEligibility
app/src/androidTest/java/com/example/booking_app/feature/review/  # Espresso: viết đánh giá

supabase/
├── functions/review/index.ts                  # Edge Function
├── functions/review/index.test.ts             # deno test
├── functions/_shared/reviews.ts               # tính tổng điểm, applyReviewVisibility()
└── migrations/<timestamp>_review_photos.sql   # bucket + RLS

firestore.rules                                # phần reviews, favorites
firestore.indexes.json                         # 5 index mới
firestore-tests/reviews.test.ts, favorites.test.ts
scripts/seed/070-review.ts                     # dữ liệu demo cho quickstart
```

**Structure Decision**: Một package tính năng `feature/review` cho cả đánh giá và yêu thích (cùng nhóm 07), theo nguyên tắc I. Mã server nằm trong `supabase/functions/review` cùng `_shared/` như constitution quy định. Rules và index dùng chung file gốc của repo, mỗi nhóm sửa phần của mình.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
