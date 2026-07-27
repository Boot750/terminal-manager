package org.nanoya.terminalmanager

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.nanoya.terminalmanager.settings.TerminalManagerConfig
import org.nanoya.terminalmanager.settings.TerminalManagerSettings
import org.nanoya.terminalmanager.settings.TerminalTabConfig
import org.nanoya.terminalmanager.settings.TrustedProjectsSettings
import kotlin.coroutines.resume

class TerminalStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = TerminalManagerSettings.getInstance(project)
        val effectiveConfig = settings.getEffectiveConfig()

        if (!effectiveConfig.enabled || effectiveConfig.tabs.isEmpty()) {
            return
        }

        val enabledTabs = effectiveConfig.tabs.filter {
            it.enabled && TabOpenGate.shouldOpen(it.openIf, project.basePath)
        }
        if (enabledTabs.isEmpty()) {
            return
        }

        // Check if project is trusted for running startup commands
        val trustedSettings = TrustedProjectsSettings.getInstance()
        val canRunCommands = trustedSettings.isProjectTrusted(project)

        val terminalToolWindow = withContext(Dispatchers.EDT) {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                ?: return@withContext null

            // Wait for the activation callback rather than just requesting activation:
            // the tool window runs it once its contents are in place.
            suspendCancellableCoroutine { continuation ->
                toolWindow.activate { continuation.resume(Unit) }
            }
            toolWindow
        } ?: return

        // Defer the close/create so IntelliJ's asynchronous tab restore and
        // auto-create finish first. Closing a terminal whose session is still
        // *starting* cancels the in-flight start and logs a (benign) "Failed to
        // start" cancellation. After a short delay those sessions have started,
        // so closing them is graceful. The delay also lets the dedupe snapshot
        // below see the fully restored tabs.
        delay(700)

        withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext
            openConfiguredTabs(project, terminalToolWindow, effectiveConfig, enabledTabs, canRunCommands)
        }
    }

    private fun openConfiguredTabs(
        project: Project,
        toolWindow: ToolWindow,
        config: TerminalManagerConfig,
        enabledTabs: List<TerminalTabConfig>,
        canRunCommands: Boolean
    ) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)

        // Close existing terminals if option is enabled.
        if (config.closeExistingTerminals) {
            TerminalTabHelper.closeAllTerminalTabs(toolWindow.contentManager)
        }

        // Skip tabs that already exist (by name). IntelliJ persists and restores open
        // terminal tabs across project reopens (see TerminalArrangementManager, gated by
        // the "terminal.persistent.tabs" registry key). Without this guard we would
        // create a second tab for one IntelliJ already restored — which, for tmux tabs,
        // attaches the same session twice and interleaves tmux's attach-time queries,
        // leaking escape sequences (e.g. "^[[?6c") into the pane.
        val existingNames = toolWindow.contentManager.contents
            .mapNotNull { it.displayName }
            .toSet()

        enabledTabs.forEach { tabConfig ->
            if (tabConfig.name !in existingNames) {
                TerminalTabHelper.createTerminalTab(terminalManager, tabConfig, project, canRunCommands)
            }
        }
    }
}
