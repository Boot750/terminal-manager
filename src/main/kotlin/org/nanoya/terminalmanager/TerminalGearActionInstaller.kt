package org.nanoya.terminalmanager

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.nanoya.terminalmanager.actions.OpenSettingsAction
import org.nanoya.terminalmanager.actions.ResetTerminalsAction

class TerminalGearActionInstaller : ProjectActivity {

    override suspend fun execute(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalToolWindow = toolWindowManager.getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

        terminalToolWindow?.let { toolWindow ->
            toolWindow.setTitleActions(listOf(ResetTerminalsAction(), OpenSettingsAction()))
        }
    }
}
