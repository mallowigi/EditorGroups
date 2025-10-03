package krasa.editorGroups

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.UIUtil
import krasa.editorGroups.index.IndexCache
import krasa.editorGroups.index.IndexNotReady
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.*
import krasa.editorGroups.services.AutoGroupProvider
import krasa.editorGroups.services.ExternalGroupProvider
import krasa.editorGroups.services.PanelRefresher
import krasa.editorGroups.services.RegexGroupProvider
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.Notifications
import krasa.editorGroups.support.Splitters
import java.awt.Color
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

@Suppress(
  "LongLine",
  "detekt:CyclomaticComplexMethod",
  "detekt:ReturnCount",
  "detekt:ComplexCondition",
  "detekt:MaxLineLength",
  "detekt:NestedBlockDepth",
  "detekt:RethrowCaughtException",
  "HardCodedStringLiteral",
)
@Service(Service.Level.PROJECT)
class EditorGroupManager(private val project: Project) {
  private var cache: IndexCache = IndexCache.getInstance(project)
  private val config: EditorGroupsSettings = EditorGroupsSettings.instance
  private val panelRefresher: PanelRefresher = PanelRefresher.getInstance(project)
  private var warningShown = false
  private val externalGroupProvider: ExternalGroupProvider = ExternalGroupProvider.getInstance(project)
  private val autoGroupProvider: AutoGroupProvider = AutoGroupProvider.getInstance(project)
  private val regexGroupProvider: RegexGroupProvider = RegexGroupProvider.getInstance(project)
  private var initialEditorIndex: Key<*>? = null

  @Volatile
  var lastGroup: EditorGroup = EditorGroup.EMPTY

  @Volatile
  private var switchRequest: SwitchRequest? = null

  @Volatile
  var switching: Boolean = false

  val allIndexedGroups: List<EditorGroupIndexValue>
    // TODO cache it?
    get() {
      val start = System.currentTimeMillis()
      val allGroups = cache.allGroups

      allGroups.sortedWith(COMPARATOR)

      thisLogger().debug("<getAllGroups ${System.currentTimeMillis() - start}")

      return allGroups
    }

  fun getStubGroup(
    project: Project,
    fileEditor: FileEditor,
    displayedGroup: EditorGroup,
    requestedGroup: EditorGroup?,
    currentFile: VirtualFile
  ): EditorGroup {
    val stub = true
    val refresh = false

    thisLogger().debug(
      "<getStubGroup: fileEditor = [$fileEditor], displayedGroup = [$displayedGroup], requestedGroup = [$requestedGroup], force = [$refresh], stub = [$stub], project = [${project.name}]"
    )

    val start = System.currentTimeMillis()
    var result = EditorGroup.EMPTY

    try {
      val requestedOrDisplayedGroup = requestedGroup ?: displayedGroup
      val currentFilePath = currentFile.path

      // First try to take the requestedGroup or displayedGroup
      if (result.isInvalid) {
        cache.validate(requestedOrDisplayedGroup)

        if (requestedOrDisplayedGroup.isValid &&
          (
            requestedOrDisplayedGroup is AutoGroup ||
              requestedOrDisplayedGroup.containsLink(project, currentFile) ||
              requestedOrDisplayedGroup.isOwner(currentFilePath)
            )
        ) {
          result = requestedOrDisplayedGroup
        }
      }

      // Next, try to retrieve the owning group of the current file path
      if (result.isInvalid) {
        result = cache.getOwningOrSingleGroup(currentFilePath)
      }

      // Next try to get the last group
      if (result.isInvalid) {
        result = cache.getLastEditorGroup(
          currentFile = currentFile,
          currentFilePath = currentFilePath,
          includeAutoGroups = true,
          includeFavorites = true,
          stub = stub
        )
      }

      // If nothing is found, try to match by regex if the option is on
      if (result.isInvalid && config.state.isSelectRegexGroup) {
        result = RegexGroupProvider.getInstance(project).findFirstMatchingRegexGroup(currentFile)
      }

      // If nothing is found, try to get the same name group if the option is on
      if (result.isInvalid && config.state.isAutoSameName) {
        result = SameNameGroup.INSTANCE
      }

      // If nothing is found, try to get the folder group if the option is on
      if (result.isInvalid && config.state.isAutoFolders) {
        result = FolderGroup.INSTANCE
      }

      // If nothing is found, try to get the same name group if the option is on
      if (result.isInvalid && config.state.isAutoSameFeature) {
        result = SameFeatureGroup.INSTANCE
      }

      // If the group is empty or is indexing, try other groups
      if (isEmptyAutoGroup(project, result) || isIndexingAutoGroup(project, result)) {
        thisLogger().debug("refreshing result...")

        // _refresh
        when (result) {
          is FolderGroup    -> result = autoGroupProvider.getFolderGroup(currentFile)
          is BookmarksGroup -> result = externalGroupProvider.getBookmarkGroup(result.title)
        }
      }

      result.isStub = stub

      thisLogger().debug(
        "<getStubGroup ${System.currentTimeMillis() - start}ms, EDT=${SwingUtilities.isEventDispatchThread()}, file=${currentFile.name} title='${result.title} stub='${result.isStub}' $result"
      )

      cache.setLast(currentFilePath, result)
    } catch (e: IndexNotReadyException) {
      thisLogger().debug(e.toString())
      throw IndexNotReady(
        "<getStubGroup project = [${project.name}], fileEditor = [$fileEditor], displayedGroup = [$displayedGroup], requestedGroup = [$requestedGroup], force = [$refresh]",
        e
      )
    } catch (e: Throwable) {
      thisLogger().warn(e.toString())
      throw e
    }

    return result
  }

  fun getGroup(
    project: Project,
    fileEditor: FileEditor,
    displayedGroup: EditorGroup,
    requestedGroup: EditorGroup?,
    currentFile: VirtualFile,
    refresh: Boolean,
    stub: Boolean
  ): EditorGroup {
    thisLogger().debug(
      "<getGroup: fileEditor = [$fileEditor], displayedGroup = [$displayedGroup], requestedGroup = [$requestedGroup], force = [$refresh], stub = [$stub], project = [${project.name}]"
    )

    val start = System.currentTimeMillis()
    var result = EditorGroup.EMPTY

    try {
      val requestedOrDisplayedGroup = requestedGroup ?: displayedGroup
      val currentFilePath = currentFile.path
      val force = refresh && EditorGroupsSettings.instance.isForceSwitch

      // If force switch is on, force switching
      if (force && requestedOrDisplayedGroup !is BookmarksGroup) {
        // First try to get the owning group
        if (result.isInvalid) {
          result = cache.getOwningOrSingleGroup(currentFilePath)
        }

        // Otherwise try the last group
        if (result.isInvalid) {
          result = cache.getLastEditorGroup(currentFile, currentFilePath, includeAutoGroups = false, includeFavorites = true, stub = stub)
        }

        // If not found, try with regex
        if (result.isInvalid) {
          result = RegexGroupProvider.getInstance(project).findFirstMatchingRegexGroup(currentFile)
        }
      }

      // Then, try to get the requested or displayed group
      if (result.isInvalid) {
        cache.validate(requestedOrDisplayedGroup)

        if (requestedOrDisplayedGroup.isValid &&
          (
            requestedOrDisplayedGroup is AutoGroup ||
              requestedOrDisplayedGroup.containsLink(project, currentFile) ||
              requestedOrDisplayedGroup.isOwner(currentFilePath)
            )
        ) {
          result = requestedOrDisplayedGroup
        }
      }

      if (!force) {
        // If not found, try to get the owning group
        if (result.isInvalid) {
          result = cache.getOwningOrSingleGroup(currentFilePath)
        }

        // If not found, try to get the last group
        if (result.isInvalid) {
          result = cache.getLastEditorGroup(currentFile, currentFilePath, includeAutoGroups = true, includeFavorites = true, stub = stub)
        }
      }

      // Next, try to match by regex, same name or folder
      if (result.isInvalid) {
        if (config.state.isSelectRegexGroup) {
          result = RegexGroupProvider.getInstance(project).findFirstMatchingRegexGroup(currentFile)
        }

        if (result.isInvalid && config.state.isAutoSameName) {
          result = SameNameGroup.INSTANCE
        }

        if (result.isInvalid && config.state.isAutoFolders) {
          result = FolderGroup.INSTANCE
        }

        if (result.isInvalid && config.state.isAutoSameFeature) {
          result = SameFeatureGroup.INSTANCE
        }
      }

      // If force refresh or the found group is empty or indexing
      if (refresh || isEmptyAutoGroup(project, result) || isIndexingAutoGroup(project, result)) {
        thisLogger().debug("refreshing result")

        when {
          !stub && result === requestedOrDisplayedGroup && result is EditorGroupIndexValue -> cache.initGroup(result)

          !stub && result is SameNameGroup                                                 ->
            result =
              autoGroupProvider.getSameNameGroup(currentFile)

          !stub && result is SameFeatureGroup                                              ->
            result =
              autoGroupProvider.getSameFeatureGroup(currentFile)

          !stub && result is RegexGroup                                                    ->
            result =
              regexGroupProvider.getRegexGroup(result, project, currentFile)

          result is FolderGroup                                                            ->
            result =
              autoGroupProvider.getFolderGroup(currentFile)

          result is BookmarksGroup                                                         ->
            result =
              externalGroupProvider.getBookmarkGroup(result.title)
        }

        // Last resort, try multigroup
        if (!stub &&
          sameNameGroupIsEmpty(project, result, requestedOrDisplayedGroup) &&
          !(requestedOrDisplayedGroup is SameNameGroup && !requestedOrDisplayedGroup.isStub)
        ) {
          val multiGroup = cache.getMultiGroup(currentFile)
          when {
            multiGroup.isValid                                                                       -> result = multiGroup
            config.state.isAutoFolders && AutoGroup.SAME_FILE_NAME != cache.getLast(currentFilePath) ->
              result =
                autoGroupProvider.getFolderGroup(currentFile)
          }
        }
      }

      result.isStub = stub

      thisLogger().debug(
        "<getGroup ${System.currentTimeMillis() - start}ms, EDT=${SwingUtilities.isEventDispatchThread()}, file=${currentFile.name} title='${result.title} stub='${result.isStub}' $result"
      )

      cache.setLast(currentFilePath, result)
      lastGroup = result
    } catch (e: IndexNotReadyException) {
      thisLogger().debug(e.toString())

      throw IndexNotReady(
        "<getGroup project = [${project.name}], fileEditor = [$fileEditor], displayedGroup = [$displayedGroup], requestedGroup = [$requestedGroup], force = [$refresh]",
        e
      )
    } catch (e: Throwable) {
      throw e
    }

    return result
  }

  /**
   * Retrieves and returns a sorted list of editor groups associated with the given file.
   *
   * @param file The virtual file for which to find the associated editor groups.
   * @return A sorted list of editor groups for the specified file.
   */
  fun getGroups(file: VirtualFile): List<EditorGroup> = cache.findGroups(file).sortedWith(COMPARATOR)

  private fun isIndexingAutoGroup(project: Project, result: EditorGroup): Boolean = when {
    result is AutoGroup && !isDumb(project) -> result.hasIndexing()
    else                                    -> false
  }

  private fun isEmptyAutoGroup(project: Project, result: EditorGroup): Boolean = result is AutoGroup && result.size(project) == 0

  private fun sameNameGroupIsEmpty(project: Project, result: EditorGroup, requestedGroup: EditorGroup): Boolean = when {
    result is SameNameGroup && result.size(project) <= 1            -> true
    result === EditorGroup.EMPTY && requestedGroup is SameNameGroup -> true
    else                                                            -> false
  }

  /**
   * Starts the switching process based on the provided switch request.
   *
   * @param switchRequest contains the switch request holder.
   */
  fun startSwitching(switchRequest: SwitchRequest) {
    this.switchRequest = switchRequest
    switching = true

    thisLogger().debug(
      "switching switching = [$switching], group = [${switchRequest.group}], fileToOpen = [${switchRequest.fileToOpen}], myScrollOffset = [${switchRequest.myScrollOffset}]"
    )
  }

  /**
   * Stops the current switching operation.
   *
   * This method schedules a task on the AWT event queue using `SwingUtilities.invokeLater`. Once the focus has settled down, it sets the
   * `switching` flag to `false` and logs the action.
   */
  fun stopSwitching() {
    SwingUtilities.invokeLater {
      thisLogger().debug("enabling switching")
      this.switching = false
    }
  }

  /**
   * Retrieves and clears the current switch request if it matches the provided file.
   *
   * @param file The file that is being checked against the current switch request.
   * @return The matching switch request if the provided file matches the switching file, otherwise null.
   */
  fun getAndClearSwitchingRequest(file: VirtualFile): SwitchRequest? {
    val switchingFile = switchRequest?.fileToOpen

    // Indicates that we've done switching, clear the reqest
    if (file == switchingFile) {
      val switchingGroup = switchRequest
      clearSwitchingRequest()

      thisLogger().debug("<getSwitchingRequest $switchingGroup")
      return switchingGroup
    }

    thisLogger().debug("<getSwitchingRequest=null, file=[$file], switchingFile=$switchingFile")
    return null
  }

  /**
   * Retrieves the switching request of a provided file.
   *
   * @param file The virtual file to check against the switching request.
   * @return The [SwitchRequest] if the specified file matches; otherwise, null.
   */
  fun getSwitchingRequest(file: VirtualFile): SwitchRequest? {
    val switchingFile = switchRequest?.fileToOpen

    return if (file == switchingFile) switchRequest else null
  }

  /**
   * Checks if there is an active request to switch or if switching is currently in progress.
   *
   * @return true if there is a switch request or switching is in progress, false otherwise.
   */
  fun isSwitching(): Boolean {
    thisLogger().debug("isSwitching switchRequest=$switchRequest, switching=$switching")

    return switchRequest != null || switching
  }

  /** Resets the switching mechanism by clearing any current switching requests and stopping the switching process. */
  fun resetSwitching() {
    clearSwitchingRequest()
    stopSwitching()
  }

  @Suppress("unused")
  suspend fun initCache(): Unit = panelRefresher.initCache()

  /**
   * Retrieves the background color associated with the specified VirtualFile.
   *
   * @param file the VirtualFile for which to retrieve the color
   * @return the Color object representing the background color of the VirtualFile, or null if not found
   */
  @Suppress("unused")
  fun getBgColor(file: VirtualFile): Color? = cache.getEditorGroupForColor(file).bgColor

  /**
   * Retrieves the foreground color associated with the specified VirtualFile.
   *
   * @param file the VirtualFile for which to retrieve the color
   * @return the Color object representing the foreground color of the VirtualFile, or null if not found
   */
  @Suppress("unused")
  fun getFgColor(file: VirtualFile): Color? = cache.getEditorGroupForColor(file).fgColor

  /**
   * Opens a specified file in the editor, with various options for window and tab management.
   *
   * @param groupPanel The [EditorGroupPanel]
   * @param fileToOpen The file to be opened.
   * @param line The line number to navigate to within the file, or null to open without specific line focus.
   * @param newWindow Whether to open the file in a new window.
   * @param newTab Whether to open the file in a new tab.
   * @param split The split orientation for the editor if applicable.
   * @return A Result object indicating the outcome of the open operation, or null if the operation fails.
   */
  fun openGroupFile(
    groupPanel: EditorGroupPanel,
    fileToOpen: VirtualFile,
    line: Int?,
    newWindow: Boolean,
    newTab: Boolean,
    split: Splitters
  ): OpenFileResult? {
    val displayedGroup = groupPanel.getDisplayedGroupOrEmpty()
    val tabs = groupPanel.tabs

    val editorWindowHolder = UIUtil.getParentOfType(EditorWindowHolder::class.java, groupPanel)
    var currentWindow: EditorWindow? = null

    if (editorWindowHolder != null) {
      currentWindow = editorWindowHolder.editorWindow
    }

    return open(
      currentWindow = currentWindow,
      currentFile = groupPanel.file,
      fileToOpen = fileToOpen,
      line = line,
      group = displayedGroup,
      newWindow = newWindow,
      newTab = newTab,
      splitters = split,
      switchRequest = SwitchRequest(
        group = displayedGroup,
        fileToOpen = fileToOpen,
        myScrollOffset = tabs.scrollOffset,
        width = tabs.width,
        line = line
      )
    )
  }

  /**
   * Opens a specified virtual file in the editor.
   *
   * @param fileToOpen The virtual file to open, specified by its absolute path.
   * @param newWindow A boolean flag indicating if the file should be opened in a new window.
   * @param newTab A boolean flag indicating if the file should be opened in a new tab.
   * @param split The splitter configuration indicating how the window should be split.
   * @param group The editor group in which the file should be opened.
   * @param current The currently active virtual file, may be null.
   * @return A Result object indicating the success or failure of the operation.
   */
  fun openFile(
    fileToOpen: VirtualFile,
    newWindow: Boolean,
    newTab: Boolean,
    split: Splitters,
    group: EditorGroup,
    current: VirtualFile?
  ): OpenFileResult? = open(
    currentWindow = null,
    currentFile = current,
    fileToOpen = fileToOpen,
    line = null,
    group = group,
    newWindow = newWindow,
    newTab = newTab,
    splitters = split,
    switchRequest = SwitchRequest(group, fileToOpen)
  )

  /**
   * Opens the provided file in the editor, considering various conditions such as current file, window, group, splitters, and switching
   * requests.
   *
   * @param currentWindow The current editor window parameter.
   * @param currentFile The currently opened file.
   * @param fileToOpen The file that needs to be opened.
   * @param line The line number to scroll to once the file is opened.
   * @param group The editor group to which this file belongs.
   * @param newWindow Flag indicating if the file should be opened in a new window.
   * @param newTab Flag indicating if the file should be opened in a new tab.
   * @param splitters The splitters object indicating the split orientation.
   * @param switchRequest The request for switching editor context.
   * @return Result of the file open operation, if any.
   */
  private fun open(
    currentWindow: EditorWindow?,
    currentFile: VirtualFile?,
    fileToOpen: VirtualFile,
    line: Int?,
    group: EditorGroup,
    newWindow: Boolean,
    newTab: Boolean,
    splitters: Splitters,
    switchRequest: SwitchRequest
  ): OpenFileResult? {
    thisLogger().debug(
      "open2 fileToOpen = [$fileToOpen], currentFile = [$currentFile], group = [$group], newWindow = [$newWindow], newTab = [$newTab], splitters = [$splitters], switchingRequest = [$switchRequest]"
    )

    val resultAtomicReference = AtomicReference<OpenFileResult>()
    startSwitching(switchRequest)

    if (!warningShown && UISettings.getInstance().reuseNotModifiedTabs) {
      Notifications.notifyBugs()
      warningShown = true
    }

    // TODO it does not work in constructor
    if (initialEditorIndex == null) {
      try {
        initialEditorIndex = Key.create<Any>("initial editor index")
      } catch (e: Exception) {
        thisLogger().error(e)
        initialEditorIndex = Key.create<Any>("initial editor index not found")
      }
    }

    CommandProcessor.getInstance().executeCommand(
      project,
      {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val curWindow = currentWindow ?: manager.currentWindow
        val selectedFile = currentFile ?: curWindow?.selectedFile
        val selectedComposite = curWindow?.getSelectedComposite(ignorePopup = true)

        // If the file is already open, scroll to it (if line is provided)
        if (!splitters.isSplit && !newWindow && fileToOpen == selectedFile) {
          val editors = selectedComposite?.allEditors?.find { it.file == fileToOpen }
          val scroll = scroll(line, editors!!)
          if (scroll) {
            resultAtomicReference.set(OpenFileResult(isScrolledOnly = true))
          }

          thisLogger().debug(
            "fileToOpen.equals(selectedFile) [fileToOpen=$fileToOpen, selectedFile=$selectedFile, currentFile=$currentFile]"
          )

          resetSwitching()
          return@executeCommand
        }

        fileToOpen.putUserData(EditorGroupPanel.EDITOR_GROUP, group) // for project view colors
        // Clear lock
        if (initialEditorIndex != null) {
          fileToOpen.putUserData(initialEditorIndex!!, null)
        }

        when {
          // If the file is requested to open in a split
          splitters.isSplit && curWindow != null -> {
            thisLogger().debug("openFileInSplit $fileToOpen")

            val splitter = curWindow.split(
              orientation = splitters.orientation,
              forceSplit = true,
              virtualFile = fileToOpen,
              focusNew = true
            )
            if (splitter == null) {
              thisLogger().debug("no editors opened.")
              resetSwitching()
            }
          }

          // If requested to open in a new window
          newWindow                              -> {
            thisLogger().debug("openFileInNewWindow fileToOpen = $fileToOpen")

            val fileEditors = manager.openFile(
              fileToOpen,
              window = null,
              options = FileEditorOpenOptions(requestFocus = true, openMode = OpenMode.NEW_WINDOW)
            )
            scroll(line, *fileEditors.allEditors.toTypedArray())

            if (fileEditors.allEditors.isEmpty()) {
              thisLogger().debug("no editors opened..")
              resetSwitching()
            }
          }

          else                                   -> {
            val reuseNotModifiedTabs = UISettings.getInstance().reuseNotModifiedTabs
            try {
              thisLogger().debug("openFile $fileToOpen")
              // Temporarily disable the reuse not modified tabs to force open in a new tab
              if (newTab) UISettings.getInstance().reuseNotModifiedTabs = false

              // Open the file in the current window or a new window
              val openedFileEditor = manager.openFile(
                file = fileToOpen,
                window = curWindow,
                options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true)
              )
              // The window's editors
              val fileEditors = openedFileEditor.allEditors

              if (fileEditors.isEmpty()) {  // directory or some fail
                Notifications.showWarning(message("unable.to.open.editor.for.file", fileToOpen.name))
                thisLogger().debug("no editors opened")

                resetSwitching()
                return@executeCommand
              }

              fileEditors.forEach { fileEditor -> thisLogger().debug("opened fileEditor = $fileEditor") }

              // Scroll to the line
              scroll(line, *fileEditors.toTypedArray())

              if (reuseNotModifiedTabs) return@executeCommand

              // replace current tab
              // PS: it seems to mess order of tabs less if we do it after opening a new tab
              if (EditorGroupsSettings.instance.reuseCurrentTab && selectedFile != null && !newTab) {
                thisLogger().debug("closeFile $selectedFile")
                checkNotNull(currentWindow)
                currentWindow.closeFile(selectedFile)
              }
            } finally {
              UISettings.getInstance().reuseNotModifiedTabs = reuseNotModifiedTabs
            }
          }
        }
      },
      /* name = */
      null,
      /* groupId = */
      null
    )

    return resultAtomicReference.get()
  }

  /**
   * Scrolls the first text editor of the specified file editors to the given line.
   *
   * @param line The line number to scroll to. If null, the function returns false.
   * @param fileEditors The current window's file editors
   * @return True if the scrolling was successful in any of the file editors, false otherwise.
   */
  private fun scroll(line: Int?, vararg fileEditors: FileEditor): Boolean {
    if (line == null) return false

    for (fileEditor in fileEditors) {
      if (fileEditor is TextEditorImpl) {
        val position = LogicalPosition(line, 0)
        val editor: Editor = fileEditor.editor
        editor.run {
          caretModel.removeSecondaryCarets()
          caretModel.moveToLogicalPosition(position)
          scrollingModel.scrollToCaret(ScrollType.CENTER)
          selectionModel.removeSelection()
        }
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
        return true
      }
    }
    return false
  }

  private fun clearSwitchingRequest() {
    thisLogger().debug("clearSwitchingRequest")
    switchRequest = null
  }

  data class OpenFileResult(var isScrolledOnly: Boolean)

  companion object {
    val COMPARATOR: Comparator<EditorGroup> = Comparator.comparing { group: EditorGroup -> group.title.lowercase(Locale.getDefault()) }

    @JvmStatic
    fun getInstance(project: Project): EditorGroupManager = project.getService(EditorGroupManager::class.java)
  }
}
