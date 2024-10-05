package krasa.editorGroups.tabs2

import com.intellij.util.ui.JBUI
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import java.awt.Component
import java.awt.Insets
import javax.swing.border.Border

abstract class KrTabsBorder(val tabs: KrTabsImpl) : Border {
  val thickness: Int
    get() = tabs.tabPainter.getTabTheme().topBorderThickness

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun isBorderOpaque(): Boolean = true

  open val effectiveBorder: Insets
    get() = JBUI.emptyInsets()

}
