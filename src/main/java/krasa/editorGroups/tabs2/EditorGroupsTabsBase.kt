package krasa.editorGroups.tabs2

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.ActiveRunnable
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.function.Supplier
import javax.swing.JComponent

@Suppress("unused", "HardCodedStringLiteral")
interface EditorGroupsTabsBase {
  /** Selected tab. */
  val selectedInfo: EditorGroupTabInfo?

  /** List of tabs. */
  val tabs: List<EditorGroupTabInfo>

  /** Tab Count. */
  val tabCount: Int

  /** Add a tab at the given index. */
  fun addTab(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo

  /** Adds a tab at the end. */
  fun addTab(info: EditorGroupTabInfo): EditorGroupTabInfo

  /** Removes a tab. */
  fun removeTab(info: EditorGroupTabInfo?): ActionCallback

  /** Removes all tabs. */
  fun removeAllTabs()

  /** Selects a tab, optionally requesting focus. */
  fun select(info: EditorGroupTabInfo, requestFocus: Boolean): ActionCallback

  /** Get Tab at index. */
  fun getTabAt(tabIndex: Int): EditorGroupTabInfo

  /** The tab presentation. */
  fun getPresentation(): KrTabsPresentation

  fun setDataProvider(dataProvider: DataProvider): EditorGroupsTabsBase?

  fun getTargetInfo(): EditorGroupTabInfo?

  fun addTabMouseListener(listener: MouseListener): EditorGroupsTabsBase

  fun addListener(listener: EditorGroupsTabsListener): EditorGroupsTabsBase?

  fun addListener(listener: EditorGroupsTabsListener, disposable: Disposable?): EditorGroupsTabsBase?

  fun setSelectionChangeHandler(handler: SelectionChangeHandler): EditorGroupsTabsBase?

  fun getComponent(): JComponent

  fun findInfo(event: MouseEvent): EditorGroupTabInfo?

  fun findInfo(component: Component): EditorGroupTabInfo?

  fun getIndexOf(tabInfo: EditorGroupTabInfo?): Int

  fun requestFocus()

  fun setNavigationActionBinding(prevActionId: String, nextActionId: String)

  fun setPopupGroup(popupGroup: ActionGroup, place: String, addNavigationGroup: Boolean): EditorGroupsTabsBase

  fun setPopupGroupWithSupplier(supplier: Supplier<out ActionGroup?>, place: String, addNavigationGroup: Boolean): EditorGroupsTabsBase

  fun resetDropOver(tabInfo: EditorGroupTabInfo)

  fun getTabLabel(tabInfo: EditorGroupTabInfo): Component?

  interface SelectionChangeHandler {
    fun execute(info: EditorGroupTabInfo, requestFocus: Boolean, doChangeSelection: ActiveRunnable): ActionCallback
  }
}
