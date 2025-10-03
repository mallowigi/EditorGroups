package krasa.editorGroups.model

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

@Tag("regexGroups")
class RegexGroupModels : BaseState() {
  @get:XCollection
  var regexModels: MutableList<RegexGroupModel> by list()

  override fun toString(): String = "RegExpGroupModels{models=$regexModels}"

  fun findFirstMatching(fileName: String): RegexGroupModel? = regexModels
    .sortedByDescending { it.priority }
    .firstOrNull { it.isEnabled && it.matches(fileName) }

  fun findMatching(fileName: String): List<RegexGroupModel> = regexModels
    .filter { it.isEnabled && it.matches(fileName) }
    .sortedByDescending { it.priority }

  fun find(substring: String): RegexGroupModel? {
    val deserializedGroup = RegexGroupModel.deserialize(substring) ?: return null
    return regexModels
      .sortedByDescending { it.priority }
      .find { it.isEnabled && it.myRegex == deserializedGroup.myRegex }
  }

  fun findProjectRegexGroups(): List<RegexGroupModel> = regexModels
    .filter { it.isEnabled && it.myScope == Scope.WHOLE_PROJECT }
    .sortedByDescending { it.priority }
}
