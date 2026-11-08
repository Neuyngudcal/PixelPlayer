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
- Môi trường cloud **"PixelPlayer"** có setup script cài Android SDK tại `/opt/android-sdk` (ghi vào
  `local.properties`) và mở mạng cho Google Maven, JitPack, JetBrains JDK — hãy chạy Gradle để tự kiểm tra.
  Ở môi trường khác (Default) có thể không có SDK: nếu không chạy được Gradle, nói rõ trong báo cáo cuối.
- Sau khi tạo PR, theo dõi workflow **PR Checks** và sửa đến khi xanh.
- Lệnh `/check-standards` (trong `.claude/commands/`) rà diff hiện tại theo quy chuẩn.
