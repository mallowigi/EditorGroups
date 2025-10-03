package krasa.editorGroups.tabs2.impl.islands

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsDefaultTabPainterAdapter
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainter
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainterAdapter
import krasa.editorGroups.tabs2.label.EditorGroupTabLabel
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

class IslandsTabPainterAdapter : EditorGroupsTabPainterAdapter {
  private val editorAdapter = EditorGroupsDefaultTabPainterAdapter()

  val isEnabled: Boolean
    get() = EditorGroupsSettings.instance.isIslands

  override val tabPainter: EditorGroupsTabPainter
    get() = if (isEnabled) IslandsTabPainter() else editorAdapter.tabPainter

  override fun paintBackground(
    label: EditorGroupTabLabel,
    g: Graphics,
    tabs: KrTabsImpl
  ) {
    if (!isEnabled) {
      tabs.setFirstTabOffset(0)
      editorAdapter.paintBackground(label, g, tabs)
      return
    }

    val info = label.info
    val isSelected = info == tabs.selectedInfo
    val isHovered = tabs.isHoveredTab(label)
    val active = tabs.isActiveTabs(info)

    val rect = Rectangle(label.size)
    val g2d = g.create() as Graphics2D

    try {
      GraphicsUtil.setupAAPainting(g2d)
      tabPainter.fillBackground(g2d, rect)
      tabs.setFirstTabOffset(5)

      val accentedRect = Rectangle(rect)
      JBInsets.removeFrom(accentedRect, JBInsets(5, 3, 5, 3))

      (tabPainter as IslandsTabPainter).paintTab(
        g = g2d,
        rect = accentedRect,
        tabColor = info.tabColor,
        active = active,
        selected = isSelected,
        hovered = isHovered
      )
    } finally {
      g2d.dispose()
    }
  }
}