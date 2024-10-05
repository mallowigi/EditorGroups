// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.util.ui.JBUI
import krasa.editorGroups.tabs2.KrTabInfo
import org.intellij.lang.annotations.MagicConstant
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingConstants
import kotlin.math.abs

abstract class KrTabLayout {
  open val isSideComponentOnTabs: Boolean = false

  open val isScrollable: Boolean = false

  open val isWithScrollBar: Boolean = false

  open val scrollOffset: Int = 0

  fun createShapeTransform(dimension: Dimension): KrShapeTransform =
    createShapeTransform(Rectangle(0, 0, dimension.width, dimension.height))

  open fun createShapeTransform(rectangle: Rectangle?): KrShapeTransform = KrShapeTransform.Top(rectangle)

  open fun isDragOut(tabLabel: KrTabLabel, deltaX: Int, deltaY: Int): Boolean =
    abs(deltaY.toDouble()) > tabLabel.height * dragOutMultiplier ||
      abs(deltaX.toDouble()) > tabLabel.width * dragOutMultiplier

  open fun scroll(units: Int) = Unit

  open fun isTabHidden(info: KrTabInfo): Boolean = false

  abstract fun getDropIndexFor(point: Point?): Int

  @MagicConstant(intValues = [SwingConstants.TOP.toLong(), SwingConstants.LEFT.toLong(), SwingConstants.BOTTOM.toLong(), SwingConstants.RIGHT.toLong(), -1])
  abstract fun getDropSideFor(point: Point): Int

  companion object {
    @JvmStatic
    val dragOutMultiplier: Double
      get() = 0.1

    @JvmStatic
    val maxPinnedTabWidth: Int
      get() = JBUI.scale(2000)

    @JvmStatic
    protected val minTabWidth: Int
      get() = JBUI.scale(50)

    const val DEADZONE_FOR_DECLARE_TAB_HIDDEN: Int = 10

    @JvmStatic
    fun showPinnedTabsSeparately(): Boolean {
      return UISettings.getInstance().state.showPinnedTabsInASeparateRow &&
        AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")
    }
  }
}
