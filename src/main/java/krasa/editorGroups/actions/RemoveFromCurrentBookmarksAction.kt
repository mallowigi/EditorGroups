package krasa.editorGroups.actions

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.BookmarksGroup
import krasa.editorGroups.support.Notifications
import krasa.editorGroups.support.Notifications.showWarning
import krasa.editorGroups.support.Splitters

class RemoveFromCurrentBookmarksAction : EditorGroupsAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val bookmarksGroup = getBookmarkGroup(e)

    presentation.setVisible(bookmarksGroup != null)

    if (bookmarksGroup != null) {
      presentation.setText(message("action.remove.from.bookmarks.text", bookmarksGroup.name))
      presentation.setEnabled(isEnabled(e, bookmarksGroup))
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val bookmarkGroup = getBookmarkGroup(e) ?: return
    val project = e.project ?: return

    // The files to remove
    val filesToRemove = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.dataContext)
    if (filesToRemove.isNullOrEmpty()) return
    val filesToRemoveSet = mutableSetOf(*filesToRemove)

    // Find the current bookmark group in the bookmarks manager
    val groupTitle = bookmarkGroup.name
    val bookmarkManager = BookmarksManager.getInstance(project) ?: return
    val bookmarkGroupToEdit = bookmarkManager.groups.find { it.name == groupTitle }

    if (bookmarkGroupToEdit == null) {
      notifyFail(groupTitle, filesToRemoveSet)
      return
    }

    // Remove the files from the bookmarks group
    bookmarkGroupToEdit.getBookmarks().forEach { bookmark ->
      if (bookmark !is FileBookmark) return@forEach

      if (filesToRemove.contains(bookmark.file)) {
        filesToRemoveSet.add(bookmark.file)
        bookmarkGroupToEdit.remove(bookmark)
      }
    }

    if (filesToRemoveSet.isEmpty()) {
      notifyFail(groupTitle, filesToRemoveSet)
      return
    }

    // Switch to another file if the current file is part of the files to remove
    val editorGroupPanel = getEditorGroupPanel(e) ?: return
    if (filesToRemoveSet.contains(editorGroupPanel.file)) {
      val next = editorGroupPanel.goToNextTab(newTab = false, newWindow = false, split = Splitters.NONE)
      if (!next) editorGroupPanel.goToPreviousTab(newTab = false, newWindow = false, split = Splitters.NONE)
    }

    Notifications.notifySimple(message("removed.from.0.1", groupTitle, filesToRemoveSet))
  }

  private fun notifyFail(name: String, selected: MutableSet<VirtualFile>) {
    showWarning(message("unable.to.remove.from.the.current.bookmark.group.file.0.from.1", selected, name))
  }

  /**
   * Retrieves the [BookmarksGroup] from the given `AnActionEvent` if present.
   *
   * @param e The `AnActionEvent` from which to retrieve the [BookmarksGroup].
   * @return The [BookmarksGroup] if found, or null otherwise.
   */
  private fun getBookmarkGroup(e: AnActionEvent): BookmarksGroup? {
    val eventProject = getEventProject(e) ?: return null

    val savedBookmarkGroup = EditorGroupPanel.BOOKMARK_GROUP.getData(e.dataContext)
    if (savedBookmarkGroup != null) return savedBookmarkGroup

    val fileEditorManager = FileEditorManager.getInstance(eventProject) as FileEditorManagerEx
    val currentWindow = fileEditorManager.currentWindow ?: return null

    // Get the editor group panel from the current window.
    val editor = currentWindow.getSelectedComposite(ignorePopup = true) ?: return null

    val selectedEditor = editor.selectedWithProvider?.fileEditor
    val editorGroupPanel = selectedEditor?.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL) ?: return null

    // If there is an editor group panel, get the displayed group. If it's the favorites group, return it.
    return editorGroupPanel.getDisplayedGroupOrEmpty() as? BookmarksGroup
  }

  /** Only enable if the current file is in the favorites group. */
  private fun isEnabled(e: AnActionEvent, bookmarksGroup: BookmarksGroup): Boolean {
    val project = e.project ?: return false
    val dataContext = e.dataContext
    val virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext) ?: return true

    return virtualFiles.any { bookmarksGroup.containsLink(project, it) }
  }

  companion object {
    const val ID: String = "krasa.editorGroups.actions.RemoveFromCurrentBookmarks"
  }
}
