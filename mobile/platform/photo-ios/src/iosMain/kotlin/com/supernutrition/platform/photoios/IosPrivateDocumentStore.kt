@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.supernutrition.platform.photoios

import com.supernutrition.core.config.PrivateDocumentEncoding
import com.supernutrition.core.config.PrivateDocumentId
import com.supernutrition.core.config.PrivateDocumentStore
import com.supernutrition.core.config.RevisionCheckedPrivateDocumentStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL

/** Protected atomic documents under a backup-excluded private Application Support directory. */
class IosPrivateDocumentStore(id: PrivateDocumentId, private val directory: NSURL = privateUserDataDirectory()) :
    PrivateDocumentStore {
    private val url = requireNotNull(directory.URLByAppendingPathComponent(id.fileName))
    private val document = RevisionCheckedPrivateDocumentStore(::readDocument, ::writeDocument)

    override fun load(): String? = withDocumentLock { document.load() }

    override fun save(encoded: String): Boolean = withDocumentLock { document.save(encoded) }

    private fun readDocument(): String? {
        ensurePrivateDirectory()
        val path = requireNotNull(url.path)
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        val attributes = checkNotNull(NSFileManager.defaultManager.attributesOfItemAtPath(path, null)) {
            "Private document metadata could not be read"
        }
        val size = checkNotNull(attributes[NSFileSize] as? NSNumber).unsignedLongLongValue
        require(size <= PrivateDocumentEncoding.MAX_BYTES.toULong()) { "Private document exceeds its size limit" }
        val data = checkNotNull(NSFileManager.defaultManager.contentsAtPath(path)) {
            "Private document could not be read"
        }
        require(data.length <= PrivateDocumentEncoding.MAX_BYTES.toULong()) {
            "Private document exceeds its size limit"
        }
        val bytes = if (data.length == 0uL) {
            byteArrayOf()
        } else {
            checkNotNull(data.bytes).readBytes(data.length.toInt())
        }
        return PrivateDocumentEncoding.decode(bytes)
    }

    private fun writeDocument(encoded: String): Boolean {
        ensurePrivateDirectory()
        val bytes = PrivateDocumentEncoding.encode(encoded)
        return bytes.usePinned { pinned ->
            val data = NSData.create(
                bytes = if (bytes.isEmpty()) null else pinned.addressOf(0),
                length = bytes.size.toULong(),
            )
            data.writeToURL(
                url,
                options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete,
                error = null,
            )
        }
    }

    private fun ensurePrivateDirectory() {
        check(
            NSFileManager.defaultManager.createDirectoryAtURL(
                directory,
                withIntermediateDirectories = true,
                attributes = mapOf(NSFileProtectionKey to NSFileProtectionComplete),
                error = null,
            ),
        ) { "Private directory could not be created" }
        // The stable parent excludes the whole subtree, including atomic temporary replacements.
        check(directory.setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)) {
            "Private directory could not be excluded from backup"
        }
    }
}

private fun privateUserDataDirectory(): NSURL {
    val support = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
        .first() as NSURL
    return requireNotNull(support.URLByAppendingPathComponent("LocalUserData", isDirectory = true))
}

private val documentLock = NSRecursiveLock()

private inline fun <T> withDocumentLock(action: () -> T): T {
    documentLock.lock()
    return try {
        action()
    } finally {
        documentLock.unlock()
    }
}
