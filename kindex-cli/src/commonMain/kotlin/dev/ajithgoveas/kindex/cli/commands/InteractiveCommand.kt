package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.io.RepositoryGuardrail
import dev.ajithgoveas.kindex.core.io.RepositoryRootResolver
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.storage.IndexStorage

class InteractiveCommand : CliktCommand(
    name = "interactive",
    help = "Start an interactive KIndex explorer session (default mode)"
) {
    private val directoryOpt by option("-d", "--dir", help = "Project directory to analyze").default(".")

    override fun run() {
        val t = Terminal()
        val currentWorkspaceDir = MPFile(".")
        val rootDir = RepositoryRootResolver.findRepositoryRoot(currentWorkspaceDir)
        val targetDir = if (directoryOpt == ".") rootDir else MPFile(directoryOpt)

        try {
            RepositoryGuardrail.assertWithinRepository(targetDir, rootDir)
        } catch (e: Exception) {
            t.println(red(e.message ?: "Security Boundary Error"))
            return
        }

        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")

        t.println()
        t.println(magenta("""
  ██   ██ ██ ███    ██ ██████  ███████ ██   ██
  ██  ██  ██ ████   ██ ██   ██ ██       ██ ██ 
  █████   ██ ██ ██  ██ ██   ██ █████     ███  
  ██  ██  ██ ██  ██ ██ ██   ██ ██       ██ ██ 
  ██   ██ ██ ██   ████ ██████  ███████ ██   ██
        """.trimIndent()))
        t.println(dim("  ══════════════════════════════════════════════"))
        t.println(bold(cyan("          KIndex Knowledge Explorer v1.0.0")))
        t.println(dim("  ══════════════════════════════════════════════"))
        t.println()

        if (!dbFile.exists) {
            t.println(yellow("⚠️  No index found. Run '") + bold("kindex scan .") + yellow("' first to build the index."))
            t.println(yellow("    Expected: ${dbFile.path}"))
            return
        }

        val storage = IndexStorage(dbFile)

        t.println(table {
            borderType = BorderType.ROUNDED
            body {
                row(bold("Repository:") + " " + yellow(rootDir.absolutePath))
                row(bold("Database:") + "   " + green("${dbFile.path} ✓"))
            }
        })

        while (true) {
            t.println(bold("\n  Choose an action:"))
            t.println("    " + green("1") + "  Search    — Search symbols in codebase")
            t.println("    " + green("2") + "  Deps      — Query dependency references")
            t.println("    " + green("3") + "  Stats     — View structural stats")
            t.println("    " + green("4") + "  Flow      — Architectural layer analysis")
            t.println("    " + green("5") + "  Export    — Export Mermaid graph diagram")
            t.println("    " + green("6") + "  Dead      — Identify dead / unreferenced code")
            t.println("    " + red("7") + "  Quit      — Exit session\n")
            t.print(bold(cyan("kindex> ")))

            val input = readlnOrNull()?.trim() ?: break
            if (input == "7" || input == "q" || input == "quit" || input == "exit") {
                t.println(bold(green("\n👋 Goodbye. KIndex session ended.")))
                break
            }

            when (input) {
                "1", "search" -> {
                    t.print(cyan("\nSearch term: "))
                    val query = readlnOrNull()?.trim() ?: ""
                    if (query.isNotBlank()) {
                        val matches: List<Symbol> = storage.searchSymbols(query)
                        if (matches.isEmpty()) {
                            t.println(yellow("No matching symbols found for '$query'"))
                        } else {
                            t.println(bold(cyan("\n${matches.size} matching symbol(s):")))
                            t.println(table {
                                header { row("Name", "Type", "File Path") }
                                body {
                                    matches.take(20).forEach { s: Symbol ->
                                        row(s.name, s.type.name, s.filePath)
                                    }
                                }
                            })
                        }
                    }
                }

                "2", "deps" -> {
                    t.print(cyan("\nSymbol name or ID: "))
                    val target = readlnOrNull()?.trim() ?: ""
                    if (target.isNotBlank()) {
                        val incoming = storage.getIncomingDependencies(target)
                        val outgoing = storage.getOutgoingDependencies(target)
                        t.println(bold(cyan("\nDependency analysis for '$target':")))
                        t.println(bold("Incoming (dependents): ") + yellow("${incoming.size}"))
                        incoming.take(10).forEach { e -> t.println("  ← ${e.sourceId}  (${e.relation})") }
                        t.println(bold("Outgoing (calls/imports): ") + yellow("${outgoing.size}"))
                        outgoing.take(10).forEach { e -> t.println("  → ${e.targetId}  (${e.relation})") }
                    }
                }

                "3", "stats" -> {
                    val stats = storage.getRepositoryStats()
                    t.println(bold(cyan("\n=== Repository Structural Statistics ===")))
                    t.println(table {
                        header { row("Metric", "Count") }
                        body {
                            row("Total Files",          stats.fileCount.toString())
                            row("Total Symbols",        stats.symbolCount.toString())
                            row("Packages",             stats.packageCount.toString())
                            row("Classes / Interfaces", stats.classCount.toString())
                            row("Functions / Methods",  stats.functionCount.toString())
                            row("Dependency Edges",     stats.edgeCount.toString())
                        }
                    })
                }

                "4", "flow" -> {
                    val symbols = storage.getAllSymbols()
                    val edges   = storage.getAllEdges()
                    val nodes   = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
                    val byLayer = nodes.groupBy { it.layer }

                    t.println(bold(cyan("\n=== Architectural Layer Analysis ===")))
                    t.println(table {
                        header { row("Layer", "Description", "Components") }
                        body {
                            enumValues<ArchitecturalLayer>().forEach { layer: ArchitecturalLayer ->
                                val count = byLayer[layer]?.size ?: 0
                                row("${layer.emoji} ${layer.name}", layer.displayName, count.toString())
                            }
                        }
                    })

                    val entryNodes = byLayer[ArchitecturalLayer.ENTRY_POINTS]
                    if (!entryNodes.isNullOrEmpty()) {
                        t.println(bold("\nEntry Points (${entryNodes.size}):"))
                        entryNodes.take(10).forEach { n -> t.println("  🚀 ${n.name}") }
                    }
                }

                "5", "export" -> {
                    val symbols = storage.getAllSymbols()
                    val edges   = storage.getAllEdges()
                    val nodes   = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
                    val byLayer = nodes.groupBy { it.layer }

                    val mmdPath = "${rootDir.path}/.kindex/graph.mmd"
                    val mmdContent = buildString {
                        appendLine("graph TD")
                        byLayer.entries.forEach { entry ->
                            val layer: ArchitecturalLayer = entry.key
                            val layerNodes = entry.value
                            if (layerNodes.isNotEmpty()) {
                                val layerId = layer.name.lowercase()
                                appendLine("    subgraph $layerId [\"${layer.emoji} ${layer.displayName}\"]")
                                layerNodes.take(15).forEach { node ->
                                    val safeId = node.id
                                        .substringAfterLast('/')
                                        .substringAfterLast('\\')
                                        .substringBefore('#')
                                        .replace(Regex("[^a-zA-Z0-9_]"), "_")
                                    val label = node.name
                                    appendLine("        $safeId[\"$label\"]")
                                }
                                appendLine("    end")
                            }
                        }
                    }

                    val mmdFile = MPFile(mmdPath)
                    mmdFile.writeText(mmdContent)
                    t.println(green("✓ Successfully exported Mermaid diagram to $mmdPath"))
                }

                "6", "dead" -> {
                    val symbols = storage.getAllSymbols()
                    val edges   = storage.getAllEdges()
                    val importTargets = edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId }.toSet()
                    val callTargets   = edges.filter { it.relation == RelationType.CALLS }.map { it.targetId }.toSet()
                    val allTargets    = importTargets + callTargets

                    val unreferenced = symbols.filter { s: Symbol ->
                        (s.type == SymbolType.CLASS || s.type == SymbolType.INTERFACE) &&
                            !allTargets.contains(s.id) &&
                            !s.name.contains("Main") &&
                            !s.name.endsWith("Command")
                    }

                    t.println(bold(cyan("\n💀 Dead Code Candidates (${unreferenced.size}):")))
                    if (unreferenced.isEmpty()) {
                        t.println(green("  No dead code candidates found."))
                    } else {
                        t.println(table {
                            header { row("Symbol Name", "Type", "File Path") }
                            body {
                                unreferenced.take(20).forEach { s: Symbol ->
                                    row(s.name, s.type.name, s.filePath)
                                }
                            }
                        })
                    }
                }

                else -> t.println(yellow("Unknown option '$input'. Enter a number 1–7."))
            }

            t.println(dim("\n────────────────────────────────────────────"))
        }
    }
}
