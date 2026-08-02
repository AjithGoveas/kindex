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

### 🔍 Option 2: SQLite FTS5 Tokenized Search Engine [COMPLETED ✅]

Migrate symbol query lookup from simple `LIKE` statements to SQLite FTS5 for fast, tokenized fuzzy searches on large codebases.

* **Key Deliverables:**
  - [x] Implement `symbols_fts` SQLite FTS5 virtual tables inside `kindex-storage`.
  - [x] Index symbol paths and names using token delimiters like `_` and camelCase transitions (e.g. splitting `UserServiceImpl` into `User`, `Service`, `Impl`).
  - [x] Expose tokenized fuzzy search in CLI `query` command (`kindex query Resolver` -> matches `SymbolResolver`).

---

### ⚓ Option 3: Git Hook Automation (`kindex hook`) [COMPLETED ✅]

Automate index maintenance in the background via Git lifecycle hooks (`post-commit`, `post-checkout`, `post-merge`, `post-rewrite`).

* **Key Deliverables:**
  - [x] Implement a `kindex hook install` command writing lightweight non-blocking background hooks.
  - [x] Support `post-commit`, `post-checkout`, `post-merge`, and `post-rewrite` hooks.
  - [x] Add `kindex hook status` command and `--quiet` scan mode for silent background index synchronization.

---

### 📈 Option 4: Benchmarking & Profiling on Monorepos

Establish baseline performance characteristics of KIndex against large-scale projects (e.g. Kotlin compiler, Spring Framework, Kubernetes).

* **Key Deliverables:**
  - [ ] Create a `kindex benchmark` command measuring parse speeds (files/second), re-scan times, database overhead, and memory footprint.
  - [ ] Generate synthetic codebase benchmark generators (1k, 10k, 50k files).

---

### 🎨 Option 5: Multi-Format Graph Exporters (`kindex export`) [COMPLETED ✅]

Export repository architectural graph into multiple industry-standard diagram and data formats.

* **Key Deliverables:**
  - [x] **Mermaid TD (`mermaid`)**: Export node-and-edge relationships in Mermaid syntax.
  - [x] **Graphviz DOT (`dot`)**: Export Graphviz `digraph KIndexGraph { ... }` format for visualization in OmniGraffle, Graphviz, or Gephi.
  - [x] **JSON Graph Format (`json`)**: Export structured node metadata and edge relation links for web visualizers (D3.js, Cytoscape.js).

---

### 🚀 Option 6: Root Executable Launcher & Convenience Task (`./kindex` / `kindex.bat`)

Provide immediate top-level launcher scripts in the repository root directory.

* **Key Deliverables:**
  - [ ] Root `./kindex` POSIX shell wrapper script.
  - [ ] Root `kindex.bat` Windows batch wrapper script.
  - [ ] Gradle `:installLauncher` task assembling and deploying executable wrappers automatically.

---

### 🖥️ Option 7: Interactive TUI Dashboard Enhancements

Enhance the JLine arrow-key interactive terminal UI with live search filtering, dead code inspection, and detail modals.

* **Key Deliverables:**
  - [ ] Live fuzzy symbol filtering input box in TUI.
  - [ ] Modal inspector rendering incoming/outgoing dependency edges for selected symbols.