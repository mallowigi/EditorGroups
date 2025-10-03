package krasa.editorGroups.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.indexing.FileBasedIndex
import krasa.editorGroups.index.EditorGroupIndex
import krasa.editorGroups.index.IndexCache
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.support.Notifications

class ReindexAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    thisLogger().debug("INDEXING START ${System.currentTimeMillis()}") // NON-NLS
    IndexCache.getInstance(e.project ?: return).clear()
    FileBasedIndex.getInstance().requestRebuild(EditorGroupIndex.NAME)

    Notifications.notifySimple(message("reindexing.started"))
  }

  companion object {
    const val ID: String = "krasa.editorGroups.Reindex"
  }
}
