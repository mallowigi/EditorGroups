package krasa.editorGroups.settings.regex

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.table.JBTable
import com.intellij.ui.table.TableView
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.table.ComboBoxTableCellEditor
import krasa.editorGroups.model.RegexGroupModel
import krasa.editorGroups.model.Scope
import krasa.editorGroups.support.generateColor
import krasa.editorGroups.support.toHex
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.event.DocumentEvent

class RegexTableModelEditor(
  items: List<RegexGroupModel>,
  columns: Array<ColumnInfo<*, *>>,
  itemEditor: CollectionItemEditor<RegexGroupModel>,
  emptyText: String,
  val searchTextField: SearchTextField?,
) : CollectionModelEditor<RegexGroupModel, CollectionItemEditor<RegexGroupModel>?>(itemEditor) {
  private val table: TableView<RegexGroupModel>
  private val toolbarDecorator: ToolbarDecorator
  private val model: RegexGroupsTableModel = RegexGroupsTableModel(columns, items)

  /** Backing field for model's unfiltered list. */
  private val myList: MutableList<RegexGroupModel>
    get() = model.allItems

  /** Backing field for model's filtered list. */
  private val myFilteredList: MutableList<RegexGroupModel>
    get() = model.filteredItems

  /** Own Increment for adding. */
  private var increment: Int = 0

  init {
    initUnfilteredList()

    // Table settings
    table = TableView(model)
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    table.isStriped = true
    table.setMaxItemsForSizeCalculation(MAX_ITEMS)
    table.ignoreRepaint = true
    table.fillsViewportHeight = true
    table.setShowGrid(false)
    table.setDefaultEditor(Enum::class.java, ComboBoxTableCellEditor.INSTANCE)
    table.setEnableAntialiasing(true)
    table.intercellSpacing = Dimension(0, 0)
    table.preferredScrollableViewportSize = JBUI.size(PREFERABLE_VIEWPORT_WIDTH, PREFERABLE_VIEWPORT_HEIGHT)
    table.visibleRowCount = MIN_ROW_COUNT
    table.rowHeight = ROW_HEIGHT
    table.rowMargin = 0
    // sort by priority but remove the column from the table
    table.rowSorter.sortKeys = listOf(
      RowSorter.SortKey(
        Columns.PRIORITY.index,
        SortOrder.DESCENDING
      )
    )
    table.removeColumn(table.columnModel.getColumn(Columns.TOUCHED.index))

    // Special support for checkbox: toggle by clicking or space
    TableUtil.setupCheckboxColumn(
      table.columnModel.getColumn(Columns.ENABLED.index),
      0
    )
    JBTable.setupCheckboxShortcut(
      table,
      Columns.ENABLED.index
    )

    // Display empty text when loading
    table.emptyText.setFont(UIUtil.getLabelFont().deriveFont(LOADING_FONT_SIZE))
    table.emptyText.text = emptyText

    // Setup actions
    toolbarDecorator = ToolbarDecorator.createDecorator(table, this)
    toolbarDecorator.disableUpDownActions()

    // Search and filter table
    if (searchTextField != null) {
      table.addKeyListener(object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) {
          val keyChar = e.keyChar
          if (Character.isLetter(keyChar) || Character.isDigit(keyChar)) {
            searchTextField.text = keyChar.toString()
            searchTextField.requestFocus()
          }
          super.keyPressed(e)
        }
      })

      searchTextField.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = filterTable()
      })
    }

    // Color picker listening
    MyColorPickerListener().installOn(table)
  }

  constructor(
    columns: Array<ColumnInfo<*, *>>,
    itemEditor: RegexTableItemEditor,
    emptyText: String,
    searchTextField: SearchTextField,
  ) : this(emptyList<RegexGroupModel>(), columns, itemEditor, emptyText, searchTextField)

  /** Inits the unfiltered list (before any search) */
  private fun initUnfilteredList() {
    myList.clear()
    myList.addAll(model.items)
    filterTable()
  }

  /** Filters the table - this will set the [model]'s filteredItems. */
  private fun filterTable() {
    if (searchTextField == null) return

    val text: String = searchTextField.text.trim()
    myFilteredList.clear()
    // Search by name or pattern only
    for (model in myList) {
      if (text.isEmpty() || StringUtil.containsIgnoreCase(model.myName, text) || StringUtil.containsIgnoreCase(model.myRegex, text)
      ) {
        myFilteredList.add(model)
      }
    }
//    model.filteredItems = myFilteredList
    model.fireTableDataChanged()
  }

  /**
   * Convenience method to disable/enable the table
   *
   * @param isEnabled new enabled state
   * @return self
   */
  fun enabled(isEnabled: Boolean): RegexTableModelEditor {
    table.isEnabled = isEnabled
    return this
  }

  /** Returns the [RegexGroupsTableModel]. */
  fun getModel(): RegexGroupsTableModel = model

  /** Create component with toolbar. */
  fun createComponent(): JComponent = toolbarDecorator.createPanel()

  /**
   * Apply changes to elements
   *
   * @return the new items after changes
   */
  fun apply(): List<RegexGroupModel> {
    if (helper.hasModifiedItems()) {
      val columns = model.columnInfos

      helper.process { newItem: RegexGroupModel, oldItem: RegexGroupModel ->
        // set all modified items new values
        for (column in columns) {
          if (column.isCellEditable(newItem)) column.setValue(oldItem, column.valueOf(newItem))
        }
        // Sets the newItem in place of the old item
        model.items[ContainerUtil.indexOfIdentity(model.items, newItem)] = oldItem
        true
      }
    }
    // Resets the helper
    helper.reset(model.items)
    return model.items
  }

  /**
   * Return the model items
   *
   * @return the model items
   */
  override fun getItems(): List<RegexGroupModel?> = model.items

  /**
   * Resets the [model]'s items
   *
   * @param originalItems the elements
   */
  override fun reset(originalItems: List<RegexGroupModel>) {
    super.reset(originalItems)
    model.allItems = ArrayList(originalItems)
    model.filteredItems = ArrayList(originalItems)
    initUnfilteredList()
  }

  /** Create a new custom association. */
  override fun createElement(): RegexGroupModel {
    increment++

    val newModel = RegexGroupModel()
    newModel.name = "New Regex ($increment)" // NON-NLS
    newModel.touched = true
    newModel.scope = Scope.CURRENT_FOLDER
    newModel.regex = ".*"
    newModel.notComparingGroups = null
    newModel.myColor = generateColor()
    newModel.priority = 0
    return newModel
  }

  /**
   * Overrides [silentlyReplaceItem] - we need to modify the unfiltered list when a change occurs since we're working on the filtered list
   *
   * @param oldItem item changed (in the filtered list)
   * @param newItem new item to insert
   * @param index index in the filtered list
   */
  override fun silentlyReplaceItem(oldItem: RegexGroupModel, newItem: RegexGroupModel, index: Int) {
    super.silentlyReplaceItem(oldItem, newItem, index)
    newItem.touched = true
    // silently replace item in unfiltered list
    val items = model.allItems
    val allItemsIndex = items.indexOfFirst { it.name == newItem.name }
    val i = when (allItemsIndex) {
      -1   -> ContainerUtil.indexOfIdentity(items, oldItem)
      else -> allItemsIndex
    }
    items[i] = newItem
  }

  inner class RegexGroupsTableModel(columnNames: Array<ColumnInfo<*, *>>, items: List<RegexGroupModel>) :
    ListTableModel<RegexGroupModel>(columnNames, items) {

    /** This contains all items, before any filter is applied. This is also what will be persisted. */
    var allItems: MutableList<RegexGroupModel> = items.toMutableList()

    /** This is the currently filtered table. */
    var filteredItems: MutableList<RegexGroupModel> = items.toMutableList()
      set(value) {
        field = value
        super.setItems(value)
      }

    /**
     * We display only the filtered items
     *
     * @return the [filteredItems]
     */
    override fun getItems(): MutableList<RegexGroupModel> = filteredItems

    /**
     * When items are set, we reset the table's items
     *
     * @param items
     */
    override fun setItems(items: MutableList<RegexGroupModel>) {
      allItems = items
      filteredItems = items
      fireTableDataChanged()
    }

    /**
     * Remove a row @unused
     *
     * @param index
     */
    override fun removeRow(index: Int) {
      val item = getItem(index)
      helper.remove(item)
      super.removeRow(index)
      allItems.remove(item)
      fireTableDataChanged()
    }

    override fun addRow(item: RegexGroupModel) {
      super.addRow(item)
      allItems.add(item)
    }

    /**
     * Set the value at the given row and column using the [helper]
     *
     * @param aValue value to set
     * @param rowIndex row number
     * @param columnIndex column number
     */
    override fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {
      if (rowIndex < rowCount) {
        val columnInfo = columnInfos[columnIndex]
        val item = getItem(rowIndex)
        val oldValue = columnInfo.valueOf(item)

        val comparator = when (columnInfo.columnClass) {
          Scope::class.java  -> Comparing.equal(oldValue, aValue)
          String::class.java -> Comparing.strEqual(oldValue as? String, aValue as String)
          else               -> Comparing.equal(oldValue, aValue)
        }

        if (!comparator) {
          columnInfo.setValue(helper.getMutable(item, rowIndex), aValue)
        }
      }
    }
  }

  inner class MyColorPickerListener : ClickListener() {
    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
      val point = event.point
      val row: Int = table.rowAtPoint(point)
      val column: Int = table.columnAtPoint(point) + 1 // Because the touched takes a slot...
      if (row < 0 || column < 0) return false
      val modelIndex = table.convertRowIndexToModel(row)

      if (modelIndex < 0 || modelIndex >= table.rowCount) return false

      return when (column) {
        Columns.COLOR.index -> setGroupColor(modelIndex)
        else                -> false
      }
    }

    private fun setGroupColor(row: Int): Boolean {
      val colorValue: Any = model.getValueAt(row, Columns.COLOR.index)
      val modelColor: Color = ColorUtil.fromHex(colorValue as String)

      ColorChooserService.getInstance().showPopup(null, modelColor, { color, _ ->
        color?.let {
          model.setValueAt(
            aValue = it.toHex(),
            rowIndex = row,
            columnIndex = Columns.COLOR.index
          )
        }
      })

      return true
    }
  }

  companion object {
    const val MAX_ITEMS: Int = 60
    const val MIN_ROW_COUNT: Int = 18
    const val ROW_HEIGHT: Int = 32
    const val LOADING_FONT_SIZE: Float = 24.0F
    const val PREFERABLE_VIEWPORT_WIDTH: Int = 200
    const val PREFERABLE_VIEWPORT_HEIGHT: Int = 280

    @Suppress("unused") // NON-NLS
    private enum class Columns(val index: Int) {
      ENABLED(0),
      TOUCHED(1),
      NAME(2),
      REGEX(3),
      SCOPE(4),
      NOT_COMPARING_GROUPS(5),
      COLOR(6),
      PRIORITY(7),
    }
  }
}