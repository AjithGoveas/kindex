package dev.ajithgoveas.kindex.parser.treesitter

actual typealias TSLanguage = org.treesitter.TSLanguage
actual typealias TreeSitterC = org.treesitter.TreeSitterC
actual typealias TreeSitterCpp = org.treesitter.TreeSitterCpp
actual typealias TreeSitterCSharp = org.treesitter.TreeSitterCSharp
actual typealias TreeSitterCss = org.treesitter.TreeSitterCss
actual typealias TreeSitterGo = org.treesitter.TreeSitterGo
actual typealias TreeSitterJavascript = org.treesitter.TreeSitterJavascript
actual typealias TreeSitterKotlin = org.treesitter.TreeSitterKotlin
actual typealias TreeSitterJava = org.treesitter.TreeSitterJava
actual typealias TreeSitterRust = org.treesitter.TreeSitterRust
actual typealias TreeSitterTypescript = org.treesitter.TreeSitterTypescript

actual typealias TSParser = org.treesitter.TSParser
actual typealias TSTree = org.treesitter.TSTree
actual typealias TSNode = org.treesitter.TSNode
actual typealias TSPoint = org.treesitter.TSPoint
actual class TSQuery actual constructor(
    language: TSLanguage,
    queryStr: String
) {
    val delegate = org.treesitter.TSQuery(language, queryStr)

    actual fun isValid(): Boolean = true

    actual fun getCaptureNameForId(id: Int): String = delegate.getCaptureNameForId(id)
}

fun TSNode.isNull(): Boolean = this.isNull

actual class TSQueryCursor actual constructor() {
    private val delegate = org.treesitter.TSQueryCursor()

    actual fun exec(query: TSQuery, node: TSNode) {
        delegate.exec(query.delegate, node)
    }

    actual fun getMatches(): Iterator<TSQueryMatch> {
        val it = delegate.matches
        return object : Iterator<TSQueryMatch> {
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): TSQueryMatch = TSQueryMatch(it.next())
        }
    }
}

actual class TSQueryMatch(val delegate: org.treesitter.TSQueryMatch) {
    actual fun getCaptures(): Array<TSQueryCapture> {
        return delegate.captures.map { TSQueryCapture(it) }.toTypedArray()
    }
}

actual class TSQueryCapture(val delegate: org.treesitter.TSQueryCapture) {
    actual fun getNode(): TSNode = delegate.node
    actual fun getIndex(): Int = delegate.index
}
