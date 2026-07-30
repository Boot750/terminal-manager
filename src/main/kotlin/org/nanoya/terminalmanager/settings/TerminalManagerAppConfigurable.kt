package org.nanoya.terminalmanager.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.Color
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Application-level settings configurable for Terminal Manager.
 * Appears under Settings > Tools > Terminal Manager
 */
class TerminalManagerAppConfigurable : Configurable {

    private var dialogPanel: DialogPanel? = null
    private var binaryField: JBTextField? = null
    private var statusLabel: JBLabel? = null
    private var validationTimer: Timer? = null

    private val okColor = JBColor(Color(0x2E7D32), Color(0x6CC644))
    private val errorColor = JBColor(Color(0xC0392B), Color(0xFF6B68))

    override fun getDisplayName(): String = "Terminal Manager"

    override fun createComponent(): JComponent {
        val settings = TerminalManagerAppSettings.getInstance()

        val panel = panel {
            group("Navigation", indent = false) {
                row {
                    checkBox("Navigate tabs within split instead of between splits")
                        .bindSelected(settings::lockTerminalNavigationToSplitPanel)
                        .comment(
                            "When enabled, Alt+Shift+[/] navigates between tabs within the current split panel.<br>" +
                            "When disabled (default), it navigates between split groups."
                        )
                }
            }

            // tmux settings (per-machine). Greyed out on platforms without tmux (Windows).
            group("tmux", indent = false) {
                val tmuxEnabled = ShellDetector.isTmuxPlatform()
                if (!tmuxEnabled) {
                    row {
                        comment("tmux is only available on macOS and Linux — these settings are disabled on this platform.")
                    }
                }
                row("Binary:") {
                    binaryField = textField()
                        .bindText(settings::tmuxBinary)
                        .columns(30)
                        .enabled(tmuxEnabled)
                        .comment(
                            "Path to the tmux binary used by tmux tabs.<br>" +
                            "Use \"tmux\" to resolve it from PATH, or an absolute path."
                        )
                        .component
                    actionButton(resetAction { binaryField?.text = TerminalManagerAppSettings.DEFAULT_TMUX_BINARY })
                        .enabled(tmuxEnabled)
                }
                if (tmuxEnabled) {
                    row("") {
                        statusLabel = cell(JBLabel()).component
                    }
                }

                row("Command:") {
                    val cmd = textField()
                        .bindText(settings::tmuxCommand)
                        .columns(80)
                        .enabled(tmuxEnabled)
                        .comment(
                            "Template for the tmux invocation (arguments after the binary). Placeholders:<br>" +
                            "&nbsp;&nbsp;<code>{name}</code> &mdash; tmux session name<br>" +
                            "&nbsp;&nbsp;<code>{dir}</code> &mdash; working directory<br>" +
                            "&nbsp;&nbsp;<code>{command}</code> &mdash; shell command run on session creation " +
                            "(startup command + shell), inserted as one argument and omitted if empty<br>" +
                            "Tokens are separated by whitespace; a standalone <code>;</code> separates tmux commands.",
                            maxLineLength = MAX_LINE_LENGTH_NO_WRAP
                        )
                        .component
                    actionButton(resetAction { cmd.text = TerminalManagerAppSettings.DEFAULT_TMUX_COMMAND })
                        .enabled(tmuxEnabled)
                }
            }
        }

        dialogPanel = panel
        if (ShellDetector.isTmuxPlatform()) {
            setupBinaryValidation()
        }
        return panel
    }

    /** A borderless "reset to default" icon action (the circular arrow used across IDE settings). */
    private fun resetAction(reset: () -> Unit): DumbAwareAction =
        object : DumbAwareAction("Reset to Default", null, AllIcons.General.Reset) {
            override fun actionPerformed(e: AnActionEvent) = reset()
        }

    /** Live-validates the tmux binary (via `tmux -V`) and shows the version or an error under the field. */
    private fun setupBinaryValidation() {
        val field = binaryField ?: return
        val timer = Timer(500) { revalidateBinary() }.apply { isRepeats = false }
        validationTimer = timer
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = timer.restart()
            override fun removeUpdate(e: DocumentEvent) = timer.restart()
            override fun changedUpdate(e: DocumentEvent) = timer.restart()
        })
        revalidateBinary()
    }

    private fun revalidateBinary() {
        val field = binaryField ?: return
        val label = statusLabel ?: return
        val binary = field.text.trim()
        label.text = "Checking tmux\u2026"
        label.foreground = JBColor.GRAY
        label.toolTipText = null
        // Update the label regardless of modality: the initial check runs before the
        // Settings dialog is shown, so stateForComponent() would capture NON_MODAL and the
        // update would be blocked once the dialog goes modal. any() is safe here because we
        // only mutate a label (no PSI/document/model changes).
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = TmuxValidator.validate(binary)
            ApplicationManager.getApplication().invokeLater({
                val lbl = statusLabel ?: return@invokeLater
                if (result.ok) {
                    lbl.text = "tmux ${result.version}"
                    lbl.foreground = okColor
                    lbl.toolTipText = null
                } else {
                    lbl.text = "tmux not found"
                    lbl.foreground = errorColor
                    lbl.toolTipText = result.error
                }
            }, ModalityState.any())
        }
    }

    override fun isModified(): Boolean = dialogPanel?.isModified() ?: false

    override fun apply() {
        dialogPanel?.apply()
        // The binary may have changed; drop the cached validation so it re-checks.
        TmuxValidator.invalidate()
        revalidateBinary()
    }

    override fun reset() {
        dialogPanel?.reset()
        revalidateBinary()
    }

    override fun disposeUIResources() {
        validationTimer?.stop()
        validationTimer = null
        binaryField = null
        statusLabel = null
        dialogPanel = null
    }
}
