package dev.ajithgoveas.kindex.cli.commands.tui

import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
import dev.ajithgoveas.kindex.core.analysis.ModuleGraphAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ModuleGroup
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.storage.IndexStorage

/**
 * Builds the fully-coloured content panes rendered in the right-hand side of the
 * interactive TUI. Each builder returns a [ContentModel] that may expose
 * selectable rows (for drill-down navigation) and/or quick filter keys.
 */
class TuiScreens(private val rootDir: MPFile, private val storage: IndexStorage) {

    private val B = Term.BOLD
    private val D = Term.DIM

    private fun shortName(path: String) = path.replace('\\', '/').substringAfterLast('/')
    private fun langOf(path: String): String = path.substringAfterLast('.').uppercase()

    /** Generic list pane: [items] become selectable rows via [rowFor], Enter opens [detailFor]. */
    private fun <T> listPane(
        title: String,
        header: List<String>,
        items: List<T>,
        rowFor: (T) -> String,
        detailFor: (T) -> ContentModel,
        status: String
    ): ContentModel {
        val rows = mutableListOf<String>()
        val selectable = mutableListOf<Int>()
        val at = mutableMapOf<Int, T>()
        rows.addAll(header)
        items.forEach { itm ->
            val r = rows.size
            rows.add(rowFor(itm))
            selectable.add(r)
            at[r] = itm
        }
        return ContentModel(
            title = title,
            rows = rows,
            selectable = selectable,
            onActivate = { i -> at[i]?.let { detailFor(it) } ?: ContentModel.text(title, rows) },
            status = status
        )
    }

    // ─── Dashboard ────────────────────────────────────────────────────────────

    fun dashboard(): ContentModel {
        val symbols = storage.getAllSymbols()
        val edges = storage.getAllEdges()
        val s = storage.getRepositoryStats()
        val groups = ModuleGraphAnalyzer.analyze(symbols, edges)
        val moduleEdges = ModuleGraphAnalyzer.moduleEdges(symbols, edges)
        val extRefs = ModuleGraphAnalyzer.externalRefs(symbols, edges)

        val rows = mutableListOf<String>()
        val selectable = mutableListOf<Int>()
        val moduleAt = mutableMapOf<Int, ModuleGroup>()

        rows.add(""); rows.add("  ${Term.ACCENT2}$B Repository Overview${Term.RESET}"); rows.add("")
        rows.add("  ${Term.MUTED}Path ${Term.RESET} ${Term.BRIGHT}${rootDir.absolutePath}${Term.RESET}")
        rows.add("  ${Term.MUTED}Index${Term.RESET}  ${Term.SUCCESS}${B}ok${Term.RESET}  ${D}${rootDir.path}/.kindex/index.db${Term.RESET}")
        rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}"); rows.add("")
        rows.add("  ${Term.MUTED}Files      ${Term.RESET} $B${Term.CYAN}${s.fileCount}${Term.RESET}")
        rows.add("  ${Term.MUTED}Symbols    ${Term.RESET} $B${Term.CYAN}${s.symbolCount}${Term.RESET}")
        rows.add("  ${Term.MUTED}Packages   ${Term.RESET} $B${Term.CYAN}${s.packageCount}${Term.RESET}")
        rows.add("  ${Term.MUTED}Classes    ${Term.RESET} $B${Term.CYAN}${s.classCount}${Term.RESET}")
        rows.add("  ${Term.MUTED}Functions  ${Term.RESET} $B${Term.CYAN}${s.functionCount}${Term.RESET}")
        rows.add("  ${Term.MUTED}Edges      ${Term.RESET} $B${Term.CYAN}${s.edgeCount}${Term.RESET}")
        rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}"); rows.add("")
        rows.add("  ${Term.ACCENT2}$B Modules${Term.RESET}  ${D}(${groups.size})${Term.RESET}")
        groups.forEach { g ->
            val r = rows.size
            rows.add("  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}${g.name}${Term.RESET} $D— ${g.files.size} files${Term.RESET}")
            selectable.add(r); moduleAt[r] = g
        }
        rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}"); rows.add("")
        rows.add("  ${Term.MUTED}Cross-module links — ${moduleEdges.size}${Term.RESET}")
        moduleEdges.take(8).forEach { e -> rows.add("  $D${e.source} ──${e.relation} (${e.weight})──▶ ${e.target}${Term.RESET}") }
        rows.add("")
        rows.add("  ${Term.MUTED}External dependencies — ${extRefs.size}${Term.RESET}")
        extRefs.take(5).forEach { e -> rows.add("  $D${e.source} ──▶ ${e.target}${Term.RESET}") }

        return ContentModel(
            title = "Dashboard", rows = rows, selectable = selectable,
            onActivate = { i -> moduleAt[i]?.let { filesInModule(it) } ?: dashboard() },
            status = "${s.symbolCount} symbols · ${s.edgeCount} edges · ${groups.size} modules"
        )
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    fun search(query: String): ContentModel {
        if (query.isBlank()) return ContentModel.text("Search", listOf("  ${Term.WARN}No query entered.${Term.RESET}"))
        return try {
            val matches = storage.searchSymbols(query)
            if (matches.isEmpty()) {
                ContentModel.text("Search — \"$query\"", listOf("  ${Term.WARN}No symbols found for \"$query\".${Term.RESET}"))
            } else {
                val header = listOf(
                    "", "  ${Term.ACCENT2}$B ${matches.size} result(s) for \"$query\"${Term.RESET}", "",
                    "  ${Term.MUTED}${"Name".padEnd(26)} ${"Type".padEnd(10)} ${"File".padEnd(22)} Line${Term.RESET}",
                    "  ${Term.MUTED}${"─".repeat(26)} ${"─".repeat(10)} ${"─".repeat(22)} ───${Term.RESET}"
                )
                listPane(
                    title = "Search — \"$query\"",
                    header = header,
                    items = matches,
                    rowFor = { m ->
                        "  ${Term.BRIGHT}${m.name.take(26).padEnd(26)}${Term.RESET} ${Term.CYAN}${m.type.name.take(10).padEnd(10)}${Term.RESET} ${Term.MUTED}${shortName(m.filePath).take(22).padEnd(22)}${Term.RESET} $D${m.lineNumber}${Term.RESET}"
                    },
                    detailFor = { symbolDetail(it.id) },
                    status = "${matches.size} match(es) · Enter opens a symbol"
                )
            }
        } catch (e: Exception) {
            ContentModel.text("Search", listOf("  ${Term.RED}Error: ${e.message}${Term.RESET}"))
        }
    }

    // ─── Dependencies ─────────────────────────────────────────────────────────

    fun deps(target: String): ContentModel {
        if (target.isBlank()) return ContentModel.text("Dependencies", listOf("  ${Term.WARN}No target entered.${Term.RESET}"))
        return try {
            val inc = storage.getIncomingDependencies(target)
            val out = storage.getOutgoingDependencies(target)
            val rows = mutableListOf<String>()
            val selectable = mutableListOf<Int>()
            val refAt = mutableMapOf<Int, String>()
            rows.add(""); rows.add("  ${Term.ACCENT2}$B Dependencies for \"$target\"${Term.RESET}"); rows.add("")
            rows.add("  ${Term.SUCCESS}$B Incoming — ${inc.size}${Term.RESET}")
            if (inc.isEmpty()) rows.add("  $D  none${Term.RESET}")
            inc.forEach { e -> val r = rows.size; rows.add("  ${Term.MUTED}  <- ${Term.BRIGHT}${e.sourceId.take(52)}${Term.RESET} $D(${e.relation})${Term.RESET}"); selectable.add(r); refAt[r] = e.sourceId }
            rows.add("")
            rows.add("  ${Term.CYAN}$B Outgoing — ${out.size}${Term.RESET}")
            if (out.isEmpty()) rows.add("  $D  none${Term.RESET}")
            out.forEach { e -> val r = rows.size; rows.add("  ${Term.MUTED}  -> ${Term.BRIGHT}${e.targetId.take(52)}${Term.RESET} $D(${e.relation})${Term.RESET}"); selectable.add(r); refAt[r] = e.targetId }
            ContentModel(
                title = "Dependencies — $target", rows = rows, selectable = selectable,
                onActivate = { i -> refAt[i]?.let { symbolDetail(it) } ?: deps(target) },
                status = "${inc.size} incoming · ${out.size} outgoing"
            )
        } catch (e: Exception) {
            ContentModel.text("Dependencies", listOf("  ${Term.RED}Error: ${e.message}${Term.RESET}"))
        }
    }

    // ─── Repository Stats ─────────────────────────────────────────────────────

    fun stats(): ContentModel {
        return try {
            val s = storage.getRepositoryStats()
            val symbols = storage.getAllSymbols()
            val byType = symbols.groupBy { it.type }
            val byLang = symbols.map { langOf(it.filePath) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val rows = mutableListOf<String>()
            rows.add(""); rows.add("  ${Term.ACCENT2}$B Repository Statistics${Term.RESET}"); rows.add("")
            rows.add("  ${Term.MUTED}Files         ${Term.RESET} $B${Term.CYAN}${s.fileCount}${Term.RESET}")
            rows.add("  ${Term.MUTED}Symbols       ${Term.RESET} $B${Term.CYAN}${s.symbolCount}${Term.RESET}")
            rows.add("  ${Term.MUTED}Packages      ${Term.RESET} $B${Term.CYAN}${s.packageCount}${Term.RESET}")
            rows.add("  ${Term.MUTED}Classes       ${Term.RESET} $B${Term.CYAN}${s.classCount}${Term.RESET}")
            rows.add("  ${Term.MUTED}Functions     ${Term.RESET} $B${Term.CYAN}${s.functionCount}${Term.RESET}")
            rows.add("  ${Term.MUTED}Edges         ${Term.RESET} $B${Term.CYAN}${s.edgeCount}${Term.RESET}")
            rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(42)}${Term.RESET}"); rows.add("")
            rows.add("  ${Term.ACCENT2}$B Symbols by Type${Term.RESET}")
            enumValues<SymbolType>().forEach { t -> rows.add("  ${Term.MUTED}${t.name.padEnd(12)}${Term.RESET} $B${Term.CYAN}${byType[t]?.size ?: 0}${Term.RESET}") }
            rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(42)}${Term.RESET}"); rows.add("")
            rows.add("  ${Term.ACCENT2}$B Files by Language${Term.RESET}")
            byLang.forEach { (lang, n) -> rows.add("  ${Term.MUTED}${lang.padEnd(12)}${Term.RESET} $B${Term.CYAN}$n${Term.RESET}") }
            ContentModel.text("Repository Stats", rows, "${s.symbolCount} symbols · ${s.edgeCount} edges")
        } catch (e: Exception) {
            ContentModel.text("Repository Stats", listOf("  ${Term.RED}Error: ${e.message}${Term.RESET}"))
        }
    }

    // ─── Architecture Flow ────────────────────────────────────────────────────

    fun flow(): ContentModel {
        return try {
            val nodes = ArchitectureFlowAnalyzer.classifyNodes(storage.getAllSymbols(), storage.getAllEdges())
            val byLayer = nodes.groupBy { it.layer }
            val rows = mutableListOf<String>()
            val selectable = mutableListOf<Int>()
            val refAt = mutableMapOf<Int, String>()
            rows.add(""); rows.add("  ${Term.ACCENT2}$B Architectural Layers${Term.RESET}"); rows.add("")
            enumValues<ArchitecturalLayer>().forEach { layer ->
                rows.add("  ${Term.BRIGHT}${layer.displayName.padEnd(30)}${Term.RESET} $B${Term.CYAN}${byLayer[layer]?.size ?: 0}${Term.RESET}")
            }
            rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(42)}${Term.RESET}")
            enumValues<ArchitecturalLayer>().forEach { layer ->
                val items = byLayer[layer] ?: emptyList()
                if (items.isEmpty()) return@forEach
                rows.add(""); rows.add("  ${Term.ACCENT2}$B ${layer.displayName}${Term.RESET}")
                items.take(6).forEach { n ->
                    val r = rows.size
                    rows.add("  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}${n.name}${Term.RESET} $D(${n.packageName})${Term.RESET}")
                    selectable.add(r); refAt[r] = n.id
                }
            }
            ContentModel(
                title = "Architecture Flow", rows = rows, selectable = selectable,
                onActivate = { i -> refAt[i]?.let { symbolDetail(it) } ?: flow() },
                status = "${nodes.size} classified components"
            )
        } catch (e: Exception) {
            ContentModel.text("Architecture Flow", listOf("  ${Term.RED}Error: ${e.message}${Term.RESET}"))
        }
    }

    // ─── Module Map ───────────────────────────────────────────────────────────

    fun moduleMap(): ContentModel {
        return try {
            val symbols = storage.getAllSymbols()
            val edges = storage.getAllEdges()
            val groups = ModuleGraphAnalyzer.analyze(symbols, edges)
            val moduleEdges = ModuleGraphAnalyzer.moduleEdges(symbols, edges)
            val extRefs = ModuleGraphAnalyzer.externalRefs(symbols, edges)
            val rows = mutableListOf<String>()
            val selectable = mutableListOf<Int>()
            val moduleAt = mutableMapOf<Int, ModuleGroup>()
            rows.add(""); rows.add("  ${Term.ACCENT2}$B Modules — ${groups.size}${Term.RESET}"); rows.add("")
            groups.forEach { g -> val r = rows.size; rows.add("  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}${g.name}${Term.RESET} $D— ${g.files.size} files${Term.RESET}"); selectable.add(r); moduleAt[r] = g }
            rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}"); rows.add("")
            rows.add("  ${Term.ACCENT2}$B Module Wiring — ${moduleEdges.size}${Term.RESET}")
            if (moduleEdges.isEmpty()) rows.add("  $D  none${Term.RESET}")
            moduleEdges.forEach { e -> rows.add("  ${Term.CYAN}${e.source.take(16).padEnd(16)}${Term.RESET} $D──${e.relation} (${e.weight})──▶${Term.RESET} ${Term.BRIGHT}${e.target.take(20)}${Term.RESET}") }
            rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}"); rows.add("")
            rows.add("  ${Term.ACCENT2}$B External Dependencies — ${extRefs.size}${Term.RESET}")
            if (extRefs.isEmpty()) rows.add("  $D  none${Term.RESET}")
            extRefs.take(18).forEach { e -> rows.add("  $D${e.source.take(12).padEnd(12)} ──▶ ${e.target}${Term.RESET}") }
            ContentModel(
                title = "Module Map", rows = rows, selectable = selectable,
                onActivate = { i -> moduleAt[i]?.let { filesInModule(it) } ?: moduleMap() },
                status = "${groups.size} modules · ${moduleEdges.size} links · ${extRefs.size} external refs"
            )
        } catch (e: Exception) {
            ContentModel.text("Module Map", listOf("  ${Term.RED}Error: ${e.message}${Term.RESET}"))
        }
    }

    // ─── Dead Code ────────────────────────────────────────────────────────────

    fun dead(): ContentModel {
        return try {
            val symbols = storage.getAllSymbols()
            val edges = storage.getAllEdges()
            val targets = (edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId } +
                edges.filter { it.relation == RelationType.CALLS }.map { it.targetId }).toSet()
            val dead = symbols.filter { s ->
                (s.type == SymbolType.CLASS || s.type == SymbolType.INTERFACE) &&
                    s.id !in targets && !s.name.contains("Main") && !s.name.endsWith("Command")
            }
            if (dead.isEmpty()) return ContentModel.text("Dead Code", listOf("", "  ${Term.SUCCESS}$B No dead code candidates found.${Term.RESET}"))
            val header = listOf(
                "", "  ${Term.ACCENT2}$B Dead Code Candidates — ${dead.size}${Term.RESET}", "",
                "  ${Term.MUTED}${"Name".padEnd(26)} ${"Type".padEnd(10)} ${"File".padEnd(22)}${Term.RESET}",
                "  ${Term.MUTED}${"─".repeat(26)} ${"─".repeat(10)} ${"─".repeat(22)}${Term.RESET}"
            )
            listPane(
                title = "Dead Code", header = header, items = dead,
                rowFor = { s ->
                    "  ${Term.RED}${s.name.take(26).padEnd(26)}${Term.RESET} ${Term.MUTED}${s.type.name.take(10).padEnd(10)}${Term.RESET} ${Term.MUTED}${shortName(s.filePath).take(22).padEnd(22)}${Term.RESET}"
                },
                detailFor = { symbolDetail(it.id) },
                status = "${dead.size} candidate(s) · Enter inspects"
            )
        } catch (e: Exception) {
            ContentModel.text("Dead Code", listOf("  ${Term.RED}Error: ${e.message}${Term.RESET}"))
        }
    }

    // ─── Files ────────────────────────────────────────────────────────────────

    fun files(): ContentModel {
        val byFile = storage.getAllSymbols().groupBy { it.filePath }
        if (byFile.isEmpty()) return ContentModel.text("Files", listOf("  ${Term.WARN}Index is empty.${Term.RESET}"))
        return listPane(
            title = "Files",
            header = listOf("", "  ${Term.ACCENT2}$B Indexed Files — ${byFile.size}${Term.RESET}", ""),
            items = byFile.keys.sorted(),
            rowFor = { path ->
                val syms = byFile[path] ?: emptyList()
                "  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}${shortName(path).take(34).padEnd(34)}${Term.RESET} $D${langOf(path)} · ${syms.size} symbols${Term.RESET}"
            },
            detailFor = { fileDetail(it) },
            status = "${byFile.size} files · Enter opens a file"
        )
    }

    fun filesInModule(group: ModuleGroup): ContentModel {
        return listPane(
            title = "Module — ${group.name}",
            header = listOf("", "  ${Term.ACCENT2}$B ${group.name} — ${group.files.size} files${Term.RESET}", ""),
            items = group.files,
            rowFor = { f ->
                val mark = when {
                    f.isSolo -> "${Term.WARN}●${Term.RESET}"
                    f.isActual -> "${Term.CYAN}■${Term.RESET}"
                    else -> "${Term.MUTED}●${Term.RESET}"
                }
                "  $mark ${Term.BRIGHT}${f.display.take(44)}${Term.RESET}"
            },
            detailFor = { fileDetail(it.path) },
            status = "${group.files.size} files · ● file · ■ actual · ● solo"
        )
    }

    fun fileDetail(filePath: String): ContentModel {
        val syms = storage.getAllSymbols().filter { it.filePath == filePath }
        val header = listOf(
            "", "  ${Term.ACCENT2}$B File — ${shortName(filePath)}${Term.RESET}", "",
            "  ${Term.MUTED}Path ${Term.RESET} ${Term.BRIGHT}${filePath.replace('\\', '/')}${Term.RESET}",
            "  ${Term.MUTED}Language ${Term.RESET} ${Term.BRIGHT}${langOf(filePath)}${Term.RESET}  ${Term.MUTED}Symbols ${Term.RESET} ${Term.BRIGHT}${syms.size}${Term.RESET}", "",
            "  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}", ""
        )
        return listPane(
            title = "File — ${shortName(filePath)}", header = header, items = syms,
            rowFor = { s -> "  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}${s.name.take(28).padEnd(28)}${Term.RESET} ${Term.CYAN}${s.type.name.take(10).padEnd(10)}${Term.RESET} $D:${s.lineNumber}${Term.RESET}" },
            detailFor = { symbolDetail(it.id) },
            status = "${syms.size} symbols · Enter inspects"
        )
    }

    // ─── Symbol Browser ───────────────────────────────────────────────────────

    fun symbolBrowser(filter: SymbolType? = null): ContentModel {
        val all = storage.getAllSymbols()
        val filtered = if (filter == null) all else all.filter { it.type == filter }
        val label = filter?.name?.lowercase() ?: "all"
        val header = listOf(
            "", "  ${Term.ACCENT2}$B Symbol Browser — $label${Term.RESET}  $D(${filtered.size})${Term.RESET}", "",
            "  ${Term.MUTED}${"Name".padEnd(26)} ${"Type".padEnd(10)} ${"File".padEnd(22)} Line${Term.RESET}",
            "  ${Term.MUTED}${"─".repeat(26)} ${"─".repeat(10)} ${"─".repeat(22)} ───${Term.RESET}"
        )
        val model = listPane(
            title = "Symbol Browser", header = header,
            items = filtered.sortedBy { it.name.lowercase() },
            rowFor = { s ->
                "  ${Term.BRIGHT}${s.name.take(26).padEnd(26)}${Term.RESET} ${Term.CYAN}${s.type.name.take(10).padEnd(10)}${Term.RESET} ${Term.MUTED}${shortName(s.filePath).take(22).padEnd(22)}${Term.RESET} $D${s.lineNumber}${Term.RESET}"
            },
            detailFor = { symbolDetail(it.id) },
            status = "${filtered.size} symbols · [c]lass [i]nterface [f]unction [p]ackage [a]ll"
        )
        return model.copy(filters = { c -> when (c) {
            'c' -> symbolBrowser(SymbolType.CLASS)
            'i' -> symbolBrowser(SymbolType.INTERFACE)
            'f' -> symbolBrowser(SymbolType.FUNCTION)
            'p' -> symbolBrowser(SymbolType.PACKAGE)
            else -> symbolBrowser(null)
        } })
    }

    // ─── Symbol Detail ────────────────────────────────────────────────────────

    fun symbolDetail(id: String): ContentModel {
        val sym = storage.getAllSymbols().firstOrNull { it.id == id }
        if (sym == null) {
            return ContentModel.text(
                "Symbol",
                listOf(
                    "", "  ${Term.WARN}\"${Term.fit(id, 50)}\"${Term.RESET}",
                    "  $D  is an external reference and has no index record.${Term.RESET}"
                )
            )
        }
        val inc = storage.getIncomingDependencies(id)
        val out = storage.getOutgoingDependencies(id)
        val rows = mutableListOf<String>()
        val selectable = mutableListOf<Int>()
        val refAt = mutableMapOf<Int, String>()
        rows.add(""); rows.add("  ${Term.ACCENT2}$B ${sym.name}${Term.RESET}  ${Term.CYAN}${sym.type.name}${Term.RESET}"); rows.add("")
        rows.add("  ${Term.MUTED}Package ${Term.RESET} ${Term.BRIGHT}${sym.packageName ?: "-"}${Term.RESET}")
        rows.add("  ${Term.MUTED}File    ${Term.RESET} ${Term.BRIGHT}${sym.filePath.replace('\\', '/')}${Term.RESET}  $D:${sym.lineNumber}${Term.RESET}")
        rows.add(""); rows.add("  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}"); rows.add("")
        rows.add("  ${Term.SUCCESS}$B Incoming — ${inc.size}${Term.RESET}")
        if (inc.isEmpty()) rows.add("  $D  none${Term.RESET}")
        inc.forEach { e -> val r = rows.size; rows.add("  ${Term.MUTED}  <- ${Term.BRIGHT}${Term.fit(e.sourceId, 52)}${Term.RESET} $D(${e.relation})${Term.RESET}"); selectable.add(r); refAt[r] = e.sourceId }
        rows.add("")
        rows.add("  ${Term.CYAN}$B Outgoing — ${out.size}${Term.RESET}")
        if (out.isEmpty()) rows.add("  $D  none${Term.RESET}")
        out.forEach { e -> val r = rows.size; rows.add("  ${Term.MUTED}  -> ${Term.BRIGHT}${Term.fit(e.targetId, 52)}${Term.RESET} $D(${e.relation})${Term.RESET}"); selectable.add(r); refAt[r] = e.targetId }
        return ContentModel(
            title = sym.name, rows = rows, selectable = selectable,
            onActivate = { i -> refAt[i]?.let { symbolDetail(it) } ?: symbolDetail(id) },
            status = "${inc.size} incoming · ${out.size} outgoing"
        )
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    fun scan(): ContentModel {
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
            val walkedFiles = dev.ajithgoveas.kindex.cli.walkFiles(rootDir).filter { file -> extractors.any { it.supports(file) } }
            val existingFiles = storage.getFilesMetadata()
            val filesToScan = mutableListOf<MPFile>()
            val unchangedCount = mutableListOf<String>()
            for (file in walkedFiles) {
                val dbMeta = existingFiles[file.path]
                if (dbMeta != null) {
                    val (dbTime, dbHash) = dbMeta
                    val currentHash = dev.ajithgoveas.kindex.core.io.HashUtils.sha256(file)
                    if (file.lastModified() == dbTime && currentHash == dbHash) { unchangedCount.add(file.path); continue }
                }
                filesToScan.add(file)
            }
            val walkedPathsSet = walkedFiles.map { it.path }.toSet()
            val deletedPaths = existingFiles.keys.filter { it !in walkedPathsSet }
            val parseResults = mutableListOf<dev.ajithgoveas.kindex.core.model.ParseResult>()
            for (file in filesToScan) {
                try { parseResults.add(extractors.first { it.supports(file) }.extract(file)) } catch (_: Throwable) {}
            }
            val dbSymbols = storage.getAllSymbols()
            val modifiedPathsSet = filesToScan.map { it.path }.toSet()
            val unmodifiedSymbols = dbSymbols.filter { it.filePath !in modifiedPathsSet && it.filePath !in deletedPaths }
            val newSymbols = parseResults.flatMap { it.symbols }
            val allSymbolsMap = (unmodifiedSymbols + newSymbols).associateBy { it.id }

            val resolver = dev.ajithgoveas.kindex.parser.SymbolResolver()
            val fullyResolvedResults = parseResults.map { result ->
                val rawImports = result.edges.filter { it.relation == RelationType.IMPORTS }.map { it.targetId }
                val importResolution = resolver.resolveImportsDetailed(result.sourceFile.path, rawImports, allSymbolsMap)
                val externalEdges = importResolution.unresolved.map { dev.ajithgoveas.kindex.core.model.Edge(result.sourceFile.path, it, RelationType.IMPORTS) }
                val staticEdges = result.edges.filter { it.relation == RelationType.CONTAINS || it.relation == RelationType.EXTENDS }
                val resolvedCalls = result.edges
                    .filter { it.relation == RelationType.CALLS && it.targetId.startsWith("REF:") }
                    .flatMap { callEdge ->
                        resolver.resolveCalls(callEdge.sourceId, listOf(callEdge.targetId), rawImports, result.sourceFile.packageName, allSymbolsMap)
                    }
                result.copy(edges = staticEdges + importResolution.resolved + resolvedCalls + externalEdges)
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
            } catch (_: Exception) { false }

            val rows = mutableListOf<String>()
            rows.add(""); rows.add("  ${Term.SUCCESS}$B✓ Interactive scan complete${Term.RESET}"); rows.add("")
            rows.add("  ${Term.MUTED}Total Candidate Files ${Term.RESET} $B${Term.CYAN}${walkedFiles.size}${Term.RESET}")
            rows.add("  ${Term.MUTED}Modified / New Files  ${Term.RESET} $B${Term.CYAN}${filesToScan.size}${Term.RESET}")
            rows.add("  ${Term.MUTED}Unchanged Files       ${Term.RESET} $B${Term.CYAN}${unchangedCount.size}${Term.RESET}")
            rows.add("  ${Term.MUTED}Pruned Files          ${Term.RESET} $B${Term.CYAN}${deletedPaths.size}${Term.RESET}")
            rows.add(""); rows.add("  ${Term.MUTED}Indexed Symbols       ${Term.RESET} $B${Term.CYAN}${s.symbolCount}${Term.RESET}")
            rows.add("  ${Term.MUTED}Indexed Edges         ${Term.RESET} $B${Term.CYAN}${s.edgeCount}${Term.RESET}")
            if (graphsWritten) rows.add("  ${Term.MUTED}Architecture graphs    ${Term.RESET} ${Term.SUCCESS}✓${Term.RESET}${Term.MUTED}.kindex/graph.{mmd,dot,json}${Term.RESET}")
            rows.add(""); rows.add("  ${Term.MUTED}Database updated at:${Term.RESET}")
            rows.add("  ${Term.BRIGHT}  ${dbFile.absolutePath}${Term.RESET}")
            ContentModel.text("Scan", rows, "Index refreshed · ${s.symbolCount} symbols")
        } catch (e: Exception) {
            ContentModel.text("Scan", listOf("", "  ${Term.RED}Error during repository scan: ${e.message}${Term.RESET}"))
        }
    }

    // ─── About ────────────────────────────────────────────────────────────────

    fun about(): ContentModel {
        val rows = listOf(
            "",
            "  ${Term.ACCENT2}$B KIndex — Code Knowledge Indexer${Term.RESET}",
            "  $D v1.0.0 · Kotlin Multiplatform · local-first${Term.RESET}",
            "",
            "  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}",
            "",
            "  ${Term.ACCENT2}$B Modules${Term.RESET}",
            "  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}kindex-core${Term.RESET} $D— domain, I/O, analysis${Term.RESET}",
            "  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}kindex-parser${Term.RESET} $D— tree-sitter extraction${Term.RESET}",
            "  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}kindex-storage${Term.RESET} $D— SQLDelight SQLite${Term.RESET}",
            "  ${Term.MUTED}▸${Term.RESET} ${Term.BRIGHT}kindex-cli${Term.RESET} $D— commands + TUI${Term.RESET}",
            "",
            "  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}",
            "",
            "  ${Term.ACCENT2}$B Keyboard${Term.RESET}",
            "  ${Term.MUTED}↑ ↓ / ↵${Term.RESET} ${Term.BRIGHT}navigate & select${Term.RESET}",
            "  ${Term.MUTED}1-9${Term.RESET} ${Term.BRIGHT}quick-jump to a menu item${Term.RESET}",
            "  ${Term.MUTED}PgUp PgDn${Term.RESET} ${Term.BRIGHT}scroll long lists${Term.RESET}",
            "  ${Term.MUTED}Home End${Term.RESET} ${Term.BRIGHT}first / last item${Term.RESET}",
            "  ${Term.MUTED}Esc${Term.RESET} ${Term.BRIGHT}back one level${Term.RESET}",
            "  ${Term.MUTED}q${Term.RESET} ${Term.BRIGHT}quit (from home / dialogs)${Term.RESET}",
            "",
            "  ${Term.MUTED}${"─".repeat(46)}${Term.RESET}",
            "",
            "  ${Term.WARN}Local & private.${Term.RESET} $D KIndex never leaves your machine.$D",
            ""
        )
        return ContentModel.text("About", rows, "KIndex v1.0.0")
    }
}
