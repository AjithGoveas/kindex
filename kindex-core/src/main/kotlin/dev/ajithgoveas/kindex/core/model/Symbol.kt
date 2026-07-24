package dev.ajithgoveas.kindex.core.model

enum class SymbolType {
    PACKAGE, FILE, CLASS, INTERFACE, FUNCTION
}

data class Symbol(
    val id: String,            // Unique FQN (e.g., "dev.ajithgoveas.kindex.cli.Main")
    val name: String,          // Short name (e.g., "Main")
    val type: SymbolType,
    val filePath: String,
    val packageName: String?,
    val lineNumber: Int
)

data class SourceFile(
    val path: String,
    val language: String,
    val packageName: String?
)

data class ParseResult(
    val sourceFile: SourceFile,
    val symbols: List<Symbol>
)
