package com.supernutrition.android;

import android.os.Bundle;

import com.supernutrition.domain.photoanalysis.AttemptId;
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot;
import com.supernutrition.domain.photoanalysis.PhotoRef;
import com.supernutrition.domain.photoanalysis.RecoveryReason;
import com.supernutrition.domain.photoanalysis.RequestId;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisSnapshotBundleCodec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/** Device-bound process-recreation proof for the Android lifecycle codec. */
public final class PhotoAnalysisSnapshotBundleCodecInstrumentationTest {
    private static final String ATTEMPT_KEY = "photo_analysis.snapshot.attempt";
    private static final String URI_KEY = "photo_analysis.snapshot.uri";
    private static final String BYTES_KEY = "photo_analysis.snapshot.bytes";

    @Test
    public void everySnapshotVariantRoundTripsWithoutPhotoPayload() {
        AttemptId attempt = new AttemptId("android-photo-attempt");
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
        assertFalse(encoded.containsKey(URI_KEY));
        assertFalse(encoded.containsKey(BYTES_KEY));
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
        assertEquals(sourceAttempt.getValue(), encoded.getString(ATTEMPT_KEY));
    }

    @Test
    public void malformedRequestIdIsRejectedWithoutThrowing() {
        AttemptId attempt = new AttemptId("malformed-request-attempt");
        Bundle encoded = new Bundle();
        encoded.putString("photo_analysis.snapshot.kind", "analyzing");
        encoded.putString(ATTEMPT_KEY, attempt.getValue());
        encoded.putString("photo_analysis.snapshot.opaque", "opaque-photo");
        encoded.putString("photo_analysis.snapshot.request", "");

        assertNull(PhotoAnalysisSnapshotBundleCodec.decode(encoded, attempt));
    }

    private static void assertRoundTrip(
            PhotoAnalysisBackgroundSnapshot expected,
            AttemptId attempt
    ) {
        Bundle encoded = PhotoAnalysisSnapshotBundleCodec.encode(expected, attempt);
        assertEquals(expected, PhotoAnalysisSnapshotBundleCodec.decode(encoded, attempt));
    }
}
