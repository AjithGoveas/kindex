package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.core.io.MPFile

class DeadCommand : CliktCommand(
    name = "dead",
    help = "Find unused classes, interfaces, and unreferenced code structures"
) {
    private val directory by option("-d", "--dir", help = "Project directory").default(".")

    override fun run() {
        val t = Terminal()
        val dbFile = MPFile("$directory/.kindex/index.db")
        if (!dbFile.exists) {
            t.println(red("No index found. Run 'kindex scan $directory' first."))
            return
        }

        val storage = IndexStorage(dbFile)
        val symbols = storage.getAllSymbols()
        val edges = storage.getAllEdges()

        // Index relationships
        val importTargets = edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId }.toSet()
        val containmentParents = edges.filter { it.relation == RelationType.CONTAINS }.associateBy({ it.targetId }, { it.sourceId })

        // Find entry points
        val mainFunctions = symbols.filter { it.type == SymbolType.FUNCTION && it.name.equals("main", ignoreCase = true) }
        val entryPointContainers = mainFunctions.mapNotNull { func ->
            containmentParents[func.id]
        }.toSet()

        // Unused Classes/Interfaces detection
        val unusedSymbols = symbols.filter { it.type == SymbolType.CLASS || it.type == SymbolType.INTERFACE }
            .filter { sym ->
                // Skip entry points and common runner classes
                sym.id !in entryPointContainers &&
                !sym.name.equals("Main", ignoreCase = true) &&
                !sym.name.equals("KIndex", ignoreCase = true) &&
                !sym.name.endsWith("Command") // Skip CLI commands as they are dynamically registered
            }
            .filter { sym ->
                // Flag if no file imports this FQN
                sym.id !in importTargets
            }

        if (unusedSymbols.isEmpty()) {
            t.println(green("\n✓ No dead code candidates found! Excellent code hygiene.\n"))
            return
        }

        t.println(bold(red("\nFound ${unusedSymbols.size} Unused Symbols (Dead Code Candidates):\n")))
        t.println(
            table {
                header { row("Kind", "FQN Name", "File Path", "Line") }
                body {
                    unusedSymbols.forEach { sym ->
                        row(
                            sym.type.name,
                            sym.id,
                            sym.filePath.removePrefix(MPFile(directory).absolutePath),
                            sym.lineNumber.toString()
                        )
                    }
                }
            }
        )
    }
}
