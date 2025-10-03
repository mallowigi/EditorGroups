package krasa.editorGroups.tabs2.impl.rounded

import com.intellij.ui.ColorUtil
import krasa.editorGroups.tabs2.EditorGroupsTabsPosition
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainter
import krasa.editorGroups.tabs2.impl.themes.EditorGroupDefaultTabTheme
import krasa.editorGroups.tabs2.impl.themes.EditorGroupTabTheme
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D

internal open class RoundedTabPainter : EditorGroupsTabPainter {
  private val theme = EditorGroupDefaultTabTheme()

  private val regularColors = theme.background to theme.borderColor

  private val hoveredColors = theme.hoverBackground to theme.hoverBorderColor

  private val selectedColors = theme.underlinedTabBackground to theme.underlinedTabBorderColor

  private val selectedInactiveColors = theme.underlinedTabInactiveBackground to theme.underlinedTabBorderColor

  private val selectedHoveredInactiveColors = theme.hoverBackground to theme.inactiveUnderlineColor

  private fun getColors(active: Boolean, selected: Boolean, hovered: Boolean): Pair<Color?, Color?> = when {
    selected -> when {
      active  -> selectedColors
      hovered -> selectedHoveredInactiveColors
      else    -> selectedInactiveColors
    }

    hovered  -> hoveredColors
    else     -> regularColors
  }

  override fun getTabTheme(): EditorGroupTabTheme = theme

  override fun getBackgroundColor(): Color = theme.background

  override fun paintBorderLine(g: Graphics2D, thickness: Int, from: Point, to: Point) {
    // do nothing
  }

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    g.color = theme.background
    g.fillRect(rect.x, rect.y, rect.width, rect.height)
  }

  override fun paintTab(
    position: EditorGroupsTabsPosition,
    g: Graphics2D,
    rect: Rectangle,
    borderThickness: Int,
    tabColor: Color?,
    active: Boolean,
    hovered: Boolean
  ) {
    paintTab(
      g = g,
      rect = rect,
      tabColor = tabColor,
      active = active,
      selected = false,
      hovered = hovered
    )
  }

  override fun paintSelectedTab(
    position: EditorGroupsTabsPosition,
    g: Graphics2D,
    rect: Rectangle,
    borderThickness: Int,
    tabColor: Color?,
    active: Boolean,
    hovered: Boolean
  ) {
    paintTab(
      g = g,
      rect = rect,
      tabColor = tabColor,
      active = active,
      selected = true,
      hovered = hovered
    )
  }

  override fun paintUnderline(
    position: EditorGroupsTabsPosition,
    rect: Rectangle,
    borderThickness: Int,
    g: Graphics2D,
    active: Boolean
  ) {
    // do nothing
  }

  fun paintTab(
    g: Graphics2D,
    rect: Rectangle,
    tabColor: Color?,
    active: Boolean,
    selected: Boolean,
    hovered: Boolean
  ) {
    val arc = theme.roundTabArc.toFloat()
    val shape = RoundRectangle2D.Float(
      rect.x.toFloat(),
      rect.y.toFloat(),
      rect.width.toFloat(),
      rect.height.toFloat(),
      arc,
      arc
    )

    // Paint tab bg color
    if (tabColor != null) {
      g.color = ColorUtil.withAlpha(tabColor, theme.roundTabOpacity.toDouble())
      g.fill(shape)
    }

    val (fill, draw) = getColors(
      active = active,
      selected = selected,
      hovered = hovered
    )

    if (fill != null) {
      // Paint rounded tab
      g.color = fill
      g.fill(shape)
    }

    if (draw != null) {
      // Paint rounded border
      g.color = draw
      g.draw(shape)
    }
  }
}