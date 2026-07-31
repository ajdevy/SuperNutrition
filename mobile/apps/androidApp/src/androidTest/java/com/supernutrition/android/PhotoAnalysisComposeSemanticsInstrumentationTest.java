package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.supernutrition.core.ui.UiAutomationIds;
import com.supernutrition.feature.photoanalysis.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
        assertSemantic(device, UiAutomationIds.INSTANCE.getPHOTO_ANALYSIS_TITLE().getValue(),
                targetContext.getString(R.string.capture_title));
        assertSemantic(device, UiAutomationIds.INSTANCE.getPHOTO_ANALYSIS_STATUS().getValue(),
                targetContext.getString(R.string.capture_status_ready));
        assertSemantic(device, UiAutomationIds.INSTANCE.getCAPTURE_SHUTTER().getValue(),
                targetContext.getString(R.string.capture_shutter));
        assertSemantic(device, UiAutomationIds.INSTANCE.getCAPTURE_IMPORT().getValue(),
                targetContext.getString(R.string.capture_import));
    }

    private static void assertSemantic(UiDevice device, String id, String label) {
        UiObject2 node = device.wait(Until.findObject(By.res(id)), 5_000L);
        assertNotNull("missing Compose semantic id: " + id, node);
        if (node.getText() != null) {
            assertEquals("wrong label for Compose semantic id: " + id, label, node.getText());
        } else {
            assertNotNull(
                    "wrong label for Compose semantic id: " + id,
                    node.findObject(By.text(label))
            );
        }
    }
}
