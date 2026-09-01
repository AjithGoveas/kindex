# Changelog

All notable changes to **KIndex** will be documented in this file.

---

## [1.1.0] - 2026-09-01

### 🚀 Highlights & Features
- **Full Kotlin/Native Tree-Sitter & SQLite C-Interop Linkage ([#3](https://github.com/AjithGoveas/kindex/issues/3))**: Replaced JNI/JVM platform stubs with pure Kotlin/Native bindings. Links directly against statically compiled `libsqlite3.a` (with FTS5) and `libtreesitter.a` (core + 9 language parsers: Kotlin, Java, C, C++, C#, Rust, Go, JavaScript, CSS).
- **Zero External DLL Dependencies**: The Windows executable `kindex-windows-x64.exe` is now 100% self-contained — no JVM, no Gradle, and no external runtime DLLs (`sqlite3.dll`) required.
- **Single-Source Versioning**: Introduced `BuildConst.VERSION` ("1.1.0") driven directly by `gradle.properties`.
- **Dynamic Native Packaging Gradle Task**: Added `:kindex-cli:packageNative` task to package standalone release executables and SHA-256 integrity checksums dynamically per host OS (`kindex-windows-x64.exe`, `kindex-linux-x64`, `kindex-macos-arm64`).
- **Multi-Platform Build Automation**:
  - `scripts/build-native-libs.ps1` & `.sh`: Compiles static C libraries (`libsqlite3.a` and `libtreesitter.a`) for `mingwX64`, `linuxX64`, `macosArm64`, and `macosX64`.
  - `scripts/build-linux.sh`: Linux/WSL2 automated build and packaging script.
  - `docs/MACOS_CHECKLIST.md`: Comprehensive guide for macOS compilation and verification.
- **Comprehensive Unit & Golden Test Suite**: Added 24+ common test cases covering AST extractors (`ExtractorGoldenTest`), cross-symbol resolution (`SymbolResolverTest`), guardrail validation (`RepositoryGuardrailTest`), root resolution (`RepositoryRootResolverTest`), storage persistence (`IndexStorageTest`), and file walking (`WalkFilesTest`).

### 🐛 Bug Fixes & Refinements
- **Native Tree-Sitter AST Query Matching**: Added dual query patterns (`identifier` / `type_identifier` / `simple_identifier`, `import` / `import_header`) across Kotlin, Java, and JavaScript extractors to resolve schema differences between JVM and static C grammars.
- **Query Validation & Line-by-Line Fallback**: Upgraded `BaseExtractor.runQuery()` with graceful pattern-by-pattern validation fallback if a multi-line query string is rejected by a grammar version.
- **Native Heap Memory Leak Fix**: Enclosed native pointer handles (`TSParser`, `TSTree`, `TSQuery`, `TSQueryCursor`) inside `AutoCloseable` scopes with explicit C free wrappers.
- **FTS5 Search Sanitization & Parameterization**: Fixed SQLite FTS5 syntax errors on dotted search queries (e.g. `dev.ajithgoveas.kindex`) by converting non-alphanumeric characters into space-separated wildcard tokens and parameterizing `DELETE FROM symbols_fts` queries.
- **OS-Aware Case Sensitivity Guardrails**: Implemented platform-aware case sensitivity checks in `RepositoryGuardrail` (`isFileSystemCaseSensitive` expect/actual: case-insensitive on Windows/macOS, case-sensitive on Linux).
- **Nearest-Marker Repository Root Resolution**: Refactored `RepositoryRootResolver.findRepositoryRoot()` to locate the nearest repository marker directory (`.kindex`, `.git`, `settings.gradle.kts`, `package.json`, `Cargo.toml`, `go.mod`, `CMakeLists.txt`) walking upward from any target directory.
- **Default Directory Exclusion Lists**: Enforced standard ignore rules in `walkFiles` for `.git`, `build`, `.gradle`, `.kindex`, `dist`, `third_party`, `tsbuild`, `node_modules`, `.idea`, `.vscode`, `target`, `bin`, and `obj`.
- **Package & Import Semicolon Trimming**: Fixed trailing semicolon handling in `KotlinJavaExtractor` for Java package and import statements.
- **Build Hygiene & Clean VCS**: Removed obsolete hardcoded DLL user paths from root `build.gradle.kts`, removed overly broad `*.json` from `.gitignore`, and cleaned up `.idea/vcs.xml` IDE mappings.
