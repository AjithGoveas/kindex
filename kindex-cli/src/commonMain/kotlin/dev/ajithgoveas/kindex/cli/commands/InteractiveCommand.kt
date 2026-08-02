package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
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

// ─── ANSI escape helpers ────────────────────────────────────────────────────

private const val ESC = "\u001B"
private const val RESET = "$ESC[0m"
private const val BOLD = "$ESC[1m"
private const val DIM = "$ESC[2m"
private const val REVERSE = "$ESC[7m"
private const val CLEAR = "$ESC[2J$ESC[H"
private const val ALT_SCREEN_ON  = "$ESC[?1049h"
private const val ALT_SCREEN_OFF = "$ESC[?1049l"
private const val HIDE_CURSOR = "$ESC[?25l"
private const val SHOW_CURSOR = "$ESC[?25h"

private fun fg(r: Int, g: Int, b: Int) = "$ESC[38;2;${r};${g};${b}m"
private fun bg(r: Int, g: Int, b: Int) = "$ESC[48;2;${r};${g};${b}m"
private fun moveTo(row: Int, col: Int) = "$ESC[${row};${col}H"
private fun clearLine() = "$ESC[2K"

// Design palette
private val ACCENT   = fg(139, 92, 246)   // Purple-400
private val ACCENT2  = fg(167, 139, 250)  // Purple-300
private val SUCCESS  = fg(52, 211, 153)   // Emerald-400
private val WARNING  = fg(251, 191, 36)   // Amber-400
private val MUTED    = fg(107, 114, 128)  // Gray-500
private val BRIGHT   = fg(243, 244, 246)  // Gray-100
private val RED      = fg(248, 113, 113)  // Red-400
private val CYAN     = fg(34, 211, 238)   // Cyan-400
private val BG_PANEL = bg(17, 24, 39)     // Gray-900
private val BG_SEL   = bg(55, 48, 163)    // Indigo-800
private val BG_TITLE = bg(31, 41, 55)     // Gray-800

// ─── Menu entries ───────────────────────────────────────────────────────────

private data class MenuItem(val key: String, val label: String, val desc: String, val icon: String)

private val MENU_ITEMS = listOf(
    MenuItem("1", "Search",    "Fuzzy symbol search",             "🔍"),
    MenuItem("2", "Deps",      "Dependency & call graph",         "🔗"),
    MenuItem("3", "Stats",     "Repository metrics",              "📊"),
    MenuItem("4", "Flow",      "Architectural layers",            "🏗️"),
    MenuItem("5", "Export",    "Export Mermaid diagram",          "📤"),
    MenuItem("6", "Dead Code", "Unreferenced code detection",     "💀"),
)

// ─── Screen rendering ────────────────────────────────────────────────────────

private object Screen {
    private val sb = StringBuilder()

    fun begin() { sb.clear() }

    fun write(s: String) { sb.append(s) }

    fun writeln(s: String = "") { sb.append(s).append('\n') }

    fun flush() {
        print(sb.toString())
        sb.clear()
    }

    fun pos(row: Int, col: Int) = write(moveTo(row, col))

    fun box(row: Int, col: Int, h: Int, w: Int, title: String = "") {
        val top = "╭" + "─".repeat(w - 2) + "╮"
        val mid = "│" + " ".repeat(w - 2) + "│"
        val bot = "╰" + "─".repeat(w - 2) + "╯"

        write(moveTo(row, col))
        if (title.isNotEmpty()) {
            val t = " $title "
            val fill = w - 2 - t.length
            val left = fill / 2
            val right = fill - left
            write("${MUTED}╭${"─".repeat(left)}${ACCENT2}${BOLD}$t${RESET}${MUTED}${"─".repeat(right)}╮${RESET}")
        } else {
            write("${MUTED}$top${RESET}")
        }
        for (r in 1 until h - 1) {
            write(moveTo(row + r, col))
            write("${MUTED}$mid${RESET}")
        }
        write(moveTo(row + h - 1, col))
        write("${MUTED}$bot${RESET}")
    }
}

// ─── Full-screen TUI renderer ────────────────────────────────────────────────

private const val MENU_W  = 26
private const val HEADER_H = 4
private const val FOOTER_H = 3

class InteractiveCommand : CliktCommand(
    name = "interactive",
    help = "Start an interactive KIndex explorer session (default mode)"
) {
    private val directoryOpt by option("-d", "--dir", help = "Project directory to analyze").default(".")

    private lateinit var rootDir: MPFile
    private lateinit var storage: IndexStorage
    private var termW = 120
    private var termH = 40
    private val contentW get() = termW - MENU_W - 3

    override fun run() {
        val currentDir = MPFile(".")
        rootDir = RepositoryRootResolver.findRepositoryRoot(currentDir)
        val targetDir = if (directoryOpt == ".") rootDir else MPFile(directoryOpt)

        try {
            RepositoryGuardrail.assertWithinRepository(targetDir, rootDir)
        } catch (e: Exception) {
            println("${RED}${e.message}${RESET}")
            return
        }

        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
        if (!dbFile.exists) {
            println("${WARNING}⚠  No index found at ${dbFile.path}")
            println("   Run: kindex scan .${RESET}")
            return
        }

        storage = IndexStorage(dbFile)
        enableRawMode()

        // Enter alternate screen buffer and hide cursor
        print(ALT_SCREEN_ON)
        print(HIDE_CURSOR)
        print(CLEAR)

        try {
            runTui()
        } finally {
            // Always restore terminal on exit
            print(SHOW_CURSOR)
            print(ALT_SCREEN_OFF)
            disableRawMode()
        }
    }

    // ─── Main TUI loop ─────────────────────────────────────────────────────

    private fun runTui() {
        var selectedIdx = 0
        var screen: TuiScreen = TuiScreen.Menu
        var contentLines: List<String> = emptyList()
        var inputBuffer = ""
        var inputPrompt = ""
        var inputCallback: (String) -> List<String> = { emptyList() }

        while (true) {
            drawFrame(selectedIdx, screen, contentLines, inputBuffer, inputPrompt)

            val key = readKey()

            when (screen) {
                TuiScreen.Menu -> {
                    when (key) {
                        KeyEvent.Up -> selectedIdx = (selectedIdx - 1 + MENU_ITEMS.size) % MENU_ITEMS.size
                        KeyEvent.Down -> selectedIdx = (selectedIdx + 1) % MENU_ITEMS.size
                        KeyEvent.Enter -> {
                            when (selectedIdx) {
                                0 -> { screen = TuiScreen.Input; inputPrompt = "Search symbol"; inputBuffer = ""; inputCallback = ::doSearch }
                                1 -> { screen = TuiScreen.Input; inputPrompt = "Symbol name or ID"; inputBuffer = ""; inputCallback = ::doDeps }
                                2 -> { screen = TuiScreen.Content; contentLines = doStats() }
                                3 -> { screen = TuiScreen.Content; contentLines = doFlow() }
                                4 -> { screen = TuiScreen.Content; contentLines = doExport() }
                                5 -> { screen = TuiScreen.Content; contentLines = doDeadCode() }
                            }
                        }
                        KeyEvent.Escape -> return
                        is KeyEvent.Character -> {
                            if (key.c == 'q') return
                            val n = key.c.digitToIntOrNull()
                            if (n != null && n in 1..MENU_ITEMS.size) {
                                selectedIdx = n - 1
                                when (selectedIdx) {
                                    0 -> { screen = TuiScreen.Input; inputPrompt = "Search symbol"; inputBuffer = ""; inputCallback = ::doSearch }
                                    1 -> { screen = TuiScreen.Input; inputPrompt = "Symbol name or ID"; inputBuffer = ""; inputCallback = ::doDeps }
                                    2 -> { screen = TuiScreen.Content; contentLines = doStats() }
                                    3 -> { screen = TuiScreen.Content; contentLines = doFlow() }
                                    4 -> { screen = TuiScreen.Content; contentLines = doExport() }
                                    5 -> { screen = TuiScreen.Content; contentLines = doDeadCode() }
                                }
                            }
                        }
                        else -> {}
                    }
                }

                TuiScreen.Input -> {
                    when (key) {
                        KeyEvent.Enter -> {
                            contentLines = inputCallback(inputBuffer)
                            screen = TuiScreen.Content
                        }
                        KeyEvent.Escape -> { screen = TuiScreen.Menu; inputBuffer = "" }
                        KeyEvent.Backspace -> if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1)
                        is KeyEvent.Character -> inputBuffer += key.c
                        else -> {}
                    }
                }

                TuiScreen.Content -> {
                    when (key) {
                        KeyEvent.Escape, KeyEvent.Enter -> screen = TuiScreen.Menu
                        is KeyEvent.Character -> if (key.c == 'q') screen = TuiScreen.Menu
                        else -> {}
                    }
                }
            }
        }
    }

    // ─── Frame renderer ────────────────────────────────────────────────────

    private fun drawFrame(
        selectedIdx: Int,
        screen: TuiScreen,
        contentLines: List<String>,
        inputBuffer: String,
        inputPrompt: String
    ) {
        Screen.begin()

        // ── Header ──────────────────────────────────────────────────────────
        Screen.write(moveTo(1, 1))
        Screen.write(BG_TITLE)
        Screen.write(" ".repeat(termW))
        Screen.write(moveTo(1, 1))
        Screen.write("${BOLD}${ACCENT} ◈  KINDEX${RESET}${BG_TITLE}${MUTED}  v1.0.0${RESET}${BG_TITLE}")

        val repoLabel = "  ${MUTED}repo: ${RESET}${BG_TITLE}${BRIGHT}${rootDir.absolutePath.takeLast(termW - 40)}${RESET}${BG_TITLE}"
        Screen.write(repoLabel)

        val quitLabel = "${RED}  [q] Quit  ${RESET}"
        Screen.write(moveTo(1, termW - 12))
        Screen.write("${BG_TITLE}${quitLabel}${RESET}")

        Screen.write(moveTo(2, 1))
        Screen.write("${MUTED}${"─".repeat(termW)}${RESET}")

        // ── Menu sidebar ────────────────────────────────────────────────────
        val menuTop = HEADER_H
        val menuH   = termH - HEADER_H - FOOTER_H

        Screen.write(moveTo(menuTop, 1))
        Screen.write("${MUTED}╭${"─".repeat(MENU_W - 2)}╮${RESET}")

        for (i in MENU_ITEMS.indices) {
            val item = MENU_ITEMS[i]
            val row  = menuTop + 1 + i * 2
            if (row >= menuTop + menuH - 1) break

            Screen.write(moveTo(row, 1))
            if (i == selectedIdx && screen == TuiScreen.Menu) {
                Screen.write("${MUTED}│${RESET}${BG_SEL}${BOLD}${ACCENT2} ${item.icon} ${item.label.padEnd(MENU_W - 7)} ${RESET}${MUTED}│${RESET}")
            } else {
                Screen.write("${MUTED}│${RESET}  ${MUTED}${item.icon}${RESET} ${BRIGHT}${item.label.padEnd(MENU_W - 7)}${RESET}${MUTED} │${RESET}")
            }

            // Desc line
            if (row + 1 < menuTop + menuH - 1) {
                Screen.write(moveTo(row + 1, 1))
                Screen.write("${MUTED}│   ${DIM}${item.desc.take(MENU_W - 5).padEnd(MENU_W - 5)}${RESET}${MUTED}│${RESET}")
            }
        }

        Screen.write(moveTo(menuTop + menuH - 1, 1))
        Screen.write("${MUTED}╰${"─".repeat(MENU_W - 2)}╯${RESET}")

        // ── Content divider ──────────────────────────────────────────────────
        val divCol = MENU_W + 1
        for (row in menuTop until menuTop + menuH) {
            Screen.write(moveTo(row, divCol))
            Screen.write("${MUTED}│${RESET}")
        }

        // ── Content area ─────────────────────────────────────────────────────
        val contentCol  = divCol + 2
        val contentTop  = menuTop
        val contentRows = menuH - 2

        when (screen) {
            TuiScreen.Menu -> {
                // Show welcome / summary in content pane
                val lines = buildWelcome()
                for ((i, line) in lines.take(contentRows).withIndex()) {
                    Screen.write(moveTo(contentTop + 1 + i, contentCol))
                    Screen.write(clearLine())
                    Screen.write(line)
                }
                // Clear remaining lines
                for (i in lines.size until contentRows) {
                    Screen.write(moveTo(contentTop + 1 + i, contentCol))
                    Screen.write(clearLine())
                }
            }

            TuiScreen.Input -> {
                // Clear content area and show input prompt
                for (i in 0 until contentRows) {
                    Screen.write(moveTo(contentTop + 1 + i, contentCol))
                    Screen.write(clearLine())
                }
                Screen.write(moveTo(contentTop + 3, contentCol))
                Screen.write("${ACCENT2}${BOLD}$inputPrompt${RESET}")
                Screen.write(moveTo(contentTop + 5, contentCol))
                Screen.write("${MUTED}╭${"─".repeat(contentW - 4)}╮${RESET}")
                Screen.write(moveTo(contentTop + 6, contentCol))
                Screen.write("${MUTED}│${RESET} ${BRIGHT}${inputBuffer}${ACCENT}▌${RESET}${" ".repeat((contentW - 4 - inputBuffer.length - 1).coerceAtLeast(0))}${MUTED}│${RESET}")
                Screen.write(moveTo(contentTop + 7, contentCol))
                Screen.write("${MUTED}╰${"─".repeat(contentW - 4)}╯${RESET}")
                Screen.write(moveTo(contentTop + 9, contentCol))
                Screen.write("${DIM}Press ${BRIGHT}Enter${RESET}${DIM} to confirm · ${BRIGHT}Esc${RESET}${DIM} to cancel${RESET}")
            }

            TuiScreen.Content -> {
                for ((i, line) in contentLines.take(contentRows).withIndex()) {
                    Screen.write(moveTo(contentTop + 1 + i, contentCol))
                    Screen.write(clearLine())
                    Screen.write(line)
                }
                for (i in contentLines.size until contentRows) {
                    Screen.write(moveTo(contentTop + 1 + i, contentCol))
                    Screen.write(clearLine())
                }
                if (contentLines.isNotEmpty()) {
                    Screen.write(moveTo(contentTop + contentRows, contentCol))
                    Screen.write("${DIM}Press ${BRIGHT}Enter${RESET}${DIM} or ${BRIGHT}Esc${RESET}${DIM} to go back${RESET}")
                }
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        val footerRow = termH - FOOTER_H + 1
        Screen.write(moveTo(footerRow, 1))
        Screen.write("${MUTED}${"─".repeat(termW)}${RESET}")
        Screen.write(moveTo(footerRow + 1, 1))
        Screen.write(
            "${BG_TITLE} ${MUTED}[↑↓]${RESET}${BG_TITLE} Navigate  " +
            "${MUTED}[↵]${RESET}${BG_TITLE} Select  " +
            "${MUTED}[1-6]${RESET}${BG_TITLE} Quick jump  " +
            "${MUTED}[Esc]${RESET}${BG_TITLE} Back  " +
            "${MUTED}[q]${RESET}${BG_TITLE} Quit" +
            " ".repeat((termW - 75).coerceAtLeast(0)) +
            "${RESET}"
        )

        Screen.flush()
    }

    // ─── Welcome pane ──────────────────────────────────────────────────────

    private fun buildWelcome(): List<String> {
        return try {
            val stats = storage.getRepositoryStats()
            listOf(
                "",
                "${ACCENT2}${BOLD}  ◈  Repository Overview${RESET}",
                "",
                "  ${MUTED}Path    ${RESET}${BRIGHT}${rootDir.absolutePath}${RESET}",
                "  ${MUTED}Index   ${RESET}${SUCCESS}${rootDir.path}/.kindex/index.db  ✓${RESET}",
                "",
                "  ${MUTED}┌──────────────────────────────┐${RESET}",
                "  ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.fileCount.toString().padStart(6)}${RESET}  ${MUTED}Files indexed${RESET}         ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.symbolCount.toString().padStart(6)}${RESET}  ${MUTED}Symbols extracted${RESET}     ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.packageCount.toString().padStart(6)}${RESET}  ${MUTED}Packages found${RESET}        ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.classCount.toString().padStart(6)}${RESET}  ${MUTED}Classes & interfaces${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.edgeCount.toString().padStart(6)}${RESET}  ${MUTED}Dependency edges${RESET}      ${MUTED}│${RESET}",
                "  ${MUTED}└──────────────────────────────┘${RESET}",
                "",
                "  ${DIM}Use arrow keys to navigate the menu.${RESET}",
                "  ${DIM}Press Enter to select, q to quit.${RESET}",
            )
        } catch (_: Exception) {
            listOf("  ${WARNING}⚠  Could not load statistics${RESET}")
        }
    }

    // ─── Action handlers ──────────────────────────────────────────────────

    private fun doSearch(query: String): List<String> {
        if (query.isBlank()) return listOf("  ${MUTED}No query entered.${RESET}")
        return try {
            val matches = storage.searchSymbols(query)
            if (matches.isEmpty()) {
                listOf("  ${WARNING}No symbols found matching \"$query\"${RESET}")
            } else {
                buildList {
                    add("")
                    add("  ${ACCENT2}${BOLD}Search results for \"$query\"  —  ${matches.size} match(es)${RESET}")
                    add("")
                    add("  ${MUTED}${"Name".padEnd(30)}  ${"Type".padEnd(14)}  File${RESET}")
                    add("  ${MUTED}${"─".repeat(28)}  ${"─".repeat(12)}  ${"─".repeat(30)}${RESET}")
                    matches.take(25).forEach { s: Symbol ->
                        add("  ${BRIGHT}${s.name.take(28).padEnd(28)}${RESET}  ${CYAN}${s.type.name.take(12).padEnd(12)}${RESET}  ${MUTED}${s.filePath.substringAfterLast('\\').substringAfterLast('/').take(40)}${RESET}")
                    }
                    if (matches.size > 25) add("  ${DIM}… and ${matches.size - 25} more${RESET}")
                }
            }
        } catch (e: Exception) {
            listOf("  ${RED}Error: ${e.message}${RESET}")
        }
    }

    private fun doDeps(target: String): List<String> {
        if (target.isBlank()) return listOf("  ${MUTED}No target entered.${RESET}")
        return try {
            val incoming = storage.getIncomingDependencies(target)
            val outgoing = storage.getOutgoingDependencies(target)
            buildList {
                add("")
                add("  ${ACCENT2}${BOLD}Dependencies for \"$target\"${RESET}")
                add("")
                add("  ${SUCCESS}${BOLD}▸ Incoming (dependents) — ${incoming.size}${RESET}")
                if (incoming.isEmpty()) add("  ${DIM}  none${RESET}")
                incoming.take(12).forEach { e -> add("  ${MUTED}  ← ${BRIGHT}${e.sourceId.take(60)}${RESET}${MUTED}  (${e.relation})${RESET}") }
                add("")
                add("  ${CYAN}${BOLD}▸ Outgoing (calls/imports) — ${outgoing.size}${RESET}")
                if (outgoing.isEmpty()) add("  ${DIM}  none${RESET}")
                outgoing.take(12).forEach { e -> add("  ${MUTED}  → ${BRIGHT}${e.targetId.take(60)}${RESET}${MUTED}  (${e.relation})${RESET}") }
            }
        } catch (e: Exception) {
            listOf("  ${RED}Error: ${e.message}${RESET}")
        }
    }

    private fun doStats(): List<String> {
        return try {
            val stats = storage.getRepositoryStats()
            listOf(
                "",
                "  ${ACCENT2}${BOLD}Repository Structural Statistics${RESET}",
                "",
                "  ${MUTED}┌────────────────────────────────────┬──────────┐${RESET}",
                "  ${MUTED}│${RESET}  ${BRIGHT}Metric${RESET}                              ${MUTED}│${RESET}  ${BRIGHT}Count${RESET}   ${MUTED}│${RESET}",
                "  ${MUTED}├────────────────────────────────────┼──────────┤${RESET}",
                "  ${MUTED}│${RESET}  ${MUTED}Total Files${RESET}                         ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.fileCount.toString().padEnd(6)}${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${MUTED}Total Symbols${RESET}                       ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.symbolCount.toString().padEnd(6)}${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${MUTED}Packages${RESET}                            ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.packageCount.toString().padEnd(6)}${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${MUTED}Classes & Interfaces${RESET}                ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.classCount.toString().padEnd(6)}${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${MUTED}Functions & Methods${RESET}                 ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.functionCount.toString().padEnd(6)}${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}│${RESET}  ${MUTED}Dependency Edges${RESET}                    ${MUTED}│${RESET}  ${CYAN}${BOLD}${stats.edgeCount.toString().padEnd(6)}${RESET}  ${MUTED}│${RESET}",
                "  ${MUTED}└────────────────────────────────────┴──────────┘${RESET}",
            )
        } catch (e: Exception) {
            listOf("  ${RED}Error: ${e.message}${RESET}")
        }
    }

    private fun doFlow(): List<String> {
        return try {
            val symbols = storage.getAllSymbols()
            val edges   = storage.getAllEdges()
            val nodes   = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
            val byLayer = nodes.groupBy { it.layer }

            buildList {
                add("")
                add("  ${ACCENT2}${BOLD}Architectural Layer Analysis${RESET}")
                add("")
                add("  ${MUTED}┌──────────────────────────────────────────────────┬──────┐${RESET}")
                add("  ${MUTED}│${RESET}  ${BRIGHT}Layer${RESET}                                           ${MUTED}│${RESET}  ${BRIGHT}Cnt${RESET} ${MUTED}│${RESET}")
                add("  ${MUTED}├──────────────────────────────────────────────────┼──────┤${RESET}")
                enumValues<ArchitecturalLayer>().forEach { layer ->
                    val count = byLayer[layer]?.size ?: 0
                    add("  ${MUTED}│${RESET}  ${layer.emoji} ${BRIGHT}${layer.displayName.padEnd(46)}${RESET}${MUTED}│${RESET}  ${CYAN}${BOLD}${count.toString().padEnd(4)}${RESET}${MUTED}│${RESET}")
                }
                add("  ${MUTED}└──────────────────────────────────────────────────┴──────┘${RESET}")

                val entryNodes = byLayer[ArchitecturalLayer.ENTRY_POINTS]
                if (!entryNodes.isNullOrEmpty()) {
                    add("")
                    add("  ${ACCENT2}${BOLD}🚀 Entry Points (${entryNodes.size})${RESET}")
                    add("")
                    entryNodes.take(10).forEach { n ->
                        add("  ${MUTED}  ▸ ${BRIGHT}${n.name}${RESET}")
                    }
                }
            }
        } catch (e: Exception) {
            listOf("  ${RED}Error: ${e.message}${RESET}")
        }
    }

    private fun doExport(): List<String> {
        return try {
            val symbols = storage.getAllSymbols()
            val edges   = storage.getAllEdges()
            val nodes   = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
            val byLayer = nodes.groupBy { it.layer }

            val mmdPath = "${rootDir.path}/.kindex/graph.mmd"
            val mmdContent = buildString {
                appendLine("graph TD")
                byLayer.entries.forEach { entry ->
                    val layer = entry.key
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
                            appendLine("        $safeId[\"${node.name}\"]")
                        }
                        appendLine("    end")
                    }
                }
            }

            MPFile(mmdPath).writeText(mmdContent)

            listOf(
                "",
                "  ${SUCCESS}${BOLD}✓ Export complete${RESET}",
                "",
                "  ${MUTED}Mermaid diagram written to:${RESET}",
                "  ${BRIGHT}  $mmdPath${RESET}",
                "",
                "  ${MUTED}Nodes exported: ${CYAN}${BOLD}${nodes.size}${RESET}",
            )
        } catch (e: Exception) {
            listOf("  ${RED}Error: ${e.message}${RESET}")
        }
    }

    private fun doDeadCode(): List<String> {
        return try {
            val symbols = storage.getAllSymbols()
            val edges   = storage.getAllEdges()
            val allTargets = (
                edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId } +
                edges.filter { it.relation == RelationType.CALLS }.map { it.targetId }
            ).toSet()

            val unreferenced = symbols.filter { s: Symbol ->
                (s.type == SymbolType.CLASS || s.type == SymbolType.INTERFACE) &&
                    !allTargets.contains(s.id) &&
                    !s.name.contains("Main") &&
                    !s.name.endsWith("Command")
            }

            if (unreferenced.isEmpty()) {
                listOf("", "  ${SUCCESS}${BOLD}✓ No dead code candidates found.${RESET}")
            } else {
                buildList {
                    add("")
                    add("  ${ACCENT2}${BOLD}💀 Dead Code Candidates — ${unreferenced.size} found${RESET}")
                    add("")
                    add("  ${MUTED}${"Name".padEnd(30)}  ${"Type".padEnd(12)}  File${RESET}")
                    add("  ${MUTED}${"─".repeat(28)}  ${"─".repeat(10)}  ${"─".repeat(30)}${RESET}")
                    unreferenced.take(22).forEach { s: Symbol ->
                        add("  ${RED}${s.name.take(28).padEnd(28)}${RESET}  ${MUTED}${s.type.name.take(10).padEnd(10)}${RESET}  ${MUTED}${s.filePath.substringAfterLast('\\').substringAfterLast('/').take(40)}${RESET}")
                    }
                    if (unreferenced.size > 22) add("  ${DIM}… and ${unreferenced.size - 22} more${RESET}")
                }
            }
        } catch (e: Exception) {
            listOf("  ${RED}Error: ${e.message}${RESET}")
        }
    }
}

private enum class TuiScreen { Menu, Input, Content }
