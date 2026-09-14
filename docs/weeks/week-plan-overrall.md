# Kế hoạch 5 tuần — WAF Log Analyzer

Operational plan nén từ `docs/solution-design.md` §12 (gốc 6–8 tuần) xuống **5 tuần**.  
Solution design vẫn là **source of truth** về scope, kiến trúc, stack, O1–O10. File tuần chỉ trả lời *làm gì, xong khi nào*.

| Tuần | Theme | Đóng outcome |
|------|--------|----------------|
| [01](week-01.md) | Lab pipeline + ingest + normalize | O1, O2 |
| [02](week-02.md) | Rule engine + scoring + seed rules | O3, O4, O5 (schema/app) |
| [03](week-03.md) | Alert + Incident + JWT + SSE | O5, O6 (API/SSE), O9 (promote, chưa LLM) |
| [04](week-04.md) | SOC UI + Attack Runner + LLM explain | O6, O7, O9 |
| [05](week-05.md) | Đánh giá FP/FN + báo cáo + slide | O8, O10 (+ polish O1–O9) |

```text
Week 1  Compose + Kafka + Spring ingest
Week 2  Detect + score
Week 3  Alert → Incident + realtime API
Week 4  Dashboard + probe + Ollama
Week 5  Measure + write + demo tape
```

---

## Chuẩn chung mọi file tuần

Mỗi tuần **bắt buộc** đủ 11 mục, đúng thứ tự trong [`_template.md`](_template.md):

| # | Mục | Mục đích |
|---|-----|----------|
| 1 | **Problem statement** | Gap tuần này đóng; không phải danh sách task |
| 2 | **Goal** | Một câu, đo được |
| 3 | **Outcome** | Kết quả tuần, map về O1–O10 |
| 4 | **Scope** | In / Out — Out là hàng rào chống phình |
| 5 | **Work breakdown** | Việc cụ thể |
| 6 | **Acceptance Criteria (AC)** | Điều kiện test được (Given/When/Then) |
| 7 | **Definition of Done (DoD)** | Tuần xong khi nào (checklist) |
| 8 | **Deliverables** | File/path trong monorepo |
| 9 | **Risks** | Rủi ro *của tuần*, không copy nguyên §15 |
| 10 | **Demo checkpoint** | Kịch bản chứng minh DoD |
| 11 | **Handoff** | Nợ + input tuần sau |

**Quy ước:**

- AC fail → tuần chưa Done, kể cả khi “code đã viết”.
- Out-of-scope tuần này **không** được làm sớm trừ khi tuần hiện tại đã Done.
- Không đưa DVWA, ModSec, PDF export, FP-loop, Cloud LLM vào bất kỳ tuần nào (đã khoá §3.2).
- LLM chỉ xuất hiện Week 4; không block detect/alert.
- UI tiếng Việt ưu tiên; báo cáo tiếng Việt.

---

## Quy tắc nén 8 → 5 tuần

| Gốc §12 | Gộp vào |
|---------|---------|
| Tuần 1 design (đã Approved) | Không chiếm slot; schema Flyway → Week 1 |
| Tuần 2 Compose Kafka | Week 1 |
| Tuần 3 ingest + probe skeleton | Week 1 (skeleton) + Week 4 (runner đủ ground truth) |
| Tuần 4 rules + score | Week 2 |
| Tuần 5 alert + incident + SSE | Week 3 |
| Tuần 6 React + runner | Week 4 |
| Tuần 7 Ollama + draft NIST | Week 4 (LLM) + Week 5 (báo cáo) |
| Tuần 8 FP/FN + slide + buffer | Week 5 |

Buffer thật sự chỉ còn **cuối Week 5**. Mọi tuần phải giữ Out-of-scope chặt.
