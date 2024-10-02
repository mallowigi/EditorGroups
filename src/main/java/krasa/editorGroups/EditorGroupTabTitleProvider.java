package krasa.editorGroups;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EDT;
import krasa.editorGroups.model.AutoGroup;
import krasa.editorGroups.model.EditorGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorGroupTabTitleProvider implements EditorTabTitleProvider {

  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    String presentableNameForUI = getPresentableNameForUI(project, virtualFile, null, false);

    if (!EDT.isCurrentThreadEdt()) {
      return null;
    }
    FileEditor textEditor = FileEditorManagerImpl.getInstanceEx(project).getSelectedEditor(virtualFile);

    return getTitle(project, textEditor, presentableNameForUI);
  }

  @NotNull
  public static String getPresentableNameForUI(@NotNull Project project, @NotNull VirtualFile file, EditorWindow editorWindow, boolean newAPI) {
    List<EditorTabTitleProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(
      EditorTabTitleProvider.EP_NAME.getExtensionList());
    for (EditorTabTitleProvider provider : providers) {
      if (provider instanceof EditorGroupTabTitleProvider) {
        continue;
      }
      String result;
      result = provider.getEditorTabTitle(project, file);
      if (result != null) {
        return result;
      }
    }

    return file.getPresentableName();
  }

  private String getTitle(Project project, FileEditor textEditor, String presentableNameForUI) {
    EditorGroup group = null;
    if (textEditor != null) {
      group = textEditor.getUserData(EditorGroupPanel.EDITOR_GROUP);
    }

    if (group != null && group.isValid() && !(group instanceof AutoGroup)) {
      presentableNameForUI = group.getPresentableTitle(project, presentableNameForUI, EditorGroupsSettingsState.state().isShowSize());
    }
    return presentableNameForUI;
  }

}
