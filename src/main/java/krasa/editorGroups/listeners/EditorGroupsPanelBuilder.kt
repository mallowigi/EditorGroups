package krasa.editorGroups.listeners

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.UISettingsListener.TOPIC
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.model.SwitchRequest
import krasa.editorGroups.support.unwrapPreview
import javax.swing.SwingConstants

@Service(Service.Level.APP)
class EditorGroupsPanelBuilder {
  var currentTabPlacement: Int = SwingConstants.TOP

  fun addPanelToEditor(manager: FileEditorManager, file: VirtualFile) {
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

      thisLogger().debug(
        "<fileOpenedSync EditorGroupPanel created, file=$fileToOpen in ${System.currentTimeMillis() - start}ms, fileEditor=$fileEditor"
      )
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
  fun createPanel(project: Project, manager: FileEditorManager, file: VirtualFile, switchRequest: SwitchRequest?, fileEditor: FileEditor) {
    if (!fileEditor.isValid) {
      thisLogger().debug(">createPanel: fileEditor already disposed")
      return
    }

    var topPanel = renderPanel(
      project = project,
      placement = SwingConstants.TOP,
      manager = manager,
      file = file,
      switchRequest = switchRequest,
      fileEditor = fileEditor
    )

    val bottomPanel = renderPanel(
      project = project,
      placement = SwingConstants.BOTTOM,
      manager = manager,
      file = file,
      switchRequest = switchRequest,
      fileEditor = fileEditor
    )

    // Listen for UI settings changes on this panel
    setOf(topPanel, bottomPanel).forEach { panel ->
      panel.updateVisibility()

      ApplicationManager.getApplication().messageBus.connect(panel)
        .subscribe(
          TOPIC,
          object : UISettingsListener {
            override fun uiSettingsChanged(uiSettings: UISettings) {
              val editorTabPlacement = UISettings.getInstance().editorTabPlacement
              when {
                panel.disposed                            -> return
                currentTabPlacement == editorTabPlacement -> return
                else                                      -> {
                  currentTabPlacement = uiSettings.editorTabPlacement
                  panel.updateVisibility()
                }
              }
            }
          }
        )
    }
  }

  fun renderPanel(
    project: Project,
    manager: FileEditorManager,
    file: VirtualFile,
    switchRequest: SwitchRequest?,
    fileEditor: FileEditor,
    placement: Int
  ): EditorGroupPanel {
    val panel = EditorGroupPanel(fileEditor, project, switchRequest, file)
    when (placement) {
      SwingConstants.TOP    -> manager.addTopComponent(fileEditor, panel.root)
      SwingConstants.BOTTOM -> manager.addBottomComponent(fileEditor, panel.root)
    }

    panel.currentTabPlacement = placement
    panel.postConstruct()
    return panel
  }

  companion object {
    @JvmStatic
    val instance: EditorGroupsPanelBuilder by lazy { service<EditorGroupsPanelBuilder>() }
  }
}
