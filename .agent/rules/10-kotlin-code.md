---
trigger: glob
globs: **/*.kt
---

# Code Kotlin — PixelPlayer

Khi tạo/sửa file `.kt`, tuân theo `docs/standards/CODE_STANDARDS.md`.

Nhắc nhanh:

- Đặt file đúng package (xem `AGENTS.md` §4). Tên: `XxxScreen`, `XxxViewModel`, `XxxStateHolder`,
  `XxxRepository`/`XxxRepositoryImpl`, `XxxDao`, `XxxEntity`, `XxxUiState`.
- Repository trả `Flow`/`suspend`; I/O trong `withContext(Dispatchers.IO)`; app scope = `@AppScope`.
- ViewModel: `@HiltViewModel`, `_state.update { }`, `stateIn(viewModelScope, WhileSubscribed(5_000), init)`.
- Sự kiện một lần: `MutableSharedFlow`.
- Test mới: JUnit 5 + MockK + Turbine + `MainCoroutineExtension`.
