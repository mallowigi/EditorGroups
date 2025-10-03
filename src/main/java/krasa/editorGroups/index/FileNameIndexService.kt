package krasa.editorGroups.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndex.ValueProcessor

@Service(Service.Level.APP)
class FileNameIndexService {
  /**
   * Retrieves a collection of virtual files by their name within a given project scope.
   *
   * @param name the name of the files to search for.
   * @param scope the scope within which to search for the files.
   * @return a mutable collection of virtual files that match the specified name and scope.
   */
  fun getVirtualFilesByName(name: String, scope: GlobalSearchScope): MutableCollection<VirtualFile> {
    val files: MutableSet<VirtualFile> = mutableSetOf()

    FileBasedIndex.getInstance()
      .processValues<String?, Void?>(
        FilenameWithoutExtensionIndex.NAME,
        name,
        null,
        ValueProcessor { file: VirtualFile?, _: Void? ->
          files.add(file!!)
          true
        },
        scope,
        null
      )
    return files
  }

  /**
   * Retrieves a collection of virtual files by their name, ignoring case sensitivity, within a given project scope.
   *
   * @param name the name of the files to search for.
   * @param scope the scope within which to search for the files.
   * @return a mutable set of virtual files that match the specified name (ignoring case) and scope.
   */
  private fun getVirtualFilesByNameIgnoringCase(name: String, scope: GlobalSearchScope): MutableSet<VirtualFile> {
    val keys: MutableSet<String> = mutableSetOf()

    // Retrieve all files related with name, ignoring case
    processAllFileNames(
      { fileName: String? ->
        if (name.equals(fileName, ignoreCase = true)) keys.add(fileName!!)
        true
      },
      scope
    )

    // values accessed outside of processAllKeys
    val files: MutableSet<VirtualFile> = mutableSetOf()
    keys.forEach { key -> files.addAll(getVirtualFilesByName(name = key, scope = scope)) }
    return files
  }

  /**
   * Retrieves a collection of virtual files by their name within a given project scope.
   *
   * @param name the name of the files to search for.
   * @param caseSensitively whether the search should be case-sensitive.
   * @param scope the scope within which to search for the files.
   * @return a mutable collection of virtual files that match the specified name and scope.
   */
  fun getVirtualFilesByName(name: String, caseSensitively: Boolean, scope: GlobalSearchScope): MutableCollection<VirtualFile> = when {
    caseSensitively -> getVirtualFilesByName(name, scope)
    else            -> getVirtualFilesByNameIgnoringCase(name, scope)
  }

  /**
   * Processes all file names within the specified scope using the given processor.
   *
   * @param processor the processor to be applied to each file name.
   * @param scope the scope within which to search for the file names.
   */
  private fun processAllFileNames(processor: Processor<String?>, scope: GlobalSearchScope) {
    FileBasedIndex.getInstance().processAllKeys<String?>(
      FilenameWithoutExtensionIndex.NAME,
      processor,
      scope,
      null
    )
  }

  companion object {
    @JvmStatic
    val instance: FileNameIndexService by lazy { service() }
  }
}