package krasa.editorGroups.support

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.ColorIcon
import krasa.editorGroups.language.annotator.LanguagePatternHolder
import org.jetbrains.annotations.NonNls
import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager

@NonNls
val colorMap: HashMap<String, String> = hashMapOf(
  "aliceblue" to "#f0f8ff",
  "alizarin" to "#e74c3c",
  "amethyst" to "#9b59b6",
  "antiquewhite" to "#faebd7",
  "aqua" to "#00ffff",
  "aquamarine" to "#7fffd4",
  "asbestos" to "#7f8c8d",
  "azure" to "#f0ffff",
  "beige" to "#f5f5dc",
  "belizehole" to "#2980b9",
  "bisque" to "#ffe4c4",
  "black" to "#000000",
  "blanchedalmond" to "#ffebcd",
  "blue" to "#0000ff",
  "blueviolet" to "#8a2be2",
  "brown" to "#a52a2a",
  "buff" to "#F0DC82",
  "burlywood" to "#deb887",
  "cadetblue" to "#5f9ea0",
  "carrot" to "#e67e22",
  "chartreuse" to "#7fff00",
  "chocolate" to "#d2691e",
  "clouds" to "#ecf0f1",
  "concrete" to "#95a5a6",
  "coral" to "#ff7f50",
  "cornflowerblue" to "#6495ed",
  "cornsilk" to "#fff8dc",
  "crimson" to "#dc143c",
  "cyan" to "#00ffff",
  "darkblue" to "#00008b",
  "darkbrown" to "#5C4033",
  "darkbuff" to "#976638",
  "darkcyan" to "#008b8b",
  "darkgold" to "#EEBC1D",
  "darkgoldenrod" to "#b8860b",
  "darkgray" to "#404040",
  "darkgreen" to "#006400",
  "darkgrey" to "#a9a9a9",
  "darkivory" to "#F2E58F",
  "darkkhaki" to "#bdb76b",
  "darkmagenta" to "#8b008b",
  "darkmustard" to "#7C7C40",
  "darkolivegreen" to "#556b2f",
  "darkorange" to "#ff8c00",
  "darkorchid" to "#9932cc",
  "darkpink" to "#E75480",
  "darkred" to "#8b0000",
  "darksalmon" to "#e9967a",
  "darksilver" to "#AFAFAF",
  "darkseagreen" to "#8fbc8f",
  "darkslateblue" to "#483d8b",
  "darkslategrey" to "#2f4f4f",
  "darkturquoise" to "#00ced1",
  "darkviolet" to "#9400d3",
  "darkyellow" to "#FFCC00",
  "deeppink" to "#ff1493",
  "deepskyblue" to "#00bfff",
  "dimgrey" to "#696969",
  "dodgerblue" to "#1e90ff",
  "emerald" to "#2ecc71",
  "firebrick" to "#b22222",
  "floralwhite" to "#fffaf0",
  "forestgreen" to "#228b22",
  "fuchsia" to "#ff00ff",
  "gainsboro" to "#dcdcdc",
  "ghostwhite" to "#f8f8ff",
  "gold" to "#ffd700",
  "goldenrod" to "#daa520",
  "gray" to "#808080",
  "green" to "#008000",
  "greensea" to "#16a085",
  "greenyellow" to "#adff2f",
  "honeydew" to "#f0fff0",
  "hotpink" to "#ff69b4",
  "indianred" to "#cd5c5c",
  "indigo" to "#4b0082",
  "ivory" to "#fffff0",
  "khaki" to "#f0e68c",
  "lavender" to "#e6e6fa",
  "lavenderblush" to "#fff0f5",
  "lawngreen" to "#7cfc00",
  "lemonchiffon" to "#fffacd",
  "lightblack" to "#808080",
  "lightblue" to "#add8e6",
  "lightbrown" to "#9966FF",
  "lightbuff" to "#ECD9B0",
  "lightcoral" to "#f08080",
  "lightcyan" to "#e0ffff",
  "lightgold" to "#F1E5AC",
  "lightgoldenrod" to "#FFEC8B",
  "lightgoldenrodyellow" to "#fafad2",
  "lightgray" to "#d3d3d3",
  "lightgreen" to "#90ee90",
  "lightgrey" to "#d3d3d3",
  "lightivory" to "#FFF8C9",
  "lightmagenta" to "#FF77FF",
  "lightmustard" to "#EEDD62",
  "lightorange" to "#D9A465",
  "lightpink" to "#ffb6c1",
  "lightred" to "#FF3333",
  "lightsalmon" to "#ffa07a",
  "lightseagreen" to "#20b2aa",
  "lightsilver" to "#E1E1E1",
  "lightskyblue" to "#87cefa",
  "lightslategray" to "#778899",
  "lightslategrey" to "#778899",
  "lightsteelblue" to "#b0c4de",
  "lightturquoise" to "#AFE4DE",
  "lightviolet" to "#7A5299",
  "lightyellow" to "#ffffe0",
  "lime" to "#00ff00",
  "limegreen" to "#32cd32",
  "linen" to "#faf0e6",
  "magenta" to "#ff00ff",
  "maroon" to "#800000",
  "mediumaquamarine" to "#66cdaa",
  "mediumblue" to "#0000cd",
  "mediumorchid" to "#ba55d3",
  "mediumpurple" to "#9370db",
  "mediumseagreen" to "#3cb371",
  "mediumslateblue" to "#7b68ee",
  "mediumspringgreen" to "#00fa9a",
  "mediumturquoise" to "#48d1cc",
  "mediumvioletred" to "#c71585",
  "midnightblue" to "#191970",
  "midnightblack" to "#2c3e50",
  "mintcream" to "#f5fffa",
  "mistyrose" to "#ffe4e1",
  "moccasin" to "#ffe4b5",
  "mustard" to "#FFDB58",
  "nephritis" to "#27ae60",
  "navajowhite" to "#ffdead",
  "navy" to "#000080",
  "oldlace" to "#fdf5e6",
  "olive" to "#808000",
  "olivedrab" to "#6b8e23",
  "orange" to "#ffa500",
  "orangered" to "#ff4500",
  "orchid" to "#da70d6",
  "palegoldenrod" to "#eee8aa",
  "palegreen" to "#98fb98",
  "paleturquoise" to "#afeeee",
  "palevioletred" to "#db7093",
  "papayawhip" to "#ffefd5",
  "peachpuff" to "#ffdab9",
  "peterriver" to "#3498db",
  "peru" to "#cd853f",
  "pink" to "#ffc0cb",
  "plum" to "#dda0dd",
  "pomegranate" to "#c0392b",
  "powderblue" to "#b0e0e6",
  "purple" to "#800080",
  "pumpkin" to "#d35400",
  "rebeccapurple" to "#663399",
  "red" to "#ff0000",
  "rosybrown" to "#bc8f8f",
  "royalblue" to "#4169e1",
  "saddlebrown" to "#8b4513",
  "salmon" to "#fa8072",
  "sandybrown" to "#f4a460",
  "seagreen" to "#2e8b57",
  "seashell" to "#fff5ee",
  "sienna" to "#a0522d",
  "quiksilver" to "#bdc3c7",
  "silver" to "#c0c0c0",
  "skyblue" to "#87ceeb",
  "slateblue" to "#6a5acd",
  "slategray" to "#708090",
  "slategrey" to "#708090",
  "snow" to "#fffafa",
  "springgreen" to "#00ff7f",
  "steelblue" to "#4682b4",
  "sunflower" to "#f1c40f",
  "tan" to "#d2b48c",
  "teal" to "#008080",
  "thistle" to "#d8bfd8",
  "tomato" to "#ff6347",
  "turquoise" to "#40e0d0",
  "violet" to "#ee82ee",
  "wetasphalt" to "#34495e",
  "wisteria" to "#8e44ad",
  "wheat" to "#f5deb3",
  "white" to "#ffffff",
  "whitesmoke" to "#f5f5f5",
  "yellow" to "#ffff00",
  "yellowgreen" to "#9acd32",
  "beekeeper" to "#f6e58d",
  "spiced nectarine" to "#ffbe76",
  "pink glamour" to "#ff7979",
  "june bud" to "#badc58",
  "coastal breeze" to "#dff9fb",
  "turbo" to "#f9ca24",
  "quince jelly" to "#f0932b",
  "carmine pink" to "#eb4d4b",
  "pure apple" to "#6ab04c",
  "hint of ice pack" to "#c7ecee",
  "middle blue" to "#7ed6df",
  "heliotrope" to "#e056fd",
  "exodus fruit" to "#686de0",
  "deep koamaru" to "#30336b",
  "soaring eagle" to "#95afc0",
  "greenland green" to "#22a6b3",
  "steel pink" to "#be2edd",
  "blurple" to "#4834d4",
  "deep cove" to "#130f40",
  "wizard grey" to "#535c68",
  "highlighter pink" to "#ef5777",
  "dark periwinkle" to "#575fcf",
  "megaman" to "#4bcffa",
  "fresh turquoise" to "#34e7e4",
  "minty green" to "#0be881",
  "sizzling red" to "#f53b57",
  "free speech blue" to "#3c40c6",
  "spiro disco ball" to "#0fbcf9",
  "jade dust" to "#00d8d6",
  "green teal" to "#05c46b",
  "narenji orange" to "#ffc048",
  "yriel yellow" to "#ffdd59",
  "sunset orange" to "#ff5e57",
  "hint of elusive blue" to "#d2dae2",
  "good night" to "#485460",
  "chrome yellow" to "#ffa801",
  "vibrant yellow" to "#ffd32a",
  "red orange" to "#ff3f34",
  "london square" to "#808e9b",
  "black pearl" to "#1e272e",
  "flat flesh" to "#fad390",
  "melon melody" to "#f8c291",
  "livid" to "#6a89cc",
  "spray" to "#82ccdd",
  "paradise green" to "#b8e994",
  "squash blossom" to "#f6b93b",
  "mandarin red" to "#e55039",
  "azraq blue" to "#4a69bd",
  "dupain" to "#60a3bc",
  "aurora green" to "#78e08f",
  "iceland poppy" to "#fa983a",
  "tomato red" to "#eb2f06",
  "good samaritan" to "#3c6382",
  "waterfall" to "#38ada9",
  "carrot orange" to "#e58e26",
  "jalapeno red" to "#b71540",
  "dark sapphire" to "#0c2461",
  "forest blues" to "#0a3d62",
  "reef encounter" to "#079992",
  "jigglypuff" to "#ff9ff3",
  "casandora yellow" to "#feca57",
  "pastel red" to "#ff6b6b",
  "megaman" to "#48dbfb",
  "wild caribbean green" to "#1dd1a1",
  "lotus pink" to "#f368e0",
  "double dragon skin" to "#ff9f43",
  "amour" to "#ee5253",
  "cyanite" to "#0abde3",
  "dark mountain meadow" to "#10ac84",
  "joust blue" to "#54a0ff",
  "nasu purple" to "#5f27cd",
  "light blue ballerina" to "#c8d6e5",
  "fuel town" to "#576574",
  "aqua velvet" to "#01a3a4",
  "bleu de france" to "#2e86de",
  "bluebell" to "#341f97",
  "storm petrel" to "#8395a7",
  "imperial primer" to "#222f3e",
  "orchid orange" to "#fea47f",
  "spiro disco ball" to "#25ccf7",
  "honey glow" to "#eab543",
  "sweet garden" to "#55e6c1",
  "falling star" to "#cad3c8",
  "rich gardenia" to "#f97f51",
  "clear chill" to "#1b9cfc",
  "sarawak white pepper" to "#f8efba",
  "keppel" to "#58b19f",
  "ships officer" to "#2c3a47",
  "fiery fuchsia" to "#b33771",
  "bluebell" to "#3b3b98",
  "georgia peach" to "#fd7272",
  "oasis stream" to "#9aecdb",
  "bright ube" to "#d6a2e8",
  "magenta purple" to "#6d214f",
  "ending navy blue" to "#182c61",
  "sasquatch socks" to "#fc427b",
  "pine glade" to "#bdc581",
  "highlighter lavender" to "#82589f",
  "bright lilac" to "#cd84f1",
  "pretty please" to "#ffcccc",
  "light red" to "#ff4d4d",
  "mandarin sorbet" to "#ffaf40",
  "unmellow yellow" to "#fffa65",
  "light purple" to "#c56cf0",
  "young salmon" to "#ffb8b8",
  "red orange" to "#ff3838",
  "radiant yellow" to "#ff9f1a",
  "dorn yellow" to "#fff200",
  "wintergreen" to "#32ff7e",
  "electric blue" to "#7efff5",
  "neon blue" to "#18dcff",
  "light slate blue" to "#7d5fff",
  "shadowed steel" to "#4b4b4b",
  "weird green" to "#3ae374",
  "hammam blue" to "#67e6dc",
  "light indigo" to "#7158e2",
  "baltic sea" to "#3d3d3d",
  "sunflower" to "#ffc312",
  "energos" to "#c4e538",
  "blue martina" to "#12cbc4",
  "lavender rose" to "#fda7df",
  "bara red" to "#ed4c67",
  "radiant yellow" to "#f79f1f",
  "android green" to "#a3cb38",
  "mediterranean sea" to "#1289a7",
  "lavender tea" to "#d980fa",
  "very berry" to "#b53471",
  "puffins bill" to "#ee5a24",
  "pixelated grass" to "#009432",
  "merchant marine blue" to "#0652dd",
  "forgotten purple" to "#9980fa",
  "hollyhock" to "#833471",
  "red pigment" to "#ea2027",
  "turkish aqua" to "#006266",
  "under the sea" to "#1b1464",
  "circumorbital ring" to "#5758bb",
  "magenta purple" to "#6f1e51",
  "protoss pylon" to "#00a8ff",
  "periwinkle" to "#9c88ff",
)

val colorSet: MutableSet<String> = colorMap.keys

/**
 * Retrieves the Color instance based on the specified color name and modifiers.
 *
 * @param color the color name with optional modifiers. Format: "colorName[+/-tones]". Examples: "red", "blue+2", "green-1".
 * @return the Color instance corresponding to the provided color name and modifiers, or null if not found.
 */
fun getColorInstance(color: String): Color? {
  var colorName = color.lowercase()
  var modifier = CharArray(0)

  val lighterIndex = color.indexOf("-")
  if (lighterIndex > 0) {
    colorName = color.take(lighterIndex)
    modifier = color.substring(lighterIndex).toCharArray()
  }

  val darkerIndex = color.indexOf("+")
  if (darkerIndex > 0) {
    colorName = color.take(darkerIndex)
    modifier = color.substring(darkerIndex).toCharArray()
  }

  var myColor = colorMap[colorName]?.toColor()
  var number = ""

  modifier.indices.reversed().forEach { i ->
    val c = modifier[i]
    when {
      Character.isDigit(c) -> number += c
      c == '+'             -> {
        var tones = 1
        if (number.isNotEmpty()) {
          tones = number.toInt()
          number = ""
        }
        myColor = ColorUtil.brighter(myColor ?: return@forEach, tones)
      }

      c == '-'             -> {
        var tones = 1
        if (number.isNotEmpty()) {
          tones = number.toInt()
          number = ""
        }
        myColor = ColorUtil.darker(myColor ?: return@forEach, tones)
      }
    }
  }
  return myColor
}

/**
 * Creates an icon with the specified color and size for use in the gutter.
 *
 * @param color The color to be used for the icon.
 * @param size The size of the icon, default is 12.
 * @return An Icon instance with the specified color and size.
 */
@Suppress("detekt:MagicNumber") // NON-NLS
fun gutterColorIcon(color: Color, size: Int = 12): Icon {
  val borderColor = JBColor(Color.black, Color.white)
  return ColorIcon(
    size,
    size,
    size,
    size,
    color,
    borderColor,
    24
  )
}

/** Converts a Color object to its hexadecimal string representation. */
fun Color.toHex(): String = ColorUtil.toHex(this)

/** Converts a hexadecimal string to a Color object. */
fun String.fromHex(): Color = ColorUtil.fromHex(this)

/** Converts the String to a Color object if the String matches the hex color pattern. */
fun String.toColor(): Color? = when {
  !LanguagePatternHolder.hexColorPattern.toRegex().matches(this) -> null
  else                                                           -> ColorUtil.fromHex(this)
}

@Suppress("UseJBColor")
fun getContrastedText(color: Color): Color = when {
  ColorUtil.isDark(color) -> Color.white
  else                    -> Color.black
}

fun isDarkTheme(): Boolean {
  val lookAndFeelDefaults = UIManager.getLookAndFeelDefaults()
  return lookAndFeelDefaults == null || lookAndFeelDefaults.getBoolean("ui.theme.is.dark")
}

fun dimmer(color: Color, factor: Int): Color = (0 until factor).fold(color) { acc, _ -> ColorUtil.dimmer(acc) }

fun softer(color: Color, factor: Int): Color = (0 until factor).fold(color) { acc, _ -> ColorUtil.softer(acc) }

@Suppress("detekt:MagicNumber") // NON-NLS
fun generateColor(string: String? = null): Color {
  val name = string ?: randomString(10)
  val tones = 6
  val color = Color(stringToARGB(name))
  val lightColor = ColorUtil.brighter(color, tones / 2)
  val isDark = ColorUtil.isDark(color)

  return when {
    isDarkTheme()            -> ColorUtil.darker(color, tones)
    !isDarkTheme() && isDark -> dimmer(lightColor, tones / 2)
    else                     -> softer(color, tones / 2)
  }
}

/** Converts a string to a color. */
@Suppress("detekt:MagicNumber") // NON-NLS
fun stringToARGB(charSequence: CharSequence): Int {
  var hash = 0
  val length = charSequence.length
  @Suppress("ReplaceManualRangeWithIndicesCalls")
  for (i in 0 until length) {
    hash = charSequence[i].code + ((hash shl 5) - hash)
  }
  return hash
}

fun randomString(len: Int): String {
  val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
  return (1..len)
    .map { charPool.random() }
    .joinToString("")
}