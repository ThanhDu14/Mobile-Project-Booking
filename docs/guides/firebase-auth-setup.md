# Cấu hình Firebase Authentication

Cấu hình Authentication của project **`mobile-22e6c`**, theo [quickstart §0 của spec 010](../../specs/010-account-auth/quickstart.md#0-chuẩn-bị). Người quản lý project làm một lần; mỗi thành viên chỉ cần tự thêm SHA của máy mình theo [firebase-setup.md](firebase-setup.md#2-thêm-sha-1-của-máy-mình).

Ngày làm: **2026-10-20**. Mục Email/Password, Google, Phone, Email enumeration protection và SHA bật trên Console; password policy, số thử, ngôn ngữ và tên người gửi đặt bằng Identity Toolkit API (`admin/v2/projects/mobile-22e6c/config`).

| Mục | Giá trị đã đặt |
|---|---|
| Sign-in method | Email/Password, Google, Phone đã bật |
| SMS region policy | Chỉ cho gửi tới `VN` |
| Phone numbers for testing | 1 số, mã OTP chia trong nhóm chat, **không** ghi vào repo |
| Email enumeration protection | Bật |
| User account linking | "Link accounts that use the same email" (mặc định) |
| Password policy | Tối thiểu 8 ký tự, bắt buộc có số, chế độ **Require** (`ENFORCE`), không bắt đổi mật khẩu cũ khi đăng nhập |
| Templates | Ngôn ngữ mặc định `vi`, tên người gửi `SmashNow` |
| SHA | 3 SHA-1, 4 SHA-256 |

## Lưu ý về template email

Firebase không cho sửa tiêu đề và nội dung template (lỗi `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`), chỉ đổi được tên người gửi. Email gửi đi dùng bản dịch tiếng Việt có sẵn của Firebase theo ngôn ngữ mặc định `vi`. Muốn tự viết nội dung thì phải cấu hình SMTP hoặc tên miền riêng.

## Kiểm tra lại cấu hình

```bash
curl -s -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: mobile-22e6c" \
  https://identitytoolkit.googleapis.com/admin/v2/projects/mobile-22e6c/config
```

Kết quả có cả số thử và client secret của Google, không dán vào repo hay nhóm chat.
