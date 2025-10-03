package krasa.editorGroups.settings

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class EditorGroupSetting(val categories: Array<Category>) {
  enum class Category {
    GROUPS,
    EGROUPS,
    PERFORMANCE,
    TABS,
    REGEX,
    UI
  }
}