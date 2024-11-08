// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.util.MathUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.tabs2.EditorGroupsTabsEx
import krasa.editorGroups.tabs2.impl.KrTabsImpl.Companion.isSelectionClick
import krasa.editorGroups.tabs2.impl.themes.EditorGroupsUI
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo
import krasa.editorGroups.tabs2.label.TabUiDecorator.TabUiDecoration
import java.awt.*
import java.awt.event.*
import java.util.*
import java.util.function.Function
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import kotlin.math.max
import kotlin.math.min

class EditorGroupTabLabel(
  private val tabs: KrTabsImpl, val info: EditorGroupTabInfo
) : JPanel(/* isDoubleBuffered = */ true), Accessible, UiCompatibleDataProvider {
  /** The label. */
  private val label: SimpleColoredComponent

  /** The component. */
  val labelComponent: JComponent
    get() = label

  /** Component wrapping the label. */
  private val labelPlaceholder = Wrapper(/* isDoubleBuffered = */ false)

  /** The icon. */
  private val icon: LayeredIcon

  /** The icon overlaid. */
  private val overlaidIcon: Icon? = null

  /** Indicates whether the tab label is currently being hovered by the mouse cursor. */
  var isHovered: Boolean = false
    get() = tabs.isHoveredTab(this)
    private set(value) {
      if (field == value) return
      when {
        value -> tabs.setHovered(label = this)
        else  -> tabs.unHover(label = this)
      }
    }

  /** Indicates if the current tab label is selected. */
  private val isSelected: Boolean
    get() = tabs.selectedLabel === this

  /** Gets the effective background, taking custom background into effect. */
  private val effectiveBackground: Color
    get() {
      val bg = tabs.tabPainter.getBackgroundColor()
      val customBg = tabs.tabPainter.getCustomBackground(
        tabColor = info.tabColor,
        selected = this.isSelected,
        active = tabs.isActiveTabs(this.info),
        hovered = this.isHovered
      )

      return when {
        customBg != null -> ColorUtil.alphaBlending(customBg, bg)
        else             -> bg
      }
    }

  init {
    label = createLabel(tabs = tabs, info = info)

    // Allow focus so that user can TAB into the selected TabLabel and then
    // navigate through the other tabs using the LEFT/RIGHT keys.
    isFocusable = ScreenReader.isActive()
    isOpaque = false
    layout = TabLabelLayout()

    labelPlaceholder.isOpaque = false
    labelPlaceholder.isFocusable = false
    label.isFocusable = false

    add(labelPlaceholder, BorderLayout.CENTER)
    setAlignmentToCenter()

    // Set a placeholder layered icons: one icon for the filetype, one for the states
    icon = object : LayeredIcon(layerCount = 2) {}

    // Support for tab select
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        // Right click
        if (!isSelectionClick(e) || !info.isEnabled) {
          handlePopup(e)
          return
        }

        // Select tab
        tabs.select(info = info, requestFocus = true)

        // Close previously opened right click popups
        val container = PopupUtil.getPopupContainerFor(this@EditorGroupTabLabel)
        if (container != null && ClientProperty.isTrue(container.content, MorePopupAware::class.java)) {
          container.cancel()
        }
      }

      override fun mouseClicked(e: MouseEvent) {
        handlePopup(e)
      }

      override fun mouseReleased(e: MouseEvent) {
        handlePopup(e)
      }

      override fun mouseEntered(e: MouseEvent?) {
        isHovered = true
      }

      override fun mouseExited(e: MouseEvent?) {
        isHovered = false
      }
    })

    // For screen readers
    if (isFocusable) {
      // Navigate to the previous/next tab when LEFT/RIGHT is pressed.
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          when (e.keyCode) {
            KeyEvent.VK_LEFT  -> {
              val index = tabs.getIndexOf(info)
              if (index >= 0) {
                e.consume()
                // Select the previous tab, then set the focus its label.
                val previous = tabs.findEnabledBackward(index, cycle = true)
                if (previous != null) {
                  tabs.select(previous, requestFocus = false).doWhenDone {
                    tabs.selectedLabel!!.requestFocusInWindow()
                  }
                }
              }
            }

            KeyEvent.VK_RIGHT -> {
              val index = tabs.getIndexOf(info)
              if (index >= 0) {
                e.consume()
                // Select the previous tab, then set the focus its label.
                val next = tabs.findEnabledForward(index, cycle = true)
                if (next != null) {
                  tabs.select(next, requestFocus = false).doWhenDone {
                    tabs.selectedLabel!!.requestFocusInWindow()
                  }
                }
              }
            }
          }
        }
      })

      // Repaint when we gain/lost focus so that the focus cue is displayed.
      addFocusListener(object : FocusListener {
        override fun focusGained(e: FocusEvent?) = repaint()

        override fun focusLost(e: FocusEvent?) = repaint()
      })
    }
  }

  /** Determines whether this tab label can gain focus. */
  override fun isFocusable(): Boolean {
    // We don't want the focus unless we are the selected tab.
    if (tabs.selectedLabel !== this) return false

    @Suppress("UsePropertyAccessSyntax") return super.isFocusable()
  }

  /** Create the label, with support for small labels. */
  private fun createLabel(tabs: KrTabsImpl, info: EditorGroupTabInfo?): SimpleColoredComponent {
    val label: SimpleColoredComponent = object : SimpleColoredComponent() {
      override fun getFont(): Font? {
        val font = EditorGroupsUI.font()
        val useSmallLabels = tabs.useSmallLabels()

        return when {
          isFontSet || !useSmallLabels -> font
          else                         -> RelativeFont.NORMAL.small().derive(font)
        }
      }

      override fun getActiveTextColor(attributesColor: Color?): Color? {
        val painterAdapter = tabs.tabPainterAdapter
        val theme = painterAdapter.getTabTheme()

        val hasDifferentColor = attributesColor == null || UIUtil.getLabelForeground() == attributesColor
        val foreground = when {
          tabs.selectedInfo == info && hasDifferentColor ->
            when {
              tabs.isActiveTabs(info) -> theme.underlinedTabForeground
              else                    -> theme.underlinedTabInactiveForeground
            }

          else                                           -> super.getActiveTextColor(attributesColor)
        }
        return foreground
      }
    }

    label.isOpaque = false
    label.border = null
    label.isIconOpaque = false
    label.ipad = JBUI.emptyInsets()

    return label
  }

  /** Returns the size of the tabs panel. */
  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    when {
      EditorGroupsSettings.instance.isCompactTabs -> size.height = EditorGroupsUI.compactTabHeight()
      else                                        -> size.height = EditorGroupsUI.tabHeight()
    }
    return size
  }

  /** Aligns the content to the center of the tab. */
  fun setAlignmentToCenter() {
    if (labelComponent.parent != null) return

    setPlaceholderContent(component = labelComponent)
  }

  /** Rerender label placeholder. */
  private fun setPlaceholderContent(component: JComponent) {
    labelPlaceholder.removeAll()

    val content = Centerizer(component, Centerizer.TYPE.BOTH)
    labelPlaceholder.setContent(content)
  }

  override fun paint(g: Graphics) {
    doPaint(g)
    // Paint the semi transparent fadeout when scrolling
    paintFadeout(g)
  }

  private fun doPaint(g: Graphics?) = super.paint(g)

  /** Paint the fadeout. */
  private fun paintFadeout(g: Graphics) {
    val g2d = g.create() as Graphics2D
    val fadeoutDefaultWidth = Registry.intValue("ide.editor.tabs.fadeout.width", FADEOUT_WIDTH)

    try {
      val tabBg = effectiveBackground
      val transparent = ColorUtil.withAlpha(tabBg, 0.0)
      val borderThickness = tabs.borderThickness
      val fadeoutWidth = JBUI.scale(MathUtil.clamp(fadeoutDefaultWidth, 1, FADEOUT_MAX))

      val contentRect = labelPlaceholder.bounds
      val rect = bounds
      rect.height -= borderThickness + when {
        this.isSelected -> tabs.tabPainter.getTabTheme().underlineHeight
        else            -> borderThickness
      }

      // Fadeout for left part when scrolling
      if (rect.x < 0) {
        val leftRect = Rectangle(-rect.x, borderThickness, fadeoutWidth, rect.height - 2 * borderThickness)
        paintGradientRect(g2d, leftRect, tabBg, transparent)
      }
    } finally {
      g2d.dispose()
    }
  }

  /**
   * Sets the text for the tab label and updates the visual representation of the label accordingly.
   *
   * @param text The `SimpleColoredText` to be displayed on the label. If null, the label will be cleared.
   */
  fun setText(text: SimpleColoredText?) {
    label.change({
      label.clear()
      label.icon = when {
        hasIcons() -> icon
        else       -> null
      }

      text?.appendToComponent(label)
    }, /* autoInvalidate = */ false)

    invalidateIfNeeded()
  }

  /** Replace the current icon at layer 0. */
  fun setIcon(icon: Icon?): Unit = setIcon(icon = icon, layer = 0)

  /**
   * Invalidates the label component and triggers a revalidation and repaint of the tabs if necessary.
   *
   * This method first checks if the `labelComponent` is properly associated with a `rootPane`. If the `labelComponent`'s current size is
   * equal to its preferred size, the method does nothing. Otherwise, it invalidates the `labelComponent` and calls `revalidateAndRepaint`
   * on the `tabs`.
   */
  private fun invalidateIfNeeded() {
    if (labelComponent.rootPane == null) return

    val labelDimensions = labelComponent.size
    val prefSize = labelComponent.getPreferredSize()
    if (labelDimensions != null && labelDimensions == prefSize) return

    labelComponent.invalidate()
    tabs.revalidateAndRepaint(false)
  }

  /** Whether there is at least one icon in layers. */
  private fun hasIcons(): Boolean = icon.allLayers.any { it != null }

  /** Sets the icon at the given layer. */
  @Suppress("SameParameterValue")
  private fun setIcon(icon: Icon?, layer: Int) {
    val layeredIcon = this.icon
    layeredIcon.setIcon(icon, layer)

    when {
      hasIcons() -> label.setIcon(layeredIcon)
      else       -> label.setIcon(null)
    }

    invalidateIfNeeded()
  }

  private fun handlePopup(e: MouseEvent) {
    if (e.getClickCount() != 1 || !e.isPopupTrigger || PopupUtil.getPopupContainerFor(this) != null) return

    if (e.getX() < 0 || e.getX() >= e.component.getWidth() || e.getY() < 0 || e.getY() >= e.component.getHeight()) return

    var place = tabs.popupPlace
    place = if (place != null) place else ActionPlaces.UNKNOWN
    tabs.popupInfo = this.info

    val toShow = DefaultActionGroup()
    if (tabs.popupGroup != null) {
      toShow.addAll(tabs.popupGroup!!)
      toShow.addSeparator()
    }

    val tabs = EditorGroupsTabsEx.NAVIGATION_ACTIONS_KEY.getData(
      DataManager.getInstance().getDataContext(e.component, e.getX(), e.getY())
    ) as KrTabsImpl
    if (tabs === this@EditorGroupTabLabel.tabs && tabs.addNavigationGroup) {
      toShow.addAll(tabs.navigationActions)
    }

    if (toShow.childrenCount == 0) return

    tabs.activePopup = ActionManager.getInstance().createActionPopupMenu(place, toShow).component
    tabs.activePopup!!.addPopupMenuListener(tabs.popupListener)

    tabs.activePopup!!.addPopupMenuListener(this@EditorGroupTabLabel.tabs)
    JBPopupMenu.showByEvent(e, tabs.activePopup!!)
  }

  fun apply(decoration: TabUiDecoration) {
    val resultDec = mergeUiDecorations(decoration, KrTabsImpl.defaultDecorator.getDecoration())
    border = EmptyBorder(resultDec.labelInsets)
    label.iconTextGap = resultDec.iconTextGap

    val contentInsets = resultDec.contentInsetsSupplier.apply(actionsPosition)
    labelPlaceholder.border = EmptyBorder(contentInsets)
  }

  private val isShowTabActions: Boolean
    get() = true

  private val isTabActionsOnTheRight: Boolean
    get() = true

  val actionsPosition: ActionsPosition
    get() = if (this.isShowTabActions) if (this.isTabActionsOnTheRight) ActionsPosition.RIGHT else ActionsPosition.LEFT
    else ActionsPosition.NONE

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    paintBackground(g)
  }

  private fun paintBackground(g: Graphics) {
    tabs.tabPainterAdapter.paintBackground(label = this, g = g, tabs = tabs)
  }

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)

    if (this.labelComponent.getParent() == null) {
      return
    }

    val textBounds = SwingUtilities.convertRectangle(this.labelComponent.getParent(), this.labelComponent.bounds, this)
    // Paint border around label if we got the focus
    if (isFocusOwner) {
      g.color = UIUtil.getTreeSelectionBorderColor()
      UIUtil.drawDottedRectangle(g, textBounds.x, textBounds.y, textBounds.x + textBounds.width - 1, textBounds.y + textBounds.height - 1)
    }

    if (overlaidIcon == null) {
      return
    }

    if (icon.isLayerEnabled(1)) {
      val top = (size.height - overlaidIcon.iconHeight) / 2

      overlaidIcon.paintIcon(this, g, textBounds.x - overlaidIcon.iconWidth / 2, top)
    }
  }

  override fun toString(): String = info.text

  fun setTabEnabled(enabled: Boolean) {
    this.labelComponent.setEnabled(enabled)
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val iconWidth = label.icon?.iconWidth ?: JBUI.scale(ICON_WIDTH)
    val pointInLabel = RelativePoint(event).getPoint(label)

    if (label.visibleRect.width >= iconWidth * 2 && label.findFragmentAt(pointInLabel.x) == SimpleColoredComponent.FRAGMENT_ICON) {
      icon.getToolTip(composite = false)?.let { return StringUtil.capitalize(it) }
    }
    return super.getToolTipText(event)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, info.component)
  }

  enum class ActionsPosition {
    RIGHT, LEFT, NONE
  }

  @JvmRecord
  data class MergedUiDecoration(
    val labelInsets: Insets, val contentInsetsSupplier: Function<ActionsPosition?, Insets>, val iconTextGap: Int
  )

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleTabLabel()
    }
    return accessibleContext
  }

  private inner class AccessibleTabLabel : AccessibleJPanel() {
    override fun getAccessibleName(): String? {
      var name = super.getAccessibleName()
      if (name == null) {
        name = label.getAccessibleContext().getAccessibleName()
      }
      return name
    }

    override fun getAccessibleDescription(): String? {
      var description = super.getAccessibleDescription()
      if (description == null) {
        description = label.getAccessibleContext().getAccessibleDescription()
      }
      return description
    }

    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PAGE_TAB
  }

  private inner class TabLabelLayout : BorderLayout() {
    override fun addLayoutComponent(comp: Component?, constraints: Any?) {
      checkConstraints(constraints)
      super.addLayoutComponent(comp, constraints)
    }

    override fun layoutContainer(parent: Container) {
      val prefWidth = parent.preferredSize.width
      synchronized(parent.treeLock) {
        if (tabs.effectiveLayout!!.isScrollable && (!isHovered || tabs.isHorizontalTabs) && isShowTabActions && isTabActionsOnTheRight && parent.getWidth() < prefWidth) {
          layoutScrollable(parent)
        } else if (!isHovered && !isSelected && parent.getWidth() < prefWidth) {
          layoutCompressible(parent)
        } else {
          super.layoutContainer(parent)
        }
      }
    }

    fun layoutScrollable(parent: Container) {
      val spaceTop = parent.insets.top
      val spaceLeft = parent.insets.left
      val spaceBottom = parent.getHeight() - parent.insets.bottom
      val spaceHeight = spaceBottom - spaceTop

      var xOffset = spaceLeft
      xOffset = layoutComponent(xOffset, getLayoutComponent(WEST), spaceTop, spaceHeight)
      xOffset = layoutComponent(xOffset, getLayoutComponent(CENTER), spaceTop, spaceHeight)
      layoutComponent(xOffset, getLayoutComponent(EAST), spaceTop, spaceHeight)
    }

    fun layoutComponent(xOffset: Int, component: Component?, spaceTop: Int, spaceHeight: Int): Int {
      var xOffset = xOffset
      if (component != null) {
        val prefWestWidth = component.preferredSize.width
        component.setBounds(xOffset, spaceTop, prefWestWidth, spaceHeight)
        xOffset += prefWestWidth + getHgap()
      }
      return xOffset
    }

    fun layoutCompressible(parent: Container) {
      val insets = parent.insets
      val height = parent.getHeight() - insets.bottom - insets.top
      var curX = insets.left
      val maxX = parent.getWidth() - insets.right

      val left = getLayoutComponent(WEST)
      val center = getLayoutComponent(CENTER)
      val right = getLayoutComponent(EAST)

      if (left != null) {
        left.setBounds(0, 0, 0, 0)
        val decreasedLen = parent.preferredSize.width - parent.getWidth()
        val width = max((left.preferredSize.width - decreasedLen).toDouble(), 0.0).toInt()
        curX += width
      }

      if (center != null) {
        val width = min(center.preferredSize.width.toDouble(), (maxX - curX).toDouble()).toInt()
        center.setBounds(curX, insets.top, width, height)
      }

      if (right != null) {
        right.setBounds(0, 0, 0, 0)
      }
    }

    private fun checkConstraints(constraints: Any?) {
      if (NORTH == constraints || SOUTH == constraints) {
        LOG.warn(IllegalArgumentException("constraints=" + constraints))
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(EditorGroupTabLabel::class.java)
    private const val FADEOUT_WIDTH: Int = 10
    private const val FADEOUT_MAX: Int = 200
    private const val ICON_WIDTH: Int = 16

    /**
     * Paints a rectangular area with a horizontal gradient.
     *
     * @param g The Graphics2D context to draw with.
     * @param rect The rectangle area to be filled with the gradient.
     * @param fromColor The starting color of the gradient.
     * @param toColor The ending color of the gradient.
     */
    private fun paintGradientRect(g: Graphics2D, rect: Rectangle, fromColor: Color, toColor: Color) {
      g.paint = GradientPaint(
        /* x1 = */ rect.x.toFloat(),
        /* y1 = */ rect.y.toFloat(),
        /* color1 = */ fromColor,
        /* x2 = */ (rect.x + rect.width).toFloat(),
        /* y2 = */ rect.y.toFloat(),
        /* color2 = */ toColor
      )
      g.fill(rect)
    }

    fun mergeUiDecorations(
      customDec: TabUiDecoration,
      defaultDec: TabUiDecoration
    ): MergedUiDecoration {
      val contentInsetsSupplier = Function { position: ActionsPosition? ->
        val def = Objects.requireNonNull<Function<ActionsPosition, Insets>?>(defaultDec.contentInsetsSupplier).apply(
          position!!
        )
        if (customDec.contentInsetsSupplier != null) {
          return@Function mergeInsets(customDec.contentInsetsSupplier.apply(position), def)
        }
        def
      }
      return MergedUiDecoration(
        mergeInsets(customDec.labelInsets, Objects.requireNonNull<Insets?>(defaultDec.labelInsets)),
        contentInsetsSupplier,
        ObjectUtils.notNull<Int?>(customDec.iconTextGap, Objects.requireNonNull<Int?>(defaultDec.iconTextGap))
      )
    }

    private fun mergeInsets(custom: Insets?, def: Insets): Insets {
      if (custom != null) {
        return JBInsets.addInsets(
          Insets(
            getValue(def.top, custom.top),
            getValue(def.left, custom.left),
            getValue(def.bottom, custom.bottom),
            getValue(def.right, custom.right)
          )
        )
      }
      return def
    }

    private fun getValue(currentValue: Int, newValue: Int): Int {
      return if (newValue != -1) newValue else currentValue
    }
  }
}
