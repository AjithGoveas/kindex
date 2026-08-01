# Pending Work: KIndex Roadmap

The following tasks are queued for future engineering sprints:

## Future Extensions & Enhancements

### 1. Pure Kotlin/Native Target Compilation & Linking
- Complete the native `cinterop` binding for Tree-sitter in `kindex-parser` by configuring header search paths (`api.h`) and linking local static library binaries (`libtree-sitter.a` / `tree-sitter.lib`).
- Replace native stubs in `TreeSitterNative.kt` with actual calls to the linked tree-sitter C library.

### 2. Multiplatform Native SQLite Persistence
- Integrate Touchlab's SQLiter or native C-interop SQLite bindings in `kindex-storage` (`DatabaseInitializerNative.kt`) to replace the memory/stub implementation on native desktop platforms.
- Ensure the schema creation and query routines run identically on JVM (via Exposed) and Native targets.

### 3. CI/CD & Automated Distributions
- Setup GitHub Actions workflows to compile native host binaries for multiple operating systems (Windows `mingwX64`, macOS `macosArm64`/`macosX64`, Linux `linuxX64`).
- Publish ZIP archives of releases containing native binary distributions.
