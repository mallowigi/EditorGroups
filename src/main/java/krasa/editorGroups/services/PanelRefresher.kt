package krasa.editorGroups.services

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.index.EditorGroupIndex
import krasa.editorGroups.index.IndexCache
import krasa.editorGroups.model.BookmarksGroup
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.EditorGroupIndexValue
import krasa.editorGroups.model.FolderGroup
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

@Service(Service.Level.PROJECT)
class PanelRefresher(private val project: Project) : Disposable {
  private val cacheReady = AtomicBoolean()
  private val cache: IndexCache = IndexCache.getInstance(project)

  init {
    // When switching from dumb mode to smart mode, refresh all panels.
    project.messageBus.connect()
      .subscribe(
        DumbService.DUMB_MODE,
        object : DumbService.DumbModeListener {
          override fun enteredDumbMode() = Unit

          override fun exitDumbMode() = onSmartMode()
        }
      )

    addBookmarksListener()
  }

  private fun addBookmarksListener() {
    val connect = project.messageBus.connect(this)
    connect
      .subscribe(
        BookmarksListener.TOPIC,
        object : BookmarksListener {
          override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) = refresh()

          override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) = refresh()

          override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark) = refresh()

          override fun groupsSorted() = refresh()

          override fun groupAdded(group: BookmarkGroup) = refresh()

          override fun groupRemoved(group: BookmarkGroup) = refresh()

          override fun groupRenamed(group: BookmarkGroup) = refresh()

          override fun bookmarksSorted(group: BookmarkGroup) = refresh()

          override fun bookmarkTypeChanged(bookmark: Bookmark) = refresh()

          override fun defaultGroupChanged(oldGroup: BookmarkGroup?, newGroup: BookmarkGroup?) = refresh()

          fun refresh() {
            iteratePanels(
              BiConsumer { panel: EditorGroupPanel, displayedGroup: EditorGroup ->
                if (displayedGroup !is BookmarksGroup) return@BiConsumer

                thisLogger().debug("BookmarksListener refreshing ${panel.file.name}") // NON-NLS
                panel.refreshPane(refresh = true, newGroup = displayedGroup)
              }
            )
          }
        }
      )
  }

  /**
   * Iterates over all editor panels in the current project and applies the given bi-consumer to each panel and its displayed group.
   *
   * @param biConsumer a BiConsumer that accepts an EditorGroupPanel and an EditorGroup and performs an operation on them
   */
  private fun iteratePanels(biConsumer: BiConsumer<EditorGroupPanel, EditorGroup>) {
    val manager = FileEditorManager.getInstance(project)
    for (selectedEditor in manager.allEditors) {
      val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL) ?: continue

      val displayedGroup = panel.getDisplayedGroupOrEmpty()
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

        thisLogger().debug("> onSmartMode") // NON-NLS

        val start = System.currentTimeMillis()
        val manager = FileEditorManager.getInstance(project)

        for (selectedEditor in manager.selectedEditors) {   // refreshing not selected one fucks up tabs scrolling
          val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL) ?: continue

          val displayedGroup = panel.getDisplayedGroupOrEmpty()
          if (displayedGroup is FolderGroup) continue

          thisLogger().debug("onSmartMode: refreshing panel for ${panel.file}") // NON-NLS

          panel.refreshPane(refresh = false, newGroup = null)
        }

        thisLogger().debug("onSmartMode ${System.currentTimeMillis() - start}ms ${Thread.currentThread().name}") // NON-NLS
      }
    })
  }

  /**
   * Refresh all panels of a given owner
   *
   * @param owner
   */
  fun refresh(owner: String) {
    val manager = FileEditorManager.getInstance(project)
    for (selectedEditor in manager.allEditors) {
      val panel = selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL) ?: continue

      if (panel.getDisplayedGroupOrEmpty().isOwner(owner)) {
        panel.refreshPane(refresh = false, newGroup = null)
      }
    }
  }

  /** Refresh all panels. */
  fun refresh() {
    val manager = FileEditorManager.getInstance(project)
    for (selectedEditor in manager.allEditors) {
      selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
        ?.refreshPane(refresh = true, newGroup = null)
    }
  }

  fun onIndexingDone(ownerPath: String, group: EditorGroupIndexValue): EditorGroupIndexValue {
    val resultGroup = cache.onIndexingDone(ownerPath, group)

    if (DumbService.isDumb(project)) return resultGroup

    val start = System.currentTimeMillis()
    val manager = FileEditorManager.getInstance(project)

    for (selectedEditor in manager.allEditors) {
      selectedEditor.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL)
        ?.onIndexingDone(ownerPath = ownerPath, group = resultGroup)
    }

    thisLogger().debug(
      "onIndexingDone $ownerPath - ${System.currentTimeMillis() - start}ms ${Thread.currentThread().name}" // NON-NLS
    )

    return resultGroup
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
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (_: IndexNotReadyException) {
      thisLogger().debug("initCache failed on IndexNotReadyException, will be executed again") // NON-NLS
      initCache()
      return
    }

    cacheReady.set(true)
    onSmartMode()

    thisLogger().debug("initCache done ${System.currentTimeMillis() - start}") // NON-NLS
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
      fileBasedIndex.getAllKeys(EditorGroupIndex.NAME, this@PanelRefresher.project)
        .forEach {
          fileBasedIndex.getValues(
            EditorGroupIndex.NAME,
            it,
            GlobalSearchScope.allScope(this@PanelRefresher.project)
          )
            .forEach(cache::initGroup)
        }
    }
  }

  override fun dispose() {
    // do nothing yet
  }

  companion object {
    fun getInstance(project: Project): PanelRefresher = project.getService(PanelRefresher::class.java)
  }
}