package krasa.editorGroups.language

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor.StringValueDescriptor
import com.intellij.util.textCompletion.TextCompletionValueDescriptor
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware
import krasa.editorGroups.language.annotator.LanguagePatternHolder
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GLOBAL_MACRO
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_COLOR
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_DISABLE
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_FG_COLOR
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_RELATED
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_ROOT
import krasa.editorGroups.language.annotator.LanguagePatternHolder.MODULE_MACRO
import krasa.editorGroups.language.annotator.LanguagePatternHolder.PROJECT_MACRO
import krasa.editorGroups.support.getColorInstance
import krasa.editorGroups.support.gutterColorIcon
import org.apache.commons.lang3.StringUtils
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.min

internal class EditorGroupsCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      object : CompletionProvider<CompletionParameters?>() {
        override fun addCompletions(
          parameters: CompletionParameters,
          context: ProcessingContext,
          result: CompletionResultSet
        ) {
          val file = parameters.originalFile
          val text = file.text

          val offset: Int = min(text.length, parameters.offset)
          val curLineStart = text.lastIndexOf('\n', offset - 1) + 1

          // The line to complete (ex: @group)
          var line = text.substring(curLineStart, offset)
          val indexOfAt = line.indexOf('@')
          var keyword = line.substring(max(indexOfAt, 0))

          val firstSpace = keyword.indexOf(' ') + 1
          // The text to complete after (ex: @group.ti)
          val prefix = line.substring(firstSpace).trim { it <= ' ' }

          // We'll populate
          val values = mutableListOf<String>()
          when {
            // If asking for colors, add all colors
            isColorLine(line)                                 -> values.addAll(LanguagePatternHolder.colors)

            isBooleanLine(line)                               -> values.addAll(LanguagePatternHolder.constants)

            // For path macros, add all macros keywords
            isPathLine(line) && !containsMacro(line)          -> {
              values.add(MODULE_MACRO)
              values.add(PROJECT_MACRO)
              values.add(GLOBAL_MACRO)
            }

            // Complete all keywords
            prefix.contains("@") || StringUtils.isBlank(line) -> {
              values.addAll(LanguagePatternHolder.keywords)
              values.addAll(LanguagePatternHolder.metadata)
            }
          }

          // Instantiate our provider with our values
          val provider = EditorGroupsCompletionProvider(
            descriptor = EditorGroupsValueDescriptor(),
            values = values,
            separators = listOf(' ')
          )
          // Try to match the current token against the prefix
          val activeResult = provider.applyPrefixMatcher(result, prefix)

          // Filter out values
          provider.fillCompletionVariants(parameters, prefix, activeResult)

          result.runRemainingContributors(parameters, true)
          result.stopHere()
        }

        /** Whether the line contains a macro. */
        private fun containsMacro(line: String): Boolean {
          val macros = listOf(MODULE_MACRO, PROJECT_MACRO, GLOBAL_MACRO)
          return macros.any { macro -> line.contains(macro) }
        }

        /** Whether the completion should ask for a path. */
        private fun isPathLine(line: String): Boolean {
          val pathKeywords = listOf(GROUP_ROOT, GROUP_RELATED)
          return pathKeywords.any { keyword -> line.contains(keyword) }
        }

        private fun isColorLine(line: String): Boolean {
          val pathKeywords = listOf(GROUP_COLOR, GROUP_FG_COLOR)
          return pathKeywords.any { keyword -> line.contains(keyword) }
        }

        private fun isBooleanLine(line: String): Boolean = line.contains(GROUP_DISABLE)
      }
    )
  }

  private class EditorGroupsCompletionProvider(
    descriptor: TextCompletionValueDescriptor<String>,
    values: MutableList<String>,
    separators: List<Char>
  ) : ValuesCompletionProviderDumbAware<String>(
    descriptor,
    separators,
    values,
    true
  ) {
    /** Installs an insert handler for the provided LookupElementBuilder with the highest priority. */
    override fun installInsertHandler(builder: LookupElementBuilder): LookupElement {
      val lookupElement = super.installInsertHandler(builder)
      return PrioritizedLookupElement.withPriority(lookupElement, Double.MAX_VALUE)
    }

    /** Returns the prefix of the text at the specified offset. */
    override fun getPrefix(text: String, offset: Int): String {
      val i = text.lastIndexOf(' ', offset - 1) + 1
      val j = text.lastIndexOf('\n', offset - 1) + 1
      return text.substring(max(i, j), offset)
    }
  }

  /** The string value descriptor for our language. */
  private class EditorGroupsValueDescriptor : StringValueDescriptor() {
    // If it's a color, return the color icon
    override fun getIcon(item: String): Icon? {
      val color = getColorInstance(item.lowercase()) ?: return null
      return gutterColorIcon(color)
    }

    /** Returns the description of the completion. */
    override fun getTypeText(item: String): String? = LanguagePatternHolder.allWithDescription[item]
  }
}
