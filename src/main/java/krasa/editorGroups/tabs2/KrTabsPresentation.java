package krasa.editorGroups.tabs2;

import krasa.editorGroups.tabs2.label.TabUiDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface KrTabsPresentation {

  KrTabsPresentation setUiDecorator(@Nullable TabUiDecorator decorator);

  KrTabsPresentation setInnerInsets(Insets innerInsets);

  EditorGroupsTabsPosition getTabsPosition();

  @NotNull
  KrTabsPresentation setTabsPosition(EditorGroupsTabsPosition position);

  KrTabsPresentation setFirstTabOffset(int offset);

}
