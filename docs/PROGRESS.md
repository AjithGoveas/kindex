# Project Progress: KIndex Codebase Knowledge Engine

This document tracks the milestones, architectural upgrades, and completed phases of **KIndex**.

---

## 🚀 Progress Checklist

| Phase | Milestone | Focus | Status |
| :--- | :--- | :--- | :---: |
| **Phase 1** | Foundation Scaffolding | Multi-module Gradle build DSL | **Complete ✅** |
| **Phase 2** | AST Extraction Engine | Tree-sitter & declarative S-expressions | **Complete ✅** |
| **Phase 3** | Interactive UI Console | Query CLI commands and arrow-key JLine TUI | **Complete ✅** |
| **Phase 4** | 8 Language Support | C/C++, C#, Rust, JS/TS, Go, CSS parsers | **Complete ✅** |
| **Phase 5** | KMP Refactor & Linker | KMP, SQLDelight DB, and call Reference Linker | **Complete ✅** |
| **Phase 6** | Git Hook Automation | Background re-scanning on post-commit and post-checkout | **Complete ✅** |
| **Phase 7** | SQLite FTS5 Search Engine | Tokenized camelCase/snake_case fuzzy symbol search | **Complete ✅** |
| **Phase 8** | Multi-Format Exporters | Graphviz DOT, JSON graph, and Mermaid exporters | **Complete ✅** |
| **Phase 9** | Repository Root Resolver & Security Guardrails | `RepositoryRootResolver` + `RepositoryGuardrail` boundary enforcement | **Complete ✅** |
| **Phase 10** | Standalone Windows Native Executable | Zero-dependency `kindex.exe` via Kotlin/Native `mingwX64` | **Complete ✅** |
| **Phase 11** | Cross-Platform Interactive TUI (Native) | `InteractiveCommand` promoted to `commonMain`, default mode on all targets | **Complete ✅** |
| **Phase 12** | Audit-Grade Module Architecture Graphs | Module-aware Mermaid/DOT/JSON emitted by `scan` and `export`, external deps persisted | **Complete ✅** |

---

## 🛠️ Detailed Milestones

### Phase 1 & 2: Core Scanner & AST Extraction
*   **AST Parsing Layer:** Replaced procedural loops with tree-sitter S-expression query compiles (`TSQuery`), improving AST parsing throughput.
*   **Nesting Overlap Containment:** Built bounding containment logic (`resolveNesting` in `BaseExtractor.kt`) mapping methods/functions to their containing struct or class definitions.

---

### Phase 3: CLI & Interactive Console
*   **Scanner Incremental Checks:** Integrated SHA-256 hash checks and filesystem modified-times, allowing changed files to scan incrementally.
*   **Terminal User Interface (TUI):** Developed a JLine terminal console with arrow-key navigation, option selects, context help headers, and ANSI styling.

---

### Phase 4: Extended Language Parsers
*   **Multi-language Support:** Standardized symbol definitions across C, C++, C#, Rust, JavaScript, TypeScript, Go, CSS, Kotlin, and Java.
*   **Library Synchronizations:** Aligned language query bindings to native version `0.23.x` to prevent symbol loading crashes.

---

### Phase 5: Modern KMP Migration & S-Expression Linker
*   **Idiomatic KMP Scaffolding:** Migrated the codebase modules (`kindex-core`, `kindex-parser`, `kindex-storage`, `kindex-cli`) to Kotlin Multiplatform.
*   **SQLDelight Multiplatform Storage:** Deleted Exposed ORM and migrated to SQLDelight for unified database persistence.
*   **Post-Scan Reference Linker:** Implemented a call reference resolver in `SymbolResolver.kt` tracing local file declarations, package scopes, imports, and wildcards, building resolved `CALLS` dependencies.

---

### Phase 6: Git Hook Automation & Optimizations (`kindex hook`)
*   **Git Lifecycle Hooks:** Added `install`, `uninstall`, and `status` actions supporting `post-commit`, `post-checkout`, `post-merge`, and `post-rewrite` hooks.
*   **Non-Blocking Detached Execution:** Embedded background subshell (`(...) >/dev/null 2>&1 &`) in generated shell scripts so Git operations complete instantly without blocking.
*   **Quiet Background Rescans:** Added `--quiet` / `-q` flag to `ScanCommand` suppressing interactive output during automatic background indexing.

---

### Phase 7: SQLite FTS5 Search Engine & Tokenizer
*   **Full-Text Search Virtual Table:** Created `symbols_fts` SQLite FTS5 virtual table for fast, tokenized symbol indexing.
*   **Identifier Tokenizer:** Built `SymbolTokenizer` splitting identifiers on camelCase and snake_case boundaries (`UserServiceImpl` -> `User`, `Service`, `Impl`).
*   **UTF-8 Byte Slicing Safety:** Updated `BaseExtractor` to slice source code by UTF-8 byte array indices, preventing string index out-of-bounds exceptions on multi-byte characters.

---

### Phase 8: Architectural Flow Engine & Multi-Format Exporters (`kindex flow` / `kindex export`)
*   **Automatic Entry-Point Resolver:** Built `EntryPointResolver` auto-detecting `main()`, `CliktCommand`, and script entry points.
*   **4-Tier Layer Classifier:** Built `ArchitectureFlowAnalyzer` categorizing components into Entry Points, Core Services, Storage, and Standalone Utilities.
*   **Default Hierarchical Flow Exports:** Configured default `kindex export` to generate GitDiagram-style flow maps with clean vertical subgraphs and explicit file extensions (`Main.kt`, `ExportCommand.kt`, `SymbolResolver.kt`).
*   **Multi-Level Granularity:** Supported `-g flow`, `-g file`, `-g package`, `-g symbol` for customized architectural abstractions.
*   **Focal Subgraph Traversal:** Implemented `--focus <target>` for N-hop connected subgraphs.
*   **Centralized `.kindex/` Storage:** Saved export files directly in `.kindex/` by default (`.kindex/graph.mmd`, `.kindex/graph.dot`, `.kindex/graph.json`).

---

### Phase 9: Repository Root Resolver & Local Security Guardrails
*   **Top-Level Repository Root Resolver:** Built `RepositoryRootResolver` ensuring `.kindex/` is ALWAYS located at the top-level repository root directory regardless of execution depth. Detects repository root by walking up parent directories for `.git` or `settings.gradle.kts` markers.
*   **Strict Local Repository Guardrail (`RepositoryGuardrail`):** Prevents scanning, querying, or exporting outside the canonical repository root. Canonicalizes paths via `FileSystem.SYSTEM.canonicalize()` to prevent symlink and relative path traversal attacks.
*   **Security Enforcement Verified:** Running `kindex scan ../..` correctly rejects execution with `❌ Security Error: Target path is outside local repository boundaries`.

---

### Phase 10: Standalone Windows Native Executable (`kindex.exe`)
*   **Zero-Dependency Windows Binary:** Compiled standalone `kindex.exe` via Kotlin/Native `mingwX64` target — no JVM, JRE, or Gradle required on the developer machine.
*   **Gradle Build Task (`:buildWindowsExecutable`):** Single command `./gradlew -Pnative :buildWindowsExecutable` assembles:
    *   `dist/kindex.exe` — Standalone Windows executable
    *   `dist/sqlite3.dll` — Native SQLite driver
    *   `dist/kindex.exe.sha256` — SHA-256 integrity checksum for release verification
*   **SQLDelight Native Driver Fix (`ExistingDbSchema`):** Fixed `SQLiteException: table already exists` crash when opening pre-existing database files. Implemented `ExistingDbSchema` delegation that bypasses `CREATE TABLE` on existing files.
*   **Native PRAGMA Fix:** Used `driver.executeQuery()` instead of `driver.execute()` for `PRAGMA journal_mode = WAL;` to comply with SQLiter's native query API restrictions.
*   **Tag-Based Custom Release Packaging:** Shipped `dist/kindex.exe` + `dist/kindex.exe.sha256` as release assets for manual upload to GitHub Releases under tag `v1.0.0`.

---

### Phase 11: Cross-Platform Native Interactive TUI (Default Mode)
*   **`InteractiveCommand` promoted to `commonMain`:** Rewrote the Interactive TUI from JVM-only (`jvmMain`) to pure Kotlin Multiplatform (`commonMain`) — eliminating JLine/Jansi JVM-specific dependencies. Now compiles and runs natively in `kindex.exe`.
*   **Default Execution Mode:** When `kindex.exe` is launched without any subcommand, it automatically launches the Interactive Explorer TUI.
*   **Version Flag Added:** Added `-v` / `--version` flag to CLI root command (`kindex --version` → `1.0.0`).
*   **`MPFile.writeText()` Added:** Extended `MPFile` multiplatform file abstraction with `writeText(content: String)` using `FileSystem.SYSTEM.write()` for clean file writes on all targets.

---

### Phase 12: Audit-Grade Module Architecture Graphs (`kindex scan` / `kindex export`)
*   **`ModuleGraphAnalyzer` (new, `kindex-core`):** Central module-aware renderer producing Mermaid (`renderMermaid`), Graphviz DOT (`renderDot`), and JSON (`renderJson`) from the raw index. Groups nodes into per-module subgraphs (`kindex-cli`, `kindex-core`, `kindex-parser`, `kindex-storage`), styles nodes by architectural layer, and aggregates cross-module edges with call counts.
*   **Graphs Emitted by Scan:** `ScanCommand` and `InteractiveCommand.doScan` now write `.kindex/graph.{mmd,dot,json}` automatically at the end of every scan — architecture diagrams are a first-class scan output, not just a post-hoc export.
*   **External Dependencies Persisted:** Unresolved imports are no longer dropped. `resolveImportsDetailed` in `SymbolResolver.kt` reports them and the scan pipeline stores them as `IMPORTS` edges, so `External Dependencies` nodes (clikt, mordant, okio, SQLDelight, kotlinx.cinterop, tree-sitter) render correctly in every graph.
*   **Kotlin Extraction Fixes (`KotlinJavaExtractor.kt`):** Stripped the literal `package ` / `import ` keywords (and trailing comments) from symbol ids and package names — previously every id was polluted with the keyword, breaking all import resolution. Added `object_declaration` capture so Kotlin objects (e.g. `object ModuleGraphAnalyzer`) are indexed instead of leaking their imports as externals.
*   **Resolver Matching Cascade:** `SymbolResolver` now matches exact id → `.*` wildcard → top-level-function id (`pkg#name`) → package+name → file-name fallback.
*   **`actual` / `solo` Annotation:** Rendered files correctly flagged as `actual` source-set implementations and `solo` (unused/standalone) files.
*   **JVM Tree-sitter Build Unblocked (`TreeSitterJvm.kt`):** Replaced the mismatched `actual typealias TSQuery` with an `actual class TSQuery` wrapper exposing `isValid()`, restoring `:kindex-cli:compileKotlinJvm`.
*   **Path-Based Layer Classification (`ArchitectureFlowAnalyzer.kt`):** Component layers classified from normalized file paths (`/cli/`, `/storage/`, `/parser/`, `/core/`) instead of reliance on call degree.
*   **Scratch Dir Exclusion (`walkFiles`):** Gitignored scratch directories (e.g. `tsbuild/`) excluded from scanning so vendor/tree-sitter source checkouts never pollute the knowledge graph.
*   **Verification:** Fresh scan of this repository yields 41 files / 217 symbols / 851 relationships, with a clean module graph (4 modules, module links, genuine external deps) rendered identically by `scan` and `export`.

---

> [!IMPORTANT]
> **`-Xexpect-actual-classes` Compiler Flag — Build Warning Suppression:**
> Kotlin/Multiplatform marks `expect`/`actual` class declarations as a Beta feature and emits compiler warnings on every occurrence. This has been suppressed project-wide by adding the `-Xexpect-actual-classes` opt-in flag to the root `build.gradle.kts` via a `subprojects {}` block:
>
> ```kotlin
> subprojects {
>     plugins.withId("org.jetbrains.kotlin.multiplatform") {
>         extensions.configure<KotlinMultiplatformExtension> {
>             compilerOptions {
>                 freeCompilerArgs.add("-Xexpect-actual-classes")
>             }
>         }
>     }
> }
> ```
>
> This applies globally to all 4 KMP submodules (`kindex-core`, `kindex-parser`, `kindex-storage`, `kindex-cli`) with a single declaration. The build now compiles with **zero warnings**.

> [!NOTE]
> **Windows Native Compilation Notes:**
> To resolve SQLite linking issues (`sqlite3_close` undefined symbols) and SQLDelight driver verification errors when compiling on Windows:
> - Native platform targets (`mingwX64`, `linuxX64`, `macos*`) are gated behind the `-Pnative` project property. By default, only the JVM target builds.
> - SQLDelight's verification tasks (`verify<SourceSet><Database>Migration`) are disabled programmatically to bypass Java/Windows path extraction and loading issues with the SQLite JDBC native wrapper.
> - The native linker requires `sqlite3.dll` to be present alongside `kindex.exe` at runtime. Both are bundled automatically by the `:buildWindowsExecutable` Gradle task.
