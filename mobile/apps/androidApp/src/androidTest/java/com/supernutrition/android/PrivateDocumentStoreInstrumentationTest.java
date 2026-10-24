package com.supernutrition.android;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.core.config.PrivateDocumentEncoding;
import com.supernutrition.core.config.PrivateDocumentId;
import com.supernutrition.platform.photoandroid.AndroidPrivateDocumentStore;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Real app-private filesystem I/O; fixture paths never overlap production user documents. */
public final class PrivateDocumentStoreInstrumentationTest {
    @Test
    public void staleOwnersCannotOverwriteAcceptedChangesOrResurrectDeletedValues() throws Exception {
        for (PrivateDocumentId id : PrivateDocumentId.values()) {
            File directory = fixtureDirectory();
            Context context = fixtureContext(directory);
            File target = new File(directory, id.getFileName());
            try {
                AndroidPrivateDocumentStore first = new AndroidPrivateDocumentStore(context, id);
                AndroidPrivateDocumentStore stale = new AndroidPrivateDocumentStore(context, id);
                assertNull(first.load());
                assertNull(stale.load());
                assertTrue(first.save("Accepted change"));
                assertFalse("A stale owner must reject its replacement", stale.save("Lost update"));
                AndroidPrivateDocumentStore beforeDeletion = new AndroidPrivateDocumentStore(context, id);
                assertEquals("Accepted change", beforeDeletion.load());
                assertTrue(first.save(""));
                assertFalse("A stale owner must not resurrect deleted content", beforeDeletion.save("Resurrected"));
                assertEquals("", new AndroidPrivateDocumentStore(context, id).load());
            } finally {
                target.delete();
                new File(target.getPath() + ".bak").delete();
                new File(target.getPath() + ".new").delete();
                directory.delete();
            }
        }
    }

    @Test
    public void aCommittedReplacementDoesNotBecomeFailureBecauseVerificationCannotReadIt() throws Exception {
        File directory = fixtureDirectory();
        Context context = fixtureContext(directory);
        File target = new File(directory, PrivateDocumentId.PROFILE_NAME.getFileName());
        File pending = new File(target.getPath() + ".new");
        try {
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write("Previous value".getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(pending.createNewFile());
            assertTrue(pending.setReadable(false, false));
            boolean saved = new AndroidPrivateDocumentStore(context, PrivateDocumentId.PROFILE_NAME).save("Committed value");
            target.setReadable(true, true);
            pending.setReadable(true, true);
            assertTrue("A completed replacement must remain a file", target.isFile());
            assertEquals("The replacement was committed before its reported result", "Committed value", readDocument(target));
            assertTrue("A committed replacement must report success", saved);
        } finally {
            target.setReadable(true, true);
            pending.setReadable(true, true);
            target.delete();
            pending.delete();
            new File(target.getPath() + ".bak").delete();
            directory.delete();
        }
    }

    @Test
    public void readingAnObstructedBackupPreservesTheValidBaseAndBackupContents() throws Exception {
        for (PrivateDocumentId id : PrivateDocumentId.values()) {
            File directory = fixtureDirectory();
            Context context = fixtureContext(directory);
            File target = new File(directory, id.getFileName());
            File backup = new File(target.getPath() + ".bak");
            File obstruction = new File(backup, "owned-fixture");
            try {
                try (FileOutputStream output = new FileOutputStream(target)) {
                    output.write("Previous valid bytes 🥗".getBytes(StandardCharsets.UTF_8));
                }
                assertTrue(backup.mkdir());
                assertTrue(obstruction.createNewFile());
                expectLoadFailure(new AndroidPrivateDocumentStore(context, id));
                assertTrue("A failed recovery must preserve the base file", target.isFile());
                assertEquals("Previous valid bytes 🥗", readDocument(target));
                assertTrue(obstruction.exists());
            } finally {
                new File(target, "owned-fixture").delete();
                target.delete();
                obstruction.delete();
                backup.delete();
                directory.delete();
            }
        }
    }

    @Test
    public void freshInstancesRestoreExactUnicodeAndEmptyDocuments() throws Exception {
        File directory = fixtureDirectory();
        Context context = fixtureContext(directory);
        AndroidPrivateDocumentStore first = new AndroidPrivateDocumentStore(context, PrivateDocumentId.MEAL_DIARY);
        File file = new File(directory, PrivateDocumentId.MEAL_DIARY.getFileName());
        try {
            assertNull(first.load());
            assertTrue(first.save("Анна 🥗\nSNMD1:0:"));
            assertEquals("Анна 🥗\nSNMD1:0:",
                    new AndroidPrivateDocumentStore(context, PrivateDocumentId.MEAL_DIARY).load());
            assertTrue(first.save(""));
            assertEquals("", new AndroidPrivateDocumentStore(context, PrivateDocumentId.MEAL_DIARY).load());
            assertTrue(file.getCanonicalPath().startsWith(context.getNoBackupFilesDir().getCanonicalPath() + "/"));
            assertFalse(new File(file.getPath() + ".bak").exists());
            assertFalse(new File(file.getPath() + ".new").exists());
        } finally {
            file.delete();
            directory.delete();
        }
    }

    @Test
    public void malformedAndOversizeDocumentsFailWithoutReplacingTheirBytes() throws Exception {
        File directory = fixtureDirectory();
        Context context = fixtureContext(directory);
        File file = new File(directory, PrivateDocumentId.MEAL_DIARY.getFileName());
        AndroidPrivateDocumentStore store = new AndroidPrivateDocumentStore(context, PrivateDocumentId.MEAL_DIARY);
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(new byte[] {(byte) 0xC3, 0x28});
            }
            expectLoadFailure(store);
            assertEquals(2, file.length());
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
                output.setLength(PrivateDocumentEncoding.MAX_BYTES + 1L);
            }
            expectLoadFailure(store);
            assertEquals(PrivateDocumentEncoding.MAX_BYTES + 1L, file.length());
        } finally {
            file.delete();
            directory.delete();
        }
    }

    @Test
    public void anUncommittedAtomicReplacementNeverReportsSuccess() throws Exception {
        File directory = fixtureDirectory();
        Context context = fixtureContext(directory);
        File target = new File(directory, PrivateDocumentId.PROFILE_NAME.getFileName());
        assertTrue(target.mkdir());
        File obstruction = new File(target, "owned-fixture");
        assertTrue(obstruction.createNewFile());
        File legacyBackup = new File(target.getPath() + ".bak");
        assertTrue(legacyBackup.mkdir());
        File backupObstruction = new File(legacyBackup, "owned-fixture");
        assertTrue(backupObstruction.createNewFile());
        try {
            assertFalse(new AndroidPrivateDocumentStore(context, PrivateDocumentId.PROFILE_NAME).save("new name"));
            assertTrue(obstruction.exists());
        } finally {
            obstruction.delete();
            target.delete();
            backupObstruction.delete();
            legacyBackup.delete();
            new File(target.getPath() + ".new").delete();
            directory.delete();
        }
    }

    @Test
    public void anObstructedLegacyBackupCannotReplaceThePreviousValue() throws Exception {
        File directory = fixtureDirectory();
        Context context = fixtureContext(directory);
        File target = new File(directory, PrivateDocumentId.PROFILE_NAME.getFileName());
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write("old profile".getBytes(StandardCharsets.UTF_8));
        }
        File legacyBackup = new File(target.getPath() + ".bak");
        assertTrue(legacyBackup.mkdir());
        File obstruction = new File(legacyBackup, "owned-fixture");
        assertTrue(obstruction.createNewFile());
        try {
            assertFalse(new AndroidPrivateDocumentStore(context, PrivateDocumentId.PROFILE_NAME).save("new profile"));
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    new FileInputStream(target), StandardCharsets.UTF_8))) {
                assertEquals("old profile", input.readLine());
                assertNull(input.readLine());
            }
            assertTrue(obstruction.exists());
            assertFalse(new File(target.getPath() + ".new").exists());
        } finally {
            target.delete();
            obstruction.delete();
            legacyBackup.delete();
            new File(target.getPath() + ".new").delete();
            directory.delete();
        }
    }

    @Test
    public void regularLegacyBackupRestoresCommittedBytesWithOrWithoutAnInterruptedBase() throws Exception {
        for (boolean interruptedBase : new boolean[] {false, true}) {
            File directory = fixtureDirectory();
            Context context = fixtureContext(directory);
            File target = new File(directory, PrivateDocumentId.PROFILE_NAME.getFileName());
            File backup = new File(target.getPath() + ".bak");
            try {
                try (FileOutputStream output = new FileOutputStream(backup)) {
                    output.write("Committed Анна 🥗".getBytes(StandardCharsets.UTF_8));
                }
                if (interruptedBase) {
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write("Interrupted replacement".getBytes(StandardCharsets.UTF_8));
                    }
                }
                AndroidPrivateDocumentStore store = new AndroidPrivateDocumentStore(context, PrivateDocumentId.PROFILE_NAME);
                assertEquals("Committed Анна 🥗", store.load());
                assertFalse(backup.exists());
                assertEquals("Committed Анна 🥗",
                        new AndroidPrivateDocumentStore(context, PrivateDocumentId.PROFILE_NAME).load());
                assertTrue(store.save("Explicit next save"));
                assertEquals("Explicit next save", store.load());
                assertFalse(backup.exists());
            } finally {
                target.delete();
                backup.delete();
                new File(target.getPath() + ".new").delete();
                directory.delete();
            }
        }
    }

    private static void expectLoadFailure(AndroidPrivateDocumentStore store) {
        try {
            store.load();
            fail("Invalid bytes must not be restored as identity text");
        } catch (Exception expected) {
            // Exact private paths and raw content are deliberately not printed.
        }
    }

    private static String readDocument(File file) throws Exception {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String value = input.readLine();
            assertNull(input.readLine());
            return value;
        }
    }

    private static File fixtureDirectory() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        File directory = new File(target.getNoBackupFilesDir(), "document-store-test-" + UUID.randomUUID());
        assertTrue(directory.mkdir());
        return directory;
    }

    private static Context fixtureContext(File directory) {
        return new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getNoBackupFilesDir() { return directory; }
        };
    }
}
