package org.nanoya.terminalmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
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

class ResetTerminalsAction : AnAction(
    "Reset Terminals",
    "Close all terminals and reopen configured startup terminals",
    AllIcons.Actions.Restart
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = TerminalManagerSettings.getInstance(project)

        if (!settings.skipResetConfirmation) {
            val result = MessageDialogBuilder.yesNo(
                "Reset Terminals",
                "This will close all terminal tabs and reopen the configured startup terminals.\n\nDo you want to continue?"
            )
                .yesText("Reset")
                .noText("Cancel")
                .doNotAsk(object : com.intellij.openapi.ui.DoNotAskOption {
                    override fun isToBeShown(): Boolean = true
                    override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
                        if (!toBeShown && exitCode == Messages.YES) {
                            settings.skipResetConfirmation = true
                            settings.save()
                        }
                    }
                    override fun canBeHidden(): Boolean = true
                    override fun shouldSaveOptionsOnCancel(): Boolean = false
                    override fun getDoNotShowMessage(): String = "Don't ask again"
                })
                .ask(project)

            if (!result) {
                return
            }
        }

        resetTerminals(project, settings)
    }

    private fun resetTerminals(project: com.intellij.openapi.project.Project, settings: TerminalManagerSettings) {
        val enabledTabs = settings.tabs.filter { it.enabled }

        // Check if project is trusted for running startup commands
        val trustedSettings = TrustedProjectsSettings.getInstance()
        val canRunCommands = trustedSettings.isProjectTrusted(project)

        ApplicationManager.getApplication().invokeLater {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalToolWindow = toolWindowManager.getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

            terminalToolWindow?.let { toolWindow ->
                toolWindow.activate {
                    val terminalManager = TerminalToolWindowManager.getInstance(project)

                    // Close all existing terminals
                    closeAllTerminalTabs(toolWindow.contentManager)

                    // Reopen configured terminals
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
        project: com.intellij.openapi.project.Project,
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

    private fun resolveWorkingDirectory(configuredDir: String, project: com.intellij.openapi.project.Project): String {
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

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
