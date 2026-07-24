package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.storage.IndexStorage
import java.io.File

class ExportCommand : CliktCommand(name = "export", help = "Export the knowledge graph to Mermaid format") {
    private val format by option("-f", "--format", help = "Format: mermaid").default("mermaid")
    private val outputFile by option("-o", "--output", help = "Output file path").file().default(File("graph.mmd"))
    private val directory by option("-d", "--dir", help = "Project directory").file().default(File("."))

    override fun run() {
        val t = Terminal()
        val dbFile = File(directory, ".kindex/index.db")
        if (!dbFile.exists()) {
            t.println(red("No index found. Run 'kindex scan ${directory.path}' first."))
            return
        }

        val storage = IndexStorage(dbFile)
        val edges = storage.getAllEdges()

        if (format.lowercase() == "mermaid") {
            val mermaidContent = StringBuilder("graph TD\n")
            val sanitizedEdges = mutableSetOf<String>() // Prevent duplicate lines in Mermaid output

            for (edge in edges.take(200)) {
                // Sanitize node IDs to contain only alphanumeric characters and underscores
                val rawSource = edge.sourceId.substringAfterLast("/").substringAfterLast("\\")
                val rawTarget = edge.targetId.substringAfterLast("/").substringAfterLast("\\")

                val sourceId = rawSource.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val targetId = rawTarget.replace(Regex("[^a-zA-Z0-9_]"), "_")

                // Keep user-friendly labels to show the original symbol/file name
                val sourceLabel = rawSource.replace("\"", "\\\"")
                val targetLabel = rawTarget.replace("\"", "\\\"")

                val edgeLine = "    $sourceId[\"$sourceLabel\"] -->|${edge.relation}| $targetId[\"$targetLabel\"]\n"
                if (sanitizedEdges.add(edgeLine)) {
                    mermaidContent.append(edgeLine)
                }
            }
            outputFile.writeText(mermaidContent.toString())
            t.println(green("✓ Exported Mermaid graph to ${outputFile.path}"))
        }
    }
}
