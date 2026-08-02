# Pending Work: KIndex Roadmap

This document outlines upcoming architectural enhancements, optimizations, and feature options for **KIndex**.

---

## ✅ Completed Work (Moved from Pending)

| Feature | Status |
| :--- | :---: |
| Standalone Windows Native Executable (`kindex.exe`) via `mingwX64` | **Complete ✅** |
| Repository Root Resolver & Local Security Guardrail | **Complete ✅** |
| SQLite FTS5 Tokenized Search Engine | **Complete ✅** |
| Git Hook Automation (`kindex hook`) | **Complete ✅** |
| Architectural Flow Engine & Multi-Format Exporters | **Complete ✅** |
| Interactive TUI Dashboard (promoted to `commonMain`, default native mode) | **Complete ✅** |
| `-Xexpect-actual-classes` compiler flag (zero-warning build) | **Complete ✅** |
| Version flag (`kindex --version` → `1.0.0`) | **Complete ✅** |
| Tree-sitter Native C Interop (`kindex-parser`, full native extraction parity) | **Complete ✅** |
| Audit-grade Module-Aware Architecture Graphs emitted by `kindex scan` & `export` | **Complete ✅** |

---

## 🗺️ Remaining Future Options & Enhancements

```
┌──────────────────────────────────────────────────────────┐
│ Option 1: macOS & Linux Native Executables               │
│ Cross-compile kindex for macosArm64, macosX64, linuxX64  │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│ Option 2: GitHub Actions CI/CD Release Pipeline          │
│ Auto-publish multi-platform binaries on tag push (v*)    │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│ Option 3: Benchmark & Stress Testing Suite               │
│ Profile memory footprint & parse throughput on huge repos│
└────────────────────────────┴─────────────────────────────┘
```

---

### 🔥 Option 1: macOS & Linux Native Executables [HIGH PRIORITY 🔥]

Windows (`kindex.exe`) is fully shipped. Extend native compilation to macOS and Linux for a complete cross-platform standalone binary suite.

* **Key Deliverables:**
  - [ ] Test `linuxX64` native compilation and SQLite linking on Ubuntu.
  - [ ] Test `macosArm64` / `macosX64` native compilation and SQLite linking on macOS.
  - [ ] Bundle `dist/kindex-linux-x64`, `dist/kindex-macos-arm64`, `dist/kindex-macos-x64` binaries.
  - [ ] Add platform-specific SHA-256 checksum files alongside each binary.

---

### 🔥 Option 2: GitHub Actions Automated CI/CD Release Pipeline [HIGH PRIORITY 🔥]

Automate multi-platform cross-compilation and release asset publishing on GitHub Releases triggered by tag pushes.

* **Key Deliverables:**
  - [ ] Create `.github/workflows/release.yml` triggered on version tag pushes (`v*`).
  - [ ] Matrix build job across OS targets (`windows-latest`, `ubuntu-latest`, `macos-latest`).
  - [ ] Automatically package standalone binaries per platform.
  - [ ] Automatically attach release assets to GitHub Release notes with SHA-256 checksums.

---

### 🌲 Option 3: Benchmarking & Profiling on Monorepos

Establish baseline performance characteristics of KIndex against large-scale projects (e.g. Kotlin compiler, Spring Framework, Kubernetes).

* **Key Deliverables:**
  - [ ] Create a `kindex benchmark` command measuring parse speeds (files/second), re-scan times, database overhead, and memory footprint.
  - [ ] Generate synthetic codebase benchmark generators (1k, 10k, 50k files).
  - [ ] Document baseline stats in `docs/BENCHMARKS.md`.