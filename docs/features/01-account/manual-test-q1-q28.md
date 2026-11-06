# Spec 011: checklist kiểm thử thủ công Q1–Q28 (T103)

Nguồn: [quickstart.md §5](../../../specs/011-account-profile/quickstart.md#5-kịch-bản-thủ-công-trên-máy). Bước làm và kết quả mong đợi xem ở đó; file này dùng để ghi kết quả.

## Điều kiện trước khi chạy

- [ ] Android Emulator (hoặc máy thật trỏ `10.0.2.2`) đang chạy, `adb devices` thấy máy
- [ ] Firebase Emulator: `firebase emulators:start --only auth,firestore --project demo-smashnow`
- [ ] Supabase local: `supabase start` (không dùng project thật, constitution IV)
- [ ] App build với `USE_EMULATOR=true`; tài khoản thử đã tạo theo quickstart §0
- [ ] Q5, Q8, Q9 chỉ chạy khi nhóm đã chốt cách thử Storage với token Emulator (T005)
- [ ] Q23, Q25 cần helper thật của B, C, D; nếu chưa có thì ghi bước nào còn `SKIPPED` trong `accountDeletions/{uid}`

## Kết quả

Ảnh hoặc video của Q1, Q5, Q13, Q15, Q23, Q25 lưu vào [`screenshots/`](screenshots/). Q5 đo 20 lần, đạt khi có ít nhất 19 lần dưới 5 giây (SC-002). Q22 ghi kết quả thật.

| # | Đối chiếu | Ngày chạy | Kết quả (Đạt / Không đạt / Chặn) | Ghi chú, bằng chứng |
|---|---|---|---|---|
| Q1 | US1-1, FR-001, FR-001a | | | |
| Q2 | US1-2, SC-001 | | | |
| Q3 | US1-5, FR-006 | | | |
| Q4 | Edge Case | | | |
| Q5 | US1-3, SC-002 | | | |
| Q6 | US1-4, FR-024 | | | |
| Q7 | Edge Case | | | |
| Q8 | FR-003 | | | |
| Q9 | Edge Case, SC-007 | | | |
| Q10 | Edge Case | | | |
| Q11 | US2, FR-007 | | | |
| Q12 | US2-3 | | | |
| Q13 | US3, SC-004 | | | |
| Q14 | US3-5, FR-009 | | | |
| Q15 | US4, SC-003 | | | |
| Q16 | US4-4, FR-012 | | | |
| Q17 | FR-011 | | | |
| Q18 | FR-013, FR-015 | | | |
| Q19 | US5 | | | |
| Q20 | R9 | | | |
| Q21 | US7-1, R10 | | | |
| Q22 | US7-2, R10 | | | |
| Q23 | US6-1..5, SC-005 | | | |
| Q24 | FR-021, SC-007 | | | |
| Q25 | US6-6, SC-008 | | | |
| Q26 | R11 (`IMPACT_CHANGED`) | | | |
| Q27 | FR-025 | | | |
| Q28 | Edge Case "hai máy" | | | |
| Q13 | Đủ 5 trạng thái. Nút "Đăng ký làm chủ sân" / "Sửa và nộp lại" đang ẩn vì chưa có destination `smashnow://owner/apply` (spec 110, D), đúng R14. **[BLOCKED: D]** `firestore.rules` chưa có rule đọc `ownerApplications` nên listener bị `PERMISSION_DENIED`: `player-pending` hiện "Admin sẽ xem hồ sơ trong vài ngày" thay vì ngày nộp, `player-rejected` hiện "Mở hồ sơ để xem lý do và sửa lại" thay vì lý do; app không crash, không đăng xuất | | | |
| Q14 | Đổi claim `OWNER` rồi ghi `ownerStatus = APPROVED` khi `player-pending` đang mở trang Tài khoản: không đăng xuất, app tự sang giao diện quản lý sân, trang Tài khoản hiện "Đã là chủ sân" và khối chuyển chế độ | | | |
| Q15 | Đo 8 vòng bằng mốc thời gian logcat (từ trước lệnh `input tap` tới khi Fragment trang chủ `RESUMED`, đã gồm ~0,1 giây khởi động lệnh `input`). Sang "Người chơi": 0,10–0,15 giây. Sang "Quản lý sân": 0,66–1,14 giây, 3/8 lần vượt 1 giây vì bước làm mới token bắt buộc của R7 (gọi Auth Emulator). Một lần trong khoảng 25 lần thử, sau khi mở lại app bằng `force-stop`, toggle đã sang "Quản lý sân" nhưng màn hình không chuyển; chưa tái hiện lại được | | | |
| Q16 | Ở chế độ người chơi, đổi claim `owner1` thành `PLAYER` rồi bấm "Quản lý sân": không vào được, ở lại giao diện người chơi, snackbar "Quyền chủ sân đã bị thu hồi." (lý do trống vì document chưa ghi). Ghi `ownerStatus = NONE` + `ownerRevokedReason` thì trang Tài khoản hiện thẻ thu hồi kèm lý do, mất khối chuyển chế độ | | | |
| Q17 | Chuyển "Người chơi", `am force-stop`, mở lại: vào "Trang chủ chủ sân" | | | |

Kết quả Q13–Q17 ngày 2026-10-25 đã ghi ở quickstart.md; khi chạy lại thì ghi vào bảng trên.
