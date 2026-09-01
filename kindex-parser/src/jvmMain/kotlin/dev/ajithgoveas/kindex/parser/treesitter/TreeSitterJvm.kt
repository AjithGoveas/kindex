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

actual typealias TSPoint = org.treesitter.TSPoint

actual class TSParser actual constructor() : AutoCloseable {
    private val delegate = org.treesitter.TSParser()

    actual fun setLanguage(language: TSLanguage): Boolean = delegate.setLanguage(language)

    actual fun parseString(oldTree: TSTree?, sourceCode: String): TSTree =
        TSTree(delegate.parseString(oldTree?.delegate, sourceCode))

    actual override fun close() {
    }
}

actual class TSTree internal constructor(internal val delegate: org.treesitter.TSTree) : AutoCloseable {
    actual fun getRootNode(): TSNode = TSNode(delegate.getRootNode())

    actual override fun close() {
    }
}

actual class TSNode : AutoCloseable {
    private val node: org.treesitter.TSNode

    internal constructor(node: org.treesitter.TSNode) {
        this.node = node
    }

    fun raw(): org.treesitter.TSNode = node

    actual fun isNull(): Boolean = node.isNull()
    actual fun getParent(): TSNode? = node.getParent()?.let { TSNode(it) }
    actual fun getChildCount(): Int = node.getChildCount()
    actual fun getChild(index: Int): TSNode? = node.getChild(index)?.let { TSNode(it) }
    actual fun getType(): String = node.getType()
    actual fun getStartByte(): Int = node.getStartByte()
    actual fun getEndByte(): Int = node.getEndByte()
    actual fun getStartPoint(): TSPoint = node.getStartPoint()
    actual fun getEndPoint(): TSPoint = node.getEndPoint()

    actual override fun close() {
    }
}

actual class TSQuery actual constructor(language: TSLanguage, queryStr: String) : AutoCloseable {
    private val delegate = org.treesitter.TSQuery(language, queryStr)

    actual fun isValid(): Boolean = true

    actual fun getCaptureNameForId(id: Int): String = delegate.getCaptureNameForId(id)

    fun rawQuery(): org.treesitter.TSQuery = delegate

    actual override fun close() {
    }
}

actual class TSQueryCursor actual constructor() : AutoCloseable {
    private val delegate = org.treesitter.TSQueryCursor()

    actual fun exec(query: TSQuery, node: TSNode) {
        delegate.exec(query.rawQuery(), node.raw())
    }

    actual fun getMatches(): Iterator<TSQueryMatch> {
        val it = delegate.matches
        return object : Iterator<TSQueryMatch> {
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): TSQueryMatch = TSQueryMatch(it.next())
        }
    }

    actual override fun close() {
    }
}

actual class TSQueryMatch(val delegate: org.treesitter.TSQueryMatch) : AutoCloseable {
    actual fun getCaptures(): Array<TSQueryCapture> =
        delegate.captures.map { TSQueryCapture(it) }.toTypedArray()

    actual override fun close() {
    }
}

actual class TSQueryCapture(private val delegate: org.treesitter.TSQueryCapture) {
    actual fun getNode(): TSNode = TSNode(delegate.node)
    actual fun getIndex(): Int = delegate.index
}
