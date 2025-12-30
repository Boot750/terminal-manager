package org.nanoya.terminalmanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

/**
 * Base action for switching to a specific terminal tab by index.
 */
abstract class SwitchToTerminalTabAction(private val tabIndex: Int) : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return

        val contentManager = toolWindow.contentManager
        val contents = contentManager.contents

        if (tabIndex < contents.size) {
            contentManager.setSelectedContent(contents[tabIndex])
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = project?.let {
            ToolWindowManager.getInstance(it).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        }
        val hasEnoughTabs = toolWindow?.contentManager?.contents?.let { it.size > tabIndex } ?: false
        e.presentation.isEnabled = project != null && hasEnoughTabs
    }
}

// Individual action classes for each tab position (required for plugin.xml registration)

class SwitchToTerminalTab1Action : SwitchToTerminalTabAction(0) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 1"
        templatePresentation.description = "Switch to the first terminal tab"
    }
}

class SwitchToTerminalTab2Action : SwitchToTerminalTabAction(1) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 2"
        templatePresentation.description = "Switch to the second terminal tab"
    }
}

class SwitchToTerminalTab3Action : SwitchToTerminalTabAction(2) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 3"
        templatePresentation.description = "Switch to the third terminal tab"
    }
}

class SwitchToTerminalTab4Action : SwitchToTerminalTabAction(3) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 4"
        templatePresentation.description = "Switch to the fourth terminal tab"
    }
}

class SwitchToTerminalTab5Action : SwitchToTerminalTabAction(4) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 5"
        templatePresentation.description = "Switch to the fifth terminal tab"
    }
}

class SwitchToTerminalTab6Action : SwitchToTerminalTabAction(5) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 6"
        templatePresentation.description = "Switch to the sixth terminal tab"
    }
}

class SwitchToTerminalTab7Action : SwitchToTerminalTabAction(6) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 7"
        templatePresentation.description = "Switch to the seventh terminal tab"
    }
}

class SwitchToTerminalTab8Action : SwitchToTerminalTabAction(7) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 8"
        templatePresentation.description = "Switch to the eighth terminal tab"
    }
}

class SwitchToTerminalTab9Action : SwitchToTerminalTabAction(8) {
    init {
        templatePresentation.text = "Switch to Terminal Tab 9"
        templatePresentation.description = "Switch to the ninth terminal tab"
    }
}
