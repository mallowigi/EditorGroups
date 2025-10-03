package krasa.editorGroups.tabs2.impl.islands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainterAdapter

open class KrTabsPainterProvider {
  companion object {
    @JvmStatic
    fun getInstance(): KrTabsPainterProvider? = ApplicationManager.getApplication()?.serviceOrNull()
  }

  open fun createTabPainter(): EditorGroupsTabPainterAdapter? = null
}