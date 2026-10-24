package com.supernutrition.android;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.core.config.PrivateDocumentId;
import com.supernutrition.platform.photoandroid.AndroidPrivateDocumentStore;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Instrumented journeys must exercise production wiring without using user documents. */
public final class InstrumentationStorageIsolationTest {
    @Test
    public void applicationPrivateDocumentsUseAnIsolatedInstrumentationNamespace() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File realDirectory = context.getNoBackupFilesDir().getCanonicalFile();
        File applicationDirectory = context.getApplicationContext().getNoBackupFilesDir().getCanonicalFile();
        assertFalse("Journeys must not operate on real user documents", realDirectory.equals(applicationDirectory));
        assertTrue("The fixture must remain under the no-backup parent",
                applicationDirectory.getPath().startsWith(realDirectory.getPath() + "/instrumentation-"));
        assertTrue(applicationDirectory.isDirectory());
    }

    @Test
    public void anInterruptedFixtureRestoresItsDocumentWithoutTouchingOwnedUserSideBytes() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context application = target.getApplicationContext();
        File realDirectory = target.getNoBackupFilesDir().getCanonicalFile();
        File fixtureDirectory = application.getNoBackupFilesDir().getCanonicalFile();
        assertFalse("Guard before any fixture write", realDirectory.equals(fixtureDirectory));
        File sentinel = new File(realDirectory, "owned-interruption-sentinel-" + UUID.randomUUID());
        byte[] userSideBytes = "Owned sibling remains unchanged".getBytes(StandardCharsets.UTF_8);
        File document = new File(fixtureDirectory, PrivateDocumentId.PROFILE_NAME.getFileName());
        byte[] previous = document.exists() ? readBytes(document) : null;
        try {
            writeBytes(sentinel, userSideBytes);
            try {
                assertTrue(new AndroidPrivateDocumentStore(application, PrivateDocumentId.PROFILE_NAME)
                        .save("Synthetic interrupted fixture"));
                throw new ExpectedFixtureFailure();
            } catch (ExpectedFixtureFailure expected) {
                // Exercise finally cleanup after a deliberate fixture interruption.
            } finally {
                if (previous == null) assertTrue(document.delete());
                else writeBytes(document, previous);
            }
            assertArrayEquals(userSideBytes, readBytes(sentinel));
            if (previous == null) assertFalse(document.exists());
            else assertArrayEquals(previous, readBytes(document));
            assertFalse(new File(document.getPath() + ".bak").exists());
            assertFalse(new File(document.getPath() + ".new").exists());
        } finally {
            sentinel.delete();
        }
    }

    private static byte[] readBytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
    }

    private static final class ExpectedFixtureFailure extends RuntimeException { }
}
