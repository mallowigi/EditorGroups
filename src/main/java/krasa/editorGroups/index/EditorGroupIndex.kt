package krasa.editorGroups.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import krasa.editorGroups.model.EditorGroupIndexValue
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

internal class EditorGroupIndex : FileBasedIndexExtension<String, EditorGroupIndexValue>() {

  private val myValueExternalizer: DataExternalizer<EditorGroupIndexValue> = object : DataExternalizer<EditorGroupIndexValue> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, value: EditorGroupIndexValue) {
      // WATCH OUT FOR HASHCODE AND EQUALS!!
      out.writeUTF(value.id)
      out.writeUTF(value.ownerPath)
      out.writeUTF(value.root ?: return)
      out.writeUTF(value.title)
      out.writeUTF(value.backgroundColor)
      out.writeUTF(value.foregroundColor)

      out.writeInt(value.relatedPaths.size)

      val related: List<String> = value.relatedPaths
      related.forEach(out::writeUTF)
    }

    @Throws(IOException::class)
    override fun read(input: DataInput): EditorGroupIndexValue {
      // WATCH OUT FOR HASHCODE AND EQUALS!!
      val value = EditorGroupIndexValue()
      value.id = input.readUTF()
      value.ownerPath = input.readUTF()
      value.root = input.readUTF()
      value.title = input.readUTF()
      value.backgroundColor = input.readUTF()
      value.foregroundColor = input.readUTF()

      val i = input.readInt()
      repeat(i) { value.addRelatedPath(input.readUTF()) }

      return value
    }
  }

  private val myIndexer: DataIndexer<String, EditorGroupIndexValue, FileContent> = EditorGroupIndexer()

  private val myInputFilter = FileBasedIndex.InputFilter { file: VirtualFile ->
    file.isInLocalFileSystem && !file.fileType.isBinary
  }

  override fun getVersion(): Int = 6

  override fun dependsOnFileContent(): Boolean = true

  override fun getName(): ID<String, EditorGroupIndexValue> = NAME

  override fun getIndexer(): DataIndexer<String, EditorGroupIndexValue, FileContent> = myIndexer

  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

  override fun getValueExternalizer(): DataExternalizer<EditorGroupIndexValue> = myValueExternalizer

  override fun getInputFilter(): FileBasedIndex.InputFilter = myInputFilter

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmField
    val NAME: @NonNls ID<String, EditorGroupIndexValue> = ID.create("krasa.EditorGroupIndex")
  }
}