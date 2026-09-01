package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.LanguageExtractor
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.parser.treesitter.*

abstract class BaseExtractor(
    val languageName: String,
    val extensions: List<String>
) : LanguageExtractor {

    override fun supports(file: MPFile): Boolean {
        return file.extension in extensions
    }

    data class NodeInfo(
        val type: String,
        val startByte: Int,
        val endByte: Int,
        val startRow: Int,
        val endRow: Int,
        val childTypes: List<String>
    )

    data class MatchedGroup(
        val nodes: Map<String, NodeInfo>,
        val text: Map<String, String>
    )

    data class ClassLineRange(
        val symbolId: String,
        val startLine: Int,
        val endLine: Int
    )

    fun runQuery(
        tsLanguage: TSLanguage,
        sourceCode: String,
        queryStr: String
    ): List<MatchedGroup> {
        val groups = mutableListOf<MatchedGroup>()
        val utf8Bytes = sourceCode.encodeToByteArray()

        TSParser().use { parser ->
            if (!parser.setLanguage(tsLanguage)) return emptyList()
            parser.parseString(null, sourceCode).use { tree ->
                val rootNode = tree.getRootNode()
                try {
                    if (rootNode.isNull()) return emptyList()

                    val primaryQuery = try {
                        val q = TSQuery(tsLanguage, queryStr)
                        if (q.isValid()) q else { q.close(); null }
                    } catch (_: Throwable) {
                        null
                    }

                    val queriesToRun = mutableListOf<TSQuery>()

                    if (primaryQuery != null) {
                        queriesToRun.add(primaryQuery)
                    } else {
                        val validLines = queryStr.lines()
                            .map { it.trim() }
                            .filter { line ->
                                if (line.isEmpty() || line.startsWith(";")) {
                                    false
                                } else {
                                    try {
                                        val q = TSQuery(tsLanguage, line)
                                        val ok = q.isValid()
                                        if (!ok) q.close()
                                        ok
                                    } catch (_: Throwable) {
                                        false
                                    }
                                }
                            }
                        for (line in validLines) {
                            try {
                                val q = TSQuery(tsLanguage, line)
                                if (q.isValid()) queriesToRun.add(q) else q.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    if (queriesToRun.isEmpty()) {
                        return emptyList()
                    }

                    for (query in queriesToRun) {
                        query.use { q ->
                            TSQueryCursor().use { cursor ->
                                try {
                                    cursor.exec(q, rootNode)
                                    val matches = cursor.getMatches()
                                    while (matches.hasNext()) {
                                        val match = matches.next()
                                        try {
                                            val nodesMap = mutableMapOf<String, NodeInfo>()
                                            val textMap = mutableMapOf<String, String>()
                                            for (capture in match.getCaptures()) {
                                                val node = capture.getNode()
                                                try {
                                                    if (!node.isNull()) {
                                                        val name = q.getCaptureNameForId(capture.getIndex())
                                                        val start = minOf(node.getStartByte(), utf8Bytes.size)
                                                        val end = minOf(node.getEndByte(), utf8Bytes.size)
                                                        val text =
                                                            if (start <= end && start >= 0) utf8Bytes.decodeToString(start, end).trim() else ""
                                                        val children = (0 until node.getChildCount())
                                                            .mapNotNull { node.getChild(it)?.getType() }
                                                        nodesMap[name] = NodeInfo(
                                                            type = node.getType(),
                                                            startByte = start,
                                                            endByte = end,
                                                            startRow = node.getStartPoint().getRow(),
                                                            endRow = node.getEndPoint().getRow(),
                                                            childTypes = children
                                                        )
                                                        textMap[name] = text
                                                    }
                                                } finally {
                                                    node.close()
                                                }
                                            }
                                            groups.add(MatchedGroup(nodesMap, textMap))
                                        } finally {
                                            match.close()
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                } finally {
                    rootNode.close()
                }
            }
        }
        return groups
    }

    fun resolveNesting(
        symbols: List<Symbol>,
        edges: List<Edge>,
        classLineRanges: List<ClassLineRange>
    ): List<Edge> {
        return edges.map { edge ->
            if (edge.relation == RelationType.CONTAINS) {
                val symbol = symbols.find { it.id == edge.targetId }
                if (symbol != null && symbol.type == SymbolType.FUNCTION) {
                    val matchingClass = classLineRanges
                        .filter { it.startLine <= symbol.lineNumber && it.endLine >= symbol.lineNumber }
                        .minByOrNull { it.endLine - it.startLine }
                    if (matchingClass != null) {
                        edge.copy(sourceId = matchingClass.symbolId)
                    } else edge
                } else edge
            } else edge
        }
    }
}
