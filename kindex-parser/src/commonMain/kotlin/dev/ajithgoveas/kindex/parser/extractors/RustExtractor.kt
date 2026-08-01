package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.parser.treesitter.*

class RustExtractor : BaseExtractor("Rust", listOf("rs")) {

    override fun extract(file: MPFile): ParseResult {
        val tsLanguage = TreeSitterRust()

        val queryStr = """
            (use_declaration (use_clause) @import)
            (struct_item name: (type_identifier) @class_name) @class_node
            (union_item name: (type_identifier) @class_name) @class_node
            (trait_item name: (type_identifier) @interface_name) @interface_node
            (function_item name: (identifier) @function_name) @function_node
            (impl_item trait: (type_identifier)? @impl_trait type: (type_identifier) @impl_name) @impl_node
        """.trimIndent()

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
            val implName = group.text["impl_name"]
            val implTrait = group.text["impl_trait"]
            val implNode = group.captures["impl_node"]

            if (matchedImport != null) {
                val imported = matchedImport.replace("use", "").trim(' ', ';')
                if (imported.isNotEmpty()) {
                    edges.add(Edge(file.path, imported, RelationType.IMPORTS))
                }
            }

            if (className != null && classNode != null) {
                symbols.add(
                    Symbol(
                        id = className,
                        name = className,
                        type = SymbolType.CLASS,
                        filePath = file.path,
                        packageName = "crate",
                        lineNumber = classNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, className, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(className, classNode.getStartPoint().getRow() + 1, classNode.getEndPoint().getRow() + 1))
            }

            if (interfaceName != null && interfaceNode != null) {
                symbols.add(
                    Symbol(
                        id = interfaceName,
                        name = interfaceName,
                        type = SymbolType.INTERFACE,
                        filePath = file.path,
                        packageName = "crate",
                        lineNumber = interfaceNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, interfaceName, RelationType.CONTAINS))
                classLineRanges.add(ClassLineRange(interfaceName, interfaceNode.getStartPoint().getRow() + 1, interfaceNode.getEndPoint().getRow() + 1))
            }

            if (implName != null && implNode != null) {
                classLineRanges.add(ClassLineRange(implName, implNode.getStartPoint().getRow() + 1, implNode.getEndPoint().getRow() + 1))
                if (implTrait != null) {
                    edges.add(Edge(implName, implTrait, RelationType.EXTENDS))
                }
            }

            if (functionName != null && functionNode != null) {
                symbols.add(
                    Symbol(
                        id = functionName,
                        name = functionName,
                        type = SymbolType.FUNCTION,
                        filePath = file.path,
                        packageName = "crate",
                        lineNumber = functionNode.getStartPoint().getRow() + 1
                    )
                )
                edges.add(Edge(file.path, functionName, RelationType.CONTAINS))
            }
        }

        val resolvedContainmentEdges = resolveNesting(symbols, edges, classLineRanges)

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
