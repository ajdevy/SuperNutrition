package com.supernutrition.android;

import android.content.Intent;
import android.graphics.Bitmap;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.platform.photoandroid.AndroidPhotoBridge;
import com.supernutrition.platform.photoandroid.AndroidPhotoResultKeys;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

/** Verifies the native bridge emits bytes before the common normalization seam. */
public final class AndroidPhotoBridgeBytesInstrumentationTest {
    @Test
    public void cameraThumbnailResultBecomesJpegBytes() {
        Bitmap bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888);
        Intent result = new Intent().putExtra(AndroidPhotoResultKeys.LEGACY_THUMBNAIL, bitmap);

        byte[] bytes = new AndroidPhotoBridge(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
        ).photoBytesFromResult(result);

        assertNotNull(bytes);
        assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8}, new byte[]{bytes[0], bytes[1]});
        bitmap.recycle();
    }
}
