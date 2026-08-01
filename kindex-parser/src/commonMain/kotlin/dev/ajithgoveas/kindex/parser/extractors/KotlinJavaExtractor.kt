package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class KotlinJavaExtractor : BaseExtractor("Kotlin/Java", listOf("kt", "java")) {

    override fun extract(file: MPFile): ParseResult {
        val isKotlin = file.extension == "kt"
        val tsLanguage = if (isKotlin) TreeSitterKotlin() else TreeSitterJava()

        val queryStr = if (isKotlin) {
            """
            (package_header (identifier) @package)
            (import_header (identifier) @import)
            (class_declaration (simple_identifier) @class_name) @class_node
            (interface_declaration (simple_identifier) @interface_name) @interface_node
            (function_declaration (simple_identifier) @function_name) @function_node
            """.trimIndent()
        } else {
            """
            (package_declaration (scoped_identifier) @package)
            (import_declaration (scoped_identifier) @import)
            (class_declaration name: (identifier) @class_name) @class_node
            (interface_declaration name: (identifier) @interface_name) @interface_node
            (method_declaration name: (identifier) @function_name) @function_node
            """.trimIndent()
        }

        val sourceCode = file.readText()
        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()
        val classLineRanges = mutableListOf<ClassLineRange>()

        var packageName: String? = null

        val groups = runQuery(tsLanguage, sourceCode, queryStr)

        for (group in groups) {
            val matchedPackage = group.text["package"]
            val matchedImport = group.text["import"]
            val className = group.text["class_name"]
            val classNode = group.captures["class_node"]
            val interfaceName = group.text["interface_name"]
            val interfaceNode = group.captures["interface_node"]
            val functionName = group.text["function_name"]
            val functionNode = group.captures["function_node"]

            if (matchedPackage != null) {
                packageName = matchedPackage
            }

            if (matchedImport != null) {
                edges.add(
                    Edge(
                        sourceId = file.path,
                        targetId = matchedImport,
                        relation = RelationType.IMPORTS
                    )
                )
            }

            if (className != null && classNode != null) {
                val fqn = if (packageName != null) "$packageName.$className" else className
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

                // Class inheritance extraction from header
                val braceIdx = sourceCode.indexOf('{', classNode.getStartByte())
                val headerText = if (braceIdx != -1 && braceIdx > classNode.getStartByte()) {
                    sourceCode.substring(classNode.getStartByte(), braceIdx)
                } else {
                    sourceCode.substring(classNode.getStartByte(), classNode.getEndByte())
                }

                if (headerText.contains("extends")) {
                    val extended = headerText.substringAfter("extends").trim().substringBefore("implements").substringBefore("{").trim().split(",").map { it.trim() }
                    for (ext in extended) {
                        if (ext.isNotEmpty()) {
                            edges.add(Edge(fqn, ext, RelationType.EXTENDS))
                        }
                    }
                }
                if (headerText.contains("implements")) {
                    val implemented = headerText.substringAfter("implements").trim().substringBefore("{").trim().split(",").map { it.trim() }
                    for (impl in implemented) {
                        if (impl.isNotEmpty()) {
                            edges.add(Edge(fqn, impl, RelationType.EXTENDS))
                        }
                    }
                }
                if (isKotlin && headerText.contains(":")) {
                    val supertypes = headerText.substringAfter(":").trim().substringBefore("{").trim().split(",").map { it.trim().substringBefore("(").substringBefore("<").trim() }
                    for (supertype in supertypes) {
                        if (supertype.isNotEmpty()) {
                            edges.add(Edge(fqn, supertype, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (interfaceName != null && interfaceNode != null) {
                val fqn = if (packageName != null) "$packageName.$interfaceName" else interfaceName
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

                // Interface inheritance from header
                val braceIdx = sourceCode.indexOf('{', interfaceNode.getStartByte())
                val headerText = if (braceIdx != -1 && braceIdx > interfaceNode.getStartByte()) {
                    sourceCode.substring(interfaceNode.getStartByte(), braceIdx)
                } else {
                    sourceCode.substring(interfaceNode.getStartByte(), interfaceNode.getEndByte())
                }
                if (headerText.contains("extends")) {
                    val extended = headerText.substringAfter("extends").trim().substringBefore("{").trim().split(",").map { it.trim() }
                    for (ext in extended) {
                        if (ext.isNotEmpty()) {
                            edges.add(Edge(fqn, ext, RelationType.EXTENDS))
                        }
                    }
                }
                if (isKotlin && headerText.contains(":")) {
                    val supertypes = headerText.substringAfter(":").trim().substringBefore("{").trim().split(",").map { it.trim().substringBefore("(").substringBefore("<").trim() }
                    for (supertype in supertypes) {
                        if (supertype.isNotEmpty()) {
                            edges.add(Edge(fqn, supertype, RelationType.EXTENDS))
                        }
                    }
                }
            }

            if (functionName != null && functionNode != null) {
                val fqn = if (packageName != null) "$packageName#$functionName" else functionName
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
