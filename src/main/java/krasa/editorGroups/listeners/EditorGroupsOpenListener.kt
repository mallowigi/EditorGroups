package krasa.editorGroups.listeners

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.UISettingsListener.TOPIC
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.SwitchRequest
import krasa.editorGroups.support.unwrapPreview
import javax.swing.SwingConstants

class EditorGroupsOpenListener : FileOpenedSyncListener {
  var currentTabPlacement: Int = UISettings.getInstance().editorTabPlacement
  var isLaidOut: Boolean = false

  override fun fileOpenedSync(
    manager: FileEditorManager,
    file: VirtualFile,
    editorsWithProviders: List<FileEditorWithProvider>
  ) {
    val project = manager.project
    thisLogger().debug(">fileOpenedSync [$file]")

    val fileToOpen = unwrapPreview(file) ?: return
    val editorGroupManager = EditorGroupManager.Companion.getInstance(project)

    val switchRequest = editorGroupManager.getAndClearSwitchingRequest(fileToOpen)
    val editors = manager.getEditors(fileToOpen)

    // Create editor group panel if it doesn't exist'
    for (fileEditor in editors) {
      if (fileEditor.getUserData(EditorGroupPanel.Companion.EDITOR_PANEL) != null) continue

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

    fun renderPanel(): EditorGroupPanel {
      val panel = EditorGroupPanel(fileEditor, project, switchRequest, file)
      val editorTabPlacement = UISettings.getInstance().editorTabPlacement
      when (editorTabPlacement) {
        SwingConstants.TOP    -> {
          manager.addTopComponent(fileEditor, panel.root)
          isLaidOut = true
        }

        SwingConstants.BOTTOM -> {
          manager.addBottomComponent(fileEditor, panel.root)
          isLaidOut = true
        }

        else                  -> {
          thisLogger().warn("Unsupported tab placement: $editorTabPlacement")
          isLaidOut = false
        }
      }
      panel.postConstruct()
      return panel
    }

    val panel = renderPanel()

    // Listen for UI settings changes on this panel
    ApplicationManager.getApplication().messageBus.connect(panel)
      .subscribe(TOPIC, object : UISettingsListener {
        override fun uiSettingsChanged(uiSettings: UISettings) {
          when {
            panel.disposed                                       -> return
            currentTabPlacement == uiSettings.editorTabPlacement -> return
            else                                                 -> {
              currentTabPlacement = uiSettings.editorTabPlacement

              if (isLaidOut) {
                manager.removeTopComponent(fileEditor, panel.root)
                manager.removeBottomComponent(fileEditor, panel.root)
              }

              renderPanel()
            }
          }
        }
      })

  }
}
