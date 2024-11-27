package krasa.editorGroups.tabs2.impl

import krasa.editorGroups.tabs2.label.EditorGroupTabInfo

abstract class EditorGroupsTabLayout {
  open val scrollOffset: Int
    get() = 0

  open fun scroll(units: Int): Unit = Unit

  open fun isTabHidden(info: EditorGroupTabInfo): Boolean = false
}
