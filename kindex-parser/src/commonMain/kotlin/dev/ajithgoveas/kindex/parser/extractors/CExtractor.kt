package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class CExtractor : BaseExtractor("C", listOf("c", "h")) {

    override fun extract(file: MPFile): ParseResult {
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
            val classInfo = group.nodes["class_node"]
            val functionName = group.text["function_name"]
            val functionInfo = group.nodes["function_node"]

            if (matchedImport != null) {
                val imported = matchedImport.trim(' ', '"', '<', '>')
                edges.add(Edge(file.path, imported, RelationType.IMPORTS))
            }

            if (className != null && classInfo != null) {
                symbols.add(
                    Symbol(
                        id = className,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "c",
                        lineNumber = classInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classInfo.startRow + 1, classInfo.endRow + 1))
            }

            if (functionName != null && functionInfo != null) {
                symbols.add(
                    Symbol(
                        id = functionName,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = "c",
                        lineNumber = functionInfo.startRow + 1
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
