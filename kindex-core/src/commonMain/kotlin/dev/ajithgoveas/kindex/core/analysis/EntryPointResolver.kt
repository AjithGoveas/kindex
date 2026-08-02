package dev.ajithgoveas.kindex.core.analysis

import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType

data class EntryPoint(
    val symbolId: String,
    val name: String,
    val filePath: String,
    val kind: String
)

object EntryPointResolver {
    fun findEntryPoints(symbols: List<Symbol>): List<EntryPoint> {
        val entryPoints = mutableListOf<EntryPoint>()

        for (sym in symbols) {
            val nameLower = sym.name.lowercase()
            val fileLower = sym.filePath.lowercase()

            val isMainFunction = sym.type == SymbolType.FUNCTION && (
                sym.name == "main" || sym.name == "run" || sym.name.startsWith("main(")
            )

            val isCliCommandClass = sym.type == SymbolType.CLASS && (
                sym.name.endsWith("Command") || sym.name == "KIndex" || sym.name.endsWith("Cli")
            )

            val isScriptEntryPoint = fileLower.endsWith("main.kt") || 
                fileLower.endsWith("main.rs") || 
                fileLower.endsWith("main.go") || 
                fileLower.endsWith("index.ts") || 
                fileLower.endsWith("app.js") || 
                fileLower.endsWith("server.js")

            if (isMainFunction) {
                entryPoints.add(EntryPoint(sym.id, sym.name, sym.filePath, "Main Function"))
            } else if (isCliCommandClass) {
                entryPoints.add(EntryPoint(sym.id, sym.name, sym.filePath, "CLI Command"))
            } else if (isScriptEntryPoint && sym.type == SymbolType.CLASS) {
                entryPoints.add(EntryPoint(sym.id, sym.name, sym.filePath, "Script Entry"))
            }
        }

        return entryPoints.distinctBy { it.symbolId }
    }
}
