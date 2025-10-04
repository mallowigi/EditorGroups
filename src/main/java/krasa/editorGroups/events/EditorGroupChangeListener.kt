package krasa.editorGroups.events

import com.intellij.util.messages.Topic
import krasa.editorGroups.model.EditorGroup

interface EditorGroupChangeListener {
  fun groupChanged(newGroup: EditorGroup)

  companion object {
    @JvmField
    val TOPIC: Topic<EditorGroupChangeListener> = Topic.create(
      "Editor Group Changed",
      EditorGroupChangeListener::class.java
    )
  }
}