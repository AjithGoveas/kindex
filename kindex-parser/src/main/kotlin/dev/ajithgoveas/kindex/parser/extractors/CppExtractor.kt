package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.*
import java.io.File

class CppExtractor : BaseExtractor("C++", listOf("cpp", "cc", "cxx", "hpp", "h")) {

    override fun extract(file: File): ParseResult {
        val tsLanguage = TreeSitterCpp()

        val queryStr = """
            (preproc_include path: [ (string_literal) (system_lib_string) ] @import)
            (namespace_definition name: (namespace_identifier) @package)
            (class_specifier name: (type_identifier) @class_name) @class_node
            (struct_specifier name: (type_identifier) @class_name) @class_node
            (function_declarator declarator: (identifier) @function_name) @function_node
        """.trimIndent()

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()

        var currentNamespace: String? = null

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedImport = group.text["import"]
            val matchedNamespace = group.text["package"]
            val className = group.text["class_name"]
            val classNode = group.captures["class_node"]
            val functionName = group.text["function_name"]
            val functionNode = group.captures["function_node"]

            if (matchedNamespace != null) {
                currentNamespace = matchedNamespace
            }

            if (matchedImport != null) {
                val imported = matchedImport.trim(' ', '"', '<', '>')
                edges.add(Edge(file.path, imported, RelationType.IMPORTS))
            }

            if (className != null && classNode != null) {
                val fqn = if (currentNamespace != null) "$currentNamespace::$className" else className
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = currentNamespace ?: "cpp",
                        lineNumber = classNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, classNode.startPoint.row + 1, classNode.endPoint.row + 1))

                // C++ inheritance extraction from header
                val braceIdx = sourceCode.indexOf('{', classNode.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > classNode.startByte) {
                    sourceCode.substring(classNode.startByte, braceIdx)
                } else {
                    sourceCode.substring(classNode.startByte, classNode.endByte)
                }

                if (headerText.contains(":")) {
                    val supertypes = headerText.substringAfter(":").trim().substringBefore("{").trim().split(",").map {
                        it.trim()
                          .replace(Regex("^(public|protected|private)\\s+"), "")
                          .trim()
                    }
                    for (supertype in supertypes) {
                        if (supertype.isNotEmpty()) {
                            edges.add(Edge(fqn, supertype, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (functionName != null && functionNode != null) {
                val fqn = if (currentNamespace != null) "$currentNamespace::$functionName" else functionName
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = currentNamespace ?: "cpp",
                        lineNumber = functionNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
            }
        }

        val resolvedContainmentEdges = resolveNesting(symbols, edges, classLineRanges)

        return ParseResult(
            sourceFile = SourceFile(
                path = file.path,
                language = "C++",
                packageName = currentNamespace ?: "cpp",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
