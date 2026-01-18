package org.nanoya.terminalmanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.nanoya.terminalmanager.settings.TerminalManagerSettings
import org.nanoya.terminalmanager.settings.TerminalTabConfig
import org.nanoya.terminalmanager.settings.TrustedProjectsSettings
import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

class TerminalStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = TerminalManagerSettings.getInstance(project)

        if (!settings.enabled || settings.tabs.isEmpty()) {
            return
        }

        val enabledTabs = settings.tabs.filter { it.enabled }
        if (enabledTabs.isEmpty()) {
            return
        }

        // Check if project is trusted for running startup commands
        val trustedSettings = TrustedProjectsSettings.getInstance()
        val canRunCommands = trustedSettings.isProjectTrusted(project)

        ApplicationManager.getApplication().invokeLater {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalToolWindow = toolWindowManager.getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

            terminalToolWindow?.let { toolWindow ->
                toolWindow.activate {
                    val terminalManager = TerminalToolWindowManager.getInstance(project)

                    // Close existing terminals if option is enabled
                    if (settings.closeExistingTerminals) {
                        closeAllTerminalTabs(toolWindow.contentManager)
                    }

                    enabledTabs.forEach { tabConfig ->
                        createTerminalTab(terminalManager, tabConfig, project, canRunCommands)
                    }
                }
            }
        }
    }

    private fun closeAllTerminalTabs(contentManager: ContentManager) {
        val contents = contentManager.contents.toList()
        contents.forEach { content ->
            contentManager.removeContent(content, true)
        }
    }

    private fun createTerminalTab(
        terminalManager: TerminalToolWindowManager,
        tabConfig: TerminalTabConfig,
        project: Project,
        canRunCommands: Boolean
    ) {
        val workingDir = resolveWorkingDirectory(tabConfig.workingDirectory, project)
        val shellInfo = tabConfig.getShellInfo()

        // Build TerminalTabState with shell command and working directory
        val tabState = TerminalTabState().apply {
            myTabName = tabConfig.name
            myWorkingDirectory = workingDir
            // Set shell command if not default
            if (shellInfo != null && shellInfo.id != "default" && shellInfo.command.isNotEmpty()) {
                myShellCommand = shellInfo.command
            }
        }

        // Create terminal with proper shell directly (no need to execute shell as command)
        terminalManager.createNewSession(terminalManager.terminalRunner, tabState)

        // Execute startup command if configured and project is trusted
        if (canRunCommands && tabConfig.startupCommand.isNotBlank()) {
            executeStartupCommand(terminalManager, tabConfig.startupCommand, tabConfig.name)
        }
    }

    private fun executeStartupCommand(
        terminalManager: TerminalToolWindowManager,
        command: String,
        tabName: String
    ) {
        // Wait for terminal to initialize before sending command
        Timer().schedule(500) {
            ApplicationManager.getApplication().invokeLater {
                try {
                    // Find the terminal widget by tab name and execute command
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

        // Check if it's an absolute path
        val file = File(configuredDir)
        if (file.isAbsolute) {
            return if (file.exists() && file.isDirectory) {
                configuredDir
            } else {
                projectPath
            }
        }

        // Treat as relative path to project
        val resolved = File(projectPath, configuredDir)
        return if (resolved.exists() && resolved.isDirectory) {
            resolved.absolutePath
        } else {
            projectPath
        }
    }
}
