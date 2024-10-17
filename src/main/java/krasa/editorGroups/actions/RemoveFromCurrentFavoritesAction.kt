package krasa.editorGroups.actions

import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.ide.projectView.impl.AbstractUrl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.TreeItem
import krasa.editorGroups.EditorGroupPanel2
import krasa.editorGroups.Splitters
import krasa.editorGroups.model.FavoritesGroup
import krasa.editorGroups.support.Notifications.showWarning

class RemoveFromCurrentFavoritesAction : EditorGroupsAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val favoritesGroup = getFavoritesGroup(e)

    presentation.setVisible(favoritesGroup != null)

    if (favoritesGroup != null) {
      presentation.setText("Remove from Favorites - ${favoritesGroup.title}")
      presentation.setEnabled(isEnabled(e, favoritesGroup))
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val favoritesGroup = getFavoritesGroup(e)
    if (favoritesGroup == null) return

    // The files to remove
    val filesToRemove = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.dataContext)
    if (filesToRemove.isNullOrEmpty()) return

    val name = favoritesGroup.title
    val filesToRemoveSet = mutableSetOf(*filesToRemove)

    // TODO replace with Bookmarks
    val favoritesManager = FavoritesManager.getInstance(e.project!!)
    val favoritesRootUrls = favoritesManager.getFavoritesListRootUrls(name)
    val items = mutableListOf<TreeItem<Pair<AbstractUrl, String>>>()
    val forRemoval = filterOutFavorites(items, favoritesRootUrls, e.project, filesToRemoveSet)

    if (forRemoval.isEmpty()) {
      notifyFail(name, filesToRemoveSet)
      return
    }

    // Remove the forRemoval files from the favorites list
    val isRemoved = favoritesRootUrls.removeAll(forRemoval)
    if (!isRemoved) notifyFail(name, filesToRemoveSet)

    // Switch to another file if the current file is part of the files to remove
    val editorGroupPanel = getEditorGroupPanel(e)
    if (filesToRemoveSet.contains(editorGroupPanel!!.getFile())) {
      val next = editorGroupPanel.next(false, false, Splitters.NONE)
      if (!next) editorGroupPanel.previous(false, false, Splitters.NONE)
    }
  }

  private fun notifyFail(name: String, selected: MutableSet<VirtualFile>) {
    showWarning("Unable to remove, probably the whole folder is bookmarked. File:$selected', from '$name")
  }

  private fun filterOutFavorites(
    items: MutableList<TreeItem<Pair<AbstractUrl, String>>>,
    favoriteUrls: MutableList<TreeItem<Pair<AbstractUrl, String>>>,
    project: Project?,
    filesToRemoveSet: MutableSet<VirtualFile>
  ): MutableList<TreeItem<Pair<AbstractUrl, String>>> {
    for (favoriteUrl in favoriteUrls) {
      val pair: Pair<AbstractUrl, String> = favoriteUrl.getData()
      val abstractUrl = pair.first

      val path = abstractUrl.createPath(project)
      if (path == null || path.size < 1 || path[0] == null) continue

      val element = path[0]
      when (element) {
        is SmartPsiElementPointer<*> -> addToFilesToRemove(items, favoriteUrl, element.getElement(), filesToRemoveSet)
        is PsiElement                -> addToFilesToRemove(items, favoriteUrl, element, filesToRemoveSet)
        else                         -> filterOutFavorites(items, favoriteUrl.getChildren(), project, filesToRemoveSet)
      }
    }
    return items
  }

  private fun addToFilesToRemove(
    items: MutableList<TreeItem<Pair<AbstractUrl, String>>>,
    favoriteUrl: TreeItem<Pair<AbstractUrl, String>>,
    psiElement: PsiElement?,
    filesToRemoveSet: MutableSet<VirtualFile>
  ) {
    val virtualFile = PsiUtilCore.getVirtualFile(psiElement)
    if (virtualFile == null) return

    if (filesToRemoveSet.contains(virtualFile)) items.add(favoriteUrl)
  }

  /**
   * Retrieves the `FavoritesGroup` from the given `AnActionEvent` if present.
   *
   * @param e The `AnActionEvent` from which to retrieve the `FavoritesGroup`.
   * @return The `FavoritesGroup` if found, or null otherwise.
   */
  private fun getFavoritesGroup(e: AnActionEvent): FavoritesGroup? {
    val eventProject = getEventProject(e) ?: return null

    val savedFavoritesGroup = EditorGroupPanel2.FAVORITE_GROUP.getData(e.dataContext)
    if (savedFavoritesGroup != null) return savedFavoritesGroup

    val fileEditorManager = FileEditorManager.getInstance(eventProject) as FileEditorManagerEx
    val currentWindow = fileEditorManager.currentWindow ?: return null

    // Get the editor group panel from the current window.
    val editor = currentWindow.getSelectedComposite(ignorePopup = true) ?: return null

    val selectedEditor = editor.selectedWithProvider?.fileEditor
    val editorGroupPanel = selectedEditor?.getUserData<EditorGroupPanel2?>(EditorGroupPanel2.EDITOR_PANEL) ?: return null

    // If there is an editor group panel, get the displayed group. If it's the favorites group, return it.
    val displayedGroup = editorGroupPanel.getDisplayedGroup()
    if (displayedGroup is FavoritesGroup) return displayedGroup
    return null
  }

  /** Only enable if the current file is in the favorites group. */
  private fun isEnabled(e: AnActionEvent, favoritesGroup: FavoritesGroup): Boolean {
    val project = e.project ?: return false
    val dataContext = e.dataContext

    val virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
    if (virtualFiles == null) return true

    for (virtualFile in virtualFiles) {
      val contains = favoritesGroup.containsLink(project = project, currentFile = virtualFile)
      if (contains) return true
    }
    return false
  }

  companion object {
    const val ID: String = "krasa.editorGroups.actions.RemoveFromCurrentFavorites"
  }
}
