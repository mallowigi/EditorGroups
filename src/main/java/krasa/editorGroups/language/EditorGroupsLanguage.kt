package krasa.editorGroups.language

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile

object EditorGroupsLanguage : Language("EditorGroups", "text/egroups") {
  @Suppress("unused") // NON-NLS
  private fun readResolve(): Any = EditorGroupsLanguage

  @JvmStatic
  fun isEditorGroupsLanguage(file: VirtualFile?): Boolean = when (file) {
    null -> false
    else -> getFileTypeLanguage(file.fileType) === this
  }

  @JvmStatic
  fun isEditorGroupsLanguage(ownerPath: String): Boolean =
    getFileTypeLanguage(FileTypeManager.getInstance().getFileTypeByFileName(ownerPath)) === this

  private fun getFileTypeLanguage(fileType: FileType?): Language? = when (fileType) {
    is LanguageFileType -> fileType.language
    else                -> null
  }
}
