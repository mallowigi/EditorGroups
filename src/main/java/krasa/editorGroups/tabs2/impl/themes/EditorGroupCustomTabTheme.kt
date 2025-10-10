package krasa.editorGroups.tabs2.impl.themes

import java.awt.Color
import java.awt.Font

open class EditorGroupCustomTabTheme : EditorGroupTabTheme {
  override val background: Color
    get() = EditorGroupsUI.background()

  override val borderColor: Color
    get() = EditorGroupsUI.borderColor()

  override val underlineColor: Color
    get() = EditorGroupsUI.underlineColor()

  override val inactiveUnderlineColor: Color
    get() = EditorGroupsUI.inactiveUnderlineColor()

  override val hoverBackground: Color
    get() = EditorGroupsUI.hoverBackground()

  override val hoverSelectedBackground: Color
    get() = EditorGroupsUI.hoverSelectedBackground()

  override val hoverSelectedInactiveBackground: Color
    get() = EditorGroupsUI.hoverSelectedInactiveBackground()

  override val hoverBorderColor: Color?
    get() = EditorGroupsUI.hoverBorderColor()

  override val underlinedTabBackground: Color?
    get() = EditorGroupsUI.underlinedTabBackground()

  override val underlinedTabForeground: Color
    get() = EditorGroupsUI.underlinedTabForeground()

  override val underlinedTabBorderColor: Color
    get() = EditorGroupsUI.underlinedTabBorderColor()

  override val underlineHeight: Int
    get() = EditorGroupsUI.underlineHeight()

  override val hoverInactiveBackground: Color?
    get() = EditorGroupsUI.hoverInactiveBackground()

  override val underlinedTabInactiveBackground: Color?
    get() = EditorGroupsUI.underlinedTabBackground()

  override val underlinedTabInactiveForeground: Color
    get() = EditorGroupsUI.underlinedTabForeground()

  override val inactiveColoredTabBackground: Color?
    get() = EditorGroupsUI.background()

  override val underlineArc: Int
    get() = EditorGroupsUI.underlineArc()

  override val fontSizeOffset: Int
    get() = EditorGroupsUI.fontSizeOffset()

  override val font: Font?
    get() = EditorGroupsUI.font()

  override val tabHeight: Int
    get() = EditorGroupsUI.tabHeight()

  override val compactTabHeight: Int
    get() = EditorGroupsUI.compactTabHeight()

  override val roundTabArc: Int
    get() = EditorGroupsUI.roundTabArc()

  override val roundTabOpacity: Float
    get() = EditorGroupsUI.backgroundOpacity()

  override val roundTabBorderWidth: Float
    get() = EditorGroupsUI.roundTabBorderWidth()
}