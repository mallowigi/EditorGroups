package krasa.editorGroups.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import krasa.editorGroups.model.*
import krasa.editorGroups.services.ExternalGroupProvider
import krasa.editorGroups.services.PanelRefresher
import krasa.editorGroups.services.RegexGroupProvider
import krasa.editorGroups.settings.EditorGroupProjectStorage
import krasa.editorGroups.settings.EditorGroupProjectStorage.KeyValuePair
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.FileResolver
import krasa.editorGroups.support.Notifications
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class IndexCache(private val project: Project) {

  private val groupsByLinks: MutableMap<String, EditorGroups> = ConcurrentHashMap()
  private val config = EditorGroupsSettings.instance
  private val externalGroupProvider = ExternalGroupProvider.getInstance(project)

  @get:Throws(IndexNotReadyException::class)
  val allGroups: List<EditorGroupIndexValue>
    get() {
      val fileBasedIndex = FileBasedIndex.getInstance()
      val scope = GlobalSearchScope.projectScope(project)
      val all = mutableListOf<EditorGroupIndexValue>()

      // Process index keys
      fileBasedIndex.getAllKeys(EditorGroupIndex.NAME, project)
        .forEach {
          fileBasedIndex.processValues(
            EditorGroupIndex.NAME,
            it,
            null,
            { _: VirtualFile?, value: EditorGroupIndexValue ->
              all.add(value)
              true
            },
            scope
          )
        }

      return all
    }

  val state: EditorGroupProjectStorage.State
    get() {
      val state = EditorGroupProjectStorage.State()
      val autoSameName = config.isAutoSameName
      val autoSameFeature = config.isAutoSameFeature
      val autoFolders = config.isAutoFolders

      groupsByLinks.entries.forEach { (key, value) ->
        val last = value.last
        when {
          last == null                                      -> return@forEach
          autoSameFeature && last == AutoGroup.SAME_FEATURE -> return@forEach
          autoSameName && last == AutoGroup.SAME_FILE_NAME  -> return@forEach
          autoFolders && last == AutoGroup.DIRECTORY        -> return@forEach
        }

        if (state.lastGroup.size > MAX_HISTORY_SIZE) return@forEach

        // Keep a history of the last group
        state.lastGroup.add(KeyValuePair(key, last))
      }
      return state
    }

  /** Return the EditorGroups of the given path. */
  fun getOwningOrSingleGroup(canonicalPath: String): EditorGroup {
    var result: EditorGroup = EditorGroup.EMPTY
    val editorGroups = groupsByLinks[canonicalPath] ?: return result
    val values = editorGroups.all

    result = when (values.size) {
      1    -> values.first()
      else -> getMatchingGroup(values, canonicalPath)
    }

    // init
    result.getLinks(project)

    thisLogger().debug("<getOwningOrSingleGroup result = $result") // NON-NLS

    return result
  }

  private fun getMatchingGroup(values: Collection<EditorGroup>, canonicalPath: String): EditorGroup {
    val matchedValues = values.filter { it.ownerPath == canonicalPath }
    return when (matchedValues.size) {
      1    -> matchedValues.first()
      else -> EditorGroup.EMPTY // More than one or no matching group, return empty
    }
  }

  /**
   * Retrieves the [EditorGroup] from the index based on the provided id.
   *
   * @param id The id of the [EditorGroup].
   * @return The [EditorGroup] with the matching id.
   */
  private fun getById(id: String): EditorGroup {
    val result = getGroupFromIndexById(id)
    result.getLinks(project)

    return result
  }

  /** Clear the cache. */
  fun clear(): Unit = groupsByLinks.clear()

  /**
   * Validates the given [group]. This method checks if the given [group] is valid and performs additional validation based on the type of
   * group.
   *
   * @param group The [EditorGroup] to validate.
   */
  fun validate(group: EditorGroup) {
    if (group.isInvalid) return

    if (group is EditorGroupIndexValue) {
      val id = group.id
      try {
        val groupFromIndex = getGroupFromIndexById(id)
        if (groupFromIndex != group) {
          group.invalidate()
          return
        }
      } catch (e: ProcessCanceledException) {
        throw e
      } catch (_: IndexNotReadyException) {
      }
    }

    val valid = group.isValid

    thisLogger().debug("<validate $valid") // NON-NLS
  }

  /**
   * Retrieves the EditorGroup from the index based on the provided id.
   *
   * @param id The id of the EditorGroup.
   * @return The EditorGroup with the matching id.
   */
  private fun getGroupFromIndexById(id: String): EditorGroup {
    val values = FileBasedIndex.getInstance().getValues(
      /* indexId = */
      EditorGroupIndex.NAME,
      /* dataKey = */
      id,
      /* filter = */
      GlobalSearchScope.projectScope(project)
    )

    if (values.size > 1) {
      Notifications.notifyDuplicateId(project, id, values)
    }

    val editorGroup = when {
      values.isEmpty() -> EditorGroup.EMPTY
      else             -> values[0]
    }

    thisLogger().debug("<getGroupFromIndexById $editorGroup") // NON-NLS

    return editorGroup
  }

  /**
   * Adds an [EditorGroupIndexValue] to the [groupsByLinks] map.
   *
   * @param group The [EditorGroupIndexValue] to add.
   * @param path The path to associate with the [EditorGroupIndexValue].
   */
  private fun addToCache(group: EditorGroupIndexValue, path: String) {
    // Retrieve or add an editorGroup to the map, then add this group to it
    val editorGroups = groupsByLinks.getOrPut(path) { EditorGroups() }
    editorGroups.add(group)
  }

  /**
   * Handles the completion of indexing for a specific editor group.
   *
   * @param ownerPath The path of the owner of the group.
   * @param group The editor group that has finished indexing.
   * @return The updated editor group.
   */
  fun onIndexingDone(ownerPath: String, group: EditorGroupIndexValue): EditorGroupIndexValue {
    val updatedGroup = groupsByLinks[ownerPath]
      ?.getById(group.id)
      .takeIf { it == group } as? EditorGroupIndexValue

    return updatedGroup ?: run {
      initGroup(group)
      group
    }
  }

  /**
   * Initializes the given group by adding it to the groupsByLinks map, setting its links, and adding its related paths.
   *
   * @param group The EditorGroupIndexValue to initialize.
   * @throws ProcessCanceledException if the process is canceled.
   */
  @Throws(ProcessCanceledException::class)
  fun initGroup(group: EditorGroupIndexValue) {
    thisLogger().debug("<initGroup = [$group]") // NON-NLS

    if (!group.exists()) return
    // Add the group to the cache
    addToCache(group, group.ownerPath)

    val resolvedLinks = FileResolver.resolveLinks(group, project)
    group.setLinks(resolvedLinks)

    // Add the group to the cache for each resolved link
    resolvedLinks.forEach { addToCache(group, it.path) }
  }

  /**
   * Retrieves the last editor group based on the provided parameters.
   *
   * @param currentFile The VirtualFile currently open in the editor.
   * @param currentFilePath The path of the current file.
   * @param includeAutoGroups Whether to include auto groups in the result.
   * @param includeFavorites Whether to include favorite groups in the result.
   * @param stub Whether to return a stub group if no other valid group is found.
   * @return The last EditorGroup based on the provided parameters.
   */
  fun getLastEditorGroup(
    currentFile: VirtualFile,
    currentFilePath: String,
    includeAutoGroups: Boolean,
    includeFavorites: Boolean,
    stub: Boolean
  ): EditorGroup {
    var result = EditorGroup.EMPTY
    if (!config.isRememberLastGroup) {
      thisLogger().debug("<getLastEditorGroup $result (isRememberLastGroup=false)") // NON-NLS
      return result
    }

    val groups = groupsByLinks[currentFilePath]
    if (groups != null) {
      val last = groups.last
      thisLogger().debug("last = $last") // NON-NLS

      if (last != null && config.isRememberLastGroup) {
        result = getResultGroup(
          last = last,
          includeAutoGroups = includeAutoGroups,
          includeFavorites = includeFavorites,
          stub = stub,
          currentFile = currentFile
        )
      }

      if (result.isInvalid) {
        result = getMultiGroup(currentFile)
      }
    }

    thisLogger().debug("<getLastEditorGroup $result") // NON-NLS

    return result
  }

  /**
   * Returns the appropriate [EditorGroup] based on the provided parameters.
   *
   * @param last The last group identifier.
   * @param includeAutoGroups Whether to include auto groups in the result.
   * @param includeFavorites Whether to include favorite groups in the result.
   * @param stub Whether to return a stub group if no other valid group is found.
   * @param currentFile The VirtualFile currently open in the editor.
   * @return The calculated EditorGroup based on the input parameters.
   */
  @Suppress("detekt:CyclomaticComplexMethod") // NON-NLS
  private fun getResultGroup(
    last: String,
    includeAutoGroups: Boolean,
    includeFavorites: Boolean,
    stub: Boolean,
    currentFile: VirtualFile
  ): EditorGroup {
    return when {
      includeAutoGroups && config.isAutoSameName && last == AutoGroup.SAME_FILE_NAME  -> SameNameGroup.INSTANCE
      includeAutoGroups && config.isAutoFolders && last == AutoGroup.DIRECTORY        -> FolderGroup.INSTANCE
      includeAutoGroups && config.isAutoSameFeature && last == AutoGroup.SAME_FEATURE -> SameFeatureGroup.INSTANCE
      last == HidePanelGroup.ID                                                       -> HidePanelGroup.INSTANCE

      includeFavorites && last.startsWith(RegexGroup.ID_PREFIX)                       -> {
        val groupName = last.substring(RegexGroup.ID_PREFIX.length)
        RegexGroupProvider.getInstance(project).findRegexGroup(currentFile, groupName)
      }

      includeFavorites && last == BookmarksGroup.ID_PREFIX                            ->
        externalGroupProvider.defaultBookmarkGroup

      stub                                                                            -> StubGroup()

      else                                                                            -> {
        val lastGroup = getById(last)
        if (lastGroup.containsLink(project, currentFile) || lastGroup.isOwner(currentFile.path)) lastGroup else EditorGroup.EMPTY
      }
    }
  }

  /**
   * Finds all EditorGroups associated with the given currentFile.
   *
   * @param currentFile The [VirtualFile] for which [EditorGroups] are to be found.
   * @return A List of [EditorGroup] objects found for the currentFile.
   */
  fun findGroups(currentFile: VirtualFile): List<EditorGroup> {
    val result = mutableListOf<EditorGroup>()

    groupsByLinks[currentFile.path]?.let {
      it.validate(this)
      result.addAll(it.all)
    }

    result.addAll(externalGroupProvider.findGroups(currentFile))

    thisLogger().debug("<findGroups $result") // NON-NLS
    return result
  }

  /**
   * Retrieves the EditorGroup associated with the given VirtualFile.
   *
   * @param currentFile The [VirtualFile] for which the [EditorGroup] is to be retrieved.
   * @return The [EditorGroup] corresponding to the currentFile. Returns an empty [EditorGroup] if no groups are found.
   */
  fun getMultiGroup(currentFile: VirtualFile): EditorGroup {
    var result = EditorGroup.EMPTY
    val favouriteGroups = findGroups(currentFile)

    when {
      favouriteGroups.size == 1 -> result = favouriteGroups[0]
      favouriteGroups.size > 1  -> result = EditorGroups(favouriteGroups)
    }

    thisLogger().debug("<getMultiGroup $result") // NON-NLS

    return result
  }

  /** called very often! */
  fun getEditorGroupForColor(currentFile: VirtualFile): EditorGroup {
    var result = EditorGroup.EMPTY
    val groups = groupsByLinks[currentFile.path] ?: return result
    val last = groups.last

    if (last != null && shouldUseLastGroup(last, currentFile)) {
      result = groups.getById(last)
    }

    if (result.isInvalid) {
      result = groups.ownerOrLast(currentFile.path)
    }

    return result
  }

  private fun shouldUseLastGroup(last: String?, currentFile: VirtualFile): Boolean {
    if (last == null || !config.isRememberLastGroup) return false

    val lastEditorGroups = groupsByLinks[last]
    val lastGroup = lastEditorGroups?.getById(last) ?: return false

    return lastGroup.isValid && lastGroup.containsLink(project, currentFile)
  }

  fun setLast(currentFile: String, result: EditorGroup) {
    if (!result.isValid || result.isStub) return

    val editorGroups = groupsByLinks.putIfAbsent(currentFile, EditorGroups()) ?: EditorGroups().also {
      it.add(result)
      groupsByLinks[currentFile] = it
    }

    editorGroups.last = result.id
  }

  fun getLast(currentFilePath: String): String? = groupsByLinks[currentFilePath]?.last

  fun loadState(state: EditorGroupProjectStorage.State) {
    state.lastGroup.forEach { (key, value) ->
      val editorGroups = EditorGroups()
      groupsByLinks[key] = editorGroups
      editorGroups.last = value
    }
  }

  /**
   * Removes all editor groups associated with the provided owner path.
   *
   * @param ownerPath The path of the owner for which editor groups need to be removed.
   */
  fun removeGroup(ownerPath: String) {
    groupsByLinks.values.forEach { editorGroups ->
      editorGroups.all
        .filter { it.isOwner(ownerPath) }
        .forEach { editorGroup ->
          thisLogger().debug("removeGroup invalidating $editorGroup") // NON-NLS

          editorGroup.invalidate()

          editorGroup.getLinks(project).forEach { link ->
            groupsByLinks[link.path]?.remove(editorGroup)
          }
        }
    }

    PanelRefresher.getInstance(project).refresh(ownerPath)
  }

  companion object {
    const val MAX_HISTORY_SIZE: Int = 1000

    @JvmStatic
    fun getInstance(project: Project): IndexCache = project.getService(IndexCache::class.java)
  }
}
