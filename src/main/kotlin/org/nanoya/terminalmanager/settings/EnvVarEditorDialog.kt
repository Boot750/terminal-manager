package org.nanoya.terminalmanager.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

class EnvVarEditorDialog(
    project: Project?,
    initial: Map<String, String>
) : DialogWrapper(project) {

    private val model = DefaultTableModel(arrayOf<Any>("Name", "Value"), 0).apply {
        initial.forEach { (k, v) -> addRow(arrayOf<Any>(k, v)) }
    }
    private val table = JBTable(model)

    init {
        title = "Environment Variables"
        init()
    }

    override fun createCenterPanel(): JComponent =
        ToolbarDecorator.createDecorator(table)
            .setAddAction { model.addRow(arrayOf<Any>("", "")) }
            .setRemoveAction {
                val r = table.selectedRow
                if (r >= 0) {
                    if (table.isEditing) table.cellEditor.stopCellEditing()
                    model.removeRow(r)
                }
            }
            .createPanel()

    /** Returns the edited env map, dropping rows with a blank name. */
    fun getResult(): LinkedHashMap<String, String> {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        val result = LinkedHashMap<String, String>()
        for (i in 0 until model.rowCount) {
            val k = (model.getValueAt(i, 0) as? String)?.trim().orEmpty()
            val v = (model.getValueAt(i, 1) as? String).orEmpty()
            if (k.isNotEmpty()) result[k] = v
        }
        return result
    }
}
