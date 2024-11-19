package krasa.editorGroups.tabs2.border

import com.intellij.util.ui.JBUI
import krasa.editorGroups.tabs2.EditorGroupsTabsPosition
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import java.awt.*
import javax.swing.border.Border

class EditorGroupsTabsBorder(val tabs: KrTabsImpl) : Border {
  val thickness: Int
    get() = tabs.tabPainter.getTabTheme().topBorderThickness

  val effectiveBorder: Insets
    get() = JBUI.insetsTop(thickness)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    g as Graphics2D
    if (tabs.isEmptyVisible) return

    val firstLabel = tabs.getTabLabel(tabs.getVisibleInfos().first()) ?: return

    when (tabs.position) {
      EditorGroupsTabsPosition.TOP    -> {
        val highlightThickness = thickness
        val startY = firstLabel.y - highlightThickness
        val lastRow = 1

        val yl = lastRow * tabs.headerFitSize!!.height + startY
        tabs.tabPainter.paintBorderLine(
          g = g,
          thickness = thickness,
          from = Point(x, yl),
          to = Point(x + width, yl)
        )
      }

      EditorGroupsTabsPosition.BOTTOM -> {
        val curY = height - 1 * tabs.headerFitSize!!.height
        tabs.tabPainter.paintBorderLine(
          g = g,
          thickness = thickness,
          from = Point(x, curY),
          to = Point(x + width, curY)
        )
      }
    }

    val selectedLabel = tabs.selectedLabel ?: return
    tabs.tabPainter.paintUnderline(
      position = tabs.position,
      rect = selectedLabel.bounds,
      borderThickness = thickness,
      g = g,
      active = tabs.isActiveTabs(tabs.selectedInfo)
    )
  }

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun isBorderOpaque(): Boolean = true
}
