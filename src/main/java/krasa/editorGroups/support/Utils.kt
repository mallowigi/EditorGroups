@file:Suppress("detekt:TooGenericExceptionThrown") // NON-NLS

package krasa.editorGroups.support

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.IconUtil.computeFileIcon
import com.intellij.util.ReflectionUtil
import krasa.editorGroups.model.Link
import java.awt.Component
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException
import javax.swing.Icon

const val EDITOR_GROUP_TAB_MENU: String = "krasa.editorGroups.EditorGroupsTabPopupMenu"

/**
 * Retrieves the presentable name from a given path, e.g. the last element of the path, without the extension.
 *
 * @param path the path from which to retrieve the presentable name
 * @return the presentable name
 */
fun toPresentableName(path: String): String {
  var name = path
  // Take the last element of the path
  val i = StringUtil.lastIndexOfAny(path, "\\/")
  if (i > 0) {
    name = path.substring(i + 1)
  }

  // Remove the extension if the setting is enabled
  val uiSettings: UISettings? = UISettings.instanceOrNull
  if (uiSettings != null && uiSettings.hideKnownExtensionInTabs) {
    name = cutExtension(name, "/")
  }

  return name
}

/**
 * Removes the extension from the given [result] if it ends with [separator].
 *
 * @param result The string from which to remove the extension.
 * @param separator The separator used to check if the [result] ends with it.
 * @return The [result] without the extension if it ends with [separator], otherwise the [result] itself.
 */
fun cutExtension(result: String, separator: String): String {
  val withoutExtension = FileUtil.getNameWithoutExtension(result)
  if (StringUtil.isNotEmpty(withoutExtension) && !withoutExtension.endsWith(separator)) return withoutExtension

  return result
}

/**
 * Gets the file icon for the given path.
 *
 * @param path The file path.
 * @param project The project context (optional).
 * @return The file icon.
 */
fun getFileIcon(path: String?, project: Project?): Icon {
  if (path == null) return AllIcons.FileTypes.Any_type

  val file = getFileByPath(path) ?: return AllIcons.FileTypes.Any_type
  return computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
}

/**
 * Try to resolve a file from a path, optionally from the current file
 *
 * @param path
 * @param currentFile
 * @return
 */
fun getFileByPath(path: String, currentFile: VirtualFile?): VirtualFile? {
  try {
    return ApplicationManager.getApplication().executeOnPooledThread<VirtualFile?> {
      var file: VirtualFile? = null

      when {
        path.contains(".jar")               -> file = VirtualFileManager.getInstance().findFileByUrl("jar://$path") // NON-NLS
        OSAgnosticPathUtil.isAbsolute(path) -> file = LocalFileSystem.getInstance().findFileByPath(path)
        currentFile != null                 -> {
          val parent = currentFile.parent
          if (parent != null) {
            file = parent.findFileByRelativePath(path)
          }
        }

        path.startsWith("file://")          -> file = VirtualFileManager.getInstance().findFileByUrl(path) // NON-NLS

        else                                -> file = LocalFileSystem.getInstance().findFileByPath(path)
      }

      if (file == null && currentFile == null) {
        // Try to refresh the file system and find the file again
        file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
      }

      file
    }.get()
  } catch (e: ExecutionException) {
    throw RuntimeException(e)
  } catch (e: InterruptedException) {
    throw RuntimeException(e)
  }
}

/**
 * Resolves a file by its path in the whole file system
 *
 * @param path the path of the file to resolve
 * @return the resolved VirtualFile, or null if the file is not found
 */
fun getFileByPath(path: String): VirtualFile? = getFileByPath(path, null)

/**
 * Retrieves a VirtualFile instance corresponding to the given link's file path.
 *
 * @param link The Link object containing the file path.
 * @return The VirtualFile found at the specified path, or null if no file is found.
 */
fun getFileByPath(link: Link): VirtualFile? = getFileByPath(link.path)

/**
 * Resolves a file by its path in the whole file system
 *
 * @param path the path of the file to resolve
 * @return the resolved VirtualFile, or null if the file is not found
 */
fun getNullableFileByPath(path: String?): VirtualFile? {
  return getFileByPath(path ?: return null, null)
}

/**
 * Retrieves a virtual file by its absolute path.
 *
 * @param path the absolute path of the file
 * @return the resolved [VirtualFile], or null if the file is not found
 */
fun getVirtualFileByAbsolutePath(path: String): VirtualFile? = if (File(path).exists()) getFileByPath(path) else null

/** Get file from text editor. */
fun getFileFromTextEditor(textEditor: FileEditor): VirtualFile =
  unwrapPreview(textEditor.file) ?: throw RuntimeException("File not found for $textEditor")

/** Return the file after trying to unwrap it from another plugin's wrap, if necessary. */
fun unwrapPreview(file: VirtualFile?): VirtualFile? {
  var result = file ?: return null

  try {
    // Compatibility with Quick File Preview plugin
    if (result.javaClass.getPackage().name.startsWith("net.seesharpsoft")) {
      val source = ReflectionUtil.getMethod(result.javaClass, "getSource")
      result = Objects.requireNonNull(source)?.invoke(result) as VirtualFile
    }
  } catch (e: Throwable) {
    //
    throw RuntimeException(e)
  }

  return result
}

/**
 * Returns the canonical path of the given file.
 *
 * @param file the file to get the canonical path of
 * @return the canonical path of the given file
 * @throws RuntimeException if an IOException occurs while getting the canonical path
 */
fun getCanonicalPath(file: File): String = try {
  file.canonicalPath
} catch (e: IOException) {
  throw java.lang.RuntimeException(e)
}

/**
 * Checks whether the given file is a JAR or ZIP file.
 *
 * @param file the file to be checked
 * @return true if the file is a JAR or ZIP file, false otherwise
 */
fun isJarOrZip(file: VirtualFile): Boolean {
  if (file.isDirectory) return false

  val name = file.name
  return listOf(".jar", ".zip").any { StringUtil.endsWithIgnoreCase(name, it) }
}

/**
 * Checks if a given CharSequence is blank.
 *
 * @param cs The CharSequence to check for blankness. It can be null.
 * @return true if the input CharSequence is blank, false otherwise.
 */
@Suppress("unused") // NON-NLS
fun isBlank(cs: CharSequence?): Boolean {
  if (cs == null) return true

  val strLen = cs.length
  if (strLen == 0) return true

  return cs.indices.all { Character.isWhitespace(cs[it]) }
}

/**
 * Retrieves the settings associated with the specified component.
 *
 * @param component The component for which settings are to be retrieved.
 * @return The settings associated with the component, or null if no settings are found.
 */
fun getSettings(component: Component): Settings? = Settings.KEY.getData(DataManager.getInstance().getDataContext(component))

/**
 * Navigates to the settings page associated with a specific component.
 *
 * @param component The component for which the settings page is to be navigated.
 * @param name The name of the settings page to navigate to.
 */
fun navigateToSettingsPage(component: Component, name: String) {
  val settings = getSettings(component) ?: return
  settings.select(settings.find(name))
}

/**
 * Extracts the name of a file without its type and extension.
 *
 * @param name the full name of the file, including its type and extension
 * @return the name of the file without its type and extension
 */
fun getNameWithoutTypeAndExt(name: CharSequence): CharSequence {
  val lastIndex = StringUtilRt.lastIndexOf(name, '.', 0, name.length)
  if (lastIndex < 0) return name

  val secondToLastIndex = StringUtilRt.lastIndexOf(name, '.', 0, lastIndex)
  return when {
    secondToLastIndex < 0 -> name
    else                  -> name.subSequence(0, secondToLastIndex)
  }
}
