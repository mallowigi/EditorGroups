package krasa.editorGroups.model

import com.intellij.openapi.project.Project
import krasa.editorGroups.services.AutoGroupProvider
import krasa.editorGroups.support.LinkComparator

/** Represents a group that is built automatically. */
abstract class AutoGroup(
  protected var links: List<Link>,
  open val project: Project? = null
) : EditorGroup() {

  @Volatile
  override var isValid: Boolean = true
    protected set

  abstract override val id: String
  abstract override val title: String

  val isEmpty: Boolean
    get() = links.isEmpty()

  override val switchDescription: String?
    get() = null

  override val isAuto: Boolean
    get() = true

  init {
    links.sortedWith(LinkComparator.INSTANCE)
  }

  override fun invalidate() {
    isValid = false
  }

  override fun size(project: Project): Int = links.size

  override fun getLinks(project: Project): List<Link> = links

  override fun isOwner(ownerPath: String): Boolean = false

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    return other.javaClass == this.javaClass
  }

  fun hasIndexing(): Boolean = links.any { it.name == AutoGroupProvider.INDEXING }

  override fun hashCode(): Int {
    var result = links.hashCode()
    result = 31 * result + isValid.hashCode()
    result = 31 * result + id.hashCode()
    result = 31 * result + title.hashCode()
    return result
  }

  companion object {
    const val SAME_FILE_NAME: String = "FILE_NAME"
    const val DIRECTORY: String = "DIRECTORY"
    const val SAME_FEATURE: String = "SAME_FEATURE"
  }
}
