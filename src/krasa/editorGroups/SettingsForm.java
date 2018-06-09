package krasa.editorGroups;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

public class SettingsForm {

	private static final Logger LOG = Logger.getInstance(SettingsForm.class);

	
	private JPanel root;
	private JCheckBox byName;
	private JCheckBox byFolder;
	private JCheckBox autoSwitch;
	private JCheckBox hideEmpty;
	private JCheckBox showSize;
	private JCheckBox continuousScrolling;
	private JCheckBox latencyOverFlicker;
	private JCheckBox indexOnlyEditorGroupsFileCheckBox;
	private JCheckBox excludeEGroups;


	public JPanel getRoot() {
		return root;
	}

	public boolean isSettingsModified(ApplicationConfiguration.State state) {
		return isModified(state);
	}

	public void importFrom(ApplicationConfiguration.State state) {
		setData(state);
	}

	public void apply() {
		if (LOG.isDebugEnabled()) LOG.debug("apply " + "");
		ApplicationConfiguration.State state = ApplicationConfiguration.state();
		getData(state);
	}


	private void createUIComponents() {
	}

	public void setData(ApplicationConfiguration.State data) {
		byName.setSelected(data.isAutoSameName());
		showSize.setSelected(data.isShowSize());
		hideEmpty.setSelected(data.isHideEmpty());
		autoSwitch.setSelected(data.isForceSwitch());
		byFolder.setSelected(data.isAutoFolders());
		continuousScrolling.setSelected(data.isContinuousScrolling());
		latencyOverFlicker.setSelected(data.isPreferLatencyOverFlicker());
		indexOnlyEditorGroupsFileCheckBox.setSelected(data.isIndexOnlyEditorGroupsFiles());
		excludeEGroups.setSelected(data.isExcludeEditorGroupsFiles());
	}

	public void getData(ApplicationConfiguration.State data) {
		data.setAutoSameName(byName.isSelected());
		data.setShowSize(showSize.isSelected());
		data.setHideEmpty(hideEmpty.isSelected());
		data.setForceSwitch(autoSwitch.isSelected());
		data.setAutoFolders(byFolder.isSelected());
		data.setContinuousScrolling(continuousScrolling.isSelected());
		data.setPreferLatencyOverFlicker(latencyOverFlicker.isSelected());
		data.setIndexOnlyEditorGroupsFiles(indexOnlyEditorGroupsFileCheckBox.isSelected());
		data.setExcludeEditorGroupsFiles(excludeEGroups.isSelected());
	}

	public boolean isModified(ApplicationConfiguration.State data) {
		if (byName.isSelected() != data.isAutoSameName()) return true;
		if (showSize.isSelected() != data.isShowSize()) return true;
		if (hideEmpty.isSelected() != data.isHideEmpty()) return true;
		if (autoSwitch.isSelected() != data.isForceSwitch()) return true;
		if (byFolder.isSelected() != data.isAutoFolders()) return true;
		if (continuousScrolling.isSelected() != data.isContinuousScrolling()) return true;
		if (latencyOverFlicker.isSelected() != data.isPreferLatencyOverFlicker()) return true;
		if (indexOnlyEditorGroupsFileCheckBox.isSelected() != data.isIndexOnlyEditorGroupsFiles()) return true;
		if (excludeEGroups.isSelected() != data.isExcludeEditorGroupsFiles()) return true;
		return false;
	}
}