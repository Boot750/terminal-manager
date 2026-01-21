package org.nanoya.terminalmanager.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.border.EmptyBorder

/**
 * Application-level settings configurable for Terminal Manager.
 * Appears under Settings > Tools > Terminal Manager
 */
class TerminalManagerAppConfigurable : Configurable {

    private var lockNavigationCheckbox: JCheckBox? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Terminal Manager"

    override fun createComponent(): JComponent {
        val settings = TerminalManagerAppSettings.getInstance()

        mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(10, 10, 10, 10)
        }

        val navigationPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        lockNavigationCheckbox = JCheckBox("Navigate tabs within split instead of between splits").apply {
            isSelected = settings.lockTerminalNavigationToSplitPanel
            toolTipText = "When enabled, Alt+Shift+[/] navigates between tabs within the current split panel. " +
                    "When disabled (default), Alt+Shift+[/] navigates between split groups."
        }
        navigationPanel.add(lockNavigationCheckbox)

        mainPanel!!.add(navigationPanel)
        mainPanel!!.add(Box.createVerticalGlue())

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = TerminalManagerAppSettings.getInstance()
        return lockNavigationCheckbox?.isSelected != settings.lockTerminalNavigationToSplitPanel
    }

    override fun apply() {
        val settings = TerminalManagerAppSettings.getInstance()
        settings.lockTerminalNavigationToSplitPanel = lockNavigationCheckbox?.isSelected ?: false
    }

    override fun reset() {
        val settings = TerminalManagerAppSettings.getInstance()
        lockNavigationCheckbox?.isSelected = settings.lockTerminalNavigationToSplitPanel
    }

    override fun disposeUIResources() {
        lockNavigationCheckbox = null
        mainPanel = null
    }
}
