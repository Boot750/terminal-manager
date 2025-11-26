package org.nanoya.terminalmanager.settings

import java.io.File

data class ShellInfo(
    val id: String,
    val displayName: String,
    val command: List<String>
) {
    override fun toString(): String = displayName
}

object ShellDetector {
    private var cachedShells: List<ShellInfo>? = null

    fun getAvailableShells(): List<ShellInfo> {
        cachedShells?.let { return it }

        val shells = mutableListOf<ShellInfo>()

        // Always add "Default" option
        shells.add(ShellInfo("default", "Default (IDE Setting)", emptyList()))

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        if (isWindows) {
            detectWindowsShells(shells)
        } else {
            detectUnixShells(shells)
        }

        cachedShells = shells
        return shells
    }

    private fun detectWindowsShells(shells: MutableList<ShellInfo>) {
        val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val system32 = "$systemRoot\\System32"

        // Command Prompt (check it exists)
        val cmdPath = "$system32\\cmd.exe"
        if (File(cmdPath).exists()) {
            shells.add(ShellInfo("cmd", "Command Prompt", listOf(cmdPath)))
        }

        // PowerShell (Windows PowerShell 5.x)
        val powershellPath = "$system32\\WindowsPowerShell\\v1.0\\powershell.exe"
        if (File(powershellPath).exists()) {
            shells.add(ShellInfo("powershell", "Windows PowerShell", listOf(powershellPath)))
        }

        // PowerShell Core / PowerShell 7+ (pwsh)
        val pwshPaths = listOf(
            "C:\\Program Files\\PowerShell\\7\\pwsh.exe",
            "C:\\Program Files\\PowerShell\\pwsh.exe"
        )
        pwshPaths.firstOrNull { File(it).exists() }?.let { path ->
            shells.add(ShellInfo("pwsh", "PowerShell 7", listOf(path)))
        }

        // Git Bash
        val gitBashPaths = listOf(
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
            System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Git\\bin\\bash.exe" }
        ).filterNotNull()
        gitBashPaths.firstOrNull { File(it).exists() }?.let { path ->
            shells.add(ShellInfo("gitbash", "Git Bash", listOf(path)))
        }

        // Cygwin
        val cygwinPaths = listOf(
            "C:\\cygwin64\\bin\\bash.exe",
            "C:\\cygwin\\bin\\bash.exe"
        )
        cygwinPaths.firstOrNull { File(it).exists() }?.let { path ->
            shells.add(ShellInfo("cygwin", "Cygwin Bash", listOf(path, "--login", "-i")))
        }

        // WSL distributions
        detectWslDistributions(shells)
    }

    private fun detectWslDistributions(shells: MutableList<ShellInfo>) {
        try {
            val process = ProcessBuilder("wsl.exe", "--list", "--quiet")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) {
                val distros = output.lines()
                    .map { it.trim().replace("\u0000", "") } // Remove null chars from UTF-16
                    .filter { it.isNotBlank() }

                if (distros.isNotEmpty()) {
                    // Add default WSL
                    shells.add(ShellInfo("wsl", "WSL (Default)", listOf("wsl.exe")))

                    // Add each distribution
                    distros.forEach { distro ->
                        shells.add(ShellInfo(
                            "wsl-$distro",
                            "WSL: $distro",
                            listOf("wsl.exe", "-d", distro)
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            // WSL not available
        }
    }

    private fun detectUnixShells(shells: MutableList<ShellInfo>) {
        val commonShells = mapOf(
            "/bin/bash" to "Bash",
            "/bin/zsh" to "Zsh",
            "/bin/fish" to "Fish",
            "/bin/sh" to "sh"
        )

        commonShells.forEach { (path, name) ->
            if (File(path).exists()) {
                shells.add(ShellInfo(name.lowercase(), name, listOf(path)))
            }
        }

        // Also check /usr/bin
        listOf("bash", "zsh", "fish").forEach { shell ->
            val path = "/usr/bin/$shell"
            if (File(path).exists() && shells.none { it.id == shell }) {
                shells.add(ShellInfo(shell, shell.replaceFirstChar { it.uppercase() }, listOf(path)))
            }
        }
    }

    fun refreshCache() {
        cachedShells = null
    }
}

data class TerminalTabConfig(
    var name: String = "Terminal",
    var shellId: String = "default",
    var workingDirectory: String = "",
    var enabled: Boolean = true
) {
    fun copy(): TerminalTabConfig = TerminalTabConfig(name, shellId, workingDirectory, enabled)

    fun getShellInfo(): ShellInfo? {
        return ShellDetector.getAvailableShells().find { it.id == shellId }
    }
}
