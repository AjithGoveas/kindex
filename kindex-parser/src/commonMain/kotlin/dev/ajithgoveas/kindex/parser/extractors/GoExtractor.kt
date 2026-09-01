package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class GoExtractor : BaseExtractor("Go", listOf("go")) {

    override fun extract(file: MPFile): ParseResult {
        val tsLanguage = TreeSitterGo()

        val queryStr = """
            (package_clause (package_identifier) @package)
            (type_spec name: (type_identifier) @class_name) @class_node
            (function_declaration name: (identifier) @function_name) @function_node
            (method_declaration name: (field_identifier) @function_name) @function_node
        """.trimIndent()

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()

        var packageName = "main"

        // Extract imports using robust regex matching on source code
        val singleImportRegex = Regex("""import\s+"([^"]+)"""")
        val blockImportRegex = Regex("""import\s+\(([^)]+)\)""")
        
        singleImportRegex.findAll(sourceCode).forEach { match ->
            edges.add(Edge(file.path, match.groupValues[1], RelationType.IMPORTS))
        }
        blockImportRegex.findAll(sourceCode).forEach { match ->
            val blockText = match.groupValues[1]
            Regex(""""([^"]+)"""").findAll(blockText).forEach { innerMatch ->
                edges.add(Edge(file.path, innerMatch.groupValues[1], RelationType.IMPORTS))
            }
        }

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedPackage = group.text["package"]
            val className = group.text["class_name"]
            val classInfo = group.nodes["class_node"]
            val functionName = group.text["function_name"]
            val functionInfo = group.nodes["function_node"]

            if (matchedPackage != null) {
                packageName = matchedPackage
            }

            if (className != null && classInfo != null) {
                val isInterface = "interface_type" in classInfo.childTypes

                val symbolType = if (isInterface) SymbolType.INTERFACE else SymbolType.CLASS
                val fqn = "$packageName.$className"

                symbols.add(
                    Symbol(
                        id = fqn,
                        name = className,
                        type = symbolType,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = classInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, classInfo.startRow + 1, classInfo.endRow + 1))
            }

            if (functionName != null && functionInfo != null) {
                val fqn = "$packageName.$functionName"
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = functionInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
            }
        }

        val resolvedContainmentEdges = resolveNesting(symbols, edges, classLineRanges)

        return ParseResult(
            sourceFile = SourceFile(
                path = file.path,
                language = "Go",
                packageName = packageName,
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
