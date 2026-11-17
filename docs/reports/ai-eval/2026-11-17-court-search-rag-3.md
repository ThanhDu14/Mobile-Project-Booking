# Đánh giá Tìm bằng AI — court-search-rag-3

Ngày chạy: 2026-11-17T15:47:52.537Z. Mô hình: {"budgetMs":20000,"gemini":"gemini-3.5-flash-lite","groq":"(mặc định trong llm.ts)","providers":["gemini"]}. Bộ câu: `scripts/ai-eval/cases.json` (42 câu, giờ giả định 2026-10-05T10:00:00+07:00).

| Chỉ số | Kết quả | Mục tiêu | |
|---|---|---|---|
| Đúng ý định (tìm sân, 26 câu) | 69.2% | ≥ 95% | Chưa đạt |
| Đúng toàn bộ điều kiện (tìm sân) | 65.4% | ≥ 85% | Chưa đạt |
| Đúng nhánh (ngoại lệ, 16 câu) | 75.0% | 100% | Chưa đạt |
| Vi phạm văn bản tự do | 0 | 0 | Đạt |
| Vi phạm PII | 0 | 0 | Đạt |
| Độ trễ LLM p50 / p90 | 6705 / 8771 ms | tham khảo SC-004 | |
| Câu dùng nhà cung cấp dự phòng | 0 | tham khảo | |

Số token không được nhà cung cấp trả qua lớp `LlmProvider`, nên chưa báo cáo.

## Câu chưa khớp

| Câu | Nhóm | Ý định | Chênh lệch | Văn bản tự do / PII |
|---|---|---|---|---|
| S02 | search | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ search | — |
| S04 | search | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ search | — |
| S05 | search | unclear | intent unclear ≠ search; date null ≠ "20261012" | — |
| S07 | search | unclear | intent unclear ≠ search; districtId null ≠ "binh-thanh"; date null ≠ "20261011"; dateChoices ["20261010","20261011"] ≠ []; start null ≠ "0500"; end null ≠ "1100" | — |
| S08 | search | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ search | — |
| S12 | search | unclear | intent unclear ≠ search; date null ≠ "20261017" | — |
| S15 | search | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ search | — |
| S23 | search | search | date null ≠ "20261010" | — |
| S24 | search | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ unclear | — |
| E03 | exception | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ off_topic | — |
| E09 | exception | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ faq | — |
| E12 | exception | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ search | — |
| E15 | exception | AI_UNAVAILABLE | intent AI_UNAVAILABLE ≠ search | — |
