# Tài liệu dự án SmashNow

SmashNow là ứng dụng Android để đặt sân và kết nối cộng đồng cầu lông. Thư mục này chứa toàn bộ tài liệu không phải mã nguồn của dự án.

## Cấu trúc

```
docs/
├── README.md                 ← Mục lục (file này)
├── team-contract.md          ← Thỏa thuận nhóm, lịch họp, kế hoạch 10 tuần
├── proposal/                 ← Proposal đồ án (bản gốc)
├── features/                 ← 12 nhóm tính năng: phạm vi, ảnh tham khảo, checklist DoD
│   ├── README.md             ← Bảng tổng hợp trạng thái và người phụ trách
│   └── NN-<ten-nhom>/
│       ├── README.md
│       └── screenshots/      ← NN_<ten-nhom>_buocX_mo-ta.png
├── design/                   ← Thiết kế chung
│   ├── architecture/         ← Kiến trúc app, module, luồng dữ liệu
│   ├── database/             ← Sơ đồ ER, cấu trúc Firestore, Security Rules
│   └── wireframes/           ← Wireframe / link Figma
├── reports/weekly/           ← Báo cáo tiến độ hàng tuần
├── ai-log/                   ← Nhật ký sử dụng AI (bắt buộc theo proposal mục 13)
└── guides/                   ← Hướng dẫn: Git workflow, quy trình Spec Kit, cài đặt
```

Các file sinh ra trong quá trình làm việc nằm ngoài `docs/`:

| Đường dẫn | Nội dung |
|---|---|
| `specs/NNN-<feature>/` | Spec, plan, tasks của từng tính năng, do Spec Kit (`/speckit-*`) sinh ra |
| `.specify/memory/constitution.md` | Các nguyên tắc kỹ thuật chung của dự án |

## Truy cập nhanh

- [Proposal](proposal/Proposal.html)
- [Thỏa thuận nhóm và kế hoạch 10 tuần](team-contract.md)
- [Danh sách nhóm tính năng](features/README.md)
- [Phân công tuần 1–2: đặc tả và CSDL](guides/w1-w2-spec-assignment.md)
- [Đặc tả CSDL chung](design/database/README.md)
- [Quy trình phát triển một tính năng](guides/feature-workflow.md)
- [Quy ước Git](guides/git-workflow.md)
- [Mẫu báo cáo tuần](reports/weekly/_template.md)
- [Mẫu log AI](ai-log/_template.md)
