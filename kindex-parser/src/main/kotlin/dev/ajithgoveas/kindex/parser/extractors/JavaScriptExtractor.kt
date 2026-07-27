package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.*
import java.io.File

class JavaScriptExtractor : BaseExtractor("JavaScript/TypeScript", listOf("js", "jsx", "ts", "tsx")) {

    override fun extract(file: File): ParseResult {
        val isTypeScript = file.extension in listOf("ts", "tsx")
        val tsLanguage = if (isTypeScript) TreeSitterTypescript() else TreeSitterJavascript()

        val queryStr = if (isTypeScript) {
            """
                (import_statement source: (string) @import)
                (class_declaration name: (identifier) @class_name) @class_node
                (interface_declaration name: (identifier) @interface_name) @interface_node
                (function_declaration name: (identifier) @function_name) @function_node
                (method_definition name: (property_identifier) @function_name) @function_node
            """.trimIndent()
        } else {
            """
                (import_statement source: (string) @import)
                (class_declaration name: (identifier) @class_name) @class_node
                (function_declaration name: (identifier) @function_name) @function_node
                (method_definition name: (property_identifier) @function_name) @function_node
            """.trimIndent()
        }

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()

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
                val imported = matchedImport.trim(' ', '"', '\'')
                edges.add(Edge(file.path, imported, RelationType.IMPORTS))
            }

            if (className != null && classNode != null) {
                symbols.add(
                    Symbol(
                        id = className,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "js_module",
                        lineNumber = classNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classNode.startPoint.row + 1, classNode.endPoint.row + 1))

                // JS/TS inheritance
                val braceIdx = sourceCode.indexOf('{', classNode.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > classNode.startByte) {
                    sourceCode.substring(classNode.startByte, braceIdx)
                } else {
                    sourceCode.substring(classNode.startByte, classNode.endByte)
                }
                if (headerText.contains("extends")) {
                    val extended = headerText.substringAfter("extends").trim().substringBefore("{").trim()
                    if (extended.isNotEmpty()) {
                        edges.add(Edge(className, extended, RelationType.EXTENDS))
                    }
                }
            }

            if (interfaceName != null && interfaceNode != null) {
                symbols.add(
                    Symbol(
                        id = interfaceName,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = "js_module",
                        lineNumber = interfaceNode.startPoint.row + 1
                    )
                )
                edges.add(Edge(file.path, interfaceName, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(interfaceName, interfaceNode.startPoint.row + 1, interfaceNode.endPoint.row + 1))

                // TS interface extends inheritance
                val braceIdx = sourceCode.indexOf('{', interfaceNode.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > interfaceNode.startByte) {
                    sourceCode.substring(interfaceNode.startByte, braceIdx)
                } else {
                    sourceCode.substring(interfaceNode.startByte, interfaceNode.endByte)
                }
                if (headerText.contains("extends")) {
                    val extended = headerText.substringAfter("extends").trim().substringBefore("{").trim()
                    if (extended.isNotEmpty()) {
                        edges.add(Edge(interfaceName, extended, RelationType.EXTENDS))
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
                        packageName = "js_module",
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
                language = if (isTypeScript) "TypeScript" else "JavaScript",
                packageName = "js_module",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
