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
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.core.io.MPFile
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class ExportCommand : CliktCommand(name = "export", help = "Export knowledge graph into clean, file-centric architectural flow diagrams (Mermaid, DOT, JSON)") {
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
        val currentWorkspaceDir = MPFile(".")
        val rootDir = dev.ajithgoveas.kindex.core.io.RepositoryRootResolver.findRepositoryRoot(currentWorkspaceDir)
        val targetDir = if (directory == "." || MPFile(directory).absolutePath == currentWorkspaceDir.absolutePath) {
            rootDir
        } else {
            MPFile(directory)
        }
        try {
            dev.ajithgoveas.kindex.core.io.RepositoryGuardrail.assertWithinRepository(targetDir, rootDir)
            outputOpt?.let { 
                val cleanPath = it.replace('\\', '/')
                val outFile = if (cleanPath.startsWith("/") || cleanPath.contains(":")) MPFile(cleanPath) else MPFile("${rootDir.absolutePath}/$cleanPath")
                dev.ajithgoveas.kindex.core.io.RepositoryGuardrail.assertWithinRepository(outFile, rootDir) 
            }
        } catch (e: dev.ajithgoveas.kindex.core.io.RepositoryBoundaryException) {
            t.println(red(e.message ?: "Security Boundary Error"))
            return
        }
        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
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

        // Filter out noisy string literal targets or CONTAINS edges in high level flow exports
        rawEdges = rawEdges.filter { edge ->
            !edge.targetId.contains(" = \"") &&
            !edge.targetId.contains("help =") &&
            !edge.sourceId.contains(" = \"")
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
            "dot", "graphviz" -> "${rootDir.path}/.kindex/graph.dot"
            "json" -> "${rootDir.path}/.kindex/graph.json"
            else -> "${rootDir.path}/.kindex/graph.mmd"
        }
        val targetOutputFile = outputOpt ?: defaultFile

        val content = when (granularity.lowercase()) {
            "file" -> exportFileLevel(rawEdges, targetFormat)
            "package", "module" -> exportPackageLevel(rawSymbols, rawEdges, targetFormat)
            "symbol" -> exportSymbolLevel(rawSymbols, rawEdges, targetFormat)
            else -> exportHierarchicalFlow(rawSymbols, rawEdges, targetFormat)
        }

        try {
            FileSystem.SYSTEM.write(targetOutputFile.toPath()) {
                writeUtf8(content)
            }
            t.println(green("✓ Successfully exported ${granularity.uppercase()} graph (${targetFormat.uppercase()}) to $targetOutputFile"))
        } catch (e: Exception) {
            t.println(red("Error writing export file: ${e.message}"))
        }
    }

    private fun exportHierarchicalFlow(symbols: List<Symbol>, edges: List<Edge>, format: String): String {
        val flowEdges = edges.filter { it.relation != RelationType.CONTAINS }
        val classifiedNodes = ArchitectureFlowAnalyzer.classifyNodes(symbols, flowEdges)
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(flowEdges)

        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph KIndexFlowGraph {\n")
                sb.append("    rankdir=TB;\n")
                sb.append("    compound=true;\n")
                sb.append("    node [shape=box, style=\"filled,rounded\", fontname=\"Helvetica\", color=\"#495057\", fillcolor=\"#F8F9FA\"];\n")
                sb.append("    edge [fontname=\"Helvetica\", fontsize=9, color=\"#6C757D\"];\n\n")

                val layerGroups = classifiedNodes.groupBy { it.layer }
                for (layer in ArchitecturalLayer.values()) {
                    val nodes = layerGroups[layer] ?: continue
                    sb.append("    subgraph cluster_${layer.name} {\n")
                    sb.append("        label=\"${layer.emoji} ${layer.displayName}\";\n")
                    sb.append("        style=dashed; color=\"#6C757D\";\n")
                    val fileNames = nodes.map { cleanDisplayName(it.id) }.distinct()
                    for (f in fileNames.take(8)) {
                        val safeId = toMermaidSafeId(f)
                        sb.append("        \"$safeId\" [label=\"$f\"];\n")
                    }
                    sb.append("    }\n\n")
                }

                val seenEdges = mutableSetOf<String>()
                for (e in fileEdges.take(40)) {
                    val srcName = cleanDisplayName(e.source)
                    val tgtName = cleanDisplayName(e.target)
                    val src = toMermaidSafeId(srcName)
                    val tgt = toMermaidSafeId(tgtName)
                    if (src != tgt) {
                        val line = "    \"$src\" -> \"$tgt\" [label=\"${e.relation}\"];\n"
                        if (seenEdges.add(line)) sb.append(line)
                    }
                }
                sb.append("}\n").toString()
            }
            "json" -> exportJsonNodesAndEdges(classifiedNodes, fileEdges)
            else -> {
                val sb = StringBuilder("graph TD\n")
                sb.append("    classDef entry fill:#457b9d,color:#fff,stroke:#1d3557,stroke-width:2px;\n")
                sb.append("    classDef service fill:#2a9d8f,color:#fff,stroke:#264653,stroke-width:2px;\n")
                sb.append("    classDef storage fill:#e76f51,color:#fff,stroke:#b7094c,stroke-width:2px;\n")
                sb.append("    classDef solo fill:#e9ecef,color:#212529,stroke:#ced4da,stroke-width:1px,stroke-dasharray: 5 5;\n\n")

                val layerGroups = classifiedNodes.groupBy { it.layer }
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
                sb.toString()
            }
        }
    }

    private fun exportFileLevel(edges: List<Edge>, format: String): String {
        val fileEdges = ArchitectureFlowAnalyzer.aggregateByFile(edges)
        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph FileWiringGraph {\n    rankdir=LR;\n    node [shape=box, style=\"filled,rounded\", fillcolor=\"#E9ECEF\", fontname=\"Helvetica\"];\n")
                val seen = mutableSetOf<String>()
                for (e in fileEdges.take(50)) {
                    val src = cleanDisplayName(e.source)
                    val tgt = cleanDisplayName(e.target)
                    val line = "    \"$src\" -> \"$tgt\" [label=\"${e.relation} (${e.weight})\"];\n"
                    if (seen.add(line)) sb.append(line)
                }
                sb.append("}\n").toString()
            }
            "json" -> {
                val nodes = fileEdges.flatMap { listOf(cleanDisplayName(it.source), cleanDisplayName(it.target)) }.distinct().map {
                    """    { "id": "$it", "type": "FILE" }"""
                }
                val links = fileEdges.map {
                    """    { "source": "${cleanDisplayName(it.source)}", "target": "${cleanDisplayName(it.target)}", "relation": "${it.relation}", "weight": ${it.weight} }"""
                }
                "{\n  \"nodes\": [\n${nodes.joinToString(",\n")}\n  ],\n  \"links\": [\n${links.joinToString(",\n")}\n  ]\n}"
            }
            else -> {
                val sb = StringBuilder("graph LR\n")
                val seen = mutableSetOf<String>()
                for (e in fileEdges.take(50)) {
                    val srcName = cleanDisplayName(e.source)
                    val tgtName = cleanDisplayName(e.target)
                    val src = toMermaidSafeId(srcName)
                    val tgt = toMermaidSafeId(tgtName)
                    val line = "    $src[\"$srcName\"] -->|${e.relation} ${e.weight}x| $tgt[\"$tgtName\"]\n"
                    if (seen.add(line)) sb.append(line)
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
                val seen = mutableSetOf<String>()
                for (e in pkgEdges) {
                    val line = "    \"${e.source}\" -> \"${e.target}\" [label=\"${e.relation} (${e.weight})\"];\n"
                    if (seen.add(line)) sb.append(line)
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
                val seen = mutableSetOf<String>()
                for (e in pkgEdges) {
                    val src = toMermaidSafeId(e.source)
                    val tgt = toMermaidSafeId(e.target)
                    val line = "    $src[\"${e.source}\"] -->|${e.relation} ${e.weight}x| $tgt[\"${e.target}\"]\n"
                    if (seen.add(line)) sb.append(line)
                }
                sb.toString()
            }
        }
    }

    private fun exportSymbolLevel(symbols: List<Symbol>, edges: List<Edge>, format: String): String {
        val cleanEdges = edges.filter { it.relation != RelationType.CONTAINS }
        return when (format) {
            "dot", "graphviz" -> {
                val sb = StringBuilder("digraph SymbolWiringGraph {\n    rankdir=LR;\n    node [shape=box, fontname=\"Helvetica\"];\n")
                val seen = mutableSetOf<String>()
                for (e in cleanEdges.take(60)) {
                    val src = cleanDisplayName(e.sourceId)
                    val tgt = cleanDisplayName(e.targetId)
                    val line = "    \"$src\" -> \"$tgt\" [label=\"${e.relation.name}\"];\n"
                    if (seen.add(line)) sb.append(line)
                }
                sb.append("}\n").toString()
            }
            "json" -> {
                val nodes = symbols.map {
                    """    { "id": "${cleanDisplayName(it.id)}", "name": "${it.name}", "type": "${it.type.name}" }"""
                }
                val links = cleanEdges.take(100).map {
                    """    { "source": "${cleanDisplayName(it.sourceId)}", "target": "${cleanDisplayName(it.targetId)}", "relation": "${it.relation.name}" }"""
                }
                "{\n  \"nodes\": [\n${nodes.joinToString(",\n")}\n  ],\n  \"links\": [\n${links.joinToString(",\n")}\n  ]\n}"
            }
            else -> {
                val sb = StringBuilder("graph TD\n")
                val seen = mutableSetOf<String>()
                for (e in cleanEdges.take(60)) {
                    val rawSrc = cleanDisplayName(e.sourceId)
                    val rawTgt = cleanDisplayName(e.targetId)
                    val src = toMermaidSafeId(rawSrc)
                    val tgt = toMermaidSafeId(rawTgt)
                    val line = "    $src[\"$rawSrc\"] -->|${e.relation.name}| $tgt[\"$rawTgt\"]\n"
                    if (seen.add(line)) sb.append(line)
                }
                sb.toString()
            }
        }
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

    private fun exportJsonNodesAndEdges(
        nodes: List<dev.ajithgoveas.kindex.core.analysis.ComponentNode>,
        fileEdges: List<dev.ajithgoveas.kindex.core.analysis.AggregatedEdge>
    ): String {
        val nodesJson = nodes.map {
            """    { "id": "${cleanDisplayName(it.id)}", "name": "${it.name}", "layer": "${it.layer.name}", "type": "${it.symbolType}" }"""
        }
        val linksJson = fileEdges.map {
            """    { "source": "${cleanDisplayName(it.source)}", "target": "${cleanDisplayName(it.target)}", "relation": "${it.relation}", "weight": ${it.weight} }"""
        }
        return "{\n  \"nodes\": [\n${nodesJson.joinToString(",\n")}\n  ],\n  \"links\": [\n${linksJson.joinToString(",\n")}\n  ]\n}"
    }
}
