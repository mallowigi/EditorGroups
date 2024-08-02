package krasa.editorGroups.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.PopupHandler;
import com.intellij.util.BitUtil;
import krasa.editorGroups.EditorGroupPanel;
import krasa.editorGroups.Splitters;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static krasa.editorGroups.actions.PopupMenu.popupInvoked;

public class PreviousAction extends EditorGroupsAction implements CustomComponentAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    EditorGroupPanel panel = getEditorGroupPanel(anActionEvent);
    if (panel != null) {
      InputEvent e = anActionEvent.getInputEvent();

      assert e != null;
      boolean newTab = BitUtil.isSet(e.getModifiersEx(), InputEvent.CTRL_DOWN_MASK) && (e instanceof MouseEvent) && ((MouseEvent) e).getClickCount() > 0;

      panel.previous(newTab, BitUtil.isSet(e.getModifiersEx(), InputEvent.SHIFT_DOWN_MASK), Splitters.from(e));
    }
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation) {
    ActionButton button = new ActionButton(this, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    presentation.setIcon(AllIcons.Actions.Back);
    button.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });
    return button;
  }
}
