package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import krasa.editorGroups.EditorGroupPanel

abstract class EditorGroupsAction : DumbAwareAction() {
  protected fun getEditorGroupPanel(anActionEvent: AnActionEvent): EditorGroupPanel? {
    val data = anActionEvent.getData(PlatformDataKeys.FILE_EDITOR)
    return data?.getUserData(EditorGroupPanel.EDITOR_PANEL)
  }
}
