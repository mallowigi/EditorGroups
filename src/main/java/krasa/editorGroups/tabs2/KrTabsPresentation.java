package krasa.editorGroups.tabs2;

import krasa.editorGroups.tabs2.label.TabUiDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface KrTabsPresentation {

  KrTabsPresentation setUiDecorator(@Nullable TabUiDecorator decorator);

  void setPaintBlocked(boolean blocked, boolean takeSnapshot);

  KrTabsPresentation setInnerInsets(Insets innerInsets);

  @NotNull
  KrTabsPresentation setToDrawBorderIfTabsHidden(boolean draw);

  @NotNull
  EditorGroupsTabsBase getJBTabs();

  EditorGroupsTabsPosition getTabsPosition();

  @NotNull
  KrTabsPresentation setTabsPosition(EditorGroupsTabsPosition position);

  KrTabsPresentation setFirstTabOffset(int offset);

}
