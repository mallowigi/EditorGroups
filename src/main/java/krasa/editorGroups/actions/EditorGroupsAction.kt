package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.support.getEditorPanelDataKey

abstract class EditorGroupsAction : DumbAwareAction() {
  protected fun getEditorGroupPanel(anActionEvent: AnActionEvent): EditorGroupPanel? {
    val data = anActionEvent.getData(PlatformDataKeys.FILE_EDITOR)
    val key = getEditorPanelDataKey() ?: return null
    return data?.getUserData(key)
  }
}
