package krasa.editorGroups.tabs2.impl.themes

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import krasa.editorGroups.EditorGroupPanel.Companion.COMPACT_TAB_HEIGHT
import java.awt.Color
import java.awt.Font

open class EditorGroupDefaultTabTheme : EditorGroupTabTheme {
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

  override val underlinedTabBackground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED).backgroundColor
      ?: JBUI.CurrentTheme.EditorTabs.underlinedTabBackground()

  override val underlinedTabForeground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED).foregroundColor
      ?: JBUI.CurrentTheme.EditorTabs.underlinedTabForeground()

  override val underlinedTabBorderColor: Color
    get() = JBColor("EditorTabs.underlinedBorderColor", JBColor(0x7F99C3, 0x7F99C3))

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

  override val hoverBorderColor: Color
    get() = JBColor("EditorTabs.hoverBorderColor", null)

  override val underlinedTabInactiveBackground: Color?
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED_INACTIVE).backgroundColor ?: underlinedTabBackground

  override val underlinedTabInactiveForeground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED_INACTIVE).foregroundColor ?: underlinedTabForeground

  override val inactiveColoredTabBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.inactiveColoredFileBackground()

  override val fontSizeOffset: Int
    get() = JBUI.getInt(JBUI.CurrentTheme.EditorTabs.fontSizeOffsetKey(), 0)

  override val font: Font
    get() = JBUI.CurrentTheme.EditorTabs.font()

  override val tabHeight: Int
    get() = JBUI.CurrentTheme.TabbedPane.TAB_HEIGHT.get()

  override val compactTabHeight: Int
    get() = COMPACT_TAB_HEIGHT

  override val roundTabArc: Int
    get() = JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc().float.toInt()

  override val roundTabOpacity: Float
    get() = 0.9f

  override val roundTabBorderWidth: Float
    get() = 1.0f
}