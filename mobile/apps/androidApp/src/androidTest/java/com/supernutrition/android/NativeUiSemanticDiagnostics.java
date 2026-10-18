package com.supernutrition.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only failure evidence. Never changes flags, clears caches, takes a screenshot or retries an assertion. */
final class NativeUiSemanticDiagnostics {
    private static final String TAG = "SuperNutritionUiDiag";
    private static final int MAX_NODES = 256;
    private static final int MAX_DEPTH = 24;

    private NativeUiSemanticDiagnostics() {}

    static void dump(UiDevice device, String requestedId) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        StringBuilder report = new StringBuilder();
        report.append("requestedResource=").append(requestedId)
                .append(" sdk=").append(Build.VERSION.SDK_INT)
                .append(" targetSdk=").append(context.getApplicationInfo().targetSdkVersion)
                .append(" targetPackage=").append(context.getPackageName()).append('\n');
        try {
            report.append("foregroundPackage=").append(device.getCurrentPackageName()).append('\n');
            report.append(activitySnapshot(context));
            int automationFlags = Configurator.getInstance().getUiAutomationFlags();
            report.append("configuredUiAutomationFlags=").append(automationFlags).append('\n');
            UiAutomation automation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? instrumentation.getUiAutomation(automationFlags) : instrumentation.getUiAutomation();
            AccessibilityServiceInfo info = automation.getServiceInfo();
            if (info == null) {
                report.append("serviceInfo=null\n");
            } else {
                report.append("serviceFlags=").append(info.flags)
                        .append(" reportViewIds=").append(hasFlag(info, AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS))
                        .append(" includeNotImportant=")
                        .append(hasFlag(info, AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS))
                        .append(" retrieveWindows=")
                        .append(hasFlag(info, AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS))
                        .append(" eventTypes=").append(info.eventTypes)
                        .append(" packageFilter=").append(java.util.Arrays.toString(info.packageNames)).append('\n');
            }
            dumpRoot(report, "activeRoot", automation.getRootInActiveWindow(), context.getPackageName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SparseArray<List<AccessibilityWindowInfo>> displays = automation.getWindowsOnAllDisplays();
                for (int index = 0; index < displays.size(); index++) {
                    dumpWindows(report, displays.keyAt(index), displays.valueAt(index), context.getPackageName());
                }
            } else {
                dumpWindows(report, 0, automation.getWindows(), context.getPackageName());
            }
        } catch (RuntimeException error) {
            report.append("diagnosticError=").append(error).append('\n');
        }
        String content = report.toString();
        for (String line : content.split("\n")) Log.e(TAG, line);
        try {
            save(context, requestedId, content);
        } catch (RuntimeException error) {
            Log.e(TAG, "diagnosticFileError", error);
        }
    }

    private static boolean hasFlag(AccessibilityServiceInfo info, int flag) {
        return (info.flags & flag) != 0;
    }

    private static String activitySnapshot(Context context) {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("mainThreadSnapshot=notCompleted\n");
        new Handler(Looper.getMainLooper()).post(() -> {
            StringBuilder snapshot = new StringBuilder();
            try {
                AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
                snapshot.append("accessibilityEnabled=").append(manager.isEnabled())
                        .append(" touchExplorationEnabled=").append(manager.isTouchExplorationEnabled())
                        .append(" enabledServiceCount=")
                        .append(manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).size())
                        .append('\n');
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    View decor = activity.getWindow().getDecorView();
                    snapshot.append("resumedActivity=").append(activity.getClass().getName())
                            .append(" focus=").append(decor.hasWindowFocus())
                            .append(" finishing=").append(activity.isFinishing()).append('\n');
                    dumpViews(snapshot, decor, 0, new int[]{0});
                }
                result.set(snapshot.toString());
            } catch (RuntimeException error) {
                result.set("mainThreadSnapshotError=" + error + "\n");
            } finally {
                completed.countDown();
            }
        });
        try {
            completed.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return "mainThreadSnapshot=interrupted\n";
        }
        return result.get();
    }

    private static void dumpViews(StringBuilder report, View view, int depth, int[] count) {
        if (depth > MAX_DEPTH || count[0]++ >= MAX_NODES) return;
        report.append("view depth=").append(depth).append(" class=").append(view.getClass().getName())
                .append(" attached=").append(view.isAttachedToWindow()).append(" shown=").append(view.isShown())
                .append(" visibility=").append(view.getVisibility()).append(" size=").append(view.getWidth())
                .append('x').append(view.getHeight()).append(" importance=").append(view.getImportantForAccessibility())
                .append(" nodeProvider=").append(view.getAccessibilityNodeProvider() != null).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                dumpViews(report, group.getChildAt(index), depth + 1, count);
            }
        }
    }

    private static void dumpWindows(StringBuilder report, int display, List<AccessibilityWindowInfo> windows,
                                    String targetPackage) {
        report.append("display=").append(display).append(" windows=").append(windows.size()).append('\n');
        for (AccessibilityWindowInfo window : windows) {
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            report.append("window id=").append(window.getId()).append(" type=").append(window.getType())
                    .append(" active=").append(window.isActive()).append(" focused=").append(window.isFocused())
                    .append(" bounds=").append(bounds).append('\n');
            dumpRoot(report, "windowRoot", window.getRoot(), targetPackage);
        }
    }

    private static void dumpRoot(StringBuilder report, String name, AccessibilityNodeInfo root, String targetPackage) {
        if (root == null) {
            report.append(name).append("=null\n");
            return;
        }
        try {
            report.append(name).append(" package=").append(root.getPackageName())
                    .append(" windowId=").append(root.getWindowId()).append('\n');
            if (targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                dumpNodes(report, root, 0, new int[]{0});
            } else {
                report.append("nonTargetWindowLabelsOmitted=true\n");
            }
        } finally {
            root.recycle();
        }
    }

    private static void dumpNodes(StringBuilder report, AccessibilityNodeInfo node, int depth, int[] count) {
        if (depth > MAX_DEPTH || count[0]++ >= MAX_NODES) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        report.append("node depth=").append(depth).append(" id=").append(node.getViewIdResourceName())
                .append(" class=").append(node.getClassName()).append(" text=").append(node.getText())
                .append(" description=").append(node.getContentDescription())
                .append(" visible=").append(node.isVisibleToUser()).append(" important=")
                .append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? node.isImportantForAccessibility() : "api23")
                .append(" selected=").append(node.isSelected()).append(" bounds=").append(bounds)
                .append(" children=").append(node.getChildCount()).append('\n');
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                report.append("nullChild parentDepth=").append(depth).append(" index=").append(index).append('\n');
            } else {
                try {
                    dumpNodes(report, child, depth + 1, count);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private static void save(Context context, String requestedId, String content) {
        String additional = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir");
        File parent = additional == null ? context.getExternalFilesDir(null) : new File(additional);
        if (parent == null) {
            Log.e(TAG, "diagnosticFileSkipped=noOutputDirectory");
            return;
        }
        File folder = new File(parent, "native-semantic-diagnostics");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            Log.e(TAG, "diagnosticFileSkipped=cannotCreateOutputDirectory");
            return;
        }
        String name = "missing_" + requestedId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + SystemClock.uptimeMillis() + ".txt";
        File output = new File(folder, name);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
            Log.e(TAG, "diagnosticFile=" + output.getAbsolutePath());
        } catch (IOException error) {
            Log.e(TAG, "diagnosticFileError", error);
        }
    }
}
