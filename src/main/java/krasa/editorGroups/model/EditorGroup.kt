package krasa.editorGroups.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.getFileByPath
import krasa.editorGroups.support.toPresentableName
import org.jetbrains.annotations.NonNls
import java.awt.Color
import javax.swing.Icon

abstract class EditorGroup {
  open var isStub: Boolean = false

  abstract val id: String

  open val ownerPath: String
    get() = id

  abstract val title: String

  abstract val isValid: Boolean

  open val isCustom: Boolean = false

  val isInvalid: Boolean
    get() = !isValid

  abstract val switchDescription: String?

  open val bgColor: Color?
    get() = null

  open val fgColor: Color?
    get() = null

  open val isAuto: Boolean = false

  abstract fun icon(): Icon?

  abstract fun invalidate()

  abstract fun size(project: Project): Int

  abstract fun getLinks(project: Project): List<Link>

  abstract fun isOwner(ownerPath: String): Boolean

  /**
   * Returns the presentable title for the editor group.
   *
   * @param project the project for which the title is generated
   * @param presentableNameForUI the presentable name for the UI
   * @param showSize if true, includes the size in the title; false otherwise
   * @return the presentable title
   */
  open fun getPresentableTitle(project: Project, presentableNameForUI: String, showSize: Boolean): String {
    val isEmptyTitle = StringUtil.isEmpty(title)
    val size = size(project)
    val doShowSize = EditorGroupsSettings.instance.isShowSize && showSize

    return when {
      doShowSize    -> when {
        !isEmptyTitle -> "[${this.title}] $presentableNameForUI ($size)"
        else          -> "$presentableNameForUI ($size)"
      }

      !isEmptyTitle -> "[$title] $presentableNameForUI"
      else          -> presentableNameForUI
    }
  }

  /** Returns the text to display on the tabs (for the EditorTabTitleProvider) */
  open fun getTabTitle(project: Project, presentableNameForUI: String): String {
    val isEmptyTitle = StringUtil.isEmpty(title)

    return when {
      !isEmptyTitle -> "[$title] $presentableNameForUI"
      else          -> presentableNameForUI
    }
  }

  /**
   * Checks if the given project contains a link with the specified current file path.
   *
   * @param project the project to check for links
   * @param currentFilePath the path of the current file
   * @return true if the project contains a link with the specified current file path, false otherwise
   */
  fun containsLink(project: Project, currentFilePath: String): Boolean {
    val links = getLinks(project)
    return links.any { it.path.contains(currentFilePath) }
  }

  /**
   * Checks if the given project contains a link with the specified current file.
   *
   * @param project the project to check for links
   * @param currentFile the current file to compare with links
   * @return true if the project contains a link with the specified current file, false otherwise
   */
  fun containsLink(project: Project, currentFile: VirtualFile): Boolean {
    val links = getLinks(project)
    return links.any { it.fileEquals(currentFile) }
  }

  /**
   * Checks if the provided [group] visually equals this [EditorGroup] by comparing the values of its properties.
   *
   * @param project the [Project] to check for links
   * @param group the [EditorGroup] to compare with this [EditorGroup]
   * @param links the list of [Link] objects to compare with the links of this [EditorGroup]
   * @param stub the stub indicator to compare with the `isStub` property of this [EditorGroup]
   * @return true if the provided [group] visually equals this [EditorGroup], false otherwise
   */
  fun equalsVisually(project: Project, group: EditorGroup?, links: List<Link>, stub: Boolean): Boolean = when {
    group == null       -> false
    this.isStub != stub -> false
    this != group       -> false
    else                -> this.getLinks(project) == links
  }

  /**
   * Retrieves the first existing file from the provided project's links. Returns null if no existing file is found.
   *
   * @param project the project from which to retrieve the file
   * @return the first existing file, or null if not found
   */
  fun getFirstExistingFile(project: Project): VirtualFile? = getLinks(project)
    .map { getFileByPath(it) }
    .firstOrNull { it != null && it.exists() && !it.isDirectory }

  /**
   * Returns the tab title for the editor group.
   *
   * @param project the project for which the title is generated
   * @return the tab title
   */
  @Suppress("detekt:UnusedParameter", "HardCodedStringLiteral")
  fun tabTitle(project: Project): String {
    var result = this.title
    if (result.isEmpty()) {
      result = toPresentableName(ownerPath)
    }

    return result
  }

  /**
   * Switches the title of the editor group based on the provided project.
   *
   * @param project the project for which the title is switched
   * @return the switched title, or null if not found
   */
  @NonNls
  open fun switchTitle(project: Project): String? {
    val resultOwnerPath = ownerPath
    // Take the last element of the path without the ext
    val name = toPresentableName(resultOwnerPath)
    return getPresentableTitle(project = project, presentableNameForUI = name, showSize = true)
  }

  /**
   * Retrieves the tooltip text for the tab group.
   *
   * @param project the project for which the tooltip is generated
   * @return the tooltip text for the tab group, or null if not found
   */
  fun getTabGroupTooltipText(project: Project): String = getPresentableTitle(
    project = project,
    presentableNameForUI = message("owner.0", ownerPath),
    showSize = true
  )

  /**
   * Checks if this EditorGroup is selected.
   *
   * @param editorGroup the EditorGroup to compare with this EditorGroup
   * @return true if this EditorGroup is equal to the provided groupLink, false otherwise
   */
  open fun isSelected(editorGroup: EditorGroup): Boolean = this == editorGroup

  /** Whether smart mode (i.e. not dumb mode) is required for this group type. */
  open fun needSmartMode(): Boolean = false

  fun exists(): Boolean = id != NOT_EXISTS

  companion object {
    private const val NOT_EXISTS: String = "NOT_EXISTS"

    /** EMPTY group placeholder. */
    @JvmField
    val EMPTY: EditorGroup = EditorGroupIndexValue(NOT_EXISTS, NOT_EXISTS, false).setLinks(emptyList())
  }
}