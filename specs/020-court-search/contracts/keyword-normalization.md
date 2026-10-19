# Contract: Chuẩn hóa tên và sinh `nameKeywords`

**Người viết:** A. **Người dùng:** A (tìm kiếm), D (ghi `venues.nameKeywords` khi tạo/sửa cơ sở ở spec 111 và khi duyệt hồ sơ ở spec 110/120). Lý do: [research R6](../research.md#r6-tìm-theo-tên-dùng-namekeywords-chốt-mục-9-điểm-6-đặc-tả-csdl).

## 1. Hai bản cài đặt

| Ngôn ngữ | File | Hàm |
|---|---|---|
| Kotlin | `app/src/main/java/com/example/booking_app/core/text/VietnameseNormalizer.kt`, `KeywordGenerator.kt`, `KeywordQuery.kt` | `normalize(s)`, `generate(name): List<String>`, `KeywordQuery.parse(input): KeywordQuery?` |
| TypeScript (Deno) | `supabase/functions/_shared/search-keywords.ts` | `normalize(s)`, `generateKeywords(name): string[]` |

Hai bản **phải** cho cùng kết quả. Cả hai đọc chung `supabase/functions/_shared/search-keywords.vectors.json` trong test.

## 2. `normalize(s)`

1. Đổi `đ` → `d`, `Đ` → `d`.
2. Unicode NFD, bỏ mọi ký tự thuộc lớp `Mn` (dấu kết hợp).
3. Chữ thường theo `Locale.ROOT` (TS: `toLowerCase()` sau bước 2).
4. Mọi ký tự không thuộc `[a-z0-9]` → khoảng trắng.
5. Gộp nhiều khoảng trắng thành một, bỏ khoảng trắng hai đầu.

## 3. `generate(name)`

```text
words    = normalize(name).split(" ")
prefixes = for each w in words:
             if len(w) == 1: [w]
             else: [w[0..k) for k in 2..min(len(w), 15)]
result   = distinct(prefixes), sắp xếp tăng dần, lấy tối đa 60 phần tử đầu
```

## 4. `KeywordQuery.parse(input)` (chỉ Kotlin)

- `tokens = normalize(input).split(" ")`, bỏ token rỗng.
- Tổng số ký tự của các token < 2 → trả `null` (không tìm theo tên, FR-001; Edge case "chỉ có khoảng trắng hoặc ký tự đặc biệt").
- `serverToken` = token dài nhất (cùng độ dài thì lấy token đứng trước), cắt còn 15 ký tự. Nếu token dài nhất chỉ 1 ký tự (ví dụ "a b") thì `serverToken = null` và chỉ lọc trên máy.
- Khớp trên máy: mọi token phải là tiền tố của ít nhất một từ trong `normalize(venue.name)`.

## 5. Vector test (trích; file JSON là bản đầy đủ)

| Đầu vào | `normalize` | `generate` (rút gọn) |
|---|---|---|
| `Sân cầu lông Hòa Bình` | `san cau long hoa binh` | `bi, bin, binh, ca, cau, ho, hoa, lo, lon, long, sa, san` |
| `ĐẠI NAM Badminton` | `dai nam badminton` | `ba, bad, …, badminton, da, dai, na, nam` |
| `Sân A1 – Q.10` | `san a1 q 10` | `10, a1, q, sa, san` |
| `Nhà thi đấu Phú Thọ` | `nha thi dau phu tho` | `da, dau, nh, nha, ph, phu, th, thi, tho` |
| `   ` | `` | `[]` |

| Từ khóa gõ | Tên cơ sở | Khớp? | Lý do |
|---|---|---|---|
| `hoa binh` | Sân cầu lông Hòa Bình | ✅ | US1-1 |
| `HÒA` | Sân cầu lông Hòa Bình | ✅ | US1-1 |
| `binh` | Sân cầu lông Hòa Bình | ✅ | US1-1 |
| `oa bi` | Sân cầu lông Hòa Bình | ❌ | "oa" không là phần đầu của từ nào (US1-2) |
| `h` | bất kỳ | (không tìm theo tên) | < 2 ký tự (FR-001) |
| `!!!` | bất kỳ | (không tìm theo tên) | chỉ ký tự đặc biệt |
| `badmintonarena2026` | Badmintonarena2026 Center | ✅ | `serverToken` cắt 15 ký tự, máy kiểm tra đủ token |

## 6. Rules của D cho trường này

`nameKeywords is list && nameKeywords.size() <= 60`. Không kiểm tra nội dung trong Rules (research R6, rủi ro chấp nhận).
