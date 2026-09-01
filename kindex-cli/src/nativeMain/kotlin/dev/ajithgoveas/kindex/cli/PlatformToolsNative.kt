@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.ajithgoveas.kindex.cli

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.chmod

actual fun makeExecutable(path: String) {
    try {
        chmod(path, 493)
    } catch (_: Throwable) {
    }
}
