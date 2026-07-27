package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.*
import java.io.File

class CSharpExtractor : BaseExtractor("C#", listOf("cs")) {

    override fun extract(file: File): ParseResult {
        val tsLanguage = TreeSitterCSharp()

        val queryStr = """
            (using_directive [ (qualified_name) (identifier) ] @import)
            (namespace_declaration name: [ (qualified_name) (identifier) ] @package)
            (file_scoped_namespace_declaration name: [ (qualified_name) (identifier) ] @package)
            (class_declaration name: (identifier) @class_name) @class_node
            (interface_declaration name: (identifier) @interface_name) @interface_node
            (struct_declaration name: (identifier) @class_name) @class_node
            (method_declaration name: (identifier) @function_name) @function_node
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
            val interfaceName = group.text["interface_name"]
            val interfaceNode = group.captures["interface_node"]
            val functionName = group.text["function_name"]
            val functionNode = group.captures["function_node"]

            if (matchedNamespace != null) {
                currentNamespace = matchedNamespace
            }

            if (matchedImport != null) {
                edges.add(Edge(file.path, matchedImport, RelationType.IMPORTS))
            }

            if (className != null && classNode != null) {
                val fqn = if (currentNamespace != null) "$currentNamespace.$className" else className
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = currentNamespace ?: "csharp",
                        lineNumber = classNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, classNode.startPoint.row + 1, classNode.endPoint.row + 1))

                // C# inheritance extraction
                val braceIdx = sourceCode.indexOf('{', classNode.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > classNode.startByte) {
                    sourceCode.substring(classNode.startByte, braceIdx)
                } else {
                    sourceCode.substring(classNode.startByte, classNode.endByte)
                }
                if (headerText.contains(":")) {
                    val supertypes = headerText.substringAfter(":").trim().substringBefore("{").trim().split(",").map { it.trim() }
                    for (supertype in supertypes) {
                        if (supertype.isNotEmpty()) {
                            edges.add(Edge(fqn, supertype, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (interfaceName != null && interfaceNode != null) {
                val fqn = if (currentNamespace != null) "$currentNamespace.$interfaceName" else interfaceName
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = currentNamespace ?: "csharp",
                        lineNumber = interfaceNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, fqn, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(fqn, interfaceNode.startPoint.row + 1, interfaceNode.endPoint.row + 1))

                // Interface inheritance
                val braceIdx = sourceCode.indexOf('{', interfaceNode.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > interfaceNode.startByte) {
                    sourceCode.substring(interfaceNode.startByte, braceIdx)
                } else {
                    sourceCode.substring(interfaceNode.startByte, interfaceNode.endByte)
                }
                if (headerText.contains(":")) {
                    val supertypes = headerText.substringAfter(":").trim().substringBefore("{").trim().split(",").map { it.trim() }
                    for (supertype in supertypes) {
                        if (supertype.isNotEmpty()) {
                            edges.add(Edge(fqn, supertype, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (functionName != null && functionNode != null) {
                val fqn = if (currentNamespace != null) "$currentNamespace#$functionName" else functionName
                symbols.add(
                    Symbol(
                        id = fqn,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = currentNamespace ?: "csharp",
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
                language = "C#",
                packageName = currentNamespace ?: "csharp",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
