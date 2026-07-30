package com.supernutrition.android;

import android.os.Bundle;

import java.util.Arrays;
import java.util.HashSet;

import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot;
import com.supernutrition.domain.photoanalysis.RecoveryReason;
import com.supernutrition.domain.photoanalysis.RequestId;
import com.supernutrition.domain.inference.AttemptId;
import com.supernutrition.domain.inference.PhotoRef;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisSnapshotBundleCodec;
import com.supernutrition.platform.photoandroid.AndroidPhotoAttemptIds;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Device-bound process-recreation proof for the Android lifecycle codec. */
public final class PhotoAnalysisSnapshotBundleCodecInstrumentationTest {
    @Test
    public void everySnapshotVariantRoundTripsWithoutPhotoPayload() {
        AttemptId attempt = new AttemptId(AndroidPhotoAttemptIds.CAPTURE);
        PhotoRef ref = PhotoRef.Companion.issue(attempt, "opaque-photo");

        assertRoundTrip(PhotoAnalysisBackgroundSnapshot.Capture.INSTANCE, attempt);
        assertRoundTrip(new PhotoAnalysisBackgroundSnapshot.Preparing(ref), attempt);
        assertRoundTrip(new PhotoAnalysisBackgroundSnapshot.Ready(ref), attempt);
        assertRoundTrip(new PhotoAnalysisBackgroundSnapshot.Analyzing(ref, new RequestId("request-1")), attempt);
        assertRoundTrip(new PhotoAnalysisBackgroundSnapshot.CandidateReview(ref, new RequestId("request-2")), attempt);
        assertRoundTrip(new PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.CANCELLED, ref), attempt);
        assertRoundTrip(new PhotoAnalysisBackgroundSnapshot.Recovery(RecoveryReason.PERMISSION_DENIED, null), attempt);

        Bundle encoded = PhotoAnalysisSnapshotBundleCodec.encode(
                new PhotoAnalysisBackgroundSnapshot.Ready(ref),
                attempt
        );
        assertEquals(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_SCHEMA_VERSION,
                encoded.getInt(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_SCHEMA_VERSION_KEY));
        assertEquals("opaque-photo",
                encoded.getString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_OPAQUE_KEY));
        assertEquals(new HashSet<>(Arrays.asList(
                        PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_SCHEMA_VERSION_KEY,
                        PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_KIND_KEY,
                        PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_ATTEMPT_KEY,
                        PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_OPAQUE_KEY)),
                encoded.keySet());
    }

    @Test
    public void snapshotFromAnotherAttemptIsRejected() {
        AttemptId sourceAttempt = new AttemptId("source-attempt");
        AttemptId restoredAttempt = new AttemptId("restored-attempt");
        PhotoRef ref = PhotoRef.Companion.issue(sourceAttempt, "opaque-photo");
        Bundle encoded = PhotoAnalysisSnapshotBundleCodec.encode(
                new PhotoAnalysisBackgroundSnapshot.Ready(ref),
                sourceAttempt
        );

        assertNull(PhotoAnalysisSnapshotBundleCodec.decode(encoded, restoredAttempt));
        assertEquals(sourceAttempt.getValue(),
                encoded.getString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_ATTEMPT_KEY));
    }

    @Test
    public void malformedRequestIdIsRejectedWithoutThrowing() {
        AttemptId attempt = new AttemptId("malformed-request-attempt");
        Bundle encoded = new Bundle();
        encoded.putInt(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_SCHEMA_VERSION_KEY,
                PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_SCHEMA_VERSION);
        encoded.putString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_KIND_KEY,
                PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_ANALYZING);
        encoded.putString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_ATTEMPT_KEY, attempt.getValue());
        encoded.putString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_OPAQUE_KEY, "opaque-photo");
        encoded.putString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_REQUEST_KEY, "");

        assertNull(PhotoAnalysisSnapshotBundleCodec.decode(encoded, attempt));
    }

    @Test
    public void wrongBundleTypesAreRejectedWithoutThrowing() {
        AttemptId attempt = new AttemptId("wrong-type-attempt");
        Bundle malformed = new Bundle();
        malformed.putString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_SCHEMA_VERSION_KEY, "one");
        malformed.putString(PhotoAnalysisSnapshotBundleCodec.SNAPSHOT_ATTEMPT_KEY, attempt.getValue());

        assertNull(PhotoAnalysisSnapshotBundleCodec.decode(malformed, attempt));
    }

    private static void assertRoundTrip(
            PhotoAnalysisBackgroundSnapshot expected,
            AttemptId attempt
    ) {
        Bundle encoded = PhotoAnalysisSnapshotBundleCodec.encode(expected, attempt);
        assertEquals(expected, PhotoAnalysisSnapshotBundleCodec.decode(encoded, attempt));
    }
}
