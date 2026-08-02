package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
import dev.ajithgoveas.kindex.core.analysis.EntryPointResolver
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.IndexStorage

class FlowCommand : CliktCommand(name = "flow", help = "Inspect codebase entry points, architectural layers, and component wiring") {
    private val dirArg by argument(name = "dir", help = "Project directory").optional()
    private val dirOpt by option("-d", "--dir", help = "Project directory")

    override fun run() {
        val t = Terminal()
        val directory = dirOpt ?: dirArg ?: "."
        val dbFile = MPFile("$directory/.kindex/index.db")
        if (!dbFile.exists) {
            t.println(red("No index found. Run 'kindex scan $directory' first."))
            return
        }

        val storage = IndexStorage(dbFile)
        val symbols = storage.getAllSymbols()
        val edges = storage.getAllEdges()

        if (symbols.isEmpty()) {
            t.println(yellow("Index contains no symbols for directory '$directory'."))
            return
        }

        val entryPoints = EntryPointResolver.findEntryPoints(symbols)
        val classifiedNodes = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)

        t.println(bold(cyan("Architectural Flow Analysis for '$directory':\n")))

        // Entry Points Table
        t.println(bold(magenta("🚀 Application Entry Points (${entryPoints.size}):")))
        if (entryPoints.isNotEmpty()) {
            val entryTable = table {
                header { row(bold("Kind"), bold("Symbol Name"), bold("File Location")) }
                body {
                    for (ep in entryPoints.take(10)) {
                        val file = ep.filePath.substringAfterLast("/").substringAfterLast("\\")
                        row(green(ep.kind), bold(ep.name), cyan(file))
                    }
                }
            }
            t.println(entryTable)
        } else {
            t.println(yellow("  No explicit main/CLI entry points auto-detected.\n"))
        }

        t.println()

        // Architectural Layers Summary Table
        t.println(bold(blue("🏗️ Component Distribution Across Architectural Layers:")))
        val layerCounts = classifiedNodes.groupBy { it.layer }
        val layerTable = table {
            header { row(bold("Layer"), bold("Count"), bold("Sample Components")) }
            body {
                for (layer in ArchitecturalLayer.values()) {
                    val nodes = layerCounts[layer] ?: emptyList()
                    val sample = nodes.take(4).joinToString(", ") { it.name }
                    row("${layer.emoji} ${layer.displayName}", nodes.size.toString(), gray(sample))
                }
            }
        }
        t.println(layerTable)

        t.println()

        // Top Interconnected File Dependencies
        t.println(bold(brightYellow("🔗 Top File-to-File Wiring Dependencies:")))
        if (fileEdges.isNotEmpty()) {
            val fileWiringTable = table {
                header { row(bold("Source File"), bold("Relation"), bold("Target File"), bold("Call Count")) }
                body {
                    for (edge in fileEdges.take(10)) {
                        row(cyan(edge.source), edge.relation, green(edge.target), bold(edge.weight.toString()))
                    }
                }
            }
            t.println(fileWiringTable)
        } else {
            t.println(yellow("  No cross-file dependency edges found."))
        }

        t.println()
        t.println(gray("Tip: Generate visual GitDiagram flow maps using 'kindex export $directory' (default hierarchical export)."))
    }
}
