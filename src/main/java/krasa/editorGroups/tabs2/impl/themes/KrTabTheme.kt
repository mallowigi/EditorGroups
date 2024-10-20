package krasa.editorGroups.tabs2.impl.themes

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color

interface KrTabTheme {
  val topBorderThickness: Int
    get() = JBUI.scale(1)
  val background: Color?
  val borderColor: Color
  val underlineColor: Color
  val inactiveUnderlineColor: Color
  val hoverBackground: Color
  val hoverSelectedBackground: Color
    get() = hoverBackground
  val hoverSelectedInactiveBackground: Color
    get() = hoverBackground

  val hoverInactiveBackground: Color?

  val underlinedTabBackground: Color?
  val underlinedTabForeground: Color
  val underlineHeight: Int

  val underlineArc: Int
    get() = 0
  val underlinedTabInactiveBackground: Color?
  val underlinedTabInactiveForeground: Color?
  val inactiveColoredTabBackground: Color?
}

open class KrDefaultTabTheme : KrTabTheme {
  override val topBorderThickness: Int
    get() = 1

  val globalScheme: EditorColorsScheme
    get() = EditorColorsManager.getInstance().globalScheme

  override val background: Color
    get() = JBUI.CurrentTheme.EditorTabs.background()

  override val borderColor: Color
    get() = JBColor.namedColor("EditorTabs.underTabsBorderColor", JBUI.CurrentTheme.EditorTabs.borderColor())

  override val underlineColor: Color
    get() = globalScheme.getColor(EditorColors.TAB_UNDERLINE) ?: JBUI.CurrentTheme.EditorTabs.underlineColor()

  override val inactiveUnderlineColor: Color
    get() = globalScheme.getColor(EditorColors.TAB_UNDERLINE_INACTIVE)
      ?: JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor()

  override val underlinedTabBackground: Color?
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED).backgroundColor
      ?: JBUI.CurrentTheme.EditorTabs.underlinedTabBackground()

  override val underlinedTabForeground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED).foregroundColor
      ?: JBUI.CurrentTheme.EditorTabs.underlinedTabForeground()

  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.EditorTabs.underlineHeight()

  override val underlineArc: Int
    get() = JBUI.CurrentTheme.EditorTabs.underlineArc()

  override val hoverBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.hoverBackground()

  override val hoverInactiveBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.hoverBackground(false, false)

  override val hoverSelectedBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.hoverBackground(true, true)

  override val hoverSelectedInactiveBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.hoverBackground(true, false)

  override val underlinedTabInactiveBackground: Color?
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED_INACTIVE).backgroundColor ?: underlinedTabBackground

  override val underlinedTabInactiveForeground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED_INACTIVE).foregroundColor ?: underlinedTabForeground

  override val inactiveColoredTabBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.inactiveColoredFileBackground()
}
