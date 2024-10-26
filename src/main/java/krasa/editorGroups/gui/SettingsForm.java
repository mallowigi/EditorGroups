package krasa.editorGroups.gui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ToolbarDecorator;
import krasa.editorGroups.EditorGroupsSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class SettingsForm {
  private static final Logger LOG = Logger.getInstance(SettingsForm.class);
  private JPanel root;
  private JCheckBox byName;
  private JCheckBox byFolder;
  private JCheckBox autoSwitch;
  private JCheckBox hideEmpty;
  private JCheckBox showSize;
  private JCheckBox continuousScrolling;
  private JCheckBox initializeSynchronously;
  private JCheckBox indexOnlyEditorGroupsFileCheckBox;
  private JCheckBox excludeEGroups;
  private JCheckBox compactTabs;
  private JPanel tabColors;
  private JCheckBox rememberLastGroup;
  /**
   * Group 'Switch Editor Group' action's list by type and the current file
   */
  private JCheckBox groupSwitchGroupAction;
  private JPanel modelsPanel;
  private JCheckBox selectRegexGroup;
  private JTextField groupSizeLimit;
  private JTextField tabSizeLimit;
  private JCheckBox showPanel;

  private final RegexModelTable regexModelTable;

  public JPanel getRoot() {
    return root;
  }

  public SettingsForm() {
    regexModelTable = new RegexModelTable();
    modelsPanel.add(
      ToolbarDecorator.createDecorator(regexModelTable)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            regexModelTable.addRegexModel();
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            regexModelTable.removeSelectedRegexModels();
          }
        }).setEditAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            regexModelTable.editRegexModel();
          }
        }).setMoveUpAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            regexModelTable.moveUp();
          }
        }).setMoveDownAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            regexModelTable.moveDown();
          }
        }).createPanel(), BorderLayout.CENTER);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        return regexModelTable.editRegexModel();
      }
    }.installOn(regexModelTable);


  }

  public boolean isSettingsModified(EditorGroupsSettings data) {
    if (regexModelTable.isModified(data)) return true;
    return isModified(data);
  }

  public void importFrom(EditorGroupsSettings data) {
    setData(data);
    regexModelTable.reset(data);
  }

  public void apply() {
    if (LOG.isDebugEnabled()) LOG.debug("apply ");
    EditorGroupsSettings data = EditorGroupsSettings.getInstance();

    getData(data);
    regexModelTable.commit(data);
  }


  public void setData(EditorGroupsSettings data) {
    initializeSynchronously.setSelected(data.isInitializeSynchronously());
    indexOnlyEditorGroupsFileCheckBox.setSelected(data.isIndexOnlyEditorGroupsFiles());
    groupSizeLimit.setText(String.valueOf(data.getGroupSizeLimit()));
    tabSizeLimit.setText(String.valueOf(data.getTabSizeLimit()));
    byName.setSelected(data.isAutoSameName());
    autoSwitch.setSelected(data.isForceSwitch());
    byFolder.setSelected(data.isAutoFolders());
    selectRegexGroup.setSelected(data.isSelectRegexGroup());
    rememberLastGroup.setSelected(data.isRememberLastGroup());
    hideEmpty.setSelected(data.isHideEmpty());
    showSize.setSelected(data.isShowSize());
    continuousScrolling.setSelected(data.isContinuousScrolling());
    groupSwitchGroupAction.setSelected(data.isGroupSwitchGroupAction());
    excludeEGroups.setSelected(data.isExcludeEditorGroupsFiles());
    compactTabs.setSelected(data.isCompactTabs());
    showPanel.setSelected(data.isShowPanel());
  }

  public void getData(EditorGroupsSettings data) {
    data.setInitializeSynchronously(initializeSynchronously.isSelected());
    data.setIndexOnlyEditorGroupsFiles(indexOnlyEditorGroupsFileCheckBox.isSelected());
    data.setGroupSizeLimit(Integer.parseInt(groupSizeLimit.getText()));
    data.setTabSizeLimit(Integer.parseInt(tabSizeLimit.getText()));
    data.setAutoSameName(byName.isSelected());
    data.setForceSwitch(autoSwitch.isSelected());
    data.setAutoFolders(byFolder.isSelected());
    data.setSelectRegexGroup(selectRegexGroup.isSelected());
    data.setRememberLastGroup(rememberLastGroup.isSelected());
    data.setHideEmpty(hideEmpty.isSelected());
    data.setShowSize(showSize.isSelected());
    data.setContinuousScrolling(continuousScrolling.isSelected());
    data.setGroupSwitchGroupAction(groupSwitchGroupAction.isSelected());
    data.setExcludeEditorGroupsFiles(excludeEGroups.isSelected());
    data.setCompactTabs(compactTabs.isSelected());
    data.setShowPanel(showPanel.isSelected());
  }

  public boolean isModified(EditorGroupsSettings data) {
    if (initializeSynchronously.isSelected() != data.isInitializeSynchronously()) return true;
    if (indexOnlyEditorGroupsFileCheckBox.isSelected() != data.isIndexOnlyEditorGroupsFiles()) return true;
    if (Integer.parseInt(groupSizeLimit.getText()) != data.getGroupSizeLimit()) return true;
    if (Integer.parseInt(tabSizeLimit.getText()) != data.getTabSizeLimit()) return true;
    if (byName.isSelected() != data.isAutoSameName()) return true;
    if (autoSwitch.isSelected() != data.isForceSwitch()) return true;
    if (byFolder.isSelected() != data.isAutoFolders()) return true;
    if (selectRegexGroup.isSelected() != data.isSelectRegexGroup()) return true;
    if (rememberLastGroup.isSelected() != data.isRememberLastGroup()) return true;
    if (hideEmpty.isSelected() != data.isHideEmpty()) return true;
    if (showSize.isSelected() != data.isShowSize()) return true;
    if (continuousScrolling.isSelected() != data.isContinuousScrolling()) return true;
    if (groupSwitchGroupAction.isSelected() != data.isGroupSwitchGroupAction()) return true;
    if (excludeEGroups.isSelected() != data.isExcludeEditorGroupsFiles()) return true;
    if (compactTabs.isSelected() != data.isCompactTabs()) return true;
    return showPanel.isSelected() != data.isShowPanel();
  }
}
