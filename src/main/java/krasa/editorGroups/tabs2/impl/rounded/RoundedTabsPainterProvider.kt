package krasa.editorGroups.tabs2.impl.rounded

import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainterAdapter

internal class RoundedTabsPainterProvider : KrRoundedTabsPainterProvider() {
  override fun createTabPainter(): EditorGroupsTabPainterAdapter = RoundedTabPainterAdapter()
}