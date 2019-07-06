// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl;

import krasa.editorGroups.tabs2.TabInfo;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public abstract class LayoutPassInfo {

	public final List<TabInfo> myVisibleInfos;

	protected LayoutPassInfo(List<TabInfo> visibleInfos) {
		myVisibleInfos = visibleInfos;
	}

	@Nullable
	public abstract TabInfo getPreviousFor(TabInfo info);

	@Nullable
	public abstract TabInfo getNextFor(TabInfo info);

	@Nullable
	public static TabInfo getPrevious(List<TabInfo> list, int i) {
		return i > 0 ? list.get(i - 1) : null;
	}

	@Nullable
	public static TabInfo getNext(List<TabInfo> list, int i) {
		return i < list.size() - 1 ? list.get(i + 1) : null;
	}

	public abstract int getRowCount();

	public abstract int getColumnCount(int row);

	public abstract TabInfo getTabAt(int row, int column);

	public abstract boolean hasCurveSpaceFor(final TabInfo tabInfo);

	public abstract Rectangle getHeaderRectangle();
}
