package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import krasa.editorGroups.EditorGroupsSettingsState.Companion.state

class ToggleAutoFolderGroupsAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = state().isAutoFolders

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    state().isAutoFolders = state
  }

  companion object {
    const val ID = "krasa.editorGroups.ToggleFolderEditorGroups"
  }
}
