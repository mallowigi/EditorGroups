@file:Suppress(
  "detekt:MaximumLineLength",
  "detekt:MaxLineLength",
  "detekt:ComplexCondition",
  "detekt:CyclomaticComplexMethod",
  "detekt:TooGenericExceptionThrown",
  "detekt:ThrowsCount"
)

package krasa.editorGroups

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import krasa.editorGroups.actions.PopupMenu
import krasa.editorGroups.actions.RefreshAction
import krasa.editorGroups.actions.RemoveFromCurrentBookmarksAction
import krasa.editorGroups.actions.SwitchGroupAction
import krasa.editorGroups.index.IndexNotReady
import krasa.editorGroups.language.EditorGroupsLanguage.isEditorGroupsLanguage
import krasa.editorGroups.model.*
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.*
import krasa.editorGroups.tabs2.EditorGroupsTabsBase
import krasa.editorGroups.tabs2.EditorGroupsTabsContainer
import krasa.editorGroups.tabs2.EditorGroupsTabsPosition
import krasa.editorGroups.tabs2.impl.themes.EditorGroupsUI
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("LargeClass", "MagicNumber", "LongLine")
class EditorGroupPanel(
  val fileEditor: FileEditor,
  val project: Project,
  switchRequest: SwitchRequest?,
  val file: VirtualFile
) : JBPanel<EditorGroupPanel>(BorderLayout()), Weighted, Disposable, UiCompatibleDataProvider {

  /** Keep state of the showPanel setting to decide whether to update the visibility on refresh. */
  private var hideGlobally = false

  /** Disposed state, to prevent refreshing during disposal. */
  var disposed: Boolean = false

  var currentTabPlacement: Int = SwingConstants.TOP

  /**
   * A thread-safe reference to the current refresh request.
   *
   * This variable is used to store an instance of `RefreshRequest`, which contains the refresh status and the requested editor group. It's
   * updated atomically to ensure thread safety during panel refresh operations.
   */
  internal var atomicRefreshRequest = AtomicReference<RefreshRequest?>()

  /** Keep a state indicating that we are in the middle of a refresh. */
  @Volatile
  var interrupt: Boolean = false

  @Volatile
  private var brokenScroll = false

  /** Instance of the DumbService. */
  private val dumbService = DumbService.getInstance(project)

  // A thread executor per file
  private val myTaskExecutor: ExecutorService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Krasa.editorGroups.EditorGroupPanel-${file.name}", 1)

  /** The current editor's file. */
  private val fileFromTextEditor = getFileFromTextEditor(fileEditor)

  /** The tabs component for this editor panel. */
  val tabs: EditorGroupsTabsContainer = EditorGroupsTabsContainer(
    project,
    fileEditor,
    file
  )

  /** Instance of the file editor manager for this project. */
  private val fileEditorManager = FileEditorManager.getInstance(project)

  /** The group manager for this project. */
  var groupManager: EditorGroupManager = EditorGroupManager.getInstance(project)

  /** The action toolbar. */
  private lateinit var toolbar: ActionToolbar

  /** The current file's index in the list of tabs. */
  private var currentIndex = NOT_INITIALIZED

  /** The currently displayed group. */
  @Volatile
  private var displayedGroup: EditorGroup? = null

  /** Instance of the unique tab name builder. */
  private val uniqueNameBuilder = UniqueTabNameBuilder(project)

  /** The group to be rendered for this file. */
  @Volatile
  var groupToBeRendered: EditorGroup? = switchRequest?.group

  /** The scroll offset from the switch request. */
  @Volatile
  private var myScrollOffset = switchRequest?.myScrollOffset ?: 0

  /** The line from the switch request. */
  private val line: Int? = switchRequest?.line

  val root: JComponent
    get() = this

  init {
    thisLogger().debug(
      """>new EditorGroupPanel, fileEditor = [$fileEditor], project = [${project.name}], switchingRequest = [$switchRequest], file = [$file]"""
    )

    // Disposes this when fileEditor is disposed
    Disposer.register(fileEditor, this)

    // Add this editor panel to the context of the file editor
    this.fileEditor.putUserData<EditorGroupPanel?>(EDITOR_PANEL, this)

    // If the selected tab is an editor tab, add a listener to refresh the panel when focused
    if (fileEditor is TextEditorImpl) {
      val editor: Editor = fileEditor.getEditor()
      if (editor is EditorImpl) {
        editor.addFocusListener(object : FocusChangeListener {
          override fun focusGained(editor: Editor) {
            this@EditorGroupPanel.focusGained()
          }
        })
      }
    }

    // Create the toolbar
    createToolbar()

    // Create the tabs component
    updateTabPlacement()

    // Set the first tab offset
    tabs.setFirstTabOffset(10)

    // tabs.setFocused(true)

    // Add a right click mouse listener to allow remove from favorites
    tabs.addTabMouseListener(EditorTabMouseListener(tabs))

    // Add a custom action "Compare file with editor"
    tabs.setPopupGroupWithSupplier(
      supplier = {
        CustomActionsSchema.getInstance().getCorrectedAction(EDITOR_GROUP_TAB_MENU) as ActionGroup
      },
      place = TAB_PLACE
    )

    // Listen to tab selection
    tabs.setSelectionChangeHandler(TabSelectionChangeHandler(this))

    // Custom tab height
    refreshHeight()

    // Add the tabs to the panel
    val tabsComponent = tabs.getComponent()
    if (groupToBeRendered !is HidePanelGroup) {
      add(tabsComponent, BorderLayout.CENTER)
    }

    // Add a listener to show the popup on right click the panel
    addMouseListener(getPopupHandler())

    // Add a listener to show the popup on right click the tabs
    tabs.addMouseListener(getPopupHandler())

    // Listen to settings change
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(
        EditorGroupsSettings.TOPIC,
        object : EditorGroupsSettings.SettingsNotifier {
          override fun configChanged(config: EditorGroupsSettings) = refreshHeight()
        }
      )
  }

  fun refreshHeight() {
    val tabHeight = getPanelHeight()
    this.preferredSize = Dimension(0, tabHeight)
  }

  fun getPanelHeight(): Int = when {
    EditorGroupsSettings.instance.isCompactTabs -> EditorGroupsUI.compactTabHeight()
    else                                        -> EditorGroupsUI.tabHeight()
  }

  internal fun updateTabPlacement() {
    when (this.currentTabPlacement) {
      SwingConstants.TOP    -> tabs.setTabsPosition(EditorGroupsTabsPosition.TOP)
      SwingConstants.BOTTOM -> tabs.setTabsPosition(EditorGroupsTabsPosition.BOTTOM)
    }
  }

  /** Happens once the panel is built and added. */
  fun postConstruct() {
    val editorGroupsSettingsState = EditorGroupsSettings.instance
    var editorGroup = groupToBeRendered

    // minimize flicker for the price of latency
    val preferLatencyOverFlicker = editorGroupsSettingsState.isInitializeSynchronously

    // If we decide to init sync, load a stub group first
    if (editorGroup == null && preferLatencyOverFlicker && !DumbService.isDumb(project)) {
      val start = System.currentTimeMillis()

      try {
        editorGroup = groupManager.getStubGroup(
          project = project,
          fileEditor = fileEditor,
          displayedGroup = EditorGroup.EMPTY,
          requestedGroup = editorGroup,
          currentFile = file
        )
        this.groupToBeRendered = editorGroup
      } catch (e: Throwable) {
        thisLogger().error(e)
      }

      val delta = System.currentTimeMillis() - start
      if (delta > 200) thisLogger().warn("lag on editor opening - #getGroup took $delta ms for $file")
    }

    if (editorGroup == null && !preferLatencyOverFlicker) {
      val start = System.currentTimeMillis()

      try {
        editorGroup = groupManager.getStubGroup(
          project = project,
          fileEditor = fileEditor,
          displayedGroup = EditorGroup.EMPTY,
          requestedGroup = editorGroup,
          currentFile = file
        )
        this.groupToBeRendered = editorGroup

        val delta = System.currentTimeMillis() - start
        thisLogger().debug("#getGroup:stub - on editor opening took $delta ms for $file, group=$editorGroup")
      } catch (e: ProcessCanceledException) {
        throw e
      } catch (indexNotReady: IndexNotReady) {
        thisLogger().warn("Getting stub group failed:$indexNotReady")
      }
    }

    // If still no editor group, set it to empty
    if (editorGroup == null) {
      thisLogger().debug("editorGroup == null > setVisible=false")
      this.isVisible = false
      refreshPane(refresh = false, newGroup = null)
      return
    }

    var isVisible = updateVisibility(editorGroup)
    val parent = this.getParent()

    // NPE for Code With Me
    if (parent != null) layout.layoutContainer(parent) //  forgot what this does :(

    refreshOnEDT(paintNow = false)

    // Refresh the panel if stub
    if (isVisible && editorGroup.isStub) {
      thisLogger().debug("#postConstruct: stub - calling #_refresh")
      refreshPane(refresh = false, newGroup = null)
    }
  }

  override fun dispose() {
    disposed = true
    myTaskExecutor.shutdownNow()
  }

  /** Display the popup. */
  private fun getPopupHandler(): PopupHandler = object : PopupHandler() {
    override fun invokePopup(comp: Component?, x: Int, y: Int) = PopupMenu.popupInvoked(comp, x, y)
  }

  /** Add the action buttons at the left of the panel. */
  private fun createToolbar() {
    val actionGroup = DefaultActionGroup().apply {
      add(ActionManager.getInstance().getAction(RefreshAction.ID))
      add(ActionManager.getInstance().getAction(SwitchGroupAction.ID))
    }

    toolbar = ActionManager.getInstance().createActionToolbar(
      TOOLBAR_PLACE,
      actionGroup,
      true
    )
    toolbar.targetComponent = this

    toolbar.component.apply {
      // display popup on right click
      addMouseListener(getPopupHandler())
      setBorder(JBUI.Borders.empty())
    }
    add(toolbar.component, BorderLayout.WEST)
  }

  /** Reloads the tabs in the editor panel, clearing existing tabs and fetching new ones based on the currently displayed group. */
  private fun rerenderTabs() {
    val currentGroup = this.displayedGroup
    if (currentGroup == null) return

    try {
      // First remove all current tabs
      tabs.bulkUpdate = true
      tabs.removeAllTabs()

      this.currentIndex = NOT_INITIALIZED

      // Then fetch all the links of the selected group
      val links: List<Link> = currentGroup.getLinks(project)
      // Update the visibility of the group (esp if the hideIfEmpty is enabled)
      updateVisibility(currentGroup)

      // Get the pair of paths to file names
      val pathsToNames: MutableMap<Link, String> = uniqueNameBuilder.getNamesByPath(
        paths = links,
        currentFile = file,
        project = project
      )
      // Create the tabs again
      createTabs(links, pathsToNames)
      // Set the current file tab
      addCurrentFileTab(pathsToNames)

      // If its a group holder, display the groups as tabs
      if (currentGroup is GroupsHolder) {
        createGroupLinks((currentGroup as GroupsHolder).groups)
      }

      // Loading stub
      if (currentGroup.isStub) {
        thisLogger().debug("#reloadTabs: stub - Adding Loading...")

        val tab = MyEditorGroupTabInfo(PathLink("Loading...", project), "Loading...")
        tab.selectable = false
        tabs.addTabSilently(tab, -1)
      }
    } finally {
      tabs.bulkUpdate = false

      tabs.doLayout()
      tabs.scroll(myScrollOffset)
    }
  }

  /**
   * Creates tabs for the editor panel based on the provided list of links and their associated names.
   *
   * @param links A mutable list of Link objects representing the files or directories to be converted into tabs.
   * @param pathToNames A mutable map where each Link is associated with its display name.
   */
  private fun createTabs(links: List<Link>, pathToNames: MutableMap<Link, String>) {
    var start = 0
    var end = links.size
    val tabSizeLimitInt = EditorGroupsSettings.instance.tabSizeLimit

    // If there are too many links than allowed tabs, we slice the links by the max number allowed, with the current file as the center
    if (links.size > tabSizeLimitInt) {
      var currentFilePosition = -1

      // Find the index of the current file
      for (i in links.indices) {
        val link = links[i]
        val virtualFile = link.virtualFile
        if (virtualFile != null && virtualFile == this.fileFromTextEditor && (this.line == null || link.line == this.line)) {
          currentFilePosition = i
          break
        }
      }

      if (currentFilePosition > -1) {
        start = max(0, currentFilePosition - tabSizeLimitInt / 2)
        end = min(links.size, start + tabSizeLimitInt)
      }
      thisLogger().debug("Too many tabs, skipping: ${links.size - tabSizeLimitInt}")
    }

    var j = 0
    for (i1 in start until end) {
      val link = links[i1]
      val tab = MyEditorGroupTabInfo(link = link, name = pathToNames[link]!!)

      // Add the tab silently from the end
      tabs.addTabSilently(info = tab, index = -1)

      // Select the tab if it is the current file
      if (link.line == this.line && link.fileEquals(this.fileFromTextEditor)) {
        tabs.mySelectedInfo = tab
        customizeSelectedColor(tab)
        this.currentIndex = j
      }

      j++
    }

    if (this.currentIndex == NOT_INITIALIZED) selectTabFallback()
  }

  /**
   * Checks if the given group is a custom group.
   *
   * @param group the group to be checked, or null.
   * @return true if the group is an instance of EditorGroups, BookmarkGroup, or HidePanelGroup; false otherwise.
   */
  private fun isCustomGroup(group: EditorGroup?): Boolean = group?.isCustom == true

  /**
   * Adds the current file as a tab in the editor panel.
   *
   * @param pathsToNames A mutable map where each Link is associated with its display name.
   */
  private fun addCurrentFileTab(pathsToNames: MutableMap<Link, String>) {
    val displayedGroupIsNotCustom = displayedGroup !== EditorGroup.EMPTY && !isCustomGroup(displayedGroup)

    when {
      // Editor groups Files
      this.currentIndex < 0 && isEditorGroupsLanguage(file) -> {
        val link = Link.fromFile(file, project)
        val info = MyEditorGroupTabInfo(link, pathsToNames[link]!!)

        // Colorize the groups tab with its color
        customizeSelectedColor(info)
        this.currentIndex = 0

        tabs.addTabSilently(info = info, index = 0)
        tabs.mySelectedInfo = info
      }

      this.currentIndex < 0 && displayedGroupIsNotCustom    -> {
        val isStub = displayedGroup!!.isStub
        val links = displayedGroup!!.getLinks(project)
        val excludeEditorGroupsFiles = EditorGroupsSettings.instance.isExcludeEditorGroupsFiles

        when {
          !isStub && !FileResolver.excluded(
            File(file.path),
            excludeEditorGroupsFiles
          )       -> thisLogger().warn("current file is not contained in group. file=$file, group=$displayedGroup, links=$links")

          !isStub -> thisLogger().debug("current file is excluded from the group $file $displayedGroup $links")
        }
      }
    }
  }

  /**
   * Creates tabs within the editor panel for each provided group.
   *
   * @param groups A collection of EditorGroup objects that need to be converted into tabs.
   */
  private fun createGroupLinks(groups: Collection<EditorGroup>) {
    for (editorGroup in groups) {
      tabs.addTabSilently(info = CustomGroupTabInfo(editorGroup = editorGroup), index = -1)
    }
  }

  /**  */
  fun goToPreviousTab(newTab: Boolean, newWindow: Boolean, split: Splitters): Boolean {
    if (currentIndex == NOT_INITIALIZED) {
      thisLogger().debug("goToPreviousTab fail - currentIndex == -1")
      return false
    }

    if (displayedGroup!!.isInvalid) {
      thisLogger().debug("goToPreviousTab fail - displayedGroup.isInvalid")
      return false
    }

    if (!isVisible) {
      thisLogger().debug("goToPreviousTab fail - !isVisible()")
      return false
    }

    val allTabs: List<EditorGroupTabInfo> = this.tabs.tabs
    val continuousScrolling = EditorGroupsSettings.instance.isContinuousScrolling
    var iterations = 0

    var link: Link? = null

    while (link == null && iterations < allTabs.size) {
      iterations++
      var prevIndex = this.currentIndex - iterations

      // If we reach the start
      if (!continuousScrolling && currentIndex - iterations < 0) return false

      // continuous scrolling
      if (prevIndex < 0) {
        prevIndex = allTabs.size - abs(prevIndex)
      }

      link = getLink(allTabs, prevIndex)
      thisLogger().debug("previous: index=$prevIndex, link=$link")
    }

    return openFile(
      link = link,
      newTab = newTab,
      newWindow = newWindow,
      split = split
    )
  }

  /** Go to next tab. */
  fun goToNextTab(newTab: Boolean, newWindow: Boolean, split: Splitters): Boolean {
    if (currentIndex == NOT_INITIALIZED) {
      thisLogger().debug("goToNextTab fail - currentIndex == -1")
      return false
    }

    if (displayedGroup!!.isInvalid) {
      thisLogger().debug("goToNextTab fail - displayedGroup.isInvalid")
      return false
    }

    if (!isVisible) {
      thisLogger().debug("goToNextTab fail - !isVisible()")
      return false
    }

    var iterations = 0
    val allTabs: List<EditorGroupTabInfo> = this.tabs.tabs
    val continuousScrolling = EditorGroupsSettings.instance.isContinuousScrolling
    var link: Link? = null

    while (link == null && iterations < allTabs.size) {
      iterations++

      // If we reach the end
      if (!continuousScrolling && currentIndex + iterations >= allTabs.size) return false

      val nextIndex = (currentIndex + iterations) % allTabs.size

      link = getLink(allTabs, nextIndex)
      thisLogger().debug("next: index=$nextIndex, link=$link")
    }

    return openFile(link = link, newTab = newTab, newWindow = newWindow, split = split)
  }

  /** Get the file link from the tabs at the given index. */
  private fun getLink(tabs: List<EditorGroupTabInfo>, index: Int): Link? {
    val tabInfo = tabs[index]
    if (tabInfo !is MyEditorGroupTabInfo) return null

    return tabInfo.link
  }

  /** Open the file specified by the Link, with option to open in a new tab/window/with splitters. */
  private fun openFile(link: Link?, newTab: Boolean, newWindow: Boolean, split: Splitters): Boolean {
    if (this.disposed) {
      thisLogger().debug("openFile fail - already disposed")
      return false
    }

    if (link == null) {
      thisLogger().debug("openFile fail - link is null")
      return false
    }

    if (link.virtualFile == null) {
      thisLogger().debug("openFile fail - file is null for $link")
      return false
    }

    if (this.file == link.virtualFile && !newWindow && !split.isSplit && link.line == null) {
      thisLogger().debug("openFile fail - same file")
      return false
    }

    if (groupManager.isSwitching()) {
      thisLogger().debug("openFile fail - switching ")
      return false
    }

    if (groupToBeRendered != null) {
      thisLogger().debug("openFile fail - toBeRendered != null")
      return false
    }

    val fileOpenedResult = groupManager.openGroupFile(
      groupPanel = this,
      fileToOpen = link.virtualFile!!,
      line = link.line,
      newWindow = newWindow,
      newTab = newTab,
      split = split
    )

    if (fileOpenedResult != null && fileOpenedResult.isScrolledOnly) {
      selectTab(link)
    }
    return true
  }

  /** Find the tab with the current `fileFromTextEditor` and select it. */
  private fun selectTabFallback() {
    val allTabs: List<EditorGroupTabInfo> = this.tabs.tabs

    allTabs.indices.forEach { i ->
      val tab = allTabs[i]
      if (tab is MyEditorGroupTabInfo && tab.link.fileEquals(this.fileFromTextEditor)) {
        tabs.mySelectedInfo = tab
        customizeSelectedColor(tab)
        this.currentIndex = i
      }
    }
  }

  /** Select the tab of the provided link. */
  private fun selectTab(link: Link) {
    val allTabs: List<EditorGroupTabInfo> = this.tabs.tabs

    for (i in allTabs.indices) {
      val tab = allTabs[i]
      if (tab !is MyEditorGroupTabInfo) continue

      val tabLink = tab.link
      if (tabLink != link) continue

      thisLogger().debug("selectTab selecting $link")

      this.tabs.mySelectedInfo = tab
      this.tabs.repaint()
      this.currentIndex = i
      break
    }
  }

  /** Assign the min weight for a tab. */
  override fun getWeight(): Double = Double.MIN_VALUE

  /** Main refresh action. */
  fun refreshPane(refresh: Boolean, newGroup: EditorGroup?) {
    // First we create our RefreshRequest
    when {
      // first call, no refresh request
      !refresh && newGroup == null -> atomicRefreshRequest.compareAndSet(null, RefreshRequest(refresh = false, requestedGroup = newGroup))
      else                         -> atomicRefreshRequest.set(RefreshRequest(refresh = refresh, requestedGroup = newGroup))
    }

    startRefreshing(interrupt = refresh || newGroup != null)
  }

  private fun focusGained() {
    refreshPane(refresh = false, newGroup = null)
    groupManager.stopSwitching()
  }

  fun refreshOnSelectionChanged(refresh: Boolean, switchingGroup: EditorGroup?, scrollOffset: Int) {
    thisLogger().debug("refreshOnSelectionChanged")
    this.myScrollOffset = scrollOffset

    if (switchingGroup === displayedGroup) tabs.scroll(myScrollOffset)

    refreshPane(refresh, switchingGroup)
    groupManager.stopSwitching()
  }

  /** Init refresh on the selected group. */
  private fun startRefreshing(interrupt: Boolean) {
    thisLogger().debug("> startRefreshing interrupt=$interrupt", Exception("just for logging"))

    try {
      // Add a semaphore lock to prevent multiple refreshes
      this.interrupt = true

      // Execute the refresh in a separate thread
      myTaskExecutor.submit {
        if (this.disposed) return@submit

        // Only refresh the selected group
        val selected = isSelected()
        thisLogger().debug("> startRefreshing:taskExecutor selected=$selected for ${file.name}")

        if (selected) executeRefresh()
      }
    } catch (e: RejectedExecutionException) {
      thisLogger().debug(e)
    }
  }

  /** Execute refresh. */
  private fun executeRefresh() {
    val start = System.currentTimeMillis()
    if (this.disposed) return

    val currentDisplayedGroup = this.displayedGroup

    // Do not execute on EDT
    if (SwingUtilities.isEventDispatchThread()) thisLogger().error("do not execute it on EDT")

    try {
      // A reference to hold the editor group in question
      val editorGroupRef = Ref<EditorGroup>()
      // Get the links of the current group
      val displayedLinks = when {
        currentDisplayedGroup != null -> currentDisplayedGroup.getLinks(project)
        else                          -> listOf<Link>()
      }

      val isStub = when {
        currentDisplayedGroup != null -> currentDisplayedGroup.isStub
        else                          -> true
      }

      // Create a refresh request and populate the editor group reference
      val refreshRequest = createRefreshRequest(editorGroupRef) ?: return
      val editorGroup = editorGroupRef.get()

      thisLogger().debug(
        ">executeRefresh before if: brokenScroll =$brokenScroll, request =$refreshRequest, group =$editorGroup, displayedGroup =$currentDisplayedGroup, toBeRendered =$groupToBeRendered"
      )

      // If the scroll is broken, or if refresh is asked, or if the editor group changed, or if the current editor groups is different visually than the displayedGroup
      var skipRefresh = !brokenScroll && !refreshRequest.refresh && (
        editorGroup === this.groupToBeRendered || editorGroup.equalsVisually(
          project = project,
          group = currentDisplayedGroup,
          links = displayedLinks,
          stub = isStub
        )
        )

      // If in the meantime we said to not show panel, stop refreshing
      val updateVisibility = hideGlobally != !EditorGroupsSettings.instance.isShowPanel
      if (updateVisibility) skipRefresh = false

      // If skipping refresh, return early
      if (skipRefresh) {
        if (fileEditor !is TextEditorImpl) {
          // need for UI forms - when switching to open editors , focus listener does not do that
          groupManager.stopSwitching()
        } else {
          // switched by bookmark shortcut -> need to select the right tab
          val editor: Editor = fileEditor.getEditor()
          val line = editor.caretModel.currentCaret.logicalPosition.line

          selectTab(
            VirtualFileLink(
              file = file,
              icon = null,
              line = line,
              project = project
            )
          )
        }

        thisLogger().debug("no change, skipping _refresh, toBeRendered=$groupToBeRendered. Took ${System.currentTimeMillis() - start}ms ")
        return
      }

      this.groupToBeRendered = editorGroup
      if (refreshRequest.refresh) {
        // this will have edge cases
        this.myScrollOffset = tabs.scrollOffset
      }

      render()

      // Clear the atomic refresh request once done
      atomicRefreshRequest.compareAndSet(refreshRequest, null)
      thisLogger().debug("<executeRefresh in ${System.currentTimeMillis() - start}ms ${file.name}")
    } catch (e: Throwable) {
      thisLogger().error(file.name, e)
    }
  }

  /** Renders the editor group panel. */
  private fun render() {
    thisLogger().debug(">invokeLater render")

    SwingUtilities.invokeLater {
      if (disposed) return@invokeLater
      val renderingGroup = this.groupToBeRendered

      // tabs do not like being updated while not visible first - it really messes up scrolling
      if (!this.isVisible && renderingGroup != null && updateVisibility(renderingGroup)) {
        SwingUtilities.invokeLater {
          try {
            refreshOnEDT(paintNow = true)
          } catch (e: Exception) {
            thisLogger().error(file.name, e)
          }
        }

        return@invokeLater
      }

      try {
        refreshOnEDT(paintNow = true)
      } catch (e: Exception) {
        thisLogger().error(file.name, e)
      }
    }
  }

  /** Create a refresh request in a read action. */
  private fun createRefreshRequest(editorGroupRef: Ref<EditorGroup>): RefreshRequest? {
    var refreshRequest: RefreshRequest? = null
    var success = false

    while (!success) {
      // LOCK
      this.interrupt = false

      // Get the current refresh request and reset the atomic reference
      val tempRefreshRequest = atomicRefreshRequest.getAndSet(null)
      if (tempRefreshRequest != null) refreshRequest = tempRefreshRequest

      // If no atomic refresh request, abort
      if (refreshRequest == null) {
        thisLogger().debug("initRefreshRequest - nothing to refresh ${fileEditor.name}")
        return null
      }

      // If file editor is disposed, abort
      if (!fileEditor.isValid) {
        thisLogger().debug("fileEditor disposed")
        return null
      }

      thisLogger().debug("initRefreshRequest - $refreshRequest")

      val lastGroup = getLastGroup()
      val requestedGroup = refreshRequest.requestedGroup
      val refresh = refreshRequest.refresh

      // Try to retrieve the group in a non-blocking read action, with retries
      try {
        val editorGroup = ReadAction.nonBlocking<EditorGroup?> {
          try {
            return@nonBlocking groupManager.getGroup(
              project = project,
              fileEditor = fileEditor,
              displayedGroup = lastGroup,
              requestedGroup = requestedGroup,
              currentFile = file,
              refresh = refresh,
              stub = !EditorGroupsSettings.instance.isShowPanel
            )
          } catch (e: ProcessCanceledException) {
            throw e
          } catch (e: IndexNotReady) {
            thisLogger().debug("initRefreshRequest - IndexNotReady $e", e)
            throw ProcessCanceledException(e)
          }
        }
          .expireWith(fileEditor)
          .submit(PooledThreadExecutor.INSTANCE)
          .get()

        editorGroupRef.set(editorGroup)
      } catch (e: ExecutionException) {
        val cause = e.cause

        // ok try again
        if (cause is ProcessCanceledException) {
          waitForSmartMode()
        } else if (cause is IndexNotReady) {
          waitForSmartMode()
        } else {
          throw RuntimeException(e)
        }
      } catch (e: InterruptedException) {
        thisLogger().error(e)
      }

      success = editorGroupRef.get() != null
    }

    return refreshRequest
  }

  /** Wait for smart mode to start during indexing. */
  fun waitForSmartMode() {
    thisLogger().debug("waiting on smart mode")

    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed || application.isDispatchThread) {
      throw AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode")
    }

    while (dumbService.isDumb && !project.isDisposed) {
      LockSupport.parkNanos(50_000_000)
      ProgressManager.checkCanceled()

      if (interrupt) {
        interrupt = false
        return
      }
    }
  }

  /** Return the last selected group. */
  private fun getLastGroup(): EditorGroup {
    var lastGroup = when (groupToBeRendered) {
      null -> displayedGroup
      else -> groupToBeRendered
    }

    return lastGroup ?: EditorGroup.EMPTY
  }

  /** Refresh the panel on the EDT. */
  private fun refreshOnEDT(paintNow: Boolean) {
    thisLogger().debug("doRender paintNow=$paintNow")
    if (this.disposed) return

    val renderingGroup = this.groupToBeRendered
    if (renderingGroup == null) {
      thisLogger().debug("skipping refreshOnEDT toBeRendered=$renderingGroup file=${file.name}")
      return
    }

    // In case the panel is not selected (e.g. in a split view), the scroll might break
    this.brokenScroll = !isSelected()
    if (this.brokenScroll) thisLogger().debug("rendering editor that is not selected, scrolling might break: ${file.name}")

    // Update the displayed group and reset the groupToBeRendered
    this.displayedGroup = renderingGroup
    this.groupToBeRendered = null

    val start = System.currentTimeMillis()
    rerenderTabs()

    // Add the displayed group to the file editor (for titles)
    fileEditor.putUserData<EditorGroup?>(EDITOR_GROUP, displayedGroup)

    // Add the displayed group to the file (for project view colors)
    file.putUserData<EditorGroup?>(EDITOR_GROUP, displayedGroup)

    // Update the the file editor manager, the toolbar, and stop the switching
    fileEditorManager.updateFilePresentation(file)
    toolbar.updateActionsAsync()
    groupManager.stopSwitching()

    thisLogger().debug("<refreshOnEDT ${System.currentTimeMillis() - start}ms ${fileEditor.name}, displayedGroup=$displayedGroup")
  }

  /** Hide the panel according to different conditions. */
  private fun updateVisibility(groupToRender: EditorGroup): Boolean {
    var visible: Boolean
    val editorGroupsSettingsState = EditorGroupsSettings.instance
    val dontShowPanel = !editorGroupsSettingsState.isShowPanel

    this.hideGlobally = dontShowPanel

    when {
      dontShowPanel                                                  -> visible = false

      // hide empty groups
      editorGroupsSettingsState.isHideEmpty && !groupToRender.isStub -> {
        val isEmpty = groupToRender === EditorGroup.EMPTY || groupToRender is AutoGroup && groupToRender.isEmpty
        visible = !isEmpty
      }

      else                                                           -> visible = true
    }

    thisLogger().debug("updateVisibility=$visible")

    this.isVisible = visible
    return visible
  }

  /** Refresh once indexing is done. */
  fun onIndexingDone(ownerPath: String, group: EditorGroupIndexValue) {
    // If we are in the middle of a refresh, do nothing
    if (atomicRefreshRequest.get() != null) return

    // If there is no displayed group, means that the init is not done yet
    if (displayedGroup == null) return

    // If the provided group is the same as the displayed group, no need to do nothing
    if (displayedGroup == group) return

    // If the current displayed group doesn't include the provided path, no need to refresh
    if (!displayedGroup!!.isOwner(ownerPath)) return

    thisLogger().debug("onIndexingDone ownerPath = [$ownerPath], group = [$group]")
    // concurrency is a bitch, do not alter data
    refreshPane(refresh = false, newGroup = null)
  }

  /** Return the displayed group. */
  fun getDisplayedGroupOrEmpty(): EditorGroup = displayedGroup ?: EditorGroup.EMPTY

  /** Sets bg and fg color of the selected tab, according to the settings. */
  private fun customizeSelectedColor(tab: MyEditorGroupTabInfo) {
    // custom group colors
    val bgColor = displayedGroup!!.bgColor
    val fgColor = displayedGroup!!.fgColor

    if (displayedGroup !is EditorGroups) return

    if (bgColor != null) tab.tabColor = bgColor
    if (fgColor != null) tab.setDefaultForeground(fgColor)
    // we can also decide to give a style
    tab.setDefaultStyle(Font.ITALIC)
  }

  /**
   * Checks whether the panel's current file editor is part of the editor manager selected files. This is useful when we have splitters or
   * other windows to detect which group panel is selected.
   */
  private fun isSelected(): Boolean = fileEditorManager.selectedEditors.any { it == this.fileEditor }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.VIRTUAL_FILE] = run {
      val targetInfo = tabs.getTargetInfo()
      if (targetInfo is MyEditorGroupTabInfo) {
        val path = targetInfo.link
        return@run path.virtualFile
      }
      null
    }

    sink[BOOKMARK_GROUP] = run {
      val targetInfo = tabs.getTargetInfo()
      if (targetInfo is CustomGroupTabInfo) {
        val group = targetInfo.editorGroup
        if (group is BookmarksGroup) return@run group
      }
      null
    }
  }

  /** Represents a tab info in the editor group panel. */
  class MyEditorGroupTabInfo(val link: Link, var name: String) : EditorGroupTabInfo(JLabel("")) {
    @JvmField
    var selectable: Boolean = true

    init {
      // Adds the link if needed
      link.line?.let { name += ":${it + 1}" }

      text = name // NON-NLS
      tooltipText = link.path
      // Placeholder icon
      icon = AllIcons.FileTypes.Any_type

      // Disable the tab if the file does not exist
      if (!link.exists()) isEnabled = false

      // Custom Names (for bookmarks, regexps, etc)
      val customName = link.customName ?: ""
      if (!customName.isBlank()) {
        text = customName
        tooltipText = "$name - ${link.path}" // NON-NLS
      }

      // Fetch the actual icon off the UI thread
      ApplicationManager.getApplication().runWriteAction {
        val icon = link.fileIcon
        SwingUtilities.invokeLater { this.icon = icon }
      }
    }
  }

  /** Data holder for a refresh request. */
  internal data class RefreshRequest(val refresh: Boolean, val requestedGroup: EditorGroup?) {
    override fun toString() = "RefreshRequest{_refresh=$refresh, requestedGroup=$requestedGroup}"
  }

  /**
   * Represents information about a custom group for the custom group switcher
   *
   * @constructor Creates a new instance of `CustomGroupTabInfo` with the specified `EditorGroup`.
   * @property editorGroup The editor group associated with this `CustomGroupTabInfo`.
   */
  internal inner class CustomGroupTabInfo(var editorGroup: EditorGroup) : EditorGroupTabInfo(JLabel("")) {
    init {
      val title = editorGroup.tabTitle(this@EditorGroupPanel.project) // NON-NLS
      text = "[$title]"

      tooltipText = editorGroup.getTabGroupTooltipText(this@EditorGroupPanel.project) // NON-NLS
      icon = editorGroup.icon()
    }
  }

  /** Remove favorites on right click. */
  internal inner class EditorTabMouseListener(val tabs: EditorGroupsTabsContainer) : MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) {
      // if right click a tab (or shift-click)
      if (!UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) return

      // Get the tab info
      val info = tabs.findInfo(e) ?: return

      IdeEventQueue.getInstance().blockNextEvents(e)
      tabs.setTabInfo(info)

      try {
        // Remove from current favorites
        val action = ActionManager.getInstance().getAction(RemoveFromCurrentBookmarksAction.ID)
        val e = AnActionEvent.createEvent(
          DataManager.getInstance().getDataContext(tabs),
          Presentation(),
          TAB_PLACE,
          ActionUiKind.NONE,
          e,
        )

        ActionWrapperUtil.actionPerformed(e, action, action)
      } finally {
        tabs.setTabInfo(null)
      }
    }
  }

  /** Upon selecting a different tab of the group. */
  internal inner class TabSelectionChangeHandler(val panel: EditorGroupPanel) : EditorGroupsTabsBase.SelectionChangeHandler {
    override fun execute(info: EditorGroupTabInfo, requestFocus: Boolean, doChangeSelection: ActiveRunnable): ActionCallback {
      // TODO this causes the tab to not proceed with select
      // doChangeSelection.run()

      // First check the mouse modifiers
      val trueCurrentEvent = IdeEventQueue.getInstance().trueCurrentEvent
      val modifiers = when (trueCurrentEvent) {
        is MouseEvent  -> trueCurrentEvent.getModifiersEx()
        is ActionEvent -> trueCurrentEvent.getModifiers()
        else           -> null
      }
      // If no modifiers (e.g. no CTRL/SHIFT...) do nothing
      if (modifiers == null) return ActionCallback.DONE

      // If selecting a custom group, load the custom group
      if (info is CustomGroupTabInfo) {
        panel.refreshPane(refresh = false, newGroup = info.editorGroup)
        return ActionCallback.DONE
      }

      val editorGroupTabInfo = info as MyEditorGroupTabInfo
      val fileByPath = editorGroupTabInfo.link.virtualFile
      // If the file is no longer available, disable the panel
      if (fileByPath == null) {
        panel.setEnabled(false)
        return ActionCallback.DONE
      }

      val ctrl = BitUtil.isSet(modifiers, InputEvent.CTRL_DOWN_MASK)
      val alt = BitUtil.isSet(modifiers, InputEvent.ALT_DOWN_MASK)
      val shift = BitUtil.isSet(modifiers, InputEvent.SHIFT_DOWN_MASK)

      // Finally, open the file, taking into account the modifiers
      panel.openFile(
        link = editorGroupTabInfo.link,
        newTab = ctrl,
        newWindow = shift,
        split = Splitters.from(alt, shift)
      )
      return ActionCallback.DONE
    }
  }

  companion object {
    const val NOT_INITIALIZED: Int = -10_000
    const val TOOLBAR_PLACE: String = "krasa.editorGroups.EditorGroupPanel"
    const val TAB_PLACE: String = "EditorGroupsTabPopup"
    const val COMPACT_TAB_HEIGHT: Int = 30
    val BOOKMARK_GROUP: DataKey<BookmarksGroup> = DataKey.create<BookmarksGroup>("krasa.BookmarksGroup")
    val EDITOR_PANEL: Key<EditorGroupPanel?> = Key.create<EditorGroupPanel?>("EDITOR_GROUPS_PANEL")
    val EDITOR_GROUP: Key<EditorGroup?> = Key.create<EditorGroup?>("EDITOR_GROUP")
  }
}
