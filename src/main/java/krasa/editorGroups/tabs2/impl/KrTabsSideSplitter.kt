// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl

import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.ui.ClientProperty
import krasa.editorGroups.tabs2.KrTabsPosition
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import kotlin.math.max
import kotlin.math.min

internal class KrTabsSideSplitter(private val tabs: KrTabsImpl) : Splittable, PropertyChangeListener {
  private var mySideTabsLimit = KrTabsImpl.DEFAULT_MAX_TAB_WIDTH
  val divider: OnePixelDivider

  var sideTabsLimit: Int
    get() = mySideTabsLimit
    set(sideTabsLimit) {
      if (mySideTabsLimit != sideTabsLimit) {
        mySideTabsLimit = sideTabsLimit
        tabs.putClientProperty(KrTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, mySideTabsLimit)
        tabs.resetLayout(true)
        tabs.doLayout()
        tabs.repaint()

        val info = tabs.selectedInfo
        val page = info?.component
        if (page != null) {
          page.revalidate()
          page.repaint()
        }
      }
    }

  init {
    tabs.addPropertyChangeListener(KrTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), this)
    divider = OnePixelDivider(false, this)
  }

  override fun getMinProportion(first: Boolean): Float {
    return min(
      0.5,
      (KrTabsImpl.MIN_TAB_WIDTH.toFloat() / max(1.0, tabs.width.toDouble())).toDouble()
    ).toFloat()
  }

  override fun setProportion(proportion: Float) {
    val width = tabs.width
    sideTabsLimit = when (tabs.tabsPosition) {
      KrTabsPosition.left  -> max(KrTabsImpl.MIN_TAB_WIDTH.toDouble(), (proportion * width).toDouble())
        .toInt()

      KrTabsPosition.right -> width - max(KrTabsImpl.MIN_TAB_WIDTH.toDouble(), (proportion * width).toDouble())
        .toInt()

      else                 -> width
    }
  }

  override fun getOrientation(): Boolean = false

  override fun setOrientation(verticalSplit: Boolean) = Unit

  override fun setDragging(dragging: Boolean) {}

  override fun asComponent(): Component = tabs

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (evt.source !== tabs) return
    var limit = ClientProperty.get(tabs, KrTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY)
    if (limit == null) limit = KrTabsImpl.DEFAULT_MAX_TAB_WIDTH
    sideTabsLimit = limit
  }
}
