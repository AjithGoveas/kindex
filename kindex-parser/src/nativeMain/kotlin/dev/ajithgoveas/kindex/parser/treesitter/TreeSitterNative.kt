package dev.ajithgoveas.kindex.parser.treesitter

actual abstract class TSLanguage

actual class TreeSitterC actual constructor() : TSLanguage()
actual class TreeSitterCpp actual constructor() : TSLanguage()
actual class TreeSitterCSharp actual constructor() : TSLanguage()
actual class TreeSitterCss actual constructor() : TSLanguage()
actual class TreeSitterGo actual constructor() : TSLanguage()
actual class TreeSitterJavascript actual constructor() : TSLanguage()
actual class TreeSitterKotlin actual constructor() : TSLanguage()
actual class TreeSitterJava actual constructor() : TSLanguage()
actual class TreeSitterRust actual constructor() : TSLanguage()
actual class TreeSitterTypescript actual constructor() : TSLanguage()

actual class TSParser actual constructor() {
    actual fun setLanguage(language: TSLanguage): Boolean = false
    actual fun parseString(oldTree: TSTree?, sourceCode: String): TSTree = TSTree()
}

actual class TSTree internal constructor() {
    actual fun getRootNode(): TSNode = TSNode()
}

actual class TSNode internal constructor() {
    actual fun getParent(): TSNode? = null
    actual fun getChildCount(): Int = 0
    actual fun getChild(index: Int): TSNode? = null
    actual fun getType(): String = ""
    actual fun getStartByte(): Int = 0
    actual fun getEndByte(): Int = 0
    actual fun getStartPoint(): TSPoint = TSPoint()
    actual fun getEndPoint(): TSPoint = TSPoint()
}

actual class TSPoint internal constructor() {
    actual fun getRow(): Int = 0
}

actual class TSQuery actual constructor(language: TSLanguage, queryStr: String) {
    actual fun getCaptureNameForId(id: Int): String = ""
}

actual class TSQueryCursor actual constructor() {
    actual fun exec(query: TSQuery, node: TSNode) {}
    actual fun getMatches(): Iterator<TSQueryMatch> = emptyList<TSQueryMatch>().iterator()
}

actual class TSQueryMatch internal constructor() {
    actual fun getCaptures(): Array<TSQueryCapture> = emptyArray()
}

actual class TSQueryCapture internal constructor() {
    actual fun getNode(): TSNode = TSNode()
    actual fun getIndex(): Int = 0
}
