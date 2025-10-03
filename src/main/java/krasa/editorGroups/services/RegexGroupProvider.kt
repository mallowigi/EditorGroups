package krasa.editorGroups.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.RegexGroup
import krasa.editorGroups.model.RegexGroupModel
import krasa.editorGroups.settings.regex.RegexGroupsSettings
import krasa.editorGroups.support.RegexFileResolver

@Service(Service.Level.PROJECT)
class RegexGroupProvider {
  fun findFirstMatchingRegexGroup(file: VirtualFile): EditorGroup {
    thisLogger().debug("<findFirstMatchingRegexGroup: $file") // NON-NLS

    val start = System.currentTimeMillis()
    val fileName = file.name
    val result = when (val matching = RegexGroupsSettings.instance.regexGroupModels.findFirstMatching(fileName)) {
      null -> EditorGroup.EMPTY
      else -> RegexGroup(matching, file.parent, fileName)
    }

    thisLogger().debug("<findFirstMatchingRegexGroup: result=$result in ${System.currentTimeMillis() - start}ms") // NON-NLS

    return result
  }

  fun findMatchingRegexGroups(file: VirtualFile): List<RegexGroup> {
    thisLogger().debug("findMatchingRegexGroups: $file") // NON-NLS

    val start = System.currentTimeMillis()
    val fileName = file.name
    val matching: List<RegexGroupModel> = RegexGroupsSettings.instance.regexGroupModels.findMatching(fileName)

    thisLogger().debug("findMatchingRegexGroups: ${System.currentTimeMillis() - start}ms") // NON-NLS

    return toRegexGroup(file, fileName, matching)
  }

  fun findProjectRegexGroups(): List<RegexGroup> = RegexGroupsSettings.instance.regexGroupModels.findProjectRegexGroups()
    .map {
      RegexGroup(
        regexGroupModel = it,
        folder = null,
        links = emptyList(),
        fileName = null
      )
    }

  fun toRegexGroup(file: VirtualFile, fileName: String?, matching: List<RegexGroupModel>): List<RegexGroup> = matching.map {
    RegexGroup(
      regexGroupModel = it,
      folder = file.parent,
      links = emptyList(),
      fileName = fileName
    )
  }

  fun getRegexGroup(group: RegexGroup, project: Project, currentFile: VirtualFile?): RegexGroup {
    val links = RegexFileResolver(project).resolveRegexGroupLinks(group, currentFile)

    if (currentFile != null && links.isEmpty()) {
      thisLogger().error("should contain the current file at least: $group") // NON-NLS
    }

    return RegexGroup(
      regexGroupModel = group.regexGroupModel,
      folder = group.folder,
      links = links,
      fileName = group.fileName
    )
  }

  fun findRegexGroup(file: VirtualFile, substring: String): EditorGroup {
    val regexGroupModels = RegexGroupsSettings.instance.regexGroupModels
    val regexGroupModel = regexGroupModels.find(substring) ?: return EditorGroup.EMPTY

    return RegexGroup(
      regexGroupModel = regexGroupModel,
      folder = file.parent,
      links = emptyList(),
      fileName = file.name
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RegexGroupProvider = project.getService(RegexGroupProvider::class.java)
  }
}
