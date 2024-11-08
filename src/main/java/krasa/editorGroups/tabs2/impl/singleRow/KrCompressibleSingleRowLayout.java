// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl.singleRow;

import com.intellij.ui.tabs.impl.JBTabsImpl;
import krasa.editorGroups.tabs2.EditorGroupsTabsPosition;
import krasa.editorGroups.tabs2.impl.KrTabsImpl;
import krasa.editorGroups.tabs2.label.EditorGroupTabInfo;
import krasa.editorGroups.tabs2.label.EditorGroupTabLabel;

import java.util.Iterator;

@Deprecated(forRemoval = true)
public class KrCompressibleSingleRowLayout extends KrSingleRowLayout {
  public KrCompressibleSingleRowLayout(KrTabsImpl tabs) {
    super(tabs);
  }

  @Override
  protected void recomputeToLayout(KrSingleRowPassInfo data) {
    calculateRequiredLength(data);
  }

  @Override
  protected void layoutLabels(KrSingleRowPassInfo data) {
    if (myTabs.getPresentation().getTabsPosition() != EditorGroupsTabsPosition.TOP
      && myTabs.getPresentation().getTabsPosition() != EditorGroupsTabsPosition.BOTTOM) {
      super.layoutLabels(data);
      return;
    }

    int spentLength = 0;
    int lengthEstimation = 0;

    for (EditorGroupTabInfo tabInfo : data.toLayout) {
      lengthEstimation += Math.max(getMinTabWidth(), myTabs.getInfoToLabel().get(tabInfo).getPreferredSize().width);
    }

    final int extraWidth = data.toFitLength - lengthEstimation;
    float fractionalPart = 0;
    for (Iterator<EditorGroupTabInfo> iterator = data.toLayout.iterator(); iterator.hasNext(); ) {
      EditorGroupTabInfo tabInfo = iterator.next();
      final EditorGroupTabLabel label = myTabs.getInfoToLabel().get(tabInfo);

      int length;
      int lengthIncrement = label.getPreferredSize().width;
      if (!iterator.hasNext()) {
        length = Math.min(data.toFitLength - spentLength, lengthIncrement);
      } else if (extraWidth <= 0) {//need compress
        float fLength = lengthIncrement * (float) data.toFitLength / lengthEstimation;
        fractionalPart += fLength - (int) fLength;
        length = (int) fLength;
        if (fractionalPart >= 1) {
          length++;
          fractionalPart -= 1;
        }
      } else {
        length = lengthIncrement;
      }
      spentLength += length + myTabs.getTabHGap();
      applyTabLayout(data, label, length);
      data.position = (int) label.getBounds().getMaxX() + myTabs.getTabHGap();
    }

    for (EditorGroupTabInfo eachInfo : data.toDrop) {
      JBTabsImpl.Companion.resetLayout(myTabs.getInfoToLabel().get(eachInfo));
    }
  }

  @Override
  protected boolean applyTabLayout(KrSingleRowPassInfo data, EditorGroupTabLabel label, int length) {
    boolean result = super.applyTabLayout(data, label, length);
    label.setAlignmentToCenter();
    return result;
  }
}
