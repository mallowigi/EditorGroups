package krasa.editorGroups.support

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

fun <T> lazyUiDisposable(parent: Disposable?, ui: JComponent, child: T, task: (child: T, project: Project?) -> Unit) {
  val uiRef = AtomicReference(ui)
  UiNotifyConnector.Once.installOn(
    component = ui,
    target = object : Activatable {
      override fun showNotify() {
        val ui = uiRef.getAndSet(null) ?: return
        var project: Project? = null

        var parent = parent
        if (ApplicationManager.getApplication() != null) {
          val context = DataManager.getInstance().getDataContext(ui)
          project = CommonDataKeys.PROJECT.getData(context)
          if (parent == null) {
            parent = PlatformDataKeys.UI_DISPOSABLE.getData(context)
          }
        }

        if (parent == null) {
          parent = when (project) {
            null -> {
              logger<Disposable>().warn("use application as a parent disposable") // NON-NLS
              ApplicationManager.getApplication()
            }

            else -> {
              logger<Disposable>().warn("use project as a parent disposable") // NON-NLS
              project
            }
          }
        }

        task(child, project)
        if (child is Disposable) {
          Disposer.register(parent ?: return, child)
        }
      }
    }
  )
}