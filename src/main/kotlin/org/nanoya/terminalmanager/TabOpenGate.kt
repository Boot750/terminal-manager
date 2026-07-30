package org.nanoya.terminalmanager

import java.io.File

object TabOpenGate {
    /**
     * Returns true if a tab with the given [openIf] should open. Blank/"." always opens.
     * Relative paths resolve against [projectBasePath]; absolute paths are used as-is.
     * If [projectBasePath] is null, a relative path cannot be resolved so the tab opens.
     */
    fun shouldOpen(openIf: String, projectBasePath: String?): Boolean {
        if (openIf.isBlank() || openIf == ".") return true
        val f = File(openIf)
        if (f.isAbsolute) return f.exists()
        val base = projectBasePath ?: return true
        return File(base, openIf).exists()
    }
}
