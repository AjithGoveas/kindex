# macOS Verification & Build Checklist (v1.1.0)

This checklist documents the exact steps required to compile, package, and verify **KIndex** on Apple Silicon (`macosArm64`) or Intel (`macosX64`) Mac machines.

---

## Prerequisites

1. **macOS 12+** (Monterey or later)
2. **Xcode Command Line Tools**:
   ```bash
   xcode-select --install
   ```
3. **JDK 17 or higher** (e.g., via Homebrew `brew install openjdk@21`)
4. **Git**:
   ```bash
   brew install git
   ```

---

## Build Steps

### 1. Build Native C Static Libraries (`libsqlite3.a` and `libtreesitter.a`)

```bash
chmod +x scripts/build-native-libs.sh
./scripts/build-native-libs.sh
```
*Outputs static archives to `third_party/dist/macosArm64/` (or `macosX64/`).*

### 2. Compile & Link Release Executable

```bash
./gradlew -Pnative :kindex-cli:linkReleaseExecutableMacosArm64 --console=plain
```
*(For Intel Macs, replace `MacosArm64` with `MacosX64`)*

### 3. Verify Standalone Binary Execution

```bash
# Test help command
./kindex-cli/build/bin/macosArm64/releaseExecutable/kindex-cli.kexe --version

# Run scan on repository
./kindex-cli/build/bin/macosArm64/releaseExecutable/kindex-cli.kexe scan .

# Run query for symbol
./kindex-cli/build/bin/macosArm64/releaseExecutable/kindex-cli.kexe query "BaseExtractor"
```

### 4. Package Release Artifacts

```bash
mkdir -p dist
cp ./kindex-cli/build/bin/macosArm64/releaseExecutable/kindex-cli.kexe dist/kindex-macos-arm64
shasum -a 256 dist/kindex-macos-arm64 > dist/kindex-macos-arm64.sha256
```

---

## Verification Criteria

- [ ] Standalone binary runs without requiring external `.dylib` dependencies (`otool -L dist/kindex-macos-arm64` shows system libs only).
- [ ] Scanning 50+ repository files completes in under 2 seconds.
- [ ] `query` command uses FTS5 search (no fallback warnings emitted).
- [ ] Zero AST query errors or crashes across all supported languages (Kotlin, Java, Rust, C, C++, C#, JS/TS, Go, CSS).
