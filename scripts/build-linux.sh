#!/usr/bin/env bash
# Builds KIndex native executable on Linux/WSL2 and packages it into dist/
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== Building C Static Libraries for Linux ==="
"$REPO_ROOT/scripts/build-native-libs.sh"

echo "=== Compiling & Linking KIndex Linux Native Executable ==="
cd "$REPO_ROOT"
./gradlew -Pnative :kindex-cli:linkReleaseExecutableLinuxX64 --console=plain

echo "=== Packaging Linux Executable ==="
mkdir -p "$REPO_ROOT/dist"
EXE_FILE="$REPO_ROOT/kindex-cli/build/bin/linuxX64/releaseExecutable/kindex-cli.kexe"
TARGET_EXE="$REPO_ROOT/dist/kindex-linux-x64"

if [ -f "$EXE_FILE" ]; then
    cp "$EXE_FILE" "$TARGET_EXE"
    chmod +x "$TARGET_EXE"
    SHA256="$(sha256sum "$TARGET_EXE" | awk '{print $1}')"
    echo "$SHA256  kindex-linux-x64" > "$REPO_ROOT/dist/kindex-linux-x64.sha256"
    echo "✓ Packaged Linux native executable to: $TARGET_EXE"
    echo "  SHA-256: $SHA256"
else
    echo "⚠ Linux release executable not found at: $EXE_FILE" >&2
    exit 1
fi
