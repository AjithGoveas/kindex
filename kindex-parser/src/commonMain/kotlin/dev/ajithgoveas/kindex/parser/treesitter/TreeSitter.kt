package dev.ajithgoveas.kindex.parser.treesitter

expect abstract class TSLanguage

expect class TreeSitterC() : TSLanguage
expect class TreeSitterCpp() : TSLanguage
expect class TreeSitterCSharp() : TSLanguage
expect class TreeSitterCss() : TSLanguage
expect class TreeSitterGo() : TSLanguage
expect class TreeSitterJavascript() : TSLanguage
expect class TreeSitterKotlin() : TSLanguage
expect class TreeSitterJava() : TSLanguage
expect class TreeSitterRust() : TSLanguage
expect class TreeSitterTypescript() : TSLanguage

expect class TSParser() {
    fun setLanguage(language: TSLanguage): Boolean
    fun parseString(oldTree: TSTree?, sourceCode: String): TSTree
}

expect class TSTree {
    fun getRootNode(): TSNode
}

expect class TSNode {
    fun isNull(): Boolean
    fun getParent(): TSNode?
    fun getChildCount(): Int
    fun getChild(index: Int): TSNode?
    fun getType(): String
    fun getStartByte(): Int
    fun getEndByte(): Int
    fun getStartPoint(): TSPoint
    fun getEndPoint(): TSPoint
}

expect class TSPoint {
    fun getRow(): Int
}

expect class TSQuery(language: TSLanguage, queryStr: String) {
    fun isValid(): Boolean
    fun getCaptureNameForId(id: Int): String
}

expect class TSQueryCursor() {
    fun exec(query: TSQuery, node: TSNode)
    fun getMatches(): Iterator<TSQueryMatch>
}

expect class TSQueryMatch {
    fun getCaptures(): Array<TSQueryCapture>
}

expect class TSQueryCapture {
    fun getNode(): TSNode
    fun getIndex(): Int
}
