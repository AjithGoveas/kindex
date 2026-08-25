package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class RustExtractor : BaseExtractor("Rust", listOf("rs")) {

    data class MemberLineRange(
        val symbolId: String,
        val startLine: Int,
        val endLine: Int
    )

    override fun extract(file: MPFile): ParseResult {
        val tsLanguage = TreeSitterRust()

        val queryStr = """
            (use_declaration) @import
            (struct_item name: (type_identifier) @class_name) @class_node
            (union_item name: (type_identifier) @class_name) @class_node
            (trait_item name: (type_identifier) @interface_name) @interface_node
            (function_item name: (identifier) @function_name) @function_node
            (impl_item trait: (type_identifier)? @impl_trait type: (type_identifier) @impl_name) @impl_node
            (call_expression function: (field_expression value: (identifier) @call_recv field: (field_identifier) @call_name)) @call_node
            (call_expression function: (identifier) @call_name) @call_node
        """.trimIndent()

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
            val implName = group.text["impl_name"]
            val implTrait = group.text["impl_trait"]
            val implInfo = group.nodes["impl_node"]

            val callInfo = group.nodes["call_node"]
            val callRecv = group.text["call_recv"]
            val callName = group.text["call_name"]

            if (matchedImport != null) {
                val imported = matchedImport.replace("use", "").trim(' ', ';')
                if (imported.isNotEmpty()) {
                    edges.add(Edge(file.path, imported, RelationType.IMPORTS))
                }
            }

            if (className != null && classInfo != null) {
                symbols.add(
                    Symbol(
                        id = className,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "crate",
                        lineNumber = classInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classInfo.startRow + 1, classInfo.endRow + 1))
            }

            if (interfaceName != null && interfaceInfo != null) {
                symbols.add(
                    Symbol(
                        id = interfaceName,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = "crate",
                        lineNumber = interfaceInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, interfaceName, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(interfaceName, interfaceInfo.startRow + 1, interfaceInfo.endRow + 1))
            }

            if (implName != null && implInfo != null) {
                classLineRanges.add(ClassLineRange(implName, implInfo.startRow + 1, implInfo.endRow + 1))
                if (implTrait != null) {
                    edges.add(Edge(implName, implTrait, RelationType.EXTENDS))
                }
            }

            if (functionName != null && functionInfo != null) {
                symbols.add(
                    Symbol(
                        id = functionName,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = "crate",
                        lineNumber = functionInfo.startRow + 1
                    )
                )
                edges.add(Edge(file.path, functionName, RelationType.CONTAINS))
                functionLineRanges.add(MemberLineRange(functionName, functionInfo.startRow + 1, functionInfo.endRow + 1))
            }

            if (callInfo != null) {
                val line = callInfo.startRow + 1
                if (callName != null) {
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
                language = "Rust",
                packageName = "crate",
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
