package com.supernutrition.android;

import com.supernutrition.feature.photoanalysis.PhotoAnalysisLiveCameraPreviewKt;

import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** A pending CameraX provider must leave the main thread free for permission and lifecycle events. */
public final class PhotoAnalysisCameraProviderReadinessInstrumentationTest {
    @Test
    public void pendingInitializationDoesNotReadTheFuture() throws Exception {
        RecordingFuture future = new RecordingFuture(false, false, false);
        assertNull(PhotoAnalysisLiveCameraPreviewKt.readyCameraProvider(future));
        assertEquals("pending initialization would block the UI thread", 0, future.reads);
    }

    @Test
    public void completedInitializationReturnsTheProvider() throws Exception {
        RecordingFuture future = new RecordingFuture(true, false, false);
        assertEquals("provider", PhotoAnalysisLiveCameraPreviewKt.readyCameraProvider(future));
        assertEquals(1, future.reads);
    }

    @Test(expected = ExecutionException.class)
    public void completedFailureReachesTheExistingRecoveryPath() throws Exception {
        PhotoAnalysisLiveCameraPreviewKt.readyCameraProvider(new RecordingFuture(true, true, false));
    }

    @Test(expected = CancellationException.class)
    public void cancelledInitializationReachesTheExistingRecoveryPath() throws Exception {
        PhotoAnalysisLiveCameraPreviewKt.readyCameraProvider(new RecordingFuture(true, false, true));
    }

    private static final class RecordingFuture implements Future<String> {
        private final boolean done;
        private final boolean failed;
        private final boolean cancelled;
        private int reads;

        RecordingFuture(boolean done, boolean failed, boolean cancelled) {
            this.done = done;
            this.failed = failed;
            this.cancelled = cancelled;
        }

        @Override public boolean isDone() { return done; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }

        @Override
        public String get() throws ExecutionException {
            reads++;
            if (!done) throw new AssertionError("attempted blocking read while provider initialization is pending");
            if (cancelled) throw new CancellationException();
            if (failed) throw new ExecutionException(new IllegalStateException("camera initialization failed"));
            return "provider";
        }

        @Override
        public String get(long timeout, TimeUnit unit) throws ExecutionException { return get(); }
    }
}
