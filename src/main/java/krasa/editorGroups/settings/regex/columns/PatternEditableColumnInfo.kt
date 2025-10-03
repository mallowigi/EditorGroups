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

import com.intellij.openapi.Disposable
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.RegexGroupModel
import krasa.editorGroups.settings.regex.internal.RegExpTableCellRenderer
import krasa.editorGroups.settings.regex.internal.RegexpEditor
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/** Editable column info for [RegexGroupModel] pattern. */
class PatternEditableColumnInfo(private val parent: Disposable, private val editable: Boolean) :
  EditableColumnInfo<RegexGroupModel, String>(message("RegexEditorConfigurable.columns.pattern")) {
  /** Whether the regex highlight is enabled (disabled by default because it's slow) */
  var toggledPattern: Boolean = false

  /**
   * The value of the column is the matcher
   *
   * @param item the [RegexGroupModel]
   * @return [RegexGroupModel] matcher
   */
  override fun valueOf(item: RegexGroupModel): String = item.myRegex

  /**
   * Set the [RegexGroupModel]'s matcher
   *
   * @param item the [RegexGroupModel]
   * @param value the string value for the matcher
   */
  override fun setValue(item: RegexGroupModel, value: String) {
    item.myRegex = value
    item.touched = true
  }

  /**
   * Creates an editor for the [RegexGroupModel] pattern, which validates regexps
   *
   * @param item the [RegexGroupModel]
   * @return the [TableCellEditor]
   */
  override fun getEditor(item: RegexGroupModel): TableCellEditor {
    val cellEditor = ExtendableTextField()

    return RegexpEditor(cellEditor, parent)
  }

  /**
   * Renders the regexp.
   *
   * @param item the [RegexGroupModel]
   * @return the [TableCellRenderer]
   */
  override fun getRenderer(item: RegexGroupModel): TableCellRenderer = RegExpTableCellRenderer()

  /** Set a regex language highlighter for this column. */
  override fun getCustomizedRenderer(o: RegexGroupModel, renderer: TableCellRenderer): TableCellRenderer = ModifiedInfoCellRenderer(o)

  /**
   * Whether the cell is editable
   *
   * @param item the [RegexGroupModel]
   * @return true if editable
   */
  override fun isCellEditable(item: RegexGroupModel): Boolean = editable
}