# Contract: Security Rules, TTL và test

**Phụ trách:** A. Rules ở `firestore.rules`, test ở `firestore-tests/test/ai.test.ts` (Vitest + `@firebase/rules-unit-testing`, cùng bộ test của 020/021). Lý do: [data-model §2.4](../data-model.md#24-rules), [research R7–R9](../research.md).

## 1. Rules

```text
// Trong match /users/{uid} (đã có, của A):
match /aiUsage/{day} {
  allow read: if isSelf(uid) || isAdmin();
  allow write: if false;                 // chỉ Edge Function (service account bỏ qua Rules)
}

match /aiSearchCache/{hash} {
  allow read, write: if false;
}

match /aiMetrics/{id} {
  allow read: if isAdmin();
  allow write: if false;
}
```

`isSelf(uid)` và `isAdmin()` là hàm đã có trong `firestore.rules` (của 010).

## 2. Test Rules (Emulator)

| # | Kịch bản | Mong đợi |
|---|---|---|
| AI-1 | Người chơi A đọc `users/A/aiUsage/20261005` | Cho phép |
| AI-2 | Người chơi A đọc `users/B/aiUsage/20261005` | Từ chối |
| AI-3 | Khách (không auth) đọc `users/A/aiUsage/…` | Từ chối |
| AI-4 | Người chơi A tạo/sửa `users/A/aiUsage/…` (`count: 0`) | Từ chối (SC-005: không tự đặt lại lượt) |
| AI-5 | Người chơi đọc / ghi `aiSearchCache/x` | Từ chối |
| AI-6 | Người chơi đọc / ghi `aiMetrics/x` | Từ chối |
| AI-7 | Admin đọc `aiMetrics/x` và `users/A/aiUsage/…` | Cho phép |
| AI-8 | Admin ghi `aiMetrics/x` | Từ chối |

## 3. TTL

| Collection group | Trường | Lệnh |
|---|---|---|
| `aiSearchCache` | `expiresAt` | `gcloud firestore fields ttls update expiresAt --collection-group=aiSearchCache --enable-ttl --project=<id>` |
| `aiUsage` | `expiresAt` | tương tự |
| `aiMetrics` | `expiresAt` | tương tự |

Chạy một lần trên project thật (người có quyền Owner). Nếu Spark không cho bật TTL ([R7](../research.md#r7-cache-trong-ngày-fr-018)): dùng `ai-cache-cleanup` (pg_cron 00:30, xóa document `expiresAt < now` của ba collection theo lô 300).

## 4. Test Edge Function

`supabase/functions/ai-search-parse/*.test.ts`, chạy `deno test --allow-env --allow-net --allow-read` với Firebase Emulator (Auth 9099, Firestore 8080) như `on-signup`. LLM luôn là `FakeLlmClient` (không gọi nhà cung cấp thật trong test tự động). Danh sách bảo đảm E1–E18 ở [edge-function §5](edge-function-ai-search-parse.md#5-bảo-đảm-cần-test).

## 5. Test app (JUnit4, MockK, Turbine, coroutines-test)

| Lớp | Nội dung chính |
|---|---|
| `PiiMaskerTest` | Đọc `scripts/testdata/ai/pii-vectors.json`, cùng vector với `pii.test.ts` (SC-007) |
| `AiQueryInputTest` | Rỗng, khoảng trắng, đúng 200, 201 ký tự, emoji (đếm code point) |
| `AiFilterMapperTest` | Sang `SearchCriteria`; sang deep link vãng lai (mốc 080, bỏ trường không hỗ trợ) |
| `ClarifyPlannerTest` | Bảng R11: thiếu thời gian, cuối tuần, có ngày thiếu buổi, có tên sân thì không hỏi, chip "Tối nay" ẩn sau 21:30 |
| `RelaxationPlannerTest` | Thứ tự biến thể, bỏ "sớm hơn" khi đã qua, tối đa 5 biến thể |
| `SuggestRelaxationsTest` | Fake `VenueSearchEngine.countVariants`: chỉ giữ biến thể có ≥ 1, tối đa 3, không gọi `AiSearchApi` |
| `SupabaseAiSearchApiTest` | Parse 3 outcome; 502/504 → `AiUnavailable`; timeout 10 s → `AppError.Timeout`; enum lạ → `UNCLEAR` |
| `AiAssistantViewModelTest` | Gửi hai lần nhanh → một lời gọi (US5-7); lỗi → giữ input, phát sự kiện mở bảng lọc (SC-006); hỏi lại tối đa một lần; chip hỏi lại không gọi API; khách không gửi được; hết lượt / khóa khóa nút gửi; khôi phục `SavedStateHandle` |
| `AiEntryCardViewModelTest` | Khách / còn lượt / hết lượt / đang khóa đếm ngược |

Espresso (nên có): `AiSearchFlowTest` với fake `AiSearchApi` qua Hilt test module: gõ câu → chip hiện trên `SearchFragment`; tắt mạng giả → thông báo và bảng lọc mở, câu còn trong ô.
