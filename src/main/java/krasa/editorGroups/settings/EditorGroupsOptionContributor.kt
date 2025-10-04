package krasa.editorGroups.settings

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.options.Configurable
import krasa.editorGroups.messages.EditorGroupsBundle.message

internal class EditorGroupsOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val configurable: Configurable = EditorSettingsConfigurable()
    val displayName = configurable.displayName

    val strings =
      listOf(
        message("EditorGroupSettings.dialog.resetDefaults.consent"),
        message("EditorGroupSettings.resetDefaultsButton.text"),
        message("EditorGroupsSettings.fileColorsLink.text"),
        message("EditorGroupsSettings.groupSizeLimit.text"),
        message("EditorGroupsSettings.groupSizeLimit.toolTipText"),
        message("EditorGroupsSettings.isAutoFoldersCheckbox.text"),
        message("EditorGroupsSettings.isAutoFoldersCheckbox.toolTipText"),
        message("EditorGroupsSettings.isAutoSameNameCheckbox.text"),
        message("EditorGroupsSettings.isAutoSameNameCheckbox.toolTipText"),
        message("EditorGroupsSettings.isColorTabsCheckbox.text"),
        message("EditorGroupsSettings.isColorTabsCheckbox.toolTipText"),
        message("EditorGroupsSettings.isCompactTabsCheckbox.text"),
        message("EditorGroupsSettings.isCompactTabsCheckbox.toolTipText"),
        message("EditorGroupsSettings.isContinuousScrollingCheckbox.text"),
        message("EditorGroupsSettings.isContinuousScrollingCheckbox.toolTipText"),
        message("EditorGroupsSettings.isExcludeEditorGroupsFilesCheckbox.text"),
        message("EditorGroupsSettings.isExcludeEditorGroupsFilesCheckbox.toolTipText"),
        message("EditorGroupsSettings.isForceSwitchCheckbox.text"),
        message("EditorGroupsSettings.isForceSwitchCheckbox.toolTipText"),
        message("EditorGroupsSettings.isGroupSwitchGroupActionCheckbox.text"),
        message("EditorGroupsSettings.isGroupSwitchGroupActionCheckbox.toolTipText"),
        message("EditorGroupsSettings.isHideEmptyCheckbox.text"),
        message("EditorGroupsSettings.isHideEmptyCheckbox.toolTipText"),
        message("EditorGroupsSettings.isIndexOnlyEditorGroupsFilesCheckbox.text"),
        message("EditorGroupsSettings.isIndexOnlyEditorGroupsFilesCheckbox.toolTipText"),
        message("EditorGroupsSettings.isInitializeSynchronouslyCheckbox.text"),
        message("EditorGroupsSettings.isInitializeSynchronouslyCheckbox.toolTipText"),
        message("EditorGroupsSettings.isRememberLastGroupCheckbox.text"),
        message("EditorGroupsSettings.isRememberLastGroupCheckbox.toolTipText"),
        message("EditorGroupsSettings.isSelectRegexGroupCheckbox.text"),
        message("EditorGroupsSettings.isSelectRegexGroupCheckbox.toolTipText"),
        message("EditorGroupsSettings.isShowSizeCheckbox.text"),
        message("EditorGroupsSettings.isShowSizeCheckbox.toolTipText"),
        message("EditorGroupsSettings.isSmallLabelsCheckbox.text"),
        message("EditorGroupsSettings.isSmallLabelsCheckbox.toolTipText"),
        message("EditorGroupsSettings.resetDefaultsButton.text"),
        message("EditorGroupsSettings.resetDefaultsButton.toolTipText"),
        message("EditorGroupsSettings.tabSizeLimit.text"),
        message("EditorGroupsSettings.tabSizeLimit.toolTipText"),
        message("action.krasa.editorGroups.ToggleFloatingButton.text"),
        message("action.krasa.editorGroups.ToggleFloatingButton.description"),
        message("action.krasa.editorGroups.ToggleToolWindow.text"),
        message("action.krasa.editorGroups.ToggleToolWindow.description"),
        message("action.krasa.editorGroups.ToggleRoundedTabs.text"),
        message("action.krasa.editorGroups.ToggleRoundedTabs.description"),
      )

    strings.forEach {
      processor.addOptions(it, null, displayName, EditorSettingsConfigurable.ID, displayName, true)
    }
  }
}