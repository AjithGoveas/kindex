package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.LanguageExtractor
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.*
import java.io.File

abstract class BaseExtractor(
    val languageName: String,
    val extensions: List<String>
) : LanguageExtractor {

    override fun supports(file: File): Boolean {
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
        parser.setLanguage(tsLanguage)
        val tree = parser.parseString(null, sourceCode)
        val rootNode = tree.rootNode

        val groups = mutableListOf<MatchedGroup>()
        val query = TSQuery(tsLanguage, queryStr)
        val cursor = TSQueryCursor()
        try {
            cursor.exec(query, rootNode)
            val matches = cursor.matches
            while (matches.hasNext()) {
                val match = matches.next()
                val capturesMap = mutableMapOf<String, TSNode>()
                val textMap = mutableMapOf<String, String>()
                for (capture in match.captures) {
                    val node = capture.node
                    val name = query.getCaptureNameForId(capture.index)
                    val text = sourceCode.substring(node.startByte, node.endByte).trim()
                    capturesMap[name] = node
                    textMap[name] = text
                }
                groups.add(MatchedGroup(capturesMap, textMap))
            }
        } finally {
            // No-op or native garbage collection handles cleanup in this version
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
