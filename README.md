<div align="center">

# ⚡ KIndex

### **Local-first Codebase Intelligence & AST Knowledge Graph Engine**

*Offline • Zero Dependencies • Sub-Second Queries • Zero Cloud Transmission*

[![v1.1.0](https://img.shields.io/badge/version-1.1.0-brightgreen?style=for-the-badge)](https://github.com/AjithGoveas/kindex/releases)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Windows](https://img.shields.io/badge/Windows-v1.1.0_Ready-0078D4?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/AjithGoveas/kindex/releases)
[![Linux](https://img.shields.io/badge/Linux-In_Development-FCC624?style=for-the-badge&logo=linux&logoColor=black)](#platform-support)
[![macOS](https://img.shields.io/badge/macOS-In_Development-000000?style=for-the-badge&logo=apple&logoColor=white)](#platform-support)
[![SQLite FTS5](https://img.shields.io/badge/SQLite-FTS5-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://sqlite.org/fts5.html)

<br/>

[Quick Start](#-quick-start) • [Key Features](#-key-features) • [Supported Languages](#-supported-languages) • [CLI Commands](#-cli-commands) • [Building](#-build-from-source)

</div>

---

## 🔥 Why KIndex?

Modern codebase analysis shouldn't require sending code to third-party APIs or waiting for heavy IDE indexing. **KIndex** turns your repository into a local, high-speed SQLite knowledge graph powered by **Tree-sitter** AST parsing.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 📁 Local Source Files ➔ 🌳 Tree-sitter CST ➔ ⚡ SQLite FTS5 ➔ 📊 Instant CLI │
└─────────────────────────────────────────────────────────────────────────────┘
```

- 🔒 **100% Offline & Private** — Zero code leaves your host machine. Strictly bounded to your repo directory.
- ⚡ **Zero External Dependencies** — Single self-contained binary (`kindex-windows-x64.exe`). No JVM, no Gradle, no runtime DLLs required.
- 🧠 **Smart Incremental Scanning** — Re-indexes only modified or new files in milliseconds using SHA-256 + mtime tracking.
- 🔍 **Full-Text Tokenized Search** — `camelCase` & `snake_case` symbol search powered by SQLite FTS5.
- 📐 **Automatic Architecture Diagrams** — Every scan outputs fresh Mermaid (`.mmd`), Graphviz (`.dot`), and JSON graph maps.
- 🎣 **Non-Blocking Git Hooks** — Automatically re-indexes on `git commit`, `checkout`, `merge`, and `rebase`.

---

## ⚡ Quick Start

### Windows (v1.1.0 Release)

> [!IMPORTANT]
> `kindex.exe` operates inside a strictly enforced boundary. Place `kindex.exe` inside a `.kindex/` folder at your **repository root**.

1. Create a `.kindex/` folder in your project root:
   ```powershell
   mkdir .kindex
   ```
2. Download [`kindex-windows-x64.exe`](https://github.com/AjithGoveas/kindex/releases) and place it as `.kindex\kindex.exe`.
3. Add `.kindex/` to your `.gitignore`:
   ```gitignore
   # KIndex local index database & runtime cache
   .kindex/
   ```
4. Run your first scan:
   ```powershell
   .\.kindex\kindex.exe scan .
   ```

---

## ✨ Key Features

### 💻 Interactive Knowledge Explorer
Launch `kindex.exe` without arguments for a menu-driven terminal TUI:
```powershell
.\.kindex\kindex.exe
```
```text
  1  Search    — Search symbols across codebase
  2  Deps      — Query incoming/outgoing dependency edges
  3  Stats     — View structural metrics & breakdown
  4  Flow      — 4-tier architectural layer analysis
  5  Export    — Export Mermaid, DOT, or JSON graph maps
  6  Dead      — Identify unreferenced dead code
  7  Quit      — Exit session
```

### 🔍 FTS5 Full-Text Symbol Search
Search classes, interfaces, objects, methods, and functions instantly with sub-token matching:
```powershell
.\.kindex\kindex.exe query "BaseExtractor"
```

### 🏗️ 4-Tier Architectural Flow Analysis
Categorize components and identify entry points across your codebase:
```powershell
.\.kindex\kindex.exe flow
```
- 🚀 **Entry Points & Drivers** — Mains, CLI commands, HTTP entry points
- ⚙️ **Service & Parser Engine** — Core domain logic and AST extractors
- 💾 **Storage & Infrastructure** — Database drivers, file I/O, persistence
- 🛠️ **Solo & Standalone Utilities** — Decoupled helper classes and utilities

### 📊 Module-Aware Graph Exports
Render repository structural diagrams at module, package, file, or symbol granularities:
```powershell
# Default: Module-aware Mermaid architecture map → .kindex/graph.mmd
.\.kindex\kindex.exe export

# Focused N-hop subgraph around a target symbol
.\.kindex\kindex.exe export --focus SymbolResolver -f mermaid

# File wiring in Graphviz DOT format
.\.kindex\kindex.exe export -g file -f dot
```

---

## 🌐 Platform Support

| Platform | Executable Artifact | Status |
| :--- | :--- | :--- |
| **Windows (x86_64)** | `kindex-windows-x64.exe` | **Available Now (v1.1.0)** |
| **Linux (x86_64)** | `kindex-linux-x64` | **In Development (Coming Soon)** *(Build script included)* |
| **macOS (Apple Silicon / Intel)** | `kindex-macos-arm64` / `kindex-macos-x64` | **In Development (Coming Soon)** *(Checklist included)* |

---

## 🌐 Supported Languages

| Language | Declarations & Relationships Extracted |
| :--- | :--- |
| **Kotlin & Java** | Packages, classes, interfaces, objects, methods, constructors, call sites, supertype links |
| **Rust** | Modules, structs, traits, impl blocks, function calls |
| **C & C++** | Namespaces, structs, function definitions, `#include` links |
| **C#** | Namespaces, classes, interfaces, methods, `using` imports |
| **JavaScript & TypeScript** | Modules, classes, interfaces, functions, imports |
| **Go** | Packages, structs, interfaces, functions, methods |
| **CSS** | Class selectors, ID selectors |

---

## 🛠️ CLI Commands Reference

| Command | Usage | Description |
| :--- | :--- | :--- |
| `scan` | `kindex scan .` | Incremental AST parse & index creation into `.kindex/index.db` |
| `query` | `kindex query <term>` | Full-text tokenized symbol search |
| `deps` | `kindex deps <symbol>` | List incoming and outgoing symbol reference edges |
| `flow` | `kindex flow` | Perform 4-tier architectural flow classification |
| `export` | `kindex export [-g level] [-f format]` | Export structural diagrams (`mermaid`, `dot`, `json`) |
| `dead` | `kindex dead` | Find unreferenced classes and interfaces |
| `hook` | `kindex hook [install\|status\|uninstall]` | Manage non-blocking Git hook auto-indexing |
| `stats` | `kindex stats` | View code statistics and total symbol counts |
| `--version` | `kindex --version` | Output CLI version (`1.1.0`) |

---

## 🔒 Security & Scope Bounding

KIndex operates under a **strict local repository guardrail**. Any path passed to any command is validated against the nearest repository root (identified by `.kindex`, `.git`, `settings.gradle.kts`, `package.json`, or `Cargo.toml`). Targets outside this boundary are immediately rejected:

```
❌ Security Error: Target path 'C:\Users\...\Videos' is outside local repository
   boundaries ('C:\Users\...\Videos\Projects\MyRepo').
   KIndex is strictly locked to its local repository scope.
```

---

## 🛠️ Build from Source

### Prerequisites
- JDK 17+
- Gradle
- C compiler (`gcc` / `clang` / MinGW-w64)

### 1. Build Static C Libraries
Compile SQLite (with FTS5) and Tree-sitter parsers into static archives under `third_party/dist/`:

**Windows (PowerShell):**
```powershell
.\scripts\build-native-libs.ps1
```

**Linux / macOS (Bash):**
```bash
./scripts/build-native-libs.sh
```

### 2. Package Native Executable

**Windows:**
```powershell
.\gradlew.bat -Pnative :kindex-cli:packageNative
```
Output: `dist/kindex-windows-x64.exe` (+ `dist/kindex-windows-x64.exe.sha256`)

**Linux:**
```bash
./scripts/build-linux.sh
```

> [!NOTE]
> For fast JVM development:
> ```bash
> ./gradlew build :kindex-cli:installDist
> ./gradlew :kindex-cli:jvmTest
> ```

---

## 📐 Architecture Overview

```
kindex/
├── kindex-core/     Domain models, MPFile I/O, RepositoryRootResolver, RepositoryGuardrail, FlowAnalyzer
├── kindex-parser/   Tree-sitter AST extraction, C-interop shims, S-expression query engine (10 languages)
├── kindex-storage/  SQLDelight SQLite persistence with FTS5 search (JVM + Native drivers)
└── kindex-cli/      Clikt CLI commands, Mordant ANSI TUI, native packaging tasks
```

---

## 📄 License

**Proprietary. All rights reserved.**

KIndex and all associated source code, binaries, and documentation are proprietary and confidential. Unauthorized copying, distribution, modification, or use — in whole or in part — is strictly prohibited without explicit written permission from the author.