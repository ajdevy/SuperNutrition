package com.supernutrition.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Pixel and occlusion checks run inside the packaged app's Android instrumentation. */
public final class BrandNavigationVisualInstrumentationTest {
    private static final String HOME_TAB = "capture_tab";
    private static final String REWARDS_TAB = "rewards_tab";
    private static final long TIMEOUT_MS = 15_000;

    @Test
    public void selectionChangesIconsAndKeepsLabels() throws IOException {
        UiDevice device = launchHome();
        Rect home = node(device, HOME_TAB).getVisibleBounds();
        Rect rewards = node(device, REWARDS_TAB).getVisibleBounds();
        String homeLabel = node(device, HOME_TAB).getContentDescription();
        String rewardsLabel = node(device, REWARDS_TAB).getContentDescription();
        Bitmap before = screenshot(device, "home_title", "device-home");
        node(device, REWARDS_TAB).click();
        node(device, "rewards_title");
        assertTrue(node(device, REWARDS_TAB).isSelected());
        assertTrue(!node(device, HOME_TAB).isSelected());
        Bitmap after = screenshot(device, "rewards_list", "device-rewards");
        assertEquals(home, node(device, HOME_TAB).getVisibleBounds());
        assertEquals(rewards, node(device, REWARDS_TAB).getVisibleBounds());
        assertEquals(homeLabel, node(device, HOME_TAB).getContentDescription());
        assertEquals(rewardsLabel, node(device, REWARDS_TAB).getContentDescription());
        for (Rect tab : new Rect[]{home, rewards}) {
            assertTrue("selected tab did not change appearance", changedPixels(before, after, tab) > 0);
        }
        before.recycle();
        after.recycle();
    }

    @Test
    public void heldTabKeepsItsTouchSurfaceTransparent() throws IOException {
        UiDevice device = launchHome();
        Rect tab = node(device, REWARDS_TAB).getVisibleBounds();
        Bitmap before = screenshot(device, "home_title", "device-before-hold");
        long downTime = SystemClock.uptimeMillis();
        inject(MotionEvent.ACTION_DOWN, downTime, tab.centerX(), tab.centerY());
        Bitmap held;
        try {
            SystemClock.sleep(450);
            held = screenshot(device, "home_title", "device-held");
        } finally {
            inject(MotionEvent.ACTION_UP, downTime, tab.centerX(), tab.centerY());
        }
        assertEquals("holding the tab paints a rectangular background", 0, changedPixels(before, held, tab));
        node(device, "rewards_title");
        assertTrue("the injected gesture did not activate its tab", node(device, REWARDS_TAB).isSelected());
        before.recycle();
        held.recycle();
    }

    @Test
    public void rewardsBackdropAndFinalCardClearFloatingNavigation() throws IOException {
        UiDevice device = launchHome();
        node(device, REWARDS_TAB).click();
        node(device, "rewards_title");
        float density = context().getResources().getDisplayMetrics().density;
        Bitmap initial = screenshot(device, "rewards_list", "device-rewards-backdrop");
        Rect camera = node(device, "camera_action").getVisibleBounds();
        int paper = 0xFFF6F7F5;
        assertEquals(paper, initial.getPixel(4, camera.centerY()));
        assertEquals(paper, initial.getPixel(initial.getWidth() - 5, camera.centerY()));
        initial.recycle();
        for (int attempt = 0; attempt < 6; attempt++) {
            UiObject2 card = device.findObject(By.res("rewards_row_4"));
            if (card != null && card.getVisibleBounds().height() > 0 && card.getVisibleBounds().bottom < camera.top) break;
            Rect list = node(device, "rewards_list").getVisibleBounds();
            device.swipe(list.centerX(), camera.top - Math.round(40 * density),
                    list.centerX(), list.top + Math.round(120 * density), 90);
            device.waitForIdle();
        }
        Rect weeklyCard = node(device, "rewards_row_4").getVisibleBounds();
        Rect rewardsViewport = node(device, "rewards_list").getVisibleBounds();
        for (int attempt = 0; attempt < 4; attempt++) {
            UiObject2 card = device.findObject(By.res("rewards_row_6"));
            if (card != null && card.getVisibleBounds().width() >= Math.round(145 * density)) break;
            device.swipe(rewardsViewport.centerX() + rewardsViewport.width() / 4, weeklyCard.centerY(),
                    rewardsViewport.centerX() - rewardsViewport.width() / 4, weeklyCard.centerY(), 90);
            device.waitForIdle();
        }
        Rect finalCard = node(device, "rewards_row_6").getVisibleBounds();
        Rect finalState = node(device, "rewards_row_6_state").getVisibleBounds();
        assertTrue("final reward is clipped horizontally", finalCard.width() >= Math.round(145 * density));
        assertTrue("final reward is hidden under the camera", finalCard.bottom < camera.top);
        assertTrue("final reward state is clipped", finalState.height() > 0 && finalState.bottom <= finalCard.bottom);
        screenshot(device, "rewards_list", "device-rewards-final").recycle();
        node(device, HOME_TAB).click();
        node(device, "home_title");
        assertTrue(node(device, HOME_TAB).isSelected());
    }

    private static UiDevice launchHome() {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Intent intent = context().getPackageManager().getLaunchIntentForPackage(context().getPackageName());
        assertNotNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        node(device, "home_title");
        assertTrue(node(device, HOME_TAB).isSelected());
        device.waitForIdle();
        return device;
    }

    private static UiObject2 node(UiDevice device, String id) {
        UiObject2 node = device.wait(Until.findObject(By.res(id)), TIMEOUT_MS);
        if (node == null) NativeUiSemanticDiagnostics.dump(device, id);
        assertNotNull("missing app resource " + id, node);
        return node;
    }

    private static Bitmap screenshot(UiDevice device, String surfaceId, String name) throws IOException {
        node(device, surfaceId);
        assertEquals("foreground changed; no screenshot", context().getPackageName(), device.getCurrentPackageName());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        assertEquals("foreground changed; screenshot discarded", context().getPackageName(), device.getCurrentPackageName());
        File directory = new File(context().getExternalFilesDir(null), "brand-navigation");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        return bitmap;
    }

    private static int changedPixels(Bitmap before, Bitmap after, Rect bounds) {
        int changed = 0;
        for (int y = bounds.top; y < bounds.bottom; y++) {
            for (int x = bounds.left; x < bounds.right; x++) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++;
            }
        }
        return changed;
    }

    private static void inject(int action, long downTime, int x, int y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event, true));
        } finally {
            event.recycle();
        }
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
