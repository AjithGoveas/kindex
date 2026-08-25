package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class KotlinJavaExtractor : BaseExtractor("Kotlin/Java", listOf("kt", "java")) {

    data class MemberLineRange(
        val symbolId: String,
        val startLine: Int,
        val endLine: Int
    )

    override fun extract(file: MPFile): ParseResult {
        val isKotlin = file.extension == "kt"
        val tsLanguage = if (isKotlin) TreeSitterKotlin() else TreeSitterJava()

        val queryStr = if (isKotlin) {
            """
            (package_header) @package
            (import_header) @import
            (class_declaration (type_identifier) @class_name) @class_node
            (object_declaration (type_identifier) @class_name) @class_node
            (function_declaration (simple_identifier) @function_name) @function_node
            (call_expression (simple_identifier) @call_name) @call_node
            (class_declaration (delegation_specifier (user_type (type_identifier) @super_name)))
            (class_declaration (delegation_specifier (constructor_invocation (user_type (type_identifier) @super_name))))
            (object_declaration (delegation_specifier (user_type (type_identifier) @super_name)))
            (object_declaration (delegation_specifier (constructor_invocation (user_type (type_identifier) @super_name))))
            """.trimIndent()
        } else {
            """
            (package_declaration (scoped_identifier) @package)
            (import_declaration (scoped_identifier) @import)
            (class_declaration name: (identifier) @class_name) @class_node
            (interface_declaration name: (identifier) @interface_name) @interface_node
            (method_declaration name: (identifier) @function_name) @function_node
            (method_invocation name: (identifier) @call_name) @call_node
            (object_creation_expression type: (type_identifier) @class_instantiation) @call_node
            (class_declaration (superclass (type_identifier) @super_name))
            (class_declaration (super_interfaces (type_list (type_identifier) @super_name)))
            (interface_declaration (extends_interfaces (type_list (type_identifier) @super_name)))
            """.trimIndent()
        }

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()
        val functionLineRanges = mutableListOf<MemberLineRange>()

        data class SuperTypeRef(val target: String, val line: Int)
        val superTypeRefs = mutableListOf<SuperTypeRef>()

        data class UnresolvedCall(val targetRef: String, val line: Int)
        val unresolvedCalls = mutableListOf<UnresolvedCall>()

        var packageName: String? = null

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedPackage = group.text["package"]
            val matchedImport = group.text["import"]
            val className = group.text["class_name"]
            val classInfo = group.nodes["class_node"]
            val interfaceName = group.text["interface_name"]
            val interfaceInfo = group.nodes["interface_node"]
            val functionName = group.text["function_name"]
            val functionInfo = group.nodes["function_node"]

            val callInfo = group.nodes["call_node"]
            val callName = group.text["call_name"]
            val classInstantiation = group.text["class_instantiation"]
            val superName = group.text["super_name"]
            val superInfo = group.nodes["super_name"]

            if (matchedPackage != null) {
                packageName = matchedPackage.trim()
                    .removePrefix("package")
                    .trim()
                    .substringBefore('\n')
                    .trim()
                    .ifEmpty { null }
            }

            if (matchedImport != null) {
                edges.add(
                    Edge(
                        sourceId = file.path,
                        targetId = matchedImport.trim()
                            .removePrefix("import")
                            .trim()
                            .substringBefore('\n')
                            .trim()
                            .substringBefore(" as ")
                            .trim(),
                        relation = RelationType.IMPORTS
                    )
                )
            }

            if (className != null && classInfo != null) {
                val fqn = if (packageName != null) "$packageName.$className" else className
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
            }

            if (interfaceName != null && interfaceInfo != null) {
                val fqn = if (packageName != null) "$packageName.$interfaceName" else interfaceName
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
            }

            if (functionName != null && functionInfo != null) {
                val fqn = if (packageName != null) "$packageName#$functionName" else functionName
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
                functionLineRanges.add(MemberLineRange(fqn, functionInfo.startRow + 1, functionInfo.endRow + 1))
            }

            if (superName != null && superInfo != null) {
                superTypeRefs.add(SuperTypeRef(superName, superInfo.startRow + 1))
            }

            if (callInfo != null) {
                val line = callInfo.startRow + 1
                if (classInstantiation != null) {
                    unresolvedCalls.add(UnresolvedCall("REF:$classInstantiation", line))
                } else if (callName != null) {
                    unresolvedCalls.add(UnresolvedCall("REF:$callName", line))
                }
            }
        }

        for (ref in superTypeRefs) {
            val owner = classLineRanges
                .filter { it.startLine <= ref.line && it.endLine >= ref.line }
                .minByOrNull { it.endLine - it.startLine }
            if (owner != null) {
                edges.add(Edge(owner.symbolId, ref.target, RelationType.EXTENDS))
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
                language = if (isKotlin) "Kotlin" else "Java",
                packageName = packageName,
                lastModified = file.lastModified(),
                sha256 = HashUtils.sha256(file)
            ),
            symbols = symbols,
            edges = resolvedContainmentEdges
        )
    }
}
