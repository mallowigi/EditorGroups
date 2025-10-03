package krasa.editorGroups.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import krasa.editorGroups.index.FileNameIndexService
import krasa.editorGroups.model.*
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.Notifications.notifyTooManyFiles
import krasa.editorGroups.support.RegexFileResolver
import krasa.editorGroups.support.VirtualFileComparator
import krasa.editorGroups.support.getNameWithoutTypeAndExt
import krasa.editorGroups.support.isJarOrZip

@Service(Service.Level.PROJECT)
class AutoGroupProvider(private val project: Project) {
  /**
   * Returns the "Same Folder" group
   *
   * @param file the [VirtualFile] for which the folder group is to be created
   * @return an [EditorGroup] representing the group of files in the same folder as the given file
   */
  fun getFolderGroup(file: VirtualFile): EditorGroup {
    val parent = file.parent

    val regexGroup = RegexGroup(
      RegexGroupModel.from(),
      parent
    )

    val links: List<Link> = RegexFileResolver(project).resolveRegexGroupLinks(regexGroup, file)
    return FolderGroup(folder = parent, links = links, project = project)
  }

  /**
   * Retrieves a group of files that have the same name as the given file.
   *
   * @param currentFile The file for which the same-name group is being retrieved.
   * @return An instance of [EditorGroup] containing the files with the same name.
   */
  fun getSameNameGroup(currentFile: VirtualFile): EditorGroup {
    val nameWithoutExtension = currentFile.nameWithoutExtension
    val start = System.currentTimeMillis()
    val paths = mutableListOf<VirtualFile>()

    runCatching {
      // Get related files
      FileNameIndexService.instance.getVirtualFilesByName(
        nameWithoutExtension,
        true,
        GlobalSearchScope.projectScope(project)
      ).toMutableList()
    }
      .onSuccess { virtualFilesByName ->
        thisLogger().debug("<getVirtualFilesByName=$virtualFilesByName") // NON-NLS

        val groupSizeLimitInt = EditorGroupsSettings.instance.groupSizeLimit
        val size = virtualFilesByName.size

        // collect all files, within the limit
        for (file in virtualFilesByName) {
          if (shouldSkipFile(file)) continue

          if (paths.size == groupSizeLimitInt) {
            notifyTooManyFiles()
            thisLogger().warn("<getSameNameGroup: too many results for $nameWithoutExtension = $size") // NON-NLS
            break
          }

          paths.add(file)
        }

        // Add the current file
        if (currentFile !in paths) paths.add(0, currentFile)

        paths.sortedWith(VirtualFileComparator.INSTANCE)
      }.onFailure { e ->
        when (e) {
          is ProcessCanceledException -> throw e
          else                        -> thisLogger().error(e)
        }

        val vf = LightVirtualFile(INDEXING)
        vf.isValid = false

        paths.add(vf)
      }

    val duration = System.currentTimeMillis() - start
    if (duration > DURATION) thisLogger().warn("getSameNameGroup took ${duration}ms for '$nameWithoutExtension', results: ${paths.size}") // NON-NLS

    thisLogger().debug("getSameNameGroup ${duration}ms for '$nameWithoutExtension', results: ${paths.size}") // NON-NLS

    return SameNameGroup(
      fileNameWithoutExtension = nameWithoutExtension,
      links = Link.fromVirtualFiles(paths, project),
      project = project
    )
  }

  /**
   * Retrieves a group of files that have the same feature as the given file.
   *
   * @param currentFile The file for which the same-name group is being retrieved.
   * @return An instance of [EditorGroup] containing the files with the same feature.
   */
  fun getSameFeatureGroup(currentFile: VirtualFile): EditorGroup {
    val nameWithoutExtension = getNameWithoutTypeAndExt(currentFile.name).toString()
    val start = System.currentTimeMillis()
    val paths = mutableListOf<VirtualFile>()

    runCatching {
      // Get related files
      FileNameIndexService.instance.getVirtualFilesByName(
        nameWithoutExtension,
        true,
        GlobalSearchScope.projectScope(project)
      ).toMutableList()
    }
      .onSuccess { virtualFilesByName ->
        thisLogger().debug("<getVirtualFilesByName=$virtualFilesByName") // NON-NLS

        val groupSizeLimitInt = EditorGroupsSettings.instance.groupSizeLimit
        val size = virtualFilesByName.size

        // collect all files, within the limit
        for (file in virtualFilesByName) {
          if (shouldSkipFile(file)) continue

          if (paths.size == groupSizeLimitInt) {
            notifyTooManyFiles()
            thisLogger().warn("<getSameNameGroup: too many results for $nameWithoutExtension = $size") // NON-NLS
            break
          }

          paths.add(file)
        }

        // Add the current file
        if (currentFile !in paths) paths.add(0, currentFile)

        paths.sortedWith(VirtualFileComparator.INSTANCE)
      }.onFailure { e ->
        when (e) {
          is ProcessCanceledException -> throw e
          else                        -> thisLogger().error(e)
        }

        val vf = LightVirtualFile(INDEXING)
        vf.isValid = false

        paths.add(vf)
      }

    val duration = System.currentTimeMillis() - start
    if (duration > DURATION) thisLogger().warn("getSameNameGroup took ${duration}ms for '$nameWithoutExtension', results: ${paths.size}") // NON-NLS

    thisLogger().debug("getSameNameGroup ${duration}ms for '$nameWithoutExtension', results: ${paths.size}") // NON-NLS

    return SameFeatureGroup(
      fileNameWithoutExtension = nameWithoutExtension,
      links = Link.fromVirtualFiles(paths, project),
      project = project
    )
  }

  private fun shouldSkipFile(virtualFile: VirtualFile): Boolean = when {
    ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile) -> true
    isJarOrZip(virtualFile)                               -> true
    virtualFile.isDirectory                               -> true
    else                                                  -> false
  }

  companion object {
    const val INDEXING: String = "Indexing..."
    const val DURATION: Long = 500

    fun getInstance(project: Project): AutoGroupProvider = project.getService(AutoGroupProvider::class.java)
  }
}