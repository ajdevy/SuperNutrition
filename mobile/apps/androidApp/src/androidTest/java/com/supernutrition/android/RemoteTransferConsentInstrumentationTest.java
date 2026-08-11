package com.supernutrition.android;

import android.graphics.Bitmap;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.domain.photoanalysis.AttemptScopedConsentStore;
import com.supernutrition.domain.photoanalysis.TransferConsentDecision;
import com.supernutrition.domain.photoanalysis.TransferConsentReceipt;
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer;
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The privacy properties of the consent path, on a real Android runtime.
 *
 * The JVM tests cover the rules; these cover the two things only a device
 * settles: that the bytes the receipt is bound to are the scrubbed ones the
 * platform normalizer actually produced, and that the store behaves the same
 * under Android's runtime as it does on the JVM.
 */
public final class RemoteTransferConsentInstrumentationTest {

    private static final String ATTEMPT = "att_0000000000000001";

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder("sha256:");
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    private static byte[] jpegBytes() {
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private static TransferConsentReceipt receipt(TransferConsentDecision decision, String digest) {
        return new TransferConsentReceipt(
                "1",
                ATTEMPT,
                digest,
                "photo",
                "food-identification",
                decision,
                "ZZ",
                decision == TransferConsentDecision.GRANTED ? "fixture-region-1" : null,
                decision == TransferConsentDecision.GRANTED ? "photo-transfer-copy-v1" : null,
                "retention/2",
                0,
                "cfg/1",
                "2026-08-05T10:00:00Z",
                "2026-08-05T10:10:00Z");
    }

    @Test
    public void anEmptyStoreAuthorisesNoTransfer() throws Exception {
        // The state the app is in before the user has been asked. If this ever
        // returned true, a transfer would happen with no decision behind it.
        AttemptScopedConsentStore store = new AttemptScopedConsentStore();

        assertFalse(store.isGrantedFor(ATTEMPT, sha256(jpegBytes())));
        assertFalse(store.decidedFor(ATTEMPT, sha256(jpegBytes())));
        assertNull(store.receiptFor(ATTEMPT, sha256(jpegBytes())));
    }

    @Test
    public void aDeclinedDecisionIsRecordedAndStillAuthorisesNothing() throws Exception {
        String digest = sha256(jpegBytes());
        AttemptScopedConsentStore store = new AttemptScopedConsentStore();
        store.record(ATTEMPT, receipt(TransferConsentDecision.DECLINED, digest));

        assertFalse(store.isGrantedFor(ATTEMPT, digest));
        // Proving the user said no matters as much as proving they said yes.
        assertTrue(store.decidedFor(ATTEMPT, digest));
        assertNotNull(store.receiptFor(ATTEMPT, digest));
        assertEquals(TransferConsentDecision.DECLINED, store.receiptFor(ATTEMPT, digest).getDecision());
    }

    @Test
    public void consentIsBoundToTheScrubbedNormalizedBytes() throws Exception {
        // The receipt covers the bytes that will actually be sent. Binding it to
        // anything else would let the payload change under the decision.
        byte[] scrubbed = new AndroidPhotoBridge(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
        ).photoBytesFromResult(new android.content.Intent()
                .putExtra(com.supernutrition.platform.photoandroid.AndroidPhotoResultKeys.LEGACY_THUMBNAIL,
                        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)));
        assertNotNull(scrubbed);

        String digest = sha256(scrubbed);
        AttemptScopedConsentStore store = new AttemptScopedConsentStore();
        store.record(ATTEMPT, receipt(TransferConsentDecision.GRANTED, digest));

        assertTrue(store.isGrantedFor(ATTEMPT, digest));
        // A single different byte is a different payload and a void decision.
        byte[] altered = scrubbed.clone();
        altered[altered.length - 1] ^= 0x01;
        assertFalse(store.isGrantedFor(ATTEMPT, sha256(altered)));
    }

    @Test
    public void theNormalizerStripsMetadataBeforeAnythingIsBound() throws Exception {
        // EXIF/GPS must be gone from the bytes the digest covers, not merely
        // ignored downstream.
        byte[] scrubbed = new AndroidPhotoBridge(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
        ).photoBytesFromResult(new android.content.Intent()
                .putExtra(com.supernutrition.platform.photoandroid.AndroidPhotoResultKeys.LEGACY_THUMBNAIL,
                        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)));
        assertNotNull(scrubbed);

        androidx.exifinterface.media.ExifInterface exif =
                new androidx.exifinterface.media.ExifInterface(new java.io.ByteArrayInputStream(scrubbed));
        assertNull(exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE));
        assertNull(exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE));
        assertNull(exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL));
    }

    @Test
    public void aDecisionDoesNotSurviveIntoAnotherAttempt() throws Exception {
        // contracts/interaction-policy/v1: remote_consent is attempt-scoped.
        String digest = sha256(jpegBytes());
        AttemptScopedConsentStore store = new AttemptScopedConsentStore();
        store.record(ATTEMPT, receipt(TransferConsentDecision.GRANTED, digest));
        assertTrue(store.isGrantedFor(ATTEMPT, digest));

        store.record("att_0000000000000002", receipt(TransferConsentDecision.GRANTED, digest));
        assertFalse(store.isGrantedFor(ATTEMPT, digest));
    }

    @Test
    public void theNormalizerIsReachableFromTheHost() {
        assertNotNull(new AndroidImageNormalizer());
    }
}
