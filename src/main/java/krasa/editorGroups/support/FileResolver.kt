package krasa.editorGroups.support

import com.intellij.ide.actions.OpenFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import krasa.editorGroups.index.FileNameIndexService
import krasa.editorGroups.language.EditorGroupsLanguage
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.EditorGroupIndexValue
import krasa.editorGroups.model.Link
import krasa.editorGroups.settings.EditorGroupsSettings
import org.apache.commons.io.IOCase
import org.apache.commons.io.filefilter.FileFileFilter
import org.apache.commons.io.filefilter.PrefixFileFilter
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
import java.io.IOException
import java.util.*

open class FileResolver {
  protected val project: Project
  protected val excludeEditorGroupsFiles: Boolean
  private val links: MutableSet<String>

  constructor(project: Project) {
    this.project = project
    excludeEditorGroupsFiles = EditorGroupsSettings.instance.isExcludeEditorGroupsFiles

    links = object : LinkedHashSet<String>() {
      override fun add(element: String): Boolean = super.add(sanitize(element))
    }
  }

  fun getLinks(): Set<String> = links

  @Suppress("detekt:TooGenericExceptionThrown") // NON-NLS
  private fun resolve(ownerFilePath: String, root: String?, relatedPaths: List<String>, group: EditorGroupIndexValue): List<Link> {
    try {
      return resolve2(ownerFilePath, root, relatedPaths, group)
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  @Throws(IOException::class)
  @Suppress("detekt:MagicNumber") // NON-NLS
  private fun resolve2(ownerFilePath: String?, root: String?, relatedPaths: List<String>, group: EditorGroupIndexValue): List<Link> {
    val start = System.currentTimeMillis()
    val ownerFile = getNullableFileByPath(ownerFilePath)
    val rootFolder = resolveRootFolder(ownerFilePath, root, group, ownerFile)

    if (ownerFilePath != null) {
      add(File(ownerFilePath), false)
    }

    for (filePath in relatedPaths) {
      ProgressManager.checkCanceled()
      val newStart = System.currentTimeMillis()

      try {
        val sanitizedFilePath = sanitize(useMacros(ownerFile, filePath))

        when {
          sanitizedFilePath.startsWith("*/") && sanitizedFilePath.endsWith(".*") -> resolveSameNameProjectFiles(sanitizedFilePath)
          sanitizedFilePath.startsWith("*/")                                     -> resolveProjectFiles(sanitizedFilePath)
          sanitizedFilePath.contains("**") && sanitizedFilePath.contains("*.")   -> resolveGlobExtensions(sanitizedFilePath)
          sanitizedFilePath.contains("**")                                       -> resolveGlobFiles(sanitizedFilePath)
          FileUtil.isAbsolute(sanitizedFilePath)                                 -> resolve(File(sanitizedFilePath))
          else                                                                   -> resolve(File(rootFolder, sanitizedFilePath))
        }
      } catch (e: TooManyFilesException) {
        e.showNotification()
        thisLogger().warn("TooManyFilesException filePath='$filePath rootFolder=$rootFolder, group = [$group]") // NON-NLS
        thisLogger().debug(e)
      }

      val delta = System.currentTimeMillis() - newStart

      if (delta > 100) {
        thisLogger().debug("resolveLink $filePath ${delta}ms") // NON-NLS
      }
    }

    thisLogger().debug("<resolveLinks ${System.currentTimeMillis() - start}ms links=$links") // NON-NLS

    return Link.from(links, project)
  }

  private fun resolveRootFolder(ownerFilePath: String?, root: String?, group: EditorGroupIndexValue, ownerFile: VirtualFile?): String {
    var newRoot = root ?: ""

    if (newRoot.startsWith("..")) {
      val file = File(ownerFilePath?.let { File(it).parentFile }, newRoot)

      thisLogger().debug("root $file exists=${file.exists()}") // NON-NLS

      newRoot = getCanonicalPath(file)
    }

    newRoot = useMacros(ownerFile, newRoot)

    val rootFile = File(newRoot)
    if (!rootFile.exists()) {
      Notifications.showWarning(
        message("notifications.root.does.not.exist", newRoot, ownerFile?.let { Notifications.href(it) }.toString(), group),
        object : NotificationAction("") {
          override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            ownerFile?.let { OpenFileAction.openFile(it, project) }
            notification.expire()
          }
        }
      )
    }

    return if (rootFile.isFile) rootFile.parent else newRoot
  }

  @Throws(IOException::class)
  private fun resolveSameNameProjectFiles(filePath: String?) {
    var sanitizedPath = (filePath ?: return).substring("*/".length)
    sanitizedPath = StringUtils.substringBefore(sanitizedPath, ".*")

    var fileName = sanitizedPath
    if (fileName.contains("/")) {
      fileName = StringUtils.substringAfterLast(fileName, "/")
    }

    val virtualFilesByName = FileNameIndexService.instance.getVirtualFilesByName(
      fileName,
      !SystemInfo.isWindows,
      GlobalSearchScope.allScope(project)
    )

    for (file in virtualFilesByName) {
      val canonicalPath = file.path
      if (StringUtils.substringBeforeLast(file.path, ".").endsWith(sanitizedPath)) {
        add(canonicalPath)
      }
    }
  }

  @Throws(IOException::class)
  protected fun resolveProjectFiles(filePath: String?) {
    val sanitizedPath = (filePath ?: return).substring("*/".length)

    var fileName = sanitizedPath
    if (fileName.contains("/")) {
      fileName = StringUtils.substringAfterLast(fileName, "/")
    }

    val virtualFilesByName = FilenameIndex.getVirtualFilesByName(
      fileName,
      !SystemInfo.isWindows,
      GlobalSearchScope.allScope(project)
    )

    for (file in virtualFilesByName) {
      val canonicalPath = file.path
      if (canonicalPath.endsWith(sanitizedPath)) {
        add(canonicalPath)
      }
    }
  }

  @Throws(IOException::class)
  protected fun resolveGlobExtensions(filePath: String?) {
    val sanitizedPath = (filePath ?: return).substring("*/".length)

    val parentDir = StringUtils.substringBeforeLast(sanitizedPath, "**")
    var fileName = sanitizedPath
    if (fileName.contains("/")) {
      fileName = StringUtils.substringAfterLast(fileName, "/")
    }
    val extension = StringUtils.substringAfterLast(fileName, ".")

    val virtualFilesByName = FilenameIndex.getAllFilesByExt(
      project,
      extension,
      GlobalSearchScope.allScope(project)
    )

    for (file in virtualFilesByName) {
      val canonicalPath = file.path
      if (canonicalPath.contains(parentDir)) {
        add(canonicalPath)
      }
    }
  }

  @Throws(IOException::class)
  protected fun resolveGlobFiles(filePath: String?) {
    val sanitizedPath = (filePath ?: return).substring("*/".length)

    var fileName = sanitizedPath
    if (fileName.contains("/")) {
      fileName = StringUtils.substringAfterLast(fileName, "/")
    }

    val virtualFilesByName = FilenameIndex.getVirtualFilesByName(
      fileName,
      !SystemInfo.isWindows,
      GlobalSearchScope.allScope(project)
    )

    for (file in virtualFilesByName) {
      val canonicalPath = file.path
      if (canonicalPath.endsWith(sanitizedPath)) {
        add(canonicalPath)
      }
    }
  }

  @Throws(IOException::class)
  protected fun resolve(file: File) {
    when {
      file.isFile      -> add(file, true)
      file.isDirectory -> addChildren(file)
      else             -> addMatching(file)
    }
  }

  @Throws(IOException::class)
  protected fun addChildren(parentDir: File) {
    val foundFiles = checkNotNull(parentDir.listFiles(FileFileFilter.INSTANCE as FileFilter))
    for (foundFile in foundFiles) {
      add(foundFile, false)
    }
  }

  @Throws(IOException::class)
  protected fun addMatching(file: File) {
    val parentDir = file.parentFile
    // could be some shit like '/*', do not use canonical path
    val path = sanitize(file.absolutePath)
    val fileName = StringUtils.substringAfterLast(path, "/")

    if (!fileName.isEmpty() && !file.isDirectory && parentDir.isDirectory) {
      val builder = WildcardFileFilter.builder()
      builder.setWildcards(fileName)
      builder.setIoCase(IOCase.SYSTEM)
      val filter = builder.get() as FileFilter

      var foundFiles = parentDir.listFiles(filter)
      Objects.requireNonNull(foundFiles)
        .asSequence()
        .filter { it.isFile }
        .forEach { add(it, false) }

      if (foundFiles == null) return

      if (foundFiles.size == 0) {
        foundFiles = parentDir.listFiles(PrefixFileFilter("$fileName.", IOCase.SYSTEM) as FilenameFilter)
        for (f in Objects.requireNonNull(foundFiles)) add(f, false)
      }
    }
  }

  @Throws(IOException::class)
  protected fun add(canonicalPath: String) {
    add(File(canonicalPath), false)
  }

  @Throws(IOException::class)
  protected fun add(file: File, definedManually: Boolean) {
    if (links.size > EditorGroupsSettings.instance.groupSizeLimit) throw TooManyFilesException()

    if (file.isFile && !(!definedManually && excluded(file, excludeEditorGroupsFiles))) {
      links.add(getCanonicalPath(file))
    }
  }

  protected fun useMacros(virtualFile: VirtualFile?, folder: String): String {
    when {
      folder.startsWith(PROJECT)                       -> {
        val baseDir = project.guessProjectDir()
        val canonicalPath = baseDir?.canonicalPath
        return folder.replace(PROJECT_REGEX.toRegex(), canonicalPath ?: "")
      }

      virtualFile != null && folder.startsWith(MODULE) -> {
        val moduleForFile = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(virtualFile)
        val moduleDirPath = ModuleUtilCore.getModuleDirPath(moduleForFile!!)
        return folder.replace(MODULE_REGEX.toRegex(), moduleDirPath)
      }

      else                                             -> return folder
    }
  }

  companion object {
    const val PROJECT_REGEX: String = "^PROJECT"
    const val PROJECT: String = "PROJECT"
    const val MODULE_REGEX: String = "^MODULE"
    const val MODULE: String = "MODULE"

    @Throws(ProcessCanceledException::class)
    fun resolveLinks(group: EditorGroupIndexValue, project: Project): List<Link> {
      thisLogger().debug("<resolveLinks [$group], project = [${project.name}]") // NON-NLS

      return resolveLinks(
        project = project,
        ownerFilePath = group.ownerPath,
        root = group.root,
        relatedPaths = group.relatedPaths,
        group = group
      )
    }

    fun resolveLinks(
      project: Project,
      ownerFilePath: String,
      root: String?,
      relatedPaths: List<String>,
      group: EditorGroupIndexValue,
    ): List<Link> {
      thisLogger().debug("<resolveLinks ownerFilePath=$ownerFilePath, root=$root, relatedPaths=$relatedPaths, group = [$group]") // NON-NLS

      return FileResolver(project).resolve(
        ownerFilePath = ownerFilePath,
        root = root,
        relatedPaths = relatedPaths,
        group = group
      )
    }

    protected fun sanitize(filePath: String?): String {
      var replace = filePath!!.replace("\\", "/")
      // file path starting with // causes major delays for some reason
      replace = replace.replaceFirst("/+".toRegex(), "/")
      return replace
    }

    @JvmStatic
    fun excluded(file: File, excludeEditorGroupsFiles: Boolean): Boolean = when {
      excludeEditorGroupsFiles && EditorGroupsLanguage.isEditorGroupsLanguage(getCanonicalPath(file)) -> true
      else                                                                                            -> FileUtil.isJarOrZip(file)
    }
  }
}