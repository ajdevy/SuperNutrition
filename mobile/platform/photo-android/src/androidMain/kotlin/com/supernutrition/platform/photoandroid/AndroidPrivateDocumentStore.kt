package com.supernutrition.platform.photoandroid

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.util.AtomicFile
import com.supernutrition.core.config.PrivateDocumentEncoding
import com.supernutrition.core.config.PrivateDocumentId
import com.supernutrition.core.config.PrivateDocumentStore
import com.supernutrition.core.config.RevisionCheckedPrivateDocumentStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** All instances serialize revision checks and atomic replacements under one process-wide lock. */
class AndroidPrivateDocumentStore(context: Context, id: PrivateDocumentId) : PrivateDocumentStore {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, id.fileName))
    private val backup = File(file.baseFile.path + ".bak")
    private val pending = File(file.baseFile.path + ".new")
    private val document = RevisionCheckedPrivateDocumentStore(::readDocument, ::writeDocument)

    override fun load(): String? = synchronized(lock) { document.load() }

    override fun save(encoded: String): Boolean = synchronized(lock) {
        try {
            document.save(encoded)
        } catch (_: IOException) {
            false
        }
    }

    private fun readDocument(): String? {
        // Older AtomicFile recovery deletes the valid base before trying to rename a legacy backup.
        if (backup.isDirectory) throw IOException("Invalid private-document backup")
        return try {
            file.openRead().use { PrivateDocumentEncoding.decode(it.readBoundedDocument()) }
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists() || backup.exists()) throw missing
            null
        }
    }

    private fun writeDocument(encoded: String): Boolean {
        val bytes = PrivateDocumentEncoding.encode(encoded)
        return try {
            FileOutputStream(pending).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            // A successful syscall is the commit point; no fallible verification follows it.
            Os.rename(pending.path, file.baseFile.path)
            true
        } catch (_: IOException) {
            discardPending()
        } catch (_: ErrnoException) {
            discardPending()
        }
    }

    private fun discardPending(): Boolean {
        if (pending.isFile) pending.delete()
        return false
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
