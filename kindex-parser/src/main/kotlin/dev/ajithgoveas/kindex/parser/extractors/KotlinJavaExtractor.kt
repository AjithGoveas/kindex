package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.model.SourceFile
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.parser.LanguageExtractor
import org.treesitter.TSParser
import org.treesitter.TSNode
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterKotlin
import java.io.File

class KotlinJavaExtractor : LanguageExtractor {

    override fun supports(file: File): Boolean {
        return file.extension in listOf("kt", "java")
    }

    override fun extract(file: File): ParseResult {
        val language = if (file.extension == "kt") "Kotlin" else "Java"
        val tsLanguage = if (file.extension == "kt") {
            TreeSitterKotlin()
        } else {
            TreeSitterJava()
        }

        val parser = TSParser()
        parser.setLanguage(tsLanguage)
        val sourceCode = file.readText()
        val tree = parser.parseString(null, sourceCode)
        val rootNode = tree.rootNode

        var packageName: String? = null
        val symbols = mutableListOf<Symbol>()

        // Traversal loop over top-level AST nodes
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue

            when (child.type) {
                "package_header", "package_declaration" -> {
                    val childText = sourceCode.substring(child.startByte, child.endByte)
                    packageName = childText.replace("package", "").trim(' ', ';', '\n', '\r')
                }
                "class_declaration", "interface_declaration" -> {
                    var nameNode: TSNode? = null
                    for (j in 0 until child.childCount) {
                        val c = child.getChild(j) ?: continue
                        if (c.type == "type_identifier" || c.type == "identifier") {
                            nameNode = c
                            break
                        }
                    }
                    val name = if (nameNode != null) {
                        sourceCode.substring(nameNode.startByte, nameNode.endByte)
                    } else {
                        "Unknown"
                    }
                    val type = if (child.type.contains("interface")) SymbolType.INTERFACE else SymbolType.CLASS
                    val fqn = if (packageName != null) "$packageName.$name" else name

                    symbols.add(
                        Symbol(
                            id = fqn,
                            name = name,
                            type = type,
                            filePath = file.path,
                            packageName = packageName,
                            lineNumber = child.startPoint.row + 1
                        )
                    )
                }
                "function_declaration", "method_declaration" -> {
                    var nameNode: TSNode? = null
                    for (j in 0 until child.childCount) {
                        val c = child.getChild(j) ?: continue
                        if (c.type == "simple_identifier" || c.type == "identifier") {
                            nameNode = c
                            break
                        }
                    }
                    val name = if (nameNode != null) {
                        sourceCode.substring(nameNode.startByte, nameNode.endByte)
                    } else {
                        "anonymous"
                    }
                    val fqn = if (packageName != null) "$packageName#$name" else name

                    symbols.add(
                        Symbol(
                            id = fqn,
                            name = name,
                            type = SymbolType.FUNCTION,
                            filePath = file.path,
                            packageName = packageName,
                            lineNumber = child.startPoint.row + 1
                        )
                    )
                }
            }
        }

        return ParseResult(
            sourceFile = SourceFile(file.path, language, packageName),
            symbols = symbols
        )
    }
}
