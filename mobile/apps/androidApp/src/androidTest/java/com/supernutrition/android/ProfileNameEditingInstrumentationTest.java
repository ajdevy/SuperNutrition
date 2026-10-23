package com.supernutrition.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.supernutrition.core.ui.UiAutomationIds;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Back cancels the optional draft after the native keyboard is dismissed. */
public final class ProfileNameEditingInstrumentationTest {
    private static final long TIMEOUT_MS = 15_000;

    @Test
    public void systemBackCancelsNameEditingAndPreservesProfile() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        node(device, UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue()).click();
        String name = visibleName(node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue()));
        node(device, UiAutomationIds.INSTANCE.getPROFILE_EDIT_NAME().getValue()).click();
        node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME_INPUT().getValue()).setText("Unsaved QA draft");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        });
        device.waitForIdle();
        device.pressBack();
        assertTrue("Back must dismiss the draft editor", device.wait(Until.gone(
                By.res(UiAutomationIds.INSTANCE.getPROFILE_NAME_INPUT().getValue())), TIMEOUT_MS));
        assertNull("Back must cancel editing without leaving Profile",
                device.findObject(By.res(UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue())));
        node(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue());
        assertEquals("cancel must keep the saved session name", name,
                visibleName(node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue())));
    }

    private static String visibleName(UiObject2 node) {
        return node.getText() == null ? node.getContentDescription() : node.getText();
    }

    private static UiObject2 node(UiDevice device, String id) {
        UiObject2 node = device.wait(Until.findObject(By.res(id)), TIMEOUT_MS);
        assertNotNull("missing app resource " + id, node);
        return node;
    }
}
