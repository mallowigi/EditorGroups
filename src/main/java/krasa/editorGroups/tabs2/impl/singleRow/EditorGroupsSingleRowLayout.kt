package krasa.editorGroups.tabs2.impl.singleRow

import krasa.editorGroups.tabs2.EditorGroupsTabsPosition
import krasa.editorGroups.tabs2.impl.EditorGroupsTabLayout
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import krasa.editorGroups.tabs2.impl.singleRow.EditorGroupsSingleRowLayoutStrategy.Bottom
import krasa.editorGroups.tabs2.impl.singleRow.EditorGroupsSingleRowLayoutStrategy.Top
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo
import krasa.editorGroups.tabs2.label.EditorGroupTabLabel
import java.awt.Dimension
import java.awt.Rectangle
import java.lang.ref.WeakReference
import javax.swing.JComponent

abstract class EditorGroupsSingleRowLayout(
  val tabs: KrTabsImpl
) : EditorGroupsTabLayout() {
  var lastSingleRowLayout: EditorGroupsSingleRowPassInfo? = null

  private val topStrategy: EditorGroupsSingleRowLayoutStrategy = Top(this)
  private val bottomStrategy: EditorGroupsSingleRowLayoutStrategy = Bottom(this)

  val strategy: EditorGroupsSingleRowLayoutStrategy
    get() = when (tabs.getPresentation().tabsPosition) {
      EditorGroupsTabsPosition.TOP    -> topStrategy
      EditorGroupsTabsPosition.BOTTOM -> bottomStrategy
    }

  /** Check whether to relayout tab labels. */
  @Suppress("detekt:ReturnCount", "HardCodedStringLiteral")
  protected open fun shouldRelayoutLabels(passInfo: EditorGroupsSingleRowPassInfo): Boolean {
    var layoutLabels = true

    when {
      tabs.forcedRelayout                                -> return true
      lastSingleRowLayout == null                        -> return true
      lastSingleRowLayout?.contentCount != tabs.tabCount -> return true
      lastSingleRowLayout?.layoutSize != tabs.size       -> return true
      lastSingleRowLayout?.scrollOffset != scrollOffset  -> return true
    }

    for (tabInfo in passInfo.visibleTabInfos) {
      val tabLabel: EditorGroupTabLabel = tabs.getTabLabel(tabInfo)!!
      if (!tabLabel.isValid) return true

      if (tabs.selectedInfo === tabInfo && tabLabel.bounds.width != 0) {
        layoutLabels = false
      }
    }

    return layoutLabels
  }

  /** Layout the tabs in a single row. */
  fun layoutSingleRow(visibleTabInfos: MutableList<EditorGroupTabInfo>): EditorGroupsLayoutPassInfo {
    var passInfo = EditorGroupsSingleRowPassInfo(layout = this, visibleTabInfos = visibleTabInfos)

    // Reuse the last single row layout if nothing changed
    val shouldLayoutLabels = shouldRelayoutLabels(passInfo)
    if (!shouldLayoutLabels && lastSingleRowLayout != null) {
      passInfo = lastSingleRowLayout!!
    }

    // Prepare the passinfo
    val selectedInfo = tabs.selectedInfo
    prepareLayoutPassInfo(passInfo = passInfo)

    // Reset the layout
    tabs.resetLayout(shouldLayoutLabels)

    // Layout the tabs
    if (shouldLayoutLabels) {
      recomputeToLayout(passInfo)

      // Sets the left position
      passInfo.position = strategy.getStartPosition(passInfo) - scrollOffset

      // Layout the labels
      layoutLabels(passInfo)

      // Layout the more button
      layoutMoreButton(passInfo)
    }

    // Layout the selected info
    if (selectedInfo != null) {
      passInfo.component = WeakReference<JComponent?>(selectedInfo.component)
      strategy.layoutComp(passInfo)
    }

    // The tab rectangle
    passInfo.tabRectangle = Rectangle()

    if (!passInfo.toLayout.isEmpty()) {
      val firstLabel = tabs.getTabLabel(passInfo.toLayout[0])
      val lastLabel = findLastVisibleLabel(passInfo)

      // Compute the rectangle between first label and last label
      if (firstLabel != null && lastLabel != null) {
        passInfo.tabRectangle!!.x = firstLabel.bounds.x
        passInfo.tabRectangle!!.y = firstLabel.bounds.y
        passInfo.tabRectangle!!.width = passInfo.entryPointRect.maxX.toInt() + tabs.actionsInsets.right - passInfo.tabRectangle!!.x
        passInfo.tabRectangle!!.height = lastLabel.bounds.maxY.toInt() - passInfo.tabRectangle!!.y
      }
    }

    // Save last pass info
    lastSingleRowLayout = passInfo
    return passInfo
  }

  protected open fun findLastVisibleLabel(passInfo: EditorGroupsSingleRowPassInfo): EditorGroupTabLabel? =
    tabs.getTabLabel(passInfo.toLayout[passInfo.toLayout.size - 1])

  /** Prepare the passInfo. */
  protected fun prepareLayoutPassInfo(passInfo: EditorGroupsSingleRowPassInfo) {
    // Save the insets
    passInfo.insets = tabs.layoutInsets
    // Add offset left
    passInfo.insets!!.left += tabs.firstTabOffset

    // Set the fit length
    passInfo.toFitLength = strategy.getToFitLength(passInfo)
  }

  protected open fun layoutMoreButton(passInfo: EditorGroupsSingleRowPassInfo) {
    passInfo.moreRect = strategy.getMoreRect(passInfo)
  }

  protected fun layoutLabels(passInfo: EditorGroupsSingleRowPassInfo) {
    var layoutStopped = false

    for (tabInfo in passInfo.toLayout) {
      val tabLabel = tabs.getTabLabel(tabInfo)!!

      if (layoutStopped) {
        val rect = strategy.getLayoutRect(
          passInfo = passInfo,
          position = 0,
          fixedPos = strategy.getFixedPosition(passInfo),
          length = 0,
          fixedFitLength = strategy.getFixedFitLength(passInfo)
        )

        // Layout the tabLabel in rect
        tabs.layout(tabLabel, rect)
        continue
      }

      val tabLabelSize = tabLabel.preferredSize
      val length = strategy.getLengthIncrement(tabLabelSize)
      val continueLayout = applyTabLayout(passInfo = passInfo, label = tabLabel, length = length)

      passInfo.position = strategy.getMaxPosition(tabLabel.bounds)
      passInfo.position += tabs.tabHGap

      if (!continueLayout) {
        layoutStopped = true
      }
    }
  }

  protected open fun applyTabLayout(passInfo: EditorGroupsSingleRowPassInfo, label: EditorGroupTabLabel, length: Int): Boolean {
    val rect = strategy.getLayoutRect(
      passInfo = passInfo,
      position = passInfo.position,
      fixedPos = strategy.getFixedPosition(passInfo),
      length = length,
      fixedFitLength = strategy.getFixedFitLength(passInfo)
    )

    tabs.layout(component = label, bounds = rect)

    label.setAlignmentToCenter()
    return true
  }

  /**
   * Recomputes the layout for the editor groups single row configuration.
   *
   * @param passInfo The data object containing the current layout information, e.g visible tabs, required lengths, scroll offsets.
   */
  protected abstract fun recomputeToLayout(passInfo: EditorGroupsSingleRowPassInfo)

  protected fun calculateRequiredLength(passInfo: EditorGroupsSingleRowPassInfo) {
    passInfo.requiredLength = passInfo.requiredLength + passInfo.insets!!.left + passInfo.insets!!.right

    for (eachInfo in passInfo.visibleTabInfos) {
      passInfo.requiredLength = passInfo.requiredLength + getRequiredLength(eachInfo)
      passInfo.toLayout.add(eachInfo)
    }

    passInfo.requiredLength = passInfo.requiredLength + strategy.additionalLength
  }

  protected fun getRequiredLength(tabInfo: EditorGroupTabInfo?): Int {
    val label = tabs.infoToLabel[tabInfo]
    return strategy.getLengthIncrement(label?.preferredSize ?: Dimension()) + tabs.tabHGap
  }

  override fun isTabHidden(tabInfo: EditorGroupTabInfo): Boolean = lastSingleRowLayout != null
}
