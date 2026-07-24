package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.storage.IndexStorage
import java.io.File

class DepsCommand : CliktCommand(
    name = "deps",
    help = "Show dependencies or incoming imports for a given symbol or file"
) {
    private val target by argument(help = "Symbol name or File path to inspect")
    private val directory by option("-d", "--dir", help = "Project directory").file().default(File("."))

    override fun run() {
        val t = Terminal()
        val dbFile = File(directory, ".kindex/index.db")
        if (!dbFile.exists()) {
            t.println(red("No index found. Run 'kindex scan ${directory.path}' first."))
            return
        }

        val storage = IndexStorage(dbFile)
        val incoming = storage.getIncomingDependencies(target)
        val outgoing = storage.getOutgoingDependencies(target)

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            t.println(yellow("No dependency edges found for '$target'"))
            return
        }

        if (outgoing.isNotEmpty()) {
            t.println(bold("\nOutgoing Dependencies (What '$target' relies on):\n"))
            t.println(table {
                header { row("Relation", "Target Symbol / File") }
                body {
                    outgoing.forEach { edge ->
                        row(edge.relation.name, edge.targetId)
                    }
                }
            })
        }

        if (incoming.isNotEmpty()) {
            t.println(bold("\nIncoming Dependents (Who uses / imports '$target'):\n"))
            t.println(table {
                header { row("Relation", "Source Symbol / File") }
                body {
                    incoming.forEach { edge ->
                        row(edge.relation.name, edge.sourceId)
                    }
                }
            })
        }
    }
}
