package krasa.editorGroups.language

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.icons.EditorGroupsIcons
import java.nio.charset.StandardCharsets
import javax.swing.Icon

object EditorGroupsFileType : LanguageFileType(EditorGroupsLanguage) {
  override fun getName(): String = "EditorGroups"

  override fun getDescription(): String = "EditorGroups config file"

  override fun getDefaultExtension(): String = "egroups"

  override fun getIcon(): Icon = EditorGroupsIcons.logo

  override fun getCharset(virtualFile: VirtualFile, bytes: ByteArray): String? = StandardCharsets.UTF_8.name()
}
