package dev.ajithgoveas.kindex.cli

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

actual fun makeExecutable(path: String) {
    try {
        val p = Paths.get(path)
        val view = Files.getFileAttributeView(p, PosixFileAttributeView::class.java) ?: return
        val perms = Files.getPosixFilePermissions(p).toMutableSet()
        perms.add(PosixFilePermission.OWNER_EXECUTE)
        perms.add(PosixFilePermission.GROUP_EXECUTE)
        perms.add(PosixFilePermission.OTHERS_EXECUTE)
        Files.setPosixFilePermissions(p, perms)
    } catch (_: Throwable) {
    }
}
