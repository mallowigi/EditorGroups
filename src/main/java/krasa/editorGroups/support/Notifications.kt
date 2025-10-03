package krasa.editorGroups.support

import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.ui.UISettings
import com.intellij.notification.*
import com.intellij.notification.Notifications.Bus.notify
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.EditorGroupIndexValue
import org.jetbrains.annotations.NonNls

object Notifications {
  const val ID: String = "Editor Groups"

  private val notificationGroup: NotificationGroup
    get() = NotificationGroupManager.getInstance().getNotificationGroup(ID)

  fun notifyBugs() {
    val content = message("notifications.content.bugs")

    val notification = notificationGroup
      .createNotification(ID, content, NotificationType.WARNING)
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
    val content = message("notification.content.indexing", message, href(file))
    val notification = notificationGroup.createNotification(ID, content, NotificationType.WARNING)
      .addAction(object : NotificationAction(message("notification.content.open")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          OpenFileAction.openFile(file, project)
          notification.expire()
        }
      })

    show(notification)
  }

  fun href(file: VirtualFile): String = href(file.name)

  @NonNls
  fun href(name: String): String = "<a href=\"$name\">$name<a/>"

  fun notifyDuplicateId(project: Project, id: String, values: List<EditorGroupIndexValue>) {
    val vals = values.joinToString { href(it.ownerPath) }
    val content = message("notifications.content.duplicate.group.id", id, vals)

    showWarning(
      content,
      object : NotificationAction("") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          OpenFileAction.openFile(e.presentation.description, project)
        }
      }
    )
  }

  fun showWarning(content: String, action: NotificationAction?) {
    val notification = notificationGroup.createNotification(ID, content, NotificationType.WARNING)
    if (action != null) notification.addAction(action)

    thisLogger().warn(RuntimeException(content))
    show(notification)
  }

  @JvmStatic
  fun showWarning(s: String) {
    val notification = notificationGroup.createNotification(ID, s, NotificationType.WARNING)
    show(notification)
  }

  @JvmStatic
  fun notifyTooManyFiles(): Unit = showWarning(TooManyFilesException.FOUND_TOO_MANY_MATCHING_FILES_SKIPPING)

  @JvmStatic
  fun notifySimple(msg: String) {
    val notification = notificationGroup.createNotification(ID, msg, NotificationType.INFORMATION)
    show(notification)
  }

  @JvmStatic
  fun notifyState(str: String, state: Boolean) {
    val msg = if (state) message("notification.content.enabled", str) else message("notification.content.disabled", str)
    val notification = notificationGroup.createNotification(ID, msg, NotificationType.INFORMATION)
    show(notification)
  }

  private fun show(notification: Notification) = ApplicationManager.getApplication().invokeLater { notify(notification) }
}
