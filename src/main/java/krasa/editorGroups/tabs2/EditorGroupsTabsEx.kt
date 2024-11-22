package krasa.editorGroups.tabs2

import com.intellij.openapi.actionSystem.DataKey
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo

interface EditorGroupsTabsEx : EditorGroupsTabsBase {

  val isEmptyVisible: Boolean

  fun updateTabActions(validateNow: Boolean)

  fun addTabSilently(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo?

  fun removeTab(info: EditorGroupTabInfo, forcedSelectionTransfer: EditorGroupTabInfo?)

  fun getToSelectOnRemoveOf(info: EditorGroupTabInfo): EditorGroupTabInfo?

  fun sortTabs(comparator: Comparator<EditorGroupTabInfo>)

  companion object {
    @JvmField
    val NAVIGATION_ACTIONS_KEY: DataKey<EditorGroupsTabsEx> = DataKey.create("EditorGroupsTabsEx")
  }
}
