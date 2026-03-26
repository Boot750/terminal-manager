package org.nanoya.terminalmanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.nanoya.terminalmanager.settings.TerminalManagerSettings
import org.nanoya.terminalmanager.settings.TrustedProjectsSettings

class TerminalStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = TerminalManagerSettings.getInstance(project)
        val effectiveConfig = settings.getEffectiveConfig()

        if (!effectiveConfig.enabled || effectiveConfig.tabs.isEmpty()) {
            return
        }

        val enabledTabs = effectiveConfig.tabs.filter { it.enabled }
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
                    if (effectiveConfig.closeExistingTerminals) {
                        TerminalTabHelper.closeAllTerminalTabs(toolWindow.contentManager)
                    }

                    enabledTabs.forEach { tabConfig ->
                        TerminalTabHelper.createTerminalTab(terminalManager, tabConfig, project, canRunCommands)
                    }
                }
            }
        }
    }
}
