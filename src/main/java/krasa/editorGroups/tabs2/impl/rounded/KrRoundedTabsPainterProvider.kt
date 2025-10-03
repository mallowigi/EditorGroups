package krasa.editorGroups.tabs2.impl.rounded

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainterAdapter

open class KrRoundedTabsPainterProvider {
  companion object {
    @JvmStatic
    fun getInstance(): KrRoundedTabsPainterProvider? = ApplicationManager.getApplication()?.serviceOrNull()
  }

  open fun createTabPainter(): EditorGroupsTabPainterAdapter? = null
}