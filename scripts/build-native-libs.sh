#!/usr/bin/env bash
# Builds static native libraries (SQLite with FTS5, tree-sitter) for Kotlin/Native.
# Linux/macOS hosts: outputs archives under third_party/dist/<target>/.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THIRD_PARTY="$REPO_ROOT/third_party"

case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) TARGET="linuxX64" ;;
    Darwin-arm64) TARGET="macosArm64" ;;
    Darwin-x86_64) TARGET="macosX64" ;;
    *) echo "Unsupported host: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

OUT_DIR="$THIRD_PARTY/dist/$TARGET"
mkdir -p "$OUT_DIR"
WORK="$(mktemp -d)"

CC="${CC:-cc}"
AR="${AR:-ar}"

echo "Host target: $TARGET"

"$CC" -c "$THIRD_PARTY/sqlite/sqlite3.c" -o "$WORK/sqlite3.o" \
    -O2 -fPIC -DNDEBUG \
    -DSQLITE_ENABLE_FTS5 \
    -DSQLITE_OMIT_LOAD_EXTENSION \
    -DSQLITE_THREADSAFE=1 \
    -I"$THIRD_PARTY/sqlite"
"$AR" rcs "$OUT_DIR/libsqlite3.a" "$WORK/sqlite3.o"

"$CC" -c "$THIRD_PARTY/tree-sitter/lib.c" -o "$WORK/treesitter_core.o" \
    -O2 -fPIC -DNDEBUG \
    -I"$THIRD_PARTY/tree-sitter/include"

TS_OBJS="$WORK/treesitter_core.o"
for gdir in "$THIRD_PARTY/grammars"/tree-sitter-*; do
    name="$(basename "$gdir")"
    "$CC" -c "$gdir/parser.c" -o "$WORK/${name}_parser.o" \
        -O2 -fPIC -DNDEBUG -I"$gdir"
    TS_OBJS="$TS_OBJS $WORK/${name}_parser.o"
    if [ -f "$gdir/scanner.c" ]; then
        "$CC" -c "$gdir/scanner.c" -o "$WORK/${name}_scanner.o" \
            -O2 -fPIC -DNDEBUG -I"$gdir"
        TS_OBJS="$TS_OBJS $WORK/${name}_scanner.o"
    fi
done

"$AR" rcs "$OUT_DIR/libtreesitter.a" $TS_OBJS

ls -lh "$OUT_DIR"
