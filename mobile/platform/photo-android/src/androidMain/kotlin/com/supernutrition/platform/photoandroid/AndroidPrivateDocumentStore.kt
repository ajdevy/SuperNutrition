package com.supernutrition.platform.photoandroid

import android.content.Context
import android.util.AtomicFile
import com.supernutrition.core.config.PrivateDocumentEncoding
import com.supernutrition.core.config.PrivateDocumentId
import com.supernutrition.core.config.PrivateDocumentStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** All instances share a lock because AtomicFile does not coordinate concurrent readers/writers. */
class AndroidPrivateDocumentStore(context: Context, id: PrivateDocumentId) : PrivateDocumentStore {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, id.fileName))
    private val backup = File(file.baseFile.path + ".bak")
    private val pending = File(file.baseFile.path + ".new")

    override fun load(): String? = synchronized(lock) {
        try {
            file.openRead().use { PrivateDocumentEncoding.decode(it.readBoundedDocument()) }
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists() || backup.exists()) throw missing
            null
        }
    }

    override fun save(encoded: String): Boolean = synchronized(lock) {
        val bytes = PrivateDocumentEncoding.encode(encoded)
        // An unrestorable legacy directory must not let a new write replace the valid base file.
        if (backup.isDirectory) return@synchronized false
        val stream = try {
            file.startWrite()
        } catch (_: IOException) {
            return@synchronized false
        }
        try {
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
            // finishWrite can log a failed rename instead of throwing; never report that as a saved document.
            if (backup.exists() || pending.exists()) {
                file.failWrite(stream)
                false
            } else {
                file.openRead().use { it.readBoundedDocument().contentEquals(bytes) }
            }
        } catch (_: IOException) {
            file.failWrite(stream)
            false
        }
    }

    private companion object {
        val lock = Any()
    }
}

private fun InputStream.readBoundedDocument(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var count = read(buffer)
    while (count != -1) {
        require(output.size() <= PrivateDocumentEncoding.MAX_BYTES - count) {
            "Private document exceeds its size limit"
        }
        output.write(buffer, 0, count)
        count = read(buffer)
    }
    return output.toByteArray()
}
