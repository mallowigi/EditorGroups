package krasa.editorGroups.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.FontComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.DEFAULT_FONT
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.MAX_GROUP_SIZE_LIMIT
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.MAX_TAB_SIZE_LIMIT
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.MIN_GROUP_SIZE_LIMIT
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.MIN_TAB_SIZE_LIMIT
import krasa.editorGroups.support.bind
import krasa.editorGroups.support.navigateToSettingsPage
import javax.swing.SwingConstants

internal class EditorSettingsConfigurable : BoundSearchableConfigurable(
  message("settings.title"),
  ID
) {
  private var main: DialogPanel? = null

  private val settings: EditorGroupsSettings = EditorGroupsSettings.instance
  private val settingsClone: EditorGroupsSettings = settings.clone()

  /** Init the dialog. */
  fun init() {
    initComponents()
  }

  private fun initComponents() {
    lateinit var isEnabled: Cell<JBCheckBox>
    lateinit var behaviorGroup: CollapsibleRow
    lateinit var presentationGroup: CollapsibleRow
    lateinit var performanceGroup: CollapsibleRow

    val propertyGraph = PropertyGraph()
    val tabsPlacementProperty: GraphProperty<Int> = propertyGraph.property(settingsClone.tabsPlacement)

    main = panel {
      row {
        isEnabled = checkBox(message("EditorGroupsSettings.isShowPanelCheckbox.text"))
          .bindSelected(settingsClone::isShowPanel)
          .comment(message("EditorGroupsSettings.isShowPanelCheckbox.toolTipText"))
      }

      behaviorGroup = collapsibleGroup(message("settings.features.behavior.title")) {
        row {
          checkBox(message("EditorGroupsSettings.isSelectRegexGroupCheckbox.text"))
            .bindSelected(settingsClone::isSelectRegexGroup)
            .comment(message("EditorGroupsSettings.isSelectRegexGroupCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isAutoSameFeatureCheckbox.text"))
            .bindSelected(settingsClone::isAutoSameFeature)
            .comment(message("EditorGroupsSettings.isAutoSameFeatureCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isAutoSameNameCheckbox.text"))
            .bindSelected(settingsClone::isAutoSameName)
            .comment(message("EditorGroupsSettings.isAutoSameNameCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isAutoFoldersCheckbox.text"))
            .bindSelected(settingsClone::isAutoFolders)
            .comment(message("EditorGroupsSettings.isAutoFoldersCheckbox.toolTipText"))
        }

        separator()

        row {
          checkBox(message("EditorGroupsSettings.isForceSwitchCheckbox.text"))
            .bindSelected(settingsClone::isForceSwitch)
            .comment(message("EditorGroupsSettings.isForceSwitchCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isHideEmptyCheckbox.text"))
            .bindSelected(settingsClone::isHideEmpty)
            .comment(message("EditorGroupsSettings.isHideEmptyCheckbox.toolTipText"))
        }

        separator()

        row {
          checkBox(message("EditorGroupsSettings.reuseCurrentTab.text"))
            .bindSelected(settingsClone::reuseCurrentTab)
            .comment(message("EditorGroupsSettings.reuseCurrentTab.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isRememberLastGroupCheckbox.text"))
            .bindSelected(settingsClone::isRememberLastGroup)
            .comment(message("EditorGroupsSettings.isRememberLastGroupCheckbox.toolTipText"))
        }
      }
        .also { it.expanded = true }

      presentationGroup = collapsibleGroup(message("settings.features.presentation.title")) {
        row(message("EditorGroupsSettings.tabsPlacement.text")) {
          segmentedButton(listOf(SwingConstants.TOP, SwingConstants.BOTTOM)) {
            text = when (it) {
              SwingConstants.TOP    -> message("EditorGroupsSettings.tabsPlacementTop.text")
              SwingConstants.BOTTOM -> message("EditorGroupsSettings.tabsPlacementBottom.text")
              else                  -> message("EditorGroupsSettings.tabsPlacementTop.text")
            }
          }
            .bind(tabsPlacementProperty)
            .selectedItem = tabsPlacementProperty.get()
        }
          .rowComment(message("EditorGroupsSettings.tabsPlacement.toolTipText"))
          .bottomGap(BottomGap.SMALL)

        row {
          checkBox(message("EditorGroupsSettings.isShowSizeCheckbox.text"))
            .bindSelected(settingsClone::isShowSize)
            .comment(message("EditorGroupsSettings.isShowSizeCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isShowMetaCheckbox.text"))
            .bindSelected(settingsClone::isShowMeta)
            .comment(message("EditorGroupsSettings.isShowMetaCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isCompactTabsCheckbox.text"))
            .bindSelected(settingsClone::isCompactTabs)
            .comment(message("EditorGroupsSettings.isCompactTabsCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isRoundedTabsCheckbox.text"))
            .bindSelected(settingsClone::isRoundedTabs)
            .comment(message("EditorGroupsSettings.isRoundedTabsCheckbox.toolTipText"))
        }

        separator()

        row {
          val tabFontCheckbox = checkBox(message("EditorGroupsSettings.isCustomFont.text"))
            .bindSelected(settingsClone::isCustomFont)
            .gap(RightGap.COLUMNS)
            .comment(message("EditorGroupsSettings.isCustomFont.tooltipText"))

          cell(FontComboBox())
            .enabledIf(tabFontCheckbox.selected)
            .bind(settingsClone::customFont, DEFAULT_FONT)
        }

        row {
          checkBox(message("EditorGroupsSettings.isSmallLabelsCheckbox.text"))
            .bindSelected(settingsClone::isSmallLabels)
            .comment(message("EditorGroupsSettings.isSmallLabelsCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isColorTabsCheckbox.text"))
            .bindSelected(settingsClone::isColorTabs)
            .comment(message("EditorGroupsSettings.isColorTabsCheckbox.toolTipText"))
        }

        indent {
          row {
            link(message("EditorGroupsSettings.fileColorsLink.text")) { navigateToFileStatusColors() }
              .applyToComponent { icon = AllIcons.Ide.Link }
          }
        }

        separator()

        row {
          checkBox(message("EditorGroupsSettings.isGroupSwitchGroupActionCheckbox.text"))
            .bindSelected(settingsClone::isGroupSwitchGroupAction)
            .comment(message("EditorGroupsSettings.isGroupSwitchGroupActionCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isContinuousScrollingCheckbox.text"))
            .bindSelected(settingsClone::isContinuousScrolling)
            .comment(message("EditorGroupsSettings.isContinuousScrollingCheckbox.toolTipText"))
        }
      }
        .also { it.expanded = true }

      performanceGroup = collapsibleGroup(message("settings.features.performance.title")) {
        row {
          checkBox(message("EditorGroupsSettings.isExcludeEditorGroupsFilesCheckbox.text"))
            .bindSelected(settingsClone::isExcludeEditorGroupsFiles)
            .comment(message("EditorGroupsSettings.isExcludeEditorGroupsFilesCheckbox.toolTipText"))
        }

        row {
          checkBox(message("EditorGroupsSettings.isIndexOnlyEditorGroupsFilesCheckbox.text"))
            .bindSelected(settingsClone::isIndexOnlyEditorGroupsFiles)
            .comment(message("EditorGroupsSettings.isIndexOnlyEditorGroupsFilesCheckbox.toolTipText"))
        }

        separator()

        row {
          checkBox(message("EditorGroupsSettings.isInitializeSynchronouslyCheckbox.text"))
            .bindSelected(settingsClone::isInitializeSynchronously)
            .comment(message("EditorGroupsSettings.isInitializeSynchronouslyCheckbox.toolTipText"))
        }

        separator()

        row {
          spinner(MIN_GROUP_SIZE_LIMIT..MAX_GROUP_SIZE_LIMIT, step = 1)
            .label(message("EditorGroupsSettings.groupSizeLimit.text"))
            .bindIntValue(settingsClone::groupSizeLimit)
            .gap(RightGap.COLUMNS)
            .comment(message("EditorGroupsSettings.groupSizeLimit.toolTipText"))
        }

        row {
          spinner(MIN_TAB_SIZE_LIMIT..MAX_TAB_SIZE_LIMIT, step = 1)
            .label(message("EditorGroupsSettings.tabSizeLimit.text"))
            .bindIntValue(settingsClone::tabSizeLimit)
            .gap(RightGap.COLUMNS)
            .comment(message("EditorGroupsSettings.tabSizeLimit.toolTipText"))
        }
      }
        .also { it.expanded = true }

      resetButton { doReset() }
    }

    behaviorGroup.enabledIf(isEnabled.selected)
    presentationGroup.enabledIf(isEnabled.selected)
    performanceGroup.enabledIf(isEnabled.selected)

    // Synchronize changes back to the original property
    tabsPlacementProperty.afterChange { newValue ->
      settingsClone.tabsPlacement = newValue
    }
  }

  private fun doReset() {
    EditorGroupsSettings.instance.askResetSettings {
      settingsClone.reset()
      settings.reset()
      main?.reset()
    }
  }

  private fun navigateToFileStatusColors() {
    navigateToSettingsPage(main ?: return, FILE_COLORS_ID)
  }

  /** Check if the form was modified. */
  override fun isModified(): Boolean {
    if (super.isModified()) return true
    return settings != settingsClone
  }

  /** Create the dialog panel. */
  override fun createPanel(): DialogPanel {
    init()
    return main!!
  }

  /** Apply the settings. */
  override fun apply() {
    super.apply()

    settings.apply(settingsClone)
    EditorGroupsSettings.instance.fireChanged()
  }

  override fun getDisplayName(): String = message("settings.title")

  companion object {
    const val ID: String = "EditorSettingsConfigurable"
    const val FILE_COLORS_ID = "reference.settings.ide.settings.file-colors"
  }
}