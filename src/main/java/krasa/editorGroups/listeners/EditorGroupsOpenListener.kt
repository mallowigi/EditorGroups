package krasa.editorGroups.listeners

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.vfs.VirtualFile

class EditorGroupsOpenListener : FileOpenedSyncListener {
  override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
    EditorGroupsPanelBuilder.instance.addPanelToEditor(source, file)
  }
}