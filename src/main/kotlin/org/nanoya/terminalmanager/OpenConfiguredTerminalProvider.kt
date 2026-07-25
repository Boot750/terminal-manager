package org.nanoya.terminalmanager

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.ui.OpenPredefinedTerminalActionProvider
import org.nanoya.terminalmanager.settings.TerminalManagerSettings
import org.nanoya.terminalmanager.settings.TerminalTabConfig
import org.nanoya.terminalmanager.settings.TrustedProjectsSettings

/**
 * Contributes the project's configured startup terminals to IntelliJ's terminal
 * "new session" dropdown (the "+" chevron), so each configured tab can be opened
 * on demand by name — with its shell, working directory, tmux, startup command and
 * color settings applied.
 */
class OpenConfiguredTerminalProvider : OpenPredefinedTerminalActionProvider {

    override fun listOpenPredefinedTerminalActions(project: Project): List<AnAction> {
        val config = TerminalManagerSettings.getInstance(project).getEffectiveConfig()
        return config.tabs.map { OpenConfiguredTerminalAction(it) }
    }
}

private class OpenConfiguredTerminalAction(
    private val tabConfig: TerminalTabConfig
) : DumbAwareAction(tabConfig.name) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val canRunCommands = TrustedProjectsSettings.getInstance().isProjectTrusted(project)

        ApplicationManager.getApplication().invokeLater {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return@invokeLater
            toolWindow.activate {
                val terminalManager = TerminalToolWindowManager.getInstance(project)
                TerminalTabHelper.createTerminalTab(terminalManager, tabConfig, project, canRunCommands)
            }
        }
    }
}
