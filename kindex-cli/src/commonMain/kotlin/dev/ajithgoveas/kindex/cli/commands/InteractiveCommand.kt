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
import dev.ajithgoveas.kindex.storage.RepositoryStats

// ─── Application model ───────────────────────────────────────────────────────

private data class MenuItem(val key: Char, val icon: String, val label: String, val desc: String)

private val MENU = listOf(
    MenuItem('D', "◉", "Dashboard", "Repository overview & module summary"),
    MenuItem('S', "⌕", "Search Symbols", "Fuzzy full-text symbol search"),
    MenuItem('E', "⇄", "Dependencies", "Incoming / outgoing references"),
    MenuItem('T', "≡", "Repository Stats", "Structural metrics & breakdowns"),
    MenuItem('F', "⌬", "Architecture Flow", "Architectural layer classification"),
    MenuItem('M', "▦", "Module Map", "Cross-module dependency graph"),
    MenuItem('X', "⇩", "Export", "Mermaid / DOT / JSON diagrams"),
    MenuItem('C', "✖", "Dead Code", "Unreferenced class & interface candidates"),
    MenuItem('L', "▤", "Files", "Browse every indexed source file"),
    MenuItem('B', "◈", "Symbol Browser", "Browse symbols with type filters"),
    MenuItem('R', "⟳", "Scan Now", "Re-index changed files in place"),
    MenuItem('A', "ℹ", "About", "Version, keys & security model"),
    MenuItem('Q', "✕", "Quit", "Exit KIndex")
)

private val GRANS = listOf("flow", "file", "package", "symbol")
private val FMTS = listOf("mermaid", "dot", "json")

private const val MENU_W = 28

private sealed class State {
    object Home : State()
    class Content(val model: ContentModel, val sel: Int = 0, val scroll: Int = 0) : State()
    class Input(val prompt: String, val onCommit: (String) -> Unit) : State()
    class Export(val gran: Int, val fmt: Int, val msg: String?) : State()
    class Confirm(val prompt: String, val selectedYes: Boolean = true, val onYes: () -> Unit) : State()
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

    private var statsCache: RepositoryStats? = null
    private var homeLinesCache: List<String> = emptyList()
    private var covered: BooleanArray = BooleanArray(0)
    private var lastSize = 0 to 0
    private var firstRender = true

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
        statsCache = runCatching { storage.getRepositoryStats() }.getOrNull()
        homeLinesCache = homeLines()
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
            is KeyEvent.Character -> {
                val c = k.c.uppercaseChar()
                val idx = MENU.indexOfFirst { it.key == c }
                if (idx >= 0) {
                    menuSel = idx
                    activate(idx)
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
            10 -> {
                state = State.Content(ContentModel.text("Scan", listOf("", "  ${Term.ACCENT2}⟳ Scanning repository, please wait...${Term.RESET}")), 0, 0)
                render()
                open(screens.scan())
            }
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
            KeyEvent.Left, KeyEvent.Right, KeyEvent.Tab -> {
                state = State.Confirm(s.prompt, !s.selectedYes, s.onYes)
            }
            KeyEvent.Enter -> {
                if (s.selectedYes) s.onYes() else state = State.Home
            }
            KeyEvent.Escape -> state = State.Home
            is KeyEvent.Character -> when (k.c.lowercaseChar()) {
                'y' -> s.onYes()
                'n', 'q' -> state = State.Home
            }
            else -> {}
        }
    }

    private fun confirmQuit() {
        state = State.Confirm("Quit KIndex?", true) { quitFlag = true }
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

    private fun contentViewRows(): Int = (termH - 9).coerceAtLeast(3)

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
        if (covered.size != h + 1) {
            covered = BooleanArray(h + 1)
        } else {
            covered.fill(false)
        }
        if (firstRender || lastSize != (w to h)) {
            t.w(t.CLEAR)
            firstRender = false
            lastSize = w to h
        } else {
            t.w("\u001B[H")
        }
        drawFrame(w, h)
        drawHeader(w)

        val dividerY1 = 5
        val dividerY2 = h - 3
        val bodyTop = 6
        val bodyBot = h - 4

        // Draw top body divider
        t.w(t.at(dividerY1, 1))
        t.w("╠" + "═".repeat(MENU_W) + "╬" + "═".repeat(w - MENU_W - 3) + "╣")

        // Draw bottom body divider
        t.w(t.at(dividerY2, 1))
        t.w("╠" + "═".repeat(MENU_W) + "╩" + "═".repeat(w - MENU_W - 3) + "╣")

        drawMenu(bodyTop, bodyBot, w)
        drawContent(bodyTop, bodyBot, w)
        for (y in bodyTop..bodyBot) if (!covered[y]) contentRow(y, "", w)
        drawFooter(w, h)
        t.flush()
    }

    /** Emit a full-width bordered row (used for header, footer regions). */
    private fun putLine(y: Int, s: String, w: Int, fill: String = "") {
        t.w(t.at(y, 1))
        t.w("║" + Term.sel(s, w - 2, fill) + "║")
    }

    /** Emit a menu-pane row: left border, menu text, divider at the pane split, right border. */
    private fun menuRow(y: Int, s: String, w: Int, fill: String = "", divider: String = "║") {
        val mw = MENU_W - 2
        t.w(t.at(y, 1)); t.w("║")
        t.w(Term.sel(s, mw, fill))
        t.w(t.at(y, MENU_W + 2)); t.w(divider)
        t.w(t.at(y, w)); t.w("║")
    }

    /** Emit a content-region row: left border, menu divider, content text, right border. */
    private fun contentRow(y: Int, s: String, w: Int, fill: String = "", marker: Boolean = false) {
        if (y in covered.indices) covered[y] = true
        val cw = w - MENU_W - 3
        val divider = if (marker) "${t.ACCENT2}${t.BOLD}║${t.RESET}" else "║"
        t.w(t.at(y, 1)); t.w("║")
        t.w(t.at(y, MENU_W + 2)); t.w(divider)
        t.w(t.at(y, MENU_W + 3))
        if (marker) {
            val mw = (cw - 2).coerceAtLeast(1)
            t.w(Term.sel("${t.ACCENT2}${t.BOLD}▶${t.RESET} $s", mw, fill))
        } else {
            t.w(Term.sel(s, cw, fill))
        }
        t.w(t.at(y, w)); t.w("║")
    }

    /** Emit a content-region title row with a filled chip and right-aligned tag. */
    private fun titleRow(y: Int, title: String, w: Int, tag: String = "") {
        if (y in covered.indices) covered[y] = true
        val cw = w - MENU_W - 3
        val left = " ${t.MUTED}╠═${t.RESET} ${t.ACCENT2}${t.BOLD}$title${t.RESET} ${t.MUTED}═"
        val right = if (tag.isNotEmpty()) "═ ${t.MUTED}$tag${t.RESET} ${t.MUTED}═╣${t.RESET} " else "${t.MUTED}═╣${t.RESET} "
        val padN = cw - t.visibleLength(left) - t.visibleLength(right)
        t.w(t.at(y, 1)); t.w("║")
        t.w(t.at(y, MENU_W + 2)); t.w("║")
        t.w(t.at(y, MENU_W + 3))
        t.w(t.MUTED + left + "═".repeat(padN.coerceAtLeast(0)) + right + t.RESET)
        t.w(t.at(y, w)); t.w("║")
    }

    /** A small keycap chip used in hint bars and dialogs. */
    private fun keycap(k: String) = "${t.BG_ROW}${t.BRIGHT}${t.BOLD}$k${t.RESET}"

    /** Vertical scrollbar drawn in the rightmost content column when a pane overflows. */
    private fun drawScrollbar(bodyTop: Int, viewH: Int, total: Int, scroll: Int, w: Int) {
        if (total <= viewH) return
        val col = w - 1
        val thumbH = (viewH * viewH / total).coerceIn(1, viewH)
        val maxScroll = total - viewH
        val thumbPos = if (maxScroll <= 0) 0 else (scroll * (viewH - thumbH) / maxScroll).coerceIn(0, viewH - thumbH)
        for (i in 0 until viewH) {
            val inThumb = i >= thumbPos && i < thumbPos + thumbH
            t.w(t.at(bodyTop + i, col))
            t.w(if (inThumb) "${t.BG_ROW}${t.ACCENT2}▐${t.RESET}" else "${t.MUTED}▐${t.RESET}")
        }
    }

    private fun stateTag(): String = when (state) {
        State.Home -> "MENU"
        is State.Content -> "BROWSE"
        is State.Input -> "INPUT"
        is State.Export -> "EXPORT"
        is State.Confirm -> "CONFIRM"
    }

    private fun drawFrame(w: Int, h: Int) {
        t.w(t.at(1, 1)); t.w("╔" + "═".repeat(w - 2) + "╗")
        t.w(t.at(h, 1)); t.w("╚" + "═".repeat(w - 3) + "╝") // Omit last column cell to prevent scroll-up flicker
    }

    private fun drawHeader(w: Int) {
        val repoName = rootDir.name.ifEmpty { rootDir.path.substringAfterLast('/').substringAfterLast('\\') }

        // Neon Cyber Gradient
        val c1 = Triple(0, 229, 255)   // Electric Cyan
        val c2 = Triple(99, 102, 241)  // Indigo
        val c3 = Triple(236, 72, 153)  // Hot Pink

        // Totally new sleek, modern half-block geometric logo (Exactly 28 chars wide)
        val raw1 = " █▄▀  █  █▄ █  █▀▄  █▀▀  ▀▄▀"
        val raw2 = " █▀▄  █  █ ▀█  █ █  █▀▀   █ "
        val raw3 = " ▀ ▀  ▀  ▀  ▀  ▀▀   ▀▀▀  ▀ ▀"

        val logo1 = Term.gradient(raw1, c1, c3)
        val logo2 = Term.gradient(raw2, c1, c3)
        val logo3 = Term.gradient(raw3, c1, c3)

        // cw = exactly the body content width (same formula as contentRow)
        val cw = w - MENU_W - 3

        // Row 2: Logo Row 1 & Repo Path
        val r2Right = " ${t.MUTED}path ${t.RESET}${t.BRIGHT}${rootDir.absolutePath}${t.RESET}"
        t.w(t.at(2, 1))
        t.w("║" + logo1 + "║" + Term.sel(r2Right, cw, t.BG_TITLE) + "║")

        // Row 3: Logo Row 2 & Stats
        val stats = statsCache
        val r3Right = if (stats != null) {
            "  ${t.MUTED}files ${t.RESET}${t.CYAN}${stats.fileCount}${t.RESET}  " +
                    "${t.MUTED}symbols ${t.RESET}${t.CYAN}${stats.symbolCount}${t.RESET}  " +
                    "${t.MUTED}edges ${t.RESET}${t.CYAN}${stats.edgeCount}${t.RESET}"
        } else {
            "  ${t.MUTED}no index — run ${t.RESET}${t.ACCENT}kindex scan${t.RESET}"
        }
        t.w(t.at(3, 1))
        t.w("║" + logo2 + "║" + Term.sel(r3Right, cw, t.BG_TITLE) + "║")

        // Row 4: Logo Row 3 & Mode + repo name
        val mode = stateTag()
        val r4Right = " ${keycap(" $mode ")}  ${t.MUTED}$repoName  ·  alt-buffer  ·  raw keys${t.RESET}"
        t.w(t.at(4, 1))
        t.w("║" + logo3 + "║" + Term.sel(r4Right, cw, t.BG_TITLE) + "║")
    }

    private fun drawMenu(y0: Int, y1: Int, w: Int) {
        // Background fill
        for (yy in y0..y1) menuRow(yy, "", w, t.BG_ROW)

        // Header: app name + version chip (Updated to match electric gradient)
        val hdr = " ${Term.gradient("⎈ KIndex", Triple(0, 229, 255), Triple(236, 72, 153))}  ${t.MUTED}v1.0${t.RESET}"
        menuRow(y0, hdr, w, t.BG_ROW)

        // Thin separator
        val sep = " ${t.MUTED}${"─".repeat(MENU_W - 3)}${t.RESET}"
        menuRow(y0 + 1, sep, w, t.BG_ROW)

        var y = y0 + 2
        MENU.forEachIndexed { i, item ->
            if (y > y1 - 2) return@forEachIndexed
            val active = state is State.Home && i == menuSel
            if (active) {
                // Active item: full-highlight row with left accent bar via cursor movement
                val keyPart   = "${t.ACCENT2}${t.BOLD}${item.key}${t.RESET}"
                val iconPart  = "${t.ACCENT2}${item.icon}${t.RESET}"
                val labelPart = "${t.BRIGHT}${t.BOLD}${item.label}${t.RESET}"
                val rowStr    = "${t.ACCENT2}▌${t.RESET} ${t.MUTED}[${keyPart}${t.MUTED}]${t.RESET} $iconPart $labelPart"
                menuRow(y, rowStr, w, t.BG_SEL_HOT, "${t.ACCENT2}${t.BOLD}┃${t.RESET}")
            } else {
                val keyPart   = "${t.CYAN}${item.key}${t.RESET}"
                val iconPart  = "${t.MUTED}${item.icon}${t.RESET}"
                val labelPart = "${t.BRIGHT}${item.label}${t.RESET}"
                val rowStr    = "  ${t.MUTED}[${keyPart}${t.MUTED}]${t.RESET} $iconPart $labelPart"
                menuRow(y, rowStr, w, t.BG_ROW, "${t.MUTED}│${t.RESET}")
            }
            y++
        }

        // Bottom description strip for active item
        if (y1 - 1 >= y0 + 2) {
            val desc = MENU[menuSel].desc
            val descLine = " ${t.DIM}▸ $desc${t.RESET}"
            menuRow(y1 - 1, descLine, w, t.BG_ROW)
        }
    }

    private fun drawContent(y0: Int, y1: Int, w: Int) {
        val viewRows = y1 - y0 + 1
        when (val s = state) {
            is State.Home -> {
                val lines = homeLinesCache
                titleRow(y0, "Welcome", w)
                contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((w - MENU_W - 5).coerceAtLeast(0))}${t.RESET}", w)
                var i = 0
                while (i < viewRows - 2 && i < lines.size) {
                    contentRow(y0 + 2 + i, lines[i], w)
                    i++
                }
            }

            is State.Content -> {
                val model = s.model
                val tag = if (model.selectable.isNotEmpty()) "${model.selectable.size} items" else ""
                titleRow(y0, model.title, w, tag)
                contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((w - MENU_W - 5).coerceAtLeast(0))}${t.RESET}", w)
                val bodyTop = y0 + 2
                val bodyRows = viewRows - 2
                val selRow = model.selectable.getOrNull(s.sel)
                var i = 0
                while (i < bodyRows) {
                    val idx = s.scroll + i
                    if (idx >= model.rows.size) break
                    val selected = idx == selRow
                    contentRow(bodyTop + i, model.rows[idx], w, if (selected) t.BG_SEL else "", marker = selected)
                    i++
                }
                drawScrollbar(bodyTop, bodyRows, model.rows.size, s.scroll, w)
            }

            is State.Input -> drawInput(y0, y1, w, s)
            is State.Export -> drawExport(y0, y1, w, s)
            is State.Confirm -> drawConfirm(y0, y1, w, s)
        }
    }

    private fun drawInput(y0: Int, y1: Int, w: Int, s: State.Input) {
        val cw = w - MENU_W - 3
        titleRow(y0, s.prompt, w, "INPUT")
        contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((cw - 4).coerceAtLeast(0))}${t.RESET}", w)
        val boxY = y0 + 3
        val boxW = (cw - 6).coerceAtLeast(16)
        contentRow(boxY, " ${t.MUTED}╭${"─".repeat(boxW)}╮${t.RESET}", w)
        val visibleInput = if (inputBuf.length > boxW - 4) {
            "…" + inputBuf.takeLast(boxW - 5)
        } else {
            inputBuf
        }
        contentRow(boxY + 1, " ${t.MUTED}│${t.RESET} ${t.ACCENT2}${t.BOLD}❯${t.RESET} ${t.BRIGHT}${t.fit(visibleInput, boxW - 4)}${t.ACCENT}▌${t.RESET}", w)
        contentRow(boxY + 2, " ${t.MUTED}╰${"─".repeat(boxW)}╯${t.RESET}", w)
        contentRow(boxY + 4, " ${t.DIM}Type to search · Enter confirms · Esc cancels${t.RESET}", w)
    }

    private fun drawExport(y0: Int, y1: Int, w: Int, s: State.Export) {
        val cw = w - MENU_W - 3
        titleRow(y0, "Export Diagrams", w, "EXPORT")
        contentRow(y0 + 1, " ${t.MUTED}${"─".repeat((cw - 4).coerceAtLeast(0))}${t.RESET}", w)
        contentRow(y0 + 3, " ${t.BRIGHT}${t.BOLD}Granularity${t.RESET}  ${t.DIM}← → or g to change${t.RESET}", w)
        var line = "  "
        GRANS.forEachIndexed { i, g ->
            line += if (i == s.gran) "${t.BG_SEL} ● ${t.ACCENT2}${t.BOLD}$g${t.RESET}${t.BG_SEL} ${t.RESET} " else " ${t.MUTED}$g${t.RESET} "
        }
        contentRow(y0 + 4, line, w)
        contentRow(y0 + 6, " ${t.BRIGHT}${t.BOLD}Format${t.RESET}  ${t.DIM}m / d / j${t.RESET}", w)
        line = "  "
        FMTS.forEachIndexed { i, f ->
            line += if (i == s.fmt) "${t.BG_SEL} ● ${t.ACCENT2}${t.BOLD}$f${t.RESET}${t.BG_SEL} ${t.RESET} " else " ${t.MUTED}$f${t.RESET} "
        }
        contentRow(y0 + 7, line, w)
        val ext = if (FMTS[s.fmt] == "dot") "dot" else if (FMTS[s.fmt] == "json") "json" else "mmd"
        contentRow(y0 + 9, " ${t.MUTED}Output${t.RESET}  ${t.BRIGHT}.kindex/graph.$ext${t.RESET}", w)
        s.msg?.let { contentRow(y0 + 11, " $it", w) }
    }

    private fun drawConfirm(y0: Int, y1: Int, w: Int, s: State.Confirm) {
        val cw = w - MENU_W - 3
        val bw = 42
        val bx = MENU_W + 3 + ((cw - bw) / 2).coerceAtLeast(0)
        val by = y0 + (y1 - y0) / 2 - 3

        val bgVal = Term.bg(240, 240, 240)
        val fgVal = Term.fg(15, 23, 42)
        val theme = bgVal + fgVal

        // Pre-clear all lines covered by the dialog and shadow
        for (row in by..by + 7) {
            if (row in covered.indices) covered[row] = true
            t.w(t.at(row, MENU_W + 3)); t.w(" ".repeat(cw))
        }

        // Helper to output a styled line of the dialog
        fun putDialogRow(y: Int, content: String) {
            t.w(t.at(y, bx))
            t.w(theme + content + Term.RESET)
            // Draw right shadow (2 spaces wide)
            t.w(t.at(y, bx + bw))
            t.w(Term.bg(15, 23, 42) + "  " + Term.RESET)
        }

        // Top Border with Title centered
        val title = " Quit KIndex "
        val padVal = (bw - 2 - title.length) / 2
        val topBorder = "╔" + "═".repeat(padVal) + title + "═".repeat(bw - 2 - padVal - title.length) + "╗"
        putDialogRow(by, topBorder)

        // Row 1: Empty
        putDialogRow(by + 1, "║" + " ".repeat(bw - 2) + "║")

        // Row 2: Prompt
        val textLen = Term.visibleLength(s.prompt)
        val padPrompt = (bw - 2 - textLen).coerceAtLeast(0)
        val leftPadPrompt = padPrompt / 2
        val rightPadPrompt = padPrompt - leftPadPrompt
        val promptRow = "║" + " ".repeat(leftPadPrompt) + "${Term.BOLD}${s.prompt}${Term.RESET}${theme}" + " ".repeat(rightPadPrompt) + "║"
        putDialogRow(by + 2, promptRow)

        // Row 3: Empty
        putDialogRow(by + 3, "║" + " ".repeat(bw - 2) + "║")

        // Row 4: Buttons
        val yesPart = if (s.selectedYes) {
            "[ ${Term.bg(34, 197, 94)}${Term.fg(255, 255, 255)}${Term.BOLD} Yes ${Term.RESET}${theme} ]"
        } else {
            "[ ${Term.bg(200, 200, 200)}${Term.fg(75, 85, 99)} Yes ${Term.RESET}${theme} ]"
        }
        val noPart = if (!s.selectedYes) {
            "[ ${Term.bg(239, 68, 68)}${Term.fg(255, 255, 255)}${Term.BOLD}  No  ${Term.RESET}${theme} ]"
        } else {
            "[ ${Term.bg(200, 200, 200)}${Term.fg(75, 85, 99)}  No  ${Term.RESET}${theme} ]"
        }
        val btnRowContent = "     $yesPart     $noPart     "
        val btnRowLen = Term.visibleLength(btnRowContent)
        val padBtns = (bw - 2 - btnRowLen).coerceAtLeast(0)
        val leftPadBtns = padBtns / 2
        val rightPadBtns = padBtns - leftPadBtns
        val btnRow = "║" + " ".repeat(leftPadBtns) + btnRowContent + " ".repeat(rightPadBtns) + "║"
        putDialogRow(by + 4, btnRow)

        // Row 5: Empty
        putDialogRow(by + 5, "║" + " ".repeat(bw - 2) + "║")

        // Bottom Border
        val botBorder = "╚" + "═".repeat(bw - 2) + "╝"
        putDialogRow(by + 6, botBorder)

        // Row 7: Bottom shadow
        val shadowY = by + 7
        t.w(t.at(shadowY, bx + 2))
        t.w(Term.bg(15, 23, 42) + " ".repeat(bw) + Term.RESET)
    }

    private fun drawFooter(w: Int, h: Int) {
        val status = when (val s = state) {
            is State.Home -> "${t.ACCENT2}${MENU[menuSel].icon}${t.RESET}  ${t.DIM}${MENU[menuSel].desc}${t.RESET}"
            is State.Content -> {
                val base = s.model.status
                val view = contentViewRows() - 2
                val extra = buildString {
                    if (s.model.selectable.isNotEmpty()) append("  ${t.MUTED}·${t.RESET}  ${t.CYAN}${s.sel + 1}/${s.model.selectable.size}${t.RESET}")
                    if (s.model.rows.size > view) append("  ${t.MUTED}·${t.RESET}  ${t.DIM}rows ${s.scroll + 1}–${(s.scroll + view).coerceAtMost(s.model.rows.size)}/${s.model.rows.size}${t.RESET}")
                }
                "${t.DIM}$base${t.RESET}$extra"
            }
            is State.Input  -> "${t.DIM}${s.prompt}${t.RESET}"
            is State.Export -> "${t.DIM}${s.msg ?: "Select granularity & format, then press Enter"}${t.RESET}"
            is State.Confirm -> "${t.DIM}${s.prompt}${t.RESET}"
        }
        putLine(h - 2, " $status", w)

        val hints = when (val s = state) {
            State.Home       -> listOf("↑↓" to "Navigate", "↵" to "Open", "A-Z" to "Jump", "Esc/q" to "Quit")
            is State.Content -> listOf("↑↓" to "Move", "PgDn" to "Page", "↵" to "Open", "Esc" to "Back", "q" to "Quit")
            is State.Input   -> listOf("↵" to "Confirm", "Esc" to "Cancel")
            is State.Export  -> listOf("g" to "Granularity", "m/d/j" to "Format", "↵" to "Export", "Esc" to "Back")
            is State.Confirm -> listOf("←→" to "Select", "↵" to "Confirm", "Esc" to "Cancel")
        }
        val bar = hints.joinToString("   ") { (k, lbl) -> "${keycap(" $k ")} ${t.DIM}$lbl${t.RESET}" }
        putLine(h - 1, " $bar", w, t.BG_TITLE)
    }

    private fun ansiRow(label: String, value: String, width: Int): String {
        val visL = Term.visibleLength(label)
        val visV = Term.visibleLength(value)
        val pad = (width - visL - visV).coerceAtLeast(0)
        return label + " ".repeat(pad) + value
    }

    private fun homeLines(): List<String> {
        return try {
            val s = statsCache ?: storage.getRepositoryStats()
            val w = 36
            val lines = mutableListOf<String>()
            lines.add("")
            lines.add("  ${t.ACCENT2}${t.BOLD}Repository Overview${t.RESET}")
            lines.add("  ${t.MUTED}${"─".repeat(w + 4)}${t.RESET}")
            lines.add("  ${t.MUTED}Path ${t.RESET}  ${t.BRIGHT}${rootDir.name.ifEmpty { "KIndex" }}${t.RESET}")
            lines.add("  ${t.MUTED}Index${t.RESET}  ${t.SUCCESS}${t.BOLD}ok${t.RESET} ${t.DIM}(local)${t.RESET}")
            lines.add("  ${t.MUTED}${"─".repeat(w + 4)}${t.RESET}")
            lines.add(ansiRow("  ${t.MUTED}📁 Files${t.RESET}", "${t.CYAN}${t.BOLD}${s.fileCount}${t.RESET}", w))
            lines.add(ansiRow("  ${t.MUTED}◈ Symbols${t.RESET}", "${t.CYAN}${t.BOLD}${s.symbolCount}${t.RESET}", w))
            lines.add(ansiRow("  ${t.MUTED}📦 Packages${t.RESET}", "${t.CYAN}${t.BOLD}${s.packageCount}${t.RESET}", w))
            lines.add(ansiRow("  ${t.MUTED}🗂 Classes${t.RESET}", "${t.CYAN}${t.BOLD}${s.classCount}${t.RESET}", w))
            lines.add(ansiRow("  ${t.MUTED}⚙ Functions${t.RESET}", "${t.CYAN}${t.BOLD}${s.functionCount}${t.RESET}", w))
            lines.add(ansiRow("  ${t.MUTED}⇄ Edges${t.RESET}", "${t.CYAN}${t.BOLD}${s.edgeCount}${t.RESET}", w))
            lines.add("  ${t.MUTED}${"─".repeat(w + 4)}${t.RESET}")
            lines.add("")
            lines.add("  ${t.DIM}Press a bracketed shortcut key or use arrow keys.${t.RESET}")
            lines
        } catch (_: Exception) {
            listOf("", "  ${t.WARN}Could not load stats.${t.RESET}")
        }
    }

    // ─── Export execution ─────────────────────────────────────────────────────

    private fun runExport(gran: Int, fmt: Int): String {
        return try {
            val symbols = storage.getAllSymbols()
            val edges = storage.getAllEdges()
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