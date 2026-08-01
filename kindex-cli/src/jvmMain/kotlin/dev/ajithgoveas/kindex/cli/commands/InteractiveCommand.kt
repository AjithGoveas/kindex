package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.parser.extractors.*
import org.jline.terminal.TerminalBuilder
import org.jline.keymap.BindingReader
import org.jline.keymap.KeyMap
import org.jline.utils.InfoCmp
import org.fusesource.jansi.AnsiConsole
import java.io.File

class InteractiveCommand : CliktCommand(
    name = "interactive",
    help = "Start an interactive KIndex session"
) {
    private val directory: File by argument(help = "Project directory to analyze").file(
        mustExist = true,
        canBeFile = false,
        canBeDir = true
    )

    enum class KeyAction { UP, DOWN, ENTER, QUIT, SEL_1, SEL_2, SEL_3, SEL_4, SEL_5, SEL_6 }

    override fun run() {
        // Initialize AnsiConsole to enable ANSI escape processing under Windows CMD/Powershell
        AnsiConsole.systemInstall()

        val t = Terminal()
        val dbDir = File(directory, ".kindex")
        val dbFile = File(dbDir, "index.db")

        // Draw welcome dashboard panel
        t.println()
        t.println(magenta("""
  ██   ██ ██ ███    ██ ██████  ███████ ██   ██
  ██  ██  ██ ████   ██ ██   ██ ██       ██ ██ 
  █████   ██ ██ ██  ██ ██   ██ █████     ███  
  ██  ██  ██ ██  ██ ██ ██   ██ ██       ██ ██ 
  ██   ██ ██ ██   ████ ██████  ███████ ██   ██
        """.trimIndent()))
        t.println(dim("  ═══════════════════════════════════════════════"))
        
        t.println(
            table {
                borderType = BorderType.DOUBLE
                header {
                    row(bold(cyan("  KINDEX KNOWLEDGE EXPLORER SESSION  ")))
                }
                body {
                    row(dim("Repository Search, Dependency Resolution & Dead Code Detection"))
                    row(bold("Directory:") + " " + yellow(directory.absolutePath))
                    row(bold("Session DB:") + " " + yellow(dbFile.path))
                }
            }
        )
        t.println(cyan("Analyzing codebase structures, please wait..."))
        
        val extractors = listOf(
            KotlinJavaExtractor(),
            RustExtractor(),
            CExtractor(),
            CppExtractor(),
            CSharpExtractor(),
            JavaScriptExtractor(),
            GoExtractor(),
            CssExtractor()
        )
        val walkedFiles = directory.walkTopDown()
            .filter { file -> file.isFile && extractors.any { it.supports(dev.ajithgoveas.kindex.core.io.MPFile(file.path)) } }
            .filter { !it.path.contains("/build/") && !it.path.contains("/.gradle/") && !it.path.contains("/.kindex/") }
            .toList()

        t.println("Found ${walkedFiles.size} source files. Extracting ASTs...")
        val parseResults = walkedFiles.map { file ->
            val mpFile = dev.ajithgoveas.kindex.core.io.MPFile(file.path)
            val ext = extractors.first { it.supports(mpFile) }
            ext.extract(mpFile)
        }

        val allSymbolsMap = parseResults.flatMap { it.symbols }.associateBy { it.id }
        val resolver = dev.ajithgoveas.kindex.parser.SymbolResolver()
        val fullyResolvedResults = parseResults.map { result ->
            val rawImportStrings = result.edges
                .filter { it.relation == dev.ajithgoveas.kindex.core.model.RelationType.IMPORTS }
                .map { it.targetId }

            val resolvedImportEdges = resolver.resolveImports(result.sourceFile.path, rawImportStrings, allSymbolsMap)
            val containmentEdges = result.edges.filter { it.relation != dev.ajithgoveas.kindex.core.model.RelationType.IMPORTS }

            result.copy(edges = containmentEdges + resolvedImportEdges)
        }

        val storage = IndexStorage(dev.ajithgoveas.kindex.core.io.MPFile(dbFile.path))
        storage.saveResultsIncremental(fullyResolvedResults, emptyList())

        t.println(green("✓ Engine is ready. Database active."))
        Thread.sleep(800)

        // Register shutdown hook to delete database folder on process kill or exit
        Runtime.getRuntime().addShutdownHook(Thread {
            cleanupSession(dbDir, t)
            AnsiConsole.systemUninstall()
        })

        // Setup JLine terminal
        var jlineTerminal = TerminalBuilder.builder().system(true).jansi(true).build()
        jlineTerminal.enterRawMode()
        var reader = jlineTerminal.reader()

        val options = listOf(
            "[SEARCH] Search symbols in codebase",
            "[DEPS]   Query dependencies & references",
            "[STATS]  View structural stats",
            "[DEAD]   Identify dead / unused code",
            "[GRAPH]  Export Mermaid graph",
            "[QUIT]   Quit session & exit"
        )

        val helpTips = listOf(
            "💡 Search codebase symbols using keywords, class/trait names, or functions.",
            "💡 Trace cross-symbol import references, incoming dependents, and outgoing callers.",
            "💡 View a metrics breakdown of the indexed repository codebase (files, packages, classes).",
            "💡 Locate dead code (classes, methods, and interfaces with 0 incoming references).",
            "💡 Generate and export a graph representation to graph.mmd in Mermaid format.",
            "💡 Tear down the local session database and safely exit the explorer."
        )

        var selectedIndex = 0

        fun drawMenu() {
            // Use JLine native clear screen to prevent duplication across different shell/cmd window environments
            jlineTerminal.puts(InfoCmp.Capability.clear_screen)
            jlineTerminal.flush()

            val writer = jlineTerminal.writer()
            // Always display the branded name/logo on top of the menu options
            writer.println(magenta("""
  ██   ██ ██ ███    ██ ██████  ███████ ██   ██
  ██  ██  ██ ████   ██ ██   ██ ██       ██ ██ 
  █████   ██ ██ ██  ██ ██   ██ █████     ███  
  ██  ██  ██ ██  ██ ██ ██   ██ ██       ██ ██ 
  ██   ██ ██ ██   ████ ██████  ███████ ██   ██
            """.trimIndent()).toString())
            writer.println(dim("  ═══════════════════════════════════════════════").toString())

            writer.println(bold(cyan("  🧭 INDEX KNOWLEDGE DASHBOARD")).toString())
            writer.println(dim("  Use Arrow keys or WASD/Vim to navigate. Numbers 1-6 to select.\n").toString())
            
            options.forEachIndexed { idx, option ->
                if (idx == selectedIndex) {
                    writer.println(bold(magenta("    ▸ $option")).toString())
                } else {
                    writer.println(dim("      $option").toString())
                }
            }

            // Divider and contextual help tip
            writer.println(dim("\n  ───────────────────────────────────────────────────────────").toString())
            writer.println("  " + yellow(helpTips[selectedIndex]) + "\n")
            writer.flush()
        }

        while (true) {
            val keyMap = KeyMap<KeyAction>()
            
            // Bind Native Terminal Capabilities
            val keyUpCap = jlineTerminal.getStringCapability(InfoCmp.Capability.key_up)
            if (keyUpCap != null) keyMap.bind(KeyAction.UP, keyUpCap)
            val keyDownCap = jlineTerminal.getStringCapability(InfoCmp.Capability.key_down)
            if (keyDownCap != null) keyMap.bind(KeyAction.DOWN, keyDownCap)

            // Bind fallback escape codes
            keyMap.bind(KeyAction.UP, "\u001b[A", "\u001bOA")
            keyMap.bind(KeyAction.DOWN, "\u001b[B", "\u001bOB")
            
            // Bind Keyboard Shortcuts
            keyMap.bind(KeyAction.UP, "w", "W", "k", "K")
            keyMap.bind(KeyAction.DOWN, "s", "S", "j", "J")
            
            keyMap.bind(KeyAction.ENTER, "\r", "\n")
            keyMap.bind(KeyAction.QUIT, "q", "Q")

            keyMap.bind(KeyAction.SEL_1, "1")
            keyMap.bind(KeyAction.SEL_2, "2")
            keyMap.bind(KeyAction.SEL_3, "3")
            keyMap.bind(KeyAction.SEL_4, "4")
            keyMap.bind(KeyAction.SEL_5, "5")
            keyMap.bind(KeyAction.SEL_6, "6")

            val bindingReader = BindingReader(reader)

            drawMenu()

            val action = bindingReader.readBinding(keyMap)
            var actionSelected = false

            when (action) {
                KeyAction.UP -> {
                    selectedIndex = (selectedIndex - 1 + options.size) % options.size
                }
                KeyAction.DOWN -> {
                    selectedIndex = (selectedIndex + 1) % options.size
                }
                KeyAction.QUIT -> {
                    break
                }
                KeyAction.SEL_1 -> { selectedIndex = 0; actionSelected = true }
                KeyAction.SEL_2 -> { selectedIndex = 1; actionSelected = true }
                KeyAction.SEL_3 -> { selectedIndex = 2; actionSelected = true }
                KeyAction.SEL_4 -> { selectedIndex = 3; actionSelected = true }
                KeyAction.SEL_5 -> { selectedIndex = 4; actionSelected = true }
                KeyAction.SEL_6 -> { selectedIndex = 5; actionSelected = true }
                KeyAction.ENTER -> {
                    actionSelected = true
                }
                else -> {}
            }

            if (actionSelected) {
                if (selectedIndex == 5) { // Quit option
                    break
                }
                // Temporarily close raw terminal to read standard stdin
                jlineTerminal.close()

                t.print("\u001b[H\u001b[2J") // Clear screen during action execution
                try {
                    when (selectedIndex) {
                        0 -> {
                            t.println(bold(magenta("🔎 SEARCH SYMBOLS IN CODEBASE")))
                            t.println(dim("───────────────────────────────────────────────────────────\n"))
                            t.print(bold("Enter search keyword") + cyan(" ❯ "))
                            val term = readlnOrNull()?.trim() ?: ""
                            if (term.isNotEmpty()) {
                                val matches = storage.searchSymbols(term)
                                if (matches.isEmpty()) {
                                    t.println(yellow("\nNo symbols found matching '$term'"))
                                } else {
                                    t.println(
                                        table {
                                            borderType = BorderType.DOUBLE
                                            header { row(bold("Kind"), bold("Name"), bold("Package"), bold("File Path"), bold("Line")) }
                                            body {
                                                matches.forEach { sym ->
                                                    row(
                                                        green(sym.type.name),
                                                        bold(white(sym.name)),
                                                        blue(sym.packageName ?: "-"),
                                                        dim(sym.filePath.removePrefix(directory.absolutePath)),
                                                        yellow(sym.lineNumber.toString())
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        1 -> {
                            t.println(bold(magenta("🔗 QUERY SYMBOL DEPENDENCIES")))
                            t.println(dim("───────────────────────────────────────────────────────────\n"))
                            t.print(bold("Enter symbol target FQN/Name") + cyan(" ❯ "))
                            val target = readlnOrNull()?.trim() ?: ""
                            if (target.isNotEmpty()) {
                                val incoming = storage.getIncomingDependencies(target)
                                val outgoing = storage.getOutgoingDependencies(target)
                                if (incoming.isEmpty() && outgoing.isEmpty()) {
                                    t.println(yellow("\nNo dependency edges found for '$target'"))
                                } else {
                                    if (outgoing.isNotEmpty()) {
                                        t.println(bold(magenta("\nOutgoing Dependencies (What it relies on):\n")))
                                        t.println(
                                            table {
                                                borderType = BorderType.DOUBLE
                                                header { row(bold("Relation"), bold("Target Symbol / File")) }
                                                body {
                                                    outgoing.forEach {
                                                        row(yellow(it.relation.name), dim(it.targetId))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    if (incoming.isNotEmpty()) {
                                        t.println(bold(magenta("\nIncoming Dependents (Who imports/uses it):\n")))
                                        t.println(
                                            table {
                                                borderType = BorderType.DOUBLE
                                                header { row(bold("Relation"), bold("Source Symbol / File")) }
                                                body {
                                                    incoming.forEach {
                                                        row(yellow(it.relation.name), dim(it.sourceId))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            t.println(bold(magenta("📊 REPOSITORY STRUCTURAL STATS")))
                            t.println(dim("───────────────────────────────────────────────────────────\n"))
                            val stats = storage.getRepositoryStats()
                            t.println(
                                table {
                                    borderType = BorderType.DOUBLE
                                    header { row(bold(cyan("Metric")), bold(cyan("Value"))) }
                                    body {
                                        row("Total Source Files", bold(white(stats.fileCount.toString())))
                                        row("Total Extracted Symbols", bold(white(stats.symbolCount.toString())))
                                        row("Total Packages", bold(white(stats.packageCount.toString())))
                                        row("Classes & Interfaces", bold(white(stats.classCount.toString())))
                                        row("Functions & Methods", bold(white(stats.functionCount.toString())))
                                        row("Relationships / Edges", bold(white(stats.edgeCount.toString())))
                                    }
                                }
                            )
                        }
                        3 -> {
                            t.println(bold(magenta("💀 DEAD CODE DETECTOR")))
                            t.println(dim("───────────────────────────────────────────────────────────\n"))
                            val symbols = storage.getAllSymbols()
                            val edges = storage.getAllEdges()
                            val importTargets = edges.filter { it.relation == dev.ajithgoveas.kindex.core.model.RelationType.IMPORTS }.map { it.targetId }.toSet()
                            val containmentParents = edges.filter { it.relation == dev.ajithgoveas.kindex.core.model.RelationType.CONTAINS }.associateBy({ it.targetId }, { it.sourceId })
                            val mainFunctions = symbols.filter { it.type == dev.ajithgoveas.kindex.core.model.SymbolType.FUNCTION && it.name.equals("main", ignoreCase = true) }
                            val entryPointContainers = mainFunctions.mapNotNull { containmentParents[it.id] }.toSet()

                            val unusedSymbols = symbols.filter { it.type == dev.ajithgoveas.kindex.core.model.SymbolType.CLASS || it.type == dev.ajithgoveas.kindex.core.model.SymbolType.INTERFACE }
                                .filter { it.id !in entryPointContainers && !it.name.equals("Main", ignoreCase = true) && !it.name.equals("KIndex", ignoreCase = true) && !it.name.endsWith("Command") }
                                .filter { it.id !in importTargets }

                            if (unusedSymbols.isEmpty()) {
                                  t.println(green("✓ No dead code candidates found! Codebase is perfectly clean."))
                            } else {
                                t.println(bold(red("\nDead Code Candidates (${unusedSymbols.size} Unused Symbols):\n")))
                                t.println(
                                    table {
                                        borderType = BorderType.DOUBLE
                                        header { row(bold("Kind"), bold("FQN Name"), bold("File Path"), bold("Line")) }
                                        body {
                                            unusedSymbols.forEach {
                                                row(
                                                    red(it.type.name),
                                                    bold(white(it.id)),
                                                    dim(it.filePath.removePrefix(directory.absolutePath)),
                                                    yellow(it.lineNumber.toString())
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        4 -> {
                            t.println(bold(magenta("🕸️ MERMAID GRAPH EXPORT")))
                            t.println(dim("───────────────────────────────────────────────────────────\n"))
                            val edges = storage.getAllEdges()
                            val mermaidContent = StringBuilder("graph TD\n")
                            val sanitizedEdges = mutableSetOf<String>()
                            for (edge in edges.take(200)) {
                                val rawSource = edge.sourceId.substringAfterLast("/").substringAfterLast("\\")
                                val rawTarget = edge.targetId.substringAfterLast("/").substringAfterLast("\\")
                                val sourceId = rawSource.replace(Regex("[^a-zA-Z0-9_]"), "_")
                                val targetId = rawTarget.replace(Regex("[^a-zA-Z0-9_]"), "_")
                                val edgeLine = "    $sourceId[\"$rawSource\"] -->|${edge.relation}| $targetId[\"$rawTarget\"]\n"
                                if (sanitizedEdges.add(edgeLine)) {
                                    mermaidContent.append(edgeLine)
                                }
                            }
                            val outputFile = File(directory, "graph.mmd")
                            outputFile.writeText(mermaidContent.toString())
                            t.println(green("✓ Exported Mermaid graph to ${outputFile.path}"))
                        }
                    }
                } catch (e: Exception) {
                    t.println(red("Error executing option: ${e.message}"))
                }

                t.print(dim("\nPress Enter to return to menu..."))
                readlnOrNull()

                // Re-enable raw terminal and rebuild reader
                jlineTerminal = TerminalBuilder.builder().system(true).jansi(true).build()
                jlineTerminal.enterRawMode()
                reader = jlineTerminal.reader()
            }
        }

        jlineTerminal.close()
        AnsiConsole.systemUninstall()
        cleanupSession(dbDir, t)
    }

    private fun cleanupSession(dbDir: File, t: Terminal) {
        if (dbDir.exists()) {
            t.println(yellow("\nCleaning up session database..."))
            dbDir.deleteRecursively()
            t.println(green("✓ Cleaned up session data."))
        }
    }
}
