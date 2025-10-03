package krasa.editorGroups.model

import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.project.Project
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.ui.FileColorManager
import krasa.editorGroups.icons.EditorGroupsIcons
import krasa.editorGroups.messages.EditorGroupsBundle.message
import org.jetbrains.annotations.NonNls
import java.awt.Color
import javax.swing.Icon

class BookmarksGroup(val bookmarkGroup: BookmarkGroup?, val project: Project) : EditorGroup() {
  private val links: MutableList<Link> = mutableListOf()

  @NonNls
  override val id: String = "$ID_PREFIX:${bookmarkGroup?.name}"

  override val title: String = message("bookmarks")

  override val isValid: Boolean = true

  override val isCustom: Boolean = true

  val name: String
    get() = bookmarkGroup?.name ?: message("unnamed")

  override val switchDescription: String?
    get() = when (bookmarkGroup?.isDefault) {
      true -> message("default")
      else -> null
    }

  override val bgColor: Color?
    get() {
      val fileColorManager = FileColorManager.getInstance(project)
      return fileColorManager.getScopeColor(BOOKMARKS_GROUP_SCOPE_ID)
    }

  init {
    loadBookmarks(project)
  }

  private fun loadBookmarks(project: Project) {
    bookmarkGroup?.getBookmarks()?.forEach { bookmark ->
      when (bookmark) {
        is LineBookmark -> loadLineBookmark(
          bookmark = bookmark,
          bookmarkGroup = bookmarkGroup,
          project = project
        )

        is FileBookmark -> loadFileBookmark(
          bookmark = bookmark,
          bookmarkGroup = bookmarkGroup,
          project = project
        )
      }
    }
  }

  private fun loadLineBookmark(bookmark: LineBookmark, bookmarkGroup: BookmarkGroup, project: Project) {
    val file = bookmark.file
    val icon = EditorGroupsIcons.bookmarks
    val line = bookmark.line
    val desc = bookmarkGroup.getDescription(bookmark)

    if (file.isDirectory) {
      file.children.forEach { child -> links.add(VirtualFileLink(child, project)) }
      return
    }

    links.add(
      VirtualFileLink(
        file = file,
        icon = icon,
        line = line,
        project = project
      ).withDescription(desc)
    )
  }

  private fun loadFileBookmark(bookmark: FileBookmark, bookmarkGroup: BookmarkGroup, project: Project) {
    val file = bookmark.file
    val desc = bookmarkGroup.getDescription(bookmark)

    if (file.isDirectory) {
      file.children.forEach { child -> links.add(VirtualFileLink(child, project).withDescription(desc)) }
      return
    }

    links.add(VirtualFileLink(file, project).withDescription(desc))
  }

  override fun isSelected(editorGroup: EditorGroup): Boolean = when (editorGroup) {
    is BookmarksGroup -> editorGroup.id == this.id
    else              -> super.isSelected(editorGroup)
  }

  override fun switchTitle(project: Project): String = "$title - [$name]"

  override fun icon(): Icon = EditorGroupsIcons.bookmarks

  override fun invalidate(): Unit = Unit

  override fun size(project: Project): Int = links.size

  override fun getLinks(project: Project): List<Link> = links

  override fun isOwner(ownerPath: String): Boolean = false

  override fun equals(other: Any?): Boolean = other is BookmarksGroup && other.id == this.id

  override fun needSmartMode(): Boolean = true

  override fun toString(): String = "BookmarksGroup{links=$links, name='$name'}"

  override fun getTabTitle(project: Project, presentableNameForUI: String): String {
    val isEmptyName = name.isEmpty()

    return when {
      !isEmptyName -> "[$name] $presentableNameForUI"
      else         -> presentableNameForUI
    }
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + links.hashCode()
    result = 31 * result + title.hashCode()
    result = 31 * result + isValid.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  class BookmarksGroupScope : NamedScope(
    BOOKMARKS_GROUP_SCOPE_ID,
    EditorGroupsIcons.bookmarks,
    null
  ) {
    @NonNls
    override fun getDefaultColorName(): String = "Green"

    override fun getPresentableName(): String = BOOKMARKS_GROUP_SCOPE_NAME
  }

  companion object {
    const val ID_PREFIX: String = "BOOKMARKS"

    @NonNls
    const val BOOKMARKS_GROUP_SCOPE_ID: String = "krasa.editorGroups.model.BookmarksGroup"
    val BOOKMARKS_GROUP_SCOPE_NAME: String = message("group.bookmarks.scope")
    val BOOKMARKS_GROUP_SCOPE: NamedScope = BookmarksGroupScope()
  }
}
