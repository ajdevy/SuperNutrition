package com.supernutrition.android;

import android.content.Intent;

import com.supernutrition.core.ui.CameraAvailability;
import com.supernutrition.core.ui.CameraPermissionState;
import com.supernutrition.domain.inference.AttemptId;
import com.supernutrition.domain.inference.PhotoRef;
import com.supernutrition.domain.photoanalysis.ImageOrientation;
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput;
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot;
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent;
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState;
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore;
import com.supernutrition.domain.photoanalysis.PhotoSessionStore;
import com.supernutrition.domain.photoanalysis.RecoveryReason;
import com.supernutrition.feature.photoanalysis.CaptureOperationState;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidHostController;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature;
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer;
import com.supernutrition.platform.photoandroid.AndroidPhotoBridgePort;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Collections;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Permission callbacks must prepare the viewfinder without acting as a shutter. */
public final class PhotoAnalysisCameraPermissionInstrumentationTest {
    private static final Object SHARED_PRODUCTION_PARSER = productionParserFixture();

    @Test
    public void grantingCameraPermissionDoesNotCapturePhoto() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.bridge.permission = CameraPermissionState.GRANTED;
        fixture.operation.setPermissionRequestInFlight(true);

        fixture.host.onPermissionResult(true);

        assertEquals("granting preview access must not press the shutter", 0, fixture.captures);
        assertNull("preview permission must not allocate a photo operation", fixture.operation.getPendingOperationId());
        assertFalse("the system request guard must clear after the result",
                fixture.operation.getPermissionRequestInFlight());
        assertEquals(PhotoAnalysisState.Capture.INSTANCE, fixture.feature.getState());
    }

    @Test
    public void firstCameraEntryRequestsPreviewPermissionOnlyOnce() {
        PermissionFixture fixture = new PermissionFixture();

        fixture.host.onCameraPreviewEntered();
        fixture.host.onCameraPreviewEntered();

        assertEquals(1, fixture.requests);
        assertEquals(0, fixture.captures);
        assertTrue(fixture.operation.getPermissionRequestInFlight());
        assertNull(fixture.operation.getPendingOperationId());
    }

    @Test
    public void alreadyGrantedCameraEntryDoesNotRequestOrCapture() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.bridge.permission = CameraPermissionState.GRANTED;

        assertEquals(CameraPermissionState.GRANTED, fixture.host.onCameraPreviewEntered());

        assertEquals(0, fixture.requests);
        assertEquals(0, fixture.captures);
        assertNull(fixture.operation.getPendingOperationId());
    }

    @Test
    public void cameraEntryWithoutHardwareDoesNotRequestUnusedPermission() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.bridge.availability = CameraAvailability.UNAVAILABLE;

        fixture.host.onCameraPreviewEntered();
        fixture.host.requestCameraPreviewAccess();

        assertEquals(0, fixture.requests);
        assertEquals(0, fixture.captures);
    }

    @Test
    public void decliningPreviewPermissionKeepsCaptureControlsAvailable() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.host.onCameraPreviewEntered();
        fixture.bridge.permission = CameraPermissionState.RATIONALE;

        fixture.host.onPermissionResult(false);
        fixture.host.onCameraPreviewEntered();

        assertEquals("denial must not automatically reopen the prompt", 1, fixture.requests);
        assertEquals(0, fixture.rationales);
        assertFalse(fixture.operation.getPermissionRequestInFlight());
        assertEquals(PhotoAnalysisState.Capture.INSTANCE, fixture.feature.getState());
        assertEquals(0, fixture.captures);
    }

    @Test
    public void previousRefusalDoesNotReopenPermissionOnCameraEntry() {
        for (CameraPermissionState permission : new CameraPermissionState[]{
                CameraPermissionState.RATIONALE,
                CameraPermissionState.DENIED,
                CameraPermissionState.PERMANENTLY_DENIED,
                CameraPermissionState.RESTRICTED
        }) {
            PermissionFixture fixture = new PermissionFixture();
            fixture.bridge.permission = permission;

            fixture.host.onCameraPreviewEntered();
            fixture.host.onCameraPreviewEntered();

            assertEquals(permission.name(), 0, fixture.requests);
            assertEquals(permission.name(), 0, fixture.rationales);
            assertEquals(permission.name(), 0, fixture.settings);
            assertEquals(permission.name(), 0, fixture.captures);
        }
    }

    @Test
    public void explicitRetryWaitsForRationaleAndSharesRequestGuard() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.bridge.permission = CameraPermissionState.RATIONALE;

        fixture.host.requestCameraPreviewAccess();

        assertEquals(1, fixture.rationales);
        assertEquals(0, fixture.requests);
        fixture.rationaleContinuation.invoke();
        fixture.host.requestCameraPreviewAccess();

        assertEquals(1, fixture.rationales);
        assertEquals(1, fixture.requests);
        assertEquals(0, fixture.captures);
    }

    @Test
    public void permanentRefusalOpensSettingsOnlyAfterExplicitAction() {
        for (CameraPermissionState permission : new CameraPermissionState[]{
                CameraPermissionState.PERMANENTLY_DENIED,
                CameraPermissionState.RESTRICTED
        }) {
            PermissionFixture fixture = new PermissionFixture();
            fixture.bridge.permission = permission;
            fixture.host.onCameraPreviewEntered();
            assertEquals(0, fixture.settings);

            fixture.host.requestCameraPreviewAccess();

            assertEquals(1, fixture.settings);
            assertEquals(0, fixture.requests);
            assertEquals(0, fixture.captures);
        }
    }

    @Test
    public void explicitRetryAfterDenialRequestsPermissionWithoutCapture() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.bridge.permission = CameraPermissionState.DENIED;

        fixture.host.requestCameraPreviewAccess();

        assertEquals(1, fixture.requests);
        assertEquals(0, fixture.captures);
    }

    @Test
    public void settingsGrantClearsPermissionRecoveryWithoutCapture() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.feature.dispatch(PhotoAnalysisEvent.PermissionDenied.INSTANCE);
        fixture.bridge.permission = CameraPermissionState.GRANTED;

        assertEquals(CameraPermissionState.GRANTED, fixture.host.refreshCameraPermission());

        assertEquals(PhotoAnalysisState.Capture.INSTANCE, fixture.feature.getState());
        assertEquals(0, fixture.captures);
        assertNull(fixture.operation.getPendingOperationId());
    }

    @Test
    public void returningFromSettingsWithoutGrantPreservesPermissionRecovery() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.feature.dispatch(PhotoAnalysisEvent.PermissionDenied.INSTANCE);
        PhotoAnalysisState before = fixture.feature.getState();
        fixture.bridge.permission = CameraPermissionState.PERMANENTLY_DENIED;

        assertEquals(CameraPermissionState.PERMANENTLY_DENIED, fixture.host.refreshCameraPermission());

        assertSame(before, fixture.feature.getState());
        assertEquals(0, fixture.requests);
        assertEquals(0, fixture.captures);
    }

    @Test
    public void permissionRefreshPreservesEveryOtherRecoveryReason() {
        for (RecoveryReason reason : RecoveryReason.values()) {
            if (reason == RecoveryReason.PERMISSION_DENIED) continue;
            PermissionFixture fixture = new PermissionFixture();
            fixture.feature.dispatch(new PhotoAnalysisEvent.RestoreFromSnapshot(
                    new PhotoAnalysisBackgroundSnapshot.Recovery(reason, null)));
            PhotoAnalysisState before = fixture.feature.getState();
            fixture.bridge.permission = CameraPermissionState.GRANTED;

            fixture.host.refreshCameraPermission();

            assertSame(reason.name(), before, fixture.feature.getState());
            assertEquals(0, fixture.captures);
        }
    }

    @Test
    public void permissionRefreshPreservesAnExistingPhoto() {
        PermissionFixture fixture = new PermissionFixture();
        PhotoRef ref = fixture.feature.issuePhotoRef("retained-photo");
        NormalizedPhotoInput input = new NormalizedPhotoInput(
                ref, new byte[]{1}, 1, 1, ImageOrientation.UP, "sRGB", Collections.emptySet(),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        fixture.feature.dispatch(new PhotoAnalysisEvent.PhotoCaptured(ref));
        fixture.feature.dispatch(new PhotoAnalysisEvent.PreparationSucceeded(input));
        PhotoAnalysisState before = fixture.feature.getState();
        fixture.bridge.permission = CameraPermissionState.GRANTED;

        fixture.host.onPermissionResult(true);
        fixture.host.refreshCameraPermission();

        assertSame(before, fixture.feature.getState());
        assertArrayEquals(new byte[]{1}, ((PhotoAnalysisState.Ready) before).getInput().getBytes());
        assertEquals(0, fixture.captures);
    }

    @Test
    public void restoredReviewRecoveryDoesNotAutomaticallyRequestCameraPermission() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.feature.dispatch(new PhotoAnalysisEvent.RestoreFromSnapshot(
                new PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.REVIEW_RESTORE_UNAVAILABLE, null)));
        PhotoAnalysisState before = fixture.feature.getState();
        assertEquals(new PhotoAnalysisState.Recovery(RecoveryReason.REVIEW_RESTORE_UNAVAILABLE, null), before);
        assertEquals(CameraPermissionState.NOT_DETERMINED, fixture.bridge.cameraPermissionState());

        fixture.host.onCameraPreviewEntered();

        assertEquals("restored review recovery must not request live camera access", 0, fixture.requests);
        assertSame("permission entry must preserve the restored recovery", before, fixture.feature.getState());
        assertEquals(0, fixture.captures);
    }

    @Test
    public void backgroundedCaptureDoesNotAutomaticallyRequestCameraPermission() {
        PermissionFixture fixture = new PermissionFixture();
        fixture.feature.dispatch(PhotoAnalysisEvent.Backgrounded.INSTANCE);
        PhotoAnalysisState before = fixture.feature.getState();
        assertTrue(before instanceof PhotoAnalysisState.Backgrounded);
        assertEquals(PhotoAnalysisState.Capture.INSTANCE, fixture.feature.currentWorkState());
        assertEquals(CameraPermissionState.NOT_DETERMINED, fixture.bridge.cameraPermissionState());

        fixture.host.onCameraPreviewEntered();

        assertEquals("backgrounded Capture must not launch a permission request", 0, fixture.requests);
        assertSame("permission entry must preserve the backgrounded renderer", before, fixture.feature.getState());
        assertEquals(0, fixture.captures);
    }

    private static final class PermissionFixture {
        final PermissionBridge bridge = new PermissionBridge();
        final PhotoAnalysisFeature feature = featureFixture();
        final CaptureOperationState operation = new CaptureOperationState();
        int captures;
        int requests;
        int settings;
        int rationales;
        Function0<Unit> rationaleContinuation;
        final PhotoAnalysisAndroidHostController host = new PhotoAnalysisAndroidHostController(
                feature,
                bridge,
                new AndroidImageNormalizer(),
                () -> { requests++; return Unit.INSTANCE; },
                onContinue -> { rationales++; rationaleContinuation = onContinue; return Unit.INSTANCE; },
                operationId -> { captures++; return Unit.INSTANCE; },
                () -> Unit.INSTANCE,
                () -> { settings++; return Unit.INSTANCE; },
                operation,
                photo -> Unit.INSTANCE,
                () -> Unit.INSTANCE,
                null
        );
    }

    private static PhotoAnalysisFeature featureFixture() {
        // Keep the four explicit fixture dependencies; let Kotlin supply any
        // trailing optional references without coupling this helper to its arity.
        for (Constructor<?> constructor : PhotoAnalysisFeature.class.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length >= 6 && parameterTypes[parameterTypes.length - 1].getName()
                    .equals("kotlin.jvm.internal.DefaultConstructorMarker")) {
                Object[] arguments = new Object[parameterTypes.length];
                arguments[0] = new AttemptId("camera-permission-test");
                arguments[1] = new PhotoLeaseStore();
                arguments[2] = new PhotoSessionStore();
                arguments[3] = SHARED_PRODUCTION_PARSER;
                arguments[parameterTypes.length - 2] = -1 << 4;
                try {
                    return (PhotoAnalysisFeature) constructor.newInstance(arguments);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("unable to create the permission feature fixture", error);
                }
            }
        }
        throw new AssertionError("missing Kotlin default constructor for PhotoAnalysisFeature");
    }

    private static Object productionParserFixture() {
        // Permission fixtures need separate feature state, not repeated immutable catalog indexes.
        // Reflect the existing factory to retain the Java test's current dependency boundary.
        try {
            return Class.forName("com.supernutrition.domain.nutrition.ManualIngredientParserKt")
                    .getMethod("productionManualIngredientParser")
                    .invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("unable to create the shared production parser fixture", error);
        }
    }

    private static final class PermissionBridge implements AndroidPhotoBridgePort {
        CameraPermissionState permission = CameraPermissionState.NOT_DETERMINED;
        CameraAvailability availability = CameraAvailability.AVAILABLE;
        boolean requestInFlight;

        @Override
        public CameraPermissionState cameraPermissionState() {
            if (permission == CameraPermissionState.GRANTED) return permission;
            return requestInFlight ? CameraPermissionState.NOT_DETERMINED : permission;
        }

        @Override
        public CameraAvailability cameraAvailability() {
            return availability;
        }

        @Override
        public Intent cameraCaptureIntent(String operationId) {
            return new Intent();
        }

        @Override
        public Intent photoPickerIntent() {
            return new Intent();
        }

        @Override
        public Intent settingsIntent() {
            return new Intent();
        }

        @Override
        public int cleanupStaleCaptureFiles() {
            return 0;
        }

        @Override
        public boolean isCaptureFailure(Intent result) {
            return false;
        }

        @Override
        public byte[] photoBytesFromResult(Intent result) {
            return null;
        }

        @Override
        public void markCameraPermissionRequestStarted() {
            requestInFlight = true;
        }

        @Override
        public void markCameraPermissionRequestCompleted() {
            requestInFlight = false;
        }
    }
}
