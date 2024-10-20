package krasa.editorGroups.tabs2.impl.painter

import krasa.editorGroups.tabs2.impl.KrTabLabel
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import krasa.editorGroups.tabs2.impl.themes.KrTabTheme
import java.awt.Graphics

interface KrTabPainterAdapter {
  fun paintBackground(label: KrTabLabel, g: Graphics, tabs: KrTabsImpl)
  val tabPainter: KrTabPainter
  fun getTabTheme(): KrTabTheme = tabPainter.getTabTheme()
}
