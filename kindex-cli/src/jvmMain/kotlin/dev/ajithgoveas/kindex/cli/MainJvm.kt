package dev.ajithgoveas.kindex.cli

import com.github.ajalt.clikt.core.CliktCommand
import dev.ajithgoveas.kindex.cli.commands.InteractiveCommand

actual fun getInteractiveCommand(): CliktCommand? = InteractiveCommand()
