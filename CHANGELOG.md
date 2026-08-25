# Changelog

All notable changes to **KIndex** will be documented in this file.

---

## [1.1.0] - 2026-08-25

### Fixed
- **Native Tree-Sitter AST Query Rejections**: Resolved node-type schema mismatches between JVM (Bonede) and static C Native tree-sitter grammars (e.g. `import` vs `import_header`, `identifier` vs `simple_identifier`).
- **Query Validation & Fallback**: Upgraded `BaseExtractor.runQuery()` with graceful line-by-line pattern validation fallback on both JVM and Native targets.
- **FTS5 Syntax Errors & Parameterization**: Fixed SQLite FTS5 crashes on dotted search terms (e.g., `dev.ajithgoveas.kindex`) by sanitizing non-alphanumeric characters into space-separated prefix wildcards and parameterizing SQL `DELETE` queries.
- **OS Case Sensitivity Guardrails**: Implemented platform-aware case sensitivity checks in `RepositoryGuardrail` (`isFileSystemCaseSensitive` expect/actual: case-insensitive on Windows/macOS, case-sensitive on Linux).
- **Nearest-Marker Repository Root Resolution**: Updated `RepositoryRootResolver.findRepositoryRoot()` to locate the nearest repository marker (`.kindex`, `.git`, `settings.gradle.kts`, `package.json`, `Cargo.toml`, `go.mod`, `CMakeLists.txt`) encountered walking upward from the target directory.
- **Default Directory Ignore Lists**: Enforced standard ignore rules in `walkFiles` for `.git`, `build`, `.gradle`, `.kindex`, `dist`, `third_party`, `tsbuild`, `node_modules`, `.idea`, `.vscode`, `target`, `bin`, and `obj`.
- **Package & Import Semicolon Trimming**: Fixed trailing semicolon handling in `KotlinJavaExtractor` for Java package and import statements.

### Added
- **Single-Source Versioning**: Introduced `BuildConst.VERSION` ("1.1.0") driven by `gradle.properties`.
- **Native Packaging Gradle Task**: Added `:kindex-cli:packageNative` task that outputs `dist/kindex-windows-x64.exe` alongside its `.sha256` hash.
- **Multi-Platform Build Tooling**:
  - `scripts/build-native-libs.sh`: Cross-compiles static C libraries (`libsqlite3.a` with FTS5 and `libtreesitter.a` with all 9 language parsers) for `linuxX64`, `macosArm64`, and `macosX64`.
  - `scripts/build-linux.sh`: WSL2/Linux build and packaging script.
  - `docs/MACOS_CHECKLIST.md`: macOS compilation and verification guide.
- **Comprehensive Unit & Golden Test Suite**: Added 24+ common test cases covering extractors (`ExtractorGoldenTest`), symbol resolution (`SymbolResolverTest`), guardrails (`RepositoryGuardrailTest`), root resolution (`RepositoryRootResolverTest`), storage (`IndexStorageTest`), and file walking (`WalkFilesTest`).
