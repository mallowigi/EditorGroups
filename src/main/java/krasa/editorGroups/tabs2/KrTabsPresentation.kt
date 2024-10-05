// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.TimedDeadzone
import java.awt.Color
import java.awt.Insets

interface KrTabsPresentation {
  var isHideTabs: Boolean

  fun setPaintFocus(paintFocus: Boolean): KrTabsPresentation

  fun setSideComponentVertical(vertical: Boolean): KrTabsPresentation

  fun setSideComponentOnTabs(onTabs: Boolean): KrTabsPresentation

  fun setSideComponentBefore(before: Boolean): KrTabsPresentation

  fun setSingleRow(singleRow: Boolean): KrTabsPresentation

  fun setUiDecorator(decorator: KrUiDecorator?): KrTabsPresentation

  fun setPaintBlocked(blocked: Boolean, takeSnapshot: Boolean)

  fun setInnerInsets(innerInsets: Insets): KrTabsPresentation

  fun setFocusCycle(root: Boolean): KrTabsPresentation?

  fun setToDrawBorderIfTabsHidden(draw: Boolean): KrTabsPresentation

  fun setTabLabelActionsAutoHide(autoHide: Boolean): KrTabsPresentation

  fun setTabLabelActionsMouseDeadzone(length: TimedDeadzone.Length?): KrTabsPresentation

  val tabsPosition: KrTabsPosition

  fun setTabDraggingEnabled(enabled: Boolean): KrTabsPresentation

  fun setAlphabeticalMode(alphabeticalMode: Boolean): KrTabsPresentation

  fun setRequestFocusOnLastFocusedComponent(request: Boolean): KrTabsPresentation?

  fun setSupportsCompression(supportsCompression: Boolean): KrTabsPresentation

  fun setFirstTabOffset(offset: Int)

  fun setEmptyText(text: @NlsContexts.StatusText String?): KrTabsPresentation

  fun setActiveTabFillIn(color: Color): KrTabsPresentation
}
