package org.nanoya.terminalmanager

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.nanoya.terminalmanager.actions.OpenSettingsAction
import org.nanoya.terminalmanager.actions.ResetTerminalsAction

class TerminalGearActionInstaller : ProjectActivity {

    override suspend fun execute(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalToolWindow = toolWindowManager.getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

        terminalToolWindow?.let { toolWindow ->
            withContext(Dispatchers.EDT) {
                toolWindow.setTitleActions(listOf(ResetTerminalsAction(), OpenSettingsAction()))
            }
        }
    }
}
