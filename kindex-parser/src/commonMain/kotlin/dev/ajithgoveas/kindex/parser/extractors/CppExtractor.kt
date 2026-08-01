package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class CppExtractor : BaseExtractor("C++", listOf("cpp", "cc", "hpp", "h")) {

    override fun extract(file: MPFile): ParseResult {
        val tsLanguage = TreeSitterCpp()

        val queryStr = """
            (preproc_include path: [ (string_literal) (system_lib_string) ] @import)
            (class_specifier name: (type_identifier) @class_name) @class_node
            (struct_specifier name: (type_identifier) @class_name) @class_node
            (function_declarator declarator: (field_identifier) @function_name) @function_node
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
                        packageName = "cpp",
                        lineNumber = classNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classNode.getStartPoint().getRow() + 1, classNode.getEndPoint().getRow() + 1))

                // C++ class inheritance extraction
                val braceIdx = sourceCode.indexOf('{', classNode.getStartByte())
                val headerText = if (braceIdx != -1 && braceIdx > classNode.getStartByte()) {
                    sourceCode.substring(classNode.getStartByte(), braceIdx)
                } else {
                    sourceCode.substring(classNode.getStartByte(), classNode.getEndByte())
                }
                if (headerText.contains(":")) {
                    val extendedList = headerText.substringAfter(":").trim().substringBefore("{").split(",").map {
                        it.trim().split(" ").last().trim() // Strip 'public', 'private', 'virtual' modifiers
                    }
                    for (ext in extendedList) {
                        if (ext.isNotEmpty()) {
                            edges.add(Edge(className, ext, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (functionName != null && functionNode != null) {
                symbols.add(
                    Symbol(
                        id = functionName,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = "cpp",
                        lineNumber = functionNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, functionName, RelationType.CONTAINS))
            }
        }

        val resolvedContainmentEdges = resolveNesting(symbols, edges, classLineRanges)

        return ParseResult(
            sourceFile = SourceFile(
                path = file.path,
                language = "C++",
                packageName = "cpp",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
