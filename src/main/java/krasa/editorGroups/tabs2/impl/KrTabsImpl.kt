@file:Suppress("detekt:All")

package krasa.editorGroups.tabs2.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.fill2DRoundRect
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.hover.HoverListener
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.util.Alarm
import com.intellij.util.Function
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.*
import kotlinx.coroutines.Runnable
import krasa.editorGroups.support.lazyUiDisposable
import krasa.editorGroups.tabs2.*
import krasa.editorGroups.tabs2.border.EditorGroupsTabsBorder
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsDefaultTabPainterAdapter
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainter
import krasa.editorGroups.tabs2.impl.painter.EditorGroupsTabPainterAdapter
import krasa.editorGroups.tabs2.impl.singleRow.EditorGroupsLayoutPassInfo
import krasa.editorGroups.tabs2.impl.singleRow.EditorGroupsScrollableSingleRowLayout
import krasa.editorGroups.tabs2.impl.singleRow.EditorGroupsSingleRowLayout
import krasa.editorGroups.tabs2.impl.singleRow.EditorGroupsSingleRowPassInfo
import krasa.editorGroups.tabs2.impl.themes.EditorGroupTabTheme
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo
import krasa.editorGroups.tabs2.label.EditorGroupTabLabel
import krasa.editorGroups.tabs2.label.TabUiDecorator
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier
import javax.accessibility.Accessible
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.ChangeListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.ComponentUI
import kotlin.math.max

private val LOG = logger<KrTabsImpl>()

@Suppress("detekt:LargeClass", "detekt:MagicNumber", "detekt:StringLiteralDuplication")
@DirtyUI
open class KrTabsImpl(
  private var project: Project?,
  private val parentDisposable: Disposable,
) : JComponent(),
  EditorGroupsTabsEx,
  PropertyChangeListener,
  UiDataProvider,
  PopupMenuListener,
  KrTabsPresentation,
  UISettingsListener,
  QuickActionProvider,
  MorePopupAware,
  Accessible {
  private val scrollBarActivityTracker = ScrollBarActivityTracker()

  // List of visible tabs
  internal val visibleTabInfos = ArrayList<EditorGroupTabInfo>()

  // Map tab -> index
  private val hiddenInfos = HashMap<EditorGroupTabInfo, Int>()

  // TODO correct to selectedInfo
  var mySelectedInfo: EditorGroupTabInfo? = null

  // The more tabs toolbar
  val moreToolbar: ActionToolbar?

  // Returns default one action horizontal toolbar size (26x24)
  val moreToolbarPreferredSize: Dimension
    get() {
      val baseSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      return Dimension(
        baseSize.width + JBUI.scale(4),
        baseSize.height + JBUI.scale(2)
      )
    }

  // Header fit dimension
  var headerFitSize: Dimension? = null

  // Tab panel inner insets
  private var innerInsets: Insets = JBUI.emptyInsets()

  // Mouse listeners
  private val tabMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList<EventListener>()

  // Listeners
  private val tabListeners = ContainerUtil.createLockFreeCopyOnWriteList<EditorGroupsTabsListener>()

  // Whether the tab panel is focused
  private var isFocused = false

  // Provider for the popup group
  private var popupGroupSupplier: (() -> ActionGroup)? = null

  // The place of popup
  var popupPlace: String? = null

  // The tabinfo of the current popup
  var popupInfo: EditorGroupTabInfo? = null

  // Listener on the tabs popup
  val popupListener: PopupMenuListener

  // Current opened popup
  var activePopup: JPopupMenu? = null

  // The popup group
  val popupGroup: ActionGroup?
    get() = popupGroupSupplier?.invoke()

  // Provides data to be consumed by others
  private var dataProvider: DataProvider? = null

  // Tabs to remove in the next layout pass
  private val deferredToRemove = WeakHashMap<Component, Component>()

  // todo add override
  // val override tabsPosition: EditorGroupsTabsPosition
  //   get() = tabListOptions.tabPosition

  // The row layout to lay the tabs over
  internal var effectiveLayout: EditorGroupsTabLayout? = createRowLayout()

  // Keep a cache of the last layout pass
  var lastLayoutPass: EditorGroupsLayoutPassInfo? = null
    private set

  // Flag to indicate if the tabs are relayout
  internal var forcedRelayout: Boolean = false
    private set

  // Instance of tab decorator
  internal var uiDecorator: TabUiDecorator? = null

  // Keep a state for recent activity for the scrollbar
  val isRecentlyActive: Boolean
    get() = scrollBarActivityTracker.isRecentlyActive

  // tabs cache
  private var allTabs: List<EditorGroupTabInfo>? = null

  // Focus manager
  private var focusManager = IdeFocusManager.getGlobalInstance()

  // Number of deferred tabs to remove
  private var removeDeferredRequest: Long = 0

  // Tab Border
  private val tabBorder = createTabBorder()

  // cache the current tab info before switching
  private var oldSelection: EditorGroupTabInfo? = null

  // The handler or tab selection
  private var selectionChangeHandler: EditorGroupsTabsBase.SelectionChangeHandler? = null

  // deferred focus request
  private var deferredFocusRequest: Runnable? = null

  // First tab offset
  internal var firstTabOffset = 0

  /** The tab painter adapter. */
  @JvmField
  internal val tabPainterAdapter: EditorGroupsTabPainterAdapter = createTabPainterAdapter()

  /** The tab painter. */
  val tabPainter: EditorGroupsTabPainter = tabPainterAdapter.tabPainter

  // Needed for scroll
  var isMouseInsideTabsArea: Boolean = false
    private set

  // flag to warn if remove in progress
  private var removeNotifyInProgress = false

  // Current tab label
  private var tabLabelAtMouse: EditorGroupTabLabel? = null

  // Scrollbar
  private val scrollBar: JBScrollBar

  // Scrollbar listener
  private val scrollBarChangeListener: ChangeListener

  // Whether the scroll is on
  private var scrollBarOn = false

  // Scrollbar model
  private val scrollBarModel: BoundedRangeModel
    get() = scrollBar.model

  // Layout insets
  val layoutInsets: Insets
    get() = tabBorder.effectiveBorder

  // Keep the selected label
  val selectedLabel: EditorGroupTabLabel?
    get() = selectedInfo?.tabLabel

  // Delegate to border's thickness
  val borderThickness: Int
    get() = tabBorder.thickness

  // The tab gap
  val tabHGap: Int
    get() = -tabBorder.thickness

  // is empty visible
  override val isEmptyVisible: Boolean
    get() = visibleTabInfos.isEmpty()

  // Cache the tab size
  override val tabCount: Int
    get() = tabs.size

  // Whether we should sort the tabs alphabetically
  val isAlphabeticalMode: Boolean
    get() = true // TODO add setting for it

  /** The list of tabs. */
  override val tabs: List<EditorGroupTabInfo>
    get() {
      // If allTabs is not null, it means that the tabs are already sorted and we can return them directly.
      this.allTabs?.let { return it }

      val result = visibleTabInfos.toMutableList()
      for (tabInfo in hiddenInfos.keys) {
        result.add(getIndexInHiddenInfos(tabInfo), tabInfo)
      }

      if (isAlphabeticalMode) {
        sortTabsAlphabetically(result)
      }

      this.allTabs = result
      return result
    }

  /** The currently selected tab. */
  override val selectedInfo: EditorGroupTabInfo?
    get() = when {
      oldSelection != null                     -> oldSelection
      mySelectedInfo == null                   -> visibleTabInfos.firstOrNull()
      visibleTabInfos.contains(mySelectedInfo) -> mySelectedInfo
      else                                     -> null
    }

  // Map infos to labels
  val infoToLabel: MutableMap<EditorGroupTabInfo, EditorGroupTabLabel> = HashMap()

  var position: EditorGroupsTabsPosition = EditorGroupsTabsPosition.TOP
    private set

  /** @return insets, that should be used to layout [KrTabsImpl.moreToolbar] */
  val actionsInsets: Insets = JBInsets.create(Insets(0, 5, 0, 8))

  // Return the focused component
  private val toFocus: JComponent?
    get() {
      val info = selectedInfo ?: return null
      var toFocus: JComponent? = info.component
      if (toFocus == null || !toFocus.isShowing) return null

      val policyToFocus = focusManager.getFocusTargetFor(toFocus)
      if (policyToFocus != null) {
        return policyToFocus
      }

      return toFocus
    }

  init {
    isOpaque = true
    background = tabPainter.getBackgroundColor()
    border = tabBorder
    // reset decorations
    setUiDecorator(null)
    setLayout(createRowLayout())

    // Add disposer for popup
    popupListener = object : PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}

      override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = disposePopupListener()

      override fun popupMenuCanceled(e: PopupMenuEvent) = disposePopupListener()
    }

    val actionManager = ActionManager.getInstance()

    // More tabs toolbar
    @Suppress("UnresolvedPluginConfigReference")
    moreToolbar = createToolbar(
      group = DefaultActionGroup(actionManager.getAction("TabList")),
      targetComponent = this,
      actionManager = actionManager
    )
    add(moreToolbar.component)

    // This scroll pane won't be shown on screen, it is needed only to handle scrolling events and properly update a scrolling model
    val fakeScrollPane = JBScrollPane(
      ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
    )
    scrollBar = object : JBScrollBar(HORIZONTAL) {
      override fun updateUI() {
        super.updateUI()
        val fontSize = JBFont.labelFontSize()
        setUnitIncrement(fontSize)
        setBlockIncrement(fontSize * 10)
      }

      override fun isThin(): Boolean = true
    }

    fakeScrollPane.verticalScrollBar = scrollBar
    fakeScrollPane.horizontalScrollBar = scrollBar
    fakeScrollPane.isVisible = true
    fakeScrollPane.setBounds(0, 0, 0, 0)
    add(fakeScrollPane) // isShowing() should return true for this component
    add(scrollBar)

    // Listen to scroll events on the fake scroll pane and redispatch them to the tabs
    addMouseWheelListener { event ->
      val modifiers = UIUtil.getAllModifiers(event) or InputEvent.SHIFT_DOWN_MASK
      val e = MouseEventAdapter.convert(
        /* event = */ event,
        /* source = */ fakeScrollPane,
        /* id = */ event.id,
        /* when = */ event.getWhen(),
        /* modifiers = */ modifiers,
        /* x = */ event.x,
        /* y = */ event.y
      )
      MouseEventAdapter.redispatch(e, fakeScrollPane)
    }
    // AWT listener
    addMouseMotionAwtListener()

    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getDefaultComponent(aContainer: Container): Component? = toFocus
    }

    // Hide popup focus
    lazyUiDisposable(parent = parentDisposable, ui = this, child = this) { _, proj ->
      if (project == null && proj != null) project = proj

      val listener = AWTEventListener { _: AWTEvent? ->
        if (JBPopupFactory.getInstance().getChildPopups(this@KrTabsImpl).isEmpty()) {
          processFocusChange()
        }
      }

      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.FOCUS_EVENT_MASK)
    }

    // Add client property to hide the tabs from the hierarchy
    putClientProperty(
      UIUtil.NOT_IN_HIERARCHY_COMPONENTS,
      Iterable {
        visibleTabInfos.asSequence().filter { it != mySelectedInfo }.map { it.component }.iterator()
      }
    )

    object : HoverListener() {
      override fun mouseEntered(component: Component, x: Int, y: Int) {
        toggleScrollBar(isInsideTabsArea(y))
      }

      override fun mouseMoved(component: Component, x: Int, y: Int) {
        toggleScrollBar(isInsideTabsArea(y) || isScrollBarAdjusting())
      }

      override fun mouseExited(component: Component) {
        toggleScrollBar(false)
      }
    }.addTo(this)

    scrollBarChangeListener = ChangeListener { updateTabsOffsetFromScrollBar() }

    setTabsPosition(tabsPosition)
  }

  @Suppress("IncorrectParentDisposable")
  constructor(project: Project) : this(project = project, parentDisposable = project)

  /** Sort the tabs alphabetically. */
  fun sortTabsAlphabetically(tabs: MutableList<EditorGroupTabInfo>) {
    tabs.sortWith { o1, o2 -> NaturalComparator.INSTANCE.compare(o1.text, o2.text) }
  }

  /** Reset scrollbar activity. */
  protected fun resetScrollBarActivity() {
    scrollBarActivityTracker.reset()
  }

  /** Whether the scrollbar is adjusting. */
  internal fun isScrollBarAdjusting(): Boolean = scrollBar.valueIsAdjusting

  /** Add an event listener to specify whether the mouse is inside the tabs area. */
  private fun addMouseMotionAwtListener() {
    val listener = AWTEventListener { event ->
      val tabRectangle = lastLayoutPass?.headerRectangle ?: return@AWTEventListener

      event as MouseEvent
      val point = event.point
      SwingUtilities.convertPointToScreen(point, event.component)

      var rectangle = visibleRect.intersection(tabRectangle)
      val p = rectangle.location
      SwingUtilities.convertPointToScreen(p, this@KrTabsImpl)
      rectangle.location = p

      val inside = rectangle.contains(point)
      if (inside == isMouseInsideTabsArea) return@AWTEventListener

      isMouseInsideTabsArea = inside
      scrollBarActivityTracker.cancelActivityTimer()

      if (!inside) {
        scrollBarActivityTracker.setRecentlyActive()
      }
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK)
  }

  // Tab border
  protected fun createTabBorder(): EditorGroupsTabsBorder = EditorGroupsTabsBorder(this)

  // Tab painter adapter
  protected open fun createTabPainterAdapter(): EditorGroupsTabPainterAdapter = EditorGroupsDefaultTabPainterAdapter()

  /** Check whether the current position is inside the tabs area (for scrolling) */
  private fun isInsideTabsArea(y: Int): Boolean {
    val area = lastLayoutPass?.headerRectangle?.size ?: return false
    return when (tabsPosition) {
      EditorGroupsTabsPosition.TOP    -> y <= area.height
      EditorGroupsTabsPosition.BOTTOM -> y >= height - area.height
    }
  }

  /** Toggle scroll bar on mouse over. */
  private fun toggleScrollBar(state: Boolean) {
    if (state == scrollBarOn) return

    scrollBarOn = state
    scrollBar.toggle(state)
  }

  /** Return the position of the scrollbar. */
  private fun getScrollBarBounds(): Rectangle = when (tabsPosition) {
    EditorGroupsTabsPosition.TOP    -> Rectangle(0, 1, width, SCROLL_BAR_THICKNESS)
    EditorGroupsTabsPosition.BOTTOM -> Rectangle(0, height - SCROLL_BAR_THICKNESS, width, SCROLL_BAR_THICKNESS)
  }

  /** Revalidate tabs on settings change. */
  override fun uiSettingsChanged(uiSettings: UISettings) {
    for (tab in visibleTabInfos) {
      tab.revalidate()
    }

    updateRowLayout()
  }

  /** Update the layout. */
  private fun updateRowLayout() {
    val layout = createRowLayout()
    // set the current scroll value to new layout
    layout.scroll(scrollBarModel.value)
    setLayout(layout)

    applyDecoration()
    relayout(forced = true, layoutNow = true)
  }

  /** The row layout. */
  protected open fun createRowLayout(): EditorGroupsSingleRowLayout = EditorGroupsScrollableSingleRowLayout(this)

  /** Hover the tab. */
  fun setHovered(label: EditorGroupTabLabel?) {
    val old = tabLabelAtMouse
    tabLabelAtMouse = label

    if (old != null) {
      old.revalidate()
      old.repaint()
    }

    if (tabLabelAtMouse != null) {
      tabLabelAtMouse!!.revalidate()
      tabLabelAtMouse!!.repaint()
    }
  }

  /** Remove hover at the tab. */
  fun unHover(label: EditorGroupTabLabel) {
    if (tabLabelAtMouse === label) {
      tabLabelAtMouse = null
      label.revalidate()
      label.repaint()
    }
  }

  /** Whether the tab is hovered. */
  fun isHoveredTab(label: EditorGroupTabLabel?): Boolean = label != null && label === tabLabelAtMouse

  /** Whether the tab panel is active. */
  open fun isActiveTabs(info: EditorGroupTabInfo?): Boolean = UIUtil.isFocusAncestor(this)

  /** Reset tabs cache. */
  @RequiresEdt
  fun resetTabsCache() {
    allTabs = null
  }

  /**
   * Handles changes in focus for the component.
   *
   * This method determines if the current focus owner is the component itself or a descendant of the component. If so, it sets the
   * component as focused. Otherwise, it sets the component as not focused.
   *
   * It first retrieves the current focus owner using the KeyboardFocusManager. If the focus owner is null, it marks the component as not
   * focused and returns.
   *
   * If the component itself or one of its descendants holds the focus, it sets the focused state to true. Otherwise, it sets the focused
   * state to false.
   */
  private fun processFocusChange() {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (owner == null) {
      setFocused(false)
      return
    }

    if (owner === this || SwingUtilities.isDescendingFrom(owner, this)) {
      setFocused(focused = true)
    } else {
      setFocused(focused = false)
    }
  }

  /**
   * Initializes the component and attaches necessary listeners upon addition to its parent. This method overrides the default addNotify
   * implementation to add a ChangeListener to the scrollBarModel and to handle any deferred focus requests. If there is a deferred focus
   * request, it will be executed and set to null.
   */
  override fun addNotify() {
    super.addNotify()

    scrollBarModel.addChangeListener(scrollBarChangeListener)

    if (deferredFocusRequest != null) {
      val request = deferredFocusRequest!!
      deferredFocusRequest = null
      request.run()
    }
  }

  /** Remove a tab. */
  override fun remove(index: Int) {
    if (removeNotifyInProgress) {
      thisLogger().warn("removeNotify in progress")
    }

    super.remove(index)
  }

  /** Remove all tabs. */
  override fun removeAll() {
    if (removeNotifyInProgress) {
      thisLogger().warn("removeNotify in progress")
    }

    super.removeAll()
  }

  /**
   * This method is called when the component is about to be removed from its display. Handles cleanup tasks and ensures that any ongoing
   * processes related to the component being removed are properly managed.
   *
   * Specifically, this method:
   * 1. Sets a flag to indicate that the removal is in progress.
   * 2. Calls the superclass's `removeNotify` method to perform any default actions.
   * 3. Resets the flag indicating that the removal process has finished.
   * 4. Ensures that the component is no longer focused.
   * 5. Removes the change listener from the scroll bar model to avoid memory leaks.
   */
  override fun removeNotify() {
    try {
      removeNotifyInProgress = true
      super.removeNotify()
    } finally {
      removeNotifyInProgress = false
    }

    setFocused(false)

    scrollBarModel.removeChangeListener(scrollBarChangeListener)
  }

  /**
   * Adjusts the layout of a given component by modifying its size and position.
   *
   * @param componentX The x-coordinate of the component's initial position.
   * @param componentY The y-coordinate of the component's initial position.
   * @param component The JComponent to be resized and repositioned.
   * @param deltaWidth The amount by which the component's width should be adjusted.
   * @param deltaHeight The amount by which the component's height should be adjusted.
   * @return A Rectangle representing the new bounds of the component.
   */
  fun layoutComp(
    componentX: Int,
    componentY: Int,
    component: JComponent,
    deltaWidth: Int,
    deltaHeight: Int
  ): Rectangle = layoutComp(
    bounds = Rectangle(componentX, componentY, width, height),
    component = component,
    deltaWidth = deltaWidth,
    deltaHeight = deltaHeight
  )

  /**
   * Adjusts the layout of a given component by considering specified bounds, insets, and deltas in width and height.
   *
   * @param bounds The bounding rectangle specifying the initial position and size.
   * @param component The JComponent to be laid out.
   * @param deltaWidth The additional width to add to the component's calculated width.
   * @param deltaHeight The additional height to add to the component's calculated height.
   * @return A rectangle representing the new bounds of the component after layout adjustments.
   */
  fun layoutComp(bounds: Rectangle, component: JComponent, deltaWidth: Int, deltaHeight: Int): Rectangle {
    val insets = layoutInsets
    val inner = innerInsets
    val x = insets.left + bounds.x + inner.left
    val y = insets.top + bounds.y + inner.top
    var width = bounds.width - insets.left - insets.right - bounds.x - inner.left - inner.right
    var height = bounds.height - insets.top - insets.bottom - bounds.y - inner.top - inner.bottom

    width += deltaWidth
    height += deltaHeight

    return layout(component = component, x = x, y = y, width = width, height = height)
  }

  /**
   * Adjusts the layout of the provided component with specified deltas for x, y coordinates and width, height.
   *
   * @param passInfo an object containing layout information and component references
   * @param deltaX the change in the x-coordinate position for the component's layout
   * @param deltaY the change in the y-coordinate position for the component's layout
   * @param deltaWidth the change in the width of the component's layout
   * @param deltaHeight the change in the height of the component's layout
   */
  fun layoutComp(passInfo: EditorGroupsSingleRowPassInfo, deltaX: Int, deltaY: Int, deltaWidth: Int, deltaHeight: Int) {
    val hToolbar = passInfo.hToolbar?.get()

    when {
      hToolbar != null -> {
        val toolbarHeight = hToolbar.preferredSize.height
        val compRect = layoutComp(
          componentX = deltaX,
          componentY = toolbarHeight + deltaY,
          component = passInfo.component?.get()!!,
          deltaWidth = deltaWidth,
          deltaHeight = deltaHeight
        )

        layout(
          component = hToolbar,
          x = compRect.x,
          y = compRect.y - toolbarHeight,
          width = compRect.width,
          height = toolbarHeight
        )
      }

      else             -> layoutComp(
        componentX = deltaX,
        componentY = deltaY,
        component = passInfo.component?.get()!!,
        deltaWidth = deltaWidth,
        deltaHeight = deltaHeight
      )
    }
  }

  /**
   * Updates the layout of the specified component with the provided bounds.
   *
   * @param component the component to update the layout for
   * @param bounds the new bounds to set for the component
   * @return the bounds that were set for the component
   */
  fun layout(component: JComponent, bounds: Rectangle): Rectangle {
    val now = component.bounds
    if (bounds != now) {
      component.bounds = bounds
    }

    component.doLayout()
    component.putClientProperty(LAYOUT_DONE, true)

    return bounds
  }

  /**
   * Positions and sizes the given component based on the specified bounds.
   *
   * @param component the JComponent to be positioned and sized
   * @param x the x-coordinate of the component's new location
   * @param y the y-coordinate of the component's new location
   * @param width the new width of the component
   * @param height the new height of the component
   * @return a Rectangle representing the component's new bounds
   */
  fun layout(component: JComponent, x: Int, y: Int, width: Int, height: Int): Rectangle =
    layout(component = component, bounds = Rectangle(x, y, width, height))

  /** Set the offset of the first tab. */
  override fun setFirstTabOffset(offset: Int): KrTabsPresentation {
    this.firstTabOffset = offset
    return this
  }

  /** Whether we need to show the more tabs button. */
  override fun canShowMorePopup(): Boolean {
    val rect = lastLayoutPass?.moreRect
    return rect != null && !rect.isEmpty
  }

  /** Show the more tabs popup. */
  override fun showMorePopup(): JBPopup? {
    val rect = lastLayoutPass?.moreRect ?: return null
    val hiddenInfos = visibleTabInfos
      .filter { effectiveLayout!!.isTabHidden(it) }
      .takeIf { it.isNotEmpty() } ?: return null

    return showListPopup(rect = rect, hiddenInfos = hiddenInfos)
  }

  /** Show the hidden tabs popup. */
  private fun showListPopup(rect: Rectangle, hiddenInfos: List<EditorGroupTabInfo>): JBPopup {
    // get the first label of hidden infos, this is where we'll add our separator
    val separatorIndex = hiddenInfos.indexOfFirst { info ->
      val label = info.tabLabel!!
      label.x >= 0
    }

    // The tab info at the separator index
    val separatorInfo = when {
      separatorIndex > 0 -> hiddenInfos[separatorIndex]
      else               -> null
    }

    val step = HiddenInfosListPopupStep(hiddenInfos, separatorInfo)
    // Try to restore the selected index
    val selectedIndex = ClientProperty.get(this, HIDDEN_INFOS_SELECT_INDEX_KEY)
    if (selectedIndex != null) {
      step.defaultOptionIndex = selectedIndex
    }

    val popup = JBPopupFactory.getInstance().createListPopup(project!!, step) {
      val descriptor = object : ListItemDescriptorAdapter<EditorGroupTabInfo>() {
        override fun getTextFor(value: EditorGroupTabInfo): String = value.text

        override fun getIconFor(value: EditorGroupTabInfo): Icon? = value.icon

        override fun hasSeparatorAboveOf(value: EditorGroupTabInfo): Boolean = value == separatorInfo
      }

      object : GroupedItemsListRenderer<EditorGroupTabInfo?>(descriptor) {
        private val SELECTED_KEY = Key.create<Boolean>("SELECTED")
        var component: JPanel? = null
        var iconLabel: JLabel? = null
        var textLabel: SimpleColoredComponent? = null

        override fun createItemComponent(): JComponent {
          // there is the separate label 'textLabel', but the original one still should be created,
          // as it is used from the GroupedElementsRenderer.configureComponent
          createLabel()

          component = JPanel()
          component!!.layout = BoxLayout(this.component, BoxLayout.X_AXIS)
          // painting underline on the left for the selected tab
          component!!.border = object : Border {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
              if (!ClientProperty.isTrue(c, SELECTED_KEY)) return

              val padding = JBUI.scale(2)
              val arc = JBUI.scale(4)
              val theme: EditorGroupTabTheme = tabPainter.getTabTheme()

              val rect = Rectangle(x, y + padding, theme.underlineHeight, height - padding * 2)

              (g as Graphics2D).fill2DRoundRect(rect, arc.toDouble(), theme.underlineColor)
            }

            override fun getBorderInsets(c: Component): Insets = JBInsets.create(Insets(0, 9, 0, 3))

            override fun isBorderOpaque(): Boolean = true
          }

          // add icon label
          iconLabel = JLabel()
          component!!.add(iconLabel)

          // Add some spacing between icon and text
          val gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2
          component!!.add(Box.createRigidArea(Dimension(gap, 0)))

          // Add text label
          textLabel = object : SimpleColoredComponent() {
            override fun getMaximumSize(): Dimension = preferredSize
          }
          textLabel!!.myBorder = null
          textLabel!!.setIpad(JBUI.emptyInsets())
          textLabel!!.setOpaque(true)
          component!!.add(textLabel)

          val result = layoutComponent(component)
          // For new UI
          if (result is SelectablePanel) {
            result.setBorder(JBUI.Borders.empty(0, 5))
            result.selectionInsets = JBInsets.create(0, 5)
            result.preferredHeight = JBUI.scale(26)
          }

          return result
        }

        override fun customizeComponent(
          list: JList<out EditorGroupTabInfo?>?,
          tabInfo: EditorGroupTabInfo?,
          isSelected: Boolean
        ) {
          iconLabel!!.icon = tabInfo?.icon
          textLabel!!.clear()
          tabInfo!!.coloredText.appendToComponent(textLabel!!)

          val customBackground = tabInfo.tabColor
          myRendererComponent.background = customBackground ?: JBUI.CurrentTheme.Popup.BACKGROUND

          ClientProperty.put(component!!, SELECTED_KEY, if (tabInfo == selectedInfo) true else null)
          component!!.invalidate()
        }

        override fun setComponentIcon(icon: Icon?, disabledIcon: Icon?) {
          // the icon will be set in customizeComponent
        }

        override fun createSeparator() = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
      }
    }
    popup.content.putClientProperty(MorePopupAware::class.java, true)
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        ApplicationManager.getApplication().invokeLater {
          ClientProperty.put(this@KrTabsImpl, HIDDEN_INFOS_SELECT_INDEX_KEY, null)
        }
      }
    })
    popup.show(RelativePoint(this, Point(rect.x, rect.y + rect.height)))
    return popup
  }

  /** Request focus. */
  override fun requestFocus() {
    val toFocus = toFocus
    when (toFocus) {
      null -> focusManager.doWhenFocusSettlesDown { super.requestFocus() }
      else -> focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
    }
  }

  /** Request focus in window. */
  override fun requestFocusInWindow(): Boolean = toFocus?.requestFocusInWindow() ?: super.requestFocusInWindow()

  /** Add tab at the given index. */
  override fun addTab(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo =
    addTab(info = info, index = index, fireEvents = true)

  /** Add a tab silently at the given index (e.g. do not fire events) */
  override fun addTabSilently(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo =
    addTab(info = info, index = index, fireEvents = false)

  /**
   * Adds a new tab into the editor group at a specified index and optionally fires events related to the addition.
   *
   * @param info Information about the editor group tab to be added.
   * @param index The position at which the tab should be added.
   * @param fireEvents Whether or not to fire events related to the addition of the tab.
   * @return Information about the added editor group tab.
   */
  private fun addTab(info: EditorGroupTabInfo, index: Int, fireEvents: Boolean): EditorGroupTabInfo {
    if (addTabWithoutUpdating(tabInfo = info, index = index)) return tabs[tabs.indexOf(info)]

    updateAll(forcedRelayout = false)
    if (fireEvents && tabCount == 1) {
      fireBeforeSelectionChanged(oldInfo = null, newInfo = info)
      fireSelectionChanged(oldInfo = null, newInfo = info)
    }

    revalidateAndRepaint(layoutNow = false)
    return info
  }

  /**
   * Adds a tab without updating the existing tabs.
   *
   * @param tabInfo the tab information to be added
   * @param index the position at which the tab should be inserted
   * @return true if tab already exists and adding was not performed, false otherwise
   */
  private fun addTabWithoutUpdating(tabInfo: EditorGroupTabInfo, index: Int): Boolean {
    if (tabs.contains(tabInfo)) return true

    tabInfo.changeSupport.addPropertyChangeListener(this)

    // Create the tab label
    val label = createTabLabel(tabInfo)
    label.setText(tabInfo.coloredText)
    label.toolTipText = tabInfo.tooltipText
    label.setIcon(tabInfo.icon)

    tabInfo.tabLabel = label
    // TODO REMOVE THIS once we remove infoToLabel
    infoToLabel.put(tabInfo, label)

    // Add the tab at the given index
    when {
      index < 0 || index > visibleTabInfos.size - 1 -> visibleTabInfos.add(tabInfo)
      else                                          -> visibleTabInfos.add(index, tabInfo)
    }

    resetTabsCache()
    add(label)
    // Remove scroll border
    adjust(tabInfo)
    return false
  }

  // Add a tab at the end
  override fun addTab(info: EditorGroupTabInfo): EditorGroupTabInfo = addTab(info, -1)

  /** Create a new tab label. */
  protected open fun createTabLabel(info: EditorGroupTabInfo): EditorGroupTabLabel = EditorGroupTabLabel(this, info)

  /** Get a tab label at the given info. */
  override fun getTabLabel(info: EditorGroupTabInfo): EditorGroupTabLabel? = info.tabLabel ?: infoToLabel[info]

  override fun setPopupGroup(popupGroup: ActionGroup, place: String, addNavigationGroup: Boolean): EditorGroupsTabsBase =
    setPopupGroupWithSupplier({ popupGroup }, place)

  /** Set the supplier of the popup group. */
  override fun setPopupGroupWithSupplier(
    supplier: Supplier<out ActionGroup>,
    place: String
  ): EditorGroupsTabsBase {
    popupGroupSupplier = supplier::get
    popupPlace = place
    return this
  }

  /**
   * Updates all necessary components and state based on whether a forced relayout is needed.
   *
   * @param forcedRelayout Indicates if a forced relayout should be performed.
   */
  private fun updateAll(forcedRelayout: Boolean) {
    val toSelect = selectedInfo
    mySelectedInfo = toSelect
    updateContainer(forced = forcedRelayout, layoutNow = false)
    removeDeferred()
    updateListeners()
    updateEnabledState()
  }

  /** Select a tab. */
  override fun select(tabInfo: EditorGroupTabInfo, requestFocus: Boolean): ActionCallback =
    doSetSelected(tabInfo = tabInfo, requestFocus = requestFocus, requestFocusInWindow = false)

  /** Select a tab and execute the change selection. */
  private fun doSetSelected(tabInfo: EditorGroupTabInfo, requestFocus: Boolean, requestFocusInWindow: Boolean): ActionCallback {
    if (!isEnabled) return ActionCallback.REJECTED

    // temporary state to make selection fully visible (scrolled in view)
    isMouseInsideTabsArea = false

    return when {
      selectionChangeHandler != null -> selectionChangeHandler!!.execute(
        info = tabInfo,
        requestFocus = requestFocus,
        doChangeSelection = object : ActiveRunnable() {
          override fun run(): ActionCallback = executeSelectionChange(tabInfo, requestFocus, requestFocusInWindow)
        }
      )

      else                           -> executeSelectionChange(tabInfo, requestFocus, requestFocusInWindow)
    }
  }

  /**
   * Handles a selection change in a tabbed interface, focusing the selected tab if requested, firing appropriate events, and updating the
   * UI layout.
   *
   * @param tabInfo The information about the tab that lost or gained selection.
   * @param requestFocus A flag indicating whether focus should be requested.
   * @param requestFocusInWindow A flag indicating whether focus should be requested within the window.
   * @return An ActionCallback representing the result of the focus request and subsequent UI updates.
   */
  private fun executeSelectionChange(
    tabInfo: EditorGroupTabInfo,
    requestFocus: Boolean,
    requestFocusInWindow: Boolean
  ): ActionCallback {
    // If selecting the same tab, focus force it
    if (mySelectedInfo != null && mySelectedInfo == tabInfo) {
      if (!requestFocus) return ActionCallback.DONE

      val owner = focusManager.focusOwner
      val c = tabInfo.component

      return when {
        owner != null && (c === owner || SwingUtilities.isDescendingFrom(owner, c)) -> {
          // This might look like a no-op, but in some cases it's not. In particular, it's required when a focus transfer has just been
          // requested to another component. E.g., this happens on 'unsplit' operation when we remove an editor component from UI hierarchy and
          // re-add it at once in a different layout, and want that editor component to preserve focus afterward.
          requestFocusLaterOn(owner, requestFocusInWindow)
        }

        else                                                                        -> requestFocusLater(requestFocusInWindow)
      }
    }

    // Keep new selected info
    val oldTabInfo = mySelectedInfo
    mySelectedInfo = tabInfo

    // Put label at top of tab
    val newInfo = mySelectedInfo
    tabInfo.tabLabel?.let { setComponentZOrder(it, 0) }

    // Lay scrollbar on top of tabs
    setComponentZOrder(scrollBar, 0)
    // Fire events
    fireBeforeSelectionChanged(oldTabInfo, newInfo)

    // Update the tabs component, removing or adding tabs if necessary
    val oldMouseInsideTabsArea = isMouseInsideTabsArea
    try {
      updateContainer(forced = false, layoutNow = true)
    } finally {
      isMouseInsideTabsArea = oldMouseInsideTabsArea
    }

    // Fire events
    fireSelectionChanged(oldTabInfo, newInfo)
    // If no focus requested, return earlier after removing deferred to remove tabs
    if (!requestFocus) return removeDeferred()

    val toFocus = toFocus
    when {
      project != null && toFocus != null -> {
        val result = ActionCallback()

        // Remove deferred and focus the toFocus
        requestFocusLater(requestFocusInWindow).doWhenProcessed {
          when {
            project!!.isDisposed -> result.setRejected()
            else                 -> removeDeferred().notifyWhenDone(result)
          }
        }
        return result
      }

      else                               -> {
        requestFocusLaterOn(this, requestFocusInWindow)
        return removeDeferred()
      }
    }
  }

  /** Select tab without firing events. */
  @RequiresEdt
  fun selectTabSilently(tab: EditorGroupTabInfo) {
    mySelectedInfo = tab
    // Lay components
    setComponentZOrder(tab.tabLabel!!, 0)
    setComponentZOrder(scrollBar, 0)

    val oldMouseInsideTabs = isMouseInsideTabsArea
    try {
      val component = tab.component
      if (component?.parent != null) add(component)
    } finally {
      isMouseInsideTabsArea = oldMouseInsideTabs
    }
  }

  /** Fire event before changing selection. */
  private fun fireBeforeSelectionChanged(oldInfo: EditorGroupTabInfo?, newInfo: EditorGroupTabInfo?) {
    if (oldInfo == newInfo) return
    oldSelection = oldInfo

    try {
      tabListeners.forEach { it.beforeSelectionChanged(oldInfo, newInfo) }
    } finally {
      oldSelection = null
    }
  }

  /** Fire event after changing selection. */
  private fun fireSelectionChanged(oldInfo: EditorGroupTabInfo?, newInfo: EditorGroupTabInfo?) {
    if (oldInfo == newInfo) return
    tabListeners.forEach { it?.selectionChanged(oldInfo, newInfo) }
  }

  /** Fire event when a tab is removed. */
  private fun fireTabRemoved(info: EditorGroupTabInfo) {
    tabListeners.forEach { it?.tabRemoved(info) }
  }

  /**
   * Requests focus for the specified component at a later time.
   *
   * The method ensures that the focus request is performed on the Event Dispatch Thread (EDT). Depending on the value of `inWindow`, it
   * either requests focus in the window or uses the focus manager to request focus in the project.
   *
   * @param toFocus the component for which the focus request should be performed. If null, the method immediately returns
   *    `ActionCallback.DONE`.
   * @param inWindow if true, the focus request is performed using `requestFocusInWindow`. If false, the focus manager is used to request
   *    focus in the project.
   * @return an `ActionCallback` that indicates the status of the focus request. It will be set to `DONE` if the request was successful, and
   *    `REJECTED` if the component is not showing .
   */
  private fun requestFocusLaterOn(toFocus: Component?, inWindow: Boolean): ActionCallback {
    if (toFocus == null) return ActionCallback.DONE

    // If the component is not showing, return rejected
    if (!isShowing) return ActionCallback.REJECTED

    val callback = ActionCallback()
    ApplicationManager.getApplication().invokeLater {
      when {
        inWindow -> {
          toFocus.requestFocusInWindow()
          callback.setDone()
        }

        else     -> focusManager.requestFocusInProject(toFocus, project).notifyWhenDone(callback)
      }
    }

    return callback
  }

  /**
   * Requests focus to the `toFocus` component at a later time.
   *
   * @param inWindow Whether to request focus within the window or globally in the project.
   * @return An ActionCallback indicating the result of the focus request.
   */
  private fun requestFocusLater(inWindow: Boolean): ActionCallback {
    if (!isShowing) return ActionCallback.REJECTED

    val callback = ActionCallback()
    ApplicationManager.getApplication().invokeLater {
      val toFocus = toFocus
      when {
        toFocus == null -> callback.setDone()
        inWindow        -> {
          toFocus.requestFocusInWindow()
          callback.setDone()
        }

        else            -> focusManager.requestFocusInProject(toFocus, project).notifyWhenDone(callback)
      }
    }
    return callback
  }

  /** Remove deferred tabs. */
  private fun removeDeferred(): ActionCallback {
    if (deferredToRemove.isEmpty()) return ActionCallback.DONE

    val callback = ActionCallback()
    val executionRequest = ++removeDeferredRequest

    focusManager.doWhenFocusSettlesDown {
      if (removeDeferredRequest == executionRequest) removeDeferredNow()
      callback.setDone()
    }

    return callback
  }

  /** Immediately removes deferred elements from their parent, ensuring any pending removals are processed. */
  private fun removeDeferredNow() {
    deferredToRemove.keys.forEach { tabToRemove ->
      if (tabToRemove != null && tabToRemove.parent === this) remove(tabToRemove)
    }

    deferredToRemove.clear()
  }

  /** Execute actions when specific tab properties change. */
  override fun propertyChange(evt: PropertyChangeEvent) {
    val tabInfo = evt.source as EditorGroupTabInfo
    when (evt.propertyName) {
      EditorGroupTabInfo.ACTION_GROUP -> relayout(forced = false, layoutNow = false)
      EditorGroupTabInfo.COMPONENT    -> relayout(forced = true, layoutNow = false)

      EditorGroupTabInfo.TEXT         -> {
        updateText(tabInfo)
        revalidateAndRepaint(layoutNow = true)
      }

      EditorGroupTabInfo.ICON         -> {
        updateIcon(tabInfo)
        revalidateAndRepaint(layoutNow = true)
      }

      EditorGroupTabInfo.TAB_COLOR    -> revalidateAndRepaint(layoutNow = true)

      EditorGroupTabInfo.HIDDEN       -> {
        updateHiddenState()
        relayout(forced = false, layoutNow = false)
      }

      EditorGroupTabInfo.ENABLED      -> updateEnabledState()
    }
  }

  /** Update enabled state of all tabs. */
  private fun updateEnabledState() {
    val all = tabs
    for (tabInfo in all) {
      tabInfo.tabLabel?.setTabEnabled(tabInfo.isEnabled)
    }

    val selected = mySelectedInfo
    if (selected == null || selected.isEnabled) return

    // Try to select the requested tab with focus
    val toSelect = getToSelectOnRemoveOf(selected)
    if (toSelect != null) {
      select(tabInfo = toSelect, requestFocus = focusManager.getFocusedDescendantFor(this) != null)
    }
  }

  /** Update hidden state of all tabs. */
  private fun updateHiddenState() {
    var shouldUpdate = false
    val visible = visibleTabInfos.iterator()

    // Update the visibleTabInfos list based on the hidden state of the tabs
    while (visible.hasNext()) {
      val tabInfo = visible.next()
      if (tabInfo.isHidden && !hiddenInfos.containsKey(tabInfo)) {
        hiddenInfos.put(tabInfo, visibleTabInfos.indexOf(tabInfo))
        visible.remove()
        shouldUpdate = true
      }
    }

    // Update hidden infos based on the visible state of the tabs
    val hidden = hiddenInfos.keys.iterator()
    while (hidden.hasNext()) {
      val tabInfo = hidden.next()
      if (!tabInfo.isHidden && hiddenInfos.containsKey(tabInfo)) {
        visibleTabInfos.add(getIndexInHiddenInfos(tabInfo), tabInfo)
        hidden.remove()
        shouldUpdate = true
      }
    }

    if (shouldUpdate) {
      resetTabsCache()
      val oldSelectedInfo = mySelectedInfo
      // Remove selected info from the hidden infos since it came into view
      if (oldSelectedInfo != null && hiddenInfos.containsKey(oldSelectedInfo)) {
        mySelectedInfo = getToSelectOnRemoveOf(oldSelectedInfo)
      }

      updateAll(forcedRelayout = true)
    }
  }

  /**
   * Gets the index of the specified tab information in the list of hidden tabs.
   *
   * @param tabInfo The tab information for which the index is to be found.
   * @return The index of the tab information in the hidden infos list, or the size of the visible tab infos if it's not found in hidden
   *    infos.
   */
  private fun getIndexInHiddenInfos(tabInfo: EditorGroupTabInfo): Int {
    val info = hiddenInfos[tabInfo]
    var index = info ?: visibleTabInfos.size

    if (index > visibleTabInfos.size) {
      index = visibleTabInfos.size
    }

    if (index < 0) {
      index = 0
    }

    return index
  }

  /** Update the tab label icon with the tabInfo's icon. */
  private fun updateIcon(tabInfo: EditorGroupTabInfo) = tabInfo.tabLabel?.setIcon(tabInfo.icon)

  override fun isOpaque(): Boolean = super.isOpaque() && !visibleTabInfos.isEmpty()

  /** Revalidates and repaints the component, optionally performing the layout immediately. */
  open fun revalidateAndRepaint(layoutNow: Boolean) {
    if (visibleTabInfos.isEmpty() && parent != null) {
      val directParent = ComponentUtil.findUltimateParent(this)
      val toRepaint = SwingUtilities.convertRectangle(parent, bounds, directParent)
      directParent.repaint(toRepaint.x, toRepaint.y, toRepaint.width, toRepaint.height)
    }

    when {
      layoutNow -> validate()
      else      -> revalidate()
    }

    repaint()
  }

  /** Update text and tooltip text. */
  private fun updateText(tabInfo: EditorGroupTabInfo) {
    val label = tabInfo.tabLabel!!
    label.setText(tabInfo.coloredText)
    label.toolTipText = tabInfo.tooltipText
  }

  /** Returns the tab to select after removal. */
  override fun getToSelectOnRemoveOf(tabInfo: EditorGroupTabInfo): EditorGroupTabInfo? {
    if (!visibleTabInfos.contains(tabInfo) || mySelectedInfo != tabInfo || visibleTabInfos.size == 1) return null

    val index = visibleTabInfos.indexOf(tabInfo)
    var result: EditorGroupTabInfo? = null

    // Find the first enabled tab on the left tabs
    if (index > 0) {
      result = findEnabledBackward(from = index, cycle = false)
    }

    // If nothing is found (like if its the first tab), try on the right tabs
    return result ?: findEnabledForward(from = index, cycle = false)
  }

  /** Find the next enabled tab in the forward direction. */
  fun findEnabledForward(from: Int, cycle: Boolean): EditorGroupTabInfo? {
    if (from < 0) return null

    var index = from
    val infos = visibleTabInfos

    while (true) {
      index++
      if (index == infos.size) {
        if (!cycle) break
        index = 0
      }

      if (index == from) break

      val tabInfo = infos[index]
      if (tabInfo.isEnabled) return tabInfo
    }

    return null
  }

  /** Find the next enabled tab in the backward direction. */
  fun findEnabledBackward(from: Int, cycle: Boolean): EditorGroupTabInfo? {
    if (from < 0) return null

    var index = from
    val infos = visibleTabInfos

    while (true) {
      index--

      if (index == -1) {
        if (!cycle) break
        index = infos.size - 1
      }

      if (index == from) break

      val tabInfo = infos[index]
      if (tabInfo.isEnabled) return tabInfo
    }

    return null
  }

  /** Get tab at tab index. */
  override fun getTabAt(tabIndex: Int): EditorGroupTabInfo = tabs[tabIndex]

  // Return the target tab info
  override fun getTargetInfo(): EditorGroupTabInfo? = popupInfo ?: selectedInfo

  /** When popup menu is visible. */
  override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}

  /** When popup menu is invisible. */
  override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent): Unit = resetPopup()

  /** When popup menu is canceled. */
  override fun popupMenuCanceled(e: PopupMenuEvent): Unit = resetPopup()

  /** Resets the popup information. */
  private fun resetPopup() {
    SwingUtilities.invokeLater {
      // No need to reset popup info if a new popup has been already opened and myPopupInfo refers to the corresponding info.
      if (activePopup == null) popupInfo = null
    }
  }

  /** Add to deferred to remove. */
  private fun addToDeferredRemove(c: Component) {
    if (!deferredToRemove.containsKey(c)) deferredToRemove.put(c, c)
  }

  /** Updates the scroll bar's model with the latest values from the layout pass and effective layout. */
  private fun updateScrollBarModel() {
    val scrollBarModel = scrollBarModel
    if (scrollBarModel.valueIsAdjusting) return

    val maximum = lastLayoutPass!!.requiredLength
    val value = effectiveLayout!!.scrollOffset
    val extent = lastLayoutPass!!.scrollExtent

    scrollBarModel.maximum = maximum
    scrollBarModel.value = value

    // if the extent is 0, that means the layout is in improper state, so we don't show the scrollbar
    scrollBarModel.extent = if (extent == 0) value + maximum else extent
  }

  /** Update the tab offset according to the scrollbar model. */
  private fun updateTabsOffsetFromScrollBar() {
    if (!isScrollBarAdjusting()) {
      scrollBarActivityTracker.setRecentlyActive()
      toggleScrollBar(isMouseInsideTabsArea)
    }

    val currentUnitsOffset = effectiveLayout!!.scrollOffset
    val updatedOffset = scrollBarModel.value

    effectiveLayout!!.scroll(updatedOffset - currentUnitsOffset)

    SwingUtilities.invokeLater {
      relayout(forced = false, layoutNow = false)
    }
  }

  /**
   * Arranges and layouts components within the container. This method is responsible for determining the new bounds of the components,
   * updating scrollbar properties, and managing visibility changes.
   *
   * The method includes steps to:
   * - Suspend scrollbar activity to prevent model changes from being interpreted as user activity.
   * - Compute the fit size for the header.
   * - Layout tabs using the appropriate layout strategy.
   * - Center the toolbar if necessary.
   * - Reset components as needed.
   * - Set scrollbar orientation and bounds.
   * - Update the scrollbar model.
   * - Update the toolbar's visibility state if it has changed.
   *
   * Special precautions are taken to ensure that forced re-layouts are marked and that any changes are resumed properly after layout
   * adjustments are completed.
   */
  override fun doLayout() {
    // Model changes caused by layout changes shouldn't be interpreted as activity.
    scrollBarActivityTracker.suspend()

    try {
      val moreBoundsBeforeLayout = moreToolbar!!.component.bounds
      headerFitSize = computeHeaderFitSize()

      val visible = visibleTabInfos.toMutableList()
      val effectiveLayout = effectiveLayout
      if (effectiveLayout is EditorGroupsSingleRowLayout) {
        lastLayoutPass = effectiveLayout.layoutSingleRow(visible)
      }

      centerMoreToolbarPosition()

      applyResetComponents()

      scrollBar.orientation = Adjustable.HORIZONTAL
      scrollBar.bounds = getScrollBarBounds()
      updateScrollBarModel()

      updateToolbarIfVisibilityChanged(moreToolbar, moreBoundsBeforeLayout)
    } finally {
      forcedRelayout = false
      scrollBarActivityTracker.resume()
    }
  }

  /** Centers the more toolbar. */
  private fun centerMoreToolbarPosition() {
    val moreRect = lastLayoutPass!!.moreRect
    val mComponent = moreToolbar!!.component

    if (!moreRect.isEmpty) {
      val bounds = Rectangle(moreRect)
      val preferredSize = mComponent.preferredSize
      val xDiff = (bounds.width - preferredSize.width) / 2
      val yDiff = (bounds.height - preferredSize.height) / 2

      bounds.x += xDiff + 2
      bounds.width -= 2 * xDiff
      bounds.y += yDiff
      bounds.height -= 2 * yDiff

      mComponent.bounds = bounds
    } else {
      mComponent.bounds = Rectangle()
    }

    mComponent.putClientProperty(LAYOUT_DONE, true)
  }

  /** Compute the header fit size. */
  private fun computeHeaderFitSize(): Dimension {
    val max = computeMaxSize()
    return Dimension(
      size.width,
      max(max.label.height, max.toolbar.height).coerceAtLeast(0)
    )
  }

  override fun setInnerInsets(innerInsets: Insets): KrTabsPresentation {
    this.innerInsets = innerInsets
    return this
  }

  /**
   * Resets the layout based on the provided parameters.
   *
   * @param shouldResetLabels Indicates whether or not the labels should be reset.
   */
  fun resetLayout(shouldResetLabels: Boolean) {
    // Reset visible infos
    for (tabInfo in visibleTabInfos) {
      reset(tabInfo = tabInfo, shouldResetLabels = shouldResetLabels)
    }

    // Reset hidden infos
    for (tabInfo in hiddenInfos.keys) {
      reset(tabInfo = tabInfo, shouldResetLabels = shouldResetLabels)
    }

    // Reset deferred to remove
    for (eachDeferred in deferredToRemove.keys) {
      resetLayout(eachDeferred as JComponent)
    }
  }

  /** Reset the tabinfo. */
  private fun reset(tabInfo: EditorGroupTabInfo, shouldResetLabels: Boolean) {
    val c = tabInfo.component
    resetLayout(c)

    if (shouldResetLabels) {
      tabInfo.tabLabel?.let { resetLayout(it) }
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    tabPainter.fillBackground(g as Graphics2D, Rectangle(0, 0, width, height))
    drawBorder(g)
  }

  /** What component graphics to use. */
  override fun getComponentGraphics(graphics: Graphics): Graphics =
    JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))

  /** Draw the tab border. */
  protected fun drawBorder(g: Graphics): Unit = tabBorder.paintBorder(this, g, 0, 0, width, height)

  /** Find the max label size of all labels. */
  private fun computeMaxSize(): MaxHolder {
    val max = MaxHolder()

    for (tab in visibleTabInfos) {
      val label = tab.tabLabel ?: continue
      max.label.height = max.label.height.coerceAtLeast(label.preferredSize.height)
      max.label.width = max.label.width.coerceAtLeast(label.preferredSize.width)
    }

    return max
  }

  /** Get the minimum size. */
  override fun getMinimumSize(): Dimension = computeSize({ it.minimumSize }, 1)

  /** get the preferred size */
  override fun getPreferredSize(): Dimension {
    return computeSize({ it.preferredSize }, 3)
  }

  /**
   * Computes the overall size of the tabs by applying a transformation function to determine the dimensions of each tab component, and then
   * finding the maximum width and height among all tabs. Additionally, adjusts the calculated dimensions by adding header size.
   *
   * @param transform A function that takes a JComponent and returns its dimension.
   * @param tabCount The number of tabs to consider in the size calculation.
   * @return The computed dimensions as a Dimension object.
   */
  private fun computeSize(
    transform: Function<in JComponent, out Dimension>,
    tabCount: Int
  ): Dimension {
    val size = Dimension()

    // Find the max width and height of all tabs
    for (tab in visibleTabInfos) {
      val c = tab.component
      if (c != null) {
        val tabSize = transform.`fun`(c)
        size.width = max(tabSize.width, size.width)
        size.height = max(tabSize.height, size.height)
      }
    }

    addHeaderSize(size, tabCount)
    return size
  }

  /**
   * Adjusts the given Dimension to account for the size of the header based on the number of tabs.
   *
   * @param size The original Dimension to be adjusted.
   * @param tabsCount The number of tabs to consider when computing the header size.
   */
  private fun addHeaderSize(size: Dimension, tabsCount: Int) {
    val header = computeHeaderPreferredSize(tabsCount)
    size.height += header.height
    size.width = max(size.width, header.width)

    val insets = layoutInsets
    size.width += insets.left + insets.right + 1
    size.height += insets.top + insets.bottom + 1
  }

  /**
   * Computes the preferred size of the header based on the number of tabs and their preferred sizes.
   *
   * @param tabsCount The number of tabs to consider for computing the preferred size.
   * @return The preferred size of the header as a Dimension object.
   */
  private fun computeHeaderPreferredSize(tabsCount: Int): Dimension {
    val size = Dimension()
    var currentTab = 0
    val horizontal = true

    for (tab in visibleTabInfos) {
      val canGrow = currentTab < tabsCount
      val eachLabel = tab.tabLabel ?: continue
      val eachPrefSize = eachLabel.preferredSize

      if (horizontal) {
        if (canGrow) {
          size.width += eachPrefSize.width
        }
        size.height = max(size.height, eachPrefSize.height)
      }

      currentTab++
    }

    size.height += tabBorder.thickness
    return size
  }

  /** Returns this component. */
  override fun getPresentation(): KrTabsPresentation = this

  /** Removes a tab. */
  override fun removeTab(info: EditorGroupTabInfo?): ActionCallback = doRemoveTab(tabInfo = info)

  /**
   * Removes the specified tab from the collection of tabs.
   *
   * @param tabInfo The tab information that needs to be removed. Can be null.
   * @return An ActionCallback that can be used to check if the action is complete.
   */
  @RequiresEdt
  private fun doRemoveTab(tabInfo: EditorGroupTabInfo?): ActionCallback {
    if (removeNotifyInProgress) thisLogger().warn(IllegalStateException("removeNotify in progress"))

    // Clear popup
    if (popupInfo == tabInfo) popupInfo = null

    if (tabInfo == null || !tabs.contains(tabInfo)) return ActionCallback.DONE

    // Remove the tab info from the lastLayoutPass
    if (lastLayoutPass != null) {
      lastLayoutPass!!.visibleTabInfos.remove(tabInfo)
    }

    val callback = ActionCallback()
    val toSelect = getToSelectOnRemoveOf(tabInfo)

    if (toSelect == null) {
      processRemove(info = tabInfo, forcedNow = true, setSelectedToNull = selectedInfo == tabInfo)
      removeDeferred().notifyWhenDone(callback)
    } else {
      val clearSelection = tabInfo == mySelectedInfo
      val transferFocus = isFocused(tabInfo)

      // Remove tab
      processRemove(info = tabInfo, forcedNow = false, setSelectedToNull = false)

      if (clearSelection) {
        mySelectedInfo = tabInfo
      }

      doSetSelected(tabInfo = toSelect, requestFocus = transferFocus, requestFocusInWindow = true)
        .doWhenProcessed { removeDeferred().notifyWhenDone(callback) }
    }

    if (visibleTabInfos.isEmpty()) {
      removeDeferredNow()
    }

    revalidateAndRepaint(true)
    fireTabRemoved(tabInfo)
    return callback
  }

  private fun isFocused(info: EditorGroupTabInfo): Boolean {
    val label = infoToLabel[info]
    val component = info.component
    val ancestorChecker = Predicate<Component?> { focusOwner ->
      var focusOwner = focusOwner
      while (focusOwner != null) {
        if (focusOwner === label || focusOwner === component) {
          return@Predicate true
        }
        focusOwner = focusOwner.parent
      }
      false
    }
    if (ancestorChecker.test(KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner)) {
      return true
    }
    val ourWindow = SwingUtilities.getWindowAncestor(this)
    return ourWindow != null && !ourWindow.isFocused && ancestorChecker.test(ourWindow.mostRecentFocusOwner)
  }

  private fun processRemove(info: EditorGroupTabInfo?, forcedNow: Boolean, setSelectedToNull: Boolean) {
    val tabLabel = infoToLabel[info]
    tabLabel?.let { remove(it) }

    val tabComponent = info!!.component!!

    if (forcedNow || !isToDeferRemoveForLater(tabComponent)) {
      remove(tabComponent)
    } else {
      addToDeferredRemove(tabComponent)
    }

    visibleTabInfos.remove(info)
    hiddenInfos.remove(info)
    infoToLabel.remove(info)

    if (tabLabelAtMouse === tabLabel) {
      tabLabelAtMouse = null
    }

    resetTabsCache()
    updateAll(false)
  }

  override fun findInfo(component: Component): EditorGroupTabInfo? {
    for (each in tabs) {
      if (each.component === component) {
        return each
      }
    }
    return null
  }

  override fun findInfo(event: MouseEvent): EditorGroupTabInfo? {
    val point = SwingUtilities.convertPoint(event.component, event.point, this)
    return doFindInfo(point)
  }

  private fun doFindInfo(point: Point): EditorGroupTabInfo? {
    var component = findComponentAt(point)
    while (component !== this) {
      if (component == null) return null
      if (component is EditorGroupTabLabel) {
        return component.info
      }
      component = component.parent
    }
    return null
  }

  /** Removes all tabs from the current collection of tabs. */
  override fun removeAllTabs() {
    tabs.forEach { removeTab(it) }
  }

  /**
   * Updates the container based on the visibility and selection status of tab components.
   *
   * @param forced Whether the relayout should be forced regardless of the current state.
   * @param layoutNow Whether the layout update should be executed immediately.
   */
  private fun updateContainer(forced: Boolean, layoutNow: Boolean) {
    for (tabInfo in java.util.List.copyOf(visibleTabInfos)) {
      val component = tabInfo.component ?: continue

      when (tabInfo) {
        mySelectedInfo -> {
          deferredToRemove.remove(component)
          val parent = component.parent

          if (parent != null && parent !== this) {
            parent.remove(component)
          }

          if (component.parent == null) {
            add(component)
          }
        }

        else           -> {
          if (component.parent == null) continue

          when {
            isToDeferRemoveForLater(component) -> addToDeferredRemove(component)
            else                               -> remove(component)
          }
        }
      }
    }
    relayout(forced, layoutNow)
  }

  override fun addImpl(component: Component, constraints: Any?, index: Int) {
    deferredToRemove.remove(component)
    if (component is EditorGroupTabLabel) {
      val uiDecorator = uiDecorator
      component.apply(uiDecorator?.decoration ?: defaultDecorator.decoration)
    }
    super.addImpl(component, constraints, index)
  }

  fun relayout(forced: Boolean, layoutNow: Boolean) {
    if (!forcedRelayout) {
      forcedRelayout = forced
    }
    if (moreToolbar != null) {
      moreToolbar.component.isVisible = true
    }
    revalidateAndRepaint(layoutNow)
  }

  override fun addTabMouseListener(listener: MouseListener): EditorGroupsTabsBase {
    removeListeners()
    tabMouseListeners.add(listener)
    addListeners()
    return this
  }

  override fun getComponent(): JComponent = this

  override fun getName(): @NlsActions.ActionText String? {
    return ""
  }

  private fun addListeners() {
    for (eachInfo in visibleTabInfos) {
      val label = infoToLabel[eachInfo]
      for (eachListener in tabMouseListeners) {
        when (eachListener) {
          is MouseListener       -> label!!.addMouseListener(eachListener)
          is MouseMotionListener -> label!!.addMouseMotionListener(eachListener)
          else                   -> assert(false)
        }
      }
    }
  }

  private fun removeListeners() {
    for (eachInfo in visibleTabInfos) {
      val label = infoToLabel[eachInfo]
      for (eachListener in tabMouseListeners) {
        when (eachListener) {
          is MouseListener       -> label!!.removeMouseListener(eachListener)
          is MouseMotionListener -> label!!.removeMouseMotionListener(eachListener)
          else                   -> assert(false)
        }
      }
    }
  }

  private fun updateListeners() {
    removeListeners()
    addListeners()
  }

  override fun addListener(listener: EditorGroupsTabsListener): EditorGroupsTabsBase = addListener(listener = listener, disposable = null)

  override fun addListener(listener: EditorGroupsTabsListener, disposable: Disposable?): EditorGroupsTabsBase {
    tabListeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { tabListeners.remove(listener) }
    }
    return this
  }

  override fun setSelectionChangeHandler(handler: EditorGroupsTabsBase.SelectionChangeHandler): EditorGroupsTabsBase {
    selectionChangeHandler = handler
    return this
  }

  fun setFocused(focused: Boolean) {
    if (isFocused == focused) {
      return
    }

    isFocused = focused
  }

  override fun getIndexOf(tabInfo: EditorGroupTabInfo?): Int = visibleTabInfos.indexOf(tabInfo)

  private fun disposePopupListener() {
    if (activePopup != null) {
      activePopup!!.removePopupMenuListener(popupListener)
      activePopup = null
    }
  }

  private fun setLayout(layout: EditorGroupsTabLayout): Boolean {
    if (effectiveLayout === layout) {
      return false
    }
    effectiveLayout = layout
    return true
  }

  override fun setUiDecorator(decorator: TabUiDecorator?): KrTabsPresentation {
    uiDecorator = decorator ?: defaultDecorator
    applyDecoration()
    return this
  }

  override fun setUI(newUI: ComponentUI) {
    super.setUI(newUI)
    applyDecoration()
  }

  override fun updateUI() {
    super.updateUI()
    SwingUtilities.invokeLater {
      applyDecoration()
      revalidateAndRepaint(false)
    }
  }

  private fun applyDecoration() {
    uiDecorator?.decoration?.let { uiDecoration ->
      for (tabLabel in infoToLabel.values) {
        tabLabel.apply(uiDecoration)
      }
    }
    for (tabInfo in tabs) {
      adjust(tabInfo)
    }
    relayout(forced = true, layoutNow = false)
  }

  protected open fun adjust(tabInfo: EditorGroupTabInfo) {
    @Suppress("DEPRECATION") UIUtil.removeScrollBorder(tabInfo.component!!)
  }

  override fun sortTabs(comparator: Comparator<EditorGroupTabInfo>) {
    visibleTabInfos.sortWith(comparator)
    resetTabsCache()
    relayout(forced = true, layoutNow = false)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, dataProvider)
    sink[QuickActionProvider.KEY] = this@KrTabsImpl
    sink[MorePopupAware.KEY] = this@KrTabsImpl
  }

  override fun getActions(originalProvider: Boolean): List<AnAction> = emptyList()

  override fun setDataProvider(dataProvider: DataProvider): KrTabsImpl {
    this.dataProvider = dataProvider
    return this
  }

  private fun applyResetComponents() {
    for (i in 0 until componentCount) {
      val each = getComponent(i)
      if (each is JComponent && !ClientProperty.isTrue(each, LAYOUT_DONE)) {
        layout(each, Rectangle(0, 0, 0, 0))
      }
    }
  }

  override fun setTabsPosition(position: EditorGroupsTabsPosition): KrTabsPresentation {
    this.position = position
    applyDecoration()
    relayout(forced = true, layoutNow = false)
    return this
  }

  override fun getTabsPosition(): EditorGroupsTabsPosition = position

  override fun toString(): String = "KrTabs visible=$visibleTabInfos selected=$mySelectedInfo"

  private class DefaultTabDecorator : TabUiDecorator {
    override val decoration = TabUiDecorator.TabUiDecoration(
      labelInsets = JBUI.insets(5, 8),
      contentInsetsSupplier = JBUI.insets(0, 4),
      iconTextGap = JBUI.scale(4)
    )
  }

  /**
   * A custom list popup step for displaying the hidden tabs
   *
   * @param values A list of [EditorGroupTabInfo] to be displayed in the popup.
   * @param separatorInfo An optional [EditorGroupTabInfo] that acts as a separator in the list.
   * @constructor Creates an instance of [HiddenInfosListPopupStep] with the given list of tab infos and optional separator info.
   */
  private inner class HiddenInfosListPopupStep(
    values: List<EditorGroupTabInfo>,
    private val separatorInfo: EditorGroupTabInfo?
  ) : BaseListPopupStep<EditorGroupTabInfo>(/* title = */ null, /* values = */ values) {
    // Flag for determining whether we should select the tab on selection
    var shouldSelectTab = true

    override fun onChosen(selectedTab: EditorGroupTabInfo, finalChoice: Boolean): PopupStep<*>? {
      when {
        shouldSelectTab -> select(tabInfo = selectedTab, requestFocus = true)
        else            -> shouldSelectTab = true
      }
      return FINAL_CHOICE
    }

    /** Put the separator above the separator info. */
    override fun getSeparatorAbove(tabInfo: EditorGroupTabInfo): ListSeparator? = when (tabInfo) {
      separatorInfo -> ListSeparator()
      else          -> null
    }

    override fun getIconFor(tabInfo: EditorGroupTabInfo): Icon? = tabInfo.icon

    override fun getTextFor(tabInfo: EditorGroupTabInfo): String = tabInfo.text
  }

  private class MaxHolder {
    @JvmField
    val label = Dimension()

    @JvmField
    val toolbar = Dimension()
  }

  private inner class ScrollBarActivityTracker {
    var isRecentlyActive: Boolean = false
      private set

    private val relayoutDelay = 2000
    private val relayoutAlarm = Alarm(parentDisposable)
    private var suspended = false

    fun suspend() {
      suspended = true
    }

    fun resume() {
      suspended = false
    }

    fun reset() {
      relayoutAlarm.cancelAllRequests()
      isRecentlyActive = false
    }

    fun setRecentlyActive() {
      if (suspended) return

      relayoutAlarm.cancelAllRequests()
      isRecentlyActive = true

      if (!relayoutAlarm.isDisposed) {
        relayoutAlarm.addRequest(Runnable {
          isRecentlyActive = false
          relayout(forced = false, layoutNow = false)
        }, relayoutDelay)
      }
    }

    fun cancelActivityTimer() {
      relayoutAlarm.cancelAllRequests()
    }
  }

  companion object {
    private const val SCROLL_BAR_THICKNESS = 3
    private const val LAYOUT_DONE: @NonNls String = "Layout.done"

    private val HIDDEN_INFOS_SELECT_INDEX_KEY = Key.create<Int>("HIDDEN_INFOS_SELECT_INDEX")

    @JvmField
    internal val defaultDecorator: TabUiDecorator = DefaultTabDecorator()

    @JvmStatic
    fun isSelectionClick(e: MouseEvent): Boolean {
      if (e.clickCount == 1 && !e.isPopupTrigger) {
        return e.button == MouseEvent.BUTTON1 && !e.isControlDown
      }
      return false
    }

    @JvmStatic
    fun resetLayout(c: JComponent?) {
      if (c == null) {
        return
      }
      c.putClientProperty(LAYOUT_DONE, null)
    }

    private fun createToolbar(
      group: ActionGroup,
      targetComponent: JComponent,
      actionManager: ActionManager
    ): ActionToolbar {
      val toolbar = actionManager.createActionToolbar(ActionPlaces.TABS_MORE_TOOLBAR, group, true)
      toolbar.targetComponent = targetComponent
      toolbar.component.border = JBUI.Borders.empty()
      toolbar.component.isOpaque = false
      return toolbar
    }

    private fun updateToolbarIfVisibilityChanged(toolbar: ActionToolbar?, previousBounds: Rectangle) {
      if (toolbar == null) {
        return
      }

      val bounds = toolbar.component.bounds
      if (bounds.isEmpty != previousBounds.isEmpty) {
        toolbar.updateActionsAsync()
      }
    }

    private fun isToDeferRemoveForLater(c: JComponent): Boolean = c.rootPane != null
  }
}
