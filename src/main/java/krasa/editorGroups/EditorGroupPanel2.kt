package krasa.editorGroups

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.ActiveRunnable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.Weighted
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import krasa.editorGroups.EditorGroupManager.Companion.getInstance
import krasa.editorGroups.EditorGroupPanel2.MyGroupTabInfo
import krasa.editorGroups.EditorGroupPanel2.MyTabInfo
import krasa.editorGroups.EditorGroupsSettingsState.Companion.state
import krasa.editorGroups.Splitters.Companion.from
import krasa.editorGroups.actions.PopupMenu.popupInvoked
import krasa.editorGroups.language.EditorGroupsLanguage.isEditorGroupsLanguage
import krasa.editorGroups.model.AutoGroup
import krasa.editorGroups.model.BookmarkGroup
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.EditorGroupIndexValue
import krasa.editorGroups.model.EditorGroups
import krasa.editorGroups.model.FavoritesGroup
import krasa.editorGroups.model.GroupsHolder
import krasa.editorGroups.model.HidePanelGroup
import krasa.editorGroups.model.Link
import krasa.editorGroups.model.Link.Companion.fromFile
import krasa.editorGroups.model.PathLink
import krasa.editorGroups.model.VirtualFileLink
import krasa.editorGroups.support.FileResolver.Companion.excluded
import krasa.editorGroups.support.Utils
import krasa.editorGroups.tabs2.KrTabInfo
import krasa.editorGroups.tabs2.KrTabs
import krasa.editorGroups.tabs2.my.KrJBEditorTabs
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.lang.AssertionError
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EditorGroupPanel2(fileEditor: FileEditor, project: Project, switchRequest: SwitchRequest?, file: VirtualFile) :
  JBPanel<EditorGroupPanel2>(BorderLayout()), Weighted, Disposable {
  private val myTaskExecutor: ExecutorService

  private val fileEditor: FileEditor
  private val project: Project
  private val file: VirtualFile

  @Volatile
  private var myScrollOffset: Int
  private var currentIndex = NOT_INITIALIZED

  @Volatile
  private var displayedGroup: EditorGroup? = null

  @Volatile
  private var toBeRendered: EditorGroup?
  private val fileFromTextEditor: VirtualFile?
  private val tabs: KrJBEditorTabs
  private val fileEditorManager: FileEditorManager
  var groupManager: EditorGroupManager
  private var toolbar: ActionToolbar? = null
  private var disposed = false

  @Volatile
  private var brokenScroll = false
  private val uniqueNameBuilder: UniqueTabNameBuilder
  private val line: Int?
  private var hideGlobally = false
  private val dumbService: DumbService

  fun postConstruct() {
    val editorGroupsSettingsState = state()

    var editorGroup = toBeRendered

    // minimize flicker for the price of latency
    val preferLatencyOverFlicker = editorGroupsSettingsState.isInitializeSynchronously
    if (editorGroup == null && preferLatencyOverFlicker && !DumbService.isDumb(project)) {
      val start = System.currentTimeMillis()
      try {
        editorGroup = groupManager.getStubGroup(project, fileEditor, EditorGroup.EMPTY, editorGroup, file)
        toBeRendered = editorGroup
      } catch (e: ProcessCanceledException) {
        LOG.debug(e)
      } catch (e: IndexNotReady) {
        LOG.debug(e)
      } catch (e: Throwable) {
        LOG.error(e)
      }
      val delta = System.currentTimeMillis() - start
      if (delta > 200) {
        LOG.warn("lag on editor opening - #getGroup took " + delta + " ms for " + file)
      }
    }

    if (editorGroup == null && !preferLatencyOverFlicker) {
      try {
        val start = System.currentTimeMillis()
        editorGroup = groupManager.getStubGroup(project, fileEditor, EditorGroup.EMPTY, editorGroup, file)
        toBeRendered = editorGroup
        val delta = System.currentTimeMillis() - start
        if (LOG.isDebugEnabled()) LOG.debug("#getGroup:stub - on editor opening took " + delta + " ms for " + file + ", group=" + editorGroup)
      } catch (indexNotReady: ProcessCanceledException) {
        LOG.warn("Getting stub group failed" + indexNotReady)
      } catch (indexNotReady: IndexNotReady) {
        LOG.warn("Getting stub group failed" + indexNotReady)
      }
    }

    if (editorGroup == null) {
      LOG.debug("editorGroup == null > setVisible=" + false)
      setVisible(false)
      _refresh(false, null)
    } else {
      var visible: Boolean
      visible = updateVisibility(editorGroup)

      val parent = this.getParent()
      if (parent != null) {  // NPE for Code With Me
        getLayout().layoutContainer(parent) //  forgot what this does :(
      }
      _render2(false)

      if (visible && editorGroup.isStub) {
        LOG.debug("#postConstruct: stub - calling #_refresh")
        _refresh(false, null)
      }
    }
  }

  private fun renderLater() {
    SwingUtilities.invokeLater(Runnable {
      try {
        _render2(true)
      } catch (e: Exception) {
        displayedGroup = EditorGroup.EMPTY
        LOG.error(e)
      }
    })
  }

  private fun getPopupHandler(): PopupHandler {
    return object : PopupHandler() {
      override fun invokePopup(comp: Component?, x: Int, y: Int) {
        popupInvoked(comp, x, y)
      }
    }
  }

  private fun addButtons() {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(ActionManager.getInstance().getAction("krasa.editorGroups.Refresh"))
    actionGroup.add(ActionManager.getInstance().getAction("krasa.editorGroups.SwitchGroup"))

    toolbar = ActionManager.getInstance().createActionToolbar("krasa.editorGroups.EditorGroupPanel2", actionGroup, true)
    toolbar!!.setTargetComponent(this)
    val component: JComponent = toolbar!!.getComponent()
    component.addMouseListener(getPopupHandler())
    component.setBorder(JBUI.Borders.empty())
    add(component, BorderLayout.WEST)
  }

  private fun reloadTabs(paintNow: Boolean) {
    var visible: Boolean
    try {
      tabs.bulkUpdate = true

      tabs.removeAllTabs()
      currentIndex = NOT_INITIALIZED

      val links: List<Link> = displayedGroup!!.getLinks(project)
      updateVisibility(displayedGroup!!)

      val path_name: MutableMap<Link, String> = uniqueNameBuilder.getNamesByPath(links, file, project)
      createTabs(links, path_name)

      addCurrentFileTab(path_name)

      if (displayedGroup is GroupsHolder) {
        createGroupLinks((displayedGroup as GroupsHolder).groups)
      }
      if (displayedGroup!!.isStub) {
        LOG.debug("#reloadTabs: stub - Adding Loading...")
        val tab = MyTabInfo(PathLink("Loading...", project), "Loading...")
        tab.selectable = false
        tabs.addTabSilently(tab, -1)
      }
    } finally {
      tabs.bulkUpdate = false

      tabs.doLayout()
      tabs.scroll(myScrollOffset)
    }
  }

  private fun createTabs(links: List<Link>, path_name: MutableMap<Link, String>) {
    var start = 0
    var end = links.size
    val tabSizeLimitInt = state().tabSizeLimitInt

    if (links.size > tabSizeLimitInt) {
      var currentFilePosition = -1

      for (i in links.indices) {
        val link = links.get(i)
        val virtualFile = link.virtualFile
        if (virtualFile != null && virtualFile == fileFromTextEditor && (line == null || link.line == line)) {
          currentFilePosition = i
          break
        }
      }
      if (currentFilePosition > -1) {
        start = max(0, (currentFilePosition - tabSizeLimitInt / 2))
        end = min(links.size, (start + tabSizeLimitInt))
      }
      LOG.debug("Too many tabs, skipping: " + (links.size - tabSizeLimitInt))
    }

    var j = 0
    for (i1 in start until end) {
      val link = links.get(i1)

      val tab = MyTabInfo(link, path_name.get(link)!!)

      tabs.addTabSilently(tab, -1)
      if (link.line == line && link.fileEquals(fileFromTextEditor!!)) {
        tabs.mySelectedInfo = tab
        customizeSelectedColor(tab)
        currentIndex = j
      }
      j++
    }
    if (currentIndex == NOT_INITIALIZED) {
      selectTabFallback()
    }
  }

  private fun addCurrentFileTab(path_name: MutableMap<Link, String>) {
    if (currentIndex < 0 && (isEditorGroupsLanguage(file))) {
      val link = fromFile(file, project)
      val info = MyTabInfo(link, path_name.get(link)!!)
      customizeSelectedColor(info)
      currentIndex = 0
      tabs.addTabSilently(info, 0)
      tabs.mySelectedInfo = info
    } else if (currentIndex < 0 && displayedGroup !== EditorGroup.EMPTY && displayedGroup !is EditorGroups && displayedGroup !is BookmarkGroup && displayedGroup !is HidePanelGroup
    ) {
      if (!displayedGroup!!.isStub && !excluded(File(file.getPath()), state().isExcludeEditorGroupsFiles)) {
        val message =
          "current file is not contained in group. file=" + file + ", group=" + displayedGroup + ", links=" + displayedGroup!!.getLinks(
            project
          )
        if (ApplicationManager.getApplication().isInternal()) {
          LOG.error(message)
        } else {
          LOG.warn(message)
        }
      } else if (!displayedGroup!!.isStub) {
        LOG.debug("current file is excluded from the group " + file + " " + displayedGroup + " " + displayedGroup!!.getLinks(project))
      }
    }
  }

  private fun createGroupLinks(groups: Collection<EditorGroup>) {
    for (editorGroup in groups) {
      tabs.addTabSilently(MyGroupTabInfo(editorGroup), -1)
    }
  }

  class MyTabInfo(var link: Link, name: String) : KrTabInfo(JLabel("")) {

    @JvmField
    var selectable: Boolean = true

    init {
      var name = name
      this.link = link
      val line = link.line
      if (line != null) {
        name += ":" + line
      }
      setText(name)
      setTooltipText(link.path)
      setIcon(AllIcons.FileTypes.Any_type) // Placeholder icon
      if (!link.exists()) {
        setEnabled(false)
      }

      // Fetch the actual icon off the UI thread
      ApplicationManager.getApplication().runWriteAction(Runnable {
        val icon = link.fileIcon
        SwingUtilities.invokeLater(Runnable { setIcon(icon) })
      })
    }

  }

  internal inner class MyGroupTabInfo(editorGroup: EditorGroup) : KrTabInfo(JLabel("")) {
    var editorGroup: EditorGroup?

    init {
      this.editorGroup = editorGroup
      val title = editorGroup.tabTitle(this@EditorGroupPanel2.project)
      setText("[" + title + "]")
      setToolTipText(editorGroup.getTabGroupTooltipText(this@EditorGroupPanel2.project))
      setIcon(editorGroup.icon())
    }
  }

  fun previous(newTab: Boolean, newWindow: Boolean, split: Splitters): Boolean {
    if (currentIndex == NOT_INITIALIZED) { // group was not refreshed
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - currentIndex == -1")
      return false
    }
    if (displayedGroup!!.isInvalid) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - displayedGroup.isInvalid")
      return false
    }
    if (!isVisible()) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - !isVisible()")
      return false
    }

    var iterations = 0
    val tabs: List<KrTabInfo> = this.tabs.getTabs()
    var link: Link? = null

    while (link == null && iterations < tabs.size) {
      iterations++

      var index = currentIndex - iterations

      if (!state().isContinuousScrolling && currentIndex - iterations < 0) {
        return newTab
      }

      if (index < 0) {
        index = tabs.size - abs(index)
      }
      link = getLink(tabs, index)
      if (LOG.isDebugEnabled()) {
        LOG.debug("previous: index=" + index + ", link=" + link)
      }
    }

    return openFile(link, newTab, newWindow, split)
  }

  private fun getLink(tabs: List<KrTabInfo>, index: Int): Link? {
    val tabInfo = tabs.get(index)
    if (tabInfo is MyTabInfo) {
      return tabInfo.link
    }
    return null
  }

  fun next(newTab: Boolean, newWindow: Boolean, split: Splitters): Boolean {
    if (currentIndex == NOT_INITIALIZED) { // group was not refreshed
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - currentIndex == -1")
      return false
    }
    if (displayedGroup!!.isInvalid) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - displayedGroup.isInvalid")
      return false
    }
    if (!isVisible()) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - !isVisible()")
      return false
    }
    var iterations = 0
    val tabs: List<KrTabInfo> = this.tabs.getTabs()
    var link: Link? = null

    while (link == null && iterations < tabs.size) {
      iterations++

      if (!state().isContinuousScrolling && currentIndex + iterations >= tabs.size) {
        return false
      }

      val index = (currentIndex + iterations) % tabs.size
      link = getLink(tabs, index)
      if (LOG.isDebugEnabled()) {
        LOG.debug("next: index=" + index + ", link=" + link)
      }
    }

    return openFile(link, newTab, newWindow, split)
  }

  fun openFile(link: Link?, newTab: Boolean, newWindow: Boolean, split: Splitters): Boolean {
    if (disposed) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - already disposed")
      return false
    }

    if (link == null) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - link is null")
      return false
    }

    if (link.virtualFile == null) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - file is null for " + link)
      return false
    }

    if (file == link.virtualFile && !newWindow && !split.isSplit && link.line == null) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - same file")
      return false
    }


    if (groupManager.isSwitching()) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - switching ")
      return false
    }
    if (toBeRendered != null) {
      if (LOG.isDebugEnabled()) LOG.debug("openFile fail - toBeRendered != null")
      return false
    }

    val result = groupManager.openGroupFile2(this, link.virtualFile!!, link.line, newWindow, newTab, split)


    if (result != null && result.isScrolledOnly) {
      selectTab(link)
    }
    return true
  }

  private fun selectTabFallback() {
    val tabs1: List<KrTabInfo> = tabs.getTabs()
    for (i in tabs1.indices) {
      val t = tabs1.get(i)
      if (t is MyTabInfo) {
        if (t.link.fileEquals(fileFromTextEditor!!)) {
          tabs.mySelectedInfo = t
          customizeSelectedColor(t)
          currentIndex = i
        }
      }
    }
  }

  private fun selectTab(link: Link) {
    val tabs: List<KrTabInfo> = this.tabs.getTabs()
    for (i in tabs.indices) {
      val tab = tabs.get(i)
      if (tab is MyTabInfo) {
        val link1 = tab.link
        if (link1.equals(link)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("selectTab selecting " + link)
          }
          this.tabs.mySelectedInfo = tab
          this.tabs.repaint()
          currentIndex = i
          break
        }
      }
    }
  }

  fun getTabs(): KrJBEditorTabs {
    return tabs
  }

  override fun getWeight(): Double {
    return Int.Companion.MIN_VALUE.toDouble()
  }

  fun getRoot(): JComponent {
    return this
  }

  internal class RefreshRequest(refresh: Boolean, requestedGroup: EditorGroup?) {
    val refresh: Boolean
    val requestedGroup: EditorGroup?

    init {
      this.refresh = refresh
      this.requestedGroup = requestedGroup
    }

    override fun toString(): String {
      return "RefreshRequest{" +
        "_refresh=" + refresh +
        ", requestedGroup=" + requestedGroup +
        '}'
    }
  }

  internal var atomicReference: AtomicReference<RefreshRequest?> = AtomicReference<RefreshRequest?>()

  /** call from any thread */
  fun _refresh(refresh: Boolean, newGroup: EditorGroup?) {
    if (!refresh && newGroup == null) { // unnecessary or initial _refresh
      atomicReference.compareAndSet(null, RefreshRequest(false, newGroup))
    } else {
      atomicReference.set(RefreshRequest(refresh, newGroup))
    }
    _refresh2(refresh || newGroup != null)
  }

  private fun focusGained() {
    _refresh(false, null)
    groupManager.stopSwitching()
  }

  fun refreshOnSelectionChanged(refresh: Boolean, switchingGroup: EditorGroup?, scrollOffset: Int) {
    if (LOG.isDebugEnabled()) LOG.debug("refreshOnSelectionChanged")
    myScrollOffset = scrollOffset
    if (switchingGroup === displayedGroup) {
      tabs.scroll(myScrollOffset)
    }
    _refresh(refresh, switchingGroup)
    groupManager.stopSwitching()
  }

  @Volatile
  var interrupt: Boolean = false

  init {
    if (LOG.isDebugEnabled()) LOG.debug(">new EditorGroupPanel2, " + "fileEditor = [" + fileEditor + "], project = [" + project.getName() + "], switchingRequest = [" + switchRequest + "], file = [" + file + "]")
    this.fileEditor = fileEditor
    Disposer.register(fileEditor, this)
    this.project = project
    this.file = file
    uniqueNameBuilder = UniqueTabNameBuilder(project)

    this.myScrollOffset = if (switchRequest == null) 0 else switchRequest.myScrollOffset
    toBeRendered = if (switchRequest == null) null else switchRequest.group
    line = if (switchRequest == null) null else switchRequest.line

    groupManager = getInstance(this.project)
    fileEditorManager = FileEditorManager.getInstance(project)
    fileEditor.putUserData<EditorGroupPanel2?>(EDITOR_PANEL, this)
    if (fileEditor is TextEditorImpl) {
      val editor: Editor = fileEditor.getEditor()
      if (editor is EditorImpl) {
        editor.addFocusListener(object : FocusChangeListener {
          override fun focusGained(editor: Editor) {
            this@EditorGroupPanel2.focusGained()
          }
        })
      }
    }
    fileFromTextEditor = Utils.getFileFromTextEditor(project, fileEditor)
    addButtons()

    //		groupsPanel.setLayout(new HorizontalLayout(0));
    tabs = KrJBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), fileEditor, file)
    val getter = Getter { CustomActionsSchema.getInstance().getCorrectedAction("EditorGroupsTabPopupMenu") as ActionGroup? }
    tabs.setDataProvider(object : DataProvider {
      override fun getData(dataId: String): Any? {
        if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) {
          val targetInfo = tabs.getTargetInfo()
          if (targetInfo is MyTabInfo) {
            val path = targetInfo.link
            return path.virtualFile
          }
        }
        if (FAVORITE_GROUP.`is`(dataId)) {
          val targetInfo = tabs.getTargetInfo()
          if (targetInfo is MyGroupTabInfo) {
            val group = targetInfo.editorGroup
            if (group is FavoritesGroup) {
              return group
            }
          }
        }
        return null
      }
    })
    tabs.addTabMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
          val info = tabs.findInfo(e)
          if (info != null) {
            IdeEventQueue.getInstance().blockNextEvents(e)
            tabs.setMyPopupInfo(info)
            try {
//              ActionManager.getInstance().getAction(RemoveFromCurrentFavoritesAction.ID).actionPerformed(AnActionEvent.createFromInputEvent(e, ActionPlaces.UNKNOWN, new Presentation(), DataManager.getInstance().getDataContext(tabs)));
            } finally {
              tabs.setMyPopupInfo(null)
            }
          }
        }
      }
    })
    // tabs.setPopupGroup(getter, "EditorGroupsTabPopup", false)
    tabs.setSelectionChangeHandler(object : KrTabs.SelectionChangeHandler {
      override fun execute(info: KrTabInfo?, requestFocus: Boolean, doChangeSelection: ActiveRunnable): ActionCallback {
        var modifiers: Int? = null
        val trueCurrentEvent = IdeEventQueue.getInstance().trueCurrentEvent
        if (trueCurrentEvent is MouseEvent) {
          modifiers = trueCurrentEvent.getModifiersEx()
        } else if (trueCurrentEvent is ActionEvent) {
          modifiers = trueCurrentEvent.getModifiers()
        }


        if (modifiers == null) {
          return ActionCallback.DONE
        }

        if (info is MyGroupTabInfo) {
          _refresh(false, info.editorGroup)
        } else {
          val myTabInfo = info as MyTabInfo
          val fileByPath = myTabInfo.link.virtualFile
          if (fileByPath == null) {
            setEnabled(false)
            return ActionCallback.DONE
          }

          val ctrl = BitUtil.isSet(modifiers, InputEvent.CTRL_DOWN_MASK)
          val alt = BitUtil.isSet(modifiers, InputEvent.ALT_DOWN_MASK)
          val shift = BitUtil.isSet(modifiers, InputEvent.SHIFT_DOWN_MASK)
          val button2 = BitUtil.isSet(modifiers, InputEvent.BUTTON2_DOWN_MASK)

          openFile(myTabInfo.link, ctrl, shift, from(alt, shift))
        }
        return ActionCallback.DONE
      }
    })

    val tabHeight = if (state().isCompactTabs) 26 else JBUI.CurrentTheme.TabbedPane.TAB_HEIGHT.get()
    setPreferredSize(Dimension(0, tabHeight))
    val component = tabs.getComponent()
    add(component, BorderLayout.CENTER)

    addMouseListener(getPopupHandler())
    tabs.addMouseListener(getPopupHandler())


    myTaskExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Krasa.editorGroups.EditorGroupPanel2-" + file.getName(), 1)
    dumbService = DumbService.getInstance(this.project)
  }

  private fun _refresh2(interrupt: Boolean) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("> _refresh2 interrupt=" + interrupt, Exception("just for logging"))
    }
    try {
      this.interrupt = true
      myTaskExecutor.submit(Runnable {
        if (disposed) return@Runnable
        val selected = isSelected()
        if (LOG.isDebugEnabled()) {
          LOG.debug("_refresh2 selected=" + selected + " for " + file.getName())
        }
        if (selected) {
          _refresh3()
        }
      })
    } catch (e: RejectedExecutionException) {
      LOG.debug(e)
    }
  }

  private fun _refresh3() {
    val start = System.currentTimeMillis()
    if (disposed) {
      return
    }
    if (SwingUtilities.isEventDispatchThread()) {
      LOG.error("do not execute it on EDT")
    }

    try {
      val editorGroupRef = Ref<EditorGroup>()

      val displayedLinks = if (displayedGroup != null) displayedGroup!!.getLinks(project) else emptyList()
      val stub = if (displayedGroup != null) displayedGroup!!.isStub else true

      val request = getGroupInReadActionWithRetries(editorGroupRef)
      if (request == null) return

      val group = editorGroupRef.get()

      if (LOG.isDebugEnabled()) {
        LOG.debug("_refresh3 before if: brokenScroll =" + brokenScroll + ", request =" + request + ", group =" + group + ", displayedGroup =" + displayedGroup + ", toBeRendered =" + toBeRendered)
      }
      var skipRefresh =
        !brokenScroll && !request.refresh && (group === toBeRendered || group.equalsVisually(project, displayedGroup, displayedLinks, stub))
      val updateVisibility = hideGlobally != !state().isShowPanel
      if (updateVisibility) {
        skipRefresh = false
      }
      if (skipRefresh) {
        if (fileEditor !is TextEditorImpl) {
          groupManager.stopSwitching() // need for UI forms - when switching to open editors , focus listener does not do that
        } else {
          // switched by bookmark shortcut -> need to select the right tab
          val editor: Editor = fileEditor.getEditor()
          val line = editor.getCaretModel().getCurrentCaret().getLogicalPosition().line
          selectTab(VirtualFileLink(file, null, line, project))
        }


        if (LOG.isDebugEnabled()) LOG.debug("no change, skipping _refresh, toBeRendered=" + toBeRendered + ". Took " + (System.currentTimeMillis() - start) + "ms ")
        return
      }
      toBeRendered = group
      if (request.refresh) {
        myScrollOffset = tabs.getMyScrollOffset() // this will have edge cases
      }


      _render()

      atomicReference.compareAndSet(request, null)
      if (LOG.isDebugEnabled()) LOG.debug("<_refresh3 in " + (System.currentTimeMillis() - start) + "ms " + file.getName())
    } catch (e: Throwable) {
      LOG.error(file.getName(), e)
    }
  }

  private fun _render() {
    LOG.debug("invokeLater _render")
    SwingUtilities.invokeLater(Runnable {
      if (disposed) {
        return@Runnable
      }
      val rendering = toBeRendered
      // tabs do not like being updated while not visible first - it really messes up scrolling
      if (!isVisible() && rendering != null && updateVisibility(rendering)) {
        SwingUtilities.invokeLater(Runnable {
          try {
            _render2(true)
          } catch (e: Exception) {
            LOG.error(file.getName(), e)
          }
        })
      } else {
        try {
          _render2(true)
        } catch (e: Exception) {
          LOG.error(file.getName(), e)
        }
      }
    })
  }

  private fun getGroupInReadActionWithRetries(editorGroupRef: Ref<EditorGroup>): RefreshRequest? {
    var request: RefreshRequest? = null
    var success = false
    while (!success) {
      this@EditorGroupPanel2.interrupt = false
      val tempRequest = atomicReference.getAndSet(null)
      if (tempRequest != null) {
        request = tempRequest
      }

      if (request == null) {
        if (LOG.isDebugEnabled()) LOG.debug("getGroupInReadActionWithRetries - nothing to _refresh " + fileEditor.getName())
        return null
      }
      if (LOG.isDebugEnabled()) LOG.debug("getGroupInReadActionWithRetries - " + request)

      //      ProgressIndicatorUtils.yieldToPendingWriteActions();
      val lastGroup = getLastGroup()
      if (Disposer.isDisposed(fileEditor)) {
        LOG.debug("fileEditor disposed")
        return null
      }
      val requestedGroup = request.requestedGroup
      val refresh = request.refresh
      try {
        val editorGroup = ReadAction.nonBlocking<EditorGroup?>(Callable {
          try {
            return@Callable groupManager.getGroup(project, fileEditor, lastGroup, requestedGroup, file, refresh, !state().isShowPanel)
          } catch (e: ProcessCanceledException) {
            if (LOG.isDebugEnabled()) LOG.debug("getGroupInReadActionWithRetries - " + e, e)
            throw e
          } catch (e: IndexNotReady) {
            if (LOG.isDebugEnabled()) LOG.debug("getGroupInReadActionWithRetries - " + e, e)
            throw ProcessCanceledException(e)
          }
        }).expireWith(fileEditor).submit(PooledThreadExecutor.INSTANCE).get()
        editorGroupRef.set(editorGroup)
      } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause is ProcessCanceledException) {
          waitForSmartMode()
          // ok try again
        } else if (cause is IndexNotReady) {
          waitForSmartMode()
        } else {
          throw RuntimeException(e)
        }
      } catch (e: InterruptedException) {
        LOG.error(e)
      }

      success = editorGroupRef.get() != null
    }
    return request
  }

  fun waitForSmartMode() {
    LOG.debug("waiting on smart mode")

    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed() || application.isDispatchThread()) {
      throw AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode")
    }

    while (dumbService.isDumb && !project.isDisposed()) {
      LockSupport.parkNanos(50000000)
      ProgressManager.checkCanceled()

      if (interrupt) {
        interrupt = false
        return
      }
    }
  }

  private fun needSmartMode(request: RefreshRequest?, lastGroup: EditorGroup): Boolean {
    var requestedGroup: EditorGroup? = null
    val refresh = false
    if (request != null) {
      requestedGroup = request.requestedGroup
    }

    return (requestedGroup != null && requestedGroup.exists() && requestedGroup.needSmartMode()) ||
      (requestedGroup == null && lastGroup.exists() && lastGroup.needSmartMode()) || requestedGroup == null
  }

  private fun getLastGroup(): EditorGroup {
    var lastGroup = if (toBeRendered == null) displayedGroup else toBeRendered
    lastGroup = if (lastGroup == null) EditorGroup.EMPTY else lastGroup
    return lastGroup
  }

  private fun _render2(paintNow: Boolean) {
    LOG.debug("_render2 paintNow=" + paintNow)
    if (disposed) {
      return
    }
    val rendering = toBeRendered
    if (rendering == null) {
      if (LOG.isDebugEnabled()) LOG.debug("skipping _render2 toBeRendered=" + rendering + " file=" + file.getName())
      return
    }


    brokenScroll = !isSelected()
    if (brokenScroll && LOG.isDebugEnabled()) {
      LOG.warn("rendering editor that is not selected, scrolling might break: " + file.getName())
    }

    displayedGroup = rendering
    toBeRendered = null

    val start = System.currentTimeMillis()

    reloadTabs(paintNow)

    fileEditor.putUserData<EditorGroup?>(EDITOR_GROUP, displayedGroup) // for titles
    file.putUserData<EditorGroup?>(EDITOR_GROUP, displayedGroup) // for project view colors
    fileEditorManager.updateFilePresentation(file)
    toolbar!!.updateActionsAsync()

    groupManager.stopSwitching()
    if (LOG.isDebugEnabled()) LOG.debug("<refreshOnEDT " + (System.currentTimeMillis() - start) + "ms " + fileEditor.getName() + ", displayedGroup=" + displayedGroup)
  }

  private fun updateVisibility(rendering: EditorGroup): Boolean {
    var visible: Boolean
    val editorGroupsSettingsState = state()
    hideGlobally = !editorGroupsSettingsState.isShowPanel
    if (!editorGroupsSettingsState.isShowPanel || rendering is HidePanelGroup) {
      visible = false
    } else if (editorGroupsSettingsState.isHideEmpty && !rendering.isStub) {
      val hide = rendering is AutoGroup && rendering.isEmpty || rendering === EditorGroup.EMPTY
      visible = !hide
    } else {
      visible = true
    }
    if (LOG.isDebugEnabled()) LOG.debug("updateVisibility=" + visible)
    setVisible(visible)
    return visible
  }

  override fun dispose() {
    disposed = true
    myTaskExecutor.shutdownNow()
  }

  fun onIndexingDone(ownerPath: String, group: EditorGroupIndexValue) {
    if (atomicReference.get() == null && displayedGroup != null && displayedGroup!!.isOwner(ownerPath) && displayedGroup != group) {
      if (LOG.isDebugEnabled()) LOG.debug("onIndexingDone " + "ownerPath = [" + ownerPath + "], group = [" + group + "]")
      // concurrency is a bitch, do not alter data
//			displayedGroup.invalid();                    0o
      _refresh(false, null)
    }
  }

  fun getDisplayedGroup(): EditorGroup {
    if (displayedGroup == null) {
      return EditorGroup.EMPTY
    }
    return displayedGroup!!
  }

  fun getToBeRendered(): EditorGroup? {
    return toBeRendered
  }

  fun getFile(): VirtualFile {
    return file
  }

  private fun customizeSelectedColor(tab: MyTabInfo) {
    val config = state()
    val bgColor = displayedGroup!!.bgColor
    if (bgColor != null) {
      tab.setTabColor(bgColor)
    } else if (config.isTabBgColorEnabled) {
      tab.setTabColor(config.tabBgColorAsColor)
    }

    val fgColor = displayedGroup!!.fgColor
    if (fgColor != null) {
      tab.setDefaultForeground(fgColor)
    } else if (config.isTabFgColorEnabled) {
      tab.setDefaultForeground(config.tabFgColorAsColor)
    }
  }

  private fun isSelected(): Boolean {
    var selected = false
    for (selectedEditor in fileEditorManager.getSelectedEditors()) {
      if (selectedEditor === fileEditor) {
        selected = true
        break
      }
    }
    return selected
  }

  companion object {
    val FAVORITE_GROUP: DataKey<FavoritesGroup?> = DataKey.create<FavoritesGroup?>("krasa.FavoritesGroup")
    private val LOG = Logger.getInstance(EditorGroupPanel2::class.java)
    val EDITOR_PANEL: Key<EditorGroupPanel2?> = Key.create<EditorGroupPanel2?>("EDITOR_GROUPS_PANEL")
    val EDITOR_GROUP: Key<EditorGroup?> = Key.create<EditorGroup?>("EDITOR_GROUP")
    val NOT_INITIALIZED: Int = -10000
  }
}
