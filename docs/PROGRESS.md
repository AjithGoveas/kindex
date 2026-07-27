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

### Phase 4: Extended Multi-Language Support
- **Unified S-Expression Query Engine:** Redesigned all 8 extractors (Kotlin/Java, Rust, C, C++, C#, JS/TS, Go, CSS) to use declarative S-expressions (`TSQuery`) instead of procedural walked loops, maximizing native performance.
- **Hierarchical Nesting Resolution:** Implemented byte-range line-range overlap containment resolution (`resolveNesting` in `BaseExtractor.kt`) to resolve method-to-class nesting hierarchy.
- **Native Grouped Captures:** Developed a custom `MatchedGroup` grouping mechanism mapping to native `TSQueryMatch` structures, preventing nested match conflicts.
- **Syntactic AST Extractors:** Added support for **C, C++, C#, JavaScript/JSX, TypeScript/TSX, Go, and CSS**.
- **Universal Symbols Extraction:**
  - C/C++: namespaces, function definitions, structs/unions, preprocessor include links.
  - C#: namespaces, classes, interfaces, methods, using directives.
  - JS/TS: imports, classes, functions, methods.
  - Go: packages, imports, structs, interfaces, methods, functions.
  - CSS: classes and ID selectors.
- **Dependency Version Synchronizing:** Synchronized tree-sitter language parser releases to `0.23.x` to prevent classpath linkage errors.

