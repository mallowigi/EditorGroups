package krasa.editorGroups.settings.regex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SearchTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ColumnInfo
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.RegexGroupModels
import krasa.editorGroups.settings.regex.columns.*
import javax.swing.JComponent
import javax.swing.JPanel

internal class RegexEditorConfigurable :
  BoundSearchableConfigurable(
    message("settings.regex"),
    ID
  ),
  Disposable {
  private var main: DialogPanel? = null

  private val settings: RegexGroupsSettings = RegexGroupsSettings.instance
  private val settingsClone: RegexGroupsSettings = settings.clone()

  private var regexTablePanel: JPanel? = null
  private var regexSearch: SearchTextField = SearchTextField()
  private lateinit var regexModelTable: JComponent
  private var regexModelTableEditor: RegexTableModelEditor? = null

  private val columns = arrayOf<ColumnInfo<*, *>>(
    EnabledColumnInfo(),
    TouchedColumnInfo(),
    NameEditableColumnInfo(parent = this, editable = true),
    PatternEditableColumnInfo(parent = this, editable = true),
    ScopeEditableColumnInfo(editable = true),
    NotComparingGroupsEditableColumnInfo(parent = this, editable = true),
    IconColorEditableColumnInfo(parent = this),
    PriorityColumnInfo(parent = this, editable = true),
  )

  init {
    createTable()

    regexTablePanel = panel {
      row {
        cell(regexSearch)
          .align(Align.FILL)
      }

      row {
        cell(regexModelTable)
          .align(Align.FILL)
      }
    }

    main = panel {
      row {
        comment(message("RegexEditorConfigurable.explanation.text"))
      }

      row {
        comment(message("RegexEditorConfigurable.explanation2.text"))
      }

      row {
        comment(message("RegexEditorConfigurable.explanation3.text"))
      }

      row {
        cell(regexTablePanel ?: return@row)
          .align(Align.FILL)
      }

      row {
        button(message("clear.regex.groups")) { resetSettings() }
          .resizableColumn()
          .align(AlignX.RIGHT)
      }
    }

    regexSearch.textEditor.emptyText.text = message("fileSearch.placeholder")
  }

  /** Create the file icons. */
  private fun createTable() {
    val itemEditor = RegexTableItemEditor()
    regexModelTableEditor = RegexTableModelEditor(
      columns,
      itemEditor,
      message("no.regex.models"),
      regexSearch,
    )
    regexModelTable = (regexModelTableEditor ?: return).createComponent()
  }

  private fun resetSettings() {
    if (Messages.showOkCancelDialog(
        message("RegexEditorConfigurable.resetDialog.text"),
        message("RegexEditorConfigurable.resetDialog.title"),
        message("ok"),
        message("cancel"),
        Messages.getQuestionIcon()
      ) != Messages.OK
    ) {
      return
    }

    settings.reset()

    ApplicationManager.getApplication().invokeLater {
      if (regexModelTableEditor != null) {
        (regexModelTableEditor ?: return@invokeLater).reset(settings.regexGroupModels.regexModels)
      }
    }
  }

  override fun createPanel(): DialogPanel {
    loadData()
    return main!!
  }

  private fun loadData() {
    ApplicationManager.getApplication().invokeLater {
      if (regexModelTableEditor != null) {
        (regexModelTableEditor ?: return@invokeLater).reset(settings.regexGroupModels.regexModels)
      }
    }
  }

  override fun getId(): String = ID

  override fun getDisplayName(): String = message("settings.regex")

  override fun isModified(): Boolean {
    var isModified = super.isModified()
    if (regexModelTableEditor != null) {
      isModified = isModified || settings.isModified(regexModelTableEditor!!.getModel().allItems)
    }
    return isModified
  }

  override fun apply() {
    super.apply()
    settingsClone.regexGroupModels = getRegexGroups()

    settings.apply(settingsClone)
    RegexGroupsSettings.instance.fireChanged()
  }

  private fun getRegexGroups(): RegexGroupModels {
    assert(regexModelTableEditor != null)

    val allItems = regexModelTableEditor!!.getModel().allItems
    val models = RegexGroupModels()
    models.regexModels = allItems.toMutableList()
    return models
  }

  override fun dispose() {
    regexTablePanel = null
  }

  companion object {
    const val ID: String = "RegexEditorConfigurable"
  }
}
