package krasa.editorGroups.model

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.support.LinkComparator
import krasa.editorGroups.support.getFileIcon
import krasa.editorGroups.support.getVirtualFileByAbsolutePath
import java.io.File
import javax.swing.Icon

/** Represents a link to a file or directory. */
abstract class Link(private val project: Project) {
  /** Represents a link to a file or directory. */
  var icon: Icon? = null
    protected set

  /** The line number. */
  var line: Int? = null
    protected set

  var customName: String? = null
    protected set

  /** The actual path. */
  abstract val path: String

  val fileIcon: Icon
    get() = when {
      icon != null -> icon!!
      else         -> getFileIcon(path, project)
    }

  open val virtualFile: VirtualFile?
    get() = getVirtualFileByAbsolutePath(path)

  /** Return the name shown for the link. */
  open val name: String
    get() {
      val uiSettings: UISettings = UISettings.instanceOrNull ?: return path
      val virtualFile: VirtualFile? = this.virtualFile

      if (virtualFile == null) {
        thisLogger().warn("VirtualFile is null for $path") // NON-NLS
        return path
      }

      return when {
        uiSettings.hideKnownExtensionInTabs -> virtualFile.nameWithoutExtension
        else                                -> virtualFile.name
      }
    }

  constructor(icon: Icon?, line: Int?, project: Project) : this(project) {
    this.icon = icon
    this.line = line
  }

  open fun exists(): Boolean = File(path).exists()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Link) return false

    if (icon != other.icon) return false
    if (line != other.line) return false
    if (path != other.path) return false
    return customName == other.customName
  }

  open fun fileEquals(currentFile: VirtualFile): Boolean = path == currentFile.path

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + (icon?.hashCode() ?: 0)
    result = 31 * result + (line ?: 0)
    result = 31 * result + (customName?.hashCode() ?: 0)
    result = 31 * result + path.hashCode()
    return result
  }

  companion object {
    @JvmStatic
    fun from(links: Collection<String>, project: Project): List<Link> =
      links.map { PathLink(it, project) }.sortedWith(LinkComparator.INSTANCE)

    @JvmStatic
    fun fromFile(file: VirtualFile, project: Project): Link = VirtualFileLink(file, project)

    @JvmStatic
    fun fromVirtualFiles(links: Collection<VirtualFile>, project: Project): List<Link> =
      links.map { VirtualFileLink(it, project) }.sortedWith(LinkComparator.INSTANCE)
  }
}
