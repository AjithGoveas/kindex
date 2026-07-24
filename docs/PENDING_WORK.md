## 🛠 Phase 5 Engineering Options

```
┌──────────────────────────────────────────────────────────┐
│ Option 1: Native Packaging & Release Automation          │
│ Build GraalVM Native Image build pipeline & distribution │
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

### Option 1: Native Binary Packaging & Release Pipeline

Since you implemented the GraalVM / Native Image preparation and Mordant spinners, the next logical step is automating native builds so users don't need a Java Runtime to run `kindex`.

* **What to build:**
* Configure the `org.graalvm.buildtools.native` Gradle plugin in `kindex-cli`.
* Set up JNI reflection configuration for SQLite JDBC and Tree-sitter native bindings (`resource-config.json` & `reflect-config.json`).
* Create a GitHub Actions release workflow that cross-compiles native executables for **Windows (`kindex.exe`)**, **Linux (`kindex`)**, and **macOS (`kindex-arm64`)** on tag releases.



---

### Option 2: SQLite FTS5 Tokenized Search Engine

Right now, `kindex query` relies on SQL `LIKE %term%` queries, which can become slow on massive codebases with hundreds of thousands of symbols and doesn't handle partial camelCase matching well.

* **What to build:**
* Integrate SQLite's **FTS5 (Full-Text Search)** virtual tables into `kindex-storage`.
* Index symbol names, package paths, and doc comments using a custom tokenizer or `unicode61` with token delimiters like `_` and uppercase transitions (splitting `UserServiceImpl` into `User`, `Service`, `Impl`).
* Support instant fuzzy search (`kindex query UserServ`).



---

### Option 3: Git Hook Integration (`kindex hook install`)

To keep the `.kindex/index.db` knowledge graph accurate without requiring developers to manually run `kindex scan` every day, `KIndex` can self-manage its index lifecycle via Git hooks.

* **What to build:**
* Implement `kindex hook install` command in `kindex-cli`.
* Writes lightweight `.git/hooks/post-commit` and `.git/hooks/post-checkout` scripts that execute `kindex scan . --quiet` in the background.
* Thanks to your incremental SHA-256 scanner (Option 1), background hook re-indexes will complete in <200ms without freezing the terminal!



---

### Option 4: Benchmarking & Profiling on Monorepos

Prove `KIndex` performance metrics by benchmarking against large open-source repositories (e.g., Kotlin compiler, Spring Framework, or Kubernetes).

* **What to build:**
* Add a `kindex benchmark` command or Gradle task using JMH / kotlinx-benchmark.
* Measure key performance indicators:
* **Cold Index Speed:** Files indexed per second.
* **Warm Incremental Re-index Speed:** Time to detect single-file modification.
* **Database Size Overhead:** Ratio of `.kindex/index.db` size to raw source code size.
* **Memory Footprint:** Peak JVM heap consumption during multithreaded parsing.
---

Let's get `KIndex` running as a true native binary and automate the build pipeline so you can distribute standalone executables with **zero JVM dependency**.

Because `KIndex` uses native JNI bridges—both for **SQLite JDBC** (`sqlite-jdbc`) and **Tree-sitter** native libraries (`jtreesitter`)—GraalVM Native Image needs explicit configuration files (`reflect-config.json`, `resource-config.json`, and `jni-config.json`) so it doesn't strip those dynamic calls during Ahead-Of-Time (AOT) compilation.

Here is the complete implementation plan for **Native Packaging & Release Automation**.

---

## 🛠 Step 1: Add the GraalVM Native Image Gradle Plugin

Update your root `gradle/libs.versions.toml`:

```toml
[plugins]
graalvm-native = { id = "org.graalvm.buildtools.native", version = "0.10.2" }

```

Now configure `kindex-cli/build.gradle.kts` to enable native compilation and bundle native reflection metadata:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graalvm.native)
    application
}

application {
    mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
}

nativeBuild {
    execution {
        mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
    }
    imageName.set("kindex")
    
    buildArgs.addAll(
        "--no-fallback",
        "-H:+ReportExceptionStackTraces",
        "-H:IncludeResources=.*\\.db$",
        "-H:IncludeResources=.*\\.so$",
        "-H:IncludeResources=.*\\.dll$",
        "-H:IncludeResources=.*\\.dylib$",
        "--enable-url-protocols=http,https"
    )
}

```

---

## 🛠 Step 2: Configure Reflection & JNI Metadata for SQLite & Tree-sitter

Create the GraalVM reachability metadata directory at `kindex-cli/src/main/resources/META-INF/native-image/dev.ajithgoveas/kindex-cli/`:

### 1. `reflect-config.json`

```json
[
  {
    "name": "org.sqlite.JDBC",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "dev.ajithgoveas.kindex.cli.MainKt",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  }
]

```

### 2. `resource-config.json`

Ensures SQLite and Tree-sitter native platform binaries are included inside the binary executable:

```json
{
  "resources": {
    "includes": [
      {"pattern": "org/sqlite/.*"},
      {"pattern": "native/.*"},
      {"pattern": ".*\\.so"},
      {"pattern": ".*\\.dll"},
      {"pattern": ".*\\.dylib"}
    ]
  }
}

```

---

## 🛠 Step 3: Test Local Native Compilation

To compile locally using your installed GraalVM instance:

```bash
# Compile native binary (this will generate build/native/nativeCompile/kindex or kindex.exe)
./gradlew :kindex-cli:nativeCompile

# Test executing the native binary directly without java -jar!
./kindex-cli/build/native/nativeCompile/kindex --help

```

---

## 🛠 Step 4: Add GitHub Actions Cross-Platform Release Pipeline

Create `.github/workflows/release.yml` in the project root to automatically build and upload cross-platform binaries (**Windows**, **macOS ARM64**, and **Linux x86_64**) whenever you push a Git tag:

```yaml
name: Release KIndex Native Binaries

on:
  push:
    tags:
      - 'v*'

jobs:
  build-native:
    name: Build Native Image (${{ matrix.os }})
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            artifact_name: kindex-linux-amd64
            binary_path: kindex-cli/build/native/nativeCompile/kindex
          - os: windows-latest
            artifact_name: kindex-windows-amd64.exe
            binary_path: kindex-cli/build/native/nativeCompile/kindex.exe
          - os: macos-latest
            artifact_name: kindex-macos-arm64
            binary_path: kindex-cli/build/native/nativeCompile/kindex

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up GraalVM JDK
        uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm'
          github-token: ${{ secrets.GITHUB_TOKEN }}
          native-image-job-reports: 'true'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Build Native Binary
        run: ./gradlew :kindex-cli:nativeCompile --no-daemon

      - name: Rename Binary
        shell: bash
        run: |
          mkdir -p dist
          cp ${{ matrix.binary_path }} dist/${{ matrix.artifact_name }}

      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: ${{ matrix.artifact_name }}
          path: dist/${{ matrix.artifact_name }}

  create-release:
    needs: build-native
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with:
          path: release-artifacts

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: release-artifacts/**/*
          draft: false
          prerelease: false
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

```

---

## Next Steps

1. Add the `graalvm-native` plugin into `libs.versions.toml` and update `kindex-cli/build.gradle.kts`.
2. Add the native reflection JSON configurations.
3. Test a local build with `./gradlew :kindex-cli:nativeCompile`!

Let me know once you run the native compile step or if any GraalVM JNI reflection warnings pop up during the build!