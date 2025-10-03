package krasa.editorGroups.language.annotator

import com.intellij.lang.annotation.AnnotationHolder

class SourceAnnotationResult {
  private val annotations: MutableList<SyntaxHighlightAnnotation> = mutableListOf()

  fun addAll(sourceAnnotations: MutableList<SyntaxHighlightAnnotation>) {
    annotations.addAll(sourceAnnotations)
  }

  fun annotate(holder: AnnotationHolder) {
    annotations.forEach { annotation -> annotation.annotate(holder, 0) }
  }
}