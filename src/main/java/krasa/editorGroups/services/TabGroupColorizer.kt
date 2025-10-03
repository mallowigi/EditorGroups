package krasa.editorGroups.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.settings.EditorGroupsSettings
import java.awt.Color

@Service(Service.Level.PROJECT)
class TabGroupColorizer(private val project: Project) {
  fun refreshTabs(force: Boolean = false) {
    if (!force && !EditorGroupsSettings.instance.isColorTabs) return

    FileEditorManagerEx.getInstanceEx(project).windows
      .forEach { window ->
        window.tabbedPane.tabs.tabs.forEach { tabInfo ->
          val color = getColor(project, tabInfo.text)
          tabInfo.setTabColor(color)
        }
      }
  }

  fun getColor(project: Project, tabInfo: @NlsContexts.TabTitle String): Color? {
    val lastGroup = EditorGroupManager.getInstance(project).lastGroup
    return when {
      lastGroup.isStub                           -> null
      !EditorGroupsSettings.instance.isColorTabs -> null
      !lastGroup.containsLink(project, tabInfo)  -> null
      else                                       -> lastGroup.bgColor
    }
  }

  companion object {
    fun getInstance(project: Project): TabGroupColorizer = project.getService(TabGroupColorizer::class.java)
  }
}