package krasa.editorGroups.tabs2.impl.themes

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font

interface EditorGroupTabTheme {
  val topBorderThickness: Int
    get() = JBUI.scale(1)

  // Main
  val background: Color
  val borderColor: Color
  val underlineColor: Color
  val inactiveUnderlineColor: Color

  // Hover
  val hoverBackground: Color

  val hoverSelectedBackground: Color
    get() = hoverBackground

  val hoverSelectedInactiveBackground: Color
    get() = hoverBackground

  val hoverInactiveBackground: Color?
  val hoverBorderColor: Color?

  // Selected tab
  val underlinedTabBackground: Color?
  val underlinedTabForeground: Color
  val underlinedTabBorderColor: Color?

  // Underline
  val underlineHeight: Int
  val underlineArc: Int
    get() = 0

  // Inactive tab
  val underlinedTabInactiveBackground: Color?
  val underlinedTabInactiveForeground: Color?

  // Colored tab
  val inactiveColoredTabBackground: Color?

  // Font
  val fontSizeOffset: Int
    get() = 0
  val font: Font?

  // Compact
  val tabHeight: Int
  val compactTabHeight: Int

  // Islands
  val roundTabArc: Int
  val roundTabOpacity: Float?
  val roundTabBorderWidth: Float?
}