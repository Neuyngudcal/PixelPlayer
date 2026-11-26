# CLAUDE.md

Quy chuẩn của dự án dùng chung cho mọi AI agent nằm ở `AGENTS.md` (được import bên dưới).
Không lặp lại quy tắc ở đây — sửa `AGENTS.md` hoặc `docs/standards/*` khi cần đổi chuẩn.

@AGENTS.md

## Ghi chú riêng cho Claude Code

- Trước khi viết code UI: đọc `docs/standards/UI_UX_STANDARDS.md`. Trước khi viết code data/ViewModel/build:
  đọc `docs/standards/CODE_STANDARDS.md`. Chỉ cần đọc phần liên quan đến task.
- Trước khi tạo file mới, tìm file cùng loại gần nhất (Glob/Grep) và theo cấu trúc của nó.
- Khi xong thay đổi code, chạy `scripts/check-standards.sh` và sửa mọi `ERROR`; `WARN` phải sửa hoặc
  giải thích bằng comment `// Legacy: <lý do>`.
- Môi trường cloud thường không có Android SDK: nếu không chạy được Gradle, nói rõ trong báo cáo cuối.
- **Quy tắc nhánh trong phiên cloud:** KHÔNG BAO GIỜ tự ý tách nhánh mới (`git checkout -b`, `git switch -c`, `git branch ...`, hoặc nhánh `claude/*`) trong phiên cloud. Luôn giữ nguyên nhánh hiện tại (mặc định `main`) cho đến khi người dùng yêu cầu hoặc xác nhận điều chỉnh.
- Lệnh `/check-standards` (trong `.claude/commands/`) rà diff hiện tại theo quy chuẩn.
