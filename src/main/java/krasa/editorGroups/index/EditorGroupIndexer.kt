package krasa.editorGroups.index

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Pair
import com.intellij.patterns.StringPattern
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import krasa.editorGroups.language.EditorGroupsLanguage
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.EditorGroupIndexValue
import krasa.editorGroups.services.PanelRefresher
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.support.Notifications.indexingWarn
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NonNls
import java.io.File

class EditorGroupIndexer : DataIndexer<String, EditorGroupIndexValue, FileContent> {
  /** The main pattern in the files. */
  @NonNls
  private val mainPattern = MyIndexPattern("@(group)\\.\\w+.*")

  /** Index patterns. */
  @NonNls
  private val indexPatterns: Array<Pair<MyIndexPattern, Consumer>> = arrayOf(
    Pair(MyIndexPattern("^@(group)\\.root\\s(.*)"), RootConsumer()),
    Pair(MyIndexPattern("^@(group)\\.title\\s(.*)"), TitleConsumer()),
    Pair(MyIndexPattern("^@(group)\\.color\\s(.*)"), ColorConsumer()),
    Pair(MyIndexPattern("^@(group)\\.fgcolor\\s(.*)"), FgColorConsumer()),
    Pair(MyIndexPattern("^@(group)\\.related\\s(.*)"), RelatedFilesConsumer()),
    Pair(MyIndexPattern("^@(group)\\.id\\s(.*)"), IdConsumer()),
    Pair(MyIndexPattern("(^@(group)\\.disable.*)"), DisableConsumer())
  )

  /**
   * Returns the indexed map for a given file (usually egroups files)
   *
   * @param inputData the file
   * @return the map of indexed values
   */
  @Suppress("detekt:NestedBlockDepth") // NON-NLS
  override fun map(inputData: FileContent): Map<String, EditorGroupIndexValue> {
    val file = inputData.file
    val isEGroup = EditorGroupsLanguage.isEditorGroupsLanguage(file)
    if (EditorGroupsSettings.instance.isIndexOnlyEditorGroupsFiles && !isEGroup) return emptyMap()

    val ownerPath = file.path
    try {
      // The folder of the egroups file
      val folder: File
      try {
        folder = File(inputData.file.parent.path)
      } catch (_: Exception) {
        return emptyMap()
      }

      var currentGroup: EditorGroupIndexValue? = null
      var lastGroup: EditorGroupIndexValue? = null
      var index = 0
      val map = HashMap<String, EditorGroupIndexValue>()

      // Parses the file contents against the main pattern
      val chars = inputData.contentAsText.toString() // matching strings is faster than HeapCharBuffer
      val input = StringPattern.newBombedCharSequence(chars)
      val optimizedIndexingPattern = mainPattern.optimizedIndexingPattern
      val matcher = optimizedIndexingPattern!!.matcher(input)

      while (matcher.find()) {
        if (matcher.start() != matcher.end()) {
          val line = matcher.group(0).trim { it <= ' ' } // remove whitespaces
          currentGroup = processPatterns(
            inputData = inputData,
            folder = folder,
            group = currentGroup,
            line = line
          )

          if (lastGroup != null && lastGroup !== currentGroup) {
            index = add(
              inputData = inputData,
              ownerPath = ownerPath,
              lastGroup = lastGroup,
              index = index,
              map = map
            )
          }

          lastGroup = currentGroup
        }
      }

      if (currentGroup != null) {
        add(
          inputData = inputData,
          ownerPath = ownerPath,
          lastGroup = currentGroup,
          index = index,
          map = map
        )
      }

      return map
    } catch (_: DisableException) {
      // if a group is declared disabled, remove it from the index
      IndexCache.getInstance(inputData.project).removeGroup(ownerPath)
      return emptyMap()
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (e: Exception) {
      thisLogger().error(e)
      return emptyMap()
    }
  }

  fun add(
    inputData: FileContent,
    ownerPath: String,
    lastGroup: EditorGroupIndexValue,
    index: Int,
    map: HashMap<String, EditorGroupIndexValue>
  ): Int {
    var myLastGroup = lastGroup
    var myIndex = index

    myLastGroup.ownerPath = ownerPath
    if (StringUtils.isEmpty(myLastGroup.id)) {
      myLastGroup.id = ownerPath + ";" + myIndex++
    }

    if (StringUtils.isEmpty(myLastGroup.root)) {
      myLastGroup.root = ownerPath
    }

    try {
      myLastGroup = PanelRefresher.getInstance(inputData.project).onIndexingDone(ownerPath, myLastGroup)
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (e: Exception) {
      thisLogger().error(e)
    }

    if (map.containsKey(myLastGroup.id)) {
      indexingWarn(inputData.project, inputData.file, message("duplicate.group.id.0", myLastGroup.id))
    } else {
      map[myLastGroup.id] = myLastGroup
    }
    return myIndex
  }

  private fun processPatterns(
    inputData: FileContent,
    folder: File?,
    group: EditorGroupIndexValue?,
    line: CharSequence
  ): EditorGroupIndexValue? {
    var result = group

    for (indexPattern in indexPatterns) {
      val pattern = indexPattern.first.optimizedIndexingPattern
      val consumer = indexPattern.second

      if (pattern == null) continue
      val subMatcher = pattern.matcher(line)

      while (subMatcher.find()) {
        if (subMatcher.start() != subMatcher.end()) {
          result = consumer.consume(
            inputData = inputData,
            groupIndexValue = result,
            folder = folder,
            value = subMatcher.group(INDEX_PATTERN_GROUP).trim { it <= ' ' }
          )
        }
      }
    }
    return result
  }

  abstract class Consumer {
    fun init(value: EditorGroupIndexValue?): EditorGroupIndexValue = value ?: EditorGroupIndexValue()

    /**
     * Processes the given input data and returns an updated or new EditorGroupIndexValue.
     *
     * @param inputData the contents of the egroups file
     * @param groupIndexValue the current index value of the editor group, may be null
     * @param folder the folder containing the file, may be null
     * @param value the string value to process
     * @return the updated or new EditorGroupIndexValue, or null if the input data is null
     */
    abstract fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue?
  }

  /** Title consumer: Sets the title of the editor group. */
  internal class TitleConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue {
      val result = init(groupIndexValue)
      result.title = value
      return result
    }
  }

  /** Root consumer: Sets the root flag of the editor group. */
  internal class RootConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue {
      val result = init(groupIndexValue)
      result.root = value
      return result
    }
  }

  /** Color consumer: Sets the background color of the editor group. */
  internal class ColorConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue {
      val result = init(groupIndexValue)
      result.backgroundColor = value
      return result
    }
  }

  /** FgColor consumer: Sets the foreground color of the editor group. */
  internal class FgColorConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue {
      val result = init(groupIndexValue)
      result.foregroundColor = value
      return result
    }
  }

  /** Id consumer: Sets the id of the editor group. Moreover, if the group doesn't have a title defined, sets the id as the title. */
  internal class IdConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue {
      var result = init(groupIndexValue)
      if (StringUtils.isNotEmpty(result.id)) result = EditorGroupIndexValue()
      if (StringUtils.isEmpty(result.title)) result.title = value

      result.id = value
      return result
    }
  }

  /** Related files consumer: Adds a related path to the editor group's related files. */
  internal class RelatedFilesConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue? {
      var result = groupIndexValue
      if (StringUtils.isBlank(value)) return result

      result = init(result)
      result.addRelatedPath(value)
      return result
    }
  }

  /** Disable consumer: throws a DisableException, thus discarding the group. */
  private class DisableConsumer : Consumer() {
    override fun consume(
      inputData: FileContent?,
      groupIndexValue: EditorGroupIndexValue?,
      folder: File?,
      value: String
    ): EditorGroupIndexValue = throw DisableException()
  }

  internal class DisableException : RuntimeException()

  companion object {
    const val INDEX_PATTERN_GROUP: Int = 2
  }
}