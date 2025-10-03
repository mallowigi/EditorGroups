package krasa.editorGroups.toolwindow

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.support.Splitters
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.JPanel

internal class EditorGroupsToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = JPanel(BorderLayout())
    val tabList = JBList<EditorGroupFileListProvider.FileEntry>()
    panel.add(tabList, BorderLayout.CENTER)

    // Use ColoredListCellRenderer for native look and feel
    tabList.cellRenderer = object : ColoredListCellRenderer<EditorGroupFileListProvider.FileEntry>() {
      override fun customizeCellRenderer(
        list: JList<out EditorGroupFileListProvider.FileEntry?>,
        value: EditorGroupFileListProvider.FileEntry?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        if (value != null) {
          icon = value.link.fileIcon
          val currentFile = getCurrentFile(project)
          val attrs = when (value.link.virtualFile) {
            currentFile -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            else        -> SimpleTextAttributes.REGULAR_ATTRIBUTES
          }

          append(value.displayName, attrs)
          toolTipText = value.link.path
        }
        border = JBUI.Borders.empty(6, 12)
      }
    }

    // Add speed search for quick filtering
    TreeUIHelper.getInstance().installListSpeedSearch(tabList) { (_, displayName) -> displayName }

    fun updateTabs(selectCurrent: Boolean = true) {
      val entries = EditorGroupFileListProvider.getFileEntries(project)
      tabList.setListData(entries.toTypedArray())
      if (selectCurrent) {
        val currentFile = getCurrentFile(project)
        val idx = entries.indexOfFirst { (link, _) -> link.virtualFile == currentFile }
        if (idx >= 0) tabList.selectedIndex = idx
      }
    }

    updateTabs()

    // Listen for tab changes and update selection
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        updateTabs()
      }
    })

    // Switch tab on click, only if not already open
    tabList.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 1) {
          val index = tabList.locationToIndex(e.point)
          val (link, _) = tabList.model.getElementAt(index)
          val panel = getCurrentEditorGroupPanel(project)
          val virtualFile = link.virtualFile
          val currentFile = getCurrentFile(project)
          if (virtualFile != null && panel != null && virtualFile != currentFile) {
            EditorGroupManager.getInstance(project).openGroupFile(
              groupPanel = panel,
              fileToOpen = virtualFile,
              line = null,
              newWindow = false,
              newTab = false,
              split = Splitters.NONE
            )
            // Update selection after switching
            updateTabs()
          }
        }
      }
    })

    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(panel, "Current Editor Group", false)
    toolWindow.contentManager.addContent(content)
  }

  private fun getCurrentEditorGroupPanel(project: Project): EditorGroupPanel? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val selectedEditor: FileEditor? = fileEditorManager.selectedEditors.firstOrNull()
    return selectedEditor?.getUserData(EditorGroupPanel.EDITOR_PANEL)
  }

  private fun getCurrentFile(project: Project): com.intellij.openapi.vfs.VirtualFile? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    return fileEditorManager.selectedFiles.firstOrNull()
  }
}