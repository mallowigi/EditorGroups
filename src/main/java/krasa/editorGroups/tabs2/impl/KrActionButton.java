// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.InplaceButton;
import com.intellij.util.ui.TimedDeadzone;
import krasa.editorGroups.tabs2.EditorGroupTabInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.*;
import java.util.Objects;
import java.util.function.Consumer;

class KrActionButton implements ActionListener {
  private static final Logger LOG = Logger.getInstance(KrActionButton.class);

  private final IconButton myIconButton;
  private final InplaceButton myInplaceButton;
  private Presentation myPrevPresentation;
  private final AnAction myAction;
  private final String myPlace;
  private final EditorGroupTabInfo myTabInfo;
  private boolean myAutoHide;
  private boolean myToShow;

  KrActionButton(@NotNull EditorGroupTabInfo tabInfo,
                 @NotNull AnAction action,
                 String place,
                 Consumer<? super MouseEvent> pass,
                 Consumer<? super Boolean> hover,
                 TimedDeadzone.Length deadzone) {
    if (action.getActionUpdateThread() == ActionUpdateThread.BGT) {
      String name = action.getClass().getName();
      LOG.error(PluginException.createByClass(
        action.getActionUpdateThread() + " action " + StringUtil.getShortName(name) + " (" + name + ") is not allowed. " +
          "Only EDT actions are allowed.", null, action.getClass()));
    }
    myIconButton = new IconButton(null, action.getTemplatePresentation().getIcon());
    myTabInfo = tabInfo;
    myAction = action;
    myPlace = place;

    MouseListener myListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        hover.accept(true);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hover.accept(false);
      }
    };

    myInplaceButton = new InplaceButton(myIconButton, this, pass, deadzone) {
      @Override
      protected void doRepaintComponent(Component c) {
        repaintComponent(c);
      }

      @Override
      public void addNotify() {
        super.addNotify();
        myInplaceButton.addMouseListener(myListener);
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        myInplaceButton.removeMouseListener(myListener);
      }
    };
    myInplaceButton.setVisible(false);
    myInplaceButton.setFillBg(false);
  }

  public InplaceButton getComponent() {
    return myInplaceButton;
  }

  public Presentation getPrevPresentation() {
    return myPrevPresentation;
  }

  protected void repaintComponent(Component c) {
    c.repaint();
  }

  public void setMouseDeadZone(TimedDeadzone.Length deadZone) {
    myInplaceButton.setMouseDeadzone(deadZone);
  }

  public boolean update() {
    AnActionEvent event = createAnEvent(null, 0);

    myAction.update(event);
    Presentation p = event.getPresentation();
    boolean changed = !areEqual(p, myPrevPresentation);

    myIconButton.setIcons(p.getIcon(), p.getDisabledIcon(), p.getHoveredIcon());

    if (changed) {
      myInplaceButton.setIcons(myIconButton);
      String tooltipText = KeymapUtil.createTooltipText(p.getText(), myAction);
      myInplaceButton.setToolTipText(!tooltipText.isEmpty() ? tooltipText : null);
      myInplaceButton.setVisible(p.isEnabled() && p.isVisible());
    }

    myPrevPresentation = p;

    return changed;
  }

  private static boolean areEqual(Presentation p1, Presentation p2) {
    if (p1 == null || p2 == null) return false;

    return Objects.equals(p1.getText(), p2.getText())
      && Comparing.equal(p1.getIcon(), p2.getIcon())
      && Comparing.equal(p1.getHoveredIcon(), p2.getHoveredIcon())
      && p1.isEnabled() == p2.isEnabled()
      && p1.isVisible() == p2.isVisible();
  }

  @Override
  public void actionPerformed(final ActionEvent e) {
    AnActionEvent event = createAnEvent(e);
    if (ActionUtil.lastUpdateAndCheckDumb(myAction, event, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(myAction, event);
    }
  }

  private @NotNull AnActionEvent createAnEvent(final @NotNull ActionEvent e) {
    Object source = e.getSource();
    InputEvent inputEvent = null;
    if (source instanceof InputEvent) {
      inputEvent = (InputEvent) source;
    }
    return createAnEvent(inputEvent, e.getModifiers());
  }

  private @NotNull AnActionEvent createAnEvent(InputEvent inputEvent, int modifiers) {
    Presentation presentation = myAction.getTemplatePresentation().clone();
    DataContext context = DataManager.getInstance().getDataContext(myInplaceButton);
    DataContext compound = dataId -> {
      return context.getData(dataId);
    };
    return new AnActionEvent(inputEvent, compound, myPlace != null ? myPlace : ActionPlaces.UNKNOWN, presentation,
      ActionManager.getInstance(), modifiers);
  }

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    if (!myToShow) {
      toggleShowActions(false);
    }
  }

  public void toggleShowActions(boolean show) {
    if (myAutoHide) {
      myInplaceButton.setPainting(show);
    } else {
      myInplaceButton.setPainting(true);
    }

    myToShow = show;
  }
}
