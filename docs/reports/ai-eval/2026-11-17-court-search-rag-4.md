# Đánh giá Tìm bằng AI — court-search-rag-4

Ngày chạy: 2026-11-17T16:35:24.696Z. Mô hình: {"budgetMs":20000,"gemini":"gemini-3.5-flash-lite","groq":"openai/gpt-oss-120b","providers":["gemini","gemini-2","groq"]}. Bộ câu: `scripts/ai-eval/cases.json` (42 câu, giờ giả định 2026-10-05T10:00:00+07:00).

| Chỉ số | Kết quả | Mục tiêu | |
|---|---|---|---|
| Đúng ý định (tìm sân, 26 câu) | 96.2% | ≥ 95% | Đạt |
| Đúng toàn bộ điều kiện (tìm sân) | 96.2% | ≥ 85% | Đạt |
| Đúng nhánh (ngoại lệ, 16 câu) | 100.0% | 100% | Đạt |
| Vi phạm văn bản tự do | 0 | 0 | Đạt |
| Vi phạm PII | 0 | 0 | Đạt |
| Độ trễ LLM p50 / p90 | 5868 / 8427 ms | tham khảo SC-004 | |
| Câu dùng nhà cung cấp dự phòng | 8 | tham khảo | |

Số token không được nhà cung cấp trả qua lớp `LlmProvider`, nên chưa báo cáo.

## Câu chưa khớp

| Câu | Nhóm | Ý định | Chênh lệch | Văn bản tự do / PII |
|---|---|---|---|---|
| S07 | search | unclear | intent unclear ≠ search; start null ≠ "0500"; end null ≠ "1100" | — |
