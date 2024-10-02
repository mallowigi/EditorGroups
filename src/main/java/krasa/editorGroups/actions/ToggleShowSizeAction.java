package krasa.editorGroups.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import krasa.editorGroups.EditorGroupsSettingsState;
import org.jetbrains.annotations.NotNull;

public class ToggleShowSizeAction extends ToggleAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return EditorGroupsSettingsState.state().isShowSize();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    EditorGroupsSettingsState.state().setShowSize(state);
  }
}
