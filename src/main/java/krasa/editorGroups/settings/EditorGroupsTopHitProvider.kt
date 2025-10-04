/**
 * ****************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Elior "Mallowigi" Boukhobza
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * ****************************************************************************
 */
package krasa.editorGroups.settings

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ApplicationLevelProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import krasa.editorGroups.messages.EditorGroupsBundle.message

@NlsContexts.Checkbox
fun getText(suffix: String): String = StringUtil.stripHtml(message("settings.title", suffix), false)

/** region [Checkbox Definitions]. */

fun isAutoFoldersOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isAutoFoldersCheckbox.text")),
  EditorGroupsSettings.instance::isAutoFolders,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isAutoSameNameOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isAutoSameNameCheckbox.text")),
  EditorGroupsSettings.instance::isAutoSameName
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isAutoSameFeatureOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isAutoSameFeatureCheckbox.text")),
  EditorGroupsSettings.instance::isAutoSameFeature
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isColorTabsOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isColorTabsCheckbox.text")),
  EditorGroupsSettings.instance::isColorTabs
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isCompactTabsOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isCompactTabsCheckbox.text")),
  EditorGroupsSettings.instance::isCompactTabs
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isContinuousScrollingOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isContinuousScrollingCheckbox.text")),
  EditorGroupsSettings.instance::isContinuousScrolling,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun toggleExcludeEditorGroupsFilesOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isExcludeEditorGroupsFilesCheckbox.text")),
  EditorGroupsSettings.instance::isExcludeEditorGroupsFiles,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isForceSwitchOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isForceSwitchCheckbox.text")),
  EditorGroupsSettings.instance::isForceSwitch,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isGroupSwitchGroupActionOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isGroupSwitchGroupActionCheckbox.text")),
  EditorGroupsSettings.instance::isGroupSwitchGroupAction,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isHideEmptyOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isHideEmptyCheckbox.text")),
  EditorGroupsSettings.instance::isHideEmpty,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isIndexOnlyEditorGroupsFilesOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isIndexOnlyEditorGroupsFilesCheckbox.text")),
  EditorGroupsSettings.instance::isIndexOnlyEditorGroupsFiles
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isInitializeSynchronouslyOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isInitializeSynchronouslyCheckbox.text")),
  EditorGroupsSettings.instance::isInitializeSynchronously
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isRememberLastGroupOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isRememberLastGroupCheckbox.text")),
  EditorGroupsSettings.instance::isRememberLastGroup,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isSelectRegexGroupOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isSelectRegexGroupCheckbox.text")),
  EditorGroupsSettings.instance::isSelectRegexGroup,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isShowPanelEnabledOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isShowPanelCheckbox.text")),
  EditorGroupsSettings.instance::isShowPanel,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isShowSizeOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isShowSizeCheckbox.text")),
  EditorGroupsSettings.instance::isShowSize,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isShowMetaOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isShowMetaCheckbox.text")),
  EditorGroupsSettings.instance::isShowMeta,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isSmallLabelsOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.isSmallLabelsCheckbox.text")),
  EditorGroupsSettings.instance::isSmallLabels,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun reuseCurrentTabOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("EditorGroupsSettings.reuseCurrentTab.text")),
  EditorGroupsSettings.instance::reuseCurrentTab,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun roundedTabsOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("toggle.rounded.tabs")),
  EditorGroupsSettings.instance::isRoundedTabs,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isShowToolWindowOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("toggle.switch.file.tool.window")),
  EditorGroupsSettings.instance::isShowToolWindow,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

fun isShowFloatingButtonOption(): BooleanOptionDescription = CheckboxDescriptor(
  getText(message("toggle.switch.file.floating.button")),
  EditorGroupsSettings.instance::isShowFloatingButton,
).asOptionDescriptor { EditorGroupsSettings.instance.fireChanged() }

// endregion [Checkbox Definitions]

internal class EditorGroupsTopHitProvider : ApplicationLevelProvider {
  override fun getId(): String = "EditorGroupsTopHitProvider"

  override fun getOptions(): Collection<OptionDescription> = listOf(
    isAutoFoldersOption(),
    isAutoSameFeatureOption(),
    isAutoSameNameOption(),
    isColorTabsOption(),
    isCompactTabsOption(),
    isContinuousScrollingOption(),
    isForceSwitchOption(),
    isGroupSwitchGroupActionOption(),
    isHideEmptyOption(),
    isIndexOnlyEditorGroupsFilesOption(),
    isInitializeSynchronouslyOption(),
    isRememberLastGroupOption(),
    isSelectRegexGroupOption(),
    isShowFloatingButtonOption(),
    isShowMetaOption(),
    isShowPanelEnabledOption(),
    isShowSizeOption(),
    isShowToolWindowOption(),
    isSmallLabelsOption(),
    reuseCurrentTabOption(),
    roundedTabsOption(),
    toggleExcludeEditorGroupsFilesOption(),
  )
}