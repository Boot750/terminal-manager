package org.nanoya.terminalmanager

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.nanoya.terminalmanager.actions.OpenConfiguredTabAction
import org.nanoya.terminalmanager.actions.OpenSettingsAction
import org.nanoya.terminalmanager.actions.ResetTerminalsAction

class TerminalGearActionInstaller : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Since 2026.1 the reworked terminal's own tool window initializer calls
        // setTitleActions during content initialization, replacing whatever a one-shot
        // startup install put there (a race we lose more often than not). The initializer
        // runs synchronously before the tool window becomes visible, so re-asserting on
        // every toolWindowShown deterministically runs after it and wins.
        project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(toolWindow: ToolWindow) {
                if (toolWindow.id == TerminalToolWindowFactory.TOOL_WINDOW_ID) {
                    thisLogger().info("TerminalManager: terminal tool window shown, re-asserting title actions")
                    installTitleActions(toolWindow)
                }
            }
        })

        // Also install at startup so the buttons are present when the tool window was
        // restored visible without a subsequent "shown" event.
        val terminalToolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        thisLogger().info("TerminalManager: gear installer running, terminal tool window = $terminalToolWindow")
        if (terminalToolWindow != null) {
            withContext(Dispatchers.EDT) {
                installTitleActions(terminalToolWindow)
            }
        }
    }

    private fun installTitleActions(toolWindow: ToolWindow) {
        toolWindow.setTitleActions(listOf(OpenConfiguredTabAction(), ResetTerminalsAction(), OpenSettingsAction()))
        thisLogger().info("TerminalManager: title actions installed")
    }
}
