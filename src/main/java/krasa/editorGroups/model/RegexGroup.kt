package krasa.editorGroups.model

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.icons.EditorGroupsIcons
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.settings.EditorGroupsSettings
import java.awt.Color
import java.util.regex.Matcher
import javax.swing.Icon

class RegexGroup(
  regexGroupModel: RegexGroupModel,
  val folder: VirtualFile?,
  links: List<Link>,
  val fileName: String?
) : AutoGroup(links) {

  val regexGroupModel: RegexGroupModel = regexGroupModel.copy()

  override var isStub: Boolean = true

  override val id: String
    get() = ID_PREFIX + regexGroupModel.serialize()

  override val title: String
    get() = id

  val name: String
    get() = regexGroupModel.myName

  val pattern: String
    get() = regexGroupModel.regexPattern.toString()

  val scope: String
    get() = when (regexGroupModel.myScope) {
      Scope.CURRENT_FOLDER       -> message("scope.currentFolder")
      Scope.INCLUDING_SUBFOLDERS -> message("scope.includeSubfolders")
      Scope.WHOLE_PROJECT        -> message("scope.wholeProject")
    }

  override val switchDescription: String
    get() = scope

  val referenceMatcher: Matcher?
    get() {
      var referenceMatcher: Matcher? = null
      val resultFileName = fileName

      if (resultFileName != null) {
        referenceMatcher = regexGroupModel.regexPattern?.matcher(resultFileName) ?: return null
        val matches = referenceMatcher.matches()
        if (!matches) {
          thisLogger().error("$resultFileName does not match $regexGroupModel") // NON-NLS
        }
      }
      return referenceMatcher
    }

  override val bgColor: Color
    get() = regexGroupModel.myColor

  constructor(model: RegexGroupModel, folder: VirtualFile?) : this(model, folder, emptyList<Link>(), null)
  constructor(model: RegexGroupModel, folder: VirtualFile?, fileName: String?) : this(model, folder, emptyList<Link>(), fileName)

  override fun icon(): Icon = EditorGroupsIcons.regex

  override fun switchTitle(project: Project): String? {
    val doShowSize = EditorGroupsSettings.instance.isShowSize
    val size = size(project)
    val nameForUI = "[$name] - $pattern"

    return when {
      doShowSize -> "$nameForUI ($size)"
      else       -> nameForUI
    }
  }

  override fun isSelected(editorGroup: EditorGroup): Boolean = when (editorGroup) {
    is RegexGroup -> regexGroupModel == editorGroup.regexGroupModel
    else          -> super.isSelected(editorGroup)
  }

  override fun needSmartMode(): Boolean = regexGroupModel.scope == Scope.WHOLE_PROJECT

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    if (!super.equals(other)) return false

    val that = other as RegexGroup

    if (regexGroupModel != that.regexGroupModel) return false
    return folder == that.folder
  }

  override fun hashCode(): Int {
    var result = regexGroupModel.hashCode()
    result = 31 * result + (folder?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String =
    "RegexGroup{model='$regexGroupModel', fileName='$fileName', folderPath='$folder', links=${links.size}, stub='$isStub'}"

  fun getScopes(project: Project): List<VirtualFile?> {
    val paths: MutableList<VirtualFile?> = ArrayList()

    // If we want to find all files of the same regex in the whole project
    if (regexGroupModel.scope == Scope.WHOLE_PROJECT) {
      val allContentRoots = ProjectRootUtil.getAllContentRoots(project)
      // Get all content roots' paths
      allContentRoots.mapTo(paths) { it.virtualFile }
    } else {
      // Only return current folder
      paths.add(folder)
    }

    return paths
  }

  companion object {
    const val ID_PREFIX: String = "RegexGroup: "
  }
}