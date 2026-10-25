# Quickstart: kiểm chứng spec 011

Hướng dẫn chạy và kiểm tra. Không chứa code triển khai. Chi tiết dữ liệu và API: [data-model.md](data-model.md), [contracts/](contracts/).

## 0. Chuẩn bị

- Mọi bước chuẩn bị của [quickstart 010 §0](../010-account-auth/quickstart.md#0-chuẩn-bị) (máy, `google-services.json`, `local.properties`, Firebase Console).
- Spec 010 đã chạy được: đăng ký, đăng nhập, `on-signup`, `SessionManager`.
- **Mọi kịch bản ở §5 chạy cục bộ** (constitution IV): `local.properties` có `USE_EMULATOR=true` và `SUPABASE_URL=http://10.0.2.2:54321`; Firebase Emulator (Auth, Firestore, project `demo-smashnow`); `npx supabase start` + `npx supabase functions serve`. Không chạy trên `mobile-22e6c` hay Supabase `smashnow` thật.
- **Chưa chốt:** `supabase/config.toml` để Third-party Auth trỏ `mobile-22e6c`, nên Supabase local dự kiến từ chối ID token không ký của Auth Emulator. Kiểm tra theo task chuẩn bị môi trường trong [tasks.md](tasks.md) Phase 1; nếu bị từ chối thì Q5, Q8, Q9 chờ nhóm chốt cách làm, chính sách Storage vẫn được kiểm bằng pgTAP (§2).
- Supabase secrets thêm: `CRON_SECRET` (dùng chung với 081). `SUPABASE_SERVICE_ROLE_KEY` có sẵn trong môi trường Edge Function.
- Dữ liệu mẫu: seed chung `cd scripts/seed && node seed.mjs --emulator` (22 quận/huyện TP.HCM). Khu vực `isActive = false` để test: tạo tay `districts/q-cu` trên Emulator UI hoặc dùng dữ liệu của `firestore-tests`.
- Tài khoản thử (tạo trên Auth Emulator bằng app hoặc REST + `on-signup`, cấp vai trò bằng `scripts/admin/grant-role.ts` của 010):

| Tài khoản | Đăng nhập bằng | Trạng thái |
|---|---|---|
| `player1` | Email/mật khẩu | `ownerStatus = NONE` |
| `player-pending` | Email/mật khẩu | Hồ sơ chủ sân `PENDING` |
| `player-rejected` | Email/mật khẩu | Hồ sơ `REJECTED` có `rejectReason` |
| `player-revoked` | Email/mật khẩu | `ownerStatus = NONE`, `ownerRevokedReason` có giá trị, hồ sơ cũ `APPROVED` |
| `owner1` | Email/mật khẩu | `appRole = OWNER`, `APPROVED`, 1 cơ sở, có đơn và buổi vãng lai của khách |
| `google1` | Google | Không có mật khẩu |
| `phone1` | Số thử nghiệm | Không có mật khẩu, không có email |
| `admin1` | Email/mật khẩu | `appRole = ADMIN` |

## 1. Test Security Rules (bắt buộc)

```bash
cd firestore-tests
npm test
```

Kỳ vọng: P1–P18, D1–D4, A1 của [security-rules.md](contracts/security-rules.md#4-bảng-test) cùng U1–U18 của 010 đều xanh.

## 2. Test chính sách Storage (bắt buộc, constitution II)

```bash
npx supabase start
npx supabase db reset        # áp migration avatars
npx supabase test db         # pgTAP
```

Kỳ vọng: ST1–ST8 của [storage-avatars.md](contracts/storage-avatars.md#4-test-pgtap-giả-lập-jwt) đều xanh.

## 3. Test Edge Functions (bắt buộc)

```bash
npx firebase-tools@15 emulators:start --only auth,firestore --project demo-smashnow
# terminal khác
cd supabase/functions
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
FIREBASE_PROJECT_ID=demo-smashnow deno test -A delete-account/ delete-account-sweeper/ sync-account-email/ _shared/
```

Kỳ vọng: X1–X16, E1–E3 của [edge-functions-account.md](contracts/edge-functions-account.md#5-test-deno-test-firebase-emulator-auth--firestore) đều xanh. X7 (transaction lỗi thì không đổi gì) và X10 (gọi lặp, gọi song song) chạy 10 lần liên tiếp đều xanh.

## 4. Unit test app

```bash
./gradlew testDebugUnitTest --tests "*account*" --tests "*session*"
```

Phạm vi bắt buộc:
- Rule kiểm tra dữ liệu nhập: `DistrictSelectionRule`, `DifferentPasswordRule`, `DeleteConfirmRule`, `AvatarImageRule`.
- Suy ra `OwnerSection` cho đủ 6 dòng của [research R6](research.md#r6-mục-chủ-sân-trên-trang-tài-khoản-fr-008-fr-009-fr-013).
- `AppModeController`: OWNER mặc định; chuyển sang PLAYER không gọi mạng; chuyển sang OWNER khi claim đã mất thì ở lại PLAYER và trả `OwnerRevoked`; mất mạng thì dùng claim đang có.
- `FirebaseSessionManager.signOut()`: gọi mọi `SignOutHook` trước `FirebaseAuth.signOut()`, hook lỗi vẫn đăng xuất; `ERROR_USER_TOKEN_EXPIRED` → `SignedOut(SESSION_REVOKED)`.
- ViewModel (Turbine): `EditProfileViewModel` (dirty, lưu một lần, lỗi khu vực không chặn ô khác, thứ tự tải ảnh), `ChangePasswordViewModel`, `ChangeEmailViewModel`, `DeleteAccountViewModel` (nút chỉ bật khi đủ 3 điều kiện, `IMPACT_CHANGED` bỏ đánh dấu, `500` giữ nguyên màn hình), `AccountViewModel` (ẩn mục theo provider và vai trò).

## 5. Kịch bản thủ công trên máy

| # | Kịch bản | Kỳ vọng | Spec |
|---|---|---|---|
| Q1 | `player1` mở Tài khoản | Đủ thông tin, chip trình độ và khu vực, ví 0 đ có nhãn giả lập, banner xác minh nếu chưa xác minh | US1-1, FR-001, FR-001a |
| Q2 | Sửa tên, chọn "Trung bình", 2 khu vực, Lưu; tắt hẳn app, mở lại | "Đã lưu"; thông tin mới còn; đo thời gian < 1 phút | US1-2, SC-001 |
| Q3 | Sửa rồi bấm Back | Hỏi "Bỏ các thay đổi chưa lưu?" | US1-5, FR-006 |
| Q4 | Bấm Lưu 3 lần nhanh | Chỉ 1 lần ghi (xem Firestore `updatedAt`) | Edge Case |
| Q5 | **[BLOCKED: cả nhóm]** Supabase local từ chối token của Auth Emulator (`"alg" ... not allowed`, xem tasks T005); cần nhóm chốt cách thử Storage. Chọn ảnh từ thư viện, cắt, xác nhận | Ảnh mới hiện ở Tài khoản < 5 giây trên 4G (đo 20 lần, ≥ 19 lần đạt); object mới `avatar_<ms>.jpg`, object cũ bị xóa | US1-3, SC-002 |
| Q6 | Từ chối quyền camera | Có giải thích, vẫn chọn thư viện được, trang vẫn dùng được | US1-4, FR-024 |
| Q7 | Chọn file HEIC hoặc GIF | "Chỉ hỗ trợ ảnh JPG, PNG, WebP" | Edge Case |
| Q8 | **[BLOCKED: cả nhóm]** Supabase local từ chối token của Auth Emulator (`"alg" ... not allowed`, xem tasks T005); cần nhóm chốt cách thử Storage. Tải trực tiếp file 3 MB lên bucket của Supabase local (`http://127.0.0.1:54321`) bằng `curl` với token của mình | Storage từ chối (giới hạn 2 MB) | FR-003 |
| Q9 | **[BLOCKED: cả nhóm]** Supabase local từ chối token của Auth Emulator (`"alg" ... not allowed`, xem tasks T005); cần nhóm chốt cách thử Storage. Bật chế độ máy bay giữa lúc tải ảnh | Ảnh cũ giữ nguyên, có "Thử lại" | Edge Case, SC-007 |
| Q10 | Danh mục khu vực lỗi (tắt mạng khi mở picker) | Mục khu vực báo lỗi + "Thử lại", ô khác vẫn sửa được | Edge Case |
| Q11 | Đăng xuất, mở lại app | Về Trang chủ chế độ khách, Back không quay lại màn hình của tài khoản cũ; token của máy không còn trong `fcmTokens` | US2, FR-007 |
| Q12 | Đăng xuất rồi gửi thông báo thử tới tài khoản đó (D hoặc FCM console với token cũ) | Máy không nhận | US2-3 |
| Q13 | 5 tài khoản (`player1`, `player-pending`, `player-rejected`, `player-revoked`, `owner1`) mở Tài khoản | Đúng nội dung và nút theo FR-008 | US3, SC-004 |
| Q14 | Admin (D) duyệt `player-pending` khi người đó đang mở app, quay lại trang Tài khoản | Trạng thái mới, không đăng xuất | US3-5, FR-009 |
| Q15 | `owner1` chuyển "Người chơi" rồi "Quản lý sân" 5 lần | Mỗi lần giao diện đổi ≤ 1 giây | US4, SC-003 |
| Q16 | Thu hồi quyền `owner1` (`grant-role.ts PLAYER` + đặt `ownerRevokedReason`), bấm "Quản lý sân" | Không vào được, về giao diện người chơi, hiện lý do | US4-4, FR-012 |
| Q17 | `owner1` tắt app khi đang ở chế độ người chơi, mở lại | Vào giao diện quản lý sân | FR-011 |
| Q18 | `admin1`, `google1`, `phone1` mở Tài khoản | Admin: không có mục chủ sân, chuyển chế độ, xóa tài khoản. Google/phone: không có Đổi mật khẩu, Đổi email | FR-013, FR-015 |
| Q19 | Đổi mật khẩu: sai mật khẩu hiện tại; mới trùng cũ; hợp lệ | Lần lượt "Mật khẩu hiện tại không đúng", "phải khác", thành công; đăng nhập lại bằng mật khẩu mới được, cũ không được | US5 |
| Q20 | Đổi mật khẩu khi cùng tài khoản đang đăng nhập ở máy thứ hai | Máy thứ hai bị đăng xuất với "Phiên đăng nhập đã hết hạn" | R9 |
| Q21 | Đổi email sang địa chỉ mới, bấm link | Máy bị đăng xuất; đăng nhập bằng email mới được, cũ không được; `users.email` = email mới sau khi đăng nhập lại | US7-1, R10 |
| Q22 | Đổi email sang email của tài khoản khác | Cùng thông báo như Q21, không có thư; email không đổi. **Ghi kết quả thật vào log AI** | US7-2, R10 |
| Q23 | `player1` có 2 đơn sắp tới, 1 lượt vãng lai, ảnh, yêu thích, 1 đánh giá → Xóa tài khoản | Màn hình xem trước đúng số đơn và tiền hoàn; nút chỉ bật khi đủ điều kiện; xóa xong về màn hình chào; không đăng nhập lại được; 2 đơn `CANCELLED`, slot trống; đánh giá hiện "Người dùng đã xóa"; ảnh không còn | US6-1..5, SC-005 |
| Q24 | Bật chế độ máy bay rồi bấm "Xóa vĩnh viễn" | Báo lỗi, tài khoản dùng bình thường | FR-021, SC-007 |
| Q25 | `owner1` xóa tài khoản | Cơ sở không còn khi tìm kiếm; mọi khách nhận thông báo hủy và ví được hoàn 100%; slot trống | US6-6, SC-008 |
| Q26 | Mở trang xem trước, dùng máy khác đặt thêm 1 đơn, quay lại bấm xóa | Màn hình cập nhật số đơn mới, phải đánh dấu lại | R11 (`IMPACT_CHANGED`) |
| Q27 | Chế độ tối, máy tính bảng `w600dp`, TalkBack trên 6 màn hình, 2 bottom sheet và hộp thoại xác nhận danh tính | Hiển thị đúng; đọc được nhãn ảnh và nút | FR-025 |
| Q28 | Mở Sửa hồ sơ trên 2 máy cùng tài khoản; máy 1 đổi tên, máy 2 đổi trình độ, lưu lần lượt; mở lại trang Tài khoản trên cả hai | Cả hai thay đổi đều còn (mỗi lần lưu chỉ ghi trường đã đổi); trường cùng sửa ở hai máy thì giữ lần lưu sau cùng | Edge Case "hai máy" |

## 6. Hoàn tất

- `./gradlew lint testDebugUnitTest assembleDebug` qua.
- `npm test` (Rules), `npx supabase test db` (Storage), `deno test` (Edge Functions) qua.
- Ảnh hoặc video Q1, Q5, Q13, Q15, Q23, Q25 lưu vào `docs/features/01-account/screenshots/`.
- Log AI ghi vào `docs/ai-log/`.
