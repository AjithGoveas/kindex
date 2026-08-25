package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class CSharpExtractor : BaseExtractor("C#", listOf("cs")) {

    override fun extract(file: MPFile): ParseResult {
        val tsLanguage = TreeSitterCSharp()

        val queryStr = """
            (using_directive (identifier) @import)
            (class_declaration name: (identifier) @class_name) @class_node
            (interface_declaration name: (identifier) @interface_name) @interface_node
            (method_declaration name: (identifier) @function_name) @function_node
        """.trimIndent()

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()

        // Check namespace using Regex since namespace nesting has changed across C# versions
        val namespaceRegex = Regex("""namespace\s+([^\s{;]+)""")
        val namespaceMatch = namespaceRegex.find(sourceCode)
        val packageName = namespaceMatch?.groupValues?.get(1) ?: "global"

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedImport = group.text["import"]
            val className = group.text["class_name"]
            val classInfo = group.nodes["class_node"]
            val interfaceName = group.text["interface_name"]
            val interfaceInfo = group.nodes["interface_node"]
            val functionName = group.text["function_name"]
            val functionInfo = group.nodes["function_node"]

            if (matchedImport != null) {
                edges.add(Edge(file.path, matchedImport, RelationType.IMPORTS))
            }

            if (className != null && classInfo != null) {
                val fqn = "$packageName.$className"
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = classInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, classInfo.startRow + 1, classInfo.endRow + 1))

                // C# class inheritance
                val braceIdx = sourceCode.indexOf('{', classInfo.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > classInfo.startByte) {
                    sourceCode.substring(classInfo.startByte, braceIdx)
                } else {
                    sourceCode.substring(classInfo.startByte, classInfo.endByte)
                }
                if (headerText.contains(":")) {
                    val inheritedList = headerText.substringAfter(":").trim().substringBefore("{").split(",").map { it.trim() }
                    for (inherited in inheritedList) {
                        if (inherited.isNotEmpty()) {
                            edges.add(Edge(fqn, inherited, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (interfaceName != null && interfaceInfo != null) {
                val fqn = "$packageName.$interfaceName"
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = interfaceInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, interfaceInfo.startRow + 1, interfaceInfo.endRow + 1))

                // C# interface inheritance
                val braceIdx = sourceCode.indexOf('{', interfaceInfo.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > interfaceInfo.startByte) {
                    sourceCode.substring(interfaceInfo.startByte, braceIdx)
                } else {
                    sourceCode.substring(interfaceInfo.startByte, interfaceInfo.endByte)
                }
                if (headerText.contains(":")) {
                    val inheritedList = headerText.substringAfter(":").trim().substringBefore("{").split(",").map { it.trim() }
                    for (inherited in inheritedList) {
                        if (inherited.isNotEmpty()) {
                            edges.add(Edge(fqn, inherited, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (functionName != null && functionInfo != null) {
                val fqn = "$packageName#$functionName"
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
                language = "C#",
                packageName = packageName,
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
