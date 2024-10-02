package krasa.editorGroups.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.ui.PopupHandler;
import krasa.editorGroups.EditorGroupManager;
import krasa.editorGroups.EditorGroupPanel;
import krasa.editorGroups.icons.EditorGroupsIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class RefreshAction extends EditorGroupsAction implements CustomComponentAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    Document doc = getDocument(anActionEvent);
    if (doc != null) {
      FileDocumentManager.getInstance().saveDocument(doc);
    }

    EditorGroupPanel panel = getEditorGroupPanel(anActionEvent);
    if (panel != null) {
      panel._refresh(true, null);
    }


    EditorGroupManager editorGroupManager = EditorGroupManager.getInstance(Objects.requireNonNull(anActionEvent.getProject()));
    editorGroupManager.resetSwitching();
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation) {
    ActionButton refresh = new ActionButton(this, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    refresh.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        PopupMenu.popupInvoked(comp, x, y);
      }
    });
    presentation.setIcon(EditorGroupsIcons.refresh);

    return refresh;
  }

  private static Document getDocument(AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return editor != null ? editor.getDocument() : null;
  }
}
