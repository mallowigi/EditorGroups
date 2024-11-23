@file:Suppress("UsePropertyAccessSyntax", "detekt:All")

package krasa.editorGroups.tabs2.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.advanced.AdvancedSettings
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
import com.intellij.ui.tabs.impl.SingleHeightTabs
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
import org.jetbrains.annotations.Nls
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
import kotlin.Pair
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
  // List of visible tabs
  private val visibleTabInfos = ArrayList<EditorGroupTabInfo>()

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
        result.add(getIndexInVisibleArray(tabInfo), tabInfo)
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
    lazyUiDisposable(parent = parentDisposable, ui = this, child = this) { child, project ->
      if (this@KrTabsImpl.project == null && project != null) {
        this@KrTabsImpl.project = project
      }

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
        getVisibleInfos().asSequence().filter { it != mySelectedInfo }.map { it.component }.iterator()
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

  override fun canShowMorePopup(): Boolean {
    val rect = lastLayoutPass?.moreRect
    return rect != null && !rect.isEmpty
  }

  override fun showMorePopup(): JBPopup? {
    val rect = lastLayoutPass?.moreRect ?: return null
    val hiddenInfos = getVisibleInfos().filter { effectiveLayout!!.isTabHidden(it) }.takeIf { it.isNotEmpty() } ?: return null

    return showListPopup(rect = rect, hiddenInfos = hiddenInfos)
  }

  private fun showListPopup(rect: Rectangle, hiddenInfos: List<EditorGroupTabInfo>): JBPopup {
    val separatorIndex = hiddenInfos.indexOfFirst { info ->
      val label = infoToLabel[info]
      label!!.x >= 0
    }

    val separatorInfo = if (separatorIndex > 0) hiddenInfos[separatorIndex] else null
    val step = HiddenInfosListPopupStep(hiddenInfos, separatorInfo)
    val selectedIndex = ClientProperty.get(this, HIDDEN_INFOS_SELECT_INDEX_KEY)
    if (selectedIndex != null) {
      step.defaultOptionIndex = selectedIndex
    }
    val popup = JBPopupFactory.getInstance().createListPopup(project!!, step) {
      val descriptor = object : ListItemDescriptorAdapter<EditorGroupTabInfo>() {
        @Suppress("DialogTitleCapitalization")
        override fun getTextFor(value: EditorGroupTabInfo): String = value.text

        override fun getIconFor(value: EditorGroupTabInfo): Icon? = value.icon

        override fun hasSeparatorAboveOf(value: EditorGroupTabInfo): Boolean = value == separatorInfo
      }
      object : GroupedItemsListRenderer<EditorGroupTabInfo?>(descriptor) {
        private val HOVER_INDEX_KEY = Key.create<Int>("HOVER_INDEX")
        private val TAB_INFO_KEY = Key.create<EditorGroupTabInfo?>("TAB_INFO")
        private val SELECTED_KEY = Key.create<Boolean>("SELECTED")
        var component: JPanel? = null
        var iconLabel: JLabel? = null
        var textLabel: SimpleColoredComponent? = null
        var actionLabel: JLabel? = null
        var listMouseListener: MouseAdapter? = null
        override fun createItemComponent(): JComponent {
          // there is the separate label 'textLabel', but the original one still should be created,
          // as it is used from the GroupedElementsRenderer.configureComponent
          createLabel()
          component = JPanel()
          val layout = BoxLayout(component, BoxLayout.X_AXIS)
          component!!.layout = layout
          // painting underline for the selected tab
          component!!.border = object : Border {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
              if (!ClientProperty.isTrue(c, SELECTED_KEY)) {
                return
              }
              val inset = JBUI.scale(2)
              val arc = JBUI.scale(4)
              val theme: EditorGroupTabTheme = tabPainter.getTabTheme()

              val rect = Rectangle(x, y + inset, theme.underlineHeight, height - inset * 2)
              (g as Graphics2D).fill2DRoundRect(rect, arc.toDouble(), theme.underlineColor)
            }

            override fun getBorderInsets(c: Component): Insets = JBInsets.create(Insets(0, 9, 0, 3))

            override fun isBorderOpaque(): Boolean = true
          }
          val settings = UISettings.getInstance()
          if (!settings.closeTabButtonOnTheRight) {
            addActionLabel()
            val gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2
            component!!.add(Box.createRigidArea(Dimension(gap, 0)))
          }
          iconLabel = JLabel()
          component!!.add(iconLabel)
          val gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2
          component!!.add(Box.createRigidArea(Dimension(gap, 0)))
          textLabel = object : SimpleColoredComponent() {
            override fun getMaximumSize(): Dimension = preferredSize
          }
          textLabel!!.myBorder = null
          textLabel!!.setIpad(JBUI.emptyInsets())
          textLabel!!.setOpaque(true)
          component!!.add(textLabel)
          if (settings.closeTabButtonOnTheRight) {
            component!!.add(Box.createRigidArea(JBDimension(30, 0)))
            component!!.add(Box.createHorizontalGlue())
            addActionLabel()
          }
          val result = layoutComponent(component)
          if (result is SelectablePanel) {
            result.setBorder(JBUI.Borders.empty(0, 5))
            result.selectionInsets = JBInsets.create(0, 5)
            result.preferredHeight = JBUI.scale(26)
          }
          return result
        }

        private fun addActionLabel() {
          actionLabel = JLabel()
          component!!.add(actionLabel)
        }

        override fun customizeComponent(
          list: JList<out EditorGroupTabInfo?>?,
          info: EditorGroupTabInfo?,
          isSelected: Boolean
        ) {
          if (actionLabel != null) {
            val isHovered = ClientProperty.get(list, HOVER_INDEX_KEY) == myCurrentIndex
            val icon = getTabActionIcon(info!!, isHovered)
            actionLabel!!.icon = icon
            ClientProperty.put(actionLabel!!, TAB_INFO_KEY, info)
            addMouseListener(list!!)
          }
          val selectedInfo = selectedInfo
          var icon = info?.icon
          if (icon != null && info != selectedInfo) {
            icon = IconLoader.getTransparentIcon(icon, JBUI.CurrentTheme.EditorTabs.unselectedAlpha())
          }
          iconLabel!!.icon = icon
          textLabel!!.clear()
          info!!.coloredText.appendToComponent(textLabel!!)
          val customBackground = info.tabColor
          myRendererComponent.background = customBackground ?: JBUI.CurrentTheme.Popup.BACKGROUND
          ClientProperty.put(component!!, SELECTED_KEY, if (info == selectedInfo) true else null)
          component!!.invalidate()
        }

        override fun setComponentIcon(icon: Icon?, disabledIcon: Icon?) {
          // the icon will be set in customizeComponent
        }

        override fun createSeparator(): SeparatorWithText {
          val labelInsets = JBUI.CurrentTheme.Popup.separatorLabelInsets()
          return GroupHeaderSeparator(labelInsets)
        }

        private fun addMouseListener(list: JList<out EditorGroupTabInfo>) {
          if (listMouseListener != null) {
            return
          }

          listMouseListener = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
              val point = e.locationOnScreen
              SwingUtilities.convertPointFromScreen(point, list)
              val hoveredIndex = list.locationToIndex(point)
              val renderer = ListUtil.getDeepestRendererChildComponentAt(list, e.point)
              updateHoveredIconIndex(if (ClientProperty.get(renderer, TAB_INFO_KEY) != null) hoveredIndex else -1)
            }

            override fun mouseExited(e: MouseEvent) {
              updateHoveredIconIndex(-1)
            }

            private fun updateHoveredIconIndex(hoveredIndex: Int) {
              val oldIndex = ClientProperty.get(list, HOVER_INDEX_KEY)
              ClientProperty.put(list, HOVER_INDEX_KEY, hoveredIndex)
              if (oldIndex != hoveredIndex) {
                list.repaint()
              }
            }
          }
          val listeners = list.mouseListeners
          val motionListeners = list.mouseMotionListeners
          listeners.forEach(list::removeMouseListener)
          motionListeners.forEach(list::removeMouseMotionListener)
          list.addMouseListener(listMouseListener)
          list.addMouseMotionListener(listMouseListener)
          listeners.forEach(list::addMouseListener)
          motionListeners.forEach(list::addMouseMotionListener)
        }
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

  // returns the icon that will be used in the hidden tabs list
  protected open fun getTabActionIcon(info: EditorGroupTabInfo, isHovered: Boolean): Icon? = EmptyIcon.ICON_16

  private inner class HiddenInfosListPopupStep(
    values: List<EditorGroupTabInfo>,
    private val separatorInfo: EditorGroupTabInfo?
  ) :
    BaseListPopupStep<EditorGroupTabInfo>(
      null,
      values
    ) {
    var selectTab = true
    override fun onChosen(selectedValue: EditorGroupTabInfo, finalChoice: Boolean): PopupStep<*>? {
      if (selectTab) {
        select(selectedValue, true)
      } else {
        selectTab = true
      }
      return FINAL_CHOICE
    }

    override fun getSeparatorAbove(value: EditorGroupTabInfo): ListSeparator? = when (value) {
      separatorInfo -> ListSeparator()
      else          -> null
    }

    override fun getIconFor(value: EditorGroupTabInfo): Icon? = value.icon

    override fun getTextFor(value: EditorGroupTabInfo): String {
      @Suppress("DialogTitleCapitalization")
      return value.text
    }
  }

  override fun requestFocus() {
    val toFocus = toFocus
    when (toFocus) {
      null -> focusManager.doWhenFocusSettlesDown { super.requestFocus() }
      else -> focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
    }
  }

  override fun requestFocusInWindow(): Boolean = toFocus?.requestFocusInWindow() ?: super.requestFocusInWindow()

  override fun addTab(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo =
    addTab(info = info, index = index, isDropTarget = false, fireEvents = true)

  override fun addTabSilently(info: EditorGroupTabInfo, index: Int): EditorGroupTabInfo =
    addTab(info = info, index = index, isDropTarget = false, fireEvents = false)

  private fun addTab(info: EditorGroupTabInfo, index: Int, isDropTarget: Boolean, fireEvents: Boolean): EditorGroupTabInfo {
    if (!isDropTarget && tabs.contains(info)) {
      return tabs[tabs.indexOf(info)]
    }

    info.changeSupport.addPropertyChangeListener(this)
    val label = createTabLabel(info)
    infoToLabel.put(info, label)
    if (!isDropTarget) {
      if (index < 0 || index > visibleTabInfos.size - 1) {
        visibleTabInfos.add(info)
      } else {
        visibleTabInfos.add(index, info)
      }
    }
    resetTabsCache()
    updateText(info)
    updateIcon(info)
    info.tabLabel = label
    add(label)
    adjust(info)
    updateAll(false)
    if (info.isHidden) {
      updateHiding()
    }
    if (fireEvents && tabCount == 1) {
      fireBeforeSelectionChanged(null, info)
      fireSelectionChanged(null, info)
    }
    revalidateAndRepaint(false)
    return info
  }

  protected open fun createTabLabel(info: EditorGroupTabInfo): EditorGroupTabLabel = EditorGroupTabLabel(this, info)

  override fun addTab(info: EditorGroupTabInfo): EditorGroupTabInfo = addTab(info, -1)

  override fun getTabLabel(info: EditorGroupTabInfo): EditorGroupTabLabel? = infoToLabel[info]

  override fun setPopupGroup(popupGroup: ActionGroup, place: String, addNavigationGroup: Boolean): EditorGroupsTabsBase =
    setPopupGroupWithSupplier({ popupGroup }, place)

  override fun setPopupGroupWithSupplier(
    supplier: Supplier<out ActionGroup>,
    place: String
  ): EditorGroupsTabsBase {
    popupGroupSupplier = supplier::get
    popupPlace = place
    return this
  }

  private fun updateAll(forcedRelayout: Boolean) {
    val toSelect = selectedInfo
    setSelectedInfo(toSelect)
    updateContainer(forcedRelayout, false)
    removeDeferred()
    updateListeners()
    updateEnabling()
  }

  override fun select(info: EditorGroupTabInfo, requestFocus: Boolean): ActionCallback =
    doSetSelected(info = info, requestFocus = requestFocus, requestFocusInWindow = false)

  private fun doSetSelected(info: EditorGroupTabInfo, requestFocus: Boolean, requestFocusInWindow: Boolean): ActionCallback {
    if (!isEnabled) {
      return ActionCallback.REJECTED
    }

    // temporary state to make selection fully visible (scrolled in view)
    isMouseInsideTabsArea = false
    return if (selectionChangeHandler != null) {
      selectionChangeHandler!!.execute(
        info, requestFocus,
        object : ActiveRunnable() {
          override fun run(): ActionCallback = executeSelectionChange(info, requestFocus, requestFocusInWindow)
        }
      )
    } else {
      executeSelectionChange(info, requestFocus, requestFocusInWindow)
    }
  }

  private fun executeSelectionChange(
    info: EditorGroupTabInfo,
    requestFocus: Boolean,
    requestFocusInWindow: Boolean
  ): ActionCallback {
    if (mySelectedInfo != null && mySelectedInfo == info) {
      if (!requestFocus) {
        return ActionCallback.DONE
      }

      val owner = focusManager.focusOwner
      val c = info.component
      return if (c != null && owner != null && (c === owner || SwingUtilities.isDescendingFrom(owner, c))) {
        // This might look like a no-op, but in some cases it's not. In particular, it's required when a focus transfer has just been
        // requested to another component. E.g., this happens on 'unsplit' operation when we remove an editor component from UI hierarchy and
        // re-add it at once in a different layout, and want that editor component to preserve focus afterward.
        requestFocus(owner, requestFocusInWindow)
      } else {
        requestFocus(toFocus, requestFocusInWindow)
      }
    }

    val oldInfo = mySelectedInfo
    setSelectedInfo(info)

    val newInfo = selectedInfo
    val label = infoToLabel[info]
    if (label != null) {
      setComponentZOrder(label, 0)
    }

    setComponentZOrder(scrollBar, 0)
    fireBeforeSelectionChanged(oldInfo, newInfo)

    val oldValue = isMouseInsideTabsArea

    try {
      updateContainer(forced = false, layoutNow = true)
    } finally {
      isMouseInsideTabsArea = oldValue
    }

    fireSelectionChanged(oldInfo, newInfo)

    if (!requestFocus) {
      return removeDeferred()
    }

    val toFocus = toFocus
    if (project != null && toFocus != null) {
      val result = ActionCallback()
      requestFocus(toFocus, requestFocusInWindow).doWhenProcessed {
        if (project!!.isDisposed) {
          result.setRejected()
        } else {
          removeDeferred().notifyWhenDone(result)
        }
      }
      return result
    } else {
      ApplicationManager.getApplication().invokeLater({
        if (requestFocusInWindow) {
          requestFocusInWindow()
        } else {
          focusManager.requestFocusInProject(this, project)
        }
      }, ModalityState.nonModal())
      return removeDeferred()
    }
  }

  private fun fireBeforeSelectionChanged(oldInfo: EditorGroupTabInfo?, newInfo: EditorGroupTabInfo?) {
    if (oldInfo != newInfo) {
      oldSelection = oldInfo
      try {
        for (eachListener in tabListeners) {
          eachListener.beforeSelectionChanged(oldInfo, newInfo)
        }
      } finally {
        oldSelection = null
      }
    }
  }

  private fun fireSelectionChanged(oldInfo: EditorGroupTabInfo?, newInfo: EditorGroupTabInfo?) {
    if (oldInfo != newInfo) {
      for (eachListener in tabListeners) {
        eachListener?.selectionChanged(oldInfo, newInfo)
      }
    }
  }

  private fun fireTabRemoved(info: EditorGroupTabInfo) {
    for (eachListener in tabListeners) {
      eachListener?.tabRemoved(info)
    }
  }

  private fun requestFocus(toFocus: Component?, inWindow: Boolean): ActionCallback {
    if (toFocus == null) {
      return ActionCallback.DONE
    }
    if (isShowing) {
      val result = ActionCallback()
      ApplicationManager.getApplication().invokeLater {
        if (inWindow) {
          toFocus.requestFocusInWindow()
          result.setDone()
        } else {
          focusManager.requestFocusInProject(toFocus, project).notifyWhenDone(result)
        }
      }
      return result
    }
    return ActionCallback.REJECTED
  }

  private fun removeDeferred(): ActionCallback {
    if (deferredToRemove.isEmpty()) {
      return ActionCallback.DONE
    }

    val callback = ActionCallback()
    val executionRequest = ++removeDeferredRequest
    focusManager.doWhenFocusSettlesDown {
      if (removeDeferredRequest == executionRequest) {
        removeDeferredNow()
      }
      callback.setDone()
    }
    return callback
  }

  private fun unqueueFromRemove(c: Component) {
    deferredToRemove.remove(c)
  }

  private fun removeDeferredNow() {
    for (each in deferredToRemove.keys) {
      if (each != null && each.parent === this) {
        remove(each)
      }
    }
    deferredToRemove.clear()
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    val tabInfo = evt.source as EditorGroupTabInfo
    when (evt.propertyName) {
      EditorGroupTabInfo.ACTION_GROUP -> {
        relayout(false, false)
      }

      EditorGroupTabInfo.COMPONENT    -> relayout(true, false)
      EditorGroupTabInfo.TEXT         -> {
        updateText(tabInfo)
        revalidateAndRepaint()
      }

      EditorGroupTabInfo.ICON         -> {
        updateIcon(tabInfo)
        revalidateAndRepaint()
      }

      EditorGroupTabInfo.TAB_COLOR    -> revalidateAndRepaint()
      EditorGroupTabInfo.HIDDEN       -> {
        updateHiding()
        relayout(false, false)
      }

      EditorGroupTabInfo.ENABLED      -> updateEnabling()
    }
  }

  private fun updateEnabling() {
    val all = tabs
    for (tabInfo in all) {
      infoToLabel[tabInfo]!!.setTabEnabled(tabInfo.isEnabled)
    }
    val selected = selectedInfo
    if (selected != null && !selected.isEnabled) {
      val toSelect = getToSelectOnRemoveOf(selected)
      if (toSelect != null) {
        select(info = toSelect, requestFocus = focusManager.getFocusedDescendantFor(this) != null)
      }
    }
  }

  private fun updateHiding() {
    var update = false
    val visible = visibleTabInfos.iterator()
    while (visible.hasNext()) {
      val tabInfo = visible.next()
      if (tabInfo.isHidden && !hiddenInfos.containsKey(tabInfo)) {
        hiddenInfos.put(tabInfo, visibleTabInfos.indexOf(tabInfo))
        visible.remove()
        update = true
      }
    }
    val hidden = hiddenInfos.keys.iterator()
    while (hidden.hasNext()) {
      val each = hidden.next()
      if (!each.isHidden && hiddenInfos.containsKey(each)) {
        visibleTabInfos.add(getIndexInVisibleArray(each), each)
        hidden.remove()
        update = true
      }
    }
    if (update) {
      resetTabsCache()
      if (mySelectedInfo != null && hiddenInfos.containsKey(mySelectedInfo)) {
        val toSelect = getToSelectOnRemoveOf(mySelectedInfo!!)
        setSelectedInfo(toSelect)
      }
      updateAll(true)
    }
  }

  private fun getIndexInVisibleArray(each: EditorGroupTabInfo): Int {
    val info = hiddenInfos[each]
    var index = info ?: visibleTabInfos.size
    if (index > visibleTabInfos.size) {
      index = visibleTabInfos.size
    }
    if (index < 0) {
      index = 0
    }
    return index
  }

  private fun updateIcon(tabInfo: EditorGroupTabInfo) {
    infoToLabel[tabInfo]?.setIcon(tabInfo.icon)
  }

  fun revalidateAndRepaint() {
    revalidateAndRepaint(true)
  }

  override fun isOpaque(): Boolean = !visibleTabInfos.isEmpty()

  open fun revalidateAndRepaint(layoutNow: Boolean) {
    if (visibleTabInfos.isEmpty() && parent != null) {
      val nonOpaque = ComponentUtil.findUltimateParent(this)
      val toRepaint = SwingUtilities.convertRectangle(parent, bounds, nonOpaque)
      nonOpaque.repaint(toRepaint.x, toRepaint.y, toRepaint.width, toRepaint.height)
    }
    if (layoutNow) {
      validate()
    } else {
      revalidate()
    }
    repaint()
  }

  private fun updateText(tabInfo: EditorGroupTabInfo) {
    val label = infoToLabel[tabInfo]
    label!!.setText(tabInfo.coloredText)
    label.toolTipText = tabInfo.tooltipText
  }

  fun setSelectedInfo(info: EditorGroupTabInfo?) {
    mySelectedInfo = info
  }

  override fun getToSelectOnRemoveOf(info: EditorGroupTabInfo): EditorGroupTabInfo? {
    if (!visibleTabInfos.contains(info) || mySelectedInfo != info || visibleTabInfos.size == 1) {
      return null
    }

    val index = getVisibleInfos().indexOf(info)
    var result: EditorGroupTabInfo? = null
    if (index > 0) {
      result = findEnabledBackward(index, false)
    }
    return result ?: findEnabledForward(index, false)
  }

  fun findEnabledForward(from: Int, cycle: Boolean): EditorGroupTabInfo? {
    if (from < 0) {
      return null
    }

    var index = from
    val infos = getVisibleInfos()
    while (true) {
      index++
      if (index == infos.size) {
        if (!cycle) {
          break
        }
        index = 0
      }
      if (index == from) {
        break
      }
      val tabInfo = infos[index]
      if (tabInfo.isEnabled) {
        return tabInfo
      }
    }
    return null
  }

  fun findEnabledBackward(from: Int, cycle: Boolean): EditorGroupTabInfo? {
    if (from < 0) {
      return null
    }

    var index = from
    val infos = getVisibleInfos()
    while (true) {
      index--
      if (index == -1) {
        if (!cycle) {
          break
        }
        index = infos.size - 1
      }
      if (index == from) {
        break
      }
      val each = infos[index]
      if (each.isEnabled) {
        return each
      }
    }
    return null
  }

  override fun getTabAt(tabIndex: Int): EditorGroupTabInfo = tabs[tabIndex]

  override fun getTargetInfo(): EditorGroupTabInfo? = popupInfo ?: selectedInfo

  override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}

  override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
    resetPopup()
  }

  override fun popupMenuCanceled(e: PopupMenuEvent) {
    resetPopup()
  }

  private fun resetPopup() {
    // todo [kirillk] dirty hack, should rely on ActionManager to understand that menu item was either chosen on or cancelled
    SwingUtilities.invokeLater {
      // No need to reset popup info if a new popup has been already opened and myPopupInfo refers to the corresponding info.
      if (activePopup == null) {
        popupInfo = null
      }
    }
  }

  override fun setPaintBlocked(blocked: Boolean, takeSnapshot: Boolean) {}
  private fun addToDeferredRemove(c: Component) {
    if (!deferredToRemove.containsKey(c)) {
      deferredToRemove.put(c, c)
    }
  }

  override fun setToDrawBorderIfTabsHidden(toDrawBorderIfTabsHidden: Boolean): KrTabsPresentation = this

  override fun getJBTabs(): EditorGroupsTabsBase = this

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

  private fun updateTabsOffsetFromScrollBar() {
    val currentUnitsOffset = effectiveLayout!!.scrollOffset
    val updatedOffset = scrollBarModel.value
    effectiveLayout!!.scroll(updatedOffset - currentUnitsOffset)
    relayout(forced = false, layoutNow = false)
  }

  override fun doLayout() {
    try {
      val moreBoundsBeforeLayout = moreToolbar!!.component.bounds
      headerFitSize = computeHeaderFitSize()
      val visible = getVisibleInfos().toMutableList()

      val effectiveLayout = effectiveLayout
      if (effectiveLayout is EditorGroupsSingleRowLayout) {
        lastLayoutPass = effectiveLayout.layoutSingleRow(visible)
      }

      centerizeMoreToolbarPosition()

      applyResetComponents()

      scrollBar.orientation = Adjustable.HORIZONTAL
      scrollBar.bounds = getScrollBarBounds()
      updateScrollBarModel()

      updateToolbarIfVisibilityChanged(moreToolbar, moreBoundsBeforeLayout)
    } finally {
      forcedRelayout = false
    }
  }

  private fun centerizeMoreToolbarPosition() {
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

  private fun computeHeaderFitSize(): Dimension {
    val max = computeMaxSize()
    return Dimension(
      size.width,
      max(max.label.height, max.toolbar.height)
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

  private fun reset(tabInfo: EditorGroupTabInfo, shouldResetLabels: Boolean) {
    val c = tabInfo.component
    if (c != null) {
      resetLayout(c)
    }
    if (shouldResetLabels) {
      resetLayout(infoToLabel[tabInfo])
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    tabPainter.fillBackground(g as Graphics2D, Rectangle(0, 0, width, height))
    drawBorder(g)
  }

  open fun getVisibleInfos(): List<EditorGroupTabInfo> {
    if (AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      groupPinnedFirst(visibleTabInfos)
    }
    return visibleTabInfos
  }

  private fun groupPinnedFirst(infos: MutableList<EditorGroupTabInfo>) {
    var firstNotPinned = -1
    for (i in infos.indices) {
      if (firstNotPinned == -1) {
        firstNotPinned = i
      }
    }
  }

  override fun getComponentGraphics(graphics: Graphics): Graphics =
    JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))

  protected fun drawBorder(g: Graphics) {
    tabBorder.paintBorder(this, g, 0, 0, width, height)
  }

  private fun computeMaxSize(): Max {
    val max = Max()
    for (eachInfo in visibleTabInfos) {
      val label = infoToLabel[eachInfo]
      max.label.height = max.label.height.coerceAtLeast(label!!.preferredSize.height)
      max.label.width = max.label.width.coerceAtLeast(label.preferredSize.width)
    }
    return max
  }

  override fun getMinimumSize(): Dimension = computeSize({ it.minimumSize }, 1)

  override fun getPreferredSize(): Dimension {
    return computeSize(
      { component: JComponent -> component.preferredSize },
      3
    )
  }

  private fun computeSize(
    transform: com.intellij.util.Function<in JComponent, out Dimension>,
    tabCount: Int
  ): Dimension {
    val size = Dimension()
    for (each in visibleTabInfos) {
      val c = each.component
      if (c != null) {
        val eachSize = transform.`fun`(c)
        size.width = max(eachSize.width, size.width)
        size.height = max(eachSize.height, size.height)
      }
    }
    addHeaderSize(size, tabCount)
    return size
  }

  private fun addHeaderSize(size: Dimension, tabsCount: Int) {
    val header = computeHeaderPreferredSize(tabsCount)
    val horizontal = tabsPosition == EditorGroupsTabsPosition.TOP || tabsPosition == EditorGroupsTabsPosition.BOTTOM
    if (horizontal) {
      size.height += header.height
      size.width = max(size.width, header.width)
    } else {
      size.height += max(size.height, header.height)
      size.width += header.width
    }
    val insets = layoutInsets
    size.width += insets.left + insets.right + 1
    size.height += insets.top + insets.bottom + 1
  }

  private fun computeHeaderPreferredSize(tabsCount: Int): Dimension {
    val infos: Iterator<EditorGroupTabInfo?> = infoToLabel.keys.iterator()
    val size = Dimension()
    var currentTab = 0
    val horizontal = tabsPosition == EditorGroupsTabsPosition.TOP || tabsPosition == EditorGroupsTabsPosition.BOTTOM
    while (infos.hasNext()) {
      val canGrow = currentTab < tabsCount
      val eachInfo = infos.next()
      val eachLabel = infoToLabel[eachInfo]
      val eachPrefSize = eachLabel!!.preferredSize
      if (horizontal) {
        if (canGrow) {
          size.width += eachPrefSize.width
        }
        size.height = max(size.height, eachPrefSize.height)
      } else {
        size.width = max(size.width, eachPrefSize.width)
        if (canGrow) {
          size.height += eachPrefSize.height
        }
      }
      currentTab++
    }
    if (horizontal) {
      size.height += tabBorder.thickness
    } else {
      size.width += tabBorder.thickness
    }
    return size
  }

  override fun getPresentation(): KrTabsPresentation = this

  override fun removeTab(info: EditorGroupTabInfo?): ActionCallback = doRemoveTab(info, null)

  override fun removeTab(info: EditorGroupTabInfo, forcedSelectionTransfer: EditorGroupTabInfo?) {
    doRemoveTab(info, forcedSelectionTransfer)
  }

  @RequiresEdt
  private fun doRemoveTab(
    info: EditorGroupTabInfo?,
    forcedSelectionTransfer: EditorGroupTabInfo?
  ): ActionCallback {
    if (removeNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }

    if (popupInfo == info) popupInfo = null

    if (info == null || !tabs.contains(info)) return ActionCallback.DONE

    if (lastLayoutPass != null) {
      lastLayoutPass!!.visibleTabInfos.remove(info)
    }

    val result = ActionCallback()

    val toSelect = if (forcedSelectionTransfer == null) {
      getToSelectOnRemoveOf(info)
    } else {
      assert(visibleTabInfos.contains(forcedSelectionTransfer)) { "Cannot find tab for selection transfer, tab=$forcedSelectionTransfer" }
      forcedSelectionTransfer
    }

    if (toSelect != null) {
      val clearSelection = info == mySelectedInfo
      val transferFocus = isFocused(info)
      processRemove(info, false)
      if (clearSelection) {
        setSelectedInfo(info)
      }
      doSetSelected(toSelect, transferFocus, true).doWhenProcessed { removeDeferred().notifyWhenDone(result) }
    } else {
      processRemove(info, true)
      removeDeferred().notifyWhenDone(result)
    }

    if (visibleTabInfos.isEmpty()) {
      removeDeferredNow()
    }

    revalidateAndRepaint(true)
    fireTabRemoved(info)
    return result
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

  private fun processRemove(info: EditorGroupTabInfo?, forcedNow: Boolean) {
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

  private class Max {
    @JvmField
    val label = Dimension()

    @JvmField
    val toolbar = Dimension()
  }

  private fun updateContainer(forced: Boolean, layoutNow: Boolean) {
    for (tabInfo in java.util.List.copyOf(visibleTabInfos)) {
      val component = tabInfo.component ?: continue

      if (tabInfo == selectedInfo) {
        unqueueFromRemove(component)
        val parent = component.parent
        if (parent != null && parent !== this) {
          parent.remove(component)
        }
        if (component.parent == null) {
          add(component)
        }
      } else {
        if (component.parent == null) {
          continue
        }
        if (isToDeferRemoveForLater(component)) {
          addToDeferredRemove(component)
        } else {
          remove(component)
        }
      }
    }
    relayout(forced, layoutNow)
  }

  override fun addImpl(component: Component, constraints: Any?, index: Int) {
    unqueueFromRemove(component)
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

  override fun getIndexOf(tabInfo: EditorGroupTabInfo?): Int = getVisibleInfos().indexOf(tabInfo)

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
    sink[EditorGroupsTabsEx.NAVIGATION_ACTIONS_KEY] = this@KrTabsImpl
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
  }
}

private fun getFocusOwner(): JComponent? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JComponent

private fun updateToolbarIfVisibilityChanged(toolbar: ActionToolbar?, previousBounds: Rectangle) {
  if (toolbar == null) {
    return
  }

  val bounds = toolbar.component.bounds
  if (bounds.isEmpty != previousBounds.isEmpty) {
    toolbar.updateActionsAsync()
  }
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

private class TitleAction(
  private val tabs: KrTabsImpl,
  private val titleProvider: () -> Pair<Icon, @Nls String>
) : AnAction(), CustomComponentAction {
  private val label = object : JLabel() {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      size.height = JBUI.scale(SingleHeightTabs.UNSCALED_PREF_HEIGHT)
      return size
    }

    override fun updateUI() {
      super.updateUI()
      font = EditorGroupTabLabel(tabs, EditorGroupTabInfo()).labelComponent.font
      border = JBUI.Borders.empty(0, 5, 0, 6)
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    update()
    return label
  }

  private fun update() {
    val pair = titleProvider()
    label.icon = pair.first
    label.text = pair.second
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    // do nothing
  }

  override fun update(e: AnActionEvent) {
    update()
  }
}

private fun isToDeferRemoveForLater(c: JComponent): Boolean = c.rootPane != null

private fun isChanged(oldObject: Any?, newObject: Any?): Boolean = !Comparing.equal(oldObject, newObject)
