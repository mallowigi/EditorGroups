package krasa.editorGroups.model

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.xmlb.annotations.Tag
import org.apache.commons.lang3.StringUtils
import java.util.regex.Pattern

/** Regex group model. */
@Tag("regexGroup")
class RegexGroupModel : BaseState() {
  var isEnabled: Boolean by property(true)
  var regex by string()
  var notComparingGroups by string()
  var scope by enum(Scope.CURRENT_FOLDER)

  /** Regex. */
  var myRegex: String?
    get() = regex
    set(value) {
      regex = value
      regexPattern = null
    }

  /** The regex group matches to avoid comparing. */
  var myNotComparingGroups: String?
    get() = notComparingGroups
    set(value) {
      if (value != null && value.contains("|")) throw IllegalArgumentException("notComparingGroups must not contain '|'")
      notComparingGroups = value
      notComparingGroupsIntArray = null
    }

  /** Scope. */
  var myScope: Scope?
    get() = scope
    set(value) {
      scope = value ?: Scope.CURRENT_FOLDER
    }

  @Transient
  var regexPattern: Pattern? = null
    get() {
      if (field == null) field = myRegex?.let { Pattern.compile(it) }
      return field
    }

  @Transient
  private var notComparingGroupsIntArray: IntArray? = null

  fun serialize(): String = "v1|$myScope|$myNotComparingGroups|$myRegex"

  fun matches(name: String): Boolean {
    try {
      return regexPattern?.matcher(name)?.matches() == true
    } catch (e: Exception) {
      thisLogger().error(e)
    }
    return false
  }

  fun copy(): RegexGroupModel = from(
    regex = myRegex ?: ".*",
    scope = myScope ?: Scope.CURRENT_FOLDER,
    notComparingGroups = myNotComparingGroups ?: ""
  )

  fun isComparingGroup(groupIndex: Int): Boolean {
    if (notComparingGroupsIntArray == null) notComparingGroupsIntArray = getNotComparingGroupsAsIntArray()

    return groupIndex !in notComparingGroupsIntArray!!
  }

  /**
   * Split the not comparing groups string and convert it to an int array. Ex: 1,2 -> [1,2] or 1,,2 -> [1,-1,2]
   *
   * @return the int array
   */
  private fun getNotComparingGroupsAsIntArray(): IntArray {
    if (StringUtils.isBlank(myNotComparingGroups)) return IntArray(0)

    val split = myNotComparingGroups!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val size = split.size
    val arr = IntArray(size)

    for (i in 0 until size) {
      try {
        var s = split[i]
        if (StringUtils.isBlank(s)) s = "-1"
        arr[i] = s.toInt()
      } catch (e: Exception) {
        thisLogger().error(e)
        arr[i] = -1
      }
    }
    return arr
  }

  override fun toString(): String =
    "RegexGroupModel{regex='$myRegex', notComparingGroups='$myNotComparingGroups', scope=$myScope, enabled=$isEnabled}"

  enum class Scope {
    CURRENT_FOLDER,
    INCLUDING_SUBFOLDERS,
    WHOLE_PROJECT
  }

  companion object {
    /**
     * Creates and returns a `RegexGroupModel` instance with specified parameters.
     *
     * @param regex the regular expression pattern to be used (default is ".*")
     * @param scope the scope in which the regular expression is evaluated (default is `Scope.CURRENT_FOLDER`)
     * @param notComparingGroups a comma-separated string representing groups that are not compared (default is "")
     * @return a configured instance of `RegexGroupModel`
     */
    @JvmStatic
    fun from(
      regex: String = ".*",
      scope: Scope = Scope.CURRENT_FOLDER,
      notComparingGroups: String = ""
    ): RegexGroupModel {
      val model = RegexGroupModel()
      model.myRegex = regex
      model.myScope = scope
      model.myNotComparingGroups = notComparingGroups

      return model
    }

    /**
     * Deserializes a given string into a `RegexGroupModel` object based on the specific format versions.
     *
     * @param str The string to be deserialized. It must begin with a version identifier ("v0" or "v1").
     * @return The deserialized `RegexGroupModel` if the string format is valid, or `null` if the format is not
     *    supported or an error occurs during deserialization.
     */
    @JvmStatic
    @Suppress("MagicNumber")
    fun deserialize(str: String): RegexGroupModel? {
      try {
        when {
          str.startsWith("v0") -> {
            val scopeEnd = str.indexOf("|", 3)
            val scope = str.substring(3, scopeEnd)
            val regex = str.substring(scopeEnd + 1)

            return from(
              regex = regex,
              scope = Scope.valueOf(scope)
            )
          }

          str.startsWith("v1") -> {
            val scopeEnd = str.indexOf("|", 3)
            val scope = str.substring(3, scopeEnd)

            val notComparingGroupsEnd = str.indexOf("|", scopeEnd + 1)
            val notComparingGroups = str.substring(scopeEnd + 1, notComparingGroupsEnd)

            val regex = str.substring(notComparingGroupsEnd + 1)

            return from(
              regex = regex,
              scope = Scope.valueOf(scope),
              notComparingGroups = notComparingGroups
            )
          }

          else                 -> throw RuntimeException("not supported")
        }
      } catch (e: Throwable) {
        thisLogger().warn("$e; source='$str'")
        return null
      }
    }
  }
}
