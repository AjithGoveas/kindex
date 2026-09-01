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

expect class TSParser() : AutoCloseable {
    fun setLanguage(language: TSLanguage): Boolean
    fun parseString(oldTree: TSTree?, sourceCode: String): TSTree
    override fun close()
}

expect class TSTree : AutoCloseable {
    fun getRootNode(): TSNode
    override fun close()
}

expect class TSNode : AutoCloseable {
    fun isNull(): Boolean
    fun getParent(): TSNode?
    fun getChildCount(): Int
    fun getChild(index: Int): TSNode?
    fun getType(): String
    fun getStartByte(): Int
    fun getEndByte(): Int
    fun getStartPoint(): TSPoint
    fun getEndPoint(): TSPoint
    override fun close()
}

expect class TSPoint {
    fun getRow(): Int
}

expect class TSQuery(language: TSLanguage, queryStr: String) : AutoCloseable {
    fun isValid(): Boolean
    fun getCaptureNameForId(id: Int): String
    override fun close()
}

expect class TSQueryCursor() : AutoCloseable {
    fun exec(query: TSQuery, node: TSNode)
    fun getMatches(): Iterator<TSQueryMatch>
    override fun close()
}

expect class TSQueryMatch : AutoCloseable {
    fun getCaptures(): Array<TSQueryCapture>
    override fun close()
}

expect class TSQueryCapture {
    fun getNode(): TSNode
    fun getIndex(): Int
}
