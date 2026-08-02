package dev.ajithgoveas.kindex.core.analysis

import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol

data class ModuleFile(
    val path: String,
    val module: String,
    val display: String,
    val layer: ArchitecturalLayer,
    val isSolo: Boolean,
    val isActual: Boolean
)

data class ModuleGroup(
    val name: String,
    val files: List<ModuleFile>
)

object ModuleGraphAnalyzer {

    private val actualSourceSets = setOf(
        "jvmMain", "nativeMain", "jsMain", "iosMain", "macosMain", "linuxMain",
        "mingwMain", "androidMain", "wasmMain", "appleMain"
    )

    fun analyze(symbols: List<Symbol>, edges: List<Edge>): List<ModuleGroup> {
        if (symbols.isEmpty()) return emptyList()
        return analysisOf(symbols, edges).groups
    }

    fun fileEdges(symbols: List<Symbol>, edges: List<Edge>): List<AggregatedEdge> =
        analysisOf(symbols, edges).fileEdges

    fun moduleEdges(symbols: List<Symbol>, edges: List<Edge>): List<AggregatedEdge> =
        analysisOf(symbols, edges).moduleEdges

    fun externalRefs(symbols: List<Symbol>, edges: List<Edge>): List<AggregatedEdge> =
        analysisOf(symbols, edges).externalRefs

    fun renderMermaid(symbols: List<Symbol>, edges: List<Edge>): String {
        if (symbols.isEmpty()) return "flowchart TB\n    NOTE[\"Empty knowledge graph\"]\n"
        val d = analysisOf(symbols, edges)
        val sb = StringBuilder()
        sb.appendLine("flowchart TB")
        sb.appendLine("    classDef entry fill:#457b9d,color:#fff,stroke:#1d3557,stroke-width:2px;")
        sb.appendLine("    classDef service fill:#2a9d8f,color:#fff,stroke:#264653,stroke-width:2px;")
        sb.appendLine("    classDef storage fill:#e76f51,color:#fff,stroke:#b7094c,stroke-width:2px;")
        sb.appendLine("    classDef solo fill:#e9ecef,color:#212529,stroke:#ced4da,stroke-width:1px,stroke-dasharray:5 5;")
        sb.appendLine("    classDef actual fill:#3a3a3a,color:#fff,stroke:#111,stroke-dasharray:5 5;")
        sb.appendLine("    classDef extern fill:#fff3bf,color:#212529,stroke:#f59f00;")
        sb.appendLine()

        for (g in d.groups) {
            sb.appendLine("    subgraph ${safeId(g.name)}[\"${g.name}\"]")
            sb.appendLine("        direction TB")
            for (f in g.files) {
                val id = d.idByPath[f.path] ?: continue
                sb.appendLine("        $id[\"${f.display}\"]:::${mermaidClass(f)}")
            }
            sb.appendLine("    end")
            sb.appendLine()
        }

        if (d.externalRefs.isNotEmpty()) {
            sb.appendLine("    subgraph ${safeId("External Dependencies")}[\"External Dependencies\"]")
            sb.appendLine("        direction TB")
            val extId = mutableMapOf<String, String>()
            for (r in d.externalRefs) {
                if (extId.containsKey(r.target)) continue
                val id = safeId("EXT_${r.target}")
                extId[r.target] = id
                sb.appendLine("        $id[\"${r.target}\"]:::extern")
            }
            sb.appendLine("    end")
            sb.appendLine()
            for (r in d.externalRefs.take(100)) {
                val src = d.idByPath[r.source] ?: continue
                val tgt = extId[r.target] ?: continue
                sb.appendLine("    $src -->|${r.relation}| $tgt")
            }
        }

        val seen = mutableSetOf<String>()
        for (e in d.fileEdges.take(150)) {
            val src = d.idByPath[e.source] ?: continue
            val tgt = d.idByPath[e.target] ?: continue
            val line = "    $src -->|${e.relation}| $tgt"
            if (seen.add(line)) sb.appendLine(line)
        }
        return sb.toString()
    }

    fun renderDot(symbols: List<Symbol>, edges: List<Edge>): String {
        if (symbols.isEmpty()) return "digraph KIndexModuleGraph {\n    node [label=\"Empty knowledge graph\"];\n}\n"
        val d = analysisOf(symbols, edges)
        val sb = StringBuilder()
        sb.appendLine("digraph KIndexModuleGraph {")
        sb.appendLine("    rankdir=TB;")
        sb.appendLine("    compound=true;")
        sb.appendLine("    node [shape=box, style=\"filled,rounded\", fontname=\"Helvetica\", color=\"#495057\"];")
        sb.appendLine("    edge [fontname=\"Helvetica\", fontsize=9, color=\"#6C757D\"];")
        for (g in d.groups) {
            sb.appendLine("    subgraph cluster_${safeId(g.name)} {")
            sb.appendLine("        label=\"${g.name}\"; style=filled; fillcolor=\"#F8F9FA\"; color=\"#6C757D\";")
            for (f in g.files) {
                sb.appendLine("        \"${f.path}\" [label=\"${f.display}\", fillcolor=\"${dotColor(f)}\", ${dotStyle(f)}];")
            }
            sb.appendLine("    }")
        }
        if (d.externalRefs.isNotEmpty()) {
            sb.appendLine("    subgraph cluster_external {")
            sb.appendLine("        label=\"External Dependencies\"; style=filled; fillcolor=\"#FFF3BF\"; color=\"#F59F00\";")
            for (r in d.externalRefs) {
                sb.appendLine("        \"EXT:${r.target}\" [label=\"${r.target}\", fillcolor=\"#FFF3BF\"];")
            }
            sb.appendLine("    }")
        }
        val seen = mutableSetOf<String>()
        for (e in d.fileEdges.take(200)) {
            val line = "    \"${e.source}\" -> \"${e.target}\" [label=\"${e.relation} (${e.weight})\"];"
            if (seen.add(line)) sb.appendLine(line)
        }
        for (r in d.externalRefs.take(100)) {
            sb.appendLine("    \"${r.source}\" -> \"EXT:${r.target}\" [label=\"${r.relation}\"];")
        }
        sb.appendLine("}")
        return sb.toString()
    }

    fun renderJson(symbols: List<Symbol>, edges: List<Edge>): String {
        if (symbols.isEmpty()) {
            return "{ \"nodes\": [], \"links\": [], \"modules\": [], \"moduleLinks\": [], \"externalRefs\": [] }"
        }
        val d = analysisOf(symbols, edges)
        val nodes = d.groups.flatMap { g ->
            g.files.map { f ->
                "    { \"id\": \"${escapeJson(f.path)}\", \"name\": \"${escapeJson(f.display)}\", " +
                    "\"module\": \"${escapeJson(g.name)}\", \"layer\": \"${f.layer.name}\", \"type\": \"FILE\", " +
                    "\"solo\": ${f.isSolo}, \"actual\": ${f.isActual} }"
            }
        }
        val links = d.fileEdges.map {
            "    { \"source\": \"${escapeJson(it.source)}\", \"target\": \"${escapeJson(it.target)}\", \"relation\": \"${it.relation}\", \"weight\": ${it.weight} }"
        }
        val moduleLinks = d.moduleEdges.map {
            "    { \"source\": \"${escapeJson(it.source)}\", \"target\": \"${escapeJson(it.target)}\", \"relation\": \"${it.relation}\", \"weight\": ${it.weight} }"
        }
        val externLinks = d.externalRefs.map {
            "    { \"source\": \"${escapeJson(it.source)}\", \"target\": \"${escapeJson(it.target)}\", \"relation\": \"${it.relation}\" }"
        }
        return buildString {
            appendLine("{")
            appendLine("  \"modules\": [")
            appendLine(d.groups.joinToString(",\n") { "    { \"name\": \"${escapeJson(it.name)}\", \"files\": ${it.files.size} }" })
            appendLine("  ],")
            appendLine("  \"nodes\": [")
            appendLine(nodes.joinToString(",\n"))
            appendLine("  ],")
            appendLine("  \"links\": [")
            appendLine(links.joinToString(",\n"))
            appendLine("  ],")
            appendLine("  \"moduleLinks\": [")
            appendLine(moduleLinks.joinToString(",\n"))
            appendLine("  ],")
            appendLine("  \"externalRefs\": [")
            appendLine(externLinks.joinToString(",\n"))
            appendLine("  ]")
            appendLine("}")
        }
    }

    private data class ModuleGraphData(
        val groups: List<ModuleGroup>,
        val idByPath: Map<String, String>,
        val fileEdges: List<AggregatedEdge>,
        val moduleEdges: List<AggregatedEdge>,
        val externalRefs: List<AggregatedEdge>
    )

    private fun analysisOf(symbols: List<Symbol>, edges: List<Edge>): ModuleGraphData {
        val filePaths = symbols.map { it.filePath }.distinct()
        val commonRoot = computeCommonRoot(filePaths)
        val symFile = symbols.associate { it.id to it.filePath }
        val files = filePaths.toSet()
        val entryFiles = EntryPointResolver.findEntryPoints(symbols).map { it.filePath }.toSet()
        val perSymbolLayer = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges).associate { it.id to it.layer }
        val layerByFile = mutableMapOf<String, ArchitecturalLayer>()
        for (s in symbols) {
            val layer = perSymbolLayer[s.id] ?: ArchitecturalLayer.UTILITIES
            layerByFile[s.filePath] = mergeLayer(layerByFile[s.filePath], layer)
        }

        val incoming = mutableMapOf<String, Int>()
        for (e in edges) {
            val s = resolveFile(e.sourceId, symFile, files)
            val t = resolveFile(e.targetId, symFile, files)
            if (t != null && s != t) incoming[t] = (incoming[t] ?: 0) + 1
        }

        val singleModule = filePaths.map { restOf(it, commonRoot).firstOrNull() }.distinct().size <= 1
        val defaultModule = commonRoot.lastOrNull() ?: "root"

        val groups = filePaths
            .groupBy { p -> if (singleModule) defaultModule else moduleOf(p, commonRoot) }
            .map { (mod, paths) ->
                ModuleGroup(
                    name = mod,
                    files = paths.map { p ->
                        val layer = layerByFile[p] ?: ArchitecturalLayer.UTILITIES
                        ModuleFile(
                            path = p,
                            module = mod,
                            display = displayName(p, commonRoot, singleModule),
                            layer = layer,
                            isSolo = (incoming[p] ?: 0) == 0 && p !in entryFiles,
                            isActual = isActualFile(p)
                        )
                    }.sortedWith(compareBy({ it.layer != ArchitecturalLayer.ENTRY_POINTS }, { it.display }))
                )
            }
            .sortedBy { it.name }

        val moduleByPath = groups.flatMap { g -> g.files.map { it.path to g.name } }.toMap()
        val idByPath = groups
            .flatMap { g -> g.files.map { it.path to safeId("${g.name}__${it.display}") } }
            .toMap()

        val pathEdgeMap = mutableMapOf<Pair<String, Pair<String, String>>, Int>()
        val moduleEdgeMap = mutableMapOf<Pair<String, Pair<String, String>>, Int>()
        val externalMap = mutableMapOf<Pair<String, Pair<String, String>>, Int>()
        for (e in edges) {
            val src = resolveFile(e.sourceId, symFile, files) ?: continue
            val tgt = resolveFile(e.targetId, symFile, files)
            if (tgt != null && src != tgt) {
                val fk = Pair(src, Pair(tgt, e.relation.name))
                pathEdgeMap[fk] = (pathEdgeMap[fk] ?: 0) + 1
                val sm = moduleByPath[src]
                val tm = moduleByPath[tgt]
                if (sm != null && tm != null && sm != tm) {
                    val mk = Pair(sm, Pair(tm, e.relation.name))
                    moduleEdgeMap[mk] = (moduleEdgeMap[mk] ?: 0) + 1
                }
            } else if (e.relation == RelationType.IMPORTS && tgt == null) {
                val ek = Pair(src, Pair(e.targetId, e.relation.name))
                externalMap[ek] = (externalMap[ek] ?: 0) + 1
            }
        }

        return ModuleGraphData(
            groups = groups,
            idByPath = idByPath,
            fileEdges = toSortedEdges(pathEdgeMap),
            moduleEdges = toSortedEdges(moduleEdgeMap),
            externalRefs = toSortedEdges(externalMap)
        )
    }

    private fun toSortedEdges(map: Map<Pair<String, Pair<String, String>>, Int>): List<AggregatedEdge> =
        map.map { (k, c) -> AggregatedEdge(k.first, k.second.first, k.second.second, c) }
            .sortedByDescending { it.weight }

    fun computeCommonRoot(paths: List<String>): List<String> {
        val segmented = paths
            .map { it.replace('\\', '/').split('/').filter { s -> s.isNotEmpty() && s != "." } }
            .filter { it.isNotEmpty() }
        if (segmented.isEmpty()) return emptyList()
        val common = segmented.first().toMutableList()
        for (s in segmented.drop(1)) {
            var i = 0
            while (i < common.size && i < s.size && common[i] == s[i]) i++
            while (common.size > i) common.removeAt(common.size - 1)
            if (common.isEmpty()) break
        }
        return common
    }

    private fun restOf(path: String, commonRoot: List<String>): List<String> {
        val segs = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        return segs.drop(commonRoot.size)
    }

    private fun moduleOf(path: String, commonRoot: List<String>): String =
        restOf(path, commonRoot).firstOrNull() ?: "root"

    private fun displayName(path: String, commonRoot: List<String>, singleModule: Boolean): String {
        val rest = restOf(path, commonRoot)
        val rel = if (singleModule || rest.size <= 1) rest else rest.drop(1)
        val display = if (rel.size <= 3) rel.joinToString("/") else rel.takeLast(3).joinToString("/")
        return display.ifEmpty { "unknown" }
    }

    private fun isActualFile(path: String): Boolean {
        val segs = path.replace('\\', '/').split('/')
        return segs.any { it in actualSourceSets }
    }

    private fun mergeLayer(prev: ArchitecturalLayer?, next: ArchitecturalLayer): ArchitecturalLayer {
        if (prev == null) return next
        val priority = mapOf(
            ArchitecturalLayer.ENTRY_POINTS to 3,
            ArchitecturalLayer.SERVICES to 2,
            ArchitecturalLayer.STORAGE to 1,
            ArchitecturalLayer.UTILITIES to 0
        )
        return if ((priority[prev] ?: 0) >= (priority[next] ?: 0)) prev else next
    }

    private fun resolveFile(id: String, symFile: Map<String, String>, files: Set<String>): String? {
        if (id in symFile) return symFile[id]
        if (id in files) return id
        return null
    }

    private fun mermaidClass(f: ModuleFile): String = when {
        f.isSolo -> "solo"
        f.layer == ArchitecturalLayer.ENTRY_POINTS -> "entry"
        f.isActual -> "actual"
        f.layer == ArchitecturalLayer.SERVICES -> "service"
        f.layer == ArchitecturalLayer.STORAGE -> "storage"
        else -> "solo"
    }

    private fun dotColor(f: ModuleFile): String = when {
        f.isSolo || f.layer == ArchitecturalLayer.UTILITIES -> "#E9ECEF"
        f.layer == ArchitecturalLayer.ENTRY_POINTS -> "#457B9D"
        f.layer == ArchitecturalLayer.SERVICES -> "#2A9D8F"
        f.layer == ArchitecturalLayer.STORAGE -> "#E76F51"
        else -> "#E9ECEF"
    }

    private fun dotStyle(f: ModuleFile): String =
        if (f.isSolo) "style=\"filled,dashed\"" else "style=\"filled\""

    private fun safeId(raw: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return if (clean.isEmpty() || clean[0].isDigit()) "node_$clean" else clean
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
