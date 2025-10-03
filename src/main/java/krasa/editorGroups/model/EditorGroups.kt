package krasa.editorGroups.model

import com.intellij.openapi.project.Project
import krasa.editorGroups.icons.EditorGroupsIcons
import krasa.editorGroups.index.IndexCache
import krasa.editorGroups.messages.EditorGroupsBundle.message
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

class EditorGroups : EditorGroup, GroupsHolder {

  private val groupsMap: MutableMap<String, EditorGroup> = ConcurrentHashMap()
  var last: String? = null

  val all: Collection<EditorGroup>
    get() = groupsMap.values

  override val id: String
    get() = ID

  override val switchDescription: String
    get() = message("owner.0", ownerPath)

  override val title: String = ""

  override val isValid: Boolean = true

  override val isCustom: Boolean = true

  override val groups: Collection<EditorGroup>
    get() = groupsMap.values

  constructor()

  /** no filtering by type */
  constructor(editorGroups: List<EditorGroup>) {
    editorGroups.forEach { addGroup(it) }
  }

  /** no filtering by type */
  constructor(result: EditorGroup, editorGroups: List<EditorGroup>) {
    addGroup(result)
    editorGroups.forEach { group -> addGroup(group) }
  }

  private fun addGroup(group: EditorGroup) {
    when (group) {
      is GroupsHolder -> group.groups.toList().associateByTo(groupsMap, EditorGroup::id)

      else            -> groupsMap[group.id] = group
    }
  }

  fun add(editorGroup: EditorGroup) {
    when (editorGroup) {
      is AutoGroup      -> return
      is EditorGroups   -> return
      is BookmarksGroup -> return
      else              -> addGroup(editorGroup)
    }
  }

  fun remove(editorGroup: EditorGroup) {
    groupsMap.remove(editorGroup.id)
  }

  override fun icon(): Icon = EditorGroupsIcons.groupBy

  override fun invalidate(): Unit = Unit

  override fun size(project: Project): Int = groupsMap.size

  override fun getLinks(project: Project): List<Link> = emptyList()

  override fun isOwner(ownerPath: String): Boolean = false

  fun validate(indexCache: IndexCache) {
    var iterator = groupsMap.values.iterator()
    while (iterator.hasNext()) {
      val next = iterator.next()
      indexCache.validate(next)
    }
    // IndexCache.validate accesses index which can triggers indexing which updates this map, removing it in one cycle would remove a key
    // with new valid value
    iterator = groupsMap.values.iterator()
    while (iterator.hasNext()) {
      val next = iterator.next()
      if (next.isInvalid) {
        iterator.remove()
      }
    }
  }

  fun first(): EditorGroup = groupsMap.values.iterator().next()

  fun getById(id: String): EditorGroup = groupsMap[id] ?: EMPTY

  fun ownerOrLast(currentFilePath: String?): EditorGroup = groupsMap.values.firstOrNull {
    it.isOwner(currentFilePath!!)
  } ?: EMPTY

  override fun toString(): String = "EditorGroups{map=$groupsMap, last='$last'}"

  companion object {
    const val ID: String = "EDITOR_GROUPS"
  }
}
