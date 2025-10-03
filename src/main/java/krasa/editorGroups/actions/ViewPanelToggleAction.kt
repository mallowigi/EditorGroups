package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import krasa.editorGroups.services.PanelRefresher
import krasa.editorGroups.settings.EditorGroupsSettings

class ViewPanelToggleAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(event: AnActionEvent): Boolean = EditorGroupsSettings.instance.isShowPanel

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val editorGroupsSettingsState = EditorGroupsSettings.instance
    editorGroupsSettingsState.isShowPanel = !editorGroupsSettingsState.isShowPanel

    PanelRefresher.getInstance(event.project ?: return).refresh()
  }

  companion object {
    const val ID: String = "krasa.editorGroups.ViewPanelToggleAction"
  }
}