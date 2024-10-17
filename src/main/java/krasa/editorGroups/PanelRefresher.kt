package krasa.editorGroups

import com.intellij.ide.bookmarks.Bookmark
import com.intellij.ide.bookmarks.BookmarksListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import krasa.editorGroups.index.EditorGroupIndex
import krasa.editorGroups.model.BookmarkGroup
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.EditorGroupIndexValue
import krasa.editorGroups.model.FolderGroup
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

@Service(Service.Level.PROJECT)
class PanelRefresher(private val project: Project) {
  private val cacheReady = AtomicBoolean()
  private val cache: IndexCache = IndexCache.getInstance(project)

  init {
    // When switching from dumb mode to smart mode, refresh all panels.
    project.messageBus.connect()
      .subscribe(DumbService.Companion.DUMB_MODE, object : DumbService.DumbModeListener {
        override fun enteredDumbMode() = Unit

        override fun exitDumbMode() = onSmartMode()
      })

    addBookmarksListener()
  }

  private fun addBookmarksListener() {
    project.messageBus.connect(project).subscribe(BookmarksListener.TOPIC, object : BookmarksListener {
      override fun bookmarkAdded(b: Bookmark) = refresh()

      override fun bookmarkRemoved(b: Bookmark) = refresh()

      override fun bookmarkChanged(b: Bookmark) = refresh()

      override fun bookmarksOrderChanged() = refresh()

      fun refresh() {
        iteratePanels(BiConsumer { panel: EditorGroupPanel, displayedGroup: EditorGroup ->
          if (displayedGroup is BookmarkGroup) {
            thisLogger().debug("BookmarksListener refreshing ${panel.file.name}")
            panel._refresh(true, displayedGroup)
          }
        })
      }
    })
  }

  /**
   * Iterates over all editor panels in the current project and applies the given bi-consumer to each panel and its displayed group.
   *
   * @param biConsumer a BiConsumer that accepts an EditorGroupPanel and an EditorGroup and performs an operation on them
   */
  private fun iteratePanels(biConsumer: BiConsumer<EditorGroupPanel, EditorGroup>) {
    val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    for (selectedEditor in manager.getAllEditors()) {
      val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
      if (panel == null) continue

      val displayedGroup = panel.getDisplayedGroup()
      biConsumer.accept(panel, displayedGroup)
    }
  }

  /**
   * Refreshes the panels for the selected editors if the cache is ready and the project is not disposed. This method is typically used to
   * handle changes when switching to smart mode in the application.
   */
  fun onSmartMode() {
    if (!cacheReady.get()) return

    ApplicationManager.getApplication().invokeLater(object : Runnable {
      override fun run() {
        if (project.isDisposed) return

        thisLogger().debug(">onSmartMode")

        val start = System.currentTimeMillis()
        val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl

        for (selectedEditor in manager.getSelectedEditors()) {   // refreshing not selected one fucks up tabs scrolling
          val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
          if (panel == null) continue

          val displayedGroup = panel.getDisplayedGroup()
          if (displayedGroup is FolderGroup) continue

          thisLogger().debug("onSmartMode: refreshing panel for ${panel.file}")

          panel._refresh(false, null)
        }

        thisLogger().debug("onSmartMode ${System.currentTimeMillis() - start}ms ${Thread.currentThread().name}")
      }
    })
  }

  /**
   * Refresh all panels of a given owner
   *
   * @param owner
   */
  fun refresh(owner: String) {
    val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    for (selectedEditor in manager.getAllEditors()) {
      val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
      if (panel == null) continue

      if (panel.getDisplayedGroup().isOwner(owner)) {
        panel._refresh(false, null)
      }
    }
  }

  /** Refresh all panels. */
  fun refresh() {
    val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    for (selectedEditor in manager.getAllEditors()) {
      val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
      panel?._refresh(true, null)
    }
  }

  fun onIndexingDone(ownerPath: String, group: EditorGroupIndexValue): EditorGroupIndexValue {
    var group = group
    group = cache.onIndexingDone(ownerPath, group)
    if (DumbService.isDumb(project)) { // optimization
      return group
    }

    val start = System.currentTimeMillis()
    val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    for (selectedEditor in manager.getAllEditors()) {
      val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
      if (panel != null) {
        panel.onIndexingDone(ownerPath, group)
      }
    }

    thisLogger().debug(
      "onIndexingDone $ownerPath - ${System.currentTimeMillis() - start}ms ${Thread.currentThread().name}"
    )
    return group
  }

  /**
   * Initializes the cache for the project, ensuring it is ready for use.
   *
   * @throws ProcessCanceledException if the process was canceled.
   * @throws IndexNotReadyException if the index is not ready.
   */
  suspend fun initCache() {
    if (project.isDisposed) return

    val start = System.currentTimeMillis()
    val fileBasedIndex = FileBasedIndex.getInstance()
    val cache = IndexCache.getInstance(project)

    try {
      initializeCache(fileBasedIndex, cache)
    } catch (_: ProcessCanceledException) {
      thisLogger().debug("initCache failed on ProcessCanceledException, will be executed again")
      initCache()
      return
    } catch (_: IndexNotReadyException) {
      thisLogger().debug("initCache failed on IndexNotReadyException, will be executed again")
      initCache()
      return
    }

    cacheReady.set(true)
    onSmartMode()

    thisLogger().debug("initCache done ${System.currentTimeMillis() - start}")
    // .inSmartMode(project)
    // .expireWith(project)
    // .submit(ourThreadExecutorsService)
    // .onError(Consumer { t: Throwable? -> thisLogger().error(t) })
  }

  /**
   * Initializes the cache with the groups indexed in the [EditorGroupIndex].
   *
   * @param fileBasedIndex The file-based index to retrieve keys and values from.
   * @param cache The cache instance to be initialized with the groups.
   */
  private suspend fun initializeCache(fileBasedIndex: FileBasedIndex, cache: IndexCache) {
    withContext(Dispatchers.IO) {
      val keys = fileBasedIndex.getAllKeys<String>(EditorGroupIndex.NAME, project)
      keys.forEach {
        val groups =
          fileBasedIndex.getValues(EditorGroupIndex.NAME, it, GlobalSearchScope.allScope(project))
        groups.forEach(cache::initGroup)
      }
    }
  }

  companion object {
    fun getInstance(project: Project): PanelRefresher = project.getService<PanelRefresher>(PanelRefresher::class.java)
  }
}
