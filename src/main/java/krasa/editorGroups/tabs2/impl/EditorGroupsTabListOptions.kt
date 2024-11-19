package krasa.editorGroups.tabs2.impl

import krasa.editorGroups.tabs2.EditorGroupsTabsPosition

data class EditorGroupsTabListOptions(
  @JvmField
  val requestFocusOnLastFocusedComponent: Boolean = false,
  @JvmField
  val paintFocus: Boolean = false,
  @JvmField
  val tabPosition: EditorGroupsTabsPosition = EditorGroupsTabsPosition.TOP,
  @JvmField
  val hideTabs: Boolean = false,
)
