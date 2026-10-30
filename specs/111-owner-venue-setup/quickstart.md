# Hướng dẫn nhanh: Kiểm tra thiết lập cơ sở của chủ sân

Tài liệu này mô tả các tình huống kiểm tra sau khi triển khai. Chỉ chạy với fake cục bộ và Firebase Emulator Suite, không chạy trên dự án thật. Repo hiện mới có mã khởi tạo; tên task build/kiểm thử còn tùy cấu hình Gradle và Emulator khi được triển khai, vì vậy cần xác nhận từ cấu hình thực tế thay vì tự suy đoán.

## Điều kiện chuẩn bị

- Có dependency Android cần thiết và emulator/thiết bị để kiểm tra UI.
- Cấu hình Firebase Emulator Suite để chạy Rules/kiểm thử Firestore.
- Fixture kiểm thử: owner A đã duyệt, owner B đã duyệt, player chưa được duyệt, một venue với hai court, một price rule khớp; slot trống và các slot có trạng thái `HOLD`, `BOOKED`, `BLOCKED`.
- Không dùng dữ liệu người thật, dự án Firebase production hoặc dự án Supabase production.

## Các tình huống

1. **Cô lập quyền owner**: owner A đọc/quản lý được venue, court, bảng giá của mình; owner B không truy cập được dù đoán đúng ID; player/chưa duyệt không ghi được. Xác nhận yêu cầu ghi Firestore trực tiếp cũng bị từ chối, không chỉ thao tác trên UI.
2. **Vòng đời venue**: tạo/sửa venue hợp lệ, ẩn venue và xác nhận booking mới không còn khả dụng trong khi booking đã xác nhận giữ nguyên; owner không đổi được venue admin `LOCKED` hoặc sửa trường được bảo vệ.
3. **Vòng đời court**: thêm/sửa/sắp xếp và ngừng hoạt động court; xác nhận booking mới bị chặn và booking cũ được bảo toàn.
4. **Bảng giá**: chấp nhận thứ/giờ/giá hợp lệ; từ chối thứ sai, mốc không phải 30 phút, giờ qua đêm, giá không dương/không chia hết, label sai và rule chồng giờ; cho phép xóa; xác nhận giá booking item cũ không đổi và slot tương lai thiếu rule không đặt được.
5. **Cạnh tranh booking và khóa nguyên tử**: khóa thành công khoảng nhiều slot thì mọi slot ID tương ứng đều có `BLOCKED` cùng lý do được chọn. Nếu có slot đang giữ/đặt/khóa, không slot nào trong khoảng bị đổi. Cho giao dịch giữ booking cạnh tranh với giao dịch khóa owner nhiều lần; chỉ một bên thành công và bên thua nhận trạng thái mới.
6. **An toàn mở khóa**: mở khóa block còn khớp; xác nhận slot `HOLD`/`BOOKED`, slot đã đổi trạng thái và block `DROP_IN` không bị thao tác mở khóa của owner xóa.
7. **Mạng/lỗi**: mô phỏng dữ liệu cũ, từ chối quyền, timeout sau khi gửi yêu cầu và offline. Xác nhận biểu mẫu được giữ lại, cache không được coi là quyền hiện tại, thao tác chưa rõ kết quả phải tải lại server trước khi thử lại.
8. **UI/accessibility**: kiểm tra loading/content/empty/error/thử lại, chuỗi tiếng Việt từ resource, theme sáng/tối, điện thoại và `w600dp`, contentDescription có ý nghĩa và vùng chạm 48dp.

## Kết quả mong đợi

Đạt khi các tình huống trên đúng và đạt tiêu chí đo lường trong Feature 111: danh sách/lịch dưới 3 giây trên 4G; thao tác ghi phản hồi trong 1 giây và hoàn tất trong 3 giây khi mạng bình thường. Chỉ bổ sung câu lệnh kiểm thử cụ thể sau khi xác minh task Gradle và cấu hình Emulator; không tự đặt lệnh chưa được hỗ trợ.

## Lệnh đã chạy và bằng chứng hiện tại (2026-10-29)

- Android owner unit tests: `./gradlew.bat --no-daemon testDebugUnitTest --tests 'com.example.booking_app.feature.owner.*'` — **BUILD SUCCESSFUL**, 37 test cases.
- Firestore Emulator: `npx.cmd -y firebase-tools@15 emulators:exec --config ../firebase.json --project demo-smashnow --only firestore "vitest run"` — **6 files, 46 tests passed**.
- Price preview dùng `pricePerHour / 2`, phát hiện các mốc 30 phút thiếu rule và cảnh báo gap nếu xóa rule; không ghi `minPricePerHour` và không sửa snapshot `bookings.items[].price`.
- Slot UI hiện chỉ preview/mapping range, có stale/offline/loading/error và giữ form; nút ghi bị khóa ở boundary T007, không dùng read-then-write.

T007 (atomic lock/unlock server contract), T008 (trusted authority cập nhật `minPricePerHour`) và lifecycle tạo venue đầu tiên của T110/T120 vẫn là blocker đã biết. T034 chỉ có thể xác minh preservation của recurring group hiện có; việc không tạo chain mới thuộc Feature 041 và chưa có API test boundary trong Feature 111.

## Bổ sung kiểm chứng lượt 2026-10-29

- `./gradlew.bat --no-daemon testDebugUnitTest --tests 'com.example.booking_app.feature.owner.*'`: **BUILD SUCCESSFUL**.
- `./gradlew.bat --no-daemon assembleDebug lintDebug`: **BUILD SUCCESSFUL**; lint report được tạo tại `app/build/reports/lint-results-debug.html`.
- Firebase Emulator: **7 test files, 50 tests passed**, gồm `owner-price-rules.test.ts`.
- Đã kiểm thử permission-vs-empty, retained draft sau lỗi, duplicate submit và bảo vệ schema/booking snapshot. Chưa đo SLA 3 giây/1 giây trên thiết bị hoặc mạng 4G thật, nên không tuyên bố đạt performance SLA.
- T034 vẫn mở vì boundary Feature 041 hiện không cung cấp API/test để chứng minh việc `acceptsRecurring = false` ngăn tạo chain mới; chỉ chứng minh chain cũ không bị hủy.

## Bổ sung kiểm chứng lượt 2026-10-29 — T037/T038/T034

- Owner unit tests: `./gradlew.bat --no-daemon testDebugUnitTest --tests 'com.example.booking_app.feature.owner.*'` — **BUILD SUCCESSFUL**.
- Build/lint: `./gradlew.bat --no-daemon assembleDebug lintDebug` — **BUILD SUCCESSFUL**; lint report tại `app/build/reports/lint-results-debug.html`.
- Firebase Emulator: **7 test files, 50 tests passed**.
- T037 đã được kiểm thử thêm cho permission/network/conflict classification, authoritative read-back khi conflict, giữ draft, retry read-only và không retry mutation timeout. Ứng dụng hiện không có cache repository; `isFresh` chỉ báo dữ liệu vừa được xác nhận từ server, không coi dữ liệu cũ là quyền hiện tại.
- Chưa đo SLA list/calendar dưới 3 giây hoặc mutation acknowledgement trên thiết bị/mạng 4G thật; T038 không được đánh dấu hoàn thành chỉ dựa trên build/lint.
- T034 chưa được đánh dấu: Feature 041 hiện chỉ mô tả `acceptsRecurring` và transaction recurring ở spec/plan, chưa cung cấp boundary/API test cho điều kiện “không tạo chain mới”.

## Bổ sung kiểm chứng decision gates và T037 (2026-10-29)

- T007: UI submit trả typed gate `SLOT_ATOMIC_SERVER_CONTRACT`; Emulator assertion xác nhận client owner không thể tự ghi slot `BLOCKED`. Chưa có server transaction boundary nên không triển khai lock/unlock write.
- T008: `minPricePerHour` vẫn bị chặn ở client; `pricePerHour / 2` và booking snapshot đã có test, nhưng chưa có authority/no-rule policy để mở mutation.
- T011: nút tạo venue chỉ hiển thị lifecycle message; không có client create/bypass approval.
- T037: đã kiểm thử error classification, permission-vs-empty, conflict authoritative read-back, retained draft, no-blind-retry và freshness state. Ứng dụng không có cache repository; `isFresh` chỉ biểu thị server-confirmed data.
- T038: Vietnamese resources, accessibility content description, 48dp controls, phone/`w600dp`, light/dark-compatible resources và lint/build đã kiểm tra. Chưa đo SLA trên thiết bị/mạng 4G thật, nên T038 vẫn mở.
- T034: Feature 041 chưa cung cấp boundary/API cho việc chứng minh `acceptsRecurring = false` chặn recurring-chain creation; chỉ có thể giữ nguyên chain cũ.

## Bổ sung kiểm chứng Phase 3 — Server Atomic Slot Lock & Unlock (2026-10-30)

- **T007, T028, T029**: Triển khai Supabase Edge Function `manage-slot-blocks` chạy Firestore REST transaction / batch với service role.
  - Khóa nguyên tử (`action: "LOCK"`): Kiểm tra quyền chủ sân đã duyệt, kiểm tra quyền sở hữu sân, kiểm tra tất cả các slot không bị chiếm (`BOOKED`, `HELD`, `BLOCKED`); rollback nguyên tử nếu có xung đột; tạo slot `BLOCKED` với lý do hợp lệ (`MAINTENANCE`, `EVENT`, `WALK_IN`).
  - Mở khóa an toàn (`action: "UNLOCK"`): Chỉ mở khóa slot `BLOCKED` do chủ sân tạo; từ chối và bảo vệ tuyệt đối ca `DROP_IN` (Feature 112/080) và các slot `HOLD`/`BOOKED` (Feature 040).
  - Tích hợp Android Data Layer: `ManageSlotBlocksApi`, kết nối vào `OwnerVenueRepository` và `OwnerVenueRepositoryImpl`.
- **T030**: Hoàn thiện giao diện Calendar Grid UI shell:
  - Tạo `OwnerSlotGridAdapter` hiển thị danh sách slot theo khung giờ 30 phút.
  - Nâng cấp `OwnerSlotBlockViewModel` hỗ trợ chọn khung giờ trực quan, phân biệt trạng thái `AVAILABLE`, `BLOCKED`, `BOOKED`, `HELD`, nhận diện slot có thể gỡ (`isOwnerRemovable`).
  - Nâng cấp `fragment_owner_slot_block.xml` và tạo bản tối ưu `layout-w600dp/fragment_owner_slot_block.xml` cho máy tính bảng, đảm bảo tất cả touch targets $\ge 48\text{dp}$.
  - Hỗ trợ đầy đủ: vô hiệu hóa khi ngoại tuyến (`isOffline`), chỉ báo dữ liệu cũ (`isStale`), thanh tiến trình và ngăn chặn bấm lặp lại (`isSubmitting`), giữ nguyên biểu mẫu và lựa chọn khi gặp lỗi xung đột.
- **T031**: Bổ sung `firestore-tests/test/owner-slot-blocks.test.ts` (7 tests passed):
  - Chặn owner client ghi trực tiếp slot `BLOCKED` (bảo vệ bởi Security Rules).
  - Chặn owner client tự xóa slot `BLOCKED` trực tiếp (bảo vệ bởi Security Rules).
  - Đua transaction giữa owner lock và player hold (Feature 040 transaction boundary) đảm bảo cách ly và rollback toàn bộ khi xung đột.
  - Xung đột khoảng dở dang (partial-range conflict) rollback nguyên tử không để lại bản ghi rác.
  - Bảo vệ ca `DROP_IN` không bị owner gỡ.
  - Mở khóa an toàn ca `MAINTENANCE` xóa document slot thành công.
- **T038**: Kiểm tra tài nguyên chuỗi tiếng Việt, accessibility, theme Material 3, layout điện thoại và `layout-w600dp`, kích thước vùng chạm $\ge 48\text{dp}$. Lint report đạt 0 errors (`assembleDebug lintDebug` BUILD SUCCESSFUL). Tiêu chí SLA trên mạng 4G thật (danh sách/lịch < 3s, ghi phản hồi < 1s và hoàn tất < 3s) cần thiết bị thật có SIM 4G để đo đạc thực địa.
- **T034**: Tiếp tục mở chờ Feature 041 cung cấp mã nguồn ứng dụng để kiểm chứng tích hợp chặn chuỗi mới.

Xem [data-model.md](data-model.md) và [contracts/firestore-boundary.md](contracts/firestore-boundary.md) về trường dữ liệu và ranh giới phân quyền.
