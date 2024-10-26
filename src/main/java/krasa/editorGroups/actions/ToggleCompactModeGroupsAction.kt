package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import krasa.editorGroups.settings.EditorGroupsSettings

class ToggleCompactModeGroupsAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = EditorGroupsSettings.instance.isCompactTabs

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    EditorGroupsSettings.instance.isCompactTabs = state
    EditorGroupsSettings.instance.fireChanged()
  }

  companion object {
    const val ID = "krasa.editorGroups.ToggleCompactModeGroupsAction"
  }
}