package com.supernutrition.android;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** Minimal device-side launch/DI smoke for the non-distributable host. */
public final class BootstrapHostInstrumentationTest {
    @Test
    public void testApplicationStartsKoinBeforeTheHostIsUsed() {
        assertFalse("The app must start Koin only from Application.onCreate", AndroidBootstrap.start());
    }
}
