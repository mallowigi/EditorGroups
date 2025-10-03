package krasa.editorGroups.tabs2.impl.islands

import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainterAdapter

internal class IslandsTabsPainterProvider : KrTabsPainterProvider() {
  override fun createTabPainter(): EditorGroupsTabPainterAdapter = IslandsTabPainterAdapter()
}