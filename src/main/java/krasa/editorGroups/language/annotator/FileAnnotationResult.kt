package krasa.editorGroups.language.annotator

import com.intellij.lang.annotation.AnnotationHolder

class FileAnnotationResult {
  private val sourceAnnotationResults: MutableList<SourceAnnotationResult> = mutableListOf()

  fun add(sourceAnnotationResult: SourceAnnotationResult): Boolean = sourceAnnotationResults.add(sourceAnnotationResult)

  fun annotate(holder: AnnotationHolder) {
    sourceAnnotationResults.forEach { sourceAnnotationResult -> sourceAnnotationResult.annotate(holder) }
  }
}