package krasa.editorGroups.tabs2.impl.singleRow

import com.intellij.util.ui.JBUI
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo
import krasa.editorGroups.tabs2.label.EditorGroupTabLabel
import kotlin.math.max
import kotlin.math.min

class EditorGroupsScrollableSingleRowLayout(tabs: KrTabsImpl) : EditorGroupsSingleRowLayout(tabs) {
  override var scrollOffset: Int = 0
    private set

  /** Size of the more button rect. */
  private val moreRectAxisSize: Int
    get() = strategy.moreRectAxisSize

  /** Scroll to X units. */
  override fun scroll(units: Int) {
    this.scrollOffset += units
    clampScrollOffsetToBounds(lastSingleRowLayout)
  }

  override fun shouldRelayoutLabels(passInfo: EditorGroupsSingleRowPassInfo): Boolean = true

  /**
   * Clamps the scroll offset to ensure the content is within the bounds.
   *
   * @param data The data object containing the current layout information.
   */
  private fun clampScrollOffsetToBounds(data: EditorGroupsSingleRowPassInfo?) {
    if (data == null) return

    if (data.requiredLength < data.toFitLength) {
      this.scrollOffset = 0
      return
    }

    var max = data.requiredLength - data.toFitLength + this.moreRectAxisSize
    val actionInsets = tabs.actionsInsets
    max += actionInsets.left + actionInsets.right

    this.scrollOffset = max(
      0.0,
      min(scrollOffset.toDouble(), max.toDouble())
    ).toInt()
  }

  /**
   * Scrolls the view to ensure that the selected tab is visible.
   *
   * @param passInfo The layout information for the current row of tabs.
   */
  @Suppress("detekt:NestedBlockDepth")
  private fun doScrollToSelectedTab(passInfo: EditorGroupsSingleRowPassInfo) {
    if (tabs.isMouseInsideTabsArea || tabs.isScrollBarAdjusting()) return

    var offset = -this.scrollOffset
    for (info in passInfo.visibleTabInfos) {
      val length = getRequiredLength(info)

      if (info === tabs.selectedInfo) {
        if (offset < 0) {
          scroll(offset)
          continue
        }

        var maxLength = passInfo.toFitLength - this.moreRectAxisSize
        val actionInsets = tabs.actionsInsets

        if (tabs.entryPointPreferredSize.width == 0) {
          maxLength -= actionInsets.left + actionInsets.right
        }

        if (offset + length > maxLength) {
          // a left side should always be visible
          when {
            length < maxLength -> scroll(offset + length - maxLength)
            else               -> scroll(offset)
          }
        }
        break
      }
      offset += length
    }
  }

  override fun recomputeToLayout(passInfo: EditorGroupsSingleRowPassInfo) {
    calculateRequiredLength(passInfo)
    clampScrollOffsetToBounds(passInfo)
    doScrollToSelectedTab(passInfo)
    clampScrollOffsetToBounds(passInfo)
  }

  override fun layoutMoreButton(passInfo: EditorGroupsSingleRowPassInfo) {
    if (passInfo.requiredLength <= passInfo.toFitLength) return
    passInfo.moreRect = strategy.getMoreRect(passInfo)
  }

  override fun applyTabLayout(passInfo: EditorGroupsSingleRowPassInfo, label: EditorGroupTabLabel, length: Int): Boolean {
    var length = length

    if (passInfo.requiredLength > passInfo.toFitLength) {
      length = strategy.getLengthIncrement(label.getPreferredSize())
      var moreRectSize = this.moreRectAxisSize

      if (passInfo.entryPointAxisSize == 0) {
        val insets = tabs.actionsInsets
        moreRectSize += insets.left + insets.right
      }

      if (passInfo.position + length > passInfo.toFitLength - moreRectSize) {
        if (strategy.drawPartialOverflowTabs()) {
          val clippedLength = passInfo.toFitLength - passInfo.position - moreRectSize
          val rec = strategy.getLayoutRect(
            passInfo = passInfo,
            position = passInfo.position,
            fixedPos = strategy.getFixedPosition(passInfo),
            length = clippedLength,
            fixedFitLength = strategy.getFixedFitLength(passInfo)
          )
          tabs.layout(label, rec)
        }

        label.setAlignmentToCenter()
        return false
      }
    }

    return super.applyTabLayout(passInfo, label, length)
  }

  /** Check whether the given tab is hidden (needs scrolling) */
  override fun isTabHidden(tabInfo: EditorGroupTabInfo): Boolean {
    val label = tabs.getTabLabel(tabInfo)!!
    val bounds = label.bounds
    val deadzone = JBUI.scale(DEADZONE_TAB_HIDDEN)

    return strategy.getMinPosition(bounds) < -deadzone ||
      bounds.width < label.getPreferredSize().width - deadzone ||
      bounds.height < label.getPreferredSize().height - deadzone
  }

  override fun findLastVisibleLabel(passInfo: EditorGroupsSingleRowPassInfo): EditorGroupTabLabel? {
    var i = passInfo.toLayout.size - 1
    while (i >= 0) {
      val info = passInfo.toLayout[i]
      val label = tabs.getTabLabel(info)!!

      if (!label.bounds.isEmpty) return label

      i--
    }
    return null
  }

  companion object {
    const val DEADZONE_TAB_HIDDEN: Int = 10
  }
}
