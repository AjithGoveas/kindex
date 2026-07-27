package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.*
import java.io.File

class CExtractor : BaseExtractor("C", listOf("c", "h")) {

    override fun extract(file: File): ParseResult {
        val tsLanguage = TreeSitterC()

        val queryStr = """
            (preproc_include path: [ (string_literal) (system_lib_string) ] @import)
            (struct_specifier name: (type_identifier) @class_name) @class_node
            (union_specifier name: (type_identifier) @class_name) @class_node
            (function_declarator declarator: (identifier) @function_name) @function_node
        """.trimIndent()

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedImport = group.text["import"]
            val className = group.text["class_name"]
            val classNode = group.captures["class_node"]
            val functionName = group.text["function_name"]
            val functionNode = group.captures["function_node"]

            if (matchedImport != null) {
                val imported = matchedImport.trim(' ', '"', '<', '>')
                edges.add(Edge(file.path, imported, RelationType.IMPORTS))
            }

            if (className != null && classNode != null) {
                symbols.add(
                    Symbol(
                        id = className,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "c",
                        lineNumber = classNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classNode.startPoint.row + 1, classNode.endPoint.row + 1))
            }

            if (functionName != null && functionNode != null) {
                symbols.add(
                    Symbol(
                        id = functionName,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = "c",
                        lineNumber = functionNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, functionName, RelationType.CONTAINS))
            }
        }

        val resolvedContainmentEdges = resolveNesting(symbols, edges, classLineRanges)

        return ParseResult(
            sourceFile = SourceFile(
                path = file.path,
                language = "C",
                packageName = "c",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
