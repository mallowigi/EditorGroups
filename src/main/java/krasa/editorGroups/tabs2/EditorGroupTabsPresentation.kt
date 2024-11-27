package krasa.editorGroups.tabs2

import krasa.editorGroups.tabs2.label.TabUiDecorator
import java.awt.Insets

interface EditorGroupTabsPresentation {
  val tabsPosition: EditorGroupsTabsPosition?

  fun setUiDecorator(decorator: TabUiDecorator?): EditorGroupTabsPresentation?

  fun setInnerInsets(innerInsets: Insets): EditorGroupTabsPresentation?

  fun setTabsPosition(position: EditorGroupsTabsPosition): EditorGroupTabsPresentation

  fun setFirstTabOffset(offset: Int): EditorGroupTabsPresentation?
}
