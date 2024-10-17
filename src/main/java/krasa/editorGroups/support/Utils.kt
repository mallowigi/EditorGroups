package krasa.editorGroups.support

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.ColorUtil
import com.intellij.util.IconUtil.computeFileIcon
import com.intellij.util.ReflectionUtil
import com.intellij.util.ui.NamedColorUtil
import krasa.editorGroups.model.Link
import java.awt.Color
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException
import javax.swing.Icon

val LOG = Logger.getInstance(Utils::class.java)
val ERROR_FOREGROUND_COLOR = NamedColorUtil.getErrorForeground()

val colorMap = hashMapOf(
  "black" to Color(0x000000),
  "deepskyblue" to Color(0x00bfff),
  "mediumblue" to Color(0x0000cd),
  "darkturquoise" to Color(0x00ced1),
  "mediumspringgreen" to Color(0x00fa9a),
  "blue" to Color(0x0000ff),
  "lime" to Color(0x00ff00),
  "springgreen" to Color(0x00ff7f),
  "aqua" to Color(0x00ffff),
  "cyan" to Color(0x00ffff),
  "dodgerblue" to Color(0x1e90ff),
  "seagreen" to Color(0x2e8b57),
  "darkslategray" to Color(0x2f4f4f),
  "darkslategrey" to Color(0x2f4f4f),
  "mediumseagreen" to Color(0x3cb371),
  "indigo" to Color(0x4b0082),
  "cadetblue" to Color(0x5f9ea0),
  "slateblue" to Color(0x6a5acd),
  "olivedrab" to Color(0x6b8e23),
  "mediumslateblue" to Color(0x7b68ee),
  "lawngreen" to Color(0x7cfc00),
  "chartreuse" to Color(0x7fff00),
  "aquamarine" to Color(0x7fffd4),
  "blueviolet" to Color(0x8a2be2),
  "darkblue" to Color(0x00008b),
  "darkred" to Color(0x8b0000),
  "darkcyan" to Color(0x008b8b),
  "darkmagenta" to Color(0x8b008b),
  "saddlebrown" to Color(0x8b4513),
  "darkseagreen" to Color(0x8fbc8f),
  "yellowgreen" to Color(0x9acd32),
  "lightseagreen" to Color(0x20b2aa),
  "limegreen" to Color(0x32cd32),
  "turquoise" to Color(0x40e0d0),
  "mediumturquoise" to Color(0x48d1cc),
  "mediumaquamarine" to Color(0x66cdaa),
  "navy" to Color(0x000080),
  "skyblue" to Color(0x87ceeb),
  "lightskyblue" to Color(0x87cefa),
  "lightgreen" to Color(0x90ee90),
  "palegreen" to Color(0x98fb98),
  "forestgreen" to Color(0x228b22),
  "darkslateblue" to Color(0x483d8b),
  "darkolivegreen" to Color(0x556b2f),
  "royalblue" to Color(0x4169e1),
  "steelblue" to Color(0x4682b4),
  "darkgreen" to Color(0x006400),
  "cornflowerblue" to Color(0x6495ed),
  "green" to Color(0x008000),
  "teal" to Color(0x008080),
  "mediumpurple" to Color(0x9370d8),
  "darkviolet" to Color(0x9400d3),
  "darkorchid" to Color(0x9932cc),
  "midnightblue" to Color(0x191970),
  "dimgray" to Color(0x696969),
  "dimgrey" to Color(0x696969),
  "slategray" to Color(0x708090),
  "slategrey" to Color(0x708090),
  "lightslategray" to Color(0x778899),
  "lightslategrey" to Color(0x778899),
  "maroon" to Color(0x800000),
  "purple" to Color(0x800080),
  "olive" to Color(0x808000),
  "gray" to Color(0x808080),
  "grey" to Color(0x808080),
  "darkgray" to Color(0xa9a9a9),
  "darkgrey" to Color(0xa9a9a9),
  "brown" to Color(0xa52a2a),
  "sienna" to Color(0xa0522d),
  "lightblue" to Color(0xadd8e6),
  "greenyellow" to Color(0xadff2f),
  "paleturquoise" to Color(0xafeeee),
  "lightsteelblue" to Color(0xb0c4de),
  "powderblue" to Color(0xb0e0e6),
  "darkgoldenrod" to Color(0xb8860b),
  "firebrick" to Color(0xb22222),
  "mediumorchid" to Color(0xba55d3),
  "rosybrown" to Color(0xbc8f8f),
  "darkkhaki" to Color(0xbdb76b),
  "silver" to Color(0xc0c0c0),
  "mediumvioletred" to Color(0xc71585),
  "indianred" to Color(0xcd5c5c),
  "peru" to Color(0xcd853f),
  "tan" to Color(0xd2b48c),
  "lightgray" to Color(0xd3d3d3),
  "lightgrey" to Color(0xd3d3d3),
  "thistle" to Color(0xd8bfd8),
  "chocolate" to Color(0xd2691e),
  "palevioletred" to Color(0xd87093),
  "orchid" to Color(0xda70d6),
  "goldenrod" to Color(0xdaa520),
  "crimson" to Color(0xdc143c),
  "gainsboro" to Color(0xdcdcdc),
  "plum" to Color(0xdda0dd),
  "burlywood" to Color(0xdeb887),
  "lightcyan" to Color(0xe0ffff),
  "lavender" to Color(0xe6e6fa),
  "darksalmon" to Color(0xe9967a),
  "violet" to Color(0xee82ee),
  "palegoldenrod" to Color(0xeee8aa),
  "khaki" to Color(0xf0e68c),
  "aliceblue" to Color(0xf0f8ff),
  "honeydew" to Color(0xf0fff0),
  "azure" to Color(0xf0ffff),
  "sandybrown" to Color(0xf4a460),
  "wheat" to Color(0xf5deb3),
  "beige" to Color(0xf5f5dc),
  "whitesmoke" to Color(0xf5f5f5),
  "mintcream" to Color(0xf5fffa),
  "ghostwhite" to Color(0xf8f8ff),
  "lightcoral" to Color(0xf08080),
  "salmon" to Color(0xfa8072),
  "antiquewhite" to Color(0xfaebd7),
  "linen" to Color(0xfaf0e6),
  "lightgoldenrodyellow" to Color(0xfafad2),
  "oldlace" to Color(0xfdf5e6),
  "red" to Color(0xff0000),
  "fuchsia" to Color(0xff00ff),
  "magenta" to Color(0xff00ff),
  "coral" to Color(0xff7f50),
  "darkorange" to Color(0xff8c00),
  "hotpink" to Color(0xff69b4),
  "deeppink" to Color(0xff1493),
  "orangered" to Color(0xff4500),
  "tomato" to Color(0xff6347),
  "lightsalmon" to Color(0xffa07a),
  "orange" to Color(0xffa500),
  "lightpink" to Color(0xffb6c1),
  "pink" to Color(0xffc0cb),
  "gold" to Color(0xffd700),
  "peachpuff" to Color(0xffdab9),
  "navajowhite" to Color(0xffdead),
  "moccasin" to Color(0xffe4b5),
  "bisque" to Color(0xffe4c4),
  "mistyrose" to Color(0xffe4e1),
  "blanchedalmond" to Color(0xffebcd),
  "papayawhip" to Color(0xffefd5),
  "lavenderblush" to Color(0xfff0f5),
  "seashell" to Color(0xfff5ee),
  "cornsilk" to Color(0xfff8dc),
  "lemonchiffon" to Color(0xfffacd),
  "floralwhite" to Color(0xfffaf0),
  "snow" to Color(0xfffafa),
  "rebeccapurple" to Color(0x663399),
  "yellow" to Color(0xffff00),
  "lightyellow" to Color(0xffffe0),
  "ivory" to Color(0xfffff0),
  "white" to Color(0xffffff)
)

val colorSet = colorMap.keys

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
 * Retrieves the Color instance based on the specified color name and modifiers.
 *
 * @param color the color name with optional modifiers. Format: "colorName[+/-tones]". Examples: "red", "blue+2",
 *    "green-1".
 * @return the Color instance corresponding to the provided color name and modifiers, or null if not found.
 */
fun getColorInstance(color: String): Color? {
  var colorName = color
  var modifier = CharArray(0)

  val lighterIndex = color.indexOf("-")
  if (lighterIndex > 0) {
    colorName = color.substring(0, lighterIndex)
    modifier = color.substring(lighterIndex).toCharArray()
  }

  val darkerIndex = color.indexOf("+")
  if (darkerIndex > 0) {
    colorName = color.substring(0, darkerIndex)
    modifier = color.substring(darkerIndex).toCharArray()
  }

  var myColor = colorMap[colorName]
  var number = ""

  modifier.indices.reversed().forEach { i ->
    val c = modifier[i]
    when {
      Character.isDigit(c) -> number += c
      c == '+'             -> {
        var tones = 1
        if (number.isNotEmpty()) {
          tones = number.toInt()
          number = ""
        }
        myColor = ColorUtil.brighter(myColor!!, tones)
      }

      c == '-'             -> {
        var tones = 1
        if (number.isNotEmpty()) {
          tones = number.toInt()
          number = ""
        }
        myColor = ColorUtil.darker(myColor!!, tones)
      }
    }
  }
  return myColor
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
        OSAgnosticPathUtil.isAbsolute(path) -> file = LocalFileSystem.getInstance().findFileByPath(path)
        currentFile != null                 -> {
          val parent = currentFile.parent
          if (parent != null) {
            file = parent.findFileByRelativePath(path)
          }
        }

        path.startsWith("file://")          -> file = VirtualFileManager.getInstance().findFileByUrl(path)

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
    LOG.error(e)
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
 * Checks whether the given file belongs to the local file system.
 *
 * @param currentFile The file to check.
 * @return `true` if the file is in the local file system, `false` otherwise.
 */
fun isInLocalFileSystem(currentFile: VirtualFile): Boolean = currentFile.fileSystem is LocalFileSystem

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
fun isBlank(cs: CharSequence?): Boolean {
  if (cs == null) return true

  val strLen = cs.length
  if (strLen == 0) return true

  return cs.indices.all { Character.isWhitespace(cs[it]) }
}

/**
 * Retrieves the content of a file specified by the given URL.
 *
 * @param url the URL of the file to retrieve the content from
 * @return the content of the file as a string, or null if the file does not exist or cannot be accessed
 */
fun getFileContent(url: VirtualFile): String? = FileDocumentManager.getInstance().getDocument(url)?.text

/**
 * Retrieves the content of a file specified by the given ownerPath.
 *
 * @param ownerPath the path of the file to retrieve the content from
 * @return the content of the file as a string, or null if the file does not exist or cannot be accessed
 */
fun getFileContent(ownerPath: String): String? {
  val fileByPath = getFileByPath(ownerPath) ?: return null
  return getFileContent(fileByPath)
}

/**
 * Retrieves the virtual file associated with the given URL. Returns `null` if no file is found.
 *
 * @param url The URL of the file.
 * @return The virtual file associated with the given URL, or `null` if no file is found.
 */
fun getFileByUrl(url: String): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl(url)
