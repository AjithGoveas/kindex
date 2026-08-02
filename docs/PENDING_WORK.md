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

### 🔥 Option 1: Standalone Native Executable Compilation (`kindex.exe` / `kindex`) [HIGH PRIORITY 🔥]

Enable zero-dependency standalone native binaries for Windows (`mingwX64` -> `kindex.exe`), macOS (`macosArm64`/`macosX64` -> `kindex`), and Linux (`linuxX64` -> `kindex`). Developers download a single binary, place it in their repo (or `.kindex/`), and run it directly with zero runtime/JVM prerequisites.

* **Key Deliverables:**
  - [ ] Configure Tree-sitter C header search paths (`api.h`) and static library links (`libtree-sitter.a`) in `treesitter.def` for Kotlin/Native.
  - [ ] Replace native placeholder stubs in `TreeSitterNative.kt` with actual C API wrappers.
  - [ ] Integrate Touchlab's SQLiter SQLite driver backend in `DatabaseDriverFactoryNative.kt` for native targets.
  - [ ] Support native compilation tasks (`./gradlew :kindex-cli:nativeExecutable`) outputting `kindex.exe` / `kindex`.

---

### 🔥 Option 8: GitHub Actions Automated CI/CD Release Pipeline (`.github/workflows/release.yml`) [HIGH PRIORITY 🔥]

Automate multi-platform cross-compilation and release asset publishing on GitHub Releases.

* **Key Deliverables:**
  - [ ] Create `.github/workflows/release.yml` triggered on version tag pushes (`v*`).
  - [ ] Matrix build job across OS targets (`windows-latest`, `ubuntu-latest`, `macos-latest`).
  - [ ] Automatically package standalone binaries (`kindex.exe`, `kindex-linux-x64`, `kindex-macos-arm64`).
  - [ ] Automatically attach release assets to GitHub Release notes with SHA-256 checksums.

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

### 🎨 Option 5: Architectural Flow & Multi-Format Exporters (`kindex flow` / `kindex export`) [COMPLETED ✅]

Export repository architectural graph into multiple industry-standard diagram and data formats with entry-point flow and multi-level granularity.

* **Key Deliverables:**
  - [x] **Entry-Point Flow Map (`flow`)**: Auto-detect `main()` and CLI entry points, classifying components into 4 architectural layers.
  - [x] **Mermaid TD (`mermaid`)**: Export node-and-edge relationships in Mermaid syntax with vertical subgraphs and explicit file extensions (`.kt`).
  - [x] **Graphviz DOT (`dot`)**: Export Graphviz `digraph KIndexGraph { ... }` format.
  - [x] **JSON Graph Format (`json`)**: Export structured node metadata and edge relation links for web visualizers.
  - [x] **Centralized `.kindex/` Storage**: All export diagrams saved directly in top-level `.kindex/` folder.
  - [x] **Top-Level Repository Root Resolver**: `RepositoryRootResolver` ensures `.kindex/` is located at repository root regardless of execution directory.

---

### 🖥️ Option 7: Interactive TUI Dashboard Enhancements [COMPLETED ✅]

Enhanced the JLine arrow-key interactive terminal UI with architectural graph export sub-menus, live symbol search, dead code detection, and structural statistics.

* **Key Deliverables:**
  - [x] Integrated Architectural Graph Exporter sub-menu directly inside `kindex interactive` console.
  - [x] Added dead code candidates detection with double-bordered tables.
  - [x] Added structural repository stats breakdown.