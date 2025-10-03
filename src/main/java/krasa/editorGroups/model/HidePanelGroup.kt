package krasa.editorGroups.model

import com.intellij.openapi.project.Project
import krasa.editorGroups.icons.EditorGroupsIcons
import krasa.editorGroups.messages.EditorGroupsBundle.message
import javax.swing.Icon

class HidePanelGroup : EditorGroup() {
  override val id: String
    get() = ID

  override val title: String = message("hide.panel")

  override val isValid: Boolean = true

  override val isCustom: Boolean = true

  override val isAuto: Boolean = true

  override val switchDescription: String?
    get() = null

  override fun switchTitle(project: Project): String = title

  override fun icon(): Icon = EditorGroupsIcons.hide

  override fun invalidate(): Unit = Unit

  override fun getPresentableTitle(project: Project, presentableNameForUI: String, showSize: Boolean): String = presentableNameForUI

  override fun size(project: Project): Int = 0

  override fun getLinks(project: Project): List<Link> = emptyList()

  override fun isOwner(ownerPath: String): Boolean = true

  override fun equals(other: Any?): Boolean = super.equals(other) || other is HidePanelGroup

  override fun hashCode(): Int {
    var result = title.hashCode()
    result = 31 * result + isValid.hashCode()
    return result
  }

  companion object {
    const val ID: String = "HidePanelGroup"

    val INSTANCE: HidePanelGroup = HidePanelGroup()
  }
}
