# Project Brief: KIndex – Code Knowledge Indexer

## 1. Project Overview

### Project Name

**KIndex (Code Knowledge Indexer)**

### Tagline

> *Understand any codebase in minutes, not days.*

### Vision

KIndex is a local, offline-first developer tool that scans a software project, extracts its structural information, and builds a searchable knowledge graph of the repository.

Rather than relying on manual code exploration, KIndex analyzes source code to identify files, packages, classes, functions, imports, and their relationships. The resulting knowledge graph enables developers to quickly understand unfamiliar codebases through fast searches and dependency exploration.

The primary focus of Version 1 is to build a solid indexing engine that converts source code into structured, queryable knowledge.

---

# 2. Problem Statement

Modern software projects often contain hundreds or thousands of source files spread across multiple modules and packages. Developers joining an existing project spend significant time understanding:

* Project structure
* Package organization
* Important classes
* Source file relationships
* Dependencies between components

Although IDEs provide symbol navigation, they do not present a unified view of the project's structure or relationships.

KIndex aims to bridge this gap by providing a lightweight tool that indexes a repository and exposes its structural knowledge in a searchable format.

---

# 3. Objectives

## Primary Objective

Develop a local developer tool that scans a repository and transforms its source code into a searchable knowledge graph.

---

## Secondary Objectives

* Reduce project onboarding time.
* Build a reusable symbol index.
* Enable fast dependency exploration.
* Provide quick access to project structure.
* Operate entirely offline.

---

# 4. Target Users

### Primary Users

* Software Developers
* Students
* Open Source Contributors
* New Team Members

### Secondary Users

* Technical Leads
* Software Architects

---

# 5. Scope of Version 1

After executing

```bash
kindex scan <project-directory>
```

the tool should be able to provide:

* Repository statistics
* Supported programming languages
* Package structure
* Source files
* Classes
* Interfaces
* Functions
* Methods
* Import relationships
* File dependencies
* Symbol search

Version 1 focuses only on building the indexing engine and query capabilities.

---

# 6. Functional Requirements

## 6.1 Repository Scanner

The scanner shall:

* Traverse the project directory recursively.
* Identify supported source files.
* Ignore build outputs and temporary directories.
* Collect repository metadata.

---

## 6.2 Supported Languages

Version 1 supports:

* Kotlin
* Java

The parser architecture should remain extensible to support additional languages in future versions.

---

## 6.3 Source Code Parser

The parser shall use **Tree-sitter** to construct Abstract Syntax Trees (ASTs).

The parser should extract:

* Packages
* Imports
* Classes
* Interfaces
* Functions
* Methods

---

## 6.4 Symbol Index

Every extracted symbol should contain:

* Symbol name
* Symbol type
* Source file
* Package
* Line number
* Definition
* References (where available)

---

## 6.5 Dependency Graph

Build a graph representing relationships between source code entities.

Example:

```
Repository
    │
    ├── Package
    │      │
    │      ├── File
    │      │      │
    │      │      ├── Class
    │      │      │      │
    │      │      │      └── Method
    │      │      │
    │      │      └── Imports
```

The graph should represent:

* Package relationships
* Import relationships
* File relationships
* Symbol relationships

---

## 6.6 Repository Search

The tool should support searching for symbols and relationships.

Example queries:

```
Find UserService

Show AuthenticationService

Show imports of PaymentService

List functions inside UserController

Find LoginController
```

The initial implementation should be keyword and graph based.

---

## 6.7 Local Storage

Indexed information shall be stored locally using SQLite.

Stored data includes:

* Repository metadata
* Symbols
* Dependencies
* Relationships

---

# 7. Non-Functional Requirements

### Offline Operation

The tool must work entirely offline.

---

### Cross Platform

The application should run on:

* Windows
* Linux
* macOS

---

### Performance

The tool should efficiently index medium-sized repositories while supporting concurrent file processing where appropriate.

---

### Extensibility

The architecture should allow additional language parsers to be integrated without major changes to the indexing engine.

---

# 8. Technology Stack

## Language

**Kotlin**

Reason:

* Modern JVM language
* Excellent interoperability with Java
* Strong coroutine support
* Rich ecosystem
* Suitable for building developer tools

---

## Build System

* Gradle (Kotlin DSL)

---

## CLI

* Clikt

---

## Parser

* Tree-sitter

---

## Database

* SQLite

---

## Serialization

* kotlinx.serialization

---

## Graph Representation

* Custom adjacency-list based graph implementation

---

# 9. Project Phases

## Phase 1 — Repository Indexing

* Repository scanner
* Kotlin support
* Java support
* Tree-sitter integration
* Symbol extraction
* SQLite storage

---

## Phase 2 — Knowledge Graph

* Dependency graph generation
* Repository statistics
* Symbol search
* Relationship queries

---

## Phase 3 — Graph Export

* Export knowledge graph
* Basic graph visualization support
* Graph exploration

---

# 10. Success Criteria

The project will be considered successful if a developer can scan an unfamiliar repository and quickly answer questions such as:

* Which packages exist?
* Which classes are defined?
* Where is a particular symbol located?
* Which files import a given class?
* What are the dependencies between source files?
* What is the overall structure of the repository?

without manually navigating through the source code.

---

## Design Philosophy

KIndex follows a simple principle:

> **Parse once. Build knowledge once. Query instantly.**

The project prioritizes deterministic code analysis over heuristics, ensuring that repository knowledge is generated directly from the source code structure. Version 1 intentionally focuses on building a reliable indexing and querying engine before introducing more advanced analysis features in future iterations.