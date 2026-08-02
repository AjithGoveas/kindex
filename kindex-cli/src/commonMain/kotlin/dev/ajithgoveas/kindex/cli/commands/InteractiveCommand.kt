package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import dev.ajithgoveas.kindex.cli.commands.tui.ContentModel
import dev.ajithgoveas.kindex.cli.commands.tui.Term
import dev.ajithgoveas.kindex.cli.commands.tui.TuiScreens
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ModuleGraphAnalyzer
import dev.ajithgoveas.kindex.core.io.KeyEvent
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.io.RepositoryGuardrail
import dev.ajithgoveas.kindex.core.io.RepositoryRootResolver
import dev.ajithgoveas.kindex.core.io.disableRawMode
import dev.ajithgoveas.kindex.core.io.enableRawMode
import dev.ajithgoveas.kindex.core.io.getTerminalSize
import dev.ajithgoveas.kindex.core.io.readKey
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.storage.IndexStorage

// ─── Application model ───────────────────────────────────────────────────────

private data class MenuItem(val icon: String, val label: String, val desc: String)

private val MENU = listOf(
    MenuItem("◉", "Dashboard", "Repository overview & module summary"),
    MenuItem("⌕", "Search Symbols", "Fuzzy full-text symbol search"),
    MenuItem("⇄", "Dependencies", "Incoming / outgoing references"),
    MenuItem("≡", "Repository Stats", "Structural metrics & breakdowns"),
    MenuItem("⌬", "Architecture Flow", "Architectural layer classification"),
    MenuItem("▦", "Module Map", "Cross-module dependency graph"),
    MenuItem("⇩", "Export", "Mermaid / DOT / JSON diagrams"),
    MenuItem("✖", "Dead Code", "Unreferenced class & interface candidates"),
    MenuItem("▤", "Files", "Browse every indexed source file"),
    MenuItem("◈", "Symbol Browser", "Browse symbols with type filters"),
    MenuItem("⟳", "Scan Now", "Re-index changed files in place"),
    MenuItem("ℹ", "About", "Version, keys & security model"),
    MenuItem("✕", "Quit", "Exit KIndex")
)

private val GRANS = listOf("flow", "file", "package", "symbol")
private val FMTS = listOf("mermaid", "dot", "json")

private const val MENU_W = 28

private sealed class State {
    object Home : State()
    class Content(val model: ContentModel, val sel: Int = 0, val scroll: Int = 0) : State()
    class Input(val prompt: String, val onCommit: (String) -> Unit) : State()
    class Export(val gran: Int, val fmt: Int, val msg: String?) : State()
    class Confirm(val prompt: String, val onYes: () -> Unit) : State()
}

class InteractiveCommand : CliktCommand(
    name = "interactive",
    help = "Start an interactive KIndex explorer session (default mode)"
) {
    private val dirOpt by option("-d", "--dir", help = "Project directory to analyze").default(".")

    private lateinit var rootDir: MPFile
    private lateinit var storage: IndexStorage
    private lateinit var screens: TuiScreens

    private var state: State = State.Home
    private var menuSel = 0
    private var inputBuf = ""
    private var quitFlag = false

    private val termW: Int get() = getTerminalSize().first
    private val termH: Int get() = getTerminalSize().second
    private val t: Term get() = Term

    override fun run() {
        val cwd = MPFile(".")
        rootDir = RepositoryRootResolver.findRepositoryRoot(cwd)
        val target = if (dirOpt == ".") rootDir else MPFile(dirOpt)

        try {
            RepositoryGuardrail.assertWithinRepository(target, rootDir)
        } catch (e: Exception) {
            println("${Term.RED}${e.message}${Term.RESET}"); return
        }

        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
        if (!dbFile.exists) {
            println("${Term.WARN}No index found. Run:  kindex scan .${Term.RESET}"); return
        }

        storage = IndexStorage(dbFile)
        screens = TuiScreens(rootDir, storage)
        enableRawMode()
        print(Term.ALT_ON + Term.HIDE + Term.CLEAR)

        try { loop() } finally { print(Term.SHOW + Term.ALT_OFF); disableRawMode() }
    }

    // ─── State machine ───────────────────────────────────────────────────────

    private fun loop() {
        while (!quitFlag) {
            render()
            val k = readKey()
            if (k == KeyEvent.Eof) break
            when (val s = state) {
                State.Home -> handleHome(k)
                is State.Content -> handleContent(k, s)
                is State.Input -> handleInput(k, s)
                is State.Export -> handleExport(k, s)
                is State.Confirm -> handleConfirm(k, s)
            }
        }
    }

    private fun handleHome(k: KeyEvent) {
        when (k) {
            KeyEvent.Up    -> menuSel = (menuSel - 1 + MENU.size) % MENU.size
            KeyEvent.Down  -> menuSel = (menuSel + 1) % MENU.size
            KeyEvent.Home  -> menuSel = 0
            KeyEvent.End   -> menuSel = MENU.size - 1
            KeyEvent.Enter -> activate(menuSel)
            KeyEvent.Escape -> confirmQuit()
            is KeyEvent.Character -> when {
                k.c == 'q' -> confirmQuit()
                k.c.isDigit() -> {
                    val n = k.c.digitToInt()
                    if (n in 1..MENU.size) { menuSel = n - 1; activate(menuSel) }
                }
            }
            else -> {}
        }
    }

    private fun activate(idx: Int) {
        when (idx) {
            0  -> open(screens.dashboard())
            1  -> beginInput("Search symbols") { screens.search(it) }
            2  -> beginInput("Target symbol name") { screens.deps(it) }
            3  -> open(screens.stats())
            4  -> open(screens.flow())
            5  -> open(screens.moduleMap())
            6  -> state = State.Export(0, 0, null)
            7  -> open(screens.dead())
            8  -> open(screens.files())
            9  -> open(screens.symbolBrowser())
            10 -> open(screens.scan())
            11 -> open(screens.about())
            12 -> confirmQuit()
        }
    }

    private fun handleContent(k: KeyEvent, s: State.Content) {
        val viewRows = contentViewRows()
        when (k) {
            KeyEvent.Up        -> moveSel(s, -1, viewRows)
            KeyEvent.Down      -> moveSel(s, 1, viewRows)
            KeyEvent.Home      -> setSel(s, 0, viewRows)
            KeyEvent.End       -> setSel(s, s.model.selectable.size - 1, viewRows)
            KeyEvent.PageUp    -> moveSel(s, -(viewRows - 2), viewRows)
            KeyEvent.PageDown  -> moveSel(s, viewRows - 2, viewRows)
            KeyEvent.Enter     -> {
                val row = s.model.selectable.getOrNull(s.sel) ?: return
                val next = s.model.onActivate?.invoke(row)
                if (next != null) open(next)
            }
            KeyEvent.Escape    -> state = State.Home
            is KeyEvent.Character -> when {
                k.c == 'q' -> confirmQuit()
                s.model.filters != null && k.c in "cifpa" -> open(s.model.filters(k.c))
            }
            else -> {}
        }
    }

    private fun handleInput(k: KeyEvent, s: State.Input) {
        when (k) {
            KeyEvent.Enter      -> { val q = inputBuf; inputBuf = ""; s.onCommit(q) }
            KeyEvent.Escape     -> { inputBuf = ""; state = State.Home }
            KeyEvent.Backspace  -> if (inputBuf.isNotEmpty()) inputBuf = inputBuf.dropLast(1)
            is KeyEvent.Character -> inputBuf += k.c
            else -> {}
        }
    }

    private fun handleExport(k: KeyEvent, s: State.Export) {
        when (k) {
            KeyEvent.Left, KeyEvent.Up    -> state = State.Export((s.gran + GRANS.size - 1) % GRANS.size, s.fmt, null)
            KeyEvent.Right, KeyEvent.Down -> state = State.Export((s.gran + 1) % GRANS.size, s.fmt, null)
            KeyEvent.Enter                -> state = State.Export(s.gran, s.fmt, runExport(s.gran, s.fmt))
            KeyEvent.Escape               -> state = State.Home
            is KeyEvent.Character -> when (k.c) {
                'g' -> state = State.Export((s.gran + 1) % GRANS.size, s.fmt, null)
                'f' -> state = State.Export(s.gran, (s.fmt + 1) % FMTS.size, null)
                'm' -> state = State.Export(s.gran, 0, null)
                'd' -> state = State.Export(s.gran, 1, null)
                'j' -> state = State.Export(s.gran, 2, null)
                'q' -> confirmQuit()
            }
            else -> {}
        }
    }

    private fun handleConfirm(k: KeyEvent, s: State.Confirm) {
        when (k) {
            KeyEvent.Enter -> s.onYes()
            KeyEvent.Escape -> state = State.Home
            is KeyEvent.Character -> when (k.c) {
                'y', 'Y' -> s.onYes()
                'n', 'N', 'q' -> state = State.Home
            }
            else -> {}
        }
    }

    private fun confirmQuit() {
        state = State.Confirm("Quit KIndex?") { quitFlag = true }
    }

    private fun beginInput(prompt: String, build: (String) -> ContentModel) {
        inputBuf = ""
        state = State.Input(prompt) { q -> open(build(q)) }
    }

    private fun open(model: ContentModel) {
        state = State.Content(model, 0, 0)
    }

    private fun moveSel(s: State.Content, dir: Int, viewRows: Int) {
        val n = s.model.selectable.size
        if (n == 0) return
        val sel = ((s.sel + dir) % n + n) % n
        state = State.Content(s.model, sel, ensureVisible(s.model, sel, s.scroll, viewRows))
    }

    private fun setSel(s: State.Content, sel: Int, viewRows: Int) {
        if (s.model.selectable.isEmpty()) return
        val clamped = sel.coerceIn(0, s.model.selectable.size - 1)
        state = State.Content(s.model, clamped, ensureVisible(s.model, clamped, s.scroll, viewRows))
    }

    private fun ensureVisible(model: ContentModel, sel: Int, scroll: Int, viewRows: Int): Int {
        if (model.selectable.isEmpty()) return 0
        val row = model.selectable[sel.coerceIn(0, model.selectable.size - 1)]
        return when {
            row < scroll -> row
            row >= scroll + viewRows -> row - viewRows + 1
            else -> scroll
        }
    }

    private fun contentViewRows(): Int = (termH - 6).coerceAtLeast(3)

    // ─── Rendering ────────────────────────────────────────────────────────────

    private fun render() {
        val w = termW
        val h = termH
        if (w < 70 || h < 22) {
            t.buf.clear(); t.w(t.CLEAR); t.w(t.at(1, 1))
            t.w("${t.WARN}Terminal too small (${w}x${h}). Resize to at least 70x22.${t.RESET}")
            t.flush(); return
        }
        t.buf.clear()
        t.w(t.CLEAR)
        drawFrame(w, h)
        drawHeader(w)
        val bodyTop = 3
        val bodyBot = h - 2
        drawMenu(bodyTop, bodyBot, w)
        drawContent(bodyTop, bodyBot, w)
        drawFooter(w, h)
        t.flush()
    }

    /** Emit a full-width bordered row (used for menu, header, footer regions). */
    private fun putLine(y: Int, s: String, w: Int, fill: String = "") {
        t.w(t.at(y, 1))
        t.w("│$fill" + t.pad(t.fit(s, w - 2), w - 2) + "${t.RESET}│")
    }

    /** Emit a content-region row: left border, menu divider, content text, right border. */
    private fun contentRow(y: Int, s: String, w: Int, fill: String = "") {
        val cw = w - MENU_W - 3
        t.w(t.at(y, 1)); t.w("│")
        t.w(t.at(y, MENU_W + 2)); t.w("│")
        t.w(t.at(y, MENU_W + 3))
        t.w("$fill" + t.pad(t.fit(s, cw), cw) + "${t.RESET}")
        t.w(t.at(y, w)); t.w("│")
    }

    private fun drawFrame(w: Int, h: Int) {
        t.w(t.at(1, 1)); t.w("╭" + "─".repeat(w - 2) + "╮")
        t.w(t.at(h, 1)); t.w("╰" + "─".repeat(w - 2) + "╯")
    }

    private fun drawHeader(w: Int) {
        val repoName = rootDir.name.ifEmpty { rootDir.path.substringAfterLast('/').substringAfterLast('\\') }
        val left = "${t.ACCENT}${t.BOLD}◆ KINDEX${t.RESET} ${t.MUTED}v1.0.0${t.RESET}"
        val right = "${t.MUTED}$repoName${t.RESET}"
        val padLen = (w - 2 - t.visibleLength(right)).coerceAtLeast(t.visibleLength(left))
        putLine(1, t.pad(t.fit(left, padLen), padLen) + right, w, t.BG_TITLE)

        val stats = runCatching { storage.getRepositoryStats() }.getOrNull()
        val left2 = if (stats != null) {
            "  ${t.MUTED}files ${t.RESET}${t.CYAN}${stats.fileCount}${t.RESET}  ${t.MUTED}symbols ${t.RESET}${t.CYAN}${stats.symbolCount}${t.RESET}  ${t.MUTED}edges ${t.RESET}${t.CYAN}${stats.edgeCount}${t.RESET}"
        } else {
            "  ${t.MUTED}no index stats${t.RESET}"
        }
        val right2 = "${t.MUTED}alt-buffer · raw keys${t.RESET}"
        val pad2 = (w - 2 - t.visibleLength(right2)).coerceAtLeast(t.visibleLength(left2))
        putLine(2, t.pad(t.fit(left2, pad2), pad2) + right2, w, t.BG_TITLE)
    }

    private fun drawMenu(y0: Int, y1: Int, w: Int) {
        putLine(y0, " ${t.MUTED}${t.BOLD}M E N U${t.RESET}", w)
        var y = y0 + 1
        MENU.forEachIndexed { i, item ->
            if (y > y1 - 2) return@forEachIndexed
            val active = state is State.Home && i == menuSel
            val num = (i + 1).toString().padStart(2)
            val numPart = if (active) "${t.ACCENT2}${t.BOLD}▸${t.RESET}" else "${t.MUTED}${t.BOLD}$num${t.RESET}"
            val labelPart = if (active) "${t.BRIGHT}${t.BOLD}${item.label}" else "${t.BRIGHT}${item.label}"
            val rowStr = " $numPart ${item.icon} $labelPart"
            putLine(y, rowStr, w, if (active) t.BG_SEL else "")
            y++
        }
        if (y1 - 1 > y0 + 1) {
            putLine(y1 - 1, " ${t.DIM}${MENU[menuSel].desc}${t.RESET}", w)
        }
    }

    private fun drawContent(y0: Int, y1: Int, w: Int) {
        val viewRows = y1 - y0 + 1
        when (val s = state) {
            is State.Home -> {
                val lines = homeLines()
                contentRow(y0, " ${t.ACCENT2}${t.BOLD}Welcome${t.RESET}", w)
                contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((w - MENU_W - 5).coerceAtLeast(0))}${t.RESET}", w)
                var i = 0
                while (i < viewRows - 2 && i < lines.size) {
                    contentRow(y0 + 2 + i, lines[i], w)
                    i++
                }
            }

            is State.Content -> {
                val model = s.model
                contentRow(y0, " ${t.ACCENT2}${t.BOLD}${model.title}${t.RESET}", w)
                contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((w - MENU_W - 5).coerceAtLeast(0))}${t.RESET}", w)
                val bodyTop = y0 + 2
                val bodyRows = viewRows - 2
                val selRow = model.selectable.getOrNull(s.sel)
                var i = 0
                while (i < bodyRows) {
                    val idx = s.scroll + i
                    if (idx >= model.rows.size) break
                    contentRow(bodyTop + i, model.rows[idx], w, if (idx == selRow) t.BG_SEL else "")
                    i++
                }
            }

            is State.Input -> drawInput(y0, y1, w, s)
            is State.Export -> drawExport(y0, y1, w, s)
            is State.Confirm -> drawConfirm(y0, y1, w, s)
        }
    }

    private fun drawInput(y0: Int, y1: Int, w: Int, s: State.Input) {
        val cw = w - MENU_W - 3
        contentRow(y0, " ${t.ACCENT2}${t.BOLD}${s.prompt}${t.RESET}", w)
        contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((cw - 4).coerceAtLeast(0))}${t.RESET}", w)
        val boxY = y0 + 3
        val boxW = (cw - 6).coerceAtLeast(16)
        contentRow(boxY, " ${t.MUTED}╭${"─".repeat(boxW)}╮${t.RESET}", w)
        contentRow(boxY + 1, " ${t.MUTED}│${t.RESET} ${t.BRIGHT}$inputBuf${t.ACCENT}▌${t.RESET}${" ".repeat((boxW - inputBuf.length - 1).coerceAtLeast(0))}${t.MUTED}│${t.RESET}", w)
        contentRow(boxY + 2, " ${t.MUTED}╰${"─".repeat(boxW)}╯${t.RESET}", w)
        contentRow(boxY + 4, " ${t.DIM}Enter to confirm · Esc to cancel${t.RESET}", w)
    }

    private fun drawExport(y0: Int, y1: Int, w: Int, s: State.Export) {
        val cw = w - MENU_W - 3
        contentRow(y0, " ${t.ACCENT2}${t.BOLD}Export Diagrams${t.RESET}", w)
        contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((cw - 4).coerceAtLeast(0))}${t.RESET}", w)
        contentRow(y0 + 3, " ${t.BRIGHT}${t.BOLD}Granularity${t.RESET}", w)
        var line = "  "
        GRANS.forEachIndexed { i, g ->
            line += if (i == s.gran) " ${t.BG_SEL}${t.BOLD}${t.ACCENT2}$g${t.RESET}${t.BG_SEL} ${t.RESET} " else " ${t.MUTED}$g${t.RESET} "
        }
        contentRow(y0 + 4, line, w)
        contentRow(y0 + 6, " ${t.BRIGHT}${t.BOLD}Format${t.RESET}", w)
        line = "  "
        FMTS.forEachIndexed { i, f ->
            line += if (i == s.fmt) " ${t.BG_SEL}${t.BOLD}${t.ACCENT2}$f${t.RESET}${t.BG_SEL} ${t.RESET} " else " ${t.MUTED}$f${t.RESET} "
        }
        contentRow(y0 + 7, line, w)
        val ext = if (FMTS[s.fmt] == "dot") "dot" else if (FMTS[s.fmt] == "json") "json" else "mmd"
        contentRow(y0 + 9, " ${t.MUTED}Output${t.RESET}  ${t.BRIGHT}.kindex/graph.$ext${t.RESET}", w)
        s.msg?.let { contentRow(y0 + 11, " $it", w) }
    }

    private fun drawConfirm(y0: Int, y1: Int, w: Int, s: State.Confirm) {
        val cw = w - MENU_W - 3
        val bw = (s.prompt.length + 12).coerceIn(24, (cw - 6).coerceAtLeast(24))
        val bx = MENU_W + 3 + ((cw - bw) / 2).coerceAtLeast(0)
        val by = y0 + (y1 - y0) / 2 - 2
        t.w(t.at(by, bx));     t.w("${t.BG_SEL}${" ".repeat(bw)}${t.RESET}")
        t.w(t.at(by + 1, bx)); t.w("${t.BG_SEL} ${t.BRIGHT}${s.prompt.padEnd(bw - 2)} ${t.RESET}")
        t.w(t.at(by + 2, bx)); t.w("${t.BG_SEL} ${t.MUTED}[Y]es   [N]o${t.RESET}${" ".repeat((bw - 12).coerceAtLeast(0))}")
        t.w(t.at(by + 3, bx)); t.w("${t.BG_SEL}${" ".repeat(bw)}${t.RESET}")
    }

    private fun drawFooter(w: Int, h: Int) {
        val status = when (val s = state) {
            is State.Home -> MENU[menuSel].desc
            is State.Content -> s.model.status
            is State.Input -> s.prompt
            is State.Export -> s.msg ?: "Select granularity & format, then press Enter"
            is State.Confirm -> s.prompt
        }
        putLine(h - 1, " ${t.DIM}$status${t.RESET}", w)

        val hints = when (val s = state) {
            State.Home -> listOf("[↑↓] Navigate", "[↵] Open", "[1-9] Jump", "[Esc/q] Quit")
            is State.Content -> listOf("[↑↓] Move", "[PgUp/PgDn] Scroll", "[↵] Open", "[Esc] Back", "[q] Quit")
            is State.Input -> listOf("Type…", "[↵] Confirm", "[Esc] Cancel")
            is State.Export -> listOf("[g] Granularity", "[m][d][j] Format", "[↵] Export", "[Esc] Back")
            is State.Confirm -> listOf("[y] Yes", "[n] No")
        }
        val bar = hints.joinToString("  ") { "${t.MUTED}$it${t.RESET}" }
        putLine(h, bar, w, t.BG_TITLE)
    }

    private fun homeLines(): List<String> {
        return try {
            val s = storage.getRepositoryStats()
            listOf(
                "",
                "  ${t.ACCENT2}${t.BOLD}Repository Overview${t.RESET}",
                "",
                "  ${t.MUTED}Path ${t.RESET} ${t.BRIGHT}${rootDir.absolutePath}${t.RESET}",
                "  ${t.MUTED}Index${t.RESET}  ${t.SUCCESS}${t.BOLD}ok${t.RESET}  ${t.DIM}${rootDir.path}/.kindex/index.db${t.RESET}",
                "",
                "  ${t.MUTED}Files      ${t.RESET} ${t.BOLD}${t.CYAN}${s.fileCount}${t.RESET}",
                "  ${t.MUTED}Symbols    ${t.RESET} ${t.BOLD}${t.CYAN}${s.symbolCount}${t.RESET}",
                "  ${t.MUTED}Packages   ${t.RESET} ${t.BOLD}${t.CYAN}${s.packageCount}${t.RESET}",
                "  ${t.MUTED}Classes    ${t.RESET} ${t.BOLD}${t.CYAN}${s.classCount}${t.RESET}",
                "  ${t.MUTED}Functions  ${t.RESET} ${t.BOLD}${t.CYAN}${s.functionCount}${t.RESET}",
                "  ${t.MUTED}Edges      ${t.RESET} ${t.BOLD}${t.CYAN}${s.edgeCount}${t.RESET}",
                "",
                "  ${t.DIM}Select an action from the menu to begin.${t.RESET}"
            )
        } catch (_: Exception) {
            listOf("", "  ${t.WARN}Could not load stats.${t.RESET}")
        }
    }

    // ─── Export execution ─────────────────────────────────────────────────────

    private fun runExport(gran: Int, fmt: Int): String {
        return try {
            val symbols = storage.getAllSymbols()
            val edges = storage.getAllEdges().filter {
                !it.targetId.contains(" = \"") && !it.targetId.contains("help =") && !it.sourceId.contains(" = \"")
            }
            val g = GRANS[gran]
            val f = FMTS[fmt]
            val content = when (g) {
                "package" -> packageExport(symbols, edges, f)
                "symbol" -> symbolExport(symbols, edges, f)
                else -> when (f) {
                    "dot" -> ModuleGraphAnalyzer.renderDot(symbols, edges)
                    "json" -> ModuleGraphAnalyzer.renderJson(symbols, edges)
                    else -> ModuleGraphAnalyzer.renderMermaid(symbols, edges)
                }
            }
            val ext = if (f == "dot") "dot" else if (f == "json") "json" else "mmd"
            val path = "${rootDir.path}/.kindex/graph.$ext"
            MPFile(path).writeText(content)
            "${t.SUCCESS}✓${t.RESET} ${g.uppercase()} graph (${f.uppercase()}) written to $path"
        } catch (e: Exception) {
            "${t.RED}✗${t.RESET} ${e.message}"
        }
    }

    private fun packageExport(symbols: List<Symbol>, edges: List<Edge>, fmt: String): String {
        val pkgEdges = ArchitectureFlowAnalyzer.aggregateByPackage(edges, symbols)
        return when (fmt) {
            "dot" -> buildString {
                append("digraph PackageWiringGraph {\n    rankdir=LR;\n    node [shape=box, style=\"filled,rounded\", fillcolor=\"#D8F3DC\"];\n")
                pkgEdges.forEach { e ->
                    append("    \"${e.source}\" -> \"${e.target}\" [label=\"${e.relation} (${e.weight})\"];\n")
                }
                append("}\n")
            }
            "json" -> {
                val nodes = pkgEdges.flatMap { listOf(it.source, it.target) }.distinct()
                    .joinToString(",\n") { """    { "id": "$it", "type": "PACKAGE" }""" }
                val links = pkgEdges.joinToString(",\n") {
                    """    { "source": "${it.source}", "target": "${it.target}", "relation": "${it.relation}", "weight": ${it.weight} }"""
                }
                "{\n  \"nodes\": [\n$nodes\n  ],\n  \"links\": [\n$links\n  ]\n}"
            }
            else -> buildString {
                append("graph LR\n")
                pkgEdges.forEach { e ->
                    append("    ${e.source.replace(Regex("[^a-zA-Z0-9_]"), "_")}[\"${e.source}\"] -->|${e.relation} ${e.weight}x| ${e.target.replace(Regex("[^a-zA-Z0-9_]"), "_")}[\"${e.target}\"]\n")
                }
            }
        }
    }

    private fun symbolExport(symbols: List<Symbol>, edges: List<Edge>, fmt: String): String {
        val cleanEdges = edges.filter { it.relation != RelationType.CONTAINS }
        val clean = { s: String -> s.substringBefore("#").replace('\\', '/').substringAfterLast('/').let { if (it.contains(".")) it else "$it.kt" } }
        return when (fmt) {
            "dot" -> buildString {
                append("digraph SymbolWiringGraph {\n    rankdir=LR;\n    node [shape=box];\n")
                cleanEdges.take(60).forEach { e ->
                    append("    \"${clean(e.sourceId)}\" -> \"${clean(e.targetId)}\" [label=\"${e.relation.name}\"];\n")
                }
                append("}\n")
            }
            "json" -> {
                val nodes = symbols.take(100).joinToString(",\n") {
                    """    { "id": "${clean(it.id)}", "name": "${it.name}", "type": "${it.type.name}" }"""
                }
                val links = cleanEdges.take(100).joinToString(",\n") {
                    """    { "source": "${clean(it.sourceId)}", "target": "${clean(it.targetId)}", "relation": "${it.relation.name}" }"""
                }
                "{\n  \"nodes\": [\n$nodes\n  ],\n  \"links\": [\n$links\n  ]\n}"
            }
            else -> buildString {
                append("graph TD\n")
                cleanEdges.take(60).forEach { e ->
                    val src = clean(e.sourceId).replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val tgt = clean(e.targetId).replace(Regex("[^a-zA-Z0-9_]"), "_")
                    append("    $src[\"${clean(e.sourceId)}\"] -->|${e.relation.name}| $tgt[\"${clean(e.targetId)}\"]\n")
                }
            }
        }
    }
}
