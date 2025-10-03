package krasa.editorGroups.toolwindow

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.support.Splitters
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class EditorGroupsToolWindowFactory : ToolWindowFactory {
  private val tabList = JBList<EditorGroupFileListProvider.FileEntry>()

  fun updateTabs(project: Project) {
    val entries = EditorGroupFileListProvider.getFileEntries(project)
    tabList.setListData(entries.toTypedArray())
  }

  private fun getCurrentEditorGroupPanel(project: Project): EditorGroupPanel? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val selectedEditor: FileEditor? = fileEditorManager.selectedEditors.firstOrNull()
    return selectedEditor?.getUserData(EditorGroupPanel.EDITOR_PANEL)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = JPanel(BorderLayout())
    panel.add(tabList, BorderLayout.CENTER)

    // Custom renderer to show icon and name
    tabList.cellRenderer = ListCellRenderer { list, value, _, isSelected, _ ->
      val label = JLabel()
      if (value != null) {
        label.text = value.displayName
        label.icon = value.link.fileIcon
      }
      label.isOpaque = true
      if (isSelected) {
        label.background = list.selectionBackground
        label.foreground = list.selectionForeground
      } else {
        label.background = list.background
        label.foreground = list.foreground
      }
      label
    }

    // Listen for tab changes
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) = updateTabs(project)
    })

    // Switch tab on click
    tabList.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 1) {
          val index = tabList.locationToIndex(e.point)
          val (link, _) = tabList.model.getElementAt(index)
          val panel = getCurrentEditorGroupPanel(project)
          val virtualFile = link.virtualFile

          if (virtualFile != null && panel != null) {
            EditorGroupManager.getInstance(project).openGroupFile(
              groupPanel = panel,
              fileToOpen = virtualFile,
              line = null,
              newWindow = false,
              newTab = false,
              split = Splitters.NONE
            )
          }
        }
      }
    })

    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, "Tabs", false)
    toolWindow.contentManager.addContent(content)
  }
}