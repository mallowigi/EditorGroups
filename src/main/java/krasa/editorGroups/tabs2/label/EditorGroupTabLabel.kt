package krasa.editorGroups.tabs2.label

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.util.MathUtil
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import krasa.editorGroups.tabs2.impl.themes.EditorGroupsUI
import java.awt.*
import java.awt.event.*
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class EditorGroupTabLabel(
  private val tabs: KrTabsImpl,
  val info: EditorGroupTabInfo
) : JPanel(true), Accessible, UiCompatibleDataProvider {
  /** The label. */
  private val label: SimpleColoredComponent

  /** The component. */
  val labelComponent: JComponent
    get() = label

  /** Component wrapping the label. */
  private val labelPlaceholder = Wrapper(false)

  /** The icon. */
  private val icon: LayeredIcon

  /** The icon overlaid. */
  private val overlaidIcon: Icon? = null

  /** Indicates whether the tab label is currently being hovered by the mouse cursor. */
  var isHovered: Boolean = false
    get() = tabs.isHoveredTab(this)
    private set(value) {
      if (field == value) return
      field = value

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
        if (!KrTabsImpl.isSelectionClick(e) || !info.isEnabled) {
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

    return super.isFocusable()
  }

  /** Create the label, with support for small labels. */
  private fun createLabel(tabs: KrTabsImpl, info: EditorGroupTabInfo?): SimpleColoredComponent {
    val label: SimpleColoredComponent = object : SimpleColoredComponent() {
      override fun getFont(): Font? {
        val font = EditorGroupsUI.font()
        val useSmallLabels = EditorGroupsSettings.instance.isSmallLabels

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
    }, false)

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

  /** Display the tab menu action. */
  private fun handlePopup(e: MouseEvent) {
    // If there is already a popup, return
    if (e.clickCount != 1 || !e.isPopupTrigger || PopupUtil.getPopupContainerFor(this) != null) return

    // if event is out of bounds
    if (e.x < 0 || e.x >= e.component.width) return
    if (e.y < 0 || e.y >= e.component.height) return

    val place = tabs.popupPlace ?: ActionPlaces.UNKNOWN

    // Sets this tabInfo to the current tabs' popupInfo
    tabs.popupInfo = this.info

    // Add the tab actions
    val toShow = DefaultActionGroup()
    if (tabs.popupGroup != null) {
      toShow.addAll(tabs.popupGroup ?: return)
      toShow.addSeparator()
    }

    if (toShow.childrenCount == 0) return

    // Sets the popup to the activePopup prop
    tabs.activePopup = ActionManager.getInstance().createActionPopupMenu(place, toShow).component
    // Add the tabs' popup listener
    tabs.activePopup!!.addPopupMenuListener(tabs.popupListener)
    // Basic tabs listener
    tabs.activePopup!!.addPopupMenuListener(tabs)

    // Show the popup at the event's position
    JBPopupMenu.showByEvent(e, tabs.activePopup!!)
  }

  /** Apply decorations. */
  fun apply(decoration: TabUiDecorator.TabUiDecoration) {
    val decorations =
      mergeUiDecorations(decoration, defaultDecoration = KrTabsImpl.defaultDecorator.decoration)

    border = EmptyBorder(decorations.labelInsets)
    label.iconTextGap = decorations.iconTextGap

    val contentInsets = decorations.contentInsetsSupplier
    labelPlaceholder.border = EmptyBorder(contentInsets)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    tabs.tabPainterAdapter.paintBackground(label = this, g = g, tabs = tabs)
  }

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)

    if (labelComponent.parent == null) return

    val textBounds = SwingUtilities.convertRectangle(
      /* source = */
      labelComponent.parent,
      /* aRectangle = */
      labelComponent.bounds,
      /* destination = */
      this
    )

    // Paint border around label if we got the focus (screen readers)
    if (isFocusOwner) {
      g.color = UIUtil.getTreeSelectionBorderColor()
      UIUtil.drawDottedRectangle(
        /* g = */
        g,
        /* x = */
        textBounds.x,
        /* y = */
        textBounds.y,
        /* x1 = */
        textBounds.x + textBounds.width - 1,
        /* y1 = */
        textBounds.y + textBounds.height - 1
      )
    }

    if (overlaidIcon == null) return

    // Paint layered icon
    if (icon.isLayerEnabled(1)) {
      val top = (size.height - overlaidIcon.iconHeight) / 2

      overlaidIcon.paintIcon(this, g, textBounds.x - overlaidIcon.iconWidth / 2, top)
    }
  }

  override fun toString(): String = info.text

  /** When tab is enabled, enable the intrinsic component. */
  fun setTabEnabled(enabled: Boolean) {
    this.labelComponent.setEnabled(enabled)
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val iconWidth = label.icon?.iconWidth ?: JBUI.scale(ICON_WIDTH)
    val pointInLabel = RelativePoint(event).getPoint(label)

    // Show a tooltip of the current icon if available
    if (label.visibleRect.width >= iconWidth * 2 && label.findFragmentAt(pointInLabel.x) == SimpleColoredComponent.FRAGMENT_ICON) {
      icon.getToolTip(composite = false)?.let { return StringUtil.capitalize(it) }
    }

    return super.getToolTipText(event)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, info.component)
  }

  override fun getAccessibleContext(): AccessibleContext = accessibleContext ?: AccessibleTabLabel()

  /** Merged UI Decoration. */
  @JvmRecord
  data class MergedUiDecoration(
    val labelInsets: Insets,
    val contentInsetsSupplier: Insets,
    val iconTextGap: Int
  )

  /** For accessibility screens. */
  private inner class AccessibleTabLabel : AccessibleJPanel() {
    override fun getAccessibleName(): String? = super.getAccessibleName() ?: label.accessibleContext.accessibleName

    override fun getAccessibleDescription(): String? = super.getAccessibleDescription() ?: label.accessibleContext.accessibleDescription

    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PAGE_TAB
  }

  /** Custom Tab Label Layout. */
  private inner class TabLabelLayout : BorderLayout() {
    override fun layoutContainer(parent: Container) {
      val prefWidth = parent.preferredSize.width
      synchronized(parent.treeLock) {
        when {
          !isHovered && parent.width < prefWidth -> layoutScrollable(parent)
          else                                   -> super.layoutContainer(parent)
        }
      }
    }

    fun layoutScrollable(parent: Container) {
      val spaceTop = parent.insets.top
      val spaceLeft = parent.insets.left
      val spaceBottom = parent.height - parent.insets.bottom
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
        xOffset += prefWestWidth + hgap
      }
      return xOffset
    }
  }

  companion object {
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
        /* x1 = */
        rect.x.toFloat(),
        /* y1 = */
        rect.y.toFloat(),
        /* color1 = */
        fromColor,
        /* x2 = */
        (rect.x + rect.width).toFloat(),
        /* y2 = */
        rect.y.toFloat(),
        /* color2 = */
        toColor
      )
      g.fill(rect)
    }

    /**
     * Merges custom and default tab UI decorations into a single merged decoration.
     *
     * @param customDecoration The custom tab UI decoration, which may contain user-defined insets and gaps.
     * @param defaultDecoration The default tab UI decoration, serving as fallback values for missing elements in the custom decoration.
     * @return A MergedUiDecoration object that combines both custom and default values of insets and gaps for labels and content.
     */
    fun mergeUiDecorations(
      customDecoration: TabUiDecorator.TabUiDecoration,
      defaultDecoration: TabUiDecorator.TabUiDecoration
    ): MergedUiDecoration {
      val labelInsets = mergeInsets(
        customInsets = customDecoration.labelInsets,
        defaultInsets = defaultDecoration.labelInsets!!
      )

      val contentInsetsSupplier = when {
        customDecoration.contentInsetsSupplier != null -> mergeInsets(
          customInsets = customDecoration.contentInsetsSupplier,
          defaultInsets = defaultDecoration.contentInsetsSupplier!!
        )

        else                                           -> defaultDecoration.contentInsetsSupplier!!
      }

      val iconTextGap = when {
        customDecoration.iconTextGap != null -> customDecoration.iconTextGap
        else                                 -> defaultDecoration.iconTextGap!!
      }

      return MergedUiDecoration(
        labelInsets = labelInsets,
        contentInsetsSupplier = contentInsetsSupplier,
        iconTextGap = iconTextGap,
      )
    }

    private fun mergeInsets(customInsets: Insets?, defaultInsets: Insets): Insets {
      if (customInsets == null) return defaultInsets

      @Suppress("UseDPIAwareInsets")
      return Insets(
        getValue(defaultInsets.top, customInsets.top),
        getValue(defaultInsets.left, customInsets.left),
        getValue(defaultInsets.bottom, customInsets.bottom),
        getValue(defaultInsets.right, customInsets.right)
      )
    }

    private fun getValue(currentValue: Int, newValue: Int): Int = when {
      newValue != -1 -> newValue
      else           -> currentValue
    }
  }
}