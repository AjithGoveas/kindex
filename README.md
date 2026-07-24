# KIndex

> **Understand any codebase in minutes, not days.**

KIndex is a local, offline-first developer tool that scans a software project, extracts its structural information, and builds a searchable knowledge graph of the repository.

Instead of manually navigating hundreds of files, KIndex indexes your project and provides a structured view of packages, files, classes, functions, imports, and their relationships.

---

## ✨ Features

- 📂 Recursive repository scanning
- 🌳 AST-based source code parsing using Tree-sitter
- 📦 Package and file indexing
- 🏷️ Symbol extraction (Classes, Interfaces, Functions, Methods)
- 🔗 Dependency graph generation
- 🔍 Fast symbol search
- 💾 SQLite-based local storage
- 🌐 Completely offline

---

## 📖 Why KIndex?

Understanding an unfamiliar codebase is time-consuming.

Developers often spend hours figuring out:

- Where does the application start?
- Which classes are important?
- How are files connected?
- Where is a particular function defined?
- Which files depend on another file?

KIndex helps answer these questions by converting source code into a structured knowledge graph that can be searched and explored.

---

## 🚀 Version 1 Scope

Version 1 focuses on building the core indexing engine.

It will support:

- Repository scanning
- Kotlin source parsing
- Java source parsing
- Symbol indexing
- Dependency graph generation
- SQLite storage
- CLI-based search

Future versions may introduce support for additional languages and more advanced analysis features.

---

## 🏗️ Planned Architecture

```
Repository
     │
     ▼
 Repository Scanner
     │
     ▼
 Tree-sitter Parser
     │
     ▼
 Symbol Extractor
     │
     ▼
 Knowledge Graph
     │
     ▼
 SQLite Storage
     │
     ▼
 CLI Query Engine
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build Tool | Gradle (Kotlin DSL) |
| CLI | Clikt |
| Parser | Tree-sitter |
| Database | SQLite |
| Serialization | kotlinx.serialization |

---

## 📂 Planned Project Structure

```
kindex/
├── cli/              # Command-line interface
├── scanner/          # Repository scanning
├── parser/           # Tree-sitter parsing
├── indexer/          # Symbol extraction
├── graph/            # Knowledge graph
├── storage/          # SQLite persistence
├── query/            # Search and queries
└── common/           # Shared models and utilities
```

---

## 🎯 Goals

KIndex aims to make it easy to answer questions like:

- Which packages exist?
- Where is a class defined?
- Which files import this class?
- What functions belong to this class?
- How are source files connected?

without manually exploring the repository.

---

## 📌 Design Philosophy

KIndex follows one simple principle:

> **Parse once. Build knowledge once. Query instantly.**

Rather than relying on AI or heuristic analysis, KIndex builds a deterministic representation of the source code directly from its structure.

---

## 📄 License

This project is currently under development.
License information will be added upon the first public release.