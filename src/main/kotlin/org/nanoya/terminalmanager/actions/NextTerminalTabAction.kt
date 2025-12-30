package org.nanoya.terminalmanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

/**
 * Action to switch to the next terminal tab with wrap-around.
 */
class NextTerminalTabAction : AnAction(
    "Next Terminal Tab",
    "Switch to the next terminal tab",
    null
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return

        val contentManager = toolWindow.contentManager
        val contents = contentManager.contents

        if (contents.isEmpty()) return

        val currentContent = contentManager.selectedContent
        val currentIndex = contents.indexOf(currentContent)
        val newIndex = (currentIndex + 1) % contents.size

        contentManager.setSelectedContent(contents[newIndex])
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = project?.let {
            ToolWindowManager.getInstance(it).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        }
        val hasMultipleTabs = toolWindow?.contentManager?.contents?.let { it.size > 1 } ?: false
        e.presentation.isEnabled = project != null && hasMultipleTabs
    }
}
