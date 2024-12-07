package krasa.editorGroups.extensions

import com.intellij.ide.IconLayerProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.ui.UIUtil
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.model.EditorGroup
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

internal class CustomFileIconLayerProvider : IconLayerProvider {
  fun getIcon(file: VirtualFile, project: Project?): Icon? {
    val textEditor = FileEditorManagerEx.getInstanceEx(project ?: return null).getSelectedEditor(file)

    return getSizeIcon(
      project = project,
      textEditor = textEditor,
    )
  }

  private fun getSizeIcon(project: Project, textEditor: FileEditor?): Icon? {
    var group: EditorGroup? = null
    if (textEditor != null) {
      group = textEditor.getUserData(EditorGroupPanel.EDITOR_GROUP) ?: return null
    }

    val currentSize = group?.size(project) ?: return null
    if (currentSize == 0) return null

    return CompositeIcon(currentSize)
  }

  override fun getLayerIcon(element: Iconable, isLocked: Boolean): Icon? {
    if (element is PsiElement) {
      val file = element.containingFile?.virtualFile ?: return null
      val project = element.project

      return getIcon(file, project)
    }
    return null
  }

  override fun getLayerDescription(): String = ""

  internal class CompositeIcon(private val counter: Int) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      val counterText = counter.toString()
      val metrics = g.fontMetrics
      val counterWidth = metrics.stringWidth(counterText)
      val counterHeight = metrics.ascent

      g.color = UIUtil.getTreeBackground()
      g.fillRoundRect(
        x + ICON_SIZE - OFFSET_X - counterWidth / 2,
        y + counterHeight / 2,
        counterWidth,
        counterHeight,
        16,
        16
      )

      g.color = UIUtil.getLabelForeground()
      g.font = g.font.deriveFont(FONT_SIZE)
      g.drawString(counterText, x + ICON_SIZE - counterWidth / 2, y + g.fontMetrics.ascent + ICON_SIZE / 2)
    }

    override fun getIconWidth(): Int = ICON_SIZE

    override fun getIconHeight(): Int = ICON_SIZE
  }

  companion object {
    private const val FONT_SIZE = 8.0f
    private const val ICON_SIZE = 16
    private const val OFFSET_X = 2
  }
}
