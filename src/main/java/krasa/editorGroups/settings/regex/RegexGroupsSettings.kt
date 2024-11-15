package krasa.editorGroups.settings.regex

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.ui.Messages
import com.intellij.util.messages.Topic
import krasa.editorGroups.messages.EditorGroupsBundle.message
import krasa.editorGroups.model.RegexGroupModels
import krasa.editorGroups.settings.EditorGroupSetting
import krasa.editorGroups.settings.regex.RegexGroupsSettings.RegexGroupsSettingsState

@Service(Service.Level.APP)
@State(
  name = "RegexGroups",
  storages = [Storage(value = "EditorGroups.xml")],
  category = SettingsCategory.UI
)
class RegexGroupsSettings : SimplePersistentStateComponent<RegexGroupsSettingsState>(RegexGroupsSettingsState()) {
  class RegexGroupsSettingsState : BaseState() {
    var regexGroupModels: RegexGroupModels by property(RegexGroupModels())
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

  fun clone(): RegexGroupsSettings {
    val clone = RegexGroupsSettings()
    clone.regexGroupModels = this.regexGroupModels
    return clone
  }

  fun apply(state: RegexGroupsSettings) {
    this.regexGroupModels = state.regexGroupModels
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
    this.regexGroupModels = RegexGroupModels()
    this.fireChanged()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RegexGroupsSettings
    return regexGroupModels == other.regexGroupModels
  }

  override fun hashCode(): Int {
    var result = regexGroupModels.hashCode()
    result = 31 * result + state.hashCode()
    return result
  }

  override fun toString(): String = """
    RegexGroupsSettings(
      regexGroupModels=$regexGroupModels
    )
  """.trimMargin()

  interface RegexSettingsNotifier {
    /** When Config is changed (settings) */
    fun configChanged(config: RegexGroupsSettings): Unit = Unit
  }

  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<RegexSettingsNotifier> = Topic.create(
      "Regex Groups Settings",
      RegexSettingsNotifier::class.java
    )

    @JvmStatic
    val instance: RegexGroupsSettings by lazy { service<RegexGroupsSettings>() }
  }
}
