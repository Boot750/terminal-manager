package org.nanoya.terminalmanager.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import java.awt.KeyboardFocusManager

/**
 * Action to switch to the next terminal tab.
 */
class NextTerminalTabAction : AnAction(
    "Terminal Manager: Next Tab",
    "Switch to the next terminal tab",
    null
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return

        toolWindow.activate {
            val actionManager = ActionManager.getInstance()
            // Try multiple action IDs - Terminal.NextTab was used in classic terminal,
            // but reworked terminal (2025.x) uses different IDs
            val actionIds = listOf("Terminal.NextTab", "NextTab", "ActivateNextTab", "ToolWindowSelectNextContent")

            var action: AnAction? = null
            for (id in actionIds) {
                action = actionManager.getAction(id)
                if (action != null) break
            }

            if (action == null) return@activate

            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                ?: toolWindow.component
            val baseContext = DataManager.getInstance().getDataContext(focusOwner)
            val context = SimpleDataContext.builder()
                .setParent(baseContext)
                .add(CommonDataKeys.PROJECT, project)
                .build()

            val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.TOOLWINDOW_CONTENT, ActionUiKind.NONE, null)
            action.actionPerformed(event)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = project?.let {
            ToolWindowManager.getInstance(it).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        }
        e.presentation.isEnabled = project != null && toolWindow != null && toolWindow.isAvailable
    }
}
