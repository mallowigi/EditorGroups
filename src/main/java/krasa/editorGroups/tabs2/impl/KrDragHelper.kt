// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl

import com.intellij.ide.ui.UISettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.reference.SoftReference
import com.intellij.ui.InplaceButton
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.util.Axis
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import krasa.editorGroups.tabs2.KrTabInfo
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.SwingUtilities

open class KrDragHelper protected constructor(
  private val tabs: KrTabsImpl,
  parentDisposable: Disposable
) : MouseDragHelper<KrTabsImpl>(
  parentDisposable,
  tabs
) {
  var dragSource: KrTabInfo? = null
    private set

  private var dragOriginalRec: Rectangle? = null

  var dragRec: Rectangle? = null
  private var myHoldDelta: Dimension? = null

  private var myDragOutSource: KrTabInfo? = null
  private var myPressedTabLabel: Reference<KrTabLabel?>? = null

  override fun isDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point): Boolean {
    if (this.dragSource == null || !dragSource!!.canBeDraggedOut()) return false

    val label = tabs.getInfoToLabel()[this.dragSource]
    if (label == null) return false

    val dX = dragToScreenPoint.x - startScreenPoint.x
    val dY = dragToScreenPoint.y - startScreenPoint.y

    return tabs.isDragOut(label, dX, dY)
  }

  override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
    if (!checkModifiers(event)) {
      if (myDragOutSource != null) processDragOutCancel()
      return
    }

    val delegate = myDragOutSource!!.dragOutDelegate
    if (justStarted) {
      delegate!!.dragOutStarted(event, myDragOutSource!!)
    }

    delegate!!.processDragOut(event, myDragOutSource!!)
    event.consume()
  }

  override fun processDragOutFinish(event: MouseEvent) {
    if (!checkModifiers(event)) {
      if (myDragOutSource != null) processDragOutCancel()
      return
    }

    super.processDragOutFinish(event)

    val wasSorted: Boolean = prepareDisableSorting()

    try {
      myDragOutSource!!.dragOutDelegate!!.dragOutFinished(event, myDragOutSource!!)
    } finally {
      disableSortingIfNeed(event, wasSorted)
      myDragOutSource = null
    }
  }

  override fun processDragOutCancel() {
    myDragOutSource!!.dragOutDelegate!!.dragOutCancelled(myDragOutSource!!)
    myDragOutSource = null
  }

  override fun processMousePressed(event: MouseEvent) {
    // since selection change can cause tabs to be reordered, we need to remember the tab on which the mouse was pressed, otherwise
    // we'll end up dragging the wrong tab (IDEA-65073)
    val label = findLabel(RelativePoint(event).getPoint(tabs))
    myPressedTabLabel = when (label) {
      null -> null
      else -> WeakReference<KrTabLabel?>(label)
    }
  }

  override fun processDrag(event: MouseEvent, targetScreenPoint: Point, startPointScreen: Point) {
    if (!tabs.isTabDraggingEnabled || !isDragSource(event) || !checkModifiers(event)) return

    SwingUtilities.convertPointFromScreen(startPointScreen, tabs)

    if (isDragJustStarted) {
      val pressedTabLabel = SoftReference.dereference<KrTabLabel?>(myPressedTabLabel)
      if (pressedTabLabel == null) return

      val labelBounds = pressedTabLabel.bounds

      myHoldDelta = Dimension(startPointScreen.x - labelBounds.x, startPointScreen.y - labelBounds.y)
      this.dragSource = pressedTabLabel.info
      dragRec = Rectangle(startPointScreen, labelBounds.size)
      dragOriginalRec = dragRec!!.clone() as Rectangle

      dragOriginalRec!!.x -= myHoldDelta!!.width
      dragOriginalRec!!.y -= myHoldDelta!!.height

      val delegate = dragSource!!.dragDelegate
      delegate?.dragStarted(event)
    } else {
      if (dragRec == null) return

      val toPoint = SwingUtilities.convertPoint(event.component, event.getPoint(), tabs)

      dragRec!!.x = toPoint.x
      dragRec!!.y = toPoint.y
    }

    dragRec!!.x -= myHoldDelta!!.width
    dragRec!!.y -= myHoldDelta!!.height

    val headerRec: Rectangle = tabs.lastLayoutPass!!.headerRectangle!!
    ScreenUtil.moveToFit(dragRec!!, headerRec, null)

    val deadZoneX = 0
    val deadZoneY = 0

    val top = findLabel(Point(dragRec!!.x + dragRec!!.width / 2, dragRec!!.y + deadZoneY))
    val bottom = findLabel(Point(dragRec!!.x + dragRec!!.width / 2, dragRec!!.y + dragRec!!.height - deadZoneY))
    val left = findLabel(Point(dragRec!!.x + deadZoneX, dragRec!!.y + dragRec!!.height / 2))
    val right = findLabel(Point(dragRec!!.x + dragRec!!.width - deadZoneX, dragRec!!.y + dragRec!!.height / 2))

    var targetLabel = when {
      tabs.isHorizontalTabs -> {
        findMostOverlapping(Axis.X, left, right) ?: findMostOverlapping(Axis.Y, top, bottom)
      }

      else                  -> {
        findMostOverlapping(Axis.Y, top, bottom) ?: findMostOverlapping(Axis.X, left, right)
      }
    }

    if (targetLabel != null) {
      val saved = dragRec
      dragRec = null

      tabs.reallocate(this.dragSource, targetLabel.info)

      dragOriginalRec = tabs.getInfoToLabel().get(this.dragSource)!!.bounds
      dragRec = saved

      tabs.moveDraggedTabLabel()
    } else {
      tabs.moveDraggedTabLabel()

      val border = tabs.borderThickness
      headerRec.x -= border
      headerRec.y -= border
      headerRec.width += border * 2
      headerRec.height += border * 2

      tabs.repaint(headerRec)
    }
    event.consume()
  }

  private fun isDragSource(event: MouseEvent): Boolean {
    val source = event.getSource()
    if (source is Component) {
      return SwingUtilities.windowForComponent(tabs) === SwingUtilities.windowForComponent(source)
    }
    return false
  }

  private fun findMostOverlapping(measurer: Axis, vararg labels: KrTabLabel?): KrTabLabel? {

    val freeSpace: Double = when {
      measurer.getMinValue(dragRec) < measurer.getMinValue(dragOriginalRec) -> {
        (measurer.getMaxValue(dragOriginalRec) - measurer.getMaxValue(dragRec)).toDouble()
      }

      else                                                                  -> {
        (measurer.getMinValue(dragRec) - measurer.getMinValue(dragOriginalRec)).toDouble()
      }
    }

    var max = -1
    var maxLabel: KrTabLabel? = null
    for (each in labels) {
      if (each == null) continue

      val eachBounds = each.bounds
      if (measurer.getSize(eachBounds) > freeSpace + freeSpace * 0.3) continue

      val intersection = dragRec!!.intersection(eachBounds)
      val size = intersection.width * intersection.height
      if (size > max) {
        max = size
        maxLabel = each
      }
    }

    return maxLabel
  }

  private fun findLabel(dragPoint: Point?): KrTabLabel? {
    val at = tabs.findComponentAt(dragPoint)
    if (at is InplaceButton) return null
    val label = findLabel(at)

    return when {
      label != null && label.getParent() === tabs && label.info !== this.dragSource -> label
      else                                                                          -> null
    }
  }

  private fun findLabel(c: Component?): KrTabLabel? {
    var eachParent = c
    while (eachParent != null && eachParent !== tabs) {
      if (eachParent is KrTabLabel) return eachParent
      eachParent = eachParent.getParent()
    }

    return null
  }

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean = findLabel(dragComponentPoint) != null

  override fun canFinishDragging(component: JComponent, point: RelativePoint): Boolean {
    var realDropTarget = UIUtil.getDeepestComponentAt(point.originalComponent, point.originalPoint.x, point.originalPoint.y)

    if (realDropTarget == null) {
      realDropTarget =
        SwingUtilities.getDeepestComponentAt(point.originalComponent, point.originalPoint.x, point.originalPoint.y)
    }

    if (tabs.getVisibleInfos().isEmpty() && realDropTarget != null) {
      val tabs = UIUtil.getParentOfType<KrTabsImpl?>(KrTabsImpl::class.java, realDropTarget)
      if (tabs == null || !tabs.isEditorTabs) return false
    }

    return !tabs.contains(point.getPoint(tabs)) || !tabs.getVisibleInfos().isEmpty()
  }

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    val checkModifiers = checkModifiers(event)
    if (!checkModifiers && this.dragSource == null) return

    super.processDragFinish(event, willDragOutStart)

    val wasSorted = !willDragOutStart && prepareDisableSorting()

    try {
      endDrag(willDragOutStart && checkModifiers)
    } finally {
      disableSortingIfNeed(event, wasSorted)
    }
  }

  private fun endDrag(willDragOutStart: Boolean) {
    if (willDragOutStart) {
      myDragOutSource = this.dragSource
    }

    tabs.resetTabsCache()
    if (!willDragOutStart) {
      tabs.fireTabsMoved()
    }

    tabs.relayout(true, false)

    tabs.revalidate()

    val delegate = dragSource!!.dragDelegate
    delegate?.dragFinishedOrCanceled()

    this.dragSource = null
    dragRec = null
  }

  override fun processDragCancel() {
    endDrag(false)
  }

  companion object {
    private fun prepareDisableSorting(): Boolean {
      val wasSorted = UISettings.getInstance().sortTabsAlphabetically
      if (wasSorted && !UISettings.getInstance().alwaysKeepTabsAlphabeticallySorted) {
        UISettings.getInstance().sortTabsAlphabetically = false
      }
      return wasSorted
    }

    private fun disableSortingIfNeed(event: MouseEvent, wasSorted: Boolean) {
      if (!wasSorted) return
      val uiSettings = UISettings.getInstance()

      if (event.isConsumed || uiSettings.alwaysKeepTabsAlphabeticallySorted) { // new container for separate window was created, see DockManagerImpl.MyDragSession
        uiSettings.sortTabsAlphabetically = true
        return
      }

      uiSettings.fireUISettingsChanged()

      ApplicationManager.getApplication().invokeLater(Runnable {
        val notification =
          Notification(
            "Editor Groups",
            "Alphabetical tabs order is turned on",
            "",
            NotificationType.INFORMATION
          )

        notification.addAction(
          DumbAwareAction.create("Enable sorting", Consumer { e: AnActionEvent? ->
            uiSettings.sortTabsAlphabetically = true
            uiSettings.fireUISettingsChanged()
            notification.expire()
          })
        )
          .addAction(
            DumbAwareAction.create("Always keep sorting enabled", Consumer { e: AnActionEvent? ->
              uiSettings.alwaysKeepTabsAlphabeticallySorted = true
              uiSettings.sortTabsAlphabetically = true
              uiSettings.fireUISettingsChanged()
              notification.expire()
            })
          )
        Notifications.Bus.notify(notification)
      })
    }
  }
}
