package krasa.editorGroups.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import krasa.editorGroups.EditorGroupsSettingsState;
import krasa.editorGroups.support.Utils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public abstract class EditorGroup {
  @SuppressWarnings("StaticInitializerReferencesSubClass")
  public static final EditorGroup EMPTY = new EditorGroupIndexValue("NOT_EXISTS", "NOT_EXISTS", false).setLinks(Collections.emptyList());
  private boolean stub;

  public static boolean exists(@NotNull EditorGroup group) {
    return !group.getId().equals("NOT_EXISTS");
  }

  @NotNull
  public abstract String getId();

  public String getOwnerPath() {
    return getId();
  }

  public abstract String getTitle();

  public abstract boolean isValid();

  public abstract Icon icon();

  public abstract void invalidate();

  public abstract int size(Project project);

  public boolean isInvalid() {
    return !isValid();
  }

  public abstract List<Link> getLinks(Project project);

  public abstract boolean isOwner(String ownerPath);

  public String getPresentableTitle(Project project, String presentableNameForUI, boolean showSize) {
    //			if (LOG.isDebugEnabled()) LOG.debug("getEditorTabTitle "+textEditor.getName() + ": "+group.getTitle());

    if (showSize) {
      int size = size(project);
      if (isNotEmpty(getTitle())) {
        String title = getTitle() + ":" + size;
        presentableNameForUI = "[" + title + "] " + presentableNameForUI;
      } else {
        presentableNameForUI = "[" + size + "] " + presentableNameForUI;
      }
    } else {
      boolean empty = isEmpty(getTitle());
      if (!empty) {
        presentableNameForUI = "[" + getTitle() + "] " + presentableNameForUI;
      }
    }
    return presentableNameForUI;
  }

  public String getSwitchDescription() {

    if (this instanceof AutoGroup) {
      return null;
    }
    if (!(this instanceof FavoritesGroup) && !(this instanceof BookmarkGroup)) {
      return "Owner:" + getOwnerPath();
    }
    return null;
  }


  public Color getBgColor() {
    return null;
  }

  @Deprecated
  public boolean containsLink(Project project, String currentFilePath) {
    List<Link> links = getLinks(project);
    for (Link link : links) {
      if (link.getPath().equals(currentFilePath)) {
        return true;
      }
    }
    return false;
  }

  public boolean containsLink(Project project, VirtualFile currentFile) {
    List<Link> links = getLinks(project);
    for (Link link : links) {
      if (link.fileEquals(currentFile)) {
        return true;
      }
    }
    return false;
  }

  public boolean equalsVisually(Project project, EditorGroup group, List<Link> links, boolean stub) {
    if (group == null) {
      return false;
    }
    if (this.isStub() != stub) {
      return false;
    }
    if (!this.equals(group)) {
      return false;
    }
    return this.getLinks(project).equals(links);
  }


  public Color getFgColor() {
    return null;
  }

  public VirtualFile getFirstExistingFile(Project project) {
    List<Link> links = getLinks(project);
    for (Link link : links) {
      VirtualFile fileByPath = Utils.getFileByPath(link);
      if (fileByPath != null && fileByPath.exists() && !fileByPath.isDirectory()) {
        return fileByPath;
      }
    }

    return null;
  }

  @NotNull
  public String tabTitle(Project project) {
    String title = getTitle();
    if (title.isEmpty()) {
      title = Utils.toPresentableName(getOwnerPath());
    }
    if (EditorGroupsSettingsState.state().isShowSize()) {
      title += ":" + size(project);
    }
    return title;
  }

  public String switchTitle(Project project) {
    String ownerPath = getOwnerPath();
    String name = Utils.toPresentableName(ownerPath);
    return getPresentableTitle(project, name, false);
  }

  public String getTabGroupTooltipText(Project project) {
    return getPresentableTitle(project, "Owner: " + getOwnerPath(), true);
  }

  public boolean isStub() {
    return stub;
  }

  public void setStub(boolean stub) {
    this.stub = stub;
  }

  public boolean isSelected(EditorGroup groupLink) {
    return this.equals(groupLink);
  }

  public boolean needSmartMode() {
    return false;
  }
}
