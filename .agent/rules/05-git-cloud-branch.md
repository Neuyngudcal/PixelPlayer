---
trigger: always_on
---

# Quy tắc Git & Tách nhánh trong phiên Cloud

- **Không tự ý tách nhánh trong phiên cloud:** Khi chạy trong một phiên cloud (cloud session / remote agent / web session), **KHÔNG BAO GIỜ** tự động tạo hoặc tách nhánh mới (`git checkout -b`, `git switch -c`, `git branch <tên-nhánh>`, hoặc tạo nhánh `claude/*`).
- **Giữ nguyên nhánh hiện tại:** Luôn làm việc và commit (nếu được yêu cầu) trực tiếp trên nhánh đang checkout hiện tại (mặc định là `main`), không tự ý chuyển nhánh.
- **Điều kiện ngoại lệ duy nhất:** Chỉ được phép tách nhánh hoặc đổi nhánh khi **người dùng yêu cầu hoặc xác nhận thay đổi trực tiếp** trong phiên làm việc đó.
