package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.supernutrition.core.ui.UiAutomationIds;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Real host wiring, independently of inference or a nutrition catalog. */
public final class MealDiaryJourneyInstrumentationTest {
    private static final long TIMEOUT_MS = 15_000L;

    @Test
    public void textMealCanBeSavedCorrectedAndDeletedThroughProfile() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        node(device, UiAutomationIds.INSTANCE.getHOME_ADD_FOOD().getValue()).click();
        openTextMealForm(device, context.getPackageName()).setText("Apple oatmeal");
        scrollTo(device, UiAutomationIds.INSTANCE.getMEAL_FORM_SAVE().getValue(), true).click();
        node(device, UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue()).click();
        node(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue()).click();
        node(device, UiAutomationIds.INSTANCE.mealHistoryRow(0).getValue()).click();
        assertEquals("Apple oatmeal", node(device, UiAutomationIds.INSTANCE.getMEAL_DETAILS_TITLE().getValue()).getText());
        node(device, UiAutomationIds.INSTANCE.getMEAL_DETAILS_NUTRITION_UNKNOWN().getValue());
        scrollTo(device, UiAutomationIds.INSTANCE.getMEAL_DETAILS_EDIT().getValue(), true).click();
        node(device, UiAutomationIds.INSTANCE.getMEAL_FORM_NAME().getValue()).setText("Corrected oatmeal");
        scrollTo(device, UiAutomationIds.INSTANCE.getMEAL_FORM_SAVE().getValue(), true).click();
        assertEquals("Corrected oatmeal", scrollTo(device,
                UiAutomationIds.INSTANCE.getMEAL_DETAILS_TITLE().getValue(), false).getText());
        scrollTo(device, UiAutomationIds.INSTANCE.getMEAL_DETAILS_DELETE().getValue(), true).click();
        node(device, UiAutomationIds.INSTANCE.getMEAL_DETAILS_DELETE_CONFIRM().getValue()).click();
        node(device, UiAutomationIds.INSTANCE.getMEAL_HISTORY_EMPTY().getValue());
    }

    private static UiObject2 openTextMealForm(UiDevice device, String targetPackage) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        String captureId = UiAutomationIds.INSTANCE.getCAPTURE_TEXT().getValue();
        String nameId = UiAutomationIds.INSTANCE.getMEAL_FORM_NAME().getValue();
        boolean textRequested = false;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                UiObject2 permission = device.findObject(
                        By.res(Pattern.compile("(?:.*:id/)?permission_deny_button")));
                if (permission != null && permission.isEnabled() && !permission.getVisibleBounds().isEmpty()) {
                    permission.click();
                    textRequested = false;
                } else if (targetPackage.equals(device.getCurrentPackageName())) {
                    UiObject2 name = device.findObject(By.res(nameId));
                    if (name != null && name.isEnabled() && !name.getVisibleBounds().isEmpty()) return name;
                    UiObject2 capture = device.findObject(By.res(captureId));
                    if (!textRequested && capture != null && capture.isEnabled()
                            && !capture.getVisibleBounds().isEmpty()) {
                        capture.click();
                        textRequested = true;
                    }
                }
            } catch (StaleObjectException ignored) {
                // Re-query controls replaced while the permission window or meal sheet changes.
            }
            long remaining = deadline - SystemClock.uptimeMillis();
            if (remaining > 0) SystemClock.sleep(Math.min(100L, remaining));
        }
        String missingId = textRequested ? nameId : captureId;
        NativeUiSemanticDiagnostics.dump(device, missingId);
        throw new AssertionError("Could not open text meal form: " + missingId);
    }

    private static UiObject2 node(UiDevice device, String id) {
        UiObject2 found = device.wait(Until.findObject(By.res(id)), TIMEOUT_MS);
        if (found == null) NativeUiSemanticDiagnostics.dump(device, id);
        assertNotNull("Missing semantic control: " + id, found);
        return found;
    }

    private static UiObject2 scrollTo(UiDevice device, String id, boolean downThePage) {
        device.waitForIdle();
        int minimumHeight = id.equals(UiAutomationIds.INSTANCE.getMEAL_DETAILS_TITLE().getValue()) ? 1
                : Math.round(48 * InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getResources().getDisplayMetrics().density);
        for (int attempt = 0; attempt < 10; attempt++) {
            UiObject2 found = device.findObject(By.res(id));
            if (found != null && found.getVisibleBounds().height() >= minimumHeight) {
                UiObject2 settled = settledNode(device, id, minimumHeight);
                if (settled != null) return settled;
            }
            int x = device.getDisplayWidth() / 2;
            int top = device.getDisplayHeight() / 4;
            int bottom = device.getDisplayHeight() * 3 / 4;
            device.swipe(x, downThePage ? bottom : top, x, downThePage ? top : bottom, 90);
            device.waitForIdle();
        }
        NativeUiSemanticDiagnostics.dump(device, id);
        throw new AssertionError("Could not reach a stationary visible control: " + id);
    }

    private static UiObject2 settledNode(UiDevice device, String id, int minimumHeight) {
        long deadline = SystemClock.uptimeMillis() + 3_000L;
        Rect previous = null;
        int stableSamples = 0;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 found = device.findObject(By.res(id));
            Rect bounds = found == null ? null : found.getVisibleBounds();
            if (bounds != null && bounds.height() >= minimumHeight && bounds.equals(previous)) {
                if (++stableSamples == 2) return found;
            } else {
                stableSamples = 0;
            }
            previous = bounds;
            SystemClock.sleep(200);
        }
        return null;
    }
}
