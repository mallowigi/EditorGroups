package krasa.editorGroups.services

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.model.BookmarksGroup
import krasa.editorGroups.model.EditorGroup

@Service(Service.Level.PROJECT)
class ExternalGroupProvider(private val project: Project) {
  val defaultBookmarkGroup: BookmarksGroup
    get() {
      val defaultGroup = BookmarksManager.getInstance(project)?.defaultGroup
      return BookmarksGroup(defaultGroup, project)
    }

  val bookmarkGroups: List<BookmarksGroup>
    get() {
      val bookmarksManager = BookmarksManager.getInstance(project)
      val allGroups = bookmarksManager?.groups ?: return emptyList()

      return allGroups
        .map { BookmarksGroup(it, project) }
    }

  /** Get a Bookmark group by title. */
  fun getBookmarkGroup(title: String): EditorGroup = bookmarkGroups.find { it.title == title } ?: EditorGroup.EMPTY

  /** Find Bookmark groups that contain the file. */
  fun findGroups(currentFile: VirtualFile): List<BookmarksGroup> {
    val start = System.currentTimeMillis()

    val bookmarkGroups = this.bookmarkGroups
      .filter { it.containsLink(project, currentFile) }

    thisLogger().debug("findGroups ${System.currentTimeMillis() - start}ms") // NON-NLS

    return bookmarkGroups
  }

  companion object {
    fun getInstance(project: Project): ExternalGroupProvider = project.getService(ExternalGroupProvider::class.java)
  }
}