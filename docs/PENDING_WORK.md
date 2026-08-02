# Pending Work: KIndex Roadmap

This document outlines upcoming architectural enhancements, optimizations, and feature options for **KIndex**.

---

## 🗺️ Future Options & Enhancements

```
┌──────────────────────────────────────────────────────────┐
│ Option 1: Pure Kotlin/Native Target Compilation          │
│ Link Tree-sitter C API and SQLiter driver on Native      │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│ Option 2: Full-Text Search (FTS5) & Semantic Symbol Search│
│ Add SQLite FTS5 extension for tokenized identifier search│
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│ Option 3: Git Hook Integration & Background Indexing      │
│ `kindex hook install` for post-commit/post-checkout auto │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│ Option 4: Benchmark & Stress Testing Suite               │
│ Profile memory footprint & parse throughput on huge repos│
└────────────────────────────┴─────────────────────────────┘
```

---

### 🦀 Option 1: Pure Kotlin/Native Target Compilation & Linking

Enable zero-dependency standalone native binaries for Windows (`mingwX64`), macOS (`macosArm64`/`macosX64`), and Linux (`linuxX64`) by linking directly to the C library or invoking parser binaries.

* **Key Deliverables:**
  - [ ] Configure Tree-sitter C header search paths (`api.h`) and static library links (`libtree-sitter.a`) in `treesitter.def`.
  - [ ] Replace native placeholder stubs in `TreeSitterNative.kt` with actual C API wrappers.
  - [ ] **Direct Binary Invocation Alternative:** Explore bundling the official `tree-sitter` CLI or specialized parser binary executables (similar to invoking `git` or system shell commands) to perform out-of-process AST generation, bypassing C-linking overhead.
  - [ ] **Direct Root Invocation Script/Shortcut:** Implement Gradle assembly configurations (or post-build symlinks/batch scripts) that place the compiled executable directly in the root directory as `./kindex` (or `kindex.bat`), eliminating the need to invoke the nested build distribution folder.
  - [ ] Integrate Touchlab's SQLiter SQLite driver backend in `DatabaseDriverFactoryNative.kt` for native targets.
  - [ ] Configure GitHub Actions workflows to cross-compile host-native binaries.

---

### 🔍 Option 2: SQLite FTS5 Tokenized Search Engine

Migrate symbol query lookup from simple `LIKE` statements to SQLite FTS5 for fast, tokenized fuzzy searches on large codebases.

* **Key Deliverables:**
  - [ ] Implement FTS5 virtual tables inside `kindex-storage`.
  - [ ] Index symbol paths and names using token delimiters like `_` and camelCase transitions (e.g. splitting `UserServiceImpl` into `User`, `Service`, `Impl`).
  - [ ] Expose fuzzy search filters in the CLI command (`kindex query UserServ`).

---

### ⚓ Option 3: Option 3: Git Hook Automation (`kindex hook install`)

Automate index maintenance in the background via Git lifecycle hooks.

* **Key Deliverables:**
  - [x] Implement a `kindex hook install` command writing lightweight `.git/hooks/post-commit` and `post-checkout` hooks.
  - [x] Run silent background re-scans (`kindex scan . --quiet`) when changes occur, keeping the graph synchronized.

---

### 📈 Option 4: Benchmarking & Profiling on Monorepos

Establish baseline performance characteristics of KIndex against large-scale projects (e.g. Kotlin compiler, Spring Framework, Kubernetes).

* **Key Deliverables:**
  - [ ] Create a `kindex benchmark` task utilizing JMH or kotlinx-benchmark.
  - [ ] Measure parse speeds (files/second), re-scan times, database file overhead, and peak memory footprints during scanning.