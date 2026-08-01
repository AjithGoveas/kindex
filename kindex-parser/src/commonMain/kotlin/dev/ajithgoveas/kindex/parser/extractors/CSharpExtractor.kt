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
            val classNode = group.captures["class_node"]
            val interfaceName = group.text["interface_name"]
            val interfaceNode = group.captures["interface_node"]
            val functionName = group.text["function_name"]
            val functionNode = group.captures["function_node"]

            if (matchedImport != null) {
                edges.add(Edge(file.path, matchedImport, RelationType.IMPORTS))
            }

            if (className != null && classNode != null) {
                val fqn = "$packageName.$className"
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = classNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, classNode.getStartPoint().getRow() + 1, classNode.getEndPoint().getRow() + 1))

                // C# class inheritance
                val braceIdx = sourceCode.indexOf('{', classNode.getStartByte())
                val headerText = if (braceIdx != -1 && braceIdx > classNode.getStartByte()) {
                    sourceCode.substring(classNode.getStartByte(), braceIdx)
                } else {
                    sourceCode.substring(classNode.getStartByte(), classNode.getEndByte())
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

            if (interfaceName != null && interfaceNode != null) {
                val fqn = "$packageName.$interfaceName"
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = interfaceNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, interfaceNode.getStartPoint().getRow() + 1, interfaceNode.getEndPoint().getRow() + 1))

                // C# interface inheritance
                val braceIdx = sourceCode.indexOf('{', interfaceNode.getStartByte())
                val headerText = if (braceIdx != -1 && braceIdx > interfaceNode.getStartByte()) {
                    sourceCode.substring(interfaceNode.getStartByte(), braceIdx)
                } else {
                    sourceCode.substring(interfaceNode.getStartByte(), interfaceNode.getEndByte())
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

            if (functionName != null && functionNode != null) {
                val fqn = "$packageName#$functionName"
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = packageName,
                        lineNumber = functionNode.getStartPoint().getRow() + 1
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
