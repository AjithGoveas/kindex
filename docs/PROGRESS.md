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
*   **Top-Level Repository Root Resolver:** Built `RepositoryRootResolver` ensuring `.kindex/` is ALWAYS located at the top-level repository root directory regardless of execution depth.
*   **Interactive TUI Exporter Sub-Menu:** Integrated graph export sub-menu into `kindex interactive` console. (`source`, `target`, `relation`).

---

> [!IMPORTANT]
> **Windows Compilation & Build Note:**
> To resolve SQLite linking issues (`sqlite3_close` undefined symbols) and SQLDelight driver verification errors when compiling on Windows:
> - Native platform targets (`mingwX64`, `linuxX64`, `macos*`) are gated behind the `-Pnative` project property. By default, only the JVM target builds.
> - SQLDelight's verification tasks (`verify<SourceSet><Database>Migration`) are disabled programmatically to bypass Java/Windows path extraction and loading issues with the SQLite JDBC native wrapper.
