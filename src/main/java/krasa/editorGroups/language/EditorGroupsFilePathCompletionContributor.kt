package krasa.editorGroups.language

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.icons.AllIcons
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelperRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileSystemItemUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GLOBAL_MACRO
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_RELATED
import krasa.editorGroups.language.annotator.LanguagePatternHolder.GROUP_ROOT
import krasa.editorGroups.language.annotator.LanguagePatternHolder.MODULE_MACRO
import krasa.editorGroups.language.annotator.LanguagePatternHolder.PROJECT_MACRO
import kotlin.math.max
import kotlin.math.min

internal class EditorGroupsFilePathCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      object : CompletionProvider<CompletionParameters>() {
        @Suppress("detekt:NestedBlockDepth")
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
          val position = parameters.position
          val project = position.project

          // First, retrieve the file and its context
          val originalFile = parameters.originalFile
          val contextFile = originalFile.virtualFile
          if (contextFile == null) return

          val index = ProjectRootManager.getInstance(project).fileIndex
          val contextModule = index.getModuleForFile(contextFile)
          if (contextModule == null) return

          // Next, retrieve the current position info
          val text = parameters.originalFile.text
          val offset = min(text.length, parameters.offset)

          val lineStart = text.lastIndexOf('\n', offset - 1) + 1
          val line = text.substring(lineStart, offset)

          // Check if the current line is a path related
          val (keywordEndIndex, root) = getKeywordEndIndex(line)
          if (keywordEndIndex < 0) return

          // Get the prefix (e.g. the typed characters before invoking autocomplete)
          var typedPrefix = text.substring(max(lineStart + keywordEndIndex, lineStart), offset).trim { it <= ' ' }

          // Get macro details
          var (macro, prefixWithoutMacro) = extractMacro(prefix = typedPrefix, isRoot = root)

          // Extract the path parts from the prefix
          var (parts, filePrefix) = extractPrefixParts(prefixWithoutMacro)

          // Filter the file names by prefix
          val fileNames: MutableSet<String> = getFilteredFileNames(
            project = project,
            filePrefix = filePrefix,
            parameters = parameters
          )

          // Next, we prepare our helpers, scope and module for the result
          val resultWithPrefixMatcher: CompletionResultSet = result.withPrefixMatcher(filePrefix).caseInsensitive()
          val helpers = FileReferenceHelperRegistrar.getHelpers<PsiFile?>(originalFile)
          val scope = ProjectScope.getProjectScope(project)
          val moduleForFile = ProjectRootManager.getInstance(project)
            .fileIndex
            .getModuleForFile(parameters.originalFile.virtualFile)

          for (fileName in fileNames) {
            ProgressManager.checkCanceled()

            // List all virtual files related to that name
            val files = FilenameIndex.getVirtualFilesByName(fileName, scope)
            for (virtualFile in files) {
              ProgressManager.checkCanceled()
              if (virtualFile == null || !virtualFile.isValid || Comparing.equal(virtualFile, contextFile)) continue

              // Build the list of helpers
              val helperList: MutableList<FileReferenceHelper> = ArrayList<FileReferenceHelper>()
              for (contextHelper in helpers) {
                ProgressManager.checkCanceled()

                // If the helper can be applied to the file
                if (!contextHelper.isMine(project, virtualFile)) continue

                val psiFile = contextHelper.getPsiFileSystemItem(project, virtualFile)

                // If the file matches the path prefix
                if (parts.isEmpty() || fileMatchesPathPrefix(
                    file = psiFile,
                    parts = parts
                  )
                ) {
                  helperList.add(contextHelper)
                }
              }

              if (helperList.isEmpty()) continue

              resultWithPrefixMatcher.addElement(
                FilePathLookupItem(
                  project = project,
                  originalFile = parameters.originalFile,
                  file = virtualFile,
                  macro = macro,
                  moduleForFile = moduleForFile!!,
                  helpers = helperList
                )
              )
            }
          }
        }

        private fun fileMatchesPathPrefix(file: PsiFileSystemItem?, parts: List<String>): Boolean {
          if (file == null) return false

          val contextParts = mutableListOf<String>()
          var parentFile: PsiFileSystemItem? = file

          while (parentFile?.parent.also { parentFile = it } != null) {
            parentFile?.name
              ?.takeIf { it.isNotEmpty() }
              ?.let { contextParts.add(0, it.lowercase()) }
          }

          val path = StringUtil.join(contextParts, "/")

          var nextIndex = 0
          for (s in parts) {
            if (s == "..") continue

            if (path.indexOf(s.lowercase(), nextIndex).also { nextIndex = it } == -1) return false
          }

          return true
        }

        /** Filter the file names by prefix. */
        private fun getFilteredFileNames(project: Project, filePrefix: String, parameters: CompletionParameters): MutableSet<String> {
          // Get a list of file names
          val fileNames = getAllFileNames(project)

          return fileNames
            .filter {
              filenameMatchesPrefixOrType(
                fileName = it,
                prefix = filePrefix,
                invocationCount = parameters.invocationCount
              )
            }
            .toSortedSet()
        }

        @Suppress("detekt:NestedBlockDepth")
        private fun filenameMatchesPrefixOrType(fileName: String, prefix: String, invocationCount: Int): Boolean {
          val prefixMatched = prefix.isEmpty() || StringUtil.startsWithIgnoreCase(fileName, prefix)
          if (prefixMatched && (FileType.EMPTY_ARRAY.size == 0 || invocationCount > 2)) return true

          if (prefixMatched) {
            val extension = FileUtilRt.getExtension(fileName)
            if (extension.isEmpty()) return false

            for (fileType in FileType.EMPTY_ARRAY) {
              for (matcher in FileTypeManager.getInstance().getAssociations(fileType)) {
                if (matcher.acceptsCharSequence(fileName)) return true
              }
            }
          }

          return false
        }

        /** Get a list of files from the files contributor. */
        private fun getAllFileNames(project: Project): Array<String> {
          val names = ChooseByNameContributor.FILE_EP_NAME.extensions
            .mapNotNull { contributor ->
              try {
                contributor.getNames(project, false)
              } catch (ex: ProcessCanceledException) {
                throw ex
              } catch (ex: Exception) {
                thisLogger().error(ex)
                null
              }
            }
            .flatMap { it.asIterable() }
            .toSortedSet()

          return ArrayUtil.toStringArray(names)
        }

        /** Extract the parts of a path prefix. */
        private fun extractPrefixParts(prefix: String): Pair<List<String>, String> {
          var newPrefix = prefix
          var parts: List<String> = mutableListOf()
          var lastSlashIndex: Int = newPrefix.lastIndexOf('/')

          // If not a leaf
          if (lastSlashIndex != -1) {
            val path = newPrefix.substring(0, lastSlashIndex)
            parts = path.split("/")
            newPrefix = newPrefix.substring(lastSlashIndex + 1)
          }

          return Pair(parts, newPrefix)
        }

        /** Find the end index of a path related keyword. */
        private fun getKeywordEndIndex(line: String): Pair<Int, Boolean> {
          var root = false
          var keywordEndIndex = StringUtil.indexOfSubstringEnd(line, GROUP_RELATED)

          if (keywordEndIndex < 0) {
            keywordEndIndex = StringUtil.indexOfSubstringEnd(line, GROUP_ROOT)
            root = true
          }

          return Pair(keywordEndIndex, root)
        }

        /** Parse macros and return the new prefix after removing the macro. */
        private fun extractMacro(prefix: String, isRoot: Boolean): Pair<String?, String> {
          var macro: String? = null
          lateinit var newPrefix: String

          when {
            prefix.startsWith(MODULE_MACRO)            -> {
              macro = MODULE_MACRO.replace("/", "")
              newPrefix = StringUtil.substringAfter(prefix, MODULE_MACRO) ?: prefix
            }

            prefix.startsWith(PROJECT_MACRO)           -> {
              macro = PROJECT_MACRO.replace("/", "")
              newPrefix = StringUtil.substringAfter(prefix, PROJECT_MACRO) ?: prefix
            }

            !isRoot && prefix.startsWith(GLOBAL_MACRO) -> {
              macro = GLOBAL_MACRO.replace("/", "")
              newPrefix = StringUtil.substringAfter(prefix, GLOBAL_MACRO) ?: prefix
            }

            else                                       -> {
              macro = null
              newPrefix = prefix
            }
          }
          return Pair(macro, newPrefix)
        }
      }
    )
  }

  class FilePathLookupItem(
    private val project: Project,
    private val originalFile: PsiFile?,
    private val file: VirtualFile,
    private val macro: String?,
    private val moduleForFile: Module,
    private val helpers: MutableList<FileReferenceHelper>
  ) : LookupElement() {
    private val name: String = file.name
    private val path: String = file.path
    private val info: String? = FileInfoManager.getFileAdditionalInfo(file.findPsiFile(project))

    override fun toString(): String {
      val suffix = info?.let { "($it)" } ?: ""
      return "$name $suffix"
    }

    override fun getObject(): Any = file

    override fun getLookupString(): String = name

    override fun handleInsert(context: InsertionContext) {
      val editor = context.editor

      val document = editor.document
      val startOffset = context.startOffset
      val text = document.text

      val from = text.lastIndexOf('\n', startOffset - 1) + 1
      val substring = text.substring(from, startOffset)

      var keywordEndIndex = StringUtil.indexOfSubstringEnd(substring, GROUP_RELATED) + 1
      if (keywordEndIndex <= 0) {
        keywordEndIndex = StringUtil.indexOfSubstringEnd(substring, GROUP_ROOT) + 1
      }
      if (keywordEndIndex <= 0) return

      var to = text.indexOf('\n', startOffset - 1)
      if (to < 0) to = text.length

      val relativePath = getRelativePath()
      document.replaceString(
        /* startOffset = */
        from + keywordEndIndex,
        /* endOffset = */
        to,
        /* s = */
        relativePath!!
      )

      editor.caretModel.moveToOffset(from + keywordEndIndex + relativePath.length)
      val project = context.project
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    override fun renderElement(presentation: LookupElementPresentation) {
      val relativePath = getRelativePath()

      val sb = StringBuilder()
      if (info != null) {
        sb.append(" (").append(info)
      }

      if (relativePath != null && relativePath != name) {
        when {
          info != null -> sb.append(", ")
          else         -> sb.append(" (")
        }

        sb.append(relativePath)
      }

      if (!sb.isEmpty()) {
        sb.append(')')
      }

      presentation.setItemText(name)

      if (!sb.isEmpty()) {
        presentation.setTailText(sb.toString(), true)
      }

      val psi = file.findPsiFile(project)
      val icon = if (file.isDirectory) AllIcons.Nodes.Folder else psi?.getIcon(0)
      presentation.setIcon(icon)
    }

    @Suppress("detekt:CyclomaticComplexMethod")
    private fun getRelativePath(): String? {
      val virtualFile = file

      for (helper in helpers) {
        val psiFileSystemItem = helper.getPsiFileSystemItem(project, virtualFile)
        var path: String?
        var projectBaseDir: VirtualFile = project.guessProjectDir() ?: return null

        val moduleFile = moduleForFile.guessModuleDir()

        when {
          macro == null                   -> {
            path = PsiFileSystemItemUtil.findRelativePath(originalFile, psiFileSystemItem)
            if (path == null || path.isEmpty()) {
              path = PROJECT_MACRO + VfsUtilCore.findRelativePath(projectBaseDir, virtualFile, VfsUtilCore.VFS_SEPARATOR_CHAR)
            }
          }

          GLOBAL_MACRO.startsWith(macro)  -> {
            val root = helper.findRoot(project, virtualFile)
            path = when {
              root != null -> GLOBAL_MACRO + PsiFileSystemItemUtil.findRelativePath(root, helper.getPsiFileSystemItem(project, virtualFile))

              else         -> GLOBAL_MACRO + virtualFile.name
            }
          }

          PROJECT_MACRO.startsWith(macro) ->
            path = PROJECT_MACRO + VfsUtilCore.findRelativePath(projectBaseDir, virtualFile, VfsUtilCore.VFS_SEPARATOR_CHAR)

          MODULE_MACRO.startsWith(macro)  -> path = when {
            moduleFile != null -> MODULE_MACRO + VfsUtilCore.findRelativePath(moduleFile, virtualFile, VfsUtilCore.VFS_SEPARATOR_CHAR)
            else               -> PROJECT_MACRO + VfsUtilCore.findRelativePath(
              projectBaseDir,
              virtualFile,
              VfsUtilCore.VFS_SEPARATOR_CHAR
            )
          }

          else                            -> {
            path = PsiFileSystemItemUtil.findRelativePath(originalFile, psiFileSystemItem)
            if (path == null || path.isEmpty()) {
              path = PROJECT_MACRO + VfsUtilCore.findRelativePath(projectBaseDir, virtualFile, VfsUtilCore.VFS_SEPARATOR_CHAR)
            }
          }
        }
        return path
      }

      return null
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false

      val that = other as FilePathLookupItem

      if (name != that.name) return false
      return path == that.path
    }

    override fun hashCode(): Int {
      var result = name.hashCode()
      result = 31 * result + path.hashCode()
      return result
    }
  }
}
