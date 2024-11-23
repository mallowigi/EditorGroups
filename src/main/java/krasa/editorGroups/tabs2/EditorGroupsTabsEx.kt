package krasa.editorGroups.tabs2

import krasa.editorGroups.tabs2.label.EditorGroupTabInfo

interface EditorGroupsTabsEx : EditorGroupsTabsBase {
  val isEmptyVisible: Boolean

  fun addTabSilently(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo?

  fun getToSelectOnRemoveOf(info: EditorGroupTabInfo): EditorGroupTabInfo?

  fun sortTabs(comparator: Comparator<EditorGroupTabInfo>)
}
