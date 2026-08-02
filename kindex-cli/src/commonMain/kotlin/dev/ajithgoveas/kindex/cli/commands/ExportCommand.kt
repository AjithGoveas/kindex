package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.core.analysis.ArchitectureFlowAnalyzer
import dev.ajithgoveas.kindex.core.analysis.ArchitecturalLayer
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.core.io.MPFile
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class ExportCommand : CliktCommand(name = "export", help = "Export knowledge graph into hierarchical architecture flow diagrams (Mermaid, DOT, JSON)") {
    private val granularity by option("-g", "--granularity", help = "Granularity: flow (default hierarchical flow), file, package, symbol").default("flow")
    private val format by option("-f", "--format", help = "Export format: mermaid, dot, json").default("mermaid")
    private val relationOpt by option("-r", "--relation", help = "Relation filter: all, calls, imports, extends").default("all")
    private val focusOpt by option("--focus", help = "Focus target symbol/file for N-hop connected subgraph")
    private val outputOpt by option("-o", "--output", help = "Output file path")
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
        val rawSymbols = storage.getAllSymbols()
        var rawEdges = storage.getAllEdges()

        if (rawSymbols.isEmpty()) {
            t.println(yellow("Warning: Knowledge graph is empty."))
            return
        }

        // Apply Relation Filter
        if (relationOpt.lowercase() != "all") {
            val filterRel = relationOpt.uppercase()
            rawEdges = rawEdges.filter { it.relation.name == filterRel }
        }

        // Apply Focal Subgraph Traversal
        val focusTarget = focusOpt
        if (!focusTarget.isNullOrBlank()) {
            rawEdges = ArchitectureFlowAnalyzer.computeFocusSubgraph(focusTarget, rawEdges, maxHops = 2)
            if (rawEdges.isEmpty()) {
                t.println(yellow("No connected subgraph found around focus target '$focusTarget'."))
                return
            }
        }

        val targetFormat = format.lowercase()
        val defaultFile = when (targetFormat) {
            "dot", "graphviz" -> "graph.dot"
            "json" -> "graph.json"
            else -> "graph.mmd"
        }
        val targetOutputFile = outputOpt ?: defaultFile

        val content = when (granularity.lowercase()) {
            "file" -> exportFileLevel(rawEdges, targetFormat)
            "package", "module" -> exportPackageLevel(rawSymbols, rawEdges, targetFormat)
            "symbol" -> exportSymbolLevel(rawSymbols, rawEdges, targetFormat)
            else -> exportHierarchicalFlow(rawSymbols, rawEdges, targetFormat) // Default: flow/hierarchy
        }

        try {
            FileSystem.SYSTEM.write(targetOutputFile.toPath()) {
                writeUtf8(content)
            }
            t.println(green("✓ Successfully exported ${granularity.uppercase()} level graph (${targetFormat.uppercase()}) to $targetOutputFile"))
        } catch (e: Exception) {
            t.println(red("Error writing export file: ${e.message}"))
        }
    }

    private fun exportHierarchicalFlow(symbols: List<Symbol>, edges: List<Edge>, format: String): String {
        val classifiedNodes = ArchitectureFlowAnalyzer.classifyNodes(symbols, edges)
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)

        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph KIndexFlowGraph {\n")
                sb.append("    rankdir=TB;\n")
                sb.append("    node [shape=box, style=\"filled,rounded\", fontname=\"Helvetica\"];\n")
                sb.append("    edge [fontname=\"Helvetica\", fontsize=9, color=\"#6C757D\"];\n\n")

                val layerGroups = classifiedNodes.groupBy { it.layer }
                for (layer in ArchitecturalLayer.values()) {
                    val nodes = layerGroups[layer] ?: continue
                    sb.append("    subgraph cluster_${layer.name} {\n")
                    sb.append("        label=\"${layer.emoji} ${layer.displayName}\";\n")
                    sb.append("        style=dashed; color=\"#6C757D\";\n")
                    val files = nodes.map { it.id.substringAfterLast("/").substringAfterLast("\\").substringBefore("#") }.distinct()
                    for (f in files.take(8)) {
                        val safeId = f.replace(Regex("[^a-zA-Z0-9_]"), "_")
                        sb.append("        \"$safeId\" [label=\"$f\"];\n")
                    }
                    sb.append("    }\n\n")
                }

                for (e in fileEdges.take(100)) {
                    val src = e.source.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val tgt = e.target.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    sb.append("    \"$src\" -> \"$tgt\" [label=\"${e.relation} (${e.weight})\"];\n")
                }
                sb.append("}\n").toString()
            }
            "json" -> exportJsonNodesAndEdges(classifiedNodes, fileEdges)
            else -> {
                val sb = StringBuilder("graph TD\n")
                sb.append("    classDef entry fill:#457b9d,color:#fff,stroke:#1d3557;\n")
                sb.append("    classDef service fill:#2a9d8f,color:#fff,stroke:#264653;\n")
                sb.append("    classDef storage fill:#e76f51,color:#fff,stroke:#b7094c;\n")
                sb.append("    classDef solo fill:#e9ecef,color:#212529,stroke:#ced4da,stroke-dasharray: 5 5;\n\n")

                val layerGroups = classifiedNodes.groupBy { it.layer }
                for (layer in ArchitecturalLayer.values()) {
                    val nodes = layerGroups[layer] ?: continue
                    val layerTitle = "${layer.emoji} ${layer.displayName}"
                    sb.append("    subgraph ${layer.name}[\"$layerTitle\"]\n")
                    val files = nodes.map { it.id.substringAfterLast("/").substringAfterLast("\\").substringBefore("#") }.distinct()
                    val styleClass = when(layer) {
                        ArchitecturalLayer.ENTRY_POINTS -> "entry"
                        ArchitecturalLayer.SERVICES -> "service"
                        ArchitecturalLayer.STORAGE -> "storage"
                        ArchitecturalLayer.UTILITIES -> "solo"
                    }
                    for (f in files.take(8)) {
                        val safeId = f.replace(Regex("[^a-zA-Z0-9_]"), "_")
                        sb.append("        $safeId[\"$f\"]:::$styleClass\n")
                    }
                    sb.append("    end\n\n")
                }

                for (e in fileEdges.take(100)) {
                    val src = e.source.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val tgt = e.target.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    sb.append("    $src -->|${e.relation} ${e.weight}x| $tgt\n")
                }
                sb.toString()
            }
        }
    }

    private fun exportFileLevel(edges: List<Edge>, format: String): String {
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)
        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph FileWiringGraph {\n    rankdir=LR;\n    node [shape=box, style=\"filled,rounded\", fillcolor=\"#E9ECEF\", fontname=\"Helvetica\"];\n")
                for (e in fileEdges) {
                    sb.append("    \"${e.source}\" -> \"${e.target}\" [label=\"${e.relation} (${e.weight})\"];\n")
                }
                sb.append("}\n").toString()
            }
            "json" -> {
                val nodes = fileEdges.flatMap { listOf(it.source, it.target) }.distinct().map {
                    """    { "id": "$it", "type": "FILE" }"""
                }
                val links = fileEdges.map {
                    """    { "source": "${it.source}", "target": "${it.target}", "relation": "${it.relation}", "weight": ${it.weight} }"""
                }
                "{\n  \"nodes\": [\n${nodes.joinToString(",\n")}\n  ],\n  \"links\": [\n${links.joinToString(",\n")}\n  ]\n}"
            }
            else -> {
                val sb = StringBuilder("graph LR\n")
                for (e in fileEdges) {
                    val src = e.source.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val tgt = e.target.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    sb.append("    $src[\"${e.source}\"] -->|${e.relation} ${e.weight}x| $tgt[\"${e.target}\"]\n")
                }
                sb.toString()
            }
        }
    }

    private fun exportPackageLevel(symbols: List<Symbol>, edges: List<Edge>, format: String): String {
        val pkgEdges = ArchitectureFlowAnalyzer.aggregateByPackage(edges, symbols)
        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph PackageWiringGraph {\n    rankdir=LR;\n    node [shape=box, style=\"filled,rounded\", fillcolor=\"#D8F3DC\", fontname=\"Helvetica\"];\n")
                for (e in pkgEdges) {
                    sb.append("    \"${e.source}\" -> \"${e.target}\" [label=\"${e.relation} (${e.weight})\"];\n")
                }
                sb.append("}\n").toString()
            }
            "json" -> {
                val nodes = pkgEdges.flatMap { listOf(it.source, it.target) }.distinct().map {
                    """    { "id": "$it", "type": "PACKAGE" }"""
                }
                val links = pkgEdges.map {
                    """    { "source": "${it.source}", "target": "${it.target}", "relation": "${it.relation}", "weight": ${it.weight} }"""
                }
                "{\n  \"nodes\": [\n${nodes.joinToString(",\n")}\n  ],\n  \"links\": [\n${links.joinToString(",\n")}\n  ]\n}"
            }
            else -> {
                val sb = StringBuilder("graph LR\n")
                for (e in pkgEdges) {
                    val src = e.source.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val tgt = e.target.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    sb.append("    $src[\"${e.source}\"] -->|${e.relation} ${e.weight}x| $tgt[\"${e.target}\"]\n")
                }
                sb.toString()
            }
        }
    }

    private fun exportSymbolLevel(symbols: List<Symbol>, edges: List<Edge>, format: String): String {
        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph SymbolWiringGraph {\n    rankdir=LR;\n    node [shape=box, fontname=\"Helvetica\"];\n")
                for (e in edges.take(200)) {
                    val src = e.sourceId.substringAfterLast("/").substringAfterLast("\\").replace("\"", "\\\"")
                    val tgt = e.targetId.substringAfterLast("/").substringAfterLast("\\").replace("\"", "\\\"")
                    sb.append("    \"$src\" -> \"$tgt\" [label=\"${e.relation.name}\"];\n")
                }
                sb.append("}\n").toString()
            }
            "json" -> {
                val nodes = symbols.map {
                    """    { "id": "${it.id.replace("\"", "\\\"")}", "name": "${it.name}", "type": "${it.type.name}" }"""
                }
                val links = edges.take(250).map {
                    """    { "source": "${it.sourceId.replace("\"", "\\\"")}", "target": "${it.targetId.replace("\"", "\\\"")}", "relation": "${it.relation.name}" }"""
                }
                "{\n  \"nodes\": [\n${nodes.joinToString(",\n")}\n  ],\n  \"links\": [\n${links.joinToString(",\n")}\n  ]\n}"
            }
            else -> {
                val sb = StringBuilder("graph TD\n")
                for (e in edges.take(200)) {
                    val rawSrc = e.sourceId.substringAfterLast("/").substringAfterLast("\\")
                    val rawTgt = e.targetId.substringAfterLast("/").substringAfterLast("\\")
                    val src = rawSrc.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val tgt = rawTgt.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    sb.append("    $src[\"$rawSrc\"] -->|${e.relation.name}| $tgt[\"$rawTgt\"]\n")
                }
                sb.toString()
            }
        }
    }

    private fun exportJsonNodesAndEdges(
        nodes: List<dev.ajithgoveas.kindex.core.analysis.ComponentNode>,
        fileEdges: List<dev.ajithgoveas.kindex.core.analysis.AggregatedEdge>
    ): String {
        val nodesJson = nodes.map {
            """    { "id": "${it.id.replace("\"", "\\\"")}", "name": "${it.name}", "layer": "${it.layer.name}", "type": "${it.symbolType}" }"""
        }
        val linksJson = fileEdges.map {
            """    { "source": "${it.source}", "target": "${it.target}", "relation": "${it.relation}", "weight": ${it.weight} }"""
        }
        return "{\n  \"nodes\": [\n${nodesJson.joinToString(",\n")}\n  ],\n  \"links\": [\n${linksJson.joinToString(",\n")}\n  ]\n}"
    }
}
