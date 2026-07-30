package org.nanoya.terminalmanager.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import org.nanoya.terminalmanager.TabOpenGate
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.io.File
import java.util.UUID
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class TerminalManagerConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private var enabledCheckbox: JBCheckBox? = null
    private var closeExistingCheckbox: JBCheckBox? = null
    private var sharedModel: TerminalTabTableModel? = null
    private var localModel: LocalOverrideTableModel? = null
    private var previewModel: DefaultTableModel? = null
    private var trustBannerPanel: JPanel? = null

    override fun getDisplayName(): String = "Startup Terminals"

    override fun createComponent(): JComponent {
        val settings = TerminalManagerSettings.getInstance(project)
        val trustedSettings = TrustedProjectsSettings.getInstance()

        ShellDetector.refreshCache()

        mainPanel = JPanel(BorderLayout(0, 10))

        trustBannerPanel = createTrustBannerPanel(trustedSettings)

        enabledCheckbox = JBCheckBox("Open terminals on project startup", settings.enabled)
        closeExistingCheckbox = JBCheckBox("Close existing terminal tabs first", settings.closeExistingTerminals)

        sharedModel = TerminalTabTableModel(settings.tabs.map { it.copy() }.toMutableList())
        val sharedTable = buildSharedTable(sharedModel!!)
        val sharedPanel = ToolbarDecorator.createDecorator(sharedTable)
            .setAddAction { sharedModel?.addRow() }
            .setRemoveAction {
                val r = sharedTable.selectedRow
                if (r >= 0) sharedModel?.removeRow(r)
            }
            .setMoveUpAction {
                val r = sharedTable.selectedRow
                if (r > 0) { sharedModel?.moveRow(r, r - 1); sharedTable.setRowSelectionInterval(r - 1, r - 1) }
            }
            .setMoveDownAction {
                val r = sharedTable.selectedRow
                if (r >= 0 && r < sharedModel!!.rowCount - 1) { sharedModel?.moveRow(r, r + 1); sharedTable.setRowSelectionInterval(r + 1, r + 1) }
            }
            .createPanel()

        localModel = LocalOverrideTableModel(
            loadLocalRows(settings),
            baseTabsProvider = { sharedModel?.getTabs() ?: emptyList() }
        )
        val localTable = buildLocalTable(localModel!!)
        val localPanel = ToolbarDecorator.createDecorator(localTable)
            .setAddAction { localModel?.addOverrideRow() }
            .addExtraAction(object : com.intellij.openapi.actionSystem.AnAction("Add Personal Tab", "Add a local-only personal tab", AllIconsAddPersonal) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    localModel?.addPersonalRow()
                }
            })
            .setRemoveAction {
                val r = localTable.selectedRow
                if (r >= 0) localModel?.removeRow(r)
            }
            .createPanel()

        // Card stack swapped by the scope toggle.
        val cards = JPanel(CardLayout())
        cards.add(sharedPanel, "shared")
        cards.add(localPanel, "local")

        val sharedRadio = JRadioButton("Shared (committed)", true)
        val localRadio = JRadioButton("Local (this machine)")
        ButtonGroup().apply { add(sharedRadio); add(localRadio) }
        sharedRadio.addActionListener { (cards.layout as CardLayout).show(cards, "shared") }
        localRadio.addActionListener { (cards.layout as CardLayout).show(cards, "local") }
        val scopePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Editing: "))
            add(sharedRadio)
            add(localRadio)
        }

        // Effective preview.
        previewModel = DefaultTableModel(arrayOf<Any>("Name", "Shell", "Source", "Opens?"), 0)
        val previewTable = JBTable(previewModel).apply { isEnabled = false }
        val refreshButton = JButton("Refresh preview").apply { addActionListener { refreshPreview() } }
        val previewPanel = JPanel(BorderLayout(0, 5)).apply {
            add(JPanel(BorderLayout()).apply {
                add(JBLabel("Effective (what will run):"), BorderLayout.WEST)
                add(refreshButton, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(JScrollPane(previewTable), BorderLayout.CENTER)
            preferredSize = Dimension(600, 140)
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(enabledCheckbox)
            add(closeExistingCheckbox)
        }

        val helpLabel = JBLabel(
            "<html>Working directory is relative to project root. Leave blank for project root.<br>" +
            "Startup command and env vars run after the terminal initializes (requires trust).<br>" +
            "Open If: relative path; the tab opens only if it exists (blank = always).<br>" +
            "Local overrides edit <code>.terminals/startup-terminals.local.json</code> (per-machine, gitignore-friendly).</html>"
        )

        val centerPanel = JPanel(BorderLayout(0, 5)).apply {
            add(scopePanel, BorderLayout.NORTH)
            add(cards, BorderLayout.CENTER)
            add(JPanel(BorderLayout(0, 5)).apply {
                add(helpLabel, BorderLayout.NORTH)
                add(previewPanel, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }

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

        updateTrustBannerVisibility()
        refreshPreview()

        return mainPanel!!
    }

    private val AllIconsAddPersonal get() = com.intellij.icons.AllIcons.General.Add

    private fun buildSharedTable(model: TerminalTabTableModel): JBTable {
        return JBTable(model).apply {
            setShowGrid(true)
            rowHeight = 28
            columnModel.getColumn(0).preferredWidth = 110  // Name
            columnModel.getColumn(1).preferredWidth = 140  // Shell
            columnModel.getColumn(2).preferredWidth = 160  // Working Directory
            columnModel.getColumn(3).preferredWidth = 160  // Startup Command
            columnModel.getColumn(4).preferredWidth = 70   // Color
            columnModel.getColumn(5).preferredWidth = 110  // Open If
            columnModel.getColumn(6).preferredWidth = 70   // Env
            columnModel.getColumn(7).preferredWidth = 90   // Enable tmux
            columnModel.getColumn(8).preferredWidth = 60   // Enabled

            columnModel.getColumn(1).cellEditor = ShellInfoCellEditor()
            columnModel.getColumn(1).cellRenderer = ShellInfoCellRenderer()
            columnModel.getColumn(2).cellEditor = DirectoryChooserCellEditor(project)
            columnModel.getColumn(4).cellEditor = TabColorCellEditor()
            columnModel.getColumn(4).cellRenderer = TabColorCellRenderer()
            // "Enable tmux" checkbox — greyed out on platforms without tmux.
            columnModel.getColumn(7).cellRenderer = TmuxCheckBoxRenderer()
            columnModel.getColumn(6).cellEditor = EnvCellEditor(
                project,
                getEnv = { row -> model.getEnv(row) },
                setEnv = { row, env -> model.setEnv(row, env) }
            )
        }
    }

    private fun buildLocalTable(model: LocalOverrideTableModel): JBTable {
        return JBTable(model).apply {
            setShowGrid(true)
            rowHeight = 28
            columnModel.getColumn(0).preferredWidth = 130  // Target
            columnModel.getColumn(1).preferredWidth = 100  // Name
            columnModel.getColumn(2).preferredWidth = 130  // Shell
            columnModel.getColumn(3).preferredWidth = 140  // Working Directory
            columnModel.getColumn(4).preferredWidth = 140  // Startup Command
            columnModel.getColumn(5).preferredWidth = 90   // Color
            columnModel.getColumn(6).preferredWidth = 100  // Open If
            columnModel.getColumn(7).preferredWidth = 60   // Env
            columnModel.getColumn(8).preferredWidth = 55   // Muted

            columnModel.getColumn(0).cellEditor = TargetCellEditor { model.targetOptions() }
            columnModel.getColumn(2).cellEditor = InheritShellCellEditor()
            columnModel.getColumn(2).cellRenderer = InheritShellCellRenderer()
            columnModel.getColumn(5).cellEditor = InheritColorCellEditor()
            columnModel.getColumn(7).cellEditor = EnvCellEditor(
                project,
                getEnv = { row -> model.getEnv(row) },
                setEnv = { row, env -> model.setEnv(row, env) }
            )
        }
    }

    private fun loadLocalRows(settings: TerminalManagerSettings): MutableList<LocalRow> {
        val local = settings.loadLocalConfig() ?: return mutableListOf()
        return (local.tabs ?: emptyList()).map { LocalRow.from(it) }.toMutableList()
    }

    private fun refreshPreview() {
        val model = previewModel ?: return
        model.rowCount = 0
        val base = TerminalManagerConfig(
            enabled = enabledCheckbox?.isSelected ?: true,
            closeExistingTerminals = closeExistingCheckbox?.isSelected ?: false,
            tabs = sharedModel?.getTabs() ?: emptyList()
        )
        val local = buildLocalConfig()
        ConfigMerger.mergeEffective(base, local).forEach { eff ->
            val shellName = eff.config.getShellInfo()?.displayName ?: eff.config.shellId
            val opens = when {
                eff.source == TabSource.ORPHANED -> "-"
                TabOpenGate.shouldOpen(eff.config.openIf, project.basePath) -> "yes"
                else -> "skipped"
            }
            model.addRow(arrayOf<Any>(eff.config.name, shellName, eff.source.name, opens))
        }
    }

    private fun buildLocalConfig(): LocalTerminalManagerConfig {
        val rows = localModel?.getRows() ?: emptyList()
        if (rows.isEmpty()) return LocalTerminalManagerConfig()
        return LocalTerminalManagerConfig(tabs = rows.map { it.toOverride() })
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
            "<html><b>Startup commands or env vars are configured but not enabled.</b><br>" +
            "These can run arbitrary code. Only enable if you trust this project.</html>"
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
        val hasCommandsOrEnv = sharedModel?.getTabs()?.any { it.startupCommand.isNotBlank() || it.env.isNotEmpty() } ?: false
        val isTrusted = trustedSettings.isProjectTrusted(project)
        trustBannerPanel?.isVisible = hasCommandsOrEnv && !isTrusted
    }

    override fun isModified(): Boolean {
        val settings = TerminalManagerSettings.getInstance(project)
        if (enabledCheckbox?.isSelected != settings.enabled) return true
        if (closeExistingCheckbox?.isSelected != settings.closeExistingTerminals) return true

        val currentTabs = sharedModel?.getTabs() ?: return false
        if (currentTabs.size != settings.tabs.size) return true
        val sharedChanged = currentTabs.zip(settings.tabs).any { (current, saved) ->
            current.name != saved.name ||
            current.shellId != saved.shellId ||
            current.workingDirectory != saved.workingDirectory ||
            current.enabled != saved.enabled ||
            current.startupCommand != saved.startupCommand ||
            current.color != saved.color ||
            current.useTmux != saved.useTmux ||
            current.openIf != saved.openIf ||
            current.env != saved.env
        }
        if (sharedChanged) return true

        val currentLocal = buildLocalConfig().takeUnless { ConfigMerger.isLocalEmpty(it) }
        val savedLocal = settings.loadLocalConfig()?.takeUnless { ConfigMerger.isLocalEmpty(it) }
        return currentLocal != savedLocal
    }

    override fun apply() {
        val settings = TerminalManagerSettings.getInstance(project)
        settings.enabled = enabledCheckbox?.isSelected ?: true
        settings.closeExistingTerminals = closeExistingCheckbox?.isSelected ?: false

        val sharedTabs = sharedModel?.getTabs()?.map { it.copy() }?.toMutableList() ?: mutableListOf()
        TerminalManagerSettings.assignMissingIds(sharedTabs) { UUID.randomUUID().toString() }
        settings.tabs.clear()
        settings.tabs.addAll(sharedTabs)
        settings.save()
        // Re-sync model so newly assigned ids are visible to override targets.
        sharedModel?.setTabs(sharedTabs.map { it.copy() }.toMutableList())

        val localConfig = buildLocalConfig().takeUnless { ConfigMerger.isLocalEmpty(it) }
        settings.saveLocalConfig(localConfig)
        maybeOfferGitignore(localConfig)

        updateTrustBannerVisibility()
        refreshPreview()
    }

    override fun reset() {
        val settings = TerminalManagerSettings.getInstance(project)
        settings.load()
        enabledCheckbox?.isSelected = settings.enabled
        closeExistingCheckbox?.isSelected = settings.closeExistingTerminals
        sharedModel?.setTabs(settings.tabs.map { it.copy() }.toMutableList())
        localModel?.setRows(loadLocalRows(settings))
        refreshPreview()
    }

    private fun maybeOfferGitignore(localConfig: LocalTerminalManagerConfig?) {
        if (localConfig == null) return
        val app = TerminalManagerAppSettings.getInstance()
        if (app.gitignorePromptDismissed) return
        val basePath = project.basePath ?: return
        val gitignore = File(basePath, ".gitignore")
        val entry = ".terminals/startup-terminals.local.json"
        val existingText = if (gitignore.exists()) gitignore.readText() else ""
        if (existingText.lineSequence().any { it.trim() == entry }) {
            app.gitignorePromptDismissed = true
            return
        }
        val accepted = MessageDialogBuilder.yesNo(
            "Ignore Local Terminal Overrides?",
            "Add \"$entry\" to .gitignore so your local overrides aren't committed?"
        ).ask(project)
        if (accepted) {
            val prefix = if (existingText.isNotEmpty() && !existingText.endsWith("\n")) "\n" else ""
            gitignore.appendText("$prefix$entry\n")
        }
        app.gitignorePromptDismissed = true
    }

    override fun disposeUIResources() {
        mainPanel = null
        enabledCheckbox = null
        closeExistingCheckbox = null
        sharedModel = null
        localModel = null
        previewModel = null
        trustBannerPanel = null
    }
}

// ---------------------------------------------------------------------------
// Shared base-config table
// ---------------------------------------------------------------------------

/**
 * Renders the "Enable tmux" boolean column. The checkbox is greyed out (disabled) on
 * platforms where tmux isn't available (Windows), matching the read-only cell behavior.
 */
class TmuxCheckBoxRenderer : JCheckBox(), TableCellRenderer {
    init {
        horizontalAlignment = SwingConstants.CENTER
        isBorderPainted = false
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        focused: Boolean,
        row: Int,
        column: Int
    ): Component {
        isSelected = value as? Boolean ?: false
        isEnabled = ShellDetector.isTmuxPlatform()
        background = if (selected) table.selectionBackground else table.background
        foreground = if (selected) table.selectionForeground else table.foreground
        return this
    }
}

class TerminalTabTableModel(private var tabs: MutableList<TerminalTabConfig>) : AbstractTableModel() {

    private val columnNames = arrayOf("Name", "Shell", "Working Directory", "Startup Command", "Color", "Open If", "Env", "Enable tmux", "Enabled")

    override fun getRowCount(): Int = tabs.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> =
        when (columnIndex) {
            7, 8 -> java.lang.Boolean::class.java
            else -> String::class.java
        }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        // tmux column is only editable on macOS/Linux; greyed out (read-only) elsewhere.
        if (columnIndex == 7) return ShellDetector.isTmuxPlatform()
        return true
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val tab = tabs[rowIndex]
        return when (columnIndex) {
            0 -> tab.name
            1 -> tab.shellId
            2 -> tab.workingDirectory
            3 -> tab.startupCommand
            4 -> tab.color
            5 -> tab.openIf
            6 -> "${tab.env.size} vars"
            7 -> tab.useTmux
            8 -> tab.enabled
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
            4 -> tab.color = (aValue as? TabColor)?.id ?: (aValue as? String) ?: ""
            5 -> tab.openIf = aValue as? String ?: ""
            6 -> { /* env edited via EnvCellEditor + setEnv */ }
            7 -> tab.useTmux = aValue as? Boolean ?: false
            8 -> tab.enabled = aValue as? Boolean ?: true
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun getEnv(row: Int): Map<String, String> = tabs[row].env
    fun setEnv(row: Int, env: Map<String, String>) {
        tabs[row].env = LinkedHashMap(env)
        fireTableCellUpdated(row, 6)
    }

    fun addRow() {
        tabs.add(TerminalTabConfig(id = UUID.randomUUID().toString()))
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

// ---------------------------------------------------------------------------
// Local override table
// ---------------------------------------------------------------------------

/** Mutable UI row backing the local override table; converts to/from [LocalTerminalTabOverride]. */
class LocalRow(
    var isPersonal: Boolean = false,
    var targetId: String? = null,
    var id: String? = null,
    var name: String? = null,
    var shellId: String? = null,
    var workingDirectory: String? = null,
    var startupCommand: String? = null,
    var color: String? = null,
    var useTmux: Boolean? = null,
    var env: MutableMap<String, String>? = null,
    var openIf: String? = null,
    var muted: Boolean? = null
) {
    fun toOverride(): LocalTerminalTabOverride = if (isPersonal) {
        LocalTerminalTabOverride(
            id = id ?: UUID.randomUUID().toString(),
            isPersonal = true,
            name = name?.ifBlank { null } ?: "Terminal",
            shellId = shellId?.ifBlank { null },
            workingDirectory = workingDirectory?.ifBlank { null },
            startupCommand = startupCommand?.ifBlank { null },
            color = color?.ifBlank { null },
            useTmux = useTmux,
            env = env?.takeIf { it.isNotEmpty() },
            openIf = openIf?.ifBlank { null }
        )
    } else {
        LocalTerminalTabOverride(
            targetId = targetId?.ifBlank { null },
            isPersonal = false,
            shellId = shellId?.ifBlank { null },
            workingDirectory = workingDirectory?.ifBlank { null },
            startupCommand = startupCommand?.ifBlank { null },
            color = color?.ifBlank { null },
            useTmux = useTmux,
            env = env?.takeIf { it.isNotEmpty() },
            openIf = openIf?.ifBlank { null },
            muted = muted
        )
    }

    companion object {
        fun from(ov: LocalTerminalTabOverride): LocalRow = LocalRow(
            isPersonal = ov.isPersonal,
            targetId = ov.targetId,
            id = ov.id,
            name = ov.name,
            shellId = ov.shellId,
            workingDirectory = ov.workingDirectory,
            startupCommand = ov.startupCommand,
            color = ov.color,
            useTmux = ov.useTmux,
            env = ov.env?.let { LinkedHashMap(it) },
            openIf = ov.openIf,
            muted = ov.muted
        )
    }
}

const val PERSONAL_TARGET = "(personal tab)"
const val UNSET_TARGET = "(choose tab)"

class LocalOverrideTableModel(
    private var rows: MutableList<LocalRow>,
    private val baseTabsProvider: () -> List<TerminalTabConfig>
) : AbstractTableModel() {

    private val columnNames = arrayOf("Target", "Name", "Shell", "Working Directory", "Startup Command", "Color", "Open If", "Env", "Muted")

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 8) java.lang.Boolean::class.java else String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        val row = rows[rowIndex]
        return when (columnIndex) {
            1 -> row.isPersonal       // Name editable only for personal rows
            8 -> !row.isPersonal      // Muted only for override rows
            else -> true
        }
    }

    /** Display options for the Target combo: existing base-tab names plus the personal sentinel. */
    fun targetOptions(): List<String> = baseTabsProvider().map { it.name } + PERSONAL_TARGET

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> when {
                row.isPersonal -> PERSONAL_TARGET
                else -> baseTabsProvider().find { it.id == row.targetId }?.name ?: UNSET_TARGET
            }
            1 -> if (row.isPersonal) (row.name ?: "") else "—"
            2 -> row.shellId ?: ""
            3 -> row.workingDirectory ?: ""
            4 -> row.startupCommand ?: ""
            5 -> row.color ?: ""
            6 -> row.openIf ?: ""
            7 -> "${row.env?.size ?: 0} vars"
            8 -> row.muted ?: false
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when (columnIndex) {
            0 -> {
                val display = aValue as? String ?: return
                if (display == PERSONAL_TARGET) {
                    row.isPersonal = true
                    row.targetId = null
                    if (row.id == null) row.id = UUID.randomUUID().toString()
                    if (row.name.isNullOrBlank()) row.name = "Terminal"
                } else {
                    val base = baseTabsProvider().find { it.name == display }
                    row.isPersonal = false
                    row.targetId = base?.id
                }
                fireTableRowsUpdated(rowIndex, rowIndex)
            }
            1 -> row.name = aValue as? String
            2 -> row.shellId = (aValue as? ShellInfo)?.id?.ifBlank { null } ?: (aValue as? String)?.ifBlank { null }
            3 -> row.workingDirectory = aValue as? String
            4 -> row.startupCommand = aValue as? String
            5 -> row.color = (aValue as? String)?.let { if (it == INHERIT_LABEL) null else it }
            6 -> row.openIf = aValue as? String
            7 -> { /* env edited via EnvCellEditor + setEnv */ }
            8 -> row.muted = (aValue as? Boolean)?.takeIf { it }
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun getEnv(row: Int): Map<String, String> = rows[row].env ?: emptyMap()
    fun setEnv(row: Int, env: Map<String, String>) {
        rows[row].env = if (env.isEmpty()) null else LinkedHashMap(env)
        fireTableCellUpdated(row, 7)
    }

    fun addOverrideRow() {
        rows.add(LocalRow(isPersonal = false))
        fireTableRowsInserted(rows.size - 1, rows.size - 1)
    }

    fun addPersonalRow() {
        rows.add(LocalRow(isPersonal = true, id = UUID.randomUUID().toString(), name = "Terminal"))
        fireTableRowsInserted(rows.size - 1, rows.size - 1)
    }

    fun removeRow(row: Int) {
        if (row in rows.indices) {
            rows.removeAt(row)
            fireTableRowsDeleted(row, row)
        }
    }

    fun getRows(): List<LocalRow> = rows.toList()

    fun setRows(newRows: MutableList<LocalRow>) {
        rows = newRows
        fireTableDataChanged()
    }
}

// ---------------------------------------------------------------------------
// Cell editors / renderers
// ---------------------------------------------------------------------------

const val INHERIT_LABEL = "(inherit)"

class EnvCellEditor(
    private val project: Project,
    private val getEnv: (Int) -> Map<String, String>,
    private val setEnv: (Int, Map<String, String>) -> Unit
) : AbstractCellEditor(), TableCellEditor {
    private val button = JButton()
    private var currentRow = -1

    init {
        button.addActionListener {
            val dialog = EnvVarEditorDialog(project, getEnv(currentRow))
            if (dialog.showAndGet()) {
                setEnv(currentRow, dialog.getResult())
            }
            fireEditingStopped()
        }
    }

    override fun getCellEditorValue(): Any = button.text

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        currentRow = row
        button.text = value as? String ?: ""
        return button
    }
}

class TargetCellEditor(private val optionsProvider: () -> List<String>) : AbstractCellEditor(), TableCellEditor {
    private val comboBox = JComboBox<String>()

    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: ""

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        comboBox.removeAllItems()
        optionsProvider().forEach { comboBox.addItem(it) }
        val current = value as? String
        if (current != null && (0 until comboBox.itemCount).any { comboBox.getItemAt(it) == current }) {
            comboBox.selectedItem = current
        }
        return comboBox
    }
}

class InheritShellCellEditor : AbstractCellEditor(), TableCellEditor {
    private val inherit = ShellInfo("", INHERIT_LABEL, emptyList())
    private val comboBox: JComboBox<ShellInfo>

    init {
        val shells = listOf(inherit) + ShellDetector.getAvailableShells()
        comboBox = JComboBox(shells.toTypedArray())
        comboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? ShellInfo)?.displayName ?: value?.toString() ?: ""
                return this
            }
        }
    }

    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: inherit

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        val shellId = value as? String ?: ""
        val shells = (0 until comboBox.itemCount).map { comboBox.getItemAt(it) }
        comboBox.selectedItem = shells.find { it.id == shellId } ?: shells.first()
        return comboBox
    }
}

class InheritShellCellRenderer : TableCellRenderer {
    private val label = JLabel()
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val shellId = value as? String ?: ""
        label.text = if (shellId.isBlank()) INHERIT_LABEL else (ShellDetector.getAvailableShells().find { it.id == shellId }?.displayName ?: shellId)
        if (isSelected) {
            label.background = table?.selectionBackground; label.foreground = table?.selectionForeground; label.isOpaque = true
        } else {
            label.background = table?.background; label.foreground = table?.foreground; label.isOpaque = false
        }
        return label
    }
}

class InheritColorCellEditor : AbstractCellEditor(), TableCellEditor {
    private val comboBox: JComboBox<String>

    init {
        val items = listOf(INHERIT_LABEL) + TabColor.entries.filter { it != TabColor.NONE }.map { it.id }
        comboBox = JComboBox(items.toTypedArray())
        comboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val id = value as? String
                if (id == INHERIT_LABEL || id == null) {
                    text = INHERIT_LABEL; icon = null
                } else {
                    val c = TabColor.fromId(id)
                    text = c.displayName
                    icon = c.awtColor?.let { TabColorIcon(it) }
                }
                return this
            }
        }
    }

    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: INHERIT_LABEL

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        val colorId = value as? String ?: ""
        comboBox.selectedItem = if (colorId.isBlank()) INHERIT_LABEL else colorId
        return comboBox
    }
}

class ShellInfoCellEditor : AbstractCellEditor(), TableCellEditor {
    private val comboBox: JComboBox<ShellInfo>

    init {
        val shells = ShellDetector.getAvailableShells()
        comboBox = JComboBox(shells.toTypedArray())
        comboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? ShellInfo)?.displayName ?: value?.toString() ?: ""
                return this
            }
        }
    }

    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: ShellDetector.getAvailableShells().first()

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        val shellId = value as? String ?: "default"
        val shells = ShellDetector.getAvailableShells()
        comboBox.selectedItem = shells.find { it.id == shellId } ?: shells.first()
        return comboBox
    }
}

class ShellInfoCellRenderer : TableCellRenderer {
    private val label = JLabel()
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val shellId = value as? String ?: "default"
        label.text = ShellDetector.getAvailableShells().find { it.id == shellId }?.displayName ?: shellId
        if (isSelected) {
            label.background = table?.selectionBackground; label.foreground = table?.selectionForeground; label.isOpaque = true
        } else {
            label.background = table?.background; label.foreground = table?.foreground; label.isOpaque = false
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
            val chooser = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, baseDir)
            chooser?.let { selected ->
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

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        textField.text = value as? String ?: ""
        return panel
    }
}

class TabColorCellEditor : AbstractCellEditor(), TableCellEditor {
    private val comboBox: JComboBox<TabColor>

    init {
        comboBox = JComboBox(TabColor.entries.toTypedArray())
        comboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val tabColor = value as? TabColor
                text = tabColor?.displayName ?: ""
                icon = tabColor?.awtColor?.let { TabColorIcon(it) }
                return this
            }
        }
    }

    override fun getCellEditorValue(): Any = comboBox.selectedItem ?: TabColor.NONE

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        val colorId = value as? String ?: ""
        comboBox.selectedItem = TabColor.fromId(colorId)
        return comboBox
    }
}

class TabColorCellRenderer : TableCellRenderer {
    private val label = JLabel()
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val colorId = value as? String ?: ""
        val tabColor = TabColor.fromId(colorId)
        label.text = tabColor.displayName
        label.icon = tabColor.awtColor?.let { TabColorIcon(it) }
        if (isSelected) {
            label.background = table?.selectionBackground; label.foreground = table?.selectionForeground; label.isOpaque = true
        } else {
            label.background = table?.background; label.foreground = table?.foreground; label.isOpaque = false
        }
        return label
    }
}
