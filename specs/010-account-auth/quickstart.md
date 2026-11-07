# Quickstart: kiểm chứng spec 010

Hướng dẫn chạy và kiểm tra. Không chứa code triển khai. Chi tiết dữ liệu và API: [data-model.md](data-model.md), [contracts/](contracts/).

## 0. Chuẩn bị

**Trên máy:**
- Node 20+, Deno 2, Docker (cho `supabase start`), JDK 17+, Android Studio.
- `google-services.json` của project `mobile-22e6c` đặt tại `app/`. Không commit file này.
- `local.properties` có `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` (xin trong nhóm chat) và `USE_EMULATOR=true` khi chạy với Emulator. Secrets Gradle Plugin đọc các khóa này vào `BuildConfig`; thiếu khóa nào thì dùng giá trị giả trong `local.defaults.properties` (được commit).

**Cấu hình Firebase Console (người quản lý project làm một lần, ghi lại ngày làm vào `docs/guides/`):**

| Mục | Giá trị |
|---|---|
| Sign-in method | Bật Email/Password, Google, Phone |
| Phone numbers for testing | Tối đa 10 số dạng `+84 9x xxx xxxx` hư cấu, mã 6 số khó đoán. Chia số trong nhóm chat, **không** ghi vào repo |
| Settings → User actions | **Email enumeration protection** bật (mặc định) |
| Settings → User account linking | "Link accounts that use the same email" (mặc định) |
| Settings → Password policy | Min length 8, Require numeric, chế độ **Require** |
| Templates | Ngôn ngữ tiếng Việt cho email xác minh và đặt lại mật khẩu |
| Project settings → SHA | SHA-1 debug của từng thành viên (`./gradlew signingReport`) |

**Supabase secrets:** `FIREBASE_PROJECT_ID`, `FIREBASE_SERVICE_ACCOUNT`. Dùng chung với B, đặt bằng `npx supabase secrets set`, không commit.

## 1. Test Security Rules (bắt buộc, nguyên tắc II và IV)

```bash
cd firestore-tests
npm test           # chạy Emulator Firestore + vitest
```

Kỳ vọng: 3 test nền và U1–U18 của [security-rules.md](contracts/security-rules.md#3-bảng-test-mỗi-dòng-ít-nhất-một-test-theo-đặc-tả-csdl-mục-7) đều xanh.

## 2. Test Edge Function `on-signup` (bắt buộc)

```bash
npx firebase-tools@15 emulators:start --only auth,firestore --project demo-smashnow
# terminal khác
cd supabase/functions
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
FIREBASE_PROJECT_ID=demo-smashnow deno test -A on-signup/ _shared/
```

Kỳ vọng: S1–S12 trong [edge-function-on-signup.md](contracts/edge-function-on-signup.md#6-test-deno-test-firebase-emulator-auth--firestore) đều xanh. S8 (chữ ký) chạy ở chế độ không có Emulator, với JWKS giả.

Chạy thử bằng tay trên Supabase local:

```bash
npx supabase start
npx supabase functions serve on-signup --env-file supabase/.env --no-verify-jwt
# lấy ID token từ Auth Emulator (signUp REST), rồi:
curl -X POST http://127.0.0.1:54321/functions/v1/on-signup \
  -H "Authorization: Bearer <idToken>" -H "Content-Type: application/json" \
  -d '{"displayName":"Người Thử","termsAccepted":true}'
# → {"status":"CREATED","appRole":"PLAYER"}; gọi lại → ALREADY_INITIALIZED
```

## 3. Unit test app

```bash
./gradlew testDebugUnitTest --tests "*auth*" --tests "*session*"
```

Phạm vi bắt buộc:
- `NameRule`, `EmailRule`, `PasswordRule`, `PhoneRule` (chuẩn hóa `0…` thành `+84…`), `OtpRule`.
- `OtpSession`: hết 5 phút, sai 5 lần.
- `FirebaseSessionManager` (fake `AuthDataSource`): mọi nhánh của `SessionState`.
- `RegisterViewModel`, `LoginViewModel`, `OtpViewModel`, `TermsConsentViewModel` với Turbine: bấm hai lần chỉ gửi một yêu cầu; hết 15 giây thì báo lỗi mạng; tạo tài khoản thành công sau timeout thì không tạo lại.

## 4. Kịch bản kiểm thử thủ công trên máy (với project thật, tài khoản thử)

| # | Kịch bản | Kỳ vọng | Spec |
|---|---|---|---|
| M1 | Cài mới, Đăng ký email mới | Vào màn hình chính người chơi < 2 phút, có banner xác minh; Firestore có `users/{uid}` với `termsAcceptedAt`; token có `appRole: PLAYER`, `role: authenticated` | US1, SC-001, SC-004 |
| M2 | Bấm link xác minh trong email, quay lại app | Banner biến mất | US1-5 |
| M3 | Đăng ký lại cùng email | Lỗi dưới ô email kèm hai link | US1-3 |
| M4 | Tắt mạng sau khi Firebase tạo tài khoản, trước `on-signup` (bật chế độ máy bay ngay khi bấm) | Màn hình chờ kèm "Thử lại"; mở lại app thì tự hoàn tất | Edge Case khởi tạo lỗi |
| M5 | Đăng nhập sai mật khẩu, và email không tồn tại | Cùng một thông báo "Email hoặc mật khẩu không đúng" | FR-011 |
| M6 | Tắt hẳn app rồi mở lại | Vào thẳng màn hình chính | US2-5 |
| M7 | Google lần đầu (`@gmail.com`) | Bước Hoàn tất tài khoản; xác nhận xong vào màn hình chính; tối đa 3 lần chạm khi đăng nhập lại | US4, SC-002 |
| M8 | Google lần đầu rồi đóng app ở bước đồng ý | Mở lại thì quay về bước đồng ý | Edge Case |
| M9 | Tài khoản email/mật khẩu `@gmail.com` **đã xác minh**, đăng nhập Google cùng email | Cùng `uid` (so trong Firebase Console), có 2 provider | FR-012, R7 |
| M10 | Tài khoản email/mật khẩu `@gmail.com` **chưa xác minh**, đăng nhập Google cùng email | Cùng `uid`; provider password bị gỡ (đúng như R7 dự đoán, ghi kết quả thật vào log AI) | FR-012, R7 |
| M11 | Hủy bottom sheet Google | Ở lại màn hình, không có thông báo lỗi | US4-4 |
| M12 | Số thử nghiệm, mã đúng, số mới | Bước đồng ý, rồi vào màn hình chính | US5 |
| M13 | Nhập sai mã 5 lần | Thông báo, phải "Gửi lại mã" | US5-3 |
| M14 | Chờ hơn 5 phút rồi nhập mã | "Mã đã hết hạn" | US5-4 |
| M15 | Số thật (không phải số thử nghiệm) | "Không gửi được mã… dùng email hoặc Google" | Edge Case |
| M16 | Quên mật khẩu với email có và không có tài khoản | Cùng thông báo; nút gửi lại khóa 60 giây; email có tài khoản nhận được thư | US3, FR-018 |
| M17 | Đặt mật khẩu mới `abcdefgh` trên trang của Firebase | Bị từ chối (thiếu số) theo password policy | FR-019, R9 |
| M18 | D khóa tài khoản bằng `set-account-status` (hoặc tạm thời bật cờ disabled trong Console và đổi `accountStatus`), rồi đăng nhập bằng email, Google, OTP | Đều báo "Tài khoản của bạn đã bị khóa" | FR-014, SC-005 |
| M19 | Đang mở app thì bị khóa | Bị đăng xuất ngay (listener), hoặc ở lần mở app tiếp theo | US2-7 |
| M20 | Thử tài khoản bị khóa nhập **sai** mật khẩu | Ghi lại Firebase trả `INVALID_LOGIN_CREDENTIALS` hay `USER_DISABLED` (kiểm chứng việc lộ email bị khóa) | FR-011 |
| M21 | Admin cấp `appRole: OWNER` (script), mở lại app | Vào giao diện chủ sân; thu hồi thì về giao diện người chơi | FR-015, Edge Case vai trò đổi |
| M22 | Tài khoản admin đăng nhập | Vào `nav_admin` | US2-3 |
| M23 | Đăng nhập xong bấm Back | Thoát app, không quay lại màn hình đăng nhập | Edge Case |
| M24 | Chế độ tối, máy tính bảng (`w600dp`), TalkBack | Hiển thị đúng; ô và nút đọc được nhãn | FR-023 |
| M25 | Mất mạng ở từng nút (Đăng ký, Đăng nhập, Gửi mã, Gửi) | Không crash; snackbar "Thử lại"; giữ dữ liệu trừ mật khẩu và OTP | FR-021, SC-007 |

## 5. Cấp tài khoản admin (thủ công, constitution II)

Chạy script `scripts/admin/grant-role.ts <uid> ADMIN` bằng service account trên máy người quản lý. Script gộp claim `appRole` rồi ghi `users.role`. Không có màn hình nào trong app làm được việc này.

## 6. Hoàn tất

- `./gradlew lint testDebugUnitTest assembleDebug` qua (cổng PR của constitution).
- Ảnh hoặc video M1, M7, M12, M18 lưu vào `docs/features/01-account/screenshots/`.
- Log AI ghi vào `docs/ai-log/`.
