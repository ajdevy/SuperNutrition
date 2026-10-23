package com.supernutrition.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Condition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.supernutrition.core.ui.UiAutomationIds;

import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Back cancels the optional draft after the native keyboard is dismissed. */
public final class ProfileNameEditingInstrumentationTest {
    private static final long TIMEOUT_MS = 15_000;

    @Test
    public void systemBackCancelsNameEditingAndPreservesProfile() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        node(device, UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue()).click();
        String name = visibleName(node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue()));
        String hardwareKeyboardSetting = device.executeShellCommand(
                "settings get secure show_ime_with_hard_keyboard").trim();
        assertTrue("Unexpected hardware-keyboard IME policy: " + hardwareKeyboardSetting,
                "0".equals(hardwareKeyboardSetting) || "1".equals(hardwareKeyboardSetting)
                        || "null".equals(hardwareKeyboardSetting));
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
            assertEquals("The keyboard fixture must allow the IME with a hardware keyboard", "1",
                    device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim());
            ImeObserver keyboard = new ImeObserver(activity);
            try {
                node(device, UiAutomationIds.INSTANCE.getPROFILE_EDIT_NAME().getValue()).click();
                node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME_INPUT().getValue()).setText("Unsaved QA draft");
                keyboard.setVisible(true);
                assertTrue("The name editor must exercise a shown, settled keyboard",
                        keyboard.awaitSettledVisibility(device, true));
                keyboard.setVisible(false);
                assertTrue("The keyboard must finish hiding before testing app Back",
                        keyboard.awaitSettledVisibility(device, false));
                node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME_INPUT().getValue());
                device.pressBack();
                assertTrue("Back must dismiss the draft editor", device.wait(Until.gone(
                        By.res(UiAutomationIds.INSTANCE.getPROFILE_NAME_INPUT().getValue())), TIMEOUT_MS));
                assertNull("Back must cancel editing without leaving Profile",
                        device.findObject(By.res(UiAutomationIds.INSTANCE.getHOME_PROFILE().getValue())));
                node(device, UiAutomationIds.INSTANCE.getPROFILE_HISTORY().getValue());
                assertEquals("cancel must keep the saved session name", name,
                        visibleName(node(device, UiAutomationIds.INSTANCE.getPROFILE_NAME().getValue())));
            } finally {
                keyboard.close();
            }
        } finally {
            restoreHardwareKeyboardSetting(device, hardwareKeyboardSetting);
        }
    }

    private static void restoreHardwareKeyboardSetting(UiDevice device, String value) throws IOException {
        if ("null".equals(value)) {
            device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard");
        } else if ("0".equals(value)) {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 0");
        } else {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
        }
        assertEquals("The test must restore the original hardware-keyboard IME policy", value,
                device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim());
    }

    /** A hide request and accessibility idle do not establish that native IME Back has finished. */
    private static final class ImeObserver extends WindowInsetsAnimationCompat.Callback {
        private final Activity activity;
        private final View root;
        private final Set<WindowInsetsAnimationCompat> preparedImeAnimations = new HashSet<>();
        private final Set<WindowInsetsAnimationCompat> runningImeAnimations = new HashSet<>();
        private final Set<OneShotPreDrawListener> pendingPreDrawListeners = new HashSet<>();
        private final Set<Runnable> pendingRetirements = new HashSet<>();
        private boolean closed;

        ImeObserver(Activity activity) {
            super(DISPATCH_MODE_CONTINUE_ON_SUBTREE);
            this.activity = activity;
            root = activity.getWindow().getDecorView();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    ViewCompat.setWindowInsetsAnimationCallback(root, this));
        }

        @Override
        public void onPrepare(WindowInsetsAnimationCompat animation) {
            if (closed || !isIme(animation)) {
                return;
            }
            preparedImeAnimations.add(animation);
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                retireUnstartedApi30AnimationAfterPreDraw(animation);
            }
        }

        @Override
        public WindowInsetsAnimationCompat.BoundsCompat onStart(WindowInsetsAnimationCompat animation,
                WindowInsetsAnimationCompat.BoundsCompat bounds) {
            trackRunningIme(animation);
            return bounds;
        }

        @Override
        public WindowInsetsCompat onProgress(WindowInsetsCompat insets,
                List<WindowInsetsAnimationCompat> runningAnimations) {
            for (WindowInsetsAnimationCompat animation : runningAnimations) {
                trackRunningIme(animation);
            }
            return insets;
        }

        @Override
        public void onEnd(WindowInsetsAnimationCompat animation) {
            preparedImeAnimations.remove(animation);
            runningImeAnimations.remove(animation);
        }

        void setVisible(boolean visible) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(activity.getWindow(), root);
                if (visible) {
                    controller.show(WindowInsetsCompat.Type.ime());
                } else {
                    controller.hide(WindowInsetsCompat.Type.ime());
                }
            });
        }

        boolean awaitSettledVisibility(UiDevice device, boolean visible) {
            Condition<UiDevice, Boolean> settled = ignored -> {
                boolean[] matches = {false};
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                    matches[0] = insets != null
                            && insets.isVisible(WindowInsetsCompat.Type.ime()) == visible
                            && preparedImeAnimations.isEmpty()
                            && runningImeAnimations.isEmpty();
                });
                return matches[0];
            };
            return Boolean.TRUE.equals(device.wait(settled, TIMEOUT_MS));
        }

        void close() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                closed = true;
                ViewCompat.setWindowInsetsAnimationCallback(root, null);
                for (OneShotPreDrawListener listener : pendingPreDrawListeners) {
                    listener.removeListener();
                }
                pendingPreDrawListeners.clear();
                for (Runnable retirement : pendingRetirements) {
                    root.removeCallbacks(retirement);
                }
                pendingRetirements.clear();
                preparedImeAnimations.clear();
                runningImeAnimations.clear();
            });
        }

        private void retireUnstartedApi30AnimationAfterPreDraw(WindowInsetsAnimationCompat animation) {
            OneShotPreDrawListener[] listener = {null};
            listener[0] = OneShotPreDrawListener.add(root, () -> {
                pendingPreDrawListeners.remove(listener[0]);
                if (closed) {
                    return;
                }
                Runnable retirement = new Runnable() {
                    @Override
                    public void run() {
                        pendingRetirements.remove(this);
                        if (!closed) {
                            preparedImeAnimations.remove(animation);
                        }
                    }
                };
                pendingRetirements.add(retirement);
                // Android registers its start listener after onPrepare. Wait for the whole traversal:
                // a started animation has moved to running; an API30 cancellation has no onEnd.
                root.post(retirement);
            });
            pendingPreDrawListeners.add(listener[0]);
        }

        private void trackRunningIme(WindowInsetsAnimationCompat animation) {
            if (!closed && isIme(animation)) {
                preparedImeAnimations.remove(animation);
                runningImeAnimations.add(animation);
            }
        }

        private static boolean isIme(WindowInsetsAnimationCompat animation) {
            return (animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0;
        }
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
