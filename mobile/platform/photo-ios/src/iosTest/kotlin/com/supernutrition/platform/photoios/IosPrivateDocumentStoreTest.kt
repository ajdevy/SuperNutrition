package com.supernutrition.platform.photoios

import com.supernutrition.core.config.PrivateDocumentId
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosPrivateDocumentStoreTest {
    @Test
    fun staleOwnersRejectChangesAndCannotResurrectDeletedDocuments() {
        for (id in PrivateDocumentId.entries) {
            val directory = NSURL.fileURLWithPath(NSTemporaryDirectory() + "document-store-test-" + NSUUID().UUIDString)
            try {
                val first = IosPrivateDocumentStore(id, directory)
                val stale = IosPrivateDocumentStore(id, directory)
                assertNull(first.load())
                assertNull(stale.load())
                assertTrue(first.save("Accepted change"))
                assertFalse(stale.save("Lost update"))
                val beforeDeletion = IosPrivateDocumentStore(id, directory)
                assertEquals("Accepted change", beforeDeletion.load())
                assertTrue(first.save(""))
                assertFalse(beforeDeletion.save("Resurrected"))
                assertEquals("", IosPrivateDocumentStore(id, directory).load())
            } finally {
                NSFileManager.defaultManager.removeItemAtURL(directory, null)
            }
        }
    }

    @Test
    fun protectedBackupExcludedDocumentsRestoreUnicodeAndExplicitClearing() {
        val directory = NSURL.fileURLWithPath(NSTemporaryDirectory() + "document-store-test-" + NSUUID().UUIDString)
        val store = IosPrivateDocumentStore(PrivateDocumentId.PROFILE_NAME, directory)
        try {
            assertNull(store.load())
            assertTrue(store.save("Анна 🥗"))
            assertEquals("Анна 🥗", IosPrivateDocumentStore(PrivateDocumentId.PROFILE_NAME, directory).load())
            assertTrue(store.save(""))
            assertEquals("", IosPrivateDocumentStore(PrivateDocumentId.PROFILE_NAME, directory).load())
            val backup = assertNotNull(directory.resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null))
            assertTrue((backup[NSURLIsExcludedFromBackupKey] as NSNumber).boolValue)
            val path = assertNotNull(
                directory.URLByAppendingPathComponent(PrivateDocumentId.PROFILE_NAME.fileName)?.path,
            )
            val attributes = assertNotNull(NSFileManager.defaultManager.attributesOfItemAtPath(path, null))
            assertEquals(NSFileProtectionComplete, attributes[NSFileProtectionKey])
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(directory, null)
        }
    }
}
