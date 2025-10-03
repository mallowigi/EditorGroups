package krasa.editorGroups.extensions

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.EditorGroupIndexValue
import java.awt.Color

@Suppress("UnstableApiUsage")
internal class CustomEditorGroupsTabColorProvider : EditorTabColorProvider {
  override fun getEditorTabColor(project: Project, file: VirtualFile): Color? = getBgColor(file)

  override fun getEditorTabForegroundColor(project: Project, file: VirtualFile): ColorKey? {
    val fgColor = getFgColor(file) ?: return null

    return ColorKey.createColorKey(COLOR_KEY, fgColor)
  }

  private fun getFgColor(file: VirtualFile): Color? {
    val group: EditorGroup = file.getUserData(EditorGroupPanel.EDITOR_GROUP) ?: return null
    if (group.isStub) return null
    if (group !is EditorGroupIndexValue) return null
    return group.fgColor
  }

  private fun getBgColor(file: VirtualFile): Color? {
    val group: EditorGroup = file.getUserData(EditorGroupPanel.EDITOR_GROUP) ?: return null
    if (group.isStub) return null
    if (group !is EditorGroupIndexValue) return null
    return group.bgColor
  }

  companion object {
    const val COLOR_KEY = "EditorGroupsTabColor"
  }
}
