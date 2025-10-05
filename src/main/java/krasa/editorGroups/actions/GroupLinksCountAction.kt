package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.drawCenteredString
import krasa.editorGroups.EditorGroupManager
import java.awt.*
import javax.swing.Icon

class GroupLinksCountAction : DumbAwareAction() {
  override fun isDumbAware(): Boolean = false

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent): Unit = Unit

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val currentGroup = EditorGroupManager.getInstance(project).lastGroup
    val count = currentGroup.size(project)

    e.presentation.icon = CompositeIcon(count)
    e.presentation.text = count.toString()
  }

  internal class CompositeIcon(private val counter: Int) : Icon {
    val backgroundColor: Color
      get() = JBColor.namedColor("Counter.background", Color(-0x33655850, true))

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      val counterText = counter.toString()
      val g2d = g as Graphics2D
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      val fontSize = if (counter < 100) FONT_SIZE else SMALL_FONT_SIZE

      g2d.color = backgroundColor
      g2d.fillOval(x, y, ICON_SIZE, ICON_SIZE)

      g2d.color = UIUtil.getPanelBackground()
      g2d.font = g2d.font.deriveFont(fontSize.toFloat())
      drawCenteredString(
        g2d,
        Rectangle(x, y, ICON_SIZE, ICON_SIZE),
        counterText,
      )
    }

    override fun getIconWidth(): Int = ICON_SIZE

    override fun getIconHeight(): Int = ICON_SIZE
  }

  companion object {
    private val FONT_SIZE = JBUIScale.scale(10)
    private val SMALL_FONT_SIZE = JBUIScale.scale(8)
    private val ICON_SIZE = JBUIScale.scale(16)
  }
}