package krasa.editorGroups.tabs2.impl.singleRow

import krasa.editorGroups.tabs2.impl.KrTabsImpl
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.sign

abstract class EditorGroupsSingleRowLayoutStrategy protected constructor(myLayout: EditorGroupsSingleRowLayout) {
  val myTabs: KrTabsImpl = myLayout.tabs

  abstract val moreRectAxisSize: Int

  abstract val entryPointAxisSize: Int

  abstract val additionalLength: Int

  abstract val isToCenterTextWhenStretched: Boolean

  abstract fun getStartPosition(passInfo: EditorGroupsSingleRowPassInfo): Int

  abstract fun getToFitLength(passInfo: EditorGroupsSingleRowPassInfo?): Int

  abstract fun getLengthIncrement(dimension: Dimension): Int

  abstract fun getMinPosition(bounds: Rectangle): Int

  abstract fun getMaxPosition(bounds: Rectangle): Int

  abstract fun getFixedFitLength(passInfo: EditorGroupsSingleRowPassInfo?): Int

  abstract fun getLayoutRect(
    passInfo: EditorGroupsSingleRowPassInfo,
    position: Int,
    fixedPos: Int,
    length: Int,
    fixedFitLength: Int
  ): Rectangle

  abstract fun getFixedPosition(passInfo: EditorGroupsSingleRowPassInfo): Int

  abstract fun getMoreRect(passInfo: EditorGroupsSingleRowPassInfo): Rectangle

  abstract fun getEntryPointRect(passInfo: EditorGroupsSingleRowPassInfo): Rectangle?

  abstract fun layoutComp(passInfo: EditorGroupsSingleRowPassInfo)

  /**
   * Whether a tab that didn't fit completely on the right/bottom side in scrollable layout should be clipped or hidden altogether.
   *
   * @return true if the tab should be clipped, false if hidden.
   */
  abstract fun drawPartialOverflowTabs(): Boolean

  internal abstract class Horizontal protected constructor(layout: EditorGroupsSingleRowLayout) :
    EditorGroupsSingleRowLayoutStrategy(layout) {
    override val isToCenterTextWhenStretched: Boolean
      get() = true

    override val moreRectAxisSize: Int
      get() = myTabs.moreToolbarPreferredSize.width

    override val entryPointAxisSize: Int
      get() = myTabs.entryPointPreferredSize.width

    override val additionalLength: Int
      get() = 0

    override fun getToFitLength(passInfo: EditorGroupsSingleRowPassInfo?): Int {
      if (passInfo == null) return 0

      var length = myTabs.width - passInfo.insets!!.left - passInfo.insets!!.right

      val hToolbar = passInfo.hToolbar?.get()
      if (hToolbar != null) length -= hToolbar.minimumSize.width

      length += getStartPosition(passInfo)

      val entryPointWidth = myTabs.entryPointPreferredSize.width
      val toolbarInsets = myTabs.actionsInsets
      val insets = toolbarInsets.left + toolbarInsets.right

      length -= (entryPointWidth + insets * sign(entryPointWidth.toDouble())).toInt()

      return length
    }

    override fun getLengthIncrement(dimension: Dimension): Int = max(dimension.width, MIN_TAB_WIDTH)

    override fun getMinPosition(bounds: Rectangle): Int = bounds.x

    override fun getMaxPosition(bounds: Rectangle): Int = bounds.maxX.toInt()

    override fun getFixedFitLength(passInfo: EditorGroupsSingleRowPassInfo?): Int = myTabs.headerFitSize!!.height

    override fun getLayoutRect(
      passInfo: EditorGroupsSingleRowPassInfo,
      position: Int,
      fixedPos: Int,
      length: Int,
      fixedFitLength: Int
    ): Rectangle = Rectangle(
      /* x = */
      position,
      /* y = */
      fixedPos,
      /* width = */
      length,
      /* height = */
      fixedFitLength
    )

    override fun getStartPosition(passInfo: EditorGroupsSingleRowPassInfo): Int = passInfo.insets!!.left

    override fun drawPartialOverflowTabs(): Boolean = true
  }

  internal class Top(layout: EditorGroupsSingleRowLayout) : Horizontal(layout) {

    override fun getFixedPosition(passInfo: EditorGroupsSingleRowPassInfo): Int = passInfo.insets!!.top

    override fun getEntryPointRect(passInfo: EditorGroupsSingleRowPassInfo): Rectangle {
      val x: Int = passInfo.layoutSize.width - myTabs.actionsInsets.right - passInfo.entryPointAxisSize
      return Rectangle(
        x,
        1,
        passInfo.entryPointAxisSize,
        myTabs.headerFitSize!!.height
      )
    }

    override fun getMoreRect(passInfo: EditorGroupsSingleRowPassInfo): Rectangle {
      var x: Int = passInfo.layoutSize.width - myTabs.actionsInsets.right - passInfo.moreRectAxisSize
      x -= passInfo.entryPointAxisSize

      return Rectangle(
        x,
        1,
        passInfo.moreRectAxisSize,
        myTabs.headerFitSize!!.height
      )
    }

    override fun layoutComp(passInfo: EditorGroupsSingleRowPassInfo) {
      val x = 0
      val hToolbar = passInfo.hToolbar?.get()
      val hToolbarHeight = when {
        hToolbar != null -> hToolbar.preferredSize.height
        else             -> 0
      }

      val y: Int = myTabs.headerFitSize!!.height + max(hToolbarHeight, 0)

      val comp = passInfo.component!!.get()

      when {
        hToolbar != null -> {
          val componentBounds = myTabs.layoutComp(
            componentX = x,
            componentY = y,
            component = comp!!,
            deltaWidth = 0,
            deltaHeight = 0
          )

          val toolbarHeight = hToolbar.preferredSize.height
          myTabs.layout(
            component = hToolbar,
            x = componentBounds.x,
            y = componentBounds.y - toolbarHeight,
            width = componentBounds.width,
            height = toolbarHeight
          )
        }

        else             -> myTabs.layoutComp(
          componentX = x,
          componentY = y,
          component = comp!!,
          deltaWidth = 0,
          deltaHeight = 0
        )
      }
    }
  }

  internal class Bottom(layout: EditorGroupsSingleRowLayout) : Horizontal(layout) {
    override fun layoutComp(passInfo: EditorGroupsSingleRowPassInfo) {
      myTabs.layoutComp(
        passInfo = passInfo,
        deltaX = 0,
        deltaY = 0,
        deltaWidth = 0,
        deltaHeight = -myTabs.headerFitSize!!.height
      )
    }

    override fun getFixedPosition(passInfo: EditorGroupsSingleRowPassInfo): Int =
      myTabs.size.height - passInfo.insets!!.bottom - myTabs.headerFitSize!!.height

    override fun getEntryPointRect(passInfo: EditorGroupsSingleRowPassInfo): Rectangle {
      val x: Int = passInfo.layoutSize.width - myTabs.actionsInsets.right - passInfo.entryPointAxisSize

      return Rectangle(
        x,
        getFixedPosition(passInfo),
        passInfo.entryPointAxisSize,
        myTabs.headerFitSize!!.height
      )
    }

    override fun getMoreRect(passInfo: EditorGroupsSingleRowPassInfo): Rectangle {
      var x: Int = passInfo.layoutSize.width - myTabs.actionsInsets.right - passInfo.moreRectAxisSize
      x -= passInfo.entryPointAxisSize

      return Rectangle(
        x,
        getFixedPosition(passInfo),
        passInfo.moreRectAxisSize,
        myTabs.headerFitSize!!.height
      )
    }
  }

  companion object {
    private const val MIN_TAB_WIDTH = 50
  }
}