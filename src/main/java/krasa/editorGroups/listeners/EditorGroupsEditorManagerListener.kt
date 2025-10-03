package krasa.editorGroups.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.services.TabGroupColorizer

class EditorGroupsEditorManagerListener : FileEditorManagerListener {
  override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
    // We add the fileOpened in addition to the EditorGroupsOpenListener to ensure that the panel is added to the editor.
    EditorGroupsPanelBuilder.instance.addPanelToEditor(manager, file)
  }

  /** When a tab is selected, refresh the editor group panel. */
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project
    thisLogger().debug("selectionChanged $event") // NON-NLS
    val fileEditor = event.newEditor ?: return

    val panel = fileEditor.getUserData(EditorGroupPanel.EDITOR_PANEL) ?: return

    val instance = EditorGroupManager.getInstance(project)
    val switchRequest = instance.getAndClearSwitchingRequest(panel.file)

    if (switchRequest != null) {
      val switchingGroup = switchRequest.group
      val scrollOffset = switchRequest.myScrollOffset

      // Refresh panel
      panel.refreshOnSelectionChanged(false, switchingGroup, scrollOffset)
    } else {
      panel.refreshPane(false, null)
    }

    ApplicationManager.getApplication().invokeLater {
      TabGroupColorizer.getInstance(project).refreshTabs()
    }
  }
}