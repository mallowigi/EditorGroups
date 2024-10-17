package krasa.editorGroups.actions

import com.intellij.ide.DataManager
import com.intellij.ide.actions.QuickSwitchSchemeAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.BitUtil
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.EditorGroupPanel
import krasa.editorGroups.Splitters
import krasa.editorGroups.UniqueTabNameBuilder
import krasa.editorGroups.model.Link
import krasa.editorGroups.support.Notifications.showWarning
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke

/**
 * Represents an action for switching files within a project.
 *
 * This action displays a popup that allows users to switch between files in various ways, such as opening files in new
 * tabs, new windows, or split views.
 */
class SwitchFileAction : QuickSwitchSchemeAction(), DumbAware {
  override fun showPopup(e: AnActionEvent, popup: ListPopup) {
    registerActions((popup as? ListPopupImpl?)!!)

    val project = e.project
    if (project == null) {
      popup.showInBestPositionFor(e.dataContext)
      return
    }

    val inputEvent = e.inputEvent
    when (inputEvent) {
      is MouseEvent -> {
        val component = inputEvent.component
        when (component) {
          is ActionMenuItem -> popup.showInBestPositionFor(e.dataContext)
          else              -> popup.showUnderneathOf(component)
        }
      }

      else          -> popup.showCenteredInCurrentWindow(project)
    }
  }

  private fun registerActions(popup: ListPopupImpl) = popup.run {
    // ctrl + click = open in new tab
    registerAction("newTab", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), customAction(this))
    // shift + enter = open in new window
    registerAction("newWindow", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), customAction(this))
    // alt + enter = open in right splitter
    registerAction("verticalSplit", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), customAction(this))
    // ctrl + alt + enter = open in bottom splitter
    registerAction(
      "horizontalSplit",
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK),
      customAction(this)
    )
  }

  private fun customAction(popup: ListPopupImpl): AbstractAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      val list = popup.list
      val selectedValue = list.getSelectedValue() as? PopupFactoryImpl.ActionItem

      if (selectedValue != null) {
        selectedValue.action.actionPerformed(
          // AnActionEvent(
          //   null,
          //   getDataContext(popup),
          //   myActionPlace,
          //   getTemplatePresentation(),
          //   ActionManager.getInstance(),
          //   e.getModifiers()
          // )

          AnActionEvent(
            getDataContext(popup),
            getTemplatePresentation(),
            myActionPlace,
            ActionUiKind.NONE,
            null,
            e.modifiers,
            ActionManager.getInstance()
          )
        )

        popup.closeOk(null)
      }
    }
  }

  private fun getDataContext(popup: ListPopupImpl): DataContext {
    val dataContext = DataManager.getInstance().getDataContext(popup.owner)
    val project = dataContext.getData<Project?>(CommonDataKeys.PROJECT)

    checkNotNull(project) { "Project is null for ${popup.owner}" }
    return dataContext
  }

  /**
   * Populates the popup with the group's files
   *
   * @param project The current project within which the method is called.
   * @param defaultActionGroup The group of actions to populate.
   * @param dataContext The context containing data related to the current file editor.
   */
  override fun fillActions(project: Project, defaultActionGroup: DefaultActionGroup, dataContext: DataContext) {
    try {
      val data = dataContext.getData<FileEditor?>(PlatformDataKeys.FILE_EDITOR) ?: return
      var panel: EditorGroupPanel = data.getUserData<EditorGroupPanel?>(EditorGroupPanel.EDITOR_PANEL) ?: return

      val currentFile = panel.file.path
      val group = panel.getDisplayedGroup()

      val links: MutableList<Link> = group.getLinks(project) as MutableList<Link>
      val uniqueTabNameBuilder = UniqueTabNameBuilder(project)
      val namesByPath = uniqueTabNameBuilder.getNamesByPath(
        paths = links,
        currentFile = null,
        project = project
      )

      for (link in links) {
        defaultActionGroup.add(
          newAction(
            project = project,
            panel = panel,
            currentFile = currentFile,
            link = link,
            text = namesByPath[link]
          )
        )
      }

    } catch (e: IndexNotReadyException) {
      thisLogger().error("That should not happen", e)
    }
  }

  private fun newAction(project: Project, panel: EditorGroupPanel, currentFile: String?, link: Link, text: String?): OpenFileAction {
    val action = OpenFileAction(
      link = link,
      project = project,
      panel = panel,
      text = text
    )

    if (link.path == currentFile) {
      action.getTemplatePresentation().setEnabled(false)
      action.getTemplatePresentation().setText("$text - current", false)
      action.getTemplatePresentation().setIcon(null)
    }
    return action
  }

  private class OpenFileAction(
    private val link: Link,
    private val project: Project,
    private val panel: EditorGroupPanel,
    text: String?
  ) :
    DumbAwareAction(text, link.path, link.fileIcon) {
    private val virtualFile = link.virtualFile

    init {
      getTemplatePresentation().setEnabled(virtualFile != null && virtualFile.exists())
    }

    override fun actionPerformed(e: AnActionEvent) {
      if (virtualFile == null) {
        showWarning("File not found $link")
        return
      }

      val openInNewTab = BitUtil.isSet(e.modifiers, InputEvent.CTRL_DOWN_MASK)
      val openInNewWindow = BitUtil.isSet(e.modifiers, InputEvent.SHIFT_DOWN_MASK)

      EditorGroupManager.getInstance(project).openGroupFile(
        groupPanel = panel,
        fileToOpen = virtualFile,
        line = null,
        newWindow = openInNewWindow,
        newTab = openInNewTab,
        split = Splitters.from(e)
      )
    }
  }

  companion object {
    const val ID = "krasa.editorGroups.SwitchFile"
  }
}
