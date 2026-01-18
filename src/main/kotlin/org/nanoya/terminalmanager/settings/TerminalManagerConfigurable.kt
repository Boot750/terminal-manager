package org.nanoya.terminalmanager.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class TerminalManagerConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private var enabledCheckbox: JBCheckBox? = null
    private var closeExistingCheckbox: JBCheckBox? = null
    private var tableModel: TerminalTabTableModel? = null
    private var table: JBTable? = null
    private var trustBannerPanel: JPanel? = null

    override fun getDisplayName(): String = "Startup Terminals"

    override fun createComponent(): JComponent {
        val settings = TerminalManagerSettings.getInstance(project)
        val trustedSettings = TrustedProjectsSettings.getInstance()

        // Refresh shell detection when opening settings
        ShellDetector.refreshCache()

        mainPanel = JPanel(BorderLayout(0, 10))

        // Create trust warning banner
        trustBannerPanel = createTrustBannerPanel(trustedSettings)

        enabledCheckbox = JBCheckBox("Open terminals on project startup", settings.enabled)
        closeExistingCheckbox = JBCheckBox("Close existing terminal tabs first", settings.closeExistingTerminals)

        tableModel = TerminalTabTableModel(settings.tabs.map { it.copy() }.toMutableList())
        table = JBTable(tableModel).apply {
            setShowGrid(true)
            rowHeight = 28

            columnModel.getColumn(0).preferredWidth = 120  // Name
            columnModel.getColumn(1).preferredWidth = 150  // Shell Type
            columnModel.getColumn(2).preferredWidth = 200  // Working Directory
            columnModel.getColumn(3).preferredWidth = 200  // Startup Command
            columnModel.getColumn(4).preferredWidth = 60   // Enabled

            // Shell type dropdown
            columnModel.getColumn(1).cellEditor = ShellInfoCellEditor()
            columnModel.getColumn(1).cellRenderer = ShellInfoCellRenderer()

            // Directory chooser
            columnModel.getColumn(2).cellEditor = DirectoryChooserCellEditor(project)
        }

        val decorator = ToolbarDecorator.createDecorator(table!!)
            .setAddAction { tableModel?.addRow() }
            .setRemoveAction {
                val selectedRow = table!!.selectedRow
                if (selectedRow >= 0) {
                    tableModel?.removeRow(selectedRow)
                }
            }
            .setMoveUpAction {
                val selectedRow = table!!.selectedRow
                if (selectedRow > 0) {
                    tableModel?.moveRow(selectedRow, selectedRow - 1)
                    table!!.setRowSelectionInterval(selectedRow - 1, selectedRow - 1)
                }
            }
            .setMoveDownAction {
                val selectedRow = table!!.selectedRow
                if (selectedRow >= 0 && selectedRow < tableModel!!.rowCount - 1) {
                    tableModel?.moveRow(selectedRow, selectedRow + 1)
                    table!!.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)
                }
            }
            .createPanel()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(enabledCheckbox)
            add(closeExistingCheckbox)
        }

        val helpLabel = JBLabel(
            "<html>Working directory is relative to project root. Leave blank for project root.<br>" +
            "Startup command runs after the terminal initializes (requires trust).<br>" +
            "Config stored in: <code>.terminals/startup-terminals.json</code></html>"
        )

        val centerPanel = JPanel(BorderLayout(0, 5)).apply {
            add(JBLabel("Startup Terminal Tabs:"), BorderLayout.NORTH)
            add(decorator, BorderLayout.CENTER)
            add(helpLabel, BorderLayout.SOUTH)
        }

        // Wrap top panel with trust banner
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            if (trustBannerPanel != null) {
                add(trustBannerPanel)
                add(Box.createVerticalStrut(10))
            }
            add(topPanel)
        }

        mainPanel!!.add(headerPanel, BorderLayout.NORTH)
        mainPanel!!.add(centerPanel, BorderLayout.CENTER)

        // Update banner visibility based on whether any startup commands exist
        updateTrustBannerVisibility()

        return mainPanel!!
    }

    private fun createTrustBannerPanel(trustedSettings: TrustedProjectsSettings): JPanel {
        val panel = JPanel(BorderLayout(10, 0)).apply {
            background = JBColor(0xFFF3CD, 0x5C4A00)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(0xFFECB5, 0x7A6200)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
            )
        }

        val warningLabel = JBLabel(
            "<html><b>Startup commands are configured but not enabled.</b><br>" +
            "Commands in project configs can run arbitrary code. Only enable if you trust this project.</html>"
        )

        val enableButton = JButton("Enable Startup Commands").apply {
            addActionListener {
                trustedSettings.trustProject(project)
                updateTrustBannerVisibility()
            }
        }

        panel.add(warningLabel, BorderLayout.CENTER)
        panel.add(enableButton, BorderLayout.EAST)

        return panel
    }

    private fun updateTrustBannerVisibility() {
        val trustedSettings = TrustedProjectsSettings.getInstance()
        val hasStartupCommands = tableModel?.getTabs()?.any { it.startupCommand.isNotBlank() } ?: false
        val isTrusted = trustedSettings.isProjectTrusted(project)

        trustBannerPanel?.isVisible = hasStartupCommands && !isTrusted
    }

    override fun isModified(): Boolean {
        val settings = TerminalManagerSettings.getInstance(project)
        if (enabledCheckbox?.isSelected != settings.enabled) return true
        if (closeExistingCheckbox?.isSelected != settings.closeExistingTerminals) return true

        val currentTabs = tableModel?.getTabs() ?: return false
        if (currentTabs.size != settings.tabs.size) return true

        return currentTabs.zip(settings.tabs).any { (current, saved) ->
            current.name != saved.name ||
            current.shellId != saved.shellId ||
            current.workingDirectory != saved.workingDirectory ||
            current.enabled != saved.enabled ||
            current.startupCommand != saved.startupCommand
        }
    }

    override fun apply() {
        val settings = TerminalManagerSettings.getInstance(project)
        settings.enabled = enabledCheckbox?.isSelected ?: true
        settings.closeExistingTerminals = closeExistingCheckbox?.isSelected ?: false
        settings.tabs.clear()
        settings.tabs.addAll(tableModel?.getTabs()?.map { it.copy() } ?: emptyList())
        settings.save()
    }

    override fun reset() {
        val settings = TerminalManagerSettings.getInstance(project)
        settings.load()
        enabledCheckbox?.isSelected = settings.enabled
        closeExistingCheckbox?.isSelected = settings.closeExistingTerminals
        tableModel?.setTabs(settings.tabs.map { it.copy() }.toMutableList())
    }

    override fun disposeUIResources() {
        mainPanel = null
        enabledCheckbox = null
        closeExistingCheckbox = null
        tableModel = null
        table = null
        trustBannerPanel = null
    }
}

class TerminalTabTableModel(private var tabs: MutableList<TerminalTabConfig>) : AbstractTableModel() {

    private val columnNames = arrayOf("Name", "Shell", "Working Directory", "Startup Command", "Enabled")

    override fun getRowCount(): Int = tabs.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            4 -> java.lang.Boolean::class.java
            else -> String::class.java
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val tab = tabs[rowIndex]
        return when (columnIndex) {
            0 -> tab.name
            1 -> tab.shellId
            2 -> tab.workingDirectory
            3 -> tab.startupCommand
            4 -> tab.enabled
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val tab = tabs[rowIndex]
        when (columnIndex) {
            0 -> tab.name = aValue as? String ?: ""
            1 -> tab.shellId = (aValue as? ShellInfo)?.id ?: (aValue as? String) ?: "default"
            2 -> tab.workingDirectory = aValue as? String ?: ""
            3 -> tab.startupCommand = aValue as? String ?: ""
            4 -> tab.enabled = aValue as? Boolean ?: true
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun addRow() {
        tabs.add(TerminalTabConfig())
        fireTableRowsInserted(tabs.size - 1, tabs.size - 1)
    }

    fun removeRow(row: Int) {
        if (row in tabs.indices) {
            tabs.removeAt(row)
            fireTableRowsDeleted(row, row)
        }
    }

    fun moveRow(from: Int, to: Int) {
        if (from in tabs.indices && to in tabs.indices) {
            val item = tabs.removeAt(from)
            tabs.add(to, item)
            fireTableDataChanged()
        }
    }

    fun getTabs(): List<TerminalTabConfig> = tabs.toList()

    fun setTabs(newTabs: MutableList<TerminalTabConfig>) {
        tabs = newTabs
        fireTableDataChanged()
    }
}

class ShellInfoCellEditor : AbstractCellEditor(), TableCellEditor {
    private val comboBox: JComboBox<ShellInfo>

    init {
        val shells = ShellDetector.getAvailableShells()
        comboBox = JComboBox(shells.toTypedArray())
        comboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? ShellInfo)?.displayName ?: value?.toString() ?: ""
                return this
            }
        }
    }

    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: ShellDetector.getAvailableShells().first()

    override fun getTableCellEditorComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        val shellId = value as? String ?: "default"
        val shells = ShellDetector.getAvailableShells()
        val selectedShell = shells.find { it.id == shellId } ?: shells.first()
        comboBox.selectedItem = selectedShell
        return comboBox
    }
}

class ShellInfoCellRenderer : TableCellRenderer {
    private val label = JLabel()

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val shellId = value as? String ?: "default"
        val shell = ShellDetector.getAvailableShells().find { it.id == shellId }
        label.text = shell?.displayName ?: shellId

        if (isSelected) {
            label.background = table?.selectionBackground
            label.foreground = table?.selectionForeground
            label.isOpaque = true
        } else {
            label.background = table?.background
            label.foreground = table?.foreground
            label.isOpaque = false
        }
        return label
    }
}

class DirectoryChooserCellEditor(private val project: Project) : AbstractCellEditor(), TableCellEditor {
    private val panel = JPanel(BorderLayout())
    private val textField = JBTextField()
    private val browseButton = JButton("...")

    init {
        browseButton.preferredSize = Dimension(25, 25)
        browseButton.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            val baseDir = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
            val chooser = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                descriptor,
                project,
                baseDir
            )
            chooser?.let { selected ->
                // Make path relative to project if possible
                val projectPath = project.basePath
                if (projectPath != null && selected.path.startsWith(projectPath)) {
                    val relativePath = selected.path.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
                    textField.text = relativePath.ifEmpty { "." }
                } else {
                    textField.text = selected.path
                }
            }
        }
        panel.add(textField, BorderLayout.CENTER)
        panel.add(browseButton, BorderLayout.EAST)
    }

    override fun getCellEditorValue(): Any = textField.text

    override fun getTableCellEditorComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        textField.text = value as? String ?: ""
        return panel
    }
}
