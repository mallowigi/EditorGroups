package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.PopupHandler
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.icons.EditorGroupsIcons
import java.awt.Component
import javax.swing.JComponent

class RefreshAction : EditorGroupsAction(), CustomComponentAction {
  override fun actionPerformed(anActionEvent: AnActionEvent) {
    val doc = getDocument(anActionEvent)
    if (doc != null) {
      FileDocumentManager.getInstance().saveDocument(doc)
    }

    val panel = getEditorGroupPanel(anActionEvent)
    panel?.refreshPane(true, null)

    val editorGroupManager = EditorGroupManager.getInstance(anActionEvent.project ?: return)
    editorGroupManager.resetSwitching()
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val refresh = ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

    refresh.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) = PopupMenu.popupInvoked(comp, x, y)
    })

    presentation.icon = EditorGroupsIcons.refresh

    return refresh
  }

  private fun getDocument(e: AnActionEvent): Document? = e.getData(CommonDataKeys.EDITOR)?.document

  companion object {
    const val ID: String = "krasa.editorGroups.Refresh"
  }
}
