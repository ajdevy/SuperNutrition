package com.supernutrition.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;

import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Activity destruction must release the retained host's outstanding shutter operation. */
public final class PhotoAnalysisInlineCameraLifecycleInstrumentationTest {
    @Test
    public void restoredBundleDoesNotResurrectAnUnownedShutterOperation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launch);
        try {
            Method stateGetter = activity.getClass().getDeclaredMethod("getRetainedState");
            stateGetter.setAccessible(true);
            Object retained = stateGetter.invoke(activity);
            Object operation = retained.getClass().getMethod("getCaptureOperation").invoke(retained);
            Bundle saved = new Bundle();
            saved.putString("photo_analysis.pending_operation_id", "orphaned-photo");
            Method restore = activity.getClass().getDeclaredMethod("restoreActivityState", Bundle.class);
            restore.setAccessible(true);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    restore.invoke(activity, saved);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(error);
                }
            });
            assertNull("a saved token has no owning inline controller after recreation",
                    operation.getClass().getMethod("getPendingOperationId").invoke(operation));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    @Test
    public void clearingQueuedCaptureDeliversOneFailureBeforeDetachingItsCallback() throws Exception {
        InlineFixture fixture = new InlineFixture();
        fixture.call("requestCapture", "queued-photo");

        fixture.call("clear");
        fixture.call("clear");

        assertEquals(1, fixture.operations.size());
        assertEquals("queued-photo", fixture.operations.get(0));
        assertTrue(fixture.failures.get(0));
        assertNull(fixture.call("getOnCaptureResult"));
    }

    @Test
    public void clearingStartedCaptureIgnoresItsLateCameraXResult() throws Exception {
        List<Object> callbacks = new ArrayList<>();
        InlineFixture fixture = new InlineFixture(callbacks);
        Class<?> imageCaptureType = Class.forName("androidx.camera.core.ImageCapture");
        Object builder = Class.forName("androidx.camera.core.ImageCapture$Builder").getConstructor().newInstance();
        Object capture = builder.getClass().getMethod("build").invoke(builder);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                fixture.type.getMethod("bind", Context.class, imageCaptureType).invoke(
                        fixture.controller, InstrumentationRegistry.getInstrumentation().getTargetContext(), capture);
                fixture.call("requestCapture", "started-photo");
                assertEquals("the native port callback must still be outstanding", 1, callbacks.size());
                fixture.call("clear");
                Class<?> errorType = Class.forName("androidx.camera.core.ImageCaptureException");
                Object error = errorType.getConstructor(int.class, String.class, Throwable.class)
                        .newInstance(3, "late camera failure", null);
                Class.forName("androidx.camera.core.ImageCapture$OnImageSavedCallback")
                        .getMethod("onError", errorType).invoke(callbacks.get(0), error);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals("interruption and a late result must complete only once", 1, fixture.operations.size());
        assertEquals("started-photo", fixture.operations.get(0));
        assertTrue(fixture.failures.get(0));
    }

    @Test
    public void clearingWithoutCaptureDoesNotInventAnOperation() throws Exception {
        InlineFixture fixture = new InlineFixture();

        fixture.call("clear");

        assertTrue(fixture.operations.isEmpty());
        assertNull(fixture.call("getOnCaptureResult"));
    }

    private static final class InlineFixture {
        final Class<?> type = Class.forName(
                "com.supernutrition.feature.photoanalysis.PhotoAnalysisInlineCameraController");
        final Object controller;
        final List<String> operations = new ArrayList<>();
        final List<Boolean> failures = new ArrayList<>();

        InlineFixture() throws Exception {
            this(null);
        }

        InlineFixture(List<Object> callbacks) throws Exception {
            if (callbacks == null) {
                controller = type.getConstructor().newInstance();
            } else {
                Class<?> port = Class.forName("com.supernutrition.feature.photoanalysis.InlineCameraCapturePort");
                Object deferredCapture = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port},
                        (proxy, method, arguments) -> {
                            callbacks.add(arguments[3]);
                            return null;
                        });
                controller = type.getConstructor(port).newInstance(deferredCapture);
            }
            Function2<String, Object, Unit> callback = (id, result) -> {
                operations.add(id);
                Object payload = result;
                if (result instanceof kotlin.Result<?>) {
                    try {
                        payload = result.getClass().getMethod("unbox-impl").invoke(result);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                }
                try {
                    ResultKt.throwOnFailure(payload);
                    failures.add(false);
                } catch (Throwable error) {
                    failures.add(true);
                }
                return Unit.INSTANCE;
            };
            type.getMethod("setOnCaptureResult", Function2.class).invoke(controller, callback);
        }

        Object call(String name, String... arguments) throws Exception {
            if (arguments.length == 0) return type.getMethod(name).invoke(controller);
            return type.getMethod(name, String.class).invoke(controller, arguments[0]);
        }
    }
}
