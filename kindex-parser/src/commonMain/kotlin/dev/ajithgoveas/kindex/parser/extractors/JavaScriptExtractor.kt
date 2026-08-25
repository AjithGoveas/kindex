package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class JavaScriptExtractor : BaseExtractor("JavaScript/TypeScript", listOf("js", "jsx", "ts", "tsx")) {

    data class MemberLineRange(
        val symbolId: String,
        val startLine: Int,
        val endLine: Int
    )

    override fun extract(file: MPFile): ParseResult {
        val isTypeScript = file.extension in listOf("ts", "tsx")
        val tsLanguage = if (isTypeScript) TreeSitterTypescript() else TreeSitterJavascript()

        val queryStr = if (isTypeScript) {
            """
                (import_statement source: (string) @import)
                (class_declaration name: (type_identifier) @class_name) @class_node
                (interface_declaration name: (type_identifier) @interface_name) @interface_node
                (function_declaration name: (identifier) @function_name) @function_node
                (method_definition name: (property_identifier) @function_name) @function_node
                (call_expression function: (member_expression object: (identifier) @call_recv property: (property_identifier) @call_name)) @call_node
                (call_expression function: (identifier) @call_name) @call_node
                (new_expression constructor: (identifier) @class_instantiation) @call_node
            """.trimIndent()
        } else {
            """
                (import_statement source: (string) @import)
                (class_declaration name: (identifier) @class_name) @class_node
                (function_declaration name: (identifier) @function_name) @function_node
                (method_definition name: (property_identifier) @function_name) @function_node
                (call_expression function: (member_expression object: (identifier) @call_recv property: (property_identifier) @call_name)) @call_node
                (call_expression function: (identifier) @call_name) @call_node
                (new_expression constructor: (identifier) @class_instantiation) @call_node
            """.trimIndent()
        }

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()
        val functionLineRanges = mutableListOf<MemberLineRange>()

        data class UnresolvedCall(val targetRef: String, val line: Int)
        val unresolvedCalls = mutableListOf<UnresolvedCall>()

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedImport = group.text["import"]
            val className = group.text["class_name"]
            val classInfo = group.nodes["class_node"]
            val interfaceName = group.text["interface_name"]
            val interfaceInfo = group.nodes["interface_node"]
            val functionName = group.text["function_name"]
            val functionInfo = group.nodes["function_node"]

            val callInfo = group.nodes["call_node"]
            val callRecv = group.text["call_recv"]
            val callName = group.text["call_name"]
            val classInstantiation = group.text["class_instantiation"]

            if (matchedImport != null) {
                val imported = matchedImport.trim(' ', '"', '\'')
                edges.add(Edge(file.path, imported, RelationType.IMPORTS))
            }

            if (className != null && classInfo != null) {
                symbols.add(
                    Symbol(
                        id = className,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "js_module",
                        lineNumber = classInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classInfo.startRow + 1, classInfo.endRow + 1))

                val sourceBytes = sourceCode.encodeToByteArray()
                val startB = classInfo.startByte.coerceIn(0, sourceBytes.size)
                val endB = classInfo.endByte.coerceIn(startB, sourceBytes.size)
                val headerText = sourceBytes.decodeToString(startB, endB).substringBefore("{")
                if (headerText.contains("extends")) {
                    val extended = headerText.substringAfter("extends").trim().substringBefore(" ").substringBefore("{").trim()
                    if (extended.isNotEmpty()) {
                        edges.add(Edge(className, extended, RelationType.EXTENDS))
                    }
                }
            }

            if (interfaceName != null && interfaceInfo != null) {
                symbols.add(
                    Symbol(
                        id = interfaceName,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = "js_module",
                        lineNumber = interfaceInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, interfaceName, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(interfaceName, interfaceInfo.startRow + 1, interfaceInfo.endRow + 1))

                // TS interface extends inheritance
                val braceIdx = sourceCode.indexOf('{', interfaceInfo.startByte)
                val headerText = if (braceIdx != -1 && braceIdx > interfaceInfo.startByte) {
                    sourceCode.substring(interfaceInfo.startByte, braceIdx)
                } else {
                    sourceCode.substring(interfaceInfo.startByte, interfaceInfo.endByte)
                }
                if (headerText.contains("extends")) {
                    val extended = headerText.substringAfter("extends").trim().substringBefore("{").trim()
                    if (extended.isNotEmpty()) {
                        edges.add(Edge(interfaceName, extended, RelationType.EXTENDS))
                    }
                }
            }

            if (functionName != null && functionInfo != null) {
                symbols.add(
                    Symbol(
                        id = functionName,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = "js_module",
                        lineNumber = functionInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, functionName, RelationType.CONTAINS))
                functionLineRanges.add(MemberLineRange(functionName, functionInfo.startRow + 1, functionInfo.endRow + 1))
            }

            if (callInfo != null) {
                val line = callInfo.startRow + 1
                if (classInstantiation != null) {
                    unresolvedCalls.add(UnresolvedCall("REF:$classInstantiation", line))
                } else if (callName != null) {
                    val refName = if (callRecv != null) "$callRecv.$callName" else callName
                    unresolvedCalls.add(UnresolvedCall("REF:$refName", line))
                }
            }
        }

        val resolvedContainmentEdges = resolveNesting(symbols, edges, classLineRanges).toMutableList()

        for (call in unresolvedCalls) {
            val containingFunc = functionLineRanges
                .filter { it.startLine <= call.line && it.endLine >= call.line }
                .minByOrNull { it.endLine - it.startLine }

            if (containingFunc != null) {
                resolvedContainmentEdges.add(Edge(containingFunc.symbolId, call.targetRef, RelationType.CALLS))
                continue
            }

            val containingClass = classLineRanges
                .filter { it.startLine <= call.line && it.endLine >= call.line }
                .minByOrNull { it.endLine - it.startLine }

            if (containingClass != null) {
                resolvedContainmentEdges.add(Edge(containingClass.symbolId, call.targetRef, RelationType.CALLS))
                continue
            }

            resolvedContainmentEdges.add(Edge(file.path, call.targetRef, RelationType.CALLS))
        }

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
