package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.Notifications

class ToggleFloatingButtonAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = EditorGroupsSettings.instance.isShowFloatingButton

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    EditorGroupsSettings.instance.isShowFloatingButton = state
    EditorGroupsSettings.instance.fireChanged()
    Notifications.notifyState(message("toggle.switch.file.floating.button"), state)
  }

  companion object {
    const val ID: String = "krasa.editorGroups.ToggleFloatingButtonAction"
  }
}