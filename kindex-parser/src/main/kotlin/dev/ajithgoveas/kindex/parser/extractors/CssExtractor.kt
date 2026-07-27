package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.*
import java.io.File

class CssExtractor : BaseExtractor("CSS", listOf("css")) {

    override fun extract(file: File): ParseResult {
        val tsLanguage = TreeSitterCss()

        val queryStr = """
            (class_selector (class_name) @class_name) @class_node
            (id_selector (id_name) @class_name) @class_node
        """.trimIndent()

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val className = group.text["class_name"]
            val classNode = group.captures["class_node"]

            if (className != null && classNode != null) {
                val cleanName = className.removePrefix(".").removePrefix("#")
                symbols.add(
                    Symbol(
                        id = cleanName,
                        name = cleanName,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "css",
                        lineNumber = classNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, cleanName, RelationType.CONTAINS))
            }
        }

        return ParseResult(
            sourceFile = SourceFile(
                path = file.path,
                language = "CSS",
                packageName = "css",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = edges
        )
    }
}
