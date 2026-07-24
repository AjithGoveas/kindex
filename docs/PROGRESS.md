# Project Progress: KIndex Codebase Knowledge Engine

This document details the completed development milestones and features of KIndex.

## Completed Features & Architecture

### Phase 1: Core Scanning & AST Extraction
- **AST Parsing Framework:** Pluggable extractor architecture utilizing Tree-sitter bindings.
- **Supported Languages:**
  - **Kotlin & Java:** Extracted symbols (classes, interfaces, packages, methods, properties).
  - **Rust:** Added `RustExtractor` for traits, structs, functions, and import declarations.
- **Relational Persistence:** SQLite indexing scheme mapping symbols, source files, and containment relationships.

### Phase 2: Knowledge Graph Resolution
- **Import Resolution (`SymbolResolver`):** Global resolution mapping raw imports to fully qualified name (FQN) symbols.
- **Database Query APIs:** Native APIs for retrieving incoming dependents, outgoing calls, packages, and statistics.
- **Subcommands Built:**
  - `deps`: Trace caller / callee relationships.
  - `stats`: Get structural codebase metrics.
  - `dead`: Check for unreachable symbols.
  - `export`: Export graphs to Mermaid format (`graph.mmd`).

### Phase 3: Optimizations & CLI Polish
- **Incremental Scanning:** Scans check SHA-256 hashes and last-modified timestamps, updating changed/new files and pruning deleted nodes in milliseconds.
- **Keyboard-Driven TUI (`interactive`):**
  - Fully interactive terminal menu shell.
  - Real-time **Arrow-Key Navigation** powered by JLine & JAnsi raw Console API integrations.
  - **Fallback Input Mappings:** Vim keys (`k`/`j`), WASD keys (`w`/`s`), and direct Option Selects (`1`-`6`) for robust cross-terminal support.
  - **Persistent Brand Art:** Clear block-spelled `KINDEX` welcome banner remains on top on every screen refresh.
  - **Contextual Help Cards:** Live tips display option details at the bottom of the screen as you scroll.
