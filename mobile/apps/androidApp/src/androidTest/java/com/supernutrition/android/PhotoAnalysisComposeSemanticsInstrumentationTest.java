package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/** Physical-device semantic contract for the production Compose capture surface. */
public final class PhotoAnalysisComposeSemanticsInstrumentationTest {
    @Test
    public void captureSurfaceExposesStableAutomationIdsAndLabels() {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("the acceptance app must expose a launcher activity", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        targetContext.startActivity(launchIntent);

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertSemantic(device, "photo_analysis_title", "Capture");
        assertSemantic(device, "photo_analysis_status", "Ready to capture");
        assertSemantic(device, "capture_shutter", "Take meal photo");
        assertSemantic(device, "capture_import", "Choose photo");
    }

    private static void assertSemantic(UiDevice device, String id, String label) {
        UiObject2 node = device.wait(Until.findObject(By.desc(id)), 5_000L);
        assertNotNull("missing Compose semantic id: " + id, node);
        assertNotNull("missing Compose semantic label: " + label,
                device.wait(Until.findObject(By.text(label)), 5_000L));
    }
}
