package krasa.editorGroups.language

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PlainTextTokenTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore

internal class EditorGroupsParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = EmptyLexer()

  override fun createParser(project: Project?): PsiParser {
    throw UnsupportedOperationException("Not supported")
  }

  override fun getFileNodeType(): IFileElementType = object : IFileElementType(EditorGroupsLanguage) {
    override fun parseContents(chameleon: ASTNode): ASTNode {
      val chars = chameleon.chars
      return ASTFactory.leaf(PlainTextTokenTypes.PLAIN_TEXT, chars)
    }
  }

  override fun getWhitespaceTokens(): TokenSet = TokenSet.EMPTY

  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode?): PsiElement = PsiUtilCore.NULL_PSI_ELEMENT

  override fun createFile(viewProvider: FileViewProvider): PsiFile = EditorGroupsPsiFile(viewProvider)
}
