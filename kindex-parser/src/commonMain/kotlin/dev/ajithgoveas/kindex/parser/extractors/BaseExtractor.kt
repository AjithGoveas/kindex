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

    data class MatchedGroup(
        val captures: Map<String, TSNode>,
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
        val parser = TSParser()
        if (!parser.setLanguage(tsLanguage)) return emptyList()
        val tree = parser.parseString(null, sourceCode)
        val rootNode = tree.getRootNode()
        if (rootNode.isNull()) return emptyList()

        val groups = mutableListOf<MatchedGroup>()
        val utf8Bytes = sourceCode.encodeToByteArray()
        val query = TSQuery(tsLanguage, queryStr)
        if (!query.isValid()) return emptyList()

        val cursor = TSQueryCursor()
        try {
            cursor.exec(query, rootNode)
            val matches = cursor.getMatches()
            while (matches.hasNext()) {
                val match = matches.next()
                val capturesMap = mutableMapOf<String, TSNode>()
                val textMap = mutableMapOf<String, String>()
                for (capture in match.getCaptures()) {
                    val node = capture.getNode()
                    if (!node.isNull()) {
                        val name = query.getCaptureNameForId(capture.getIndex())
                        val start = minOf(node.getStartByte(), utf8Bytes.size)
                        val end = minOf(node.getEndByte(), utf8Bytes.size)
                        val text = if (start <= end && start >= 0) utf8Bytes.decodeToString(start, end).trim() else ""
                        capturesMap[name] = node
                        textMap[name] = text
                    }
                }
                groups.add(MatchedGroup(capturesMap, textMap))
            }
        } catch (_: Throwable) {
            // Guard against parser/query execution errors
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
