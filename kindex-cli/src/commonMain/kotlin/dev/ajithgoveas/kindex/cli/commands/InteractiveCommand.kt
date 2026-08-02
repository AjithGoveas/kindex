package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import dev.ajithgoveas.kindex.cli.walkFiles
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
import dev.ajithgoveas.kindex.core.analysis.ModuleGraphAnalyzer
import dev.ajithgoveas.kindex.core.io.KeyEvent
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.io.RepositoryGuardrail
import dev.ajithgoveas.kindex.core.io.RepositoryRootResolver
import dev.ajithgoveas.kindex.core.io.disableRawMode
import dev.ajithgoveas.kindex.core.io.enableRawMode
import dev.ajithgoveas.kindex.core.io.readKey
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.core.io.getTerminalSize

// ─── ANSI escape helpers ─────────────────────────────────────────────────────

private const val ESC     = "\u001B"
private const val RESET   = "$ESC[0m"
private const val BOLD    = "$ESC[1m"
private const val DIM     = "$ESC[2m"
private const val CLEAR   = "$ESC[2J$ESC[H"
private const val ALT_ON  = "$ESC[?1049h"
private const val ALT_OFF = "$ESC[?1049l"
private const val HIDE    = "$ESC[?25l"
private const val SHOW    = "$ESC[?25h"

private const val CLR_EOL = "$ESC[0K"

private fun fg(r: Int, g: Int, b: Int) = "$ESC[38;2;$r;$g;${b}m"
private fun bg(r: Int, g: Int, b: Int) = "$ESC[48;2;$r;$g;${b}m"
private fun at(row: Int, col: Int)     = "$ESC[${row};${col}H"

// Colour palette
private val C_ACCENT   = fg(139,  92, 246)  // purple
private val C_ACCENT2  = fg(167, 139, 250)  // lavender
private val C_SUCCESS  = fg( 52, 211, 153)  // emerald
private val C_WARN     = fg(251, 191,  36)  // amber
private val C_MUTED    = fg(107, 114, 128)  // grey-500
private val C_BRIGHT   = fg(243, 244, 246)  // grey-100
private val C_RED      = fg(248, 113, 113)  // red-400
private val C_CYAN     = fg( 34, 211, 238)  // cyan
private val BG_TITLE   = bg( 17,  24,  39)  // grey-900
private val BG_SEL     = bg( 55,  48, 163)  // indigo-800

private const val MENU_W   = 24
private const val HEADER_H = 3
private const val FOOTER_H = 2
private const val DIV_COL  = MENU_W + 1
private const val CONT_COL = MENU_W + 3

private data class MenuItem(val num: String, val label: String, val desc: String)

private val MENU_ITEMS = listOf(
    MenuItem("1", "Scan",     "Re-index repository"),
    MenuItem("2", "Search",   "Fuzzy symbol search"),
    MenuItem("3", "Deps",     "Call graph & references"),
    MenuItem("4", "Stats",    "Repository metrics"),
    MenuItem("5", "Flow",     "Architectural layers"),
    MenuItem("6", "Export",   "Export diagram (Submenu)"),
    MenuItem("7", "Dead Code","Unreferenced symbols"),
)

private enum class TuiScreen { Menu, Input, ExportSubMenu, Content }

private val buf = StringBuilder(8192)
private fun w(s: String)  { buf.append(s) }
private fun flush()       { print(buf); buf.clear() }

class InteractiveCommand : CliktCommand(
    name        = "interactive",
    help        = "Start an interactive KIndex explorer session (default mode)"
) {
    private val dirOpt by option("-d", "--dir", help = "Project directory to analyze").default(".")

    private lateinit var rootDir: MPFile
    private lateinit var storage: IndexStorage

    private val termW: Int get() = getTerminalSize().first
    private val termH: Int get() = getTerminalSize().second

    override fun run() {
        val cwd = MPFile(".")
        rootDir = RepositoryRootResolver.findRepositoryRoot(cwd)
        val target = if (dirOpt == ".") rootDir else MPFile(dirOpt)

        try {
            RepositoryGuardrail.assertWithinRepository(target, rootDir)
        } catch (e: Exception) {
            println("$C_RED${e.message}$RESET"); return
        }

        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
        if (!dbFile.exists) {
            println("${C_WARN}No index found. Run:  kindex scan .$RESET"); return
        }

        storage = IndexStorage(dbFile)
        enableRawMode()
        print(ALT_ON + HIDE + CLEAR)

        try { loop() } finally { print(SHOW + ALT_OFF); disableRawMode() }
    }

    private fun loop() {
        var sel       = 0
        var exportSel = 0
        var mode      = TuiScreen.Menu
        var lines     = emptyList<String>()
        var inBuf     = ""
        var inPmt     = ""
        var inCb: (String) -> List<String> = { emptyList() }

        while (true) {
            render(sel, exportSel, mode, lines, inBuf, inPmt)
            val k = readKey()

            when (mode) {
                TuiScreen.Menu -> when (k) {
                    KeyEvent.Up    -> sel = (sel - 1 + MENU_ITEMS.size) % MENU_ITEMS.size
                    KeyEvent.Down  -> sel = (sel + 1) % MENU_ITEMS.size
                    KeyEvent.Enter -> {
                        if (sel == 5) {
                            mode = TuiScreen.ExportSubMenu
                            exportSel = 0
                        } else {
                            mode = activate(sel).also {
                                if (it == TuiScreen.Input) {
                                    inBuf = ""
                                    inPmt = promptFor(sel)
                                    inCb = callbackFor(sel)
                                } else lines = dataFor(sel)
                            }
                        }
                    }
                    KeyEvent.Escape -> return
                    is KeyEvent.Character -> {
                        if (k.c == 'q') return
                        val n = k.c.digitToIntOrNull()
                        if (n != null && n in 1..MENU_ITEMS.size) {
                            sel = n - 1
                            if (sel == 5) {
                                mode = TuiScreen.ExportSubMenu
                                exportSel = 0
                            } else {
                                mode = activate(sel).also {
                                    if (it == TuiScreen.Input) {
                                        inBuf = ""
                                        inPmt = promptFor(sel)
                                        inCb = callbackFor(sel)
                                    } else lines = dataFor(sel)
                                }
                            }
                        }
                    }
                    else -> {}
                }

                TuiScreen.ExportSubMenu -> when (k) {
                    KeyEvent.Up -> exportSel = (exportSel - 1 + 3) % 3
                    KeyEvent.Down -> exportSel = (exportSel + 1) % 3
                    KeyEvent.Enter -> {
                        val fmt = when (exportSel) {
                            0 -> "mermaid"
                            1 -> "dot"
                            else -> "json"
                        }
                        lines = doExport(fmt)
                        mode = TuiScreen.Content
                    }
                    KeyEvent.Escape -> mode = TuiScreen.Menu
                    is KeyEvent.Character -> {
                        val n = k.c.digitToIntOrNull()
                        if (n != null && n in 1..3) {
                            exportSel = n - 1
                            val fmt = when (exportSel) {
                                0 -> "mermaid"
                                1 -> "dot"
                                else -> "json"
                            }
                            lines = doExport(fmt)
                            mode = TuiScreen.Content
                        }
                    }
                    else -> {}
                }

                TuiScreen.Input -> when (k) {
                    KeyEvent.Enter     -> { lines = inCb(inBuf); mode = TuiScreen.Content }
                    KeyEvent.Escape    -> { mode = TuiScreen.Menu; inBuf = "" }
                    KeyEvent.Backspace -> if (inBuf.isNotEmpty()) inBuf = inBuf.dropLast(1)
                    is KeyEvent.Character -> inBuf += k.c
                    else -> {}
                }

                TuiScreen.Content -> when (k) {
                    KeyEvent.Escape, KeyEvent.Enter -> mode = TuiScreen.Menu
                    is KeyEvent.Character -> if (k.c == 'q') mode = TuiScreen.Menu
                    else -> {}
                }
            }
        }
    }

    private fun activate(sel: Int)   = if (sel in listOf(1, 2)) TuiScreen.Input else TuiScreen.Content
    private fun promptFor(sel: Int)  = if (sel == 1) "Search symbol" else "Symbol name"
    private fun callbackFor(sel: Int) = if (sel == 1) ::doSearch else ::doDeps
    private fun dataFor(sel: Int) = when (sel) {
        0    -> doScan()
        3    -> doStats()
        4    -> doFlow()
        6    -> doDeadCode()
        else -> emptyList()
    }

    private fun render(sel: Int, exportSel: Int, mode: TuiScreen, lines: List<String>, inBuf: String, inPmt: String) {
        val w = termW
        val h = termH
        val bodyH = h - HEADER_H - FOOTER_H
        val menuRows = MENU_ITEMS.size * 2 + 2
        val contW = w - CONT_COL

        buf.clear()
        w(CLEAR)

        // Header
        w(at(1, 1))
        w(BG_TITLE + " ".repeat(w) + RESET)
        w(at(1, 1))
        w("$BG_TITLE $BOLD$C_ACCENT KINDEX$RESET$BG_TITLE $C_MUTED v1.0.0$RESET$BG_TITLE")
        w("  $C_MUTED|$RESET$BG_TITLE  ${rootDir.absolutePath.takeLast(w - 30)}")
        val qStr = "  [q] Quit "
        w(at(1, w - qStr.length + 1))
        w("$BG_TITLE$C_RED$qStr$RESET")

        w(at(2, 1))
        w("$C_MUTED${"─".repeat(w)}$RESET")

        // Sidebar
        val sideTop = HEADER_H + 1
        w(at(sideTop, 1))
        w("$C_MUTED╭${"─".repeat(MENU_W - 2)}╮$RESET")

        for (i in MENU_ITEMS.indices) {
            val item  = MENU_ITEMS[i]
            val rowA  = sideTop + 1 + i * 2
            val rowB  = rowA + 1
            val active = (i == sel && (mode == TuiScreen.Menu || mode == TuiScreen.ExportSubMenu))
            val numStr = item.num
            val labelPad = (MENU_W - 7).coerceAtLeast(0)
            val descPad  = (MENU_W - 5).coerceAtLeast(0)

            w(at(rowA, 1))
            if (active) {
                w("$C_MUTED│$RESET$BG_SEL $BOLD$C_ACCENT2$numStr$RESET$BG_SEL $BOLD$C_BRIGHT${item.label.padEnd(labelPad)}$RESET$BG_SEL $C_MUTED│$RESET")
            } else {
                w("$C_MUTED│$RESET $C_MUTED$numStr$RESET $C_BRIGHT${item.label.padEnd(labelPad)}$RESET $C_MUTED│$RESET")
            }

            w(at(rowB, 1))
            w("$C_MUTED│  $DIM${item.desc.take(descPad).padEnd(descPad)}$RESET$C_MUTED│$RESET")
        }

        val sideBot = sideTop + menuRows - 1
        w(at(sideBot, 1))
        w("$C_MUTED╰${"─".repeat(MENU_W - 2)}╯$RESET")

        for (r in sideTop..<sideTop + bodyH) {
            w(at(r, DIV_COL))
            w("$C_MUTED│$RESET")
        }

        // Content Pane
        val contTop  = sideTop
        val contRows = bodyH - 1

        when (mode) {
            TuiScreen.Menu -> {
                val welcome = buildWelcome()
                for (r in 0 until contRows) {
                    w(at(contTop + r, CONT_COL))
                    w(CLR_EOL)
                    if (r < welcome.size) w(welcome[r])
                }
            }

            TuiScreen.ExportSubMenu -> {
                val subOptions = listOf(
                    "1. Mermaid Flow Diagram (.mmd)",
                    "2. Graphviz DOT Graph    (.dot)",
                    "3. JSON Node & Link Graph (.json)"
                )
                for (r in 0 until contRows) {
                    w(at(contTop + r, CONT_COL))
                    w(CLR_EOL)
                }
                w(at(contTop + 1, CONT_COL))
                w("$C_ACCENT2$BOLD  ◈ Select Export Format$RESET")
                w(at(contTop + 2, CONT_COL))
                w("$C_MUTED  ────────────────────────────────────────────$RESET")
                for ((idx, opt) in subOptions.withIndex()) {
                    w(at(contTop + 4 + idx * 2, CONT_COL))
                    if (idx == exportSel) {
                        w("  $BG_SEL $BOLD$C_ACCENT2 ▶  $opt $RESET")
                    } else {
                        w("     $C_BRIGHT$opt$RESET")
                    }
                }
                w(at(contTop + 12, CONT_COL))
                w("${DIM}Use arrow keys or [1-3] to select format · ${C_BRIGHT}Esc${RESET}${DIM} to cancel$RESET")
            }

            TuiScreen.Input -> {
                val boxW = (contW - 4).coerceAtLeast(10)
                for (r in 0 until contRows) {
                    w(at(contTop + r, CONT_COL))
                    w(CLR_EOL)
                }
                w(at(contTop + 2, CONT_COL))
                w("$C_ACCENT2$BOLD> $inPmt$RESET")
                w(at(contTop + 4, CONT_COL))
                w("$C_MUTED╭${"─".repeat(boxW)}╮$RESET")
                w(at(contTop + 5, CONT_COL))
                val cursor = "$C_ACCENT▌$RESET"
                val fill = " ".repeat((boxW - inBuf.length - 1).coerceAtLeast(0))
                w("$C_MUTED│$RESET $C_BRIGHT$inBuf$cursor$fill$C_MUTED│$RESET")
                w(at(contTop + 6, CONT_COL))
                w("$C_MUTED╰${"─".repeat(boxW)}╯$RESET")
                w(at(contTop + 8, CONT_COL))
                w("${DIM}Enter$RESET$DIM — confirm   $RESET${DIM}Esc$RESET$DIM — cancel$RESET")
            }

            TuiScreen.Content -> {
                for (r in 0 until contRows) {
                    w(at(contTop + r, CONT_COL))
                    w(CLR_EOL)
                    if (r < lines.size) w(lines[r])
                }
                w(at(contTop + contRows, CONT_COL))
                w("${DIM}Enter$RESET$DIM/$RESET${DIM}Esc$RESET$DIM — back$RESET")
            }
        }

        // Footer
        w(at(h - 1, 1))
        w("$C_MUTED${"─".repeat(w)}$RESET")
        w(at(h, 1))
        w(BG_TITLE)
        w("$C_MUTED [↑↓]$RESET$BG_TITLE Navigate  ")
        w("$C_MUTED [↵]$RESET$BG_TITLE Select  ")
        w("$C_MUTED [1-6]$RESET$BG_TITLE Quick jump  ")
        w("$C_MUTED [Esc]$RESET$BG_TITLE Back  ")
        w("$C_MUTED [q]$RESET$BG_TITLE Quit")
        w(" ".repeat((w - 60).coerceAtLeast(0)))
        w(RESET)

        flush()
    }

    private fun buildWelcome(): List<String> {
        return try {
            val s = storage.getRepositoryStats()
            listOf(
                "",
                "$C_ACCENT2$BOLD  Repository Overview$RESET",
                "",
                "  $C_MUTED Path   $RESET $C_BRIGHT${rootDir.absolutePath}$RESET",
                "  $C_MUTED Index  $RESET $C_SUCCESS${rootDir.path}/.kindex/index.db  ok$RESET",
                "",
                "  $C_MUTED Files      $RESET $BOLD$C_CYAN${s.fileCount}$RESET",
                "  $C_MUTED Symbols    $RESET $BOLD$C_CYAN${s.symbolCount}$RESET",
                "  $C_MUTED Packages   $RESET $BOLD$C_CYAN${s.packageCount}$RESET",
                "  $C_MUTED Classes    $RESET $BOLD$C_CYAN${s.classCount}$RESET",
                "  $C_MUTED Functions  $RESET $BOLD$C_CYAN${s.functionCount}$RESET",
                "  $C_MUTED Edges      $RESET $BOLD$C_CYAN${s.edgeCount}$RESET",
                "",
                "  ${DIM}Use arrow keys or [1-6] to navigate.$RESET",
            )
        } catch (_: Exception) {
            listOf("  $C_WARN  Could not load stats$RESET")
        }
    }

    private fun doScan(): List<String> {
        return try {
            val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
            val extractors = listOf(
                dev.ajithgoveas.kindex.parser.extractors.KotlinJavaExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.RustExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.CExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.CppExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.CSharpExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.JavaScriptExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.GoExtractor(),
                dev.ajithgoveas.kindex.parser.extractors.CssExtractor()
            )
            val walkedFiles = walkFiles(rootDir).filter { file -> extractors.any { it.supports(file) } }
            val existingFiles = storage.getFilesMetadata()

            val filesToScan = mutableListOf<MPFile>()
            val unchangedCount = mutableListOf<String>()

            for (file in walkedFiles) {
                val dbMeta = existingFiles[file.path]
                if (dbMeta != null) {
                    val (dbTime, dbHash) = dbMeta
                    val currentHash = dev.ajithgoveas.kindex.core.io.HashUtils.sha256(file)
                    if (file.lastModified() == dbTime && currentHash == dbHash) {
                        unchangedCount.add(file.path); continue
                    }
                }
                filesToScan.add(file)
            }

            val walkedPathsSet = walkedFiles.map { it.path }.toSet()
            val deletedPaths = existingFiles.keys.filter { it !in walkedPathsSet }

            val parseResults = mutableListOf<dev.ajithgoveas.kindex.core.model.ParseResult>()
            for (file in filesToScan) {
                try {
                    val ext = extractors.first { it.supports(file) }
                    parseResults.add(ext.extract(file))
                } catch (_: Throwable) {}
            }

            val dbSymbols = storage.getAllSymbols()
            val modifiedPathsSet = filesToScan.map { it.path }.toSet()
            val unmodifiedSymbols = dbSymbols.filter { it.filePath !in modifiedPathsSet && it.filePath !in deletedPaths }
            val newSymbols = parseResults.flatMap { it.symbols }
            val allSymbolsMap = (unmodifiedSymbols + newSymbols).associateBy { it.id }

            val resolver = dev.ajithgoveas.kindex.parser.SymbolResolver()
            val fullyResolvedResults = parseResults.map { result ->
                val rawImportStrings = result.edges
                    .filter { it.relation == RelationType.IMPORTS }
                    .map { it.targetId }

                val importResolution = resolver.resolveImportsDetailed(result.sourceFile.path, rawImportStrings, allSymbolsMap)
                val resolvedImportEdges = importResolution.resolved
                val externalEdges = importResolution.unresolved.map {
                    dev.ajithgoveas.kindex.core.model.Edge(result.sourceFile.path, it, RelationType.IMPORTS)
                }

                val staticEdges = result.edges.filter {
                    it.relation == RelationType.CONTAINS || it.relation == RelationType.EXTENDS
                }

                val unresolvedCalls = result.edges
                    .filter { it.relation == RelationType.CALLS && it.targetId.startsWith("REF:") }

                val resolvedCallEdges = unresolvedCalls.flatMap { callEdge ->
                    resolver.resolveCalls(
                        sourceId = callEdge.sourceId,
                        unresolvedCalls = listOf(callEdge.targetId),
                        imports = rawImportStrings,
                        currentPackage = result.sourceFile.packageName,
                        symbolIndex = allSymbolsMap
                    )
                }

                result.copy(edges = staticEdges + resolvedImportEdges + resolvedCallEdges + externalEdges)
            }

            storage.saveResultsIncremental(fullyResolvedResults, deletedPaths)
            val s = storage.getRepositoryStats()

            val graphsWritten = try {
                val syms = storage.getAllSymbols()
                if (syms.isNotEmpty()) {
                    val allEdges = storage.getAllEdges()
                    val base = dbFile.path.removeSuffix("index.db") + "graph"
                    MPFile("$base.mmd").writeText(ModuleGraphAnalyzer.renderMermaid(syms, allEdges))
                    MPFile("$base.dot").writeText(ModuleGraphAnalyzer.renderDot(syms, allEdges))
                    MPFile("$base.json").writeText(ModuleGraphAnalyzer.renderJson(syms, allEdges))
                    true
                } else false
            } catch (_: Exception) {
                false
            }

            listOf(
                "",
                "  $C_SUCCESS$BOLD✓ Interactive scan complete$RESET",
                "",
                "  $C_MUTED Total Candidate Files $RESET $BOLD$C_CYAN${walkedFiles.size}$RESET",
                "  $C_MUTED Modified / New Files  $RESET $BOLD$C_CYAN${filesToScan.size}$RESET",
                "  $C_MUTED Unchanged Files       $RESET $BOLD$C_CYAN${unchangedCount.size}$RESET",
                "  $C_MUTED Pruned Files          $RESET $BOLD$C_CYAN${deletedPaths.size}$RESET",
                "",
                "  $C_MUTED Indexed Symbols       $RESET $BOLD$C_CYAN${s.symbolCount}$RESET",
                "  $C_MUTED Indexed Edges         $RESET $BOLD$C_CYAN${s.edgeCount}$RESET",
                if (graphsWritten) "  $C_MUTED Architecture graphs    $RESET $C_SUCCESS✓${RESET}${C_MUTED}.kindex/graph.{mmd,dot,json}$RESET" else "",
                "",
                "  $C_MUTED Database updated at:$RESET",
                "  $C_BRIGHT  ${dbFile.absolutePath}$RESET"
            )
        } catch (e: Exception) {
            listOf("", "  $C_RED Error during repository scan: ${e.message}$RESET")
        }
    }

    private fun doSearch(query: String): List<String> {
        if (query.isBlank()) return listOf("  $C_MUTED No query entered.$RESET")
        return try {
            val matches = storage.searchSymbols(query)
            if (matches.isEmpty()) {
                listOf("  $C_WARN No symbols found for \"$query\"$RESET")
            } else buildList {
                add(""); add("  $C_ACCENT2$BOLD${matches.size} result(s) for \"$query\"$RESET"); add("")
                add("  $C_MUTED ${"Name".padEnd(28)}  ${"Type".padEnd(12)}  File$RESET")
                add("  $C_MUTED ${"─".repeat(28)}  ${"─".repeat(12)}  ${"─".repeat(28)}$RESET")
                matches.take(20).forEach { s: Symbol ->
                    add("  $C_BRIGHT${s.name.take(28).padEnd(28)}$RESET  $C_CYAN${s.type.name.take(12).padEnd(12)}$RESET  $C_MUTED${s.filePath.substringAfterLast('\\').substringAfterLast('/').take(36)}$RESET")
                }
                if (matches.size > 20) add("  $DIM...and ${matches.size - 20} more$RESET")
            }
        } catch (e: Exception) { listOf("  $C_RED Error: ${e.message}$RESET") }
    }

    private fun doDeps(target: String): List<String> {
        if (target.isBlank()) return listOf("  $C_MUTED No target entered.$RESET")
        return try {
            val inc = storage.getIncomingDependencies(target)
            val out = storage.getOutgoingDependencies(target)
            buildList {
                add(""); add("  $C_ACCENT2$BOLD Dependencies for \"$target\"$RESET"); add("")
                add("  $C_SUCCESS$BOLD Incoming — ${inc.size}$RESET")
                if (inc.isEmpty()) add("  $DIM  none$RESET")
                inc.take(10).forEach { e -> add("  $C_MUTED  <- $C_BRIGHT${e.sourceId.take(55)}$RESET$C_MUTED (${e.relation})$RESET") }
                add("")
                add("  $C_CYAN$BOLD Outgoing — ${out.size}$RESET")
                if (out.isEmpty()) add("  $DIM  none$RESET")
                out.take(10).forEach { e -> add("  $C_MUTED  -> $C_BRIGHT${e.targetId.take(55)}$RESET$C_MUTED (${e.relation})$RESET") }
            }
        } catch (e: Exception) { listOf("  $C_RED Error: ${e.message}$RESET") }
    }

    private fun doStats(): List<String> {
        return try {
            val s = storage.getRepositoryStats()
            listOf(
                "", "  $C_ACCENT2$BOLD Repository Statistics$RESET", "",
                "  $C_MUTED Files         $RESET $BOLD$C_CYAN${s.fileCount}$RESET",
                "  $C_MUTED Symbols       $RESET $BOLD$C_CYAN${s.symbolCount}$RESET",
                "  $C_MUTED Packages      $RESET $BOLD$C_CYAN${s.packageCount}$RESET",
                "  $C_MUTED Classes       $RESET $BOLD$C_CYAN${s.classCount}$RESET",
                "  $C_MUTED Functions     $RESET $BOLD$C_CYAN${s.functionCount}$RESET",
                "  $C_MUTED Edges         $RESET $BOLD$C_CYAN${s.edgeCount}$RESET",
            )
        } catch (e: Exception) { listOf("  $C_RED Error: ${e.message}$RESET") }
    }

    private fun doFlow(): List<String> {
        return try {
            val nodes   = ArchitectureFlowAnalyzer.classifyNodes(storage.getAllSymbols(), storage.getAllEdges())
            val byLayer = nodes.groupBy { it.layer }
            buildList {
                add(""); add("  $C_ACCENT2$BOLD Architectural Layers$RESET"); add("")
                enumValues<ArchitecturalLayer>().forEach { layer ->
                    val cnt = byLayer[layer]?.size ?: 0
                    add("  $C_BRIGHT${layer.displayName.padEnd(30)}$RESET $BOLD$C_CYAN$cnt$RESET")
                }
                val ep = byLayer[ArchitecturalLayer.ENTRY_POINTS]
                if (!ep.isNullOrEmpty()) {
                    add(""); add("  $C_ACCENT2$BOLD Entry Points$RESET"); add("")
                    ep.take(8).forEach { n -> add("  $C_MUTED > $C_BRIGHT${n.name}$RESET") }
                }
            }
        } catch (e: Exception) { listOf("  $C_RED Error: ${e.message}$RESET") }
    }

    private fun doExport(format: String): List<String> {
        return try {
            val symbols = storage.getAllSymbols()
            val edges   = storage.getAllEdges()
            val nodes   = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
            val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges.filter { it.relation != RelationType.CONTAINS })

            val ext = when (format.lowercase()) {
                "dot", "graphviz" -> "dot"
                "json" -> "json"
                else -> "mmd"
            }
            val path = "${rootDir.path}/.kindex/graph.$ext"
            val content = when (ext) {
                "dot" -> ModuleGraphAnalyzer.renderDot(symbols, edges)
                "json" -> ModuleGraphAnalyzer.renderJson(symbols, edges)
                else -> ModuleGraphAnalyzer.renderMermaid(symbols, edges)
            }

            MPFile(path).writeText(content)
            listOf(
                "",
                "  $C_SUCCESS$BOLD✓ Export complete (${format.uppercase()})$RESET",
                "",
                "  $C_MUTED Written to:$RESET",
                "  $C_BRIGHT  $path$RESET",
                "",
                "  $C_MUTED Nodes: $RESET$BOLD$C_CYAN${nodes.size}$RESET  $C_MUTED Edges: $RESET$BOLD$C_CYAN${fileEdges.size}$RESET"
            )
        } catch (e: Exception) { listOf("  $C_RED Error: ${e.message}$RESET") }
    }

    private fun doDeadCode(): List<String> {
        return try {
            val symbols = storage.getAllSymbols()
            val edges   = storage.getAllEdges()
            val targets = (edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId } +
                           edges.filter { it.relation == RelationType.CALLS }.map { it.targetId }).toSet()
            val dead = symbols.filter { s: Symbol ->
                (s.type == SymbolType.CLASS || s.type == SymbolType.INTERFACE) &&
                    s.id !in targets && !s.name.contains("Main") && !s.name.endsWith("Command")
            }
            if (dead.isEmpty()) listOf("", "  $C_SUCCESS$BOLD No dead code candidates found.$RESET")
            else buildList {
                add(""); add("  $C_ACCENT2$BOLD Dead Code Candidates — ${dead.size}$RESET"); add("")
                add("  $C_MUTED ${"Name".padEnd(28)}  ${"Type".padEnd(10)}  File$RESET")
                add("  $C_MUTED ${"─".repeat(28)}  ${"─".repeat(10)}  ${"─".repeat(26)}$RESET")
                dead.take(18).forEach { s: Symbol ->
                    add("  $C_RED${s.name.take(28).padEnd(28)}$RESET  $C_MUTED${s.type.name.take(10).padEnd(10)}$RESET  $C_MUTED${s.filePath.substringAfterLast('\\').substringAfterLast('/').take(34)}$RESET")
                }
                if (dead.size > 18) add("  $DIM...and ${dead.size - 18} more$RESET")
            }
        } catch (e: Exception) { listOf("  $C_RED Error: ${e.message}$RESET") }
    }
}
