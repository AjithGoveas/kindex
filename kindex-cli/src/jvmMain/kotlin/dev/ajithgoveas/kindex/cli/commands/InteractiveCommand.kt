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
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
import dev.ajithgoveas.kindex.core.model.RelationType
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
        AnsiConsole.systemInstall()

        val t = Terminal()
        val dbDir = File(directory, ".kindex")
        val dbFile = File(dbDir, "index.db")

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
                .filter { it.relation == RelationType.IMPORTS }
                .map { it.targetId }

            val resolvedImportEdges = resolver.resolveImports(result.sourceFile.path, rawImportStrings, allSymbolsMap)
            val containmentEdges = result.edges.filter { it.relation != RelationType.IMPORTS }

            result.copy(edges = containmentEdges + resolvedImportEdges)
        }

        val storage = IndexStorage(dev.ajithgoveas.kindex.core.io.MPFile(dbFile.path))
        storage.saveResultsIncremental(fullyResolvedResults, emptyList())

        t.println(green("✓ Engine is ready. Database active."))
        Thread.sleep(800)

        Runtime.getRuntime().addShutdownHook(Thread {
            cleanupSession(dbDir, t)
            AnsiConsole.systemUninstall()
        })

        var jlineTerminal = TerminalBuilder.builder().system(true).jansi(true).build()
        jlineTerminal.enterRawMode()
        var reader = jlineTerminal.reader()

        val options = listOf(
            "[SEARCH] Search symbols in codebase",
            "[DEPS]   Query dependencies & references",
            "[STATS]  View structural stats",
            "[DEAD]   Identify dead / unused code",
            "[GRAPH]  Export Architectural Graphs (.kindex/)",
            "[QUIT]   Quit session & exit"
        )

        val helpTips = listOf(
            "💡 Search codebase symbols using keywords, class/trait names, or functions.",
            "💡 Trace cross-symbol import references, incoming dependents, and outgoing callers.",
            "💡 View a metrics breakdown of the indexed repository codebase (files, packages, classes).",
            "💡 Locate dead code (classes, methods, and interfaces with 0 incoming references).",
            "💡 Export Mermaid, Graphviz DOT, or JSON graph diagrams directly to .kindex/ directory.",
            "💡 Tear down the local session database and safely exit the explorer."
        )

        var selectedIndex = 0

        fun drawMenu() {
            jlineTerminal.puts(InfoCmp.Capability.clear_screen)
            jlineTerminal.flush()

            val writer = jlineTerminal.writer()
            writer.println(magenta("""
  ██   ██ ██ ███    ██ ██████  ███████ ██   ██
  ██  ██  ██ ████   ██ ██   ██ ██       ██ ██ 
  █████   ██ ██ ██  ██ ██   ██ █████     ███  
  ██  ██  ██ ██  ██ ██ ██   ██ ██       ██ ██ 
  ██   ██ ██ ██   ████ ██████  ███████ ██   ██
            """.trimIndent()))
            writer.println(dim("  ═══════════════════════════════════════════════"))

            writer.println(bold(cyan("  🧭 INDEX KNOWLEDGE DASHBOARD")))
            writer.println(dim("  Use Arrow keys or WASD/Vim to navigate. Numbers 1-6 to select.\n"))
            
            options.forEachIndexed { idx, option ->
                if (idx == selectedIndex) {
                    writer.println(bold(magenta("    ▸ $option")))
                } else {
                    writer.println(dim("      $option"))
                }
            }

            writer.println(dim("\n  ───────────────────────────────────────────────────────────"))
            writer.println("  " + yellow(helpTips[selectedIndex]) + "\n")
            writer.flush()
        }

        while (true) {
            val keyMap = KeyMap<KeyAction>()
            
            val keyUpCap = jlineTerminal.getStringCapability(InfoCmp.Capability.key_up)
            if (keyUpCap != null) keyMap.bind(KeyAction.UP, keyUpCap)
            val keyDownCap = jlineTerminal.getStringCapability(InfoCmp.Capability.key_down)
            if (keyDownCap != null) keyMap.bind(KeyAction.DOWN, keyDownCap)

            keyMap.bind(KeyAction.UP, "\u001b[A", "\u001bOA")
            keyMap.bind(KeyAction.DOWN, "\u001b[B", "\u001bOB")
            
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
                if (selectedIndex == 5) {
                    break
                }
                jlineTerminal.close()

                t.print("\u001b[H\u001b[2J")
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
                            val importTargets = edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId }.toSet()
                            val containmentParents = edges.filter { it.relation == RelationType.CONTAINS }.associateBy({ it.targetId }, { it.sourceId })
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
                            t.println(bold(magenta("🕸️ ARCHITECTURAL GRAPH EXPORTERS")))
                            t.println(dim("───────────────────────────────────────────────────────────"))
                            t.println(bold("Export files are saved directly inside .kindex/ for easy access.\n"))
                            t.println("1. Hierarchical Flow Map (Mermaid TD)  -> .kindex/graph.mmd")
                            t.println("2. Graphviz DOT Diagram                -> .kindex/graph.dot")
                            t.println("3. JSON Graph Data Payload             -> .kindex/graph.json")
                            t.println("4. File-Level Dependency Wiring        -> .kindex/file_wiring.dot")
                            t.println("5. Package-Level Dependency Wiring     -> .kindex/package_wiring.json")
                            t.println()
                            t.print(bold("Select export format [1-5]") + cyan(" ❯ "))

                            val sel = readlnOrNull()?.trim() ?: "1"
                            val symbols = storage.getAllSymbols()
                            val edges = storage.getAllEdges()

                            val (fileName, content) = when (sel) {
                                "2" -> "graph.dot" to exportDotFlow(symbols, edges)
                                "3" -> "graph.json" to exportJsonFlow(symbols, edges)
                                "4" -> "file_wiring.dot" to exportFileDot(edges)
                                "5" -> "package_wiring.json" to exportPackageJson(symbols, edges)
                                else -> "graph.mmd" to exportMermaidFlow(symbols, edges)
                            }

                            val outFile = File(dbDir, fileName)
                            outFile.writeText(content)
                            t.println(green("\n✓ Successfully exported graph to: ${outFile.absolutePath}"))
                        }
                    }
                } catch (e: Exception) {
                    t.println(red("Error executing option: ${e.message}"))
                }

                t.print(dim("\nPress Enter to return to menu..."))
                readlnOrNull()

                jlineTerminal = TerminalBuilder.builder().system(true).jansi(true).build()
                jlineTerminal.enterRawMode()
                reader = jlineTerminal.reader()
            }
        }

        jlineTerminal.close()
        AnsiConsole.systemUninstall()
        cleanupSession(dbDir, t)
    }

    private fun exportMermaidFlow(symbols: List<dev.ajithgoveas.kindex.core.model.Symbol>, edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val flowEdges = edges.filter { it.relation != RelationType.CONTAINS }
        val classified = ArchitectureFlowAnalyzer.classifyNodes(symbols, flowEdges)
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(flowEdges)
        val sb = java.lang.StringBuilder("graph TD\n")
        sb.append("    classDef entry fill:#457b9d,color:#fff,stroke:#1d3557;\n")
        sb.append("    classDef service fill:#2a9d8f,color:#fff,stroke:#264653;\n")
        sb.append("    classDef storage fill:#e76f51,color:#fff,stroke:#b7094c;\n")
        sb.append("    classDef solo fill:#e9ecef,color:#212529,stroke:#ced4da,stroke-dasharray: 5 5;\n\n")

        val layerGroups = classified.groupBy { it.layer }
        for (layer in ArchitecturalLayer.values()) {
            val nodes = layerGroups[layer] ?: continue
            val layerTitle = "${layer.emoji} ${layer.displayName}"
            sb.append("    subgraph ${layer.name}[\"$layerTitle\"]\n")
            val files = nodes.map { cleanDisplayName(it.id) }.distinct()
            val styleClass = when(layer) {
                ArchitecturalLayer.ENTRY_POINTS -> "entry"
                ArchitecturalLayer.SERVICES -> "service"
                ArchitecturalLayer.STORAGE -> "storage"
                ArchitecturalLayer.UTILITIES -> "solo"
            }
            for (f in files.take(8)) {
                val safeId = toMermaidSafeId(f)
                sb.append("        $safeId[\"$f\"]:::$styleClass\n")
            }
            sb.append("    end\n\n")
        }

        val seenEdges = mutableSetOf<String>()
        for (e in fileEdges.take(40)) {
            val srcName = cleanDisplayName(e.source)
            val tgtName = cleanDisplayName(e.target)
            val src = toMermaidSafeId(srcName)
            val tgt = toMermaidSafeId(tgtName)
            if (src != tgt) {
                val line = "    $src -->|${e.relation}| $tgt\n"
                if (seenEdges.add(line)) sb.append(line)
            }
        }
        return sb.toString()
    }

    private fun exportDotFlow(symbols: List<dev.ajithgoveas.kindex.core.model.Symbol>, edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val classified = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)
        val sb = java.lang.StringBuilder("digraph KIndexFlowGraph {\n")
        sb.append("    rankdir=TB;\n")
        sb.append("    compound=true;\n")
        sb.append("    node [shape=box, style=\"filled,rounded\", fontname=\"Helvetica\", color=\"#495057\"];\n")
        sb.append("    edge [fontname=\"Helvetica\", fontsize=9, color=\"#6C757D\"];\n\n")

        val layerGroups = classified.groupBy { it.layer }
        for (layer in ArchitecturalLayer.values()) {
            val nodes = layerGroups[layer] ?: continue
            sb.append("    subgraph cluster_${layer.name} {\n")
            sb.append("        label=\"${layer.emoji} ${layer.displayName}\";\n")
            sb.append("        style=dashed; color=\"#6C757D\";\n")
            val files = nodes.map { cleanDisplayName(it.id) }.distinct()
            for (f in files.take(8)) {
                val safeId = toMermaidSafeId(f)
                sb.append("        \"$safeId\" [label=\"$f\"];\n")
            }
            sb.append("    }\n\n")
        }

        for (e in fileEdges.take(40)) {
            val src = toMermaidSafeId(cleanDisplayName(e.source))
            val tgt = toMermaidSafeId(cleanDisplayName(e.target))
            sb.append("    \"$src\" -> \"$tgt\" [label=\"${e.relation}\"];\n")
        }
        sb.append("}\n")
        return sb.toString()
    }

    private fun exportJsonFlow(symbols: List<dev.ajithgoveas.kindex.core.model.Symbol>, edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val classified = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)
        val nodesJson = classified.map {
            """    { "id": "${cleanDisplayName(it.id)}", "name": "${it.name}", "layer": "${it.layer.name}", "type": "${it.symbolType}" }"""
        }
        val linksJson = fileEdges.map {
            """    { "source": "${cleanDisplayName(it.source)}", "target": "${cleanDisplayName(it.target)}", "relation": "${it.relation}", "weight": ${it.weight} }"""
        }
        return "{\n  \"nodes\": [\n${nodesJson.joinToString(",\n")}\n  ],\n  \"links\": [\n${linksJson.joinToString(",\n")}\n  ]\n}"
    }

    private fun exportFileDot(edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)
        val sb = java.lang.StringBuilder("digraph FileWiringGraph {\n    rankdir=LR;\n    node [shape=box, style=\"filled,rounded\", fillcolor=\"#E9ECEF\", fontname=\"Helvetica\"];\n")
        for (e in fileEdges) {
            val src = cleanDisplayName(e.source)
            val tgt = cleanDisplayName(e.target)
            sb.append("    \"$src\" -> \"$tgt\" [label=\"${e.relation} (${e.weight})\"];\n")
        }
        sb.append("}\n")
        return sb.toString()
    }

    private fun exportPackageJson(symbols: List<dev.ajithgoveas.kindex.core.model.Symbol>, edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val pkgEdges = ArchitectureFlowAnalyzer.aggregateByPackage(edges, symbols)
        val nodes = pkgEdges.flatMap { listOf(it.source, it.target) }.distinct().map {
            """    { "id": "$it", "type": "PACKAGE" }"""
        }
        val links = pkgEdges.map {
            """    { "source": "${it.source}", "target": "${it.target}", "relation": "${it.relation}", "weight": ${it.weight} }"""
        }
        return "{\n  \"nodes\": [\n${nodes.joinToString(",\n")}\n  ],\n  \"links\": [\n${links.joinToString(",\n")}\n  ]\n}"
    }

    private fun cleanDisplayName(raw: String): String {
        val path = raw.substringBefore("#")
        val fileName = path.substringAfterLast("/").substringAfterLast("\\")
        if (fileName.endsWith(".kt") || fileName.endsWith(".java") || fileName.endsWith(".rs") || 
            fileName.endsWith(".ts") || fileName.endsWith(".js") || fileName.endsWith(".go") || 
            fileName.endsWith(".c") || fileName.endsWith(".cpp") || fileName.endsWith(".cs")) {
            return fileName
        }
        val shortSymbol = if (fileName.contains(".")) fileName.substringAfterLast(".") else fileName
        return if (shortSymbol.isBlank()) "Main.kt" else "$shortSymbol.kt"
    }

    private fun toMermaidSafeId(rawName: String): String {
        val clean = rawName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val reservedKeywords = setOf("graph", "subgraph", "end", "style", "class", "classdef", "click", "direction")
        return if (clean.lowercase() in reservedKeywords || clean.isEmpty()) "node_$clean" else clean
    }

    private fun cleanupSession(dbDir: File, t: Terminal) {
        if (dbDir.exists()) {
            t.println(yellow("\nCleaning up session database..."))
            dbDir.deleteRecursively()
            t.println(green("✓ Cleaned up session data."))
        }
    }
}
