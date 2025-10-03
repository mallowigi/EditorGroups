package krasa.editorGroups.support

import com.intellij.filename.UniqueNameBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.model.Link
import krasa.editorGroups.model.VirtualFileLink

/** Data Structure to handle a list of files, with possible duplicates. */
class UniqueTabNameBuilder(project: Project) {
  private var root: String? = project.basePath

  init {
    this.root = when (this.root) {
      null -> ""
      else -> FileUtil.toSystemIndependentName(this.root!!)
    }
  }

  fun getNamesByPath(paths: List<Link>, currentFile: VirtualFile?, project: Project): MutableMap<Link, String> {
    val uniqueNameBuilder = UniqueNameBuilder<Link>(root, "/")
    val pathToName: MutableMap<Link, String> = HashMap()
    val nameToPath: MutableMap<String, Link> = HashMap()
    val pathsWithDuplicateName: MutableSet<Link> = HashSet()

    // Add all links to the maps
    paths.forEach { link ->
      put(
        pathToName = pathToName,
        nameToPath = nameToPath,
        pathsWithDuplicateName = pathsWithDuplicateName,
        link = link
      )
    }

    // Add current file if not already
    if (currentFile != null) {
      val currentFilePath = currentFile.path
      val containsCurrentFile = pathToName.keys.stream().anyMatch { link1: Link? -> link1!!.path == currentFilePath }

      if (!containsCurrentFile) {
        val link: Link = VirtualFileLink(currentFile, project)
        put(
          pathToName = pathToName,
          nameToPath = nameToPath,
          pathsWithDuplicateName = pathsWithDuplicateName,
          link = link
        )
      }
    }

    for (link in pathsWithDuplicateName) {
      // For duplicates, add the path
      uniqueNameBuilder.addPath(link, link.path)

      // Replace the entry with the non duplicated entry
      val uniqueName = uniqueNameBuilder.getShortPath(link)
      pathToName[link] = uniqueName
    }
    return pathToName
  }

  private fun put(
    pathToName: MutableMap<Link, String>,
    nameToPath: MutableMap<String, Link>,
    pathsWithDuplicateName: MutableSet<Link>,
    link: Link
  ) {
    val name = link.name

    val duplicatePath = nameToPath[name]
    if (duplicatePath != null) {
      pathsWithDuplicateName.add(duplicatePath)
      pathsWithDuplicateName.add(link)
    }

    pathToName[link] = name

    nameToPath[name] = link
  }
}