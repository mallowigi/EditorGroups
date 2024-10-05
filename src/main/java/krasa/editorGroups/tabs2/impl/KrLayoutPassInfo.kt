// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package krasa.editorGroups.tabs2.impl

import krasa.editorGroups.tabs2.KrTabInfo
import java.awt.Rectangle

abstract class KrLayoutPassInfo protected constructor(@JvmField val visibleInfos: MutableList<KrTabInfo>) {
  @JvmField
  var entryPointRect: Rectangle = Rectangle()

  @JvmField
  var moreRect: Rectangle = Rectangle()

  @JvmField
  var titleRect: Rectangle = Rectangle()

  abstract val rowCount: Int

  abstract val headerRectangle: Rectangle?

  abstract val requiredLength: Int

  abstract val scrollExtent: Int

  companion object {
    fun getPrevious(list: MutableList<KrTabInfo>, i: Int): KrTabInfo? = when {
      i > 0 -> list[i - 1]
      else  -> null
    }

    fun getNext(list: MutableList<KrTabInfo>, i: Int): KrTabInfo? = when {
      i < list.size - 1 -> list[i + 1]
      else              -> null
    }
  }
}
