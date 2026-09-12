package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Physical-device semantic contract for the Compose photo-analysis shell. */
public final class PhotoAnalysisComposeSemanticsInstrumentationTest {
    private static final long SEMANTICS_TIMEOUT_MS = 15_000L;

    @Test
    public void initialLaunchOpensCaptureSurface() throws RemoteException {
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
        assertNotNull("missing initial capture close action semantic id",
                waitForNode(device, UiAutomationIds.INSTANCE.getCAPTURE_CLOSE().getValue()));
        assertNull("bottom tab bar must be hidden while initial capture is open",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue())));
    }

    @Test
    public void profileLinkOpensProfileSurface() throws RemoteException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.waitForIdle();

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent profileIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://supernutrition.app/profile/alice_123"));
        profileIntent.setPackage(targetContext.getPackageName());
        profileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(profileIntent);

        assertSemantic(device, UiAutomationIds.INSTANCE.getPROFILE_TITLE().getValue(),
                targetContext.getString(R.string.profile_title));
        assertSemantic(device, UiAutomationIds.INSTANCE.getPROFILE_ID().getValue(), "alice_123");
        assertNotNull("missing profile close semantic id",
                waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_CLOSE().getValue()));
        assertNull("bottom tab bar must be hidden on the profile destination",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue())));
    }

    @Test
    public void firstTabOpensHomeAfterRewardsSelection() throws RemoteException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.waitForIdle();

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("the acceptance app must expose a launcher activity", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);

        closeInitialCapture(device);

        UiObject2 rewardsTab = waitForNode(device, UiAutomationIds.INSTANCE.getREWARDS_TAB().getValue());
        assertNotNull("missing Rewards tab semantic id", rewardsTab);
        rewardsTab.click();
        assertTrue("Rewards tab must be selected after tapping it", rewardsTab.isSelected());
        assertSemantic(device, UiAutomationIds.INSTANCE.getREWARDS_TITLE().getValue(),
                targetContext.getString(R.string.rewards_title));

        UiObject2 homeTab = waitForNode(device, UiAutomationIds.INSTANCE.getHOME_TAB().getValue());
        assertNotNull("missing Home tab semantic id", homeTab);
        homeTab.click();
        assertTrue("Home tab must be selected after tapping it", homeTab.isSelected());
        assertSemantic(device, UiAutomationIds.INSTANCE.getHOME_TITLE().getValue(),
                targetContext.getString(R.string.home_title));
    }

    @Test
    public void centerCameraActionKeepsCaptureSurfaceAvailable() throws RemoteException, IOException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.waitForIdle();

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("the acceptance app must expose a launcher activity", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);

        closeInitialCapture(device);

        UiObject2 cameraAction = waitForNode(device, UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue());
        assertNotNull("missing centered camera action semantic id", cameraAction);
        device.executeShellCommand("pm grant " + targetContext.getPackageName() + " android.permission.CAMERA");
        cameraAction.click();
        assertSemantic(device, UiAutomationIds.INSTANCE.getPHOTO_ANALYSIS_TITLE().getValue(),
                targetContext.getString(R.string.capture_title));
        assertSemantic(device, UiAutomationIds.INSTANCE.getCAPTURE_SHUTTER().getValue(),
                targetContext.getString(R.string.capture_shutter));
        assertSemantic(device, UiAutomationIds.INSTANCE.getCAPTURE_IMPORT().getValue(),
                targetContext.getString(R.string.capture_import));
        assertNotNull("missing capture close action semantic id",
                waitForNode(device, UiAutomationIds.INSTANCE.getCAPTURE_CLOSE().getValue()));
        assertNull("bottom tab bar must be hidden while capture is open",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue())));

        device.findObject(By.res(UiAutomationIds.INSTANCE.getCAPTURE_CLOSE().getValue())).click();
        assertSemantic(device, UiAutomationIds.INSTANCE.getHOME_TITLE().getValue(),
                targetContext.getString(R.string.home_title));
    }

    @Test
    public void backFromCaptureReturnsToHomeWithoutBottomBarInCapture() throws RemoteException, IOException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.waitForIdle();

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("the acceptance app must expose a launcher activity", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);

        closeInitialCapture(device);

        UiObject2 cameraAction = waitForNode(device, UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue());
        assertNotNull("missing centered camera action semantic id", cameraAction);
        device.executeShellCommand("pm grant " + targetContext.getPackageName() + " android.permission.CAMERA");
        cameraAction.click();
        assertNotNull("capture surface did not open", waitForNode(
                device, UiAutomationIds.INSTANCE.getPHOTO_ANALYSIS_TITLE().getValue()));
        assertNull("bottom tab bar must be hidden while capture is open",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue())));

        device.pressBack();
        assertSemantic(device, UiAutomationIds.INSTANCE.getHOME_TITLE().getValue(),
                targetContext.getString(R.string.home_title));
    }

    @Test
    public void homeAddMealActionOpensCaptureSurface() throws RemoteException, IOException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        device.waitForIdle();

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("the acceptance app must expose a launcher activity", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);

        closeInitialCapture(device);

        UiObject2 addFood = waitForNode(device, UiAutomationIds.INSTANCE.getHOME_ADD_FOOD().getValue());
        assertNotNull("missing Home add-meal semantic id", addFood);
        device.executeShellCommand("pm grant " + targetContext.getPackageName() + " android.permission.CAMERA");
        addFood.click();
        assertSemantic(device, UiAutomationIds.INSTANCE.getPHOTO_ANALYSIS_TITLE().getValue(),
                targetContext.getString(R.string.capture_title));
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

    private static UiObject2 waitForNode(UiDevice device, String id) {
        long deadline = SystemClock.uptimeMillis() + SEMANTICS_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 node = device.findObject(By.res(id));
            if (node != null) {
                return node;
            }
            SystemClock.sleep(100L);
        }
        return null;
    }

    private static void closeInitialCapture(UiDevice device) {
        UiObject2 close = waitForNode(device, UiAutomationIds.INSTANCE.getCAPTURE_CLOSE().getValue());
        assertNotNull("initial capture surface did not open", close);
        close.click();
        assertNotNull("Home surface did not open after closing initial capture",
                waitForNode(device, UiAutomationIds.INSTANCE.getHOME_TITLE().getValue()));
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
