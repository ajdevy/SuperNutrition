package com.supernutrition.android;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidCatalogProvider;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Minimal device-side launch/DI smoke for the non-distributable host. */
public final class BootstrapHostInstrumentationTest {
    @Test
    public void productionApplicationProvidesCatalogFactory() {
        Object application = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue("The production application must provide the bundled catalog factory",
                application instanceof PhotoAnalysisAndroidCatalogProvider);
    }

    @Test
    public void testApplicationStartsKoinBeforeTheHostIsUsed() {
        assertFalse("The app must start Koin only from Application.onCreate", AndroidBootstrap.start());
    }
}
