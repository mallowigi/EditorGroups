package krasa.editorGroups.language.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import krasa.editorGroups.support.gutterColorIcon
import krasa.editorGroups.support.toHex
import javax.swing.Icon

class SyntaxHighlightAnnotation(
  private val startSourceOffset: Int,
  private val endSourceOffset: Int,
  private val textAttributesKey: TextAttributesKey? = null,
  private val textAttributes: TextAttributes? = null,
  private val text: String = "",
  private val isColor: Boolean = false
) {
  fun annotate(holder: AnnotationHolder, sourceOffset: Int) {
    val fileRange = TextRange.create(
      startSourceOffset + sourceOffset,
      endSourceOffset + sourceOffset
    )

    val infoAnnotation = when {
      text.isEmpty() -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(fileRange)
      else           -> holder.newAnnotation(HighlightSeverity.INFORMATION, text).range(fileRange)
    }

    when {
      textAttributes != null -> infoAnnotation.enforcedTextAttributes(textAttributes)
      textAttributesKey != null -> infoAnnotation.textAttributes(textAttributesKey)
    }

    if (isColor && textAttributes != null) {
      val color = textAttributes.backgroundColor
      val colorIcon = gutterColorIcon(color)
      infoAnnotation.gutterIconRenderer(
        object : GutterIconRenderer() {
          override fun getIcon(): Icon = colorIcon

          override fun getTooltipText(): String = color.toHex()

          override fun equals(obj: Any?): Boolean = true

          override fun hashCode(): Int = 0
        }
      )
    }

    infoAnnotation.create()
  }
}