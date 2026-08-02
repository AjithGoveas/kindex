# KIndex

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-brightgreen?style=flat-square" alt="v1.0.0" />
  <img src="https://img.shields.io/badge/kotlin-multiplatform-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin Multiplatform" />
  <img src="https://img.shields.io/badge/platform-windows-0078D4?style=flat-square&logo=windows&logoColor=white" alt="Windows" />
  <img src="https://img.shields.io/badge/database-sqlite-003B57?style=flat-square&logo=sqlite&logoColor=white" alt="SQLite" />
  <img src="https://img.shields.io/badge/license-proprietary-red?style=flat-square" alt="Proprietary" />
</p>

<p align="center">
  <b>Local-first codebase intelligence. No cloud. No runtime. Just answers.</b>
</p>

<p align="center">
  KIndex scans your repository, extracts syntactic symbols via Tree-sitter concrete syntax trees, and builds a queryable knowledge graph inside a local SQLite database — entirely offline, entirely on your machine.
</p>

---

## What is KIndex?

KIndex is a **local developer intelligence tool** designed to answer structural questions about a codebase instantly — without sending a single line of code to the cloud.

It works by:
1. Walking your source tree and parsing each file into an Abstract Syntax Tree (AST) via [Tree-sitter](https://tree-sitter.github.io/tree-sitter/)
2. Extracting declared symbols (classes, functions, interfaces, modules) and their call/import relationships
3. Storing everything in a compact SQLite database (`.kindex/index.db`) at your repository root
4. Letting you query, explore, and export the resulting knowledge graph from a fast terminal interface
5. Emitting **audit-grade module architecture graphs** (Mermaid/DOT/JSON) automatically with every scan

The **v1.0.0 release ships as a standalone Windows executable** (`kindex.exe`) — no JVM, no Gradle, no setup required.

---

## Supported Languages

| Language | Symbols Extracted |
| :--- | :--- |
| **Kotlin & Java** | Packages, classes, interfaces, methods, constructors, call sites |
| **Rust** | Modules, structs, traits, impl blocks, function calls |
| **C & C++** | Namespaces, structs, function definitions, `#include` links |
| **C#** | Namespaces, classes, interfaces, methods, `using` imports |
| **JavaScript & TypeScript** | Modules, classes, interfaces, functions, imports |
| **Go** | Packages, structs, interfaces, functions, methods |
| **CSS** | Class selectors, ID selectors |

---

## Installation

### Windows (Recommended)

KIndex v1.0.0 ships as a **zero-dependency native Windows executable**. No JVM or runtime installation needed.

> [!IMPORTANT]
> `kindex.exe` **must** be placed inside the `.kindex/` folder at the **root of the repository** you want to analyze. KIndex is strictly scoped to its host repository — it cannot read or write files outside its root boundary.

**Steps:**

1. Create a `.kindex/` folder at the root of your project if one doesn't exist yet.
2. Download `kindex.exe` and `sqlite3.dll` from the [Releases](https://github.com/AjithGoveas/kindex/releases) page.
3. Place **both files** inside `.kindex/`.
4. Run directly from your terminal:

```powershell
.\.kindex\kindex.exe
```

> [!TIP]
> Add the following entries to your `.gitignore` to prevent KIndex runtime files from being committed to version control:
> ```gitignore
> # KIndex — local repository intelligence tool
> .kindex/
> ```
> It is **strongly recommended** to ignore the entire `.kindex/` folder in VCS. The index database, exported diagrams, and executable are all local runtime artifacts and should not be tracked.

---

## Usage

### Interactive Explorer (Default Mode)

Launching `kindex.exe` without any arguments opens the **Interactive Knowledge Explorer** — a menu-driven terminal interface for searching, navigating, and analyzing your codebase.

```powershell
.\.kindex\kindex.exe
```

The explorer presents the following actions:

```
  1  Search    — Search symbols in codebase
  2  Deps      — Query dependency references
  3  Stats     — View structural stats
  4  Flow      — Architectural layer analysis
  5  Export    — Export Mermaid graph diagram
  6  Dead      — Identify dead / unreferenced code
  7  Quit      — Exit session
```

---

### Scan & Index

Index the repository's source files into `.kindex/index.db`. Run this once after setup, or whenever you make significant changes.

```powershell
.\.kindex\kindex.exe scan .
```

KIndex performs **incremental scanning** — only new or modified files are re-processed on subsequent runs.

> [!NOTE]
> Every scan also writes **audit-grade module architecture graphs** to `.kindex/graph.mmd`, `.kindex/graph.dot`, and `.kindex/graph.json` automatically — an up-to-date architectural diagram is always one scan away, no separate export step required.

---

### Query Symbols

Search for classes, functions, or any declared symbol using full-text, `camelCase`-aware tokenized search:

```powershell
# Find all symbols matching "Resolver" (also matches SymbolResolver, ImportResolver, etc.)
.\.kindex\kindex.exe query "Resolver"
```

---

### Dependency Analysis

Inspect incoming and outgoing call/import references for any symbol:

```powershell
.\.kindex\kindex.exe deps SymbolResolver
```

---

### Architectural Flow Analysis

Classify your codebase into a 4-tier architectural model and display entry points:

```powershell
.\.kindex\kindex.exe flow
```

Output shows entry points (mains, CLI commands) and component distribution across:
- 🚀 **Entry Points & Drivers**
- ⚙️ **Service & Parser Engine**
- 💾 **Storage & Infrastructure**
- 🛠️ **Solo & Standalone Utilities**

---

### Export Diagrams

Export **module-aware architecture graphs** directly to `.kindex/`. Nodes are grouped into per-module subgraphs (e.g. `kindex-cli`, `kindex-core`, `kindex-parser`, `kindex-storage`), styled by architectural layer, annotated as `actual`/`solo` where applicable, and linked to **external dependency nodes** built from unresolved imports. Cross-module edges are aggregated with call counts.

```powershell
# Default: module-aware Mermaid flow map → .kindex/graph.mmd
.\.kindex\kindex.exe export

# File-level wiring in Graphviz DOT
.\.kindex\kindex.exe export -g file -f dot

# Package-level wiring in JSON
.\.kindex\kindex.exe export -g package -f json

# Symbol-level wiring
.\.kindex\kindex.exe export -g symbol -f mermaid

# N-hop subgraph focused on a specific target
.\.kindex\kindex.exe export --focus SymbolResolver -f mermaid
```

> [!NOTE]
> `kindex scan` writes these same three graph files (`.kindex/graph.{mmd,dot,json}`) automatically at the end of every scan — `export` re-renders the index at a chosen granularity/format, including package- and symbol-level views.

---

### Dead Code Detection

List classes and interfaces with zero incoming references:

```powershell
.\.kindex\kindex.exe dead
```

---

### Git Hook Automation

Automatically re-index after every `git commit`, `git checkout`, `git merge`, and `git rebase`:

```powershell
# Install non-blocking background hooks
.\.kindex\kindex.exe hook install

# Check hook status
.\.kindex\kindex.exe hook status

# Remove hooks
.\.kindex\kindex.exe hook uninstall
```

---

### Structural Statistics

Print a summary of files, symbols, packages, classes, and dependency edges:

```powershell
.\.kindex\kindex.exe stats
```

---

### Version

```powershell
.\.kindex\kindex.exe --version
# 1.0.0
```

---

## Security Model

KIndex enforces a **strict local repository boundary**. Every path passed to any command is validated against the canonical repository root (identified by `.git` or `settings.gradle.kts`). Any operation targeting a path outside this root is immediately rejected:

```
❌ Security Error: Target path 'C:\Users\...\Videos' is outside local repository
   boundaries ('C:\Users\...\Videos\Projects\MyRepo').
   KIndex is strictly locked to its local repository scope.
```

This guarantees the tool cannot be used — accidentally or deliberately — to access, read, or index files outside the repository it was placed in.

---

## Build from Source

Requires the [Kotlin/Native toolchain](https://kotlinlang.org/docs/native-overview.html) and Gradle.

```powershell
git clone https://github.com/AjithGoveas/kindex.git
cd KIndex

# Build standalone Windows executable
.\gradlew.bat -Pnative :buildWindowsExecutable
```

Output:

```
dist/
├── kindex.exe         ← Standalone Windows binary
├── sqlite3.dll        ← Required SQLite native driver
└── kindex.exe.sha256  ← SHA-256 integrity checksum
```

> [!NOTE]
> For JVM development (no native toolchain required):
> ```bash
> ./gradlew build :kindex-cli:installDist
> ```

---

## Architecture

```
kindex/
├── kindex-core/         Core domain models, MPFile I/O, RepositoryRootResolver, RepositoryGuardrail, ArchitectureFlowAnalyzer + ModuleGraphAnalyzer (module-aware Mermaid/DOT/JSON renderers)
├── kindex-parser/       Tree-sitter AST extraction + S-expression query engine (10 languages)
├── kindex-storage/      SQLDelight SQLite persistence (JVM + Native drivers)
└── kindex-cli/          Clikt CLI commands + Interactive TUI + kindex.exe entry point
```

All four modules target **both JVM and Kotlin/Native** (`mingwX64`, `linuxX64`, `macosArm64`, `macosX64`). The `-Xexpect-actual-classes` compiler flag is applied globally to suppress Kotlin/Multiplatform Beta warnings on `expect`/`actual` class declarations.

---

## License

**Proprietary. All rights reserved.**

KIndex and all associated source code, binaries, and documentation are proprietary and confidential. Unauthorized copying, distribution, modification, or use — in whole or in part — is strictly prohibited without explicit written permission from the author.