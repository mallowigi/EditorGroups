package krasa.editorGroups.toolwindow

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.model.Link
import krasa.editorGroups.support.UniqueTabNameBuilder
import krasa.editorGroups.support.getFileIcon
import javax.swing.Icon

/**
 * Provides the list of files (Links) and their display names for the current editor group.
 */
object EditorGroupFileListProvider {
  data class FileEntry(
    val link: Link,
    val displayName: String,
    val icon: Icon
  )

  fun getFileEntries(project: Project): List<FileEntry> {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val selectedEditor: FileEditor? = fileEditorManager.selectedEditors.firstOrNull()
    val panel: EditorGroupPanel = selectedEditor?.getUserData(EditorGroupPanel.EDITOR_PANEL) ?: return emptyList()

    val group = panel.getDisplayedGroupOrEmpty()
    val links: List<Link> = group.getLinks(project)
    val uniqueTabNameBuilder = UniqueTabNameBuilder(project)
    val namesByPath = uniqueTabNameBuilder.getNamesByPath(
      paths = links,
      currentFile = null,
      project = project
    )

    return links.map { link ->
      FileEntry(
        link,
        namesByPath[link] ?: link.name,
        getFileIcon(link.virtualFile?.path, project)
      )
    }
  }
}