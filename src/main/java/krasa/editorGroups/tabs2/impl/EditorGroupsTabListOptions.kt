package krasa.editorGroups.tabs2.impl

import com.intellij.ui.tabs.JBTabsPosition

data class EditorGroupsTabListOptions(
  @JvmField
  val requestFocusOnLastFocusedComponent: Boolean = false,
  @JvmField
  val paintFocus: Boolean = false,
  @JvmField
  val tabPosition: JBTabsPosition = JBTabsPosition.top,
  @JvmField
  val hideTabs: Boolean = false,
)
