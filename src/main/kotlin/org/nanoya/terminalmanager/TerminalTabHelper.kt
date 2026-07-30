package org.nanoya.terminalmanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.nanoya.terminalmanager.settings.ShellDetector
import org.nanoya.terminalmanager.settings.TerminalManagerAppSettings
import org.nanoya.terminalmanager.settings.TerminalTabConfig
import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

object TerminalTabHelper {

    fun createTerminalTab(
        terminalManager: TerminalToolWindowManager,
        tabConfig: TerminalTabConfig,
        project: Project,
        canRunCommands: Boolean
    ) {
        val workingDir = resolveWorkingDirectory(tabConfig.workingDirectory, project)
        val shellInfo = tabConfig.getShellInfo()

        val baseCommand: List<String>? =
            if (shellInfo != null && shellInfo.id != "default" && shellInfo.command.isNotEmpty())
                shellInfo.command else null

        val appSettings = TerminalManagerAppSettings.getInstance()
        val tmuxBinary = appSettings.tmuxBinary
        val useTmux = tabConfig.useTmux && ShellDetector.isTmuxAvailable(tmuxBinary)
        val runStartupCommand = canRunCommands && tabConfig.startupCommand.isNotBlank()

        // When a tab requests tmux, warn the user if tmux can't be used (not found / not
        // actually tmux) — otherwise the failure is a silent fallback to a normal terminal.
        if (tabConfig.useTmux && ShellDetector.isTmuxPlatform()) {
            TmuxSupport.notifyIfBinaryUnusable(project, tmuxBinary)
        }

        val sessionName = TmuxSupport.sessionName(tabConfig.name, project)
        val shellCommand: List<String>? =
            if (useTmux)
                TmuxSupport.buildCommand(
                    tmuxBinary,
                    appSettings.tmuxCommand,
                    sessionName,
                    workingDir,
                    baseCommand,
                    if (runStartupCommand) tabConfig.startupCommand else null
                )
            else
                baseCommand

        val tabState = TerminalTabState().apply {
            myTabName = tabConfig.name
            myWorkingDirectory = workingDir
            if (shellCommand != null) {
                myShellCommand = shellCommand
            }
        }

        terminalManager.createNewSession(terminalManager.terminalRunner, tabState)

        applyTabColor(terminalManager, tabConfig)

        // A valid binary can still be paired with a command tmux rejects, which aborts
        // before a terminal starts. Verify the tab actually attached and warn if it didn't.
        if (useTmux) {
            TmuxSupport.verifyLaunch(project, tmuxBinary, appSettings.tmuxCommand, sessionName, tabConfig.name)
        }

        // Env injection uses the fallback (prepended shell commands) because TerminalTabState
        // exposes no env field on this creation path. Shares the trust gate with startup commands.
        // For tmux tabs the startup command is embedded in the session and runs only when the
        // session is created (see TmuxSupport.buildCommand), so it isn't re-run on reattach —
        // only the env commands (idempotent) are typed into the terminal there.
        if (canRunCommands) {
            val envCommands = buildEnvCommands(tabConfig.env, tabConfig.shellId)
            val commands = envCommands +
                listOfNotNull(tabConfig.startupCommand.takeIf { !useTmux && it.isNotBlank() })
            if (commands.isNotEmpty()) {
                executeStartupCommand(terminalManager, commands.joinToString(" && "), tabConfig.name)
            }
        }
    }

    /** Builds shell-appropriate env-setting commands for the fallback injection path. */
    fun buildEnvCommands(env: Map<String, String>, shellId: String): List<String> {
        if (env.isEmpty()) return emptyList()
        val lower = shellId.lowercase()
        return env.map { (k, v) ->
            when {
                lower == "cmd" -> "set \"$k=$v\""
                lower.startsWith("powershell") || lower == "pwsh" -> "\$env:$k=\"$v\""
                else -> "export $k=$v" // bash/zsh/wsl/gitbash/cygwin/default
            }
        }
    }

    fun closeAllTerminalTabs(contentManager: ContentManager) {
        val contents = contentManager.contents.toList()
        contents.forEach { content ->
            // Suppress the terminal's "Process 'X' Is Running" close confirmation. The user
            // explicitly opted into closing existing tabs, and the modal would otherwise
            // block startup for every tab whose shell reports (or fails to report) running
            // child processes. Same mechanism as the terminal plugin's own
            // TerminalTabCloseListener.executeContentOperationSilently.
            content.putUserData(Content.TEMPORARY_REMOVED_KEY, true)
            try {
                contentManager.removeContent(content, true)
            } finally {
                content.putUserData(Content.TEMPORARY_REMOVED_KEY, null)
            }
        }
    }

    private fun applyTabColor(terminalManager: TerminalToolWindowManager, tabConfig: TerminalTabConfig) {
        val tabColor = tabConfig.getTabColor()
        val awtColor = tabColor.awtColor ?: return

        // Delay to allow createNewSession() to register the tab in the content manager
        Timer().schedule(500) {
            ApplicationManager.getApplication().invokeLater {
                val toolWindow = terminalManager.toolWindow ?: return@invokeLater
                val contentManager = toolWindow.contentManager
                val content = contentManager.contents.find { it.displayName == tabConfig.name }
                content?.setTabColor(awtColor)
            }
        }
    }

    private fun executeStartupCommand(
        terminalManager: TerminalToolWindowManager,
        command: String,
        tabName: String
    ) {
        // Wait for terminal to initialize before sending command
        Timer().schedule(1000) {
            ApplicationManager.getApplication().invokeLater {
                try {
                    val toolWindow = terminalManager.toolWindow ?: return@invokeLater
                    val contentManager = toolWindow.contentManager
                    val content = contentManager.contents.find { it.displayName == tabName }

                    content?.let {
                        val widget = TerminalToolWindowManager.getWidgetByContent(it)
                        if (widget is ShellTerminalWidget) {
                            widget.executeCommand(command)
                        }
                    }
                } catch (e: Exception) {
                    // Silently ignore errors - command execution is best-effort
                }
            }
        }
    }

    private fun resolveWorkingDirectory(configuredDir: String, project: Project): String {
        val projectPath = project.basePath ?: System.getProperty("user.home")

        if (configuredDir.isBlank() || configuredDir == ".") {
            return projectPath
        }

        val file = File(configuredDir)
        if (file.isAbsolute) {
            return if (file.exists() && file.isDirectory) {
                configuredDir
            } else {
                projectPath
            }
        }

        val resolved = File(projectPath, configuredDir)
        return if (resolved.exists() && resolved.isDirectory) {
            resolved.absolutePath
        } else {
            projectPath
        }
    }
}
