package krasa.editorGroups.tabs2.impl.themes

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import krasa.editorGroups.settings.EditorGroupsSettings
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

object EditorGroupsUI {
  val defaultTheme: EditorGroupDefaultTabTheme = EditorGroupDefaultTabTheme()
  val defaultSize: Int = JBUI.scale(14)

  fun underlineColor(): Color = JBColor.namedColor(
    "EditorGroupsTabs.underlineColor",
    defaultTheme.underlineColor
  )

  fun underlineHeight(): Int = JBUI.getInt(
    "EditorGroupsTabs.underlineHeight",
    defaultTheme.underlineHeight
  )

  fun inactiveUnderlineColor(): Color = JBColor.namedColor(
    "EditorGroupsTabs.inactiveUnderlineColor",
    defaultTheme.inactiveUnderlineColor
  )

  fun borderColor(): Color = JBColor.namedColor(
    "EditorGroupsTabs.borderColor",
    defaultTheme.borderColor
  )

  fun background(): Color = JBColor.namedColor(
    "EditorGroupsTabs.background",
    defaultTheme.background
  )

  fun hoverBackground(): Color = JBColor.namedColor(
    "EditorGroupsTabs.hoverBackground",
    defaultTheme.hoverBackground
  )

  fun hoverInactiveBackground(): Color = JBColor.namedColor(
    "EditorGroupsTabs.hoverInactiveBackground",
    defaultTheme.hoverInactiveBackground
  )

  fun hoverSelectedBackground(): Color = JBColor.namedColor(
    "EditorGroupsTabs.hoverSelectedBackground",
    defaultTheme.hoverSelectedBackground
  )

  fun hoverSelectedInactiveBackground(): Color = JBColor.namedColor(
    "EditorGroupsTabs.hoverSelectedInactiveBackground",
    defaultTheme.hoverSelectedInactiveBackground
  )

  fun hoverBorderColor(): Color = JBColor.namedColor(
    "EditorGroupsTabs.hoverBorderColor",
    defaultTheme.hoverBorderColor
  )

  fun underlinedTabBackground(): Color = JBColor.namedColor(
    "EditorGroupsTabs.underlinedTabBackground",
    defaultTheme.underlinedTabBackground
  )

  fun underlinedTabForeground(): Color = JBColor.namedColor(
    "EditorGroupsTabs.underlinedTabForeground",
    defaultTheme.underlinedTabForeground
  )

  fun underlinedTabBorderColor(): Color = JBColor.namedColor(
    "EditorGroupsTabs.underlinedTabBorderColor",
    defaultTheme.underlinedTabBorderColor
  )

  fun underlineArc(): Int = JBUI.getInt(
    "EditorGroupsTabs.underlineArc",
    defaultTheme.underlineArc
  )

  fun compactTabHeight(): Int = JBUI.getInt(
    "EditorGroupsTabs.compactTabHeight",
    defaultTheme.compactTabHeight
  )

  fun tabHeight(): Int = JBUI.getInt(
    "EditorGroupsTabs.tabHeight",
    defaultTheme.tabHeight
  )

  fun fontSizeOffset(): Int = JBUI.getInt(
    "EditorGroupsTabs.fontSizeOffset",
    defaultTheme.fontSizeOffset
  )

  fun roundTabArc(): Int = JBUI.getInt(
    "EditorGroupsTabs.roundTabArc",
    defaultTheme.roundTabArc
  )

  fun backgroundOpacity(): Float = JBUI.getFloat(
    "EditorGroupsTabs.backgroundOpacity",
    defaultTheme.roundTabOpacity
  )

  fun font(): Font {
    val isCustomFont = EditorGroupsSettings.instance.isCustomFont
    val customFont = EditorGroupsSettings.instance.customFont

    val font = when {
      isCustomFont && customFont != null -> JBFont.create(Font(customFont, Font.PLAIN, defaultSize), false)
      else                               -> defaultFont()
    }

    return font
      .biggerOn(fontSizeOffset().toFloat())
  }

  private fun defaultFont(): JBFont {
    val font = UIManager.getFont("EditorGroupsTabs.font") ?: defaultTheme.font
    return JBFont.create(font, false)
  }
}