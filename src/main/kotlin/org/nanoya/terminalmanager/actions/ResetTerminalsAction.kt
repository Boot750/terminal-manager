package org.nanoya.terminalmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.nanoya.terminalmanager.TabOpenGate
import org.nanoya.terminalmanager.TerminalTabHelper
import org.nanoya.terminalmanager.settings.TerminalManagerConfig
import org.nanoya.terminalmanager.settings.TerminalManagerSettings
import org.nanoya.terminalmanager.settings.TrustedProjectsSettings

class ResetTerminalsAction : AnAction(
    "Reset Terminals",
    "Close all terminals and reopen configured startup terminals",
    AllIcons.Actions.Restart
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = TerminalManagerSettings.getInstance(project)
        val effectiveConfig = settings.getEffectiveConfig()

        if (!effectiveConfig.skipResetConfirmation) {
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

        resetTerminals(project, effectiveConfig)
    }

    private fun resetTerminals(project: com.intellij.openapi.project.Project, effectiveConfig: TerminalManagerConfig) {
        val enabledTabs = effectiveConfig.tabs.filter {
            it.enabled && TabOpenGate.shouldOpen(it.openIf, project.basePath)
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

                    // Close all existing terminals
                    TerminalTabHelper.closeAllTerminalTabs(toolWindow.contentManager)

                    // Reopen configured terminals
                    enabledTabs.forEach { tabConfig ->
                        TerminalTabHelper.createTerminalTab(terminalManager, tabConfig, project, canRunCommands)
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
