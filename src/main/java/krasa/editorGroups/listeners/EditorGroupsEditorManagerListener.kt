package krasa.editorGroups.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.services.TabGroupColorizer
import krasa.editorGroups.support.getEditorPanelDataKey

class EditorGroupsEditorManagerListener : FileEditorManagerListener {
  override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
    // We add the fileOpened in addition to the EditorGroupsOpenListener to ensure that the panel is added to the editor.
    EditorGroupsPanelBuilder.instance.addPanelToEditor(manager, file)
  }

  /** When a tab is selected, refresh the editor group panel. */
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project
    thisLogger().debug("selectionChanged $event")
    val fileEditor = event.newEditor
    if (fileEditor == null) return

    val key = getEditorPanelDataKey() ?: return
    val panel = fileEditor.getUserData(key)
    if (panel == null) return

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
