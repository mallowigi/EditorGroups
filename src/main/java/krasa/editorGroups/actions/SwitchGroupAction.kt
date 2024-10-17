package krasa.editorGroups.actions

import com.intellij.ide.actions.QuickSwitchSchemeAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.PopupHandler
import krasa.editorGroups.*
import krasa.editorGroups.EditorGroupsSettingsState.Companion.state
import krasa.editorGroups.actions.PopupMenu.popupInvoked
import krasa.editorGroups.icons.EditorGroupsIcons
import krasa.editorGroups.model.*
import krasa.editorGroups.support.Notifications.showWarning
import krasa.editorGroups.support.getVirtualFileByAbsolutePath
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent

class SwitchGroupAction : QuickSwitchSchemeAction(), DumbAware, CustomComponentAction {

  override fun showPopup(e: AnActionEvent, popup: ListPopup) {
    val project = e.project
    if (project == null) {
      popup.showInBestPositionFor(e.dataContext)
      return
    }

    val inputEvent = e.inputEvent
    if (inputEvent !is MouseEvent) {
      popup.showCenteredInCurrentWindow(project)
      return
    }

    val component = inputEvent.component
    when (component) {
      is ActionMenuItem -> popup.showInBestPositionFor(e.dataContext)
      else              -> popup.showUnderneathOf(component)
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    presentation.icon = EditorGroupsIcons.groupBy

    button.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) = popupInvoked(comp, x, y)
    })

    return button
  }

  override fun fillActions(project: Project, defaultActionGroup: DefaultActionGroup, dataContext: DataContext) {
    try {
      val data = dataContext.getData(PlatformDataKeys.FILE_EDITOR)
      var editorGroupPanel: EditorGroupPanel? = null
      var displayedGroup = EditorGroup.EMPTY
      var currentFileGroups = emptyList<EditorGroup>()
      val tempGroup = DefaultActionGroup()
      var file: VirtualFile? = null
      var regexGroups = emptyList<RegexGroup>()

      // Fill the actions
      if (data != null) {
        editorGroupPanel = data.getUserData(EditorGroupPanel.EDITOR_PANEL)
        if (editorGroupPanel != null) {
          file = editorGroupPanel.file
          displayedGroup = editorGroupPanel.displayedGroup

          // Same file name
          defaultActionGroup.add(
            createAction(
              displayedGroup = displayedGroup,
              targetGroup = SameNameGroup(fileNameWithoutExtension = file.nameWithoutExtension, links = emptyList()),
              project = project,
              actionHandler = refreshHandler(editorGroupPanel)
            )
          )
          // Current folder
          defaultActionGroup.add(
            createAction(
              displayedGroup = displayedGroup,
              targetGroup = FolderGroup(folder = file.parent, links = emptyList()),
              project = project,
              actionHandler = refreshHandler(editorGroupPanel)
            )
          )
          // Hide panel
          defaultActionGroup.add(
            createAction(
              displayedGroup = displayedGroup,
              targetGroup = HidePanelGroup(),
              project = project,
              actionHandler = refreshHandler(editorGroupPanel)
            )
          )

          // Current file custom groups
          currentFileGroups = fillCurrentFileGroups(
            project = project,
            tempGroup = tempGroup,
            panel = editorGroupPanel,
            file = file
          )
          // Current file related regex groups
          regexGroups = fillRegexGroups(
            project = project,
            tempGroup = tempGroup,
            panel = editorGroupPanel,
            file = file
          )
        }
      }

      addBookmarkGroup(
        project = project,
        defaultActionGroup = defaultActionGroup,
        panel = editorGroupPanel,
        displayedGroup = displayedGroup,
        file = file
      )
      fillOtherIndexedGroups(
        group = tempGroup,
        currentGroups = currentFileGroups,
        displayedGroup = displayedGroup,
        project = project
      )
      fillFavorites(
        defaultActionGroup = tempGroup,
        project = project,
        editorGroups = currentFileGroups,
        displayedGroup = displayedGroup
      )
      fillGlobalRegexGroups(
        defaultActionGroup = tempGroup,
        project = project,
        displayedGroup = displayedGroup,
        alreadyFilledRegexGroups = regexGroups
      )


      when {
        // If the option to group the groups is enabled
        state().isGroupSwitchGroupAction -> defaultActionGroup.addAll(*tempGroup.childActionsOrStubs)
        else                             -> {
          val childActionsOrStubs = tempGroup.childActionsOrStubs
          val list = childActionsOrStubs.asSequence()
            .filterNot { anAction: AnAction? -> anAction is Separator }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.templatePresentation.text })
            .toList()

          defaultActionGroup.add(Separator())
          defaultActionGroup.addAll(list)
        }
      }

      defaultActionGroup.add(Separator())
      defaultActionGroup.add(ActionManager.getInstance().getAction(TogglePanelVisibilityAction.ID))
      defaultActionGroup.add(ActionManager.getInstance().getAction(OpenConfigurationAction.ID))
    } catch (e: IndexNotReadyException) {
      thisLogger().error("That should not happen", e)
    }
  }

  /**
   * Adds a bookmark group action to the given action group.
   *
   * TODO use new bookmark system
   *
   * @param project The current project instance.
   * @param defaultActionGroup The action group to which the bookmark action will be added.
   * @param panel Optional panel to be updated upon bookmark action.
   * @param displayedGroup The editor group to be displayed.
   * @param file Optional virtual file to check for bookmark availability.
   */
  private fun addBookmarkGroup(
    project: Project,
    defaultActionGroup: DefaultActionGroup,
    panel: EditorGroupPanel?,
    displayedGroup: EditorGroup,
    file: VirtualFile?
  ) {
    val bookmarkGroup = ExternalGroupProvider.getInstance(project).bookmarkGroup

    val actionHandler = object : Handler() {
      override fun run(editorGroup: EditorGroup) {
        when {
          panel != null && file != null && bookmarkGroup.containsLink(project, file) -> refreshHandler(panel).run(bookmarkGroup)
          else                                                                       -> otherGroupHandler(project).run(bookmarkGroup)
        }
      }
    }

    val action = createBookmarkGroupAction(
      displayedGroup = displayedGroup,
      targetGroup = bookmarkGroup,
      project = project,
      actionHandler = actionHandler,
    )

    defaultActionGroup.add(action)
  }

  /**
   * Appends the file's related custom groups to the tempGroup
   *
   * @param project The current project.
   * @param tempGroup The temporary action group to populate.
   * @param panel The panel associated with editor groups.
   * @param file The currently open file.
   * @return A list of editor groups associated with the current file.
   */
  private fun fillCurrentFileGroups(
    project: Project,
    tempGroup: DefaultActionGroup,
    panel: EditorGroupPanel,
    file: VirtualFile?
  ): List<EditorGroup> {
    val displayedGroup = panel.displayedGroup
    val manager = EditorGroupManager.getInstance(project)
    val groups = manager.getGroups(file!!)

    tempGroup.add(Separator("Groups for the Current File"))

    groups.forEach {
      tempGroup.add(
        createAction(
          displayedGroup = displayedGroup,
          targetGroup = it,
          project = project,
          actionHandler = refreshHandler(panel)
        )
      )
    }

    return groups
  }

  /**
   * Appends the file related regex groups to the tempGroup
   *
   * @param project the current project context.
   * @param tempGroup the action group to which the actions will be added.
   * @param panel the editor panel containing the displayed group.
   * @param file the virtual file to be matched against regex groups.
   * @return a list of matching regex groups for the specified file.
   */
  private fun fillRegexGroups(
    project: Project,
    tempGroup: DefaultActionGroup,
    panel: EditorGroupPanel,
    file: VirtualFile?
  ): List<RegexGroup> {
    val regexGroups = RegexGroupProvider.getInstance(project).findMatchingRegexGroups(file!!)

    regexGroups.forEach { regexGroup ->
      tempGroup.add(
        createAction(
          displayedGroup = panel.displayedGroup,
          targetGroup = regexGroup,
          project = project,
          actionHandler = refreshHandler(panel)
        )
      )
    }

    return regexGroups
  }

  /**
   * Fills the provided action group with actions for all editor groups that are indexed but not currently displayed.
   *
   * @param group The action group to fill with actions.
   * @param currentGroups A list of editor groups that are currently displayed.
   * @param displayedGroup The editor group that is currently being displayed.
   * @param project The current project.
   */
  private fun fillOtherIndexedGroups(
    group: DefaultActionGroup,
    currentGroups: List<EditorGroup>,
    displayedGroup: EditorGroup,
    project: Project
  ) {
    val manager = EditorGroupManager.getInstance(project)
    val currentGroupSet = currentGroups.toSet()
    val indexingAction: AnAction = object : AnAction("Indexing...") {
      override fun actionPerformed(anActionEvent: AnActionEvent) = Unit
    }

    group.add(Separator("Other Groups"))

    runCatching { manager.allIndexedGroups }
      .onSuccess { allGroups ->
        allGroups
          .filterNot { g -> currentGroupSet.contains(g) }
          .forEach { g ->
            group.add(
              createAction(
                displayedGroup = displayedGroup,
                targetGroup = g,
                project = project,
                actionHandler = otherGroupHandler(project)
              )
            )
          }
      }
      .onFailure { exception ->
        indexingAction.templatePresentation.isEnabled = false
        group.add(indexingAction)
      }
  }

  /**
   * Populates the given `defaultActionGroup` with favorite editor groups that are not already displayed in the provided `editorGroups`.
   *
   * TODO deprecate favorites
   *
   * @param defaultActionGroup The action group to which favorite groups will be added.
   * @param project The current project context.
   * @param editorGroups The list of current editor groups.
   * @param displayedGroup The editor group that is currently displayed.
   */
  private fun fillFavorites(
    defaultActionGroup: DefaultActionGroup,
    project: Project,
    editorGroups: List<EditorGroup>,
    displayedGroup: EditorGroup
  ) {
    val favoritesGroups = ExternalGroupProvider.getInstance(project).favoritesGroups
    val alreadyDisplayed = editorGroups.filterIsInstance<FavoritesGroup>().mapTo(HashSet()) { it.title }

    if (favoritesGroups.isNotEmpty()) {
      defaultActionGroup.add(Separator("Favorites"))
      favoritesGroups
        .filterNot { it.title in alreadyDisplayed }
        .forEach { favoritesGroup ->
          defaultActionGroup.add(
            createAction(
              displayedGroup = displayedGroup,
              targetGroup = favoritesGroup,
              project = project,
              actionHandler = otherGroupHandler(project)
            )
          )
        }
    }
  }

  /**
   * Add regex groups that do not belong to the current file
   *
   * @param defaultActionGroup The action group to which regex actions will be added.
   * @param project The current project context.
   * @param displayedGroup The currently displayed editor group.
   * @param alreadyFilledRegexGroups List of regex groups that are already filled/displayed.
   */
  private fun fillGlobalRegexGroups(
    defaultActionGroup: DefaultActionGroup,
    project: Project,
    displayedGroup: EditorGroup,
    alreadyFilledRegexGroups: List<RegexGroup>
  ) {
    val regexGroups = RegexGroupProvider.getInstance(project).findProjectRegexGroups()

    val alreadyDisplayed: MutableSet<String?> = alreadyFilledRegexGroups
      .filter { it.regexGroupModel.scope == RegexGroupModel.Scope.WHOLE_PROJECT }
      .mapTo(HashSet()) { it.regexGroupModel.regex }


    if (regexGroups.isNotEmpty()) {
      defaultActionGroup.add(Separator("Regexps"))

      regexGroups
        .asSequence()
        .filterNot { alreadyDisplayed.contains(it.regexGroupModel.regex) }
        .forEach { group ->

          defaultActionGroup.add(
            createAction(
              displayedGroup = displayedGroup,
              targetGroup = group,
              project = project,
              actionHandler = otherRegexGroupHandler(project, group)
            )
          )
        }
    }
  }

  private fun refreshHandler(panel: EditorGroupPanel): Handler = object : Handler() {
    override fun run(editorGroup: EditorGroup) {
      thisLogger().debug("switching group")
      panel._refresh(false, editorGroup)
    }
  }

  private fun otherRegexGroupHandler(project: Project, group: RegexGroup) = object : Handler() {
    override fun run(editorGroup: EditorGroup) {
      val regexGroup = RegexGroupProvider.getInstance(project).getRegexGroup(group = group, project = project, currentFile = null)
      otherGroupHandler(project).run(regexGroup)
    }
  }

  /**
   * Handles the given editor group by attempting to open the first existing file in the group within the specified project. If the file
   * does not exist, it uses the owner path of the group to find a corresponding virtual file and attempts to open it. Displays a warning if
   * neither the file nor the owner path exists.
   *
   * @param project The project context within which the editor group is handled.
   * @return A newly instantiated handler for the specified editor group.
   */
  private fun otherGroupHandler(project: Project): Handler = object : Handler() {
    override fun run(editorGroup: EditorGroup) {
      val editorGroupManager = EditorGroupManager.getInstance(project)

      val file = editorGroup.getFirstExistingFile(project)
      if (file != null) {
        editorGroupManager.openFile(
          fileToOpen = file,
          newWindow = false,
          newTab = true,
          split = Splitters.NONE,
          group = editorGroup,
          current = null
        )
        return
      }

      val ownerPath = editorGroup.ownerPath
      val virtualFileByAbsolutePath = getVirtualFileByAbsolutePath(ownerPath)
      if (virtualFileByAbsolutePath != null) {
        editorGroupManager.openFile(
          fileToOpen = virtualFileByAbsolutePath,
          newWindow = false,
          newTab = true,
          split = Splitters.NONE,
          group = editorGroup,
          current = null
        )
        return
      }

      showWarning("No matching file found")
      thisLogger().debug("opening failed, no file and not even owner exist $editorGroup")
    }
  }

  /**
   * Creates a new action for switching the editor group.
   *
   * @param displayedGroup the currently displayed editor group
   * @param targetGroup the target editor group to switch to
   * @param project the project in which the switch is to be made
   * @param actionHandler the handler for the action to be performed
   * @return a new DumbAwareAction configured for the switch action
   */
  private fun createAction(
    displayedGroup: EditorGroup,
    targetGroup: EditorGroup,
    project: Project,
    actionHandler: Handler
  ): DumbAwareAction {
    val isSelected = displayedGroup.isSelected(targetGroup)
    val description = targetGroup.switchDescription

    var title = targetGroup.switchTitle(project)
    if (isSelected) {
      title += " - Current"
    }

    val dumbAwareAction: DumbAwareAction = object : DumbAwareAction(title, description, targetGroup.icon()) {
      override fun actionPerformed(event: AnActionEvent) = actionHandler.run(targetGroup)
      override fun getActionUpdateThread(): ActionUpdateThread = super.getActionUpdateThread()

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !isSelected
      }
    }

    return dumbAwareAction
  }

  private fun createBookmarkGroupAction(
    displayedGroup: EditorGroup,
    targetGroup: EditorGroup,
    project: Project,
    actionHandler: Handler
  ): DumbAwareAction {
    val isSelected = displayedGroup.isSelected(targetGroup)
    val description = targetGroup.switchDescription

    var title = targetGroup.switchTitle(project)
    if (isSelected) {
      title += " - Current"
    }

    val dumbAwareAction: DumbAwareAction = object : DumbAwareAction(title, description, targetGroup.icon()) {
      override fun actionPerformed(event: AnActionEvent) = actionHandler.run(targetGroup)
      override fun getActionUpdateThread(): ActionUpdateThread = super.getActionUpdateThread()

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !isSelected

        if (targetGroup.size(project) == 0) {
          e.presentation.isEnabled = false
          e.presentation.setText("${targetGroup.title} - empty")
        }
      }
    }

    return dumbAwareAction
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val data = e.getData(PlatformDataKeys.FILE_EDITOR) ?: return

    val presentation = e.presentation
    val panel = data.getUserData(EditorGroupPanel.EDITOR_PANEL) ?: return

    var displayedGroup = panel.displayedGroup
    val toBeRendered = panel.toBeRendered

    if (displayedGroup === EditorGroup.EMPTY && toBeRendered != null) {
      displayedGroup = toBeRendered // to remove flicker when switching
    }

    presentation.icon = displayedGroup.icon()
  }

  internal abstract class Handler {
    abstract fun run(editorGroup: EditorGroup)
  }

  companion object {
    const val ID = "krasa.editorGroups.SwitchGroup"
  }
}
