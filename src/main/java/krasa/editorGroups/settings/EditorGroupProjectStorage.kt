package krasa.editorGroups.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import krasa.editorGroups.index.IndexCache

@Service(Service.Level.PROJECT)
@State(name = "EditorGroups", storages = [Storage(value = "EditorGroups.xml")])
class EditorGroupProjectStorage(
  private val project: Project
) : PersistentStateComponent<EditorGroupProjectStorage.State> {

  class State {
    @XCollection(propertyElementName = "lastGroup", elementTypes = [KeyValuePair::class])
    var lastGroup: MutableList<KeyValuePair> = mutableListOf()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false

      val state = other as State
      return lastGroup == state.lastGroup
    }

    override fun hashCode(): Int = lastGroup.hashCode()
  }

  override fun getState(): State {
    when {
      EditorGroupsSettings.instance.isRememberLastGroup -> {
        val start = System.currentTimeMillis()
        val state = IndexCache.getInstance(project).state

        thisLogger().debug("EditorGroupProjectSettings.getState size:${state.lastGroup.size} ${System.currentTimeMillis() - start}ms") // NON-NLS
        return state
      }

      else                                              -> return State()
    }
  }

  override fun loadState(state: State) {
    if (EditorGroupsSettings.instance.isRememberLastGroup) {
      val start = System.currentTimeMillis()
      IndexCache.getInstance(project).loadState(state)

      thisLogger().debug("EditorGroupProjectSettings.loadState size:${state.lastGroup.size} ${System.currentTimeMillis() - start}ms") // NON-NLS
    }
  }

  @Tag("pair")
  @Suppress("DataClassShouldBeImmutable")
  data class KeyValuePair(
    @Attribute("key") var key: String,
    @Attribute("value") var value: String?
  )
}
