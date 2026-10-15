# Implementation Plan: Quản trị và kiểm duyệt

**Branch**: `feature/120-admin-plan` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: `specs/120-admin/spec.md`; Constitution 1.2.0; database README; assignment guide; related specs 010, 011, 100, 110, 111, 112 and content/report features.

## Summary

Feature 120 cung cấp công cụ Admin cho duyệt hồ sơ chủ sân, kiểm duyệt reports, quản lý trạng thái tài khoản/venue và quyền owner, xem số liệu quản trị, chỉnh danh mục, voucher hệ thống và gửi thông báo `SYSTEM`. App Android hiển thị dữ liệu; mọi quyền và thay đổi nhạy cảm phải được kiểm tra ở server. Giữ nguyên các collection/enum đã có trong spec và database README. Duyệt hồ sơ phải cập nhật hồ sơ, user, quyền owner và venue từ `venueDraft` như một kết quả nhất quán; xem [research.md](research.md) về cách hiện thực cần xác nhận trước khi code.

## Technical Context

**Language/Version**: Kotlin, Android `minSdk 29`, `compileSdk/targetSdk 37`; TypeScript/Deno chỉ cho Supabase Edge Functions theo Constitution.

**Primary Dependencies**: AndroidX Fragment, Navigation, ViewBinding, Material 3, Firebase Auth/Firestore/FCM; ViewModel, Coroutines/Flow, Hilt theo Constitution. Các dependency kiến trúc và test chưa được khai báo trong version catalog hiện tại. Không chọn thư viện Supabase Android hoặc dependency mới khi chưa xác định cần thiết.

**Storage**: Firestore collections hiện có `users`, `ownerApplications`, `venues`, `reports`, `districts`, `amenities`, `systemVouchers`, `systemStats`, `venues/{id}/dailyStats/{date}`, `users/{uid}/notifications`; giấy tờ trong Supabase private bucket `owner-docs`, qua signed URL 5 phút. Không dùng Supabase DB lưu dữ liệu nghiệp vụ.

**Testing**: JUnit4/MockK/coroutines-test/Turbine cho logic; Firebase Emulator cho Rules và xung đột ghi; Supabase local cho function/RLS; Espresso cho luồng duyệt chính. Hiện source chỉ có starter Android, `firestore.rules` là skeleton, `firestore-tests` là scaffold Vitest và chưa có `supabase/functions`.

**Target Platform**: Android app single Activity, Fragment/XML/ViewBinding; Supabase Edge Runtime cho quyền/ghi nhạy cảm và tác vụ server đã được quy định.

**Project Type**: Ứng dụng Android và backend serverless hiện hữu theo kiến trúc Firebase + Supabase.

**Performance Goals**: Không có ngưỡng riêng cho quản trị; danh sách dùng phân trang/index và ưu tiên tải dưới 3 giây trên 4G theo Constitution. Hồ sơ đầy đủ có thể được duyệt trong tối đa 2 phút (SC-006).

**Constraints**: Chỉ Firebase Spark và Supabase Free. Không có Cloud Functions/Storage. Không cho phép cache làm căn cứ quyết định; thao tác timeout phải tải lại trạng thái trước thử lại. Không thêm trạng thái, trường, API, dịch vụ hoặc quy trình nghiệp vụ ngoài spec và tài liệu CSDL.

**Scale/Scope**: Một khu vực quản trị trong app và các thao tác trên các collection D quản lý; quy mô demo. Số liệu hệ thống, quy tắc danh mục, fan-out và giới hạn phân trang chưa được định nghĩa.

## Constitution Check

| Nguyên tắc | Đánh giá | Thiết kế/điều kiện |
|---|---|---|
| I. MVVM, package theo feature | Đạt theo kế hoạch | `feature/admin/{ui,data,domain}`; Fragment → ViewModel → repository; StateFlow và đủ loading/content/empty/error; không gọi Firebase từ UI/ViewModel. |
| II. Quyền server-side | Đạt có điều kiện | `appRole=ADMIN` lấy từ Firebase token, xác thực lại cho từng lần đọc/ghi. Firestore Rules bảo vệ client access; Edge Function thực hiện mutation nhạy cảm và đổi custom claim; signed URL private chỉ cho Admin/người nộp, hết hạn sau 5 phút. Rules hiện chỉ là skeleton nên phải bổ sung collection rules và allow/deny Emulator tests trước khi phát hành. |
| III. Toàn vẹn đặt sân | Không tác động trực tiếp | Không sửa máy trạng thái booking/slot. Khóa venue chỉ cập nhật trạng thái venue; không tự đổi booking hoặc venue khi thu hồi owner, theo spec. |
| IV. Kiểm thử trọng tâm | Đạt theo kế hoạch | Test ViewModel/repository, Rules, quyền claim, race quyết định, signed file access và Edge Function. Emulator/local only; không dùng project thật. |
| V. XML/accessibility | Đạt theo kế hoạch | Dùng XML, ViewBinding, Material 3, resource tiếng Việt, RecyclerView/ListAdapter, DayNight, vùng chạm ≥48dp và trạng thái đủ; không thiết kế UI mới ngoài wireframe/spec. |
| VI. Riêng tư | Đạt có điều kiện | Hồ sơ giấy tờ chỉ hiển thị cho admin/người nộp; user detail chỉ trình bày dữ liệu có trong schema. Không công khai signed URL, không log giấy tờ/lý do nhạy cảm. |
| VII. YAGNI | Đạt có điều kiện | Không thêm collection/schema/dependency. Danh mục, chỉ số thống kê, giao thức server và semantics chuyển claim cần được chốt trước implementation. |

**Gate trước Phase 0**: Đạt có điều kiện. Tài liệu nguồn đủ để chốt phạm vi và storage; thiếu hợp đồng mutation, thống kê/danh mục và ranh giới transactional giữa Firestore với Firebase Auth/Supabase. Các thiếu hụt được ghi thành quyết định hoặc câu hỏi trong [research.md](research.md), không được tự giải bằng schema mới.

**Gate sau Phase 1**: Đạt có điều kiện, cùng các blocker triển khai đã nêu. Data model dưới đây chỉ phản ánh schema hiện hành; không coi các vùng chưa định nghĩa là đã được thiết kế.

## Project Structure

### Tài liệu tính năng

```text
specs/120-admin/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── admin-boundaries.md
```

### Mã nguồn dự kiến (cấu trúc hiện có là starter)

```text
app/src/main/java/com/example/booking_app/feature/admin/{ui,data,domain}/
app/src/main/res/{layout,navigation,values,values-night}/
app/src/test/java/com/example/booking_app/feature/admin/
app/src/androidTest/java/com/example/booking_app/feature/admin/
firestore.rules
firestore.indexes.json
firestore-tests/test/
supabase/functions/                 # Edge Functions; repo hiện chưa có thư mục này
supabase/migrations/                # Storage RLS only where required; no business DB
```

**Structure Decision**: Noud theo single Android app, feature packages và backend Supabase đã định nghĩa. Đây là vị trí dự kiến, không phải khẳng định rằng package, navigation, backend functions hoặc test harness đã tồn tại. Không tạo Gradle module mới.

## Kế hoạch thiết kế

1. **Hàng đợi và quyết định hồ sơ**: đọc `ownerApplications` theo `status`/`submittedAt`, ưu tiên `PENDING`; mở detail và tải giấy tờ bằng signed access của `owner-docs`. Chỉ quyết định hồ sơ còn `PENDING`; từ chối cần `rejectReason` không rỗng. Duyệt tạo venue từ `venueDraft`, cập nhật user status/role và quyền server theo schema nguồn. Nộp lại thuộc Feature 110. Không cho quyết định dựa trên cache; xung đột buộc refresh.
2. **Reports và thực thi**: đọc `OPEN` reports, target theo `targetType` + `targetPath`; action chỉ từ enum đã định nghĩa. `HIDE_CONTENT` chỉ áp dụng loại nội dung hỗ trợ ẩn, `LOCK_USER` cho user, `LOCK_VENUE` cho venue. Ghi `status/action/handledBy/handledAt` nhất quán; `DISMISSED` dùng `NONE`. Nếu target đã mất/đổi trạng thái, không chạy tác động lặp.
3. **Tài khoản, owner, venue**: tra cứu user; `accountStatus` chỉ chuyển `ACTIVE`/`LOCKED`. Thu hồi owner yêu cầu lý do và `OWNER_REVOKED`, cập nhật `role=PLAYER`, `ownerStatus=NONE`, `ownerRevokedReason`; giữ application `APPROVED` và hiện trạng venue. Venue khóa/mở riêng theo lựa chọn `ACTIVE`/`HIDDEN`; owner không được tự mở khóa. Spec 010/112 tiếp tục quyết định hiệu lực đăng nhập/quyền vận hành.
4. **Dữ liệu quản trị**: đọc `systemStats` và `dailyStats` khi tồn tại, chỉ trình bày chỉ số có nguồn/thời điểm. Voucher dùng schema `systemVouchers` của Feature 050 và kiểm tra code duy nhất xuyên cả voucher sân; `usedCount`/`redemptions` do server quản lý. Danh mục chỉ ghi các collection `districts`/`amenities` đã được giao cho nhóm 12; thao tác cụ thể cần chốt.
5. **Thông báo**: phát `SYSTEM` qua luồng server của Feature 100 đến tất cả tài khoản đang hoạt động; notification tuân cấu trúc hiện có. Không phát thông báo thành công trước khi server xác nhận và không fan-out theo client.
6. **Lỗi/khôi phục**: phân biệt permission denied, validation, conflict/stale state, file unavailable/expired URL, timeout có kết quả chưa rõ và backend unavailable. Giữ lý do đang nhập; timeout thì đọc lại authoritative state trước retry. Không cho mutation offline.

## Dependency và thứ tự tích hợp

| Phụ thuộc | Dùng cho | Giao ước/ranh giới |
|---|---|---|
| 010 Account Auth | Firebase identity, claim `appRole`, khóa tài khoản | Spec 120 không làm đăng nhập; việc khóa phải có hiệu lực theo spec 010. |
| 011 Account Profile | Hiển thị owner bị thu hồi và lý do; entry points | Schema `ownerRevokedReason`, status `NONE`, nộp lại theo spec 110. |
| 100 Notification | `OWNER_APPROVED`, `OWNER_REJECTED`, `OWNER_REVOKED`, `SYSTEM` | Tạo inbox/push và semantics retry/fan-out cần đồng bộ; admin không ghi trực tiếp inbox từ client. |
| 110 Owner Onboarding | Application, `venueDraft`, re-submit, owner-docs | Duyệt/từ chối là tác động Feature 120; hồ sơ phải kiểm tra phiên bản/trạng thái hiện tại. |
| 111/112 Owner | Venue state và hiệu lực owner | Khóa venue/thu hồi quyền phải được các feature này phản ánh; không đổi quy tắc vận hành khác. |
| 050, 070, 090, 091 | Voucher schema và report targets | Không thay đổi quy tắc voucher/nội dung; chỉ hành động với target tương thích. |
| Firebase/Supabase local | Rules, Edge Functions, signed file access | Repo có Rules skeleton/test scaffold; Edge Functions chưa có. Không test với production. |

**Complexity Tracking**: Không có vi phạm được đề nghị. Không thêm dependency, collection hay module. Những API/server handlers phải dùng contract hiện hành sau khi xác định; chưa có căn cứ ghi tên endpoint mới.
