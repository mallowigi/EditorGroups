package krasa.editorGroups.tabs2;

import krasa.editorGroups.tabs2.label.TabUiDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface KrTabsPresentation {

  KrTabsPresentation setPaintFocus(boolean paintFocus);

  KrTabsPresentation setUiDecorator(@Nullable TabUiDecorator decorator);

  KrTabsPresentation setRequestFocusOnLastFocusedComponent(boolean request);

  void setPaintBlocked(boolean blocked, boolean takeSnapshot);

  KrTabsPresentation setInnerInsets(Insets innerInsets);

  KrTabsPresentation setFocusCycle(boolean root);

  @NotNull
  KrTabsPresentation setToDrawBorderIfTabsHidden(boolean draw);

  @NotNull
  EditorGroupsTabsBase getJBTabs();

  @NotNull
  KrTabsPresentation setActiveTabFillIn(@Nullable Color color);

  EditorGroupsTabsPosition getTabsPosition();

  @NotNull
  KrTabsPresentation setTabsPosition(EditorGroupsTabsPosition position);

  KrTabsPresentation setFirstTabOffset(int offset);

}
