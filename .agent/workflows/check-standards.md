---
description: Rà thay đổi hiện tại theo quy chuẩn PixelPlayer (AGENTS.md + docs/standards)
---

1. Chạy lệnh `scripts/check-standards.sh` ở gốc repo và đọc kết quả.
2. Xem `git diff` (kể cả file chưa track) và đối chiếu với:
   - `AGENTS.md` §2 (quy tắc vàng), §3 (chuẩn mới vs. pattern cũ), §5 (Definition of Done).
   - `docs/standards/CODE_STANDARDS.md` cho code data/ViewModel/DI/test.
   - `docs/standards/UI_UX_STANDARDS.md` §14 (checklist UI) cho code Compose.
   - Tương thích dữ liệu: Room migration + schema JSON, key DataStore, backup, payload `:shared`.
3. Sửa mọi ERROR. Với WARN: sửa, hoặc giữ lại kèm `// Legacy: <lý do>` nếu đúng là cần thiết.
4. Báo cáo ngắn: đã sửa gì, còn gì giữ lại và vì sao, đã/không chạy được Gradle.
