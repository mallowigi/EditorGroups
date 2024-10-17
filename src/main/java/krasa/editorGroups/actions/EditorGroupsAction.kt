package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import krasa.editorGroups.EditorGroupPanel2

abstract class EditorGroupsAction : DumbAwareAction() {
  protected fun getEditorGroupPanel(anActionEvent: AnActionEvent): EditorGroupPanel2? {
    val data = anActionEvent.getData(PlatformDataKeys.FILE_EDITOR)
    return data?.getUserData(EditorGroupPanel2.EDITOR_PANEL)
  }
}
