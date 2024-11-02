package krasa.editorGroups.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.ui.Messages
import com.intellij.util.messages.Topic
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.RegexGroupModels
import krasa.editorGroups.settings.EditorGroupsSettings.EditorGroupsSettingsState

@Service(Service.Level.APP)
@State(name = "EditorGroups", storages = [Storage(value = "EditorGroups.xml")], category = SettingsCategory.UI)
class EditorGroupsSettings : SimplePersistentStateComponent<EditorGroupsSettingsState>(EditorGroupsSettingsState()) {
  class EditorGroupsSettingsState : BaseState() {
    var regexGroupModels: RegexGroupModels by property(RegexGroupModels())

    // Select first matching regex group if no group matches
    var isSelectRegexGroup: Boolean by property(false)

    // Select current folder group if no other group matches
    var isAutoFolders: Boolean by property(true)

    // Select same name group if no other group matches
    var isAutoSameName: Boolean by property(true)

    // Refresh button switches to a manual group if exists
    var isForceSwitch: Boolean by property(true)

    // Hide the panel if no group matches
    var isHideEmpty: Boolean by property(true)

    // Show group size at title
    var isShowSize: Boolean by property(false)

    // Color tabs of the current group
    var isColorTabs: Boolean by property(true)

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

    // Limit the number of elements in a group
    var groupSizeLimitInt: Int by property(DEFAULT_GROUP_SIZE_LIMIT)

    // Limit the number of tabs in a group
    var tabSizeLimitInt: Int by property(DEFAULT_TAB_SIZE_LIMIT)
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
      state.groupSizeLimitInt = value.toInt().coerceIn(MIN_GROUP_SIZE_LIMIT, MAX_GROUP_SIZE_LIMIT)
    }

  @EditorGroupSetting([EditorGroupSetting.Category.TABS, EditorGroupSetting.Category.PERFORMANCE])
  var tabSizeLimit: Int
    get() = state.tabSizeLimitInt
    set(value) {
      state.tabSizeLimitInt = value.toInt().coerceIn(MIN_TAB_SIZE_LIMIT, MAX_TAB_SIZE_LIMIT)
    }

  @EditorGroupSetting([EditorGroupSetting.Category.REGEX, EditorGroupSetting.Category.GROUPS])
  var regexGroupModels: RegexGroupModels
    get() = state.regexGroupModels
    set(value) {
      state.regexGroupModels = value
    }

  fun fireChanged() {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(TOPIC)
      .configChanged(this)
  }

  fun clone(): EditorGroupsSettings {
    val clone = EditorGroupsSettings()
    clone.isSelectRegexGroup = this.isSelectRegexGroup
    clone.isAutoFolders = this.isAutoFolders
    clone.isAutoSameName = this.isAutoSameName
    clone.isForceSwitch = this.isForceSwitch
    clone.isHideEmpty = this.isHideEmpty
    clone.isShowSize = this.isShowSize
    clone.isColorTabs = this.isColorTabs
    clone.isSmallLabels = this.isSmallLabels
    clone.isContinuousScrolling = this.isContinuousScrolling
    clone.isInitializeSynchronously = this.isInitializeSynchronously
    clone.isIndexOnlyEditorGroupsFiles = this.isIndexOnlyEditorGroupsFiles
    clone.isExcludeEditorGroupsFiles = this.isExcludeEditorGroupsFiles
    clone.isRememberLastGroup = this.isRememberLastGroup
    clone.isCompactTabs = this.isCompactTabs
    clone.isGroupSwitchGroupAction = this.isGroupSwitchGroupAction
    clone.isShowPanel = this.isShowPanel
    clone.groupSizeLimit = this.groupSizeLimit
    clone.tabSizeLimit = this.tabSizeLimit
    return clone
  }

  fun apply(state: EditorGroupsSettings) {
    this.isSelectRegexGroup = state.isSelectRegexGroup
    this.isAutoFolders = state.isAutoFolders
    this.isAutoSameName = state.isAutoSameName
    this.isForceSwitch = state.isForceSwitch
    this.isHideEmpty = state.isHideEmpty
    this.isShowSize = state.isShowSize
    this.isColorTabs = state.isColorTabs
    this.isSmallLabels = state.isSmallLabels
    this.isContinuousScrolling = state.isContinuousScrolling
    this.isInitializeSynchronously = state.isInitializeSynchronously
    this.isIndexOnlyEditorGroupsFiles = state.isIndexOnlyEditorGroupsFiles
    this.isExcludeEditorGroupsFiles = state.isExcludeEditorGroupsFiles
    this.isRememberLastGroup = state.isRememberLastGroup
    this.isCompactTabs = state.isCompactTabs
    this.isGroupSwitchGroupAction = state.isGroupSwitchGroupAction
    this.isShowPanel = state.isShowPanel
    this.groupSizeLimit = state.groupSizeLimit
    this.tabSizeLimit = state.tabSizeLimit
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
    this.isAutoSameName = true
    this.isForceSwitch = true
    this.isHideEmpty = true
    this.isShowSize = false
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
    this.fireChanged()
  }

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

    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<SettingsNotifier> = Topic.create("Editor Groups Settings", SettingsNotifier::class.java)

    @JvmStatic
    val instance: EditorGroupsSettings by lazy { service<EditorGroupsSettings>() }
  }
}
