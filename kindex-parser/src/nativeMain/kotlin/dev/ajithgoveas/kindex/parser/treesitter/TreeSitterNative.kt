@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.ajithgoveas.kindex.parser.treesitter

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import treesitter.tsx_cursor_exec
import treesitter.tsx_cursor_free
import treesitter.tsx_cursor_new
import treesitter.tsx_cursor_next_match
import treesitter.tsx_match_capture_count
import treesitter.tsx_match_capture_index
import treesitter.tsx_match_capture_node
import treesitter.tsx_match_free
import treesitter.tsx_node_child
import treesitter.tsx_node_child_count
import treesitter.tsx_node_end_byte
import treesitter.tsx_node_end_row
import treesitter.tsx_node_free
import treesitter.tsx_node_null
import treesitter.tsx_node_parent
import treesitter.tsx_node_start_byte
import treesitter.tsx_node_start_row
import treesitter.tsx_node_type
import treesitter.tsx_parse_string
import treesitter.tsx_parser_new
import treesitter.tsx_parser_set_language
import treesitter.tsx_query_capture_name
import treesitter.tsx_query_free
import treesitter.tsx_query_new
import treesitter.tsx_tree_root
import treesitter.tree_sitter_c
import treesitter.tree_sitter_c_sharp
import treesitter.tree_sitter_cpp
import treesitter.tree_sitter_css
import treesitter.tree_sitter_go
import treesitter.tree_sitter_java
import treesitter.tree_sitter_javascript
import treesitter.tree_sitter_kotlin
import treesitter.tree_sitter_rust

actual abstract class TSLanguage {
    internal abstract val handle: COpaquePointer?
}

actual class TreeSitterC actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_c()
}

actual class TreeSitterCpp actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_cpp()
}

actual class TreeSitterCSharp actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_c_sharp()
}

actual class TreeSitterCss actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_css()
}

actual class TreeSitterGo actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_go()
}

actual class TreeSitterJavascript actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_javascript()
}

actual class TreeSitterKotlin actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_kotlin()
}

actual class TreeSitterJava actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_java()
}

actual class TreeSitterRust actual constructor() : TSLanguage() {
    internal override val handle: COpaquePointer? = tree_sitter_rust()
}

actual class TreeSitterTypescript actual constructor() : TSLanguage() {
    // TypeScript is a superset of JavaScript; reuse the JS grammar for extraction
    internal override val handle: COpaquePointer? = tree_sitter_javascript()
}

actual class TSParser actual constructor() {
    private val ptr: COpaquePointer? = tsx_parser_new()

    actual fun setLanguage(language: TSLanguage): Boolean {
        val p = ptr ?: return false
        val lang = language.handle ?: return false
        return tsx_parser_set_language(p, lang) != 0
    }

    actual fun parseString(oldTree: TSTree?, sourceCode: String): TSTree {
        val p = ptr ?: return TSTree(null)
        val tree = tsx_parse_string(p, sourceCode)
        return TSTree(tree)
    }
}

actual class TSTree internal constructor(internal val raw: COpaquePointer?) {
    actual fun getRootNode(): TSNode = TSNode(tsx_tree_root(raw))
}

actual class TSNode internal constructor(private val node: COpaquePointer?) {
    actual fun isNull(): Boolean = node == null || tsx_node_null(node) != 0
    actual fun getParent(): TSNode? =
        if (isNull()) null else TSNode(tsx_node_parent(node))
    actual fun getChildCount(): Int = tsx_node_child_count(node).toInt()
    actual fun getChild(index: Int): TSNode? = tsx_node_child(node, index.toUInt())?.let { TSNode(it) }
    actual fun getType(): String = tsx_node_type(node)?.toKString() ?: ""
    actual fun getStartByte(): Int = tsx_node_start_byte(node).toInt()
    actual fun getEndByte(): Int = tsx_node_end_byte(node).toInt()
    actual fun getStartPoint(): TSPoint = TSPoint(tsx_node_start_row(node).toInt())
    actual fun getEndPoint(): TSPoint = TSPoint(tsx_node_end_row(node).toInt())

    fun nodePtr(): COpaquePointer? = node
}

actual class TSPoint internal constructor(val row: Int) {
    actual fun getRow(): Int = row
}

actual class TSQuery actual constructor(language: TSLanguage, queryStr: String) {
    private val ptr: COpaquePointer? = tsx_query_new(language.handle, queryStr)

    actual fun isValid(): Boolean = ptr != null

    actual fun getCaptureNameForId(id: Int): String =
        ptr?.let { tsx_query_capture_name(it, id.toUInt())?.toKString() } ?: ""

    fun queryPtr(): COpaquePointer? = ptr
}

actual class TSQueryCursor actual constructor() {
    private val ptr: COpaquePointer? = tsx_cursor_new()

    actual fun exec(query: TSQuery, node: TSNode) {
        val qp = query.queryPtr() ?: return
        val np = node.nodePtr()
        if (np != null) tsx_cursor_exec(ptr, qp, np)
    }

    actual fun getMatches(): Iterator<TSQueryMatch> = object : Iterator<TSQueryMatch> {
        private var peeked: COpaquePointer? = tsx_cursor_next_match(ptr)
        private var ready = true

        override fun hasNext(): Boolean {
            if (!ready) {
                peeked = tsx_cursor_next_match(ptr)
                ready = true
            }
            return peeked != null
        }

        override fun next(): TSQueryMatch {
            if (!hasNext()) throw NoSuchElementException()
            val m = peeked
            peeked = null
            ready = false
            val matchObj = TSQueryMatch(m)
            return matchObj
        }
    }
}

actual class TSQueryMatch internal constructor(private val matchPtr: COpaquePointer?) {
    actual fun getCaptures(): Array<TSQueryCapture> {
        if (matchPtr == null) return emptyArray()
        val count = tsx_match_capture_count(matchPtr).toInt()
        return Array(count) { idx ->
            TSQueryCapture(
                tsx_match_capture_node(matchPtr, idx.toUInt()),
                tsx_match_capture_index(matchPtr, idx.toUInt()).toInt()
            )
        }
    }
}

actual class TSQueryCapture internal constructor(
    private val node: COpaquePointer?,
    private val capIndex: Int
) {
    actual fun getNode(): TSNode = TSNode(node)
    actual fun getIndex(): Int = capIndex
}