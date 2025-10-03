/*
 * The MIT License (MIT)
 *
 *  Copyright (c) 2015-2022 Elior "Mallowigi" Boukhobza
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
package krasa.editorGroups.settings.regex.columns

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.RegexGroupModel
import krasa.editorGroups.model.Scope
import java.awt.Component
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/** Editable column info for [krasa.editorGroups.model.RegexGroupModel] scope. */
class ScopeEditableColumnInfo(private val editable: Boolean) :
  EditableColumnInfo<RegexGroupModel, Scope>(message("RegexEditorConfigurable.columns.scope")) {
  /**
   * The value of the column is the scope
   *
   * @param item the [RegexGroupModel]
   * @return [RegexGroupModel] scope
   */
  override fun valueOf(item: RegexGroupModel): Scope = item.myScope

  /**
   * Set [RegexGroupModel]'s scope
   *
   * @param item the [RegexGroupModel]
   * @param value the new scope
   */
  override fun setValue(item: RegexGroupModel, value: Scope) {
    item.myScope = value
    item.touched = true
  }

  /**
   * Creates an editor for the scope, with empty value validation
   *
   * @param item the [RegexGroupModel]
   * @return the [TableCellEditor]
   */
  override fun getEditor(item: RegexGroupModel): TableCellEditor {
    val comboBox = ComboBox(Scope.entries.toTypedArray())
    comboBox.renderer = ScopeCellRenderer()
    return DefaultCellEditor(comboBox)
  }

  override fun getRenderer(item: RegexGroupModel): TableCellRenderer = ScopeCellRenderer()

  override fun isCellEditable(item: RegexGroupModel): Boolean = editable

  override fun getColumnClass(): Class<*> = Scope::class.java

  class ScopeCellRenderer : DefaultTableCellRenderer(), ListCellRenderer<Scope> {
    private val renderer = DefaultListCellRenderer()

    override fun getListCellRendererComponent(
      list: JList<out Scope>?,
      value: Scope?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      val label = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
      label.text = value?.label
      return label
    }

    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int
    ): Component {
      val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      if (value !is Scope) return component

      text = value.label
      return component
    }
  }
}
