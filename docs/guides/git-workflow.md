# Quy ước Git

Tóm tắt từ proposal mục 12.

## Nhánh

| Nhánh | Mục đích |
|---|---|
| `main` | Bản ổn định, chỉ merge từ `develop` |
| `develop` | Nhánh tích hợp |
| `feature/<NN>-<ten>` | Mỗi tính năng một nhánh, ví dụ `feature/04-booking-time-grid` |

## Commit

Dùng dạng [Conventional Commits](https://www.conventionalcommits.org/), với scope là tên nhóm tính năng:

```
feat(booking): thêm lưới chọn khung giờ
fix(auth): sửa lỗi OTP
docs: cập nhật proposal
```

Các scope gợi ý: `auth`, `search`, `court`, `booking`, `payment`, `my-bookings`, `review`, `drop-in`, `community`, `notification`, `owner`, `admin`.

## Pull Request

- Mỗi PR cần ít nhất một thành viên khác review trước khi merge vào `develop`.
- PR nên nhỏ và gắn với một nhóm tính năng. Trong mô tả PR, ghi link đến `specs/NNN-.../` và `docs/features/NN-.../`.
- Không commit khóa API hoặc file cấu hình bí mật (`google-services.json`, `local.properties`, ...).
