@file:Suppress("detekt:Filename") // NON-NLS

package krasa.editorGroups.settings

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import krasa.editorGroups.messages.EditorGroupsBundle.message
import java.awt.event.ActionEvent

internal fun Panel.resetButton(action: (event: ActionEvent) -> Unit) {
  separator()

  row {
    button(message("EditorGroupsSettings.resetDefaultsButton.text"), action)
      .align(AlignX.LEFT)
  }
    .rowComment(message("EditorGroupsSettings.resetDefaultsButton.toolTipText"))
    .bottomGap(BottomGap.SMALL)
    .topGap(TopGap.SMALL)
}