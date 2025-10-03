/**
 * ****************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Elior "Mallowigi" Boukhobza
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * ****************************************************************************
 */
@file:Suppress("KDocMissingDocumentation")

package krasa.editorGroups.messages

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.*

@NonNls
private const val BUNDLE: String = "messages.EditorGroupsBundle"

object EditorGroupsBundle : DynamicBundle(BUNDLE) {
  private val localizedBundle: ResourceBundle?
    get() = ResourceBundle.getBundle(BUNDLE, getLocale())

  @NlsContexts.DialogMessage
  @NlsContexts.DialogTitle
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)

  override fun getMessage(key: String, vararg params: Any?): String = BundleBase.messageOrDefault(localizedBundle, key, null, *params)

  override fun messageOrDefault(key: String, defaultValue: String?, vararg params: Any?): String? =
    messageOrDefault(localizedBundle, key, defaultValue, *params)

}