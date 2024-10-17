package krasa.editorGroups

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.support.unwrapPreview

class EditorGroupsStartup : FileEditorManagerListener {
  override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
    val project = manager.project
    thisLogger().debug(">fileOpenedSync [$file]")

    val fileToOpen = unwrapPreview(file) ?: return
    val editorGroupManager = EditorGroupManager.getInstance(project)

    val switchRequest = editorGroupManager.getAndClearSwitchingRequest(fileToOpen)
    val editors = manager.getEditors(fileToOpen)

    // Create editor group panel if it doesn't exist'
    for (fileEditor in editors) {
      if (fileEditor.getUserData(EditorGroupPanel.EDITOR_PANEL) != null) continue

      val start = System.currentTimeMillis()

      createPanel(
        project = project,
        manager = manager,
        file = fileToOpen,
        switchRequest = switchRequest,
        fileEditor = fileEditor
      )

      thisLogger().debug("<fileOpenedSync EditorGroupPanel created, file=$fileToOpen in ${System.currentTimeMillis() - start}ms, fileEditor=$fileEditor")
    }
  }

  /** When a tab is selected, refresh the editor group panel. */
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project
    thisLogger().debug("selectionChanged $event")
    val fileEditor = event.newEditor
    if (fileEditor == null) return

    val panel = fileEditor.getUserData(EditorGroupPanel.EDITOR_PANEL)
    if (panel == null) return

    val instance = EditorGroupManager.getInstance(project)
    val switchRequest = instance.getAndClearSwitchingRequest(panel.file)

    if (switchRequest != null) {
      val switchingGroup = switchRequest.group
      val scrollOffset = switchRequest.myScrollOffset

      // Refresh panel
      panel.refreshOnSelectionChanged(false, switchingGroup, scrollOffset)
    } else {
      panel._refresh(false, null)
    }
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) = thisLogger().debug("fileClosed [$file]")

  /**
   * Creates the editor group panel
   *
   * @param project The [Project] associated with the editor panel.
   * @param manager The [FileEditorManager] that will manage the newly created panel.
   * @param file The [VirtualFile] associated with the editor panel.
   * @param switchRequest The request that triggered the panel creation, can be null.
   * @param fileEditor The [FileEditor] for which the panel is being created.
   */
  private fun createPanel(
    project: Project,
    manager: FileEditorManager,
    file: VirtualFile,
    switchRequest: SwitchRequest?,
    fileEditor: FileEditor
  ) {
    if (!fileEditor.isValid) {
      thisLogger().debug(">createPanel: fileEditor already disposed")
      return
    }

    val panel = EditorGroupPanel(fileEditor, project, switchRequest, file)
    manager.addTopComponent(fileEditor, panel.root)
    panel.postConstruct()
  }
}
