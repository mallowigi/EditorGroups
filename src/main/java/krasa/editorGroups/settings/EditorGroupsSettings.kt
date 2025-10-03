package krasa.editorGroups.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.ui.Messages
import com.intellij.util.messages.Topic
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.settings.EditorGroupsSettings.EditorGroupsSettingsState
import javax.swing.SwingConstants

@Service(Service.Level.APP)
@State(name = "EditorGroups", storages = [Storage(value = "EditorGroups.xml")], category = SettingsCategory.UI)
class EditorGroupsSettings : SimplePersistentStateComponent<EditorGroupsSettingsState>(EditorGroupsSettingsState()) {
  class EditorGroupsSettingsState : BaseState() {
    // Select first matching regex group if no group matches
    var isSelectRegexGroup: Boolean by property(false)

    // Select current folder group if no other group matches
    var isAutoFolders: Boolean by property(true)

    // Select same name group if no other group matches
    var isAutoSameFeature: Boolean by property(true)

    // Select same name group if no other group matches
    var isAutoSameName: Boolean by property(true)

    // Refresh button switches to a manual group if exists
    var isForceSwitch: Boolean by property(true)

    // Hide the panel if no group matches
    var isHideEmpty: Boolean by property(true)

    // Show group size at title
    var isShowSize: Boolean by property(true)

    // Show group metadata
    var isShowMeta: Boolean by property(false)

    // Color tabs of the current group
    var isColorTabs: Boolean by property(false)

    // Small labels
    var isSmallLabels: Boolean by property(true)

    // Continuous scrolling
    var isContinuousScrolling: Boolean by property(false)

    // Index synchronously - less flicker
    var isInitializeSynchronously: Boolean by property(false)

    // Index only egroups file for performance
    var isIndexOnlyEditorGroupsFiles: Boolean by property(false)

    // Exclude egroups files from indexing
    var isExcludeEditorGroupsFiles: Boolean by property(false)

    // Remember last group
    var isRememberLastGroup: Boolean by property(true)

    // Compact tabs
    var isCompactTabs: Boolean by property(false)

    // Split the list in groups
    var isGroupSwitchGroupAction: Boolean by property(false)

    // Show the panel at all
    var isShowPanel: Boolean by property(true)

    // Reuse the current tab
    var reuseCurrentTab: Boolean by property(false)

    // Limit the number of elements in a group
    var groupSizeLimitInt: Int by property(DEFAULT_GROUP_SIZE_LIMIT)

    // Limit the number of tabs in a group
    var tabSizeLimitInt: Int by property(DEFAULT_TAB_SIZE_LIMIT)

    // Custom font
    var isCustomFont: Boolean by property(false)

    // Custom font name
    var customFont: String? by string(DEFAULT_FONT)

    // Placement of the tab panel
    var tabsPlacement: Int by property(SwingConstants.TOP)

    // Islands
    var isRoundedTabs: Boolean by property(true)
  }

  @EditorGroupSetting([EditorGroupSetting.Category.REGEX, EditorGroupSetting.Category.GROUPS])
  var isSelectRegexGroup: Boolean
    get() = state.isSelectRegexGroup
    set(value) {
      state.isSelectRegexGroup = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS])
  var isAutoFolders: Boolean
    get() = state.isAutoFolders
    set(value) {
      state.isAutoFolders = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS])
  var isAutoSameFeature: Boolean
    get() = state.isAutoSameFeature
    set(value) {
      state.isAutoSameFeature = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS])
  var isAutoSameName: Boolean
    get() = state.isAutoSameName
    set(value) {
      state.isAutoSameName = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS])
  var isForceSwitch: Boolean
    get() = state.isForceSwitch
    set(value) {
      state.isForceSwitch = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.TABS])
  var reuseCurrentTab: Boolean
    get() = state.reuseCurrentTab
    set(value) {
      state.reuseCurrentTab = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS])
  var isHideEmpty: Boolean
    get() = state.isHideEmpty
    set(value) {
      state.isHideEmpty = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS, EditorGroupSetting.Category.UI])
  var isShowSize: Boolean
    get() = state.isShowSize
    set(value) {
      state.isShowSize = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS, EditorGroupSetting.Category.UI])
  var isShowMeta: Boolean
    get() = state.isShowMeta
    set(value) {
      state.isShowMeta = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.TABS, EditorGroupSetting.Category.UI])
  var isColorTabs: Boolean
    get() = state.isColorTabs
    set(value) {
      state.isColorTabs = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.UI])
  var isSmallLabels: Boolean
    get() = state.isSmallLabels
    set(value) {
      state.isSmallLabels = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.TABS])
  var isContinuousScrolling: Boolean
    get() = state.isContinuousScrolling
    set(value) {
      state.isContinuousScrolling = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.PERFORMANCE])
  var isInitializeSynchronously: Boolean
    get() = state.isInitializeSynchronously
    set(value) {
      state.isInitializeSynchronously = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.EGROUPS, EditorGroupSetting.Category.PERFORMANCE])
  var isIndexOnlyEditorGroupsFiles: Boolean
    get() = state.isIndexOnlyEditorGroupsFiles
    set(value) {
      state.isIndexOnlyEditorGroupsFiles = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.EGROUPS, EditorGroupSetting.Category.PERFORMANCE])
  var isExcludeEditorGroupsFiles: Boolean
    get() = state.isExcludeEditorGroupsFiles
    set(value) {
      state.isExcludeEditorGroupsFiles = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS])
  var isRememberLastGroup: Boolean
    get() = state.isRememberLastGroup
    set(value) {
      state.isRememberLastGroup = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.TABS, EditorGroupSetting.Category.UI])
  var isCompactTabs: Boolean
    get() = state.isCompactTabs
    set(value) {
      state.isCompactTabs = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS, EditorGroupSetting.Category.UI])
  var isGroupSwitchGroupAction: Boolean
    get() = state.isGroupSwitchGroupAction
    set(value) {
      state.isGroupSwitchGroupAction = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.UI])
  var isShowPanel: Boolean
    get() = state.isShowPanel
    set(value) {
      state.isShowPanel = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.GROUPS, EditorGroupSetting.Category.PERFORMANCE])
  var groupSizeLimit: Int
    get() = state.groupSizeLimitInt
    set(value) {
      state.groupSizeLimitInt = value.coerceIn(MIN_GROUP_SIZE_LIMIT, MAX_GROUP_SIZE_LIMIT)
    }

  @EditorGroupSetting([EditorGroupSetting.Category.TABS, EditorGroupSetting.Category.PERFORMANCE])
  var tabSizeLimit: Int
    get() = state.tabSizeLimitInt
    set(value) {
      state.tabSizeLimitInt = value.coerceIn(MIN_TAB_SIZE_LIMIT, MAX_TAB_SIZE_LIMIT)
    }

  @EditorGroupSetting([EditorGroupSetting.Category.UI, EditorGroupSetting.Category.TABS])
  var tabsPlacement: Int
    get() = state.tabsPlacement
    set(value) {
      state.tabsPlacement = value.coerceIn(SwingConstants.TOP, SwingConstants.BOTTOM)
    }

  @EditorGroupSetting([EditorGroupSetting.Category.UI, EditorGroupSetting.Category.TABS])
  var isCustomFont: Boolean
    get() = state.isCustomFont
    set(value) {
      state.isCustomFont = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.UI, EditorGroupSetting.Category.TABS])
  var customFont: String?
    get() = state.customFont
    set(value) {
      state.customFont = value
    }

  @EditorGroupSetting([EditorGroupSetting.Category.UI, EditorGroupSetting.Category.TABS])
  var isRoundedTabs: Boolean
    get() = state.isRoundedTabs
    set(value) {
      state.isRoundedTabs = value
    }

  fun fireChanged() {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(TOPIC)
      .configChanged(this)
  }

  fun clone(): EditorGroupsSettings {
    val clone = EditorGroupsSettings()
    clone.customFont = this.customFont
    clone.groupSizeLimit = this.groupSizeLimit
    clone.isAutoFolders = this.isAutoFolders
    clone.isAutoSameFeature = this.isAutoSameFeature
    clone.isAutoSameName = this.isAutoSameName
    clone.isColorTabs = this.isColorTabs
    clone.isCompactTabs = this.isCompactTabs
    clone.isContinuousScrolling = this.isContinuousScrolling
    clone.isCustomFont = this.isCustomFont
    clone.isExcludeEditorGroupsFiles = this.isExcludeEditorGroupsFiles
    clone.isForceSwitch = this.isForceSwitch
    clone.isGroupSwitchGroupAction = this.isGroupSwitchGroupAction
    clone.isHideEmpty = this.isHideEmpty
    clone.isIndexOnlyEditorGroupsFiles = this.isIndexOnlyEditorGroupsFiles
    clone.isInitializeSynchronously = this.isInitializeSynchronously
    clone.isRememberLastGroup = this.isRememberLastGroup
    clone.isSelectRegexGroup = this.isSelectRegexGroup
    clone.isShowMeta = this.isShowMeta
    clone.isShowPanel = this.isShowPanel
    clone.isShowSize = this.isShowSize
    clone.isSmallLabels = this.isSmallLabels
    clone.reuseCurrentTab = this.reuseCurrentTab
    clone.tabSizeLimit = this.tabSizeLimit
    clone.tabsPlacement = this.tabsPlacement
    clone.isRoundedTabs = this.isRoundedTabs
    return clone
  }

  fun apply(state: EditorGroupsSettings) {
    this.customFont = state.customFont
    this.groupSizeLimit = state.groupSizeLimit
    this.isAutoFolders = state.isAutoFolders
    this.isAutoSameFeature = state.isAutoSameFeature
    this.isAutoSameName = state.isAutoSameName
    this.isColorTabs = state.isColorTabs
    this.isCompactTabs = state.isCompactTabs
    this.isContinuousScrolling = state.isContinuousScrolling
    this.isCustomFont = state.isCustomFont
    this.isExcludeEditorGroupsFiles = state.isExcludeEditorGroupsFiles
    this.isForceSwitch = state.isForceSwitch
    this.isGroupSwitchGroupAction = state.isGroupSwitchGroupAction
    this.isHideEmpty = state.isHideEmpty
    this.isIndexOnlyEditorGroupsFiles = state.isIndexOnlyEditorGroupsFiles
    this.isInitializeSynchronously = state.isInitializeSynchronously
    this.isRememberLastGroup = state.isRememberLastGroup
    this.isSelectRegexGroup = state.isSelectRegexGroup
    this.isShowMeta = state.isShowMeta
    this.isShowPanel = state.isShowPanel
    this.isShowSize = state.isShowSize
    this.isSmallLabels = state.isSmallLabels
    this.reuseCurrentTab = state.reuseCurrentTab
    this.tabSizeLimit = state.tabSizeLimit
    this.tabsPlacement = state.tabsPlacement
    this.isRoundedTabs = state.isRoundedTabs
    this.fireChanged()
  }

  fun askResetSettings(action: () -> Unit) {
    val answer = Messages.showYesNoDialog(
      message("EditorGroupSettings.dialog.resetDefaults.consent"),
      message("EditorGroupSettings.resetDefaultsButton.text"),
      Messages.getWarningIcon()
    )
    if (answer == Messages.YES) {
      action()
      fireChanged()
    }
  }

  fun reset() {
    this.isSelectRegexGroup = false
    this.isAutoFolders = true
    this.isAutoSameFeature = true
    this.isAutoSameName = true
    this.isForceSwitch = true
    this.isHideEmpty = true
    this.isShowSize = true
    this.isColorTabs = true
    this.isSmallLabels = true
    this.isContinuousScrolling = false
    this.isInitializeSynchronously = false
    this.isIndexOnlyEditorGroupsFiles = false
    this.isExcludeEditorGroupsFiles = false
    this.isRememberLastGroup = true
    this.isCompactTabs = false
    this.isGroupSwitchGroupAction = false
    this.isShowPanel = true
    this.groupSizeLimit = DEFAULT_GROUP_SIZE_LIMIT
    this.tabSizeLimit = DEFAULT_TAB_SIZE_LIMIT
    this.tabsPlacement = SwingConstants.TOP
    this.isCustomFont = false
    this.customFont = DEFAULT_FONT
    this.isRoundedTabs = true
    this.fireChanged()
  }

  @Suppress("detekt:CyclomaticComplexMethod") // NON-NLS
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EditorGroupsSettings

    if (customFont != other.customFont) return false
    if (groupSizeLimit != other.groupSizeLimit) return false
    if (isAutoFolders != other.isAutoFolders) return false
    if (isAutoSameFeature != other.isAutoSameFeature) return false
    if (isAutoSameName != other.isAutoSameName) return false
    if (isColorTabs != other.isColorTabs) return false
    if (isCompactTabs != other.isCompactTabs) return false
    if (isContinuousScrolling != other.isContinuousScrolling) return false
    if (isCustomFont != other.isCustomFont) return false
    if (isExcludeEditorGroupsFiles != other.isExcludeEditorGroupsFiles) return false
    if (isForceSwitch != other.isForceSwitch) return false
    if (isGroupSwitchGroupAction != other.isGroupSwitchGroupAction) return false
    if (isHideEmpty != other.isHideEmpty) return false
    if (isIndexOnlyEditorGroupsFiles != other.isIndexOnlyEditorGroupsFiles) return false
    if (isInitializeSynchronously != other.isInitializeSynchronously) return false
    if (isRememberLastGroup != other.isRememberLastGroup) return false
    if (isSelectRegexGroup != other.isSelectRegexGroup) return false
    if (isShowMeta != other.isShowMeta) return false
    if (isShowPanel != other.isShowPanel) return false
    if (isShowSize != other.isShowSize) return false
    if (isSmallLabels != other.isSmallLabels) return false
    if (reuseCurrentTab != other.reuseCurrentTab) return false
    if (tabSizeLimit != other.tabSizeLimit) return false
    if (tabsPlacement != other.tabsPlacement) return false
    if (this@EditorGroupsSettings.isRoundedTabs != other.isRoundedTabs) return false

    return true
  }

  override fun hashCode(): Int {
    var result = isSelectRegexGroup.hashCode()
    result = 31 * result + customFont.hashCode()
    result = 31 * result + groupSizeLimit.hashCode()
    result = 31 * result + isAutoFolders.hashCode()
    result = 31 * result + isAutoSameFeature.hashCode()
    result = 31 * result + isAutoSameName.hashCode()
    result = 31 * result + isColorTabs.hashCode()
    result = 31 * result + isCompactTabs.hashCode()
    result = 31 * result + isContinuousScrolling.hashCode()
    result = 31 * result + isCustomFont.hashCode()
    result = 31 * result + isExcludeEditorGroupsFiles.hashCode()
    result = 31 * result + isForceSwitch.hashCode()
    result = 31 * result + isGroupSwitchGroupAction.hashCode()
    result = 31 * result + isHideEmpty.hashCode()
    result = 31 * result + isIndexOnlyEditorGroupsFiles.hashCode()
    result = 31 * result + isInitializeSynchronously.hashCode()
    result = 31 * result + isRememberLastGroup.hashCode()
    result = 31 * result + isShowMeta.hashCode()
    result = 31 * result + isShowPanel.hashCode()
    result = 31 * result + isShowSize.hashCode()
    result = 31 * result + isSmallLabels.hashCode()
    result = 31 * result + reuseCurrentTab.hashCode()
    result = 31 * result + tabSizeLimit
    result = 31 * result + tabsPlacement
    result = 31 * result + this@EditorGroupsSettings.isRoundedTabs.hashCode()
    return result
  }

  override fun toString(): String = """
    EditorGroupsSettings(
    |customFont=$customFont,
    |groupSizeLimit=$groupSizeLimit,
    |isAutoFolders=$isAutoFolders,
    |isAutoSameFeature=$isAutoSameFeature,
    |isAutoSameName=$isAutoSameName,
    |isColorTabs=$isColorTabs,
    |isCompactTabs=$isCompactTabs,
    |isContinuousScrolling=$isContinuousScrolling,
    |isCustomFont=$isCustomFont,
    |isExcludeEditorGroupsFiles=$isExcludeEditorGroupsFiles,
    |isForceSwitch=$isForceSwitch,
    |isGroupSwitchGroupAction=$isGroupSwitchGroupAction,
    |isHideEmpty=$isHideEmpty,
    |isIndexOnlyEditorGroupsFiles=$isIndexOnlyEditorGroupsFiles,
    |isInitializeSynchronously=$isInitializeSynchronously,
    |isRememberLastGroup=$isRememberLastGroup,
    |isSelectRegexGroup=$isSelectRegexGroup,
    |isShowMeta=$isShowMeta,
    |isShowPanel=$isShowPanel,
    |isShowSize=$isShowSize,
    |isSmallLabels=$isSmallLabels,
    |reuseCurrentTab=$reuseCurrentTab,
    |tabSizeLimit=$tabSizeLimit,
    |tabsPlacement=$tabsPlacement,
    |isRoundedTabs=${this@EditorGroupsSettings.isRoundedTabs},
    )
  """.trimMargin()

  interface SettingsNotifier {
    /** When Config is changed (settings) */
    fun configChanged(config: EditorGroupsSettings): Unit = Unit
  }

  companion object {
    const val DEFAULT_GROUP_SIZE_LIMIT: Int = 10_000
    const val MIN_GROUP_SIZE_LIMIT: Int = 1
    const val MAX_GROUP_SIZE_LIMIT: Int = 10_000

    const val DEFAULT_TAB_SIZE_LIMIT: Int = 50
    const val MIN_TAB_SIZE_LIMIT: Int = 1
    const val MAX_TAB_SIZE_LIMIT: Int = 100

    const val DEFAULT_FONT: String = "Lato"

    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<SettingsNotifier> = Topic.create(
      "Editor Groups Settings",
      SettingsNotifier::class.java
    )

    @JvmStatic
    val instance: EditorGroupsSettings by lazy { service<EditorGroupsSettings>() }
  }
}