package org.nanoya.terminalmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import org.nanoya.terminalmanager.settings.TerminalManagerConfigurable

class OpenSettingsAction : AnAction(
    "Startup Terminals Settings",
    "Configure terminal tabs that open on project startup",
    AllIcons.General.Settings
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            TerminalManagerConfigurable::class.java
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
