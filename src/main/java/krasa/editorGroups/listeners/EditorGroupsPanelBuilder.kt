package krasa.editorGroups.listeners

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
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.TOPIC
import krasa.editorGroups.support.unwrapPreview
import javax.swing.SwingConstants.BOTTOM
import javax.swing.SwingConstants.TOP

@Service(Service.Level.APP)
class EditorGroupsPanelBuilder {
  fun addPanelToEditor(manager: FileEditorManager, file: VirtualFile) {
    val project = manager.project
    thisLogger().debug(">fileOpenedSync [$file]") // NON-NLS

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
        "<fileOpenedSync EditorGroupPanel created, file=$fileToOpen in ${System.currentTimeMillis() - start}ms, fileEditor=$fileEditor" // NON-NLS
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
      thisLogger().debug(">createPanel: fileEditor already disposed") // NON-NLS
      return
    }

    val panel = EditorGroupPanel(
      fileEditor = fileEditor,
      project = project,
      switchRequest = switchRequest,
      file = file
    )

    // Add panel
    when (val editorTabPlacement = EditorGroupsSettings.instance.tabsPlacement) {
      TOP    -> manager.addTopComponent(fileEditor, panel.root)
      BOTTOM -> manager.addBottomComponent(fileEditor, panel.root)
      else   -> thisLogger().warn("Unsupported tab placement: $editorTabPlacement") // NON-NLS
    }
    panel.postConstruct()

    // Listen for UI settings changes on this panel
    ApplicationManager.getApplication().messageBus.connect(panel)
      .subscribe(
        TOPIC,
        object : EditorGroupsSettings.SettingsNotifier {
          override fun configChanged(config: EditorGroupsSettings) {
            when {
              panel.disposed                                    -> return
              panel.currentTabPlacement == config.tabsPlacement -> return
              else                                              -> {
                panel.currentTabPlacement = config.tabsPlacement

                // Remove panels on top and bottom and readd them in the new position
                manager.removeTopComponent(fileEditor, panel.root)
                manager.removeBottomComponent(fileEditor, panel.root)

                when (panel.currentTabPlacement) {
                  TOP    -> manager.addTopComponent(fileEditor, panel.root)
                  BOTTOM -> manager.addBottomComponent(fileEditor, panel.root)
                  else   -> thisLogger().warn("Unsupported tab placement: $panel.currentTabPlacement") // NON-NLS
                }

                panel.updateTabPlacement()
              }
            }
          }
        }
      )
  }

  companion object {
    @JvmStatic
    val instance: EditorGroupsPanelBuilder by lazy { service<EditorGroupsPanelBuilder>() }
  }
}
