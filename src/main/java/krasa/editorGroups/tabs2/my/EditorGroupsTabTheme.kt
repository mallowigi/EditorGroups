package krasa.editorGroups.tabs2.my

import krasa.editorGroups.tabs2.impl.themes.KrTabTheme
import java.awt.Color

open class EditorGroupsTabTheme : KrTabTheme {
  override val background: Color?
    get() = EditorGroupsTabs.background()
  override val borderColor: Color
    get() = EditorGroupsTabs.borderColor()
  override val underlineColor: Color
    get() = EditorGroupsTabs.underlineColor()
  override val inactiveUnderlineColor: Color
    get() = EditorGroupsTabs.inactiveUnderlineColor()
  override val hoverBackground: Color
    get() = EditorGroupsTabs.hoverBackground()
  override val underlinedTabBackground: Color?
    get() = EditorGroupsTabs.underlinedTabBackground()
  override val underlinedTabForeground: Color
    get() = EditorGroupsTabs.underlinedTabForeground()
  override val underlineHeight: Int
    get() = EditorGroupsTabs.underlineHeight()
  override val hoverInactiveBackground: Color?
    get() = hoverBackground
  override val underlinedTabInactiveBackground: Color?
    get() = underlinedTabBackground
  override val underlinedTabInactiveForeground: Color
    get() = underlinedTabForeground
  override val inactiveColoredTabBackground: Color?
    get() = EditorGroupsTabs.background()
  override val underlineArc: Int
    get() = EditorGroupsTabs.underlineArc()

}
