package com.supernutrition.android;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.domain.photoanalysis.ImageOrientation;
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput;
import com.supernutrition.domain.inference.AttemptId;
import com.supernutrition.domain.inference.PhotoRef;
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Device-bound proof that EXIF orientation is applied and metadata is removed. */
public final class AndroidImageNormalizerFixtureTest {
    private static final String FIXTURE_SHA256 =
            "4371a3c8d33d1503cc8904b5a9e78687194daa5525c6d8322e4e7be3d619120c";

    @Test
    public void exifGpsFixtureNormalizesToBoundedMetadataFreePixels() throws Exception {
        byte[] source = fixtureBytes();
        assertEquals(FIXTURE_SHA256, sha256(source));
        assertTrue(contains(source, "Exif"));
        assertTrue(contains(source, "GPS"));

        PhotoRef ref = PhotoRef.Companion.issue(new AttemptId("fixture-attempt"), "fixture-photo");
        NormalizedPhotoInput normalized = new AndroidImageNormalizer().normalize(
                ref,
                source,
                ImageOrientation.RIGHT
        );

        assertEquals(4, normalized.getWidth());
        assertEquals(6, normalized.getHeight());
        assertEquals(ImageOrientation.UP, normalized.getOrientation());
        assertEquals("sRGB", normalized.getColorSpace());
        assertTrue(normalized.getMetadataKeys().isEmpty());
        assertEquals(64, normalized.getContentDigest().length());
        assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8}, new byte[]{
                normalized.getBytes()[0], normalized.getBytes()[1],
        });
        assertFalse(contains(normalized.getBytes(), "Exif"));
        assertFalse(contains(normalized.getBytes(), "GPS"));
        assertFalse(contains(normalized.getBytes(), "SuperNutrition fixture"));
    }

    @Test
    public void callerSuppliedOrientationIsApplied() throws Exception {
        PhotoRef ref = PhotoRef.Companion.issue(
                new AttemptId("fixture-default-orientation"),
                "fixture-photo"
        );

        NormalizedPhotoInput normalized = new AndroidImageNormalizer().normalize(
                ref,
                fixtureBytes(),
                ImageOrientation.RIGHT
        );

        assertEquals(4, normalized.getWidth());
        assertEquals(6, normalized.getHeight());
        assertEquals(ImageOrientation.UP, normalized.getOrientation());
    }

    @Test
    public void extremeBoundNeverCreatesZeroDimensionBitmap() throws Exception {
        PhotoRef ref = PhotoRef.Companion.issue(
                new AttemptId("fixture-minimum-bound"),
                "fixture-photo"
        );

        NormalizedPhotoInput normalized = new AndroidImageNormalizer(1).normalize(
                ref,
                fixtureBytes(),
                ImageOrientation.RIGHT
        );

        assertEquals(1, normalized.getWidth());
        assertEquals(1, normalized.getHeight());
    }

    private static byte[] fixtureBytes() throws Exception {
        InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getClassLoader()
                .getResourceAsStream("fixtures/meal-exif-gps.jpg.base64");
        if (stream == null) {
            throw new AssertionError("normalization fixture was not packaged");
        }
        String encoded;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        ))) {
            encoded = reader.lines()
                    .filter(line -> !line.trim().isEmpty())
                    .filter(line -> !line.trim().startsWith("#"))
                    .collect(Collectors.joining());
        }
        return Base64.getDecoder().decode(encoded);
    }

    private static boolean contains(byte[] bytes, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= bytes.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (bytes[i + j] != target[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
