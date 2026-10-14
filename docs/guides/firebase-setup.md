# Setup Firebase

Project dùng chung: **`mobile-22e6c`** (Mobile-Booking), app Android **SmashNow** với package `com.example.booking_app`. Chỉ dùng gói Spark, xem [constitution](../../.specify/memory/constitution.md).

Plugin `com.google.gms.google-services` và các thư viện Firebase đã khai báo trong Gradle, pull về và sync là có. Hai bước dưới đây không nằm trong git nên mỗi người tự làm một lần.

## 1. Tải `google-services.json`

1. Nhờ người quản lý project mời vào Firebase (Project settings → Users and permissions).
2. Vào [Firebase Console](https://console.firebase.google.com/project/mobile-22e6c/settings/general), mục **Your apps**, chọn app SmashNow, tải `google-services.json`.
3. Đặt file vào `app/google-services.json`.

Thiếu file này thì build báo lỗi `File google-services.json is missing`. File đã nằm trong `.gitignore`, **không commit**.

## 2. Thêm SHA-1 của máy mình

Google Sign-In và Phone Auth chỉ chạy trên máy có SHA-1 đã khai báo trong app.

1. Lấy SHA-1 và SHA-256 của debug keystore:

   ```bash
   ./gradlew signingReport
   ```

   Xem dòng `SHA1` và `SHA-256` của variant `debug`.
2. Trong cùng trang app SmashNow trên Console, bấm **Add fingerprint** và dán cả hai giá trị.

Không cần tải lại `google-services.json` sau khi thêm SHA.

## Thư viện đã khai báo

Qua Firebase BoM trong `gradle/libs.versions.toml`: `firebase-auth`, `firebase-firestore`, `firebase-messaging`, `firebase-appcheck-playintegrity`, và `firebase-appcheck-debug` (chỉ bản debug).

BoM đang ở **34.19.0**. Từ BoM 35.0.0, Firebase được biên dịch bằng Kotlin 2.4, còn AGP 9.4.1 đi kèm Kotlin 2.2 nên build lỗi `Module was compiled with an incompatible version of Kotlin`. Chỉ nâng BoM lên 35 khi project đã nâng Kotlin.

## Kiểm tra

```bash
./gradlew assembleDebug
```

Build thành công thì file `app/build/generated/res/processDebugGoogleServices/values/values.xml` có `project_id` là `mobile-22e6c`.
