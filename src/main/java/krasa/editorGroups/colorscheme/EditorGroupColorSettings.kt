package krasa.editorGroups.colorscheme

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.NlsContexts
import krasa.editorGroups.messages.EditorGroupsBundle.message
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@Suppress("CompanionObjectInExtension")
internal class EditorGroupColorSettings : ColorSettingsPage {
  override fun getIcon(): Icon? = null

  override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

  override fun getDemoText(): @NonNls String = """
    <comment># This is a comment</comment>
    <metadata>@group.id</metadata> Group ID
    <keyword>@group.title</keyword> Group Title
    <keyword>@group.root</keyword> <path>*/</path>
    <keyword>@group.color</keyword> <color>dodgerBlue</color>
    <keyword>@group.fgcolor</keyword> <color>silver</color>
    <keyword>@group.related</keyword> <macro>PROJECT/</macro><path>*.svg</path>
    <keyword>@group.disable</keyword> <constant>true</constant>
  """.trimIndent()

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = DESCRIPTORS

  override fun getAttributeDescriptors(): Array<out AttributesDescriptor?> = ATTRIBUTES

  override fun getColorDescriptors(): Array<out ColorDescriptor?> = ColorDescriptor.EMPTY_ARRAY

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = message("krasa.editorGroups.EditorGroupsSettings")

  companion object {
    @NonNls
    private val DESCRIPTORS = mapOf(
      "comment" to EG_COMMENT,
      "keyword" to EG_KEYWORD,
      "metadata" to EG_METADATA,
      "path" to EG_PATH,
      "macro" to EG_MACRO,
      "color" to EG_COLOR,
      "constant" to EG_CONSTANT,
    )

    private val ATTRIBUTES: Array<AttributesDescriptor> = arrayOf(
      AttributesDescriptor(message("attribute.descriptor.comments"), EG_COMMENT),
      AttributesDescriptor(message("attribute.descriptor.groups.id"), EG_METADATA),
      AttributesDescriptor(message("attribute.descriptor.groups.property"), EG_KEYWORD),
      AttributesDescriptor(message("attribute.descriptor.values.path"), EG_PATH),
      AttributesDescriptor(message("attribute.descriptor.values.macro"), EG_MACRO),
      AttributesDescriptor(message("attribute.descriptor.values.color"), EG_COLOR),
      AttributesDescriptor(message("attribute.descriptor.values.constant"), EG_CONSTANT),
    )
  }
}