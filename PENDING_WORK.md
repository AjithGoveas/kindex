# Pending Work: KIndex Roadmap

The following tasks are queued for future engineering sprints:

## Future Extensions & Enhancements

### 1. Extended Language Extractors
- Implement `TypeScriptExtractor` and `GoExtractor` using the pre-configured version catalog coordinates.
- Extend language extraction capabilities for namespaces, interfaces, and nested structures in TS/Go.

### 2. Standalone GraalVM Binary Testing
- Validate the `graalvmNative` task output to compile native binaries (`kindex.exe` / `kindex` script).
- Verify native JNI bindings for Tree-sitter native dynamic libraries under compiled binaries.

### 3. CI/CD & Automated Distributions
- Setup GitHub Actions workflows to compile binaries for multiple operating systems (Windows, macOS, Linux).
- Publish ZIP archives of `installDist` CLI scripts on releases.
