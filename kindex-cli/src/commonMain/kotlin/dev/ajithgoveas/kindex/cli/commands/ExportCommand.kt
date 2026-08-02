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
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.core.io.MPFile
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class ExportCommand : CliktCommand(name = "export", help = "Export knowledge graph to Mermaid, Graphviz DOT, or JSON format") {
    private val format by option("-f", "--format", help = "Export format: mermaid, dot, json").default("mermaid")
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
        val edges = storage.getAllEdges()

        if (edges.isEmpty()) {
            t.println(yellow("Warning: Knowledge graph has no dependency edges to export."))
            return
        }

        val targetFormat = format.lowercase()
        val defaultFile = when (targetFormat) {
            "dot", "graphviz" -> "graph.dot"
            "json" -> "graph.json"
            else -> "graph.mmd"
        }
        val targetOutputFile = outputOpt ?: defaultFile

        val content = when (targetFormat) {
            "mermaid" -> exportMermaid(edges)
            "dot", "graphviz" -> exportDot(edges)
            "json" -> exportJson(storage, edges)
            else -> {
                t.println(red("Unsupported format '$format'. Supported formats: mermaid, dot, json"))
                return
            }
        }

        try {
            FileSystem.SYSTEM.write(targetOutputFile.toPath()) {
                writeUtf8(content)
            }
            t.println(green("✓ Successfully exported ${targetFormat.uppercase()} graph (${edges.size} edges) to $targetOutputFile"))
        } catch (e: Exception) {
            t.println(red("Error writing export file: ${e.message}"))
        }
    }

    private fun exportMermaid(edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val sb = StringBuilder("graph TD\n")
        val sanitizedEdges = mutableSetOf<String>()

        for (edge in edges.take(250)) {
            val rawSource = edge.sourceId.substringAfterLast("/").substringAfterLast("\\")
            val rawTarget = edge.targetId.substringAfterLast("/").substringAfterLast("\\")

            val sourceId = rawSource.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val targetId = rawTarget.replace(Regex("[^a-zA-Z0-9_]"), "_")

            val sourceLabel = rawSource.replace("\"", "\\\"")
            val targetLabel = rawTarget.replace("\"", "\\\"")

            val edgeLine = "    $sourceId[\"$sourceLabel\"] -->|${edge.relation.name}| $targetId[\"$targetLabel\"]\n"
            if (sanitizedEdges.add(edgeLine)) {
                sb.append(edgeLine)
            }
        }
        return sb.toString()
    }

    private fun exportDot(edges: List<dev.ajithgoveas.kindex.core.model.Edge>): String {
        val sb = StringBuilder()
        sb.append("digraph KIndexGraph {\n")
        sb.append("    rankdir=LR;\n")
        sb.append("    node [shape=box, style=\"filled,rounded\", fillcolor=\"#F8F9FA\", fontname=\"Helvetica\", color=\"#495057\"];\n")
        sb.append("    edge [fontname=\"Helvetica\", fontsize=10, color=\"#6C757D\"];\n\n")

        val seen = mutableSetOf<String>()
        for (edge in edges.take(250)) {
            val rawSource = edge.sourceId.substringAfterLast("/").substringAfterLast("\\")
            val rawTarget = edge.targetId.substringAfterLast("/").substringAfterLast("\\")

            val sourceId = rawSource.replace("\"", "\\\"")
            val targetId = rawTarget.replace("\"", "\\\"")

            val line = "    \"$sourceId\" -> \"$targetId\" [label=\"${edge.relation.name}\"];\n"
            if (seen.add(line)) {
                sb.append(line)
            }
        }
        sb.append("}\n")
        return sb.toString()
    }

    private fun exportJson(
        storage: IndexStorage,
        edges: List<dev.ajithgoveas.kindex.core.model.Edge>
    ): String {
        val symbols = storage.getAllSymbols()
        val symbolMap = symbols.associateBy { it.id }

        val nodeIds = mutableSetOf<String>()
        edges.forEach {
            nodeIds.add(it.sourceId)
            nodeIds.add(it.targetId)
        }

        val nodesList = nodeIds.map { id ->
            val sym = symbolMap[id]
            val name = (sym?.name ?: id.substringAfterLast("/").substringAfterLast("\\")).replace("\"", "\\\"")
            val type = sym?.type?.name ?: "FILE"
            val pkg = (sym?.packageName ?: "").replace("\"", "\\\"")
            """    { "id": "${id.replace("\"", "\\\"")}", "name": "$name", "type": "$type", "packageName": "$pkg" }"""
        }

        val linksList = edges.take(300).map { edge ->
            """    { "source": "${edge.sourceId.replace("\"", "\\\"")}", "target": "${edge.targetId.replace("\"", "\\\"")}", "relation": "${edge.relation.name}" }"""
        }

        return """
{
  "nodes": [
${nodesList.joinToString(",\n")}
  ],
  "links": [
${linksList.joinToString(",\n")}
  ]
}
""".trimIndent()
    }
}
