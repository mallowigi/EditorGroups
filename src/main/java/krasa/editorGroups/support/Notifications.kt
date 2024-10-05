package krasa.editorGroups.support

import com.intellij.ide.actions.OpenFileAction.Companion.openFile
import com.intellij.ide.ui.UISettings
import com.intellij.notification.*
import com.intellij.notification.Notifications.Bus.notify
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.EditorGroupIndexValue

object Notifications {
  private val notificationGroup: NotificationGroup
    get() = NotificationGroupManager.getInstance().getNotificationGroup("Editor Groups")

  fun notifyMissingFile(group: EditorGroup, path: String) {
    val content = "${("Path='$path'; Owner='${group.id}")}'"
    val notification = notificationGroup.createNotification("File does not exist", content, NotificationType.WARNING)
    show(notification)
  }

  fun notifyBugs() {
    val content =
      "Settings | ... | Editor Tabs | 'Open declaration source in the same tab' is enabled.<br/> It may cause problems when switching too fast.<br/><a href=\"#\">Click here to disable it<a/>."

    val notification = notificationGroup
      .createNotification("Editor Groups plugin", content, NotificationType.WARNING)
      .addAction(object : NotificationAction("") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          UISettings.getInstance().reuseNotModifiedTabs = false
          notification.expire()
        }
      })

    show(notification)
  }

  @JvmStatic
  fun indexingWarn(project: Project, file: VirtualFile, message: String) {
    val content = "$message in ${href(file)}"

    val notification = notificationGroup.createNotification("Editor Groups plugin", content, NotificationType.WARNING)
      .addAction(object : NotificationAction("Open") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          openFile(file, project)
          notification.expire()
        }
      })

    show(notification)
  }

  fun href(file: VirtualFile): String? = href(file.name)

  fun href(name: String): String = "<a href=\"$name\">$name<a/>"

  fun notifyDuplicateId(project: Project, id: String, values: List<EditorGroupIndexValue>) {
    val content = values.joinToString(
      prefix = "Duplicate Group ID '$id' in: [",
      postfix = "]"
    ) { href(it.ownerPath) }

    showWarning(content, object : NotificationAction("") {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        openFile(e.presentation.description, project)
      }
    })
  }

  fun showWarning(content: String, action: NotificationAction?) {
    val notification = notificationGroup.createNotification("Editor Groups plugin", content, NotificationType.WARNING)
    if (action != null) notification.addAction(action)

    LOG.warn(RuntimeException(content))
    show(notification)
  }

  @JvmStatic
  fun showWarning(s: String) {
    val notification = notificationGroup.createNotification("Editor Groups plugin", s, NotificationType.WARNING)
    show(notification)
  }

  @JvmStatic
  fun notifyTooManyFiles() = showWarning(TooManyFilesException.FOUND_TOO_MANY_MATCHING_FILES_SKIPPING)

  private fun show(notification: Notification) = ApplicationManager.getApplication().invokeLater { notify(notification) }
}
