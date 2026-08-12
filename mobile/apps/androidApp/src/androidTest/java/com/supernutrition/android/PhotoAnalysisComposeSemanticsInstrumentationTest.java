package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.supernutrition.core.ui.UiAutomationIds;
import com.supernutrition.feature.photoanalysis.R;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Physical-device semantic contract for the Compose capture surface. */
public final class PhotoAnalysisComposeSemanticsInstrumentationTest {
    private static final long SEMANTICS_TIMEOUT_MS = 15_000L;

    @Test
    public void captureSurfaceExposesStableAutomationIdsAndLabels() throws RemoteException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.waitForIdle();

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("the acceptance app must expose a launcher activity", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);

        device.waitForIdle();
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
        long deadline = SystemClock.uptimeMillis() + SEMANTICS_TIMEOUT_MS;
        String text = null;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 node = device.findObject(By.res(id));
            if (node != null) {
                try {
                    text = textFor(node);
                    if (label.equals(text)) {
                        return;
                    }
                } catch (StaleObjectException ignored) {
                    // Compose may replace the semantics node during recomposition.
                }
            }
            SystemClock.sleep(100L);
        }
        assertNotNull("missing Compose semantic id: " + id, device.findObject(By.res(id)));
        assertEquals("wrong label for Compose semantic id: " + id, label, text);
    }

    private static String textFor(UiObject2 node) {
        String text = node.getText();
        if (text != null) {
            return text;
        }
        List<UiObject2> children = node.getChildren();
        for (UiObject2 child : children) {
            String childText = child.getText();
            if (childText != null) {
                return childText;
            }
        }
        return null;
    }
}
