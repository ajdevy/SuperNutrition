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
import androidx.test.uiautomator.Until;

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
    public void initialLaunchOpensHomeSurface() throws RemoteException {
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
        assertSemantic(device, UiAutomationIds.INSTANCE.getHOME_TITLE().getValue(),
                targetContext.getString(R.string.home_title));
        assertNotNull("missing Home camera action semantic id",
                waitForNode(device, UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue()));
    }

    @Test
    public void homeAvatarOpensDesignedProfileAndBackReturnsHome() throws RemoteException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull(launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);
        ensureHomeSurface(device);
        waitForNode(device, UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue()).click();
        assertNotNull("Home must open the designed Profile with meal history",
                waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()));
        assertNull("Profile must hide the bottom navigation",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue())));
        waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_EDIT_NAME().getValue()).click();
        UiObject2 nameInput = device.wait(Until.findObject(By.clazz("android.widget.EditText")),
                SEMANTICS_TIMEOUT_MS);
        assertNotNull("Profile must offer a name editor", nameInput);
        nameInput.setText("Profile QA");
        UiObject2 saveName = device.wait(Until.findObject(By.text("Save")), SEMANTICS_TIMEOUT_MS);
        assertNotNull("Profile name editor must offer Save", saveName);
        saveName.click();
        assertSemantic(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue(), "Profile QA");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::recreate);
        device.waitForIdle();
        assertNotNull("Profile route must survive Activity recreation",
                waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()));
        assertSemantic(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue(), "Profile QA");
        waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_CLOSE().getValue()).click();
        ensureHomeSurface(device);
        waitForNode(device, UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue()).click();
        assertNotNull(waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()));
        assertSemantic(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue(), "Profile QA");
        device.pressBack();
        ensureHomeSurface(device);
    }

    @Test
    public void profileOpensMealHistoryAndBackPreservesProfile() throws RemoteException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wakeUp();
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull(launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent);
        ensureHomeSurface(device);
        waitForNode(device, UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue()).click();
        waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()).click();
        assertNotNull("Meal history must be a dedicated screen with search",
                waitForNode(device, UiAutomationIds.INSTANCE.getMEAL_HISTORY_SEARCH().getValue()));
        assertNotNull("An empty ledger must have an honest empty state",
                waitForNode(device, UiAutomationIds.INSTANCE.getMEAL_HISTORY_EMPTY().getValue()));
        assertNull("History hides the bottom navigation",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getCAMERA_ACTION().getValue())));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::recreate);
        assertNotNull("History survives Activity recreation", waitForNode(device, UiAutomationIds.INSTANCE.getMEAL_HISTORY_SEARCH().getValue()));
        device.pressBack();
        assertNotNull("System Back returns to Profile", waitForNode(device,
                UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()));
        waitForNode(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()).click();
        waitForNode(device, UiAutomationIds.INSTANCE.getMEAL_HISTORY_CLOSE().getValue()).click();
        assertNotNull("Header Back returns to Profile", waitForNode(device,
                UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()));
        device.pressBack();
        ensureHomeSurface(device);
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

        ensureHomeSurface(device);

        UiObject2 rewardsTab = waitForNode(device, UiAutomationIds.INSTANCE.getREWARDS_TAB().getValue());
        assertNotNull("missing Rewards tab semantic id", rewardsTab);
        rewardsTab.click();
        assertSelected(device, UiAutomationIds.INSTANCE.getREWARDS_TAB().getValue(),
                "Rewards tab must be selected after tapping it");
        assertSemantic(device, UiAutomationIds.INSTANCE.getREWARDS_TITLE().getValue(),
                targetContext.getString(R.string.rewards_title));

        UiObject2 homeTab = waitForNode(device, UiAutomationIds.INSTANCE.getHOME_TAB().getValue());
        assertNotNull("missing Home tab semantic id", homeTab);
        homeTab.click();
        assertSelected(device, UiAutomationIds.INSTANCE.getHOME_TAB().getValue(),
                "Home tab must be selected after tapping it");
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

        ensureHomeSurface(device);

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

        ensureHomeSurface(device);

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

        ensureHomeSurface(device);

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
        NativeUiSemanticDiagnostics.dump(device, id);
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
        NativeUiSemanticDiagnostics.dump(device, id);
        return null;
    }

    private static void ensureHomeSurface(UiDevice device) {
        assertNotNull("Home surface did not open on launch",
                waitForNode(device, UiAutomationIds.INSTANCE.getHOME_TITLE().getValue()));
    }

    private static void assertSelected(UiDevice device, String id, String message) {
        long deadline = SystemClock.uptimeMillis() + SEMANTICS_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 node = device.findObject(By.res(id));
            if (node != null) {
                try {
                    if (node.isSelected()) {
                        return;
                    }
                } catch (StaleObjectException ignored) {
                    // Compose may replace the semantics node during recomposition.
                }
            }
            SystemClock.sleep(100L);
        }
        NativeUiSemanticDiagnostics.dump(device, id);
        assertTrue(message, false);
    }

    private static String textFor(UiObject2 node) {
        String text = node.getText();
        if (text != null) {
            return text;
        }
        String contentDescription = node.getContentDescription();
        if (contentDescription != null) {
            return contentDescription;
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
