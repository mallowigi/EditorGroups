package krasa.editorGroups.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import krasa.editorGroups.EditorGroupManager

class GroupLinksCountAction : DumbAwareAction() {
  init {
    templatePresentation.icon = AllIcons.General.Information
  }

  override fun isDumbAware(): Boolean = false

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent): Unit = Unit

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val currentGroup = EditorGroupManager.getInstance(project).lastGroup
    val count = currentGroup.size(project)
    e.presentation.text = count.toString()
    e.presentation.isEnabled = false // purely display, not clickable
  }
}