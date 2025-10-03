package krasa.editorGroups.model

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.ColorUtil
import com.intellij.util.xmlb.annotations.Tag
import krasa.editorGroups.support.fromHex
import krasa.editorGroups.support.generateColor
import krasa.editorGroups.support.toHex
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.util.*
import java.util.regex.Pattern

/** Regex group model. */
@Tag("regexGroup")
class RegexGroupModel : BaseState() {
  var isEnabled: Boolean by property(true)
  var touched: Boolean = false
  var name: String? by string()
  var regex: String? by string()
  var notComparingGroups: String? by string()
  var scope: Scope by enum(Scope.CURRENT_FOLDER)
  var color: String? by string()
  var priority: Int by property(0)

  /** Optional regex name. */
  var myName: String
    get() = name ?: generateUniqueName()
    set(value) {
      name = value
    }

  /** Regex. */
  var myRegex: String
    get() = regex ?: ".*"
    set(value) {
      regex = value
      regexPattern = null
    }

  /** The regex group matches to avoid comparing. */
  var myNotComparingGroups: String
    get() = notComparingGroups ?: ""
    set(value) {
      require(!value.contains("|")) { "notComparingGroups must not contain '|'" }
      notComparingGroups = value
    }

  /** Scope. */
  var myScope: Scope
    get() = scope
    set(value) {
      scope = value
    }

  var myColor: Color
    get() = color?.fromHex() ?: generateColor(name)
    set(value) {
      color = value.toHex()
    }

  @Transient
  var regexPattern: Pattern? = null
    get() {
      if (field == null) field = myRegex.let { Pattern.compile(it) }
      return field
    }

  val isEmpty: Boolean
    get() = myName.isBlank() || myRegex.isBlank()

  @NonNls
  fun serialize(): String = "v2|$myName|$myScope|$myNotComparingGroups|$priority|$color|$myRegex"

  fun apply(other: RegexGroupModel) {
    isEnabled = other.isEnabled
    myName = other.myName
    myRegex = other.myRegex
    myNotComparingGroups = other.myNotComparingGroups
    myScope = other.myScope
    priority = other.priority
    myColor = other.myColor
  }

  fun matches(name: String): Boolean = try {
    regexPattern?.matcher(name)?.matches() == true
  } catch (e: Exception) {
    thisLogger().error(e)
    false
  }

  fun copy(): RegexGroupModel = from(
    name = myName,
    isEnabled = isEnabled,
    regex = myRegex,
    scope = myScope,
    notComparingGroups = myNotComparingGroups,
    priority = priority,
    color = myColor
  )

  fun isComparingGroup(groupIndex: Int): Boolean {
    val notComparingGroupsIntArray = getNotComparingGroupsAsIntArray()

    return groupIndex !in notComparingGroupsIntArray
  }

  /**
   * Split the not comparing groups string and convert it to an int array. Ex: 1,2 -> [1,2] or 1,,2 -> [1,-1,2]
   *
   * @return the int array
   */
  private fun getNotComparingGroupsAsIntArray(): IntArray {
    if (StringUtils.isBlank(myNotComparingGroups)) return IntArray(0)

    val split = myNotComparingGroups.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val size = split.size
    val arr = IntArray(size)

    @Suppress("ReplaceManualRangeWithIndicesCalls")
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

  override fun toString(): String = """
    RegexGroupModel{
      |name='$myName',
      |regex='$myRegex',
      |notComparingGroups='$myNotComparingGroups',
      |scope=$myScope,
      |enabled=$isEnabled
      |color=$myColor
      |priority=$priority
    }
  """.trimMargin()

  companion object {
    const val V0: String = "v0"
    const val V1: String = "v1"
    const val V2: String = "v2"

    /**
     * Creates and returns a `RegexGroupModel` instance with specified parameters.
     *
     * @param name the optional regex name (default is generated unique name)
     * @param isEnabled indicates whether the group is enabled (default is true)
     * @param regex the regular expression pattern to be used (default is ".*")
     * @param scope the scope in which the regular expression is evaluated (default is `Scope.CURRENT_FOLDER`)
     * @param notComparingGroups a comma-separated string representing groups that are not compared (default is "")
     * @param color the color to be used for the group (default is a random color based on the name)
     * @param priority the priority level for the regex group (default is 0)
     * @return a configured instance of `RegexGroupModel`
     */
    @JvmStatic
    fun from(
      name: String = generateUniqueName(),
      isEnabled: Boolean = true,
      regex: String = ".*",
      scope: Scope = Scope.CURRENT_FOLDER,
      notComparingGroups: String = "",
      color: Color = generateColor(name),
      priority: Int = 0
    ): RegexGroupModel {
      val model = RegexGroupModel()
      model.isEnabled = isEnabled
      model.myName = name
      model.myRegex = regex
      model.myScope = scope
      model.myNotComparingGroups = notComparingGroups
      model.myColor = color
      model.priority = priority

      return model
    }

    @NonNls
    fun generateUniqueName(): String = "RegexGroup-${UUID.randomUUID()}"

    /**
     * Deserializes a given string into a `RegexGroupModel` object based on the specific format versions.
     *
     * @param str The string to be deserialized. It must begin with a version identifier ("v0" or "v1").
     * @return The deserialized `RegexGroupModel` if the string format is valid, or `null` if the format is not supported or an error occurs
     *    during deserialization.
     */
    @JvmStatic
    @Suppress("MagicNumber")
    fun deserialize(str: String): RegexGroupModel? {
      val elements = str.split("|")

      try {
        when {
          str.startsWith(V0) -> {
            val (_, scope, regex) = elements

            return from(
              regex = regex,
              scope = Scope.valueOf(scope)
            )
          }

          str.startsWith(V1) -> {
            val (_, scope, regex, notComparingGroups) = elements

            return from(
              regex = regex,
              scope = Scope.valueOf(scope),
              notComparingGroups = notComparingGroups
            )
          }

          str.startsWith(V2) -> {
            val name = elements[1]
            val scope = elements[2]
            val notComparingGroups = elements[3]
            val priority = elements[4]
            val color = elements[5]
            val regex = elements[6]

            return from(
              name = name,
              regex = regex,
              scope = Scope.valueOf(scope),
              notComparingGroups = notComparingGroups,
              color = ColorUtil.fromHex(color),
              priority = priority.toInt()
            )
          }

          else               -> {
            thisLogger().error("Format not supported")
            return null
          }
        }
      } catch (e: Throwable) {
        thisLogger().warn("$e; source='$str'")
        return null
      }
    }
  }
}
