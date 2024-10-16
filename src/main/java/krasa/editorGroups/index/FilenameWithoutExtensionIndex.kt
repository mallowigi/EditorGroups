/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package krasa.editorGroups.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NonNls
import java.util.Collections

/**
 * A custom index for storing filenames without their extensions for fast
 * search and retrieval.
 */
class FilenameWithoutExtensionIndex : ScalarIndexExtension<String?>() {
  override fun getName(): ID<String?, Void?> = NAME

  /**
   * Returns a DataIndexer for indexing filenames without their extensions.
   *
   * This method provides an implementation of the DataIndexer interface
   * which maps FileContent to a single entry where the key is the filename
   * without its extension, and the value is null.
   *
   * @return A DataIndexer instance for indexing filenames without
   *    extensions.
   */
  override fun getIndexer(): DataIndexer<String?, Void?, FileContent?> = object : DataIndexer<String?, Void?, FileContent?> {
    override fun map(inputData: FileContent): MutableMap<String?, Void?> {
      val fileName = inputData.fileName
      val key = StringUtils.substringBefore(fileName, ".")
      return Collections.singletonMap<String?, Void?>(key, null)
    }
  }

  /**
   * Provides a KeyDescriptor for indexing key elements.
   *
   * This method returns a key descriptor instance specifically designed
   * for handling string keys that may be nullable. The implementation uses
   * EnumeratorStringDescriptor to manage the string keys during the indexing
   * process.
   *
   * @return A KeyDescriptor instance for nullable string keys.
   */
  override fun getKeyDescriptor(): KeyDescriptor<String?> = EnumeratorStringDescriptor.INSTANCE

  /**
   * Provides a filter for selecting the files to be indexed.
   *
   * This method returns an instance of FileBasedIndex.InputFilter that only
   * includes files which are of type VirtualFileSystemEntry, excluding other
   * file types.
   *
   * @return an InputFilter instance to determine which files are eligible
   *    for indexing.
   */
  override fun getInputFilter(): FileBasedIndex.InputFilter =
    FileBasedIndex.InputFilter { file: VirtualFile? -> file is VirtualFileSystemEntry }

  /**
   * Indicates whether the indexer depends on the content of the files.
   *
   * This method is used to specify whether the indexing process should
   * consider the actual content of the files. If it returns true, the
   * content of the files will be used during the indexing process;
   * otherwise, only the file metadata will be considered.
   *
   * @return false indicating that the indexer does not depend on the file
   *    content.
   */
  override fun dependsOnFileContent(): Boolean = false

  /**
   * Returns the version of the index.
   *
   * This version number is used to check compatibility between the index
   * implementation and the current index data. If the version number
   * changes, the index will be rebuilt.
   *
   * @return The current version of the index.
   */
  override fun getVersion(): Int = 1

  /**
   * Indicates whether the indexing process should trace the key hash to the
   * virtual file mapping.
   *
   * This method provides a boolean value that specifies whether the virtual
   * file mapping for key hashes should be traced during the indexing
   * process. This can be useful for debugging or diagnostic purposes to
   * ensure that the key hashes are correctly mapped to their corresponding
   * virtual files.
   *
   * @return true indicating that the tracing of key hash to virtual file
   *    mapping is enabled.
   */
  override fun traceKeyHashToVirtualFileMapping(): Boolean = true

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmField
    val NAME: @NonNls ID<String?, Void?> = ID.create<String?, Void?>("krasa.FilenameWithoutExtensionIndex")
  }
}
