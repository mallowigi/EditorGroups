package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.services.PanelRefresher
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.Notifications

class TogglePanelVisibilityAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    PanelRefresher.getInstance(getEventProject(e) ?: return).refresh()
    Notifications.notifyState(message("editor.groups.panel"), false)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    when {
      EditorGroupsSettings.instance.isShowPanel -> e.presentation.text = message("action.hide.panel.text")
      else                                      -> e.presentation.text = message("action.show.panel.text")
    }
  }

  companion object {
    const val ID: String = "krasa.editorGroups.TogglePanelVisibility"
  }
}