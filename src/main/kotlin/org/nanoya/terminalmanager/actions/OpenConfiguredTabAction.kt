package org.nanoya.terminalmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.nanoya.terminalmanager.TabOpenGate
import org.nanoya.terminalmanager.TerminalTabHelper
import org.nanoya.terminalmanager.settings.TerminalManagerSettings
import org.nanoya.terminalmanager.settings.TerminalTabConfig
import org.nanoya.terminalmanager.settings.TrustedProjectsSettings

class OpenConfiguredTabAction : AnAction(
    "Open Configured Terminal Tab",
    "Open one of the configured terminal tabs on demand",
    AllIcons.General.Add
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tabs = TerminalManagerSettings.getInstance(project).getEffectiveConfig().tabs
        if (tabs.isEmpty()) return

        val group = DefaultActionGroup()
        tabs.forEach { tab -> group.add(OpenSingleTabAction(project, tab)) }

        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Open Terminal Tab",
                group,
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            .showInBestPositionFor(e.dataContext)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private class OpenSingleTabAction(
        private val project: Project,
        private val tab: TerminalTabConfig
    ) : AnAction(tab.name) {
        override fun actionPerformed(e: AnActionEvent) {
            if (!TabOpenGate.shouldOpen(tab.openIf, project.basePath)) return
            val canRunCommands = TrustedProjectsSettings.getInstance().isProjectTrusted(project)
            ApplicationManager.getApplication().invokeLater {
                val toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return@invokeLater
                toolWindow.activate {
                    val terminalManager = TerminalToolWindowManager.getInstance(project)
                    TerminalTabHelper.createTerminalTab(terminalManager, tab, project, canRunCommands)
                }
            }
        }
    }
}
