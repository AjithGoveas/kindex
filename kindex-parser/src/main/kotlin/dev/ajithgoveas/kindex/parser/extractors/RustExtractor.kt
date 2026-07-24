package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.SourceFile
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.parser.LanguageExtractor
import dev.ajithgoveas.kindex.parser.HashUtils
import org.treesitter.TSParser
import org.treesitter.TSNode
import org.treesitter.TreeSitterRust
import java.io.File

class RustExtractor : LanguageExtractor {

    override fun supports(file: File): Boolean {
        return file.extension == "rs"
    }

    override fun extract(file: File): ParseResult {
        val parser = TSParser()
        parser.setLanguage(TreeSitterRust())
        val sourceCode = file.readText()
        val tree = parser.parseString(null, sourceCode)
        val rootNode = tree.rootNode

        val symbols = mutableListOf<Symbol>()
        val edges = mutableListOf<Edge>()

        // Traversal loop over top-level AST nodes
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue

            when (child.type) {
                "use_declaration" -> {
                    val childText = sourceCode.substring(child.startByte, child.endByte)
                    val importedFqn = childText.replace("use", "").trim(' ', ';', '\n', '\r')
                    edges.add(
                        Edge(
                            sourceId = file.path,
                            targetId = importedFqn,
                            relation = RelationType.IMPORTS
                        )
                    )
                }
                "struct_item", "union_item" -> {
                    var nameNode: TSNode? = null
                    for (j in 0 until child.childCount) {
                        val c = child.getChild(j) ?: continue
                        if (c.type == "type_identifier" || c.type == "identifier") {
                            nameNode = c
                            break
                        }
                    }
                    val name = if (nameNode != null) {
                        sourceCode.substring(nameNode.startByte, nameNode.endByte).trim()
                    } else {
                        "UnknownStruct"
                    }
                    symbols.add(
                        Symbol(
                            id = name,
                            name = name,
                            type = SymbolType.CLASS,
                            filePath = file.path,
                            packageName = "crate",
                            lineNumber = child.startPoint.row + 1
                        )
                    )
                    edges.add(
                        Edge(
                            sourceId = file.path,
                            targetId = name,
                            relation = RelationType.CONTAINS
                        )
                    )
                }
                "trait_item" -> {
                    var nameNode: TSNode? = null
                    for (j in 0 until child.childCount) {
                        val c = child.getChild(j) ?: continue
                        if (c.type == "type_identifier" || c.type == "identifier") {
                            nameNode = c
                            break
                        }
                    }
                    val name = if (nameNode != null) {
                        sourceCode.substring(nameNode.startByte, nameNode.endByte).trim()
                    } else {
                        "UnknownTrait"
                    }
                    symbols.add(
                        Symbol(
                            id = name,
                            name = name,
                            type = SymbolType.INTERFACE,
                            filePath = file.path,
                            packageName = "crate",
                            lineNumber = child.startPoint.row + 1
                        )
                    )
                    edges.add(
                        Edge(
                            sourceId = file.path,
                            targetId = name,
                            relation = RelationType.CONTAINS
                        )
                    )
                }
                "function_item" -> {
                    var nameNode: TSNode? = null
                    for (j in 0 until child.childCount) {
                        val c = child.getChild(j) ?: continue
                        if (c.type == "identifier") {
                            nameNode = c
                            break
                        }
                    }
                    val name = if (nameNode != null) {
                        sourceCode.substring(nameNode.startByte, nameNode.endByte).trim()
                    } else {
                        "anonymous_fn"
                    }
                    symbols.add(
                        Symbol(
                            id = name,
                            name = name,
                            type = SymbolType.FUNCTION,
                            filePath = file.path,
                            packageName = "crate",
                            lineNumber = child.startPoint.row + 1
                        )
                    )
                    edges.add(
                        Edge(
                            sourceId = file.path,
                            targetId = name,
                            relation = RelationType.CONTAINS
                        )
                    )
                }
            }
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
            edges = edges
        )
    }
}
