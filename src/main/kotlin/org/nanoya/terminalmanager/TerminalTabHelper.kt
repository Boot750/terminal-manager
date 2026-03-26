package org.nanoya.terminalmanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.content.ContentManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
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

        val tabState = TerminalTabState().apply {
            myTabName = tabConfig.name
            myWorkingDirectory = workingDir
            if (shellInfo != null && shellInfo.id != "default" && shellInfo.command.isNotEmpty()) {
                myShellCommand = shellInfo.command
            }
        }

        terminalManager.createNewSession(terminalManager.terminalRunner, tabState)

        applyTabColor(terminalManager, tabConfig)

        if (canRunCommands && tabConfig.startupCommand.isNotBlank()) {
            executeStartupCommand(terminalManager, tabConfig.startupCommand, tabConfig.name)
        }
    }

    fun closeAllTerminalTabs(contentManager: ContentManager) {
        val contents = contentManager.contents.toList()
        contents.forEach { content ->
            contentManager.removeContent(content, true)
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
