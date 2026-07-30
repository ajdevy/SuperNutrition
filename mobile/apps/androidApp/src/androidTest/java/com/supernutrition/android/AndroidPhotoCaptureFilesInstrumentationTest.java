package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.platform.photoandroid.AndroidCameraCaptureActivity;
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge;
import com.supernutrition.platform.photoandroid.AndroidPhotoCaptureFiles;
import com.supernutrition.platform.photoandroid.AndroidPhotoCaptureResultStore;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Verifies result routing and defensive cleanup at the CameraX bridge boundary. */
public final class AndroidPhotoCaptureFilesInstrumentationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void cameraIntentTargetsThePrivateCameraXActivity() {
        Intent intent = new AndroidPhotoBridge(context).cameraCaptureIntent("attempt-42", "operation-42");

        assertEquals(context.getPackageName(), intent.getComponent().getPackageName());
        assertEquals(AndroidCameraCaptureActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("attempt-42",
                intent.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_ATTEMPT_ID));
        assertEquals("operation-42",
                intent.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_OPERATION_ID));
    }

    @Test
    public void ownedResultBytesAreConsumedAndDeleted() throws Exception {
        File captureFile = AndroidPhotoCaptureFiles.INSTANCE.create(context, "attempt-42");
        byte[] source = new byte[]{0x01, 0x02, 0x03};
        try (FileOutputStream output = new FileOutputStream(captureFile)) {
            output.write(source);
        }

        AndroidPhotoBridge bridge = new AndroidPhotoBridge(context);
        byte[] result = bridge.photoBytesFromResult(
                new Intent().setData(Uri.fromFile(captureFile))
        );

        assertArrayEquals(source, result);
        assertFalse("capture result must not persist in cache", captureFile.exists());
    }

    @Test
    public void cameraBytesResultUsesOneShotOpaqueTokenWithoutAFileUri() {
        byte[] source = new byte[]{0x04, 0x05, 0x06};
        String token = AndroidPhotoCaptureResultStore.put(source);
        Intent captured = new Intent().putExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_TOKEN, token);
        byte[] result = new AndroidPhotoBridge(context).photoBytesFromResult(captured);

        assertArrayEquals(source, result);
        assertNull("CameraX result must not carry a persisted file URI", captured.getData());
        assertNull("CameraX result token must be one-shot",
                AndroidPhotoCaptureResultStore.take(token));
    }

    @Test
    public void staleCleanupRemovesOnlyOwnedCaptureFiles() throws Exception {
        File owned = AndroidPhotoCaptureFiles.INSTANCE.create(context, "stale");
        File unrelated = new File(context.getCacheDir(), "keep-me.txt");
        assertTrue(owned.createNewFile());
        assertTrue(unrelated.createNewFile());

        try {
            assertEquals(1, new AndroidPhotoBridge(context).cleanupStaleCaptureFiles());
            assertFalse(owned.exists());
            assertTrue(unrelated.exists());
        } finally {
            unrelated.delete();
        }
    }
}
