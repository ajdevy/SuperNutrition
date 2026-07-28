package com.supernutrition.android;

import com.supernutrition.testing.scaffoldprobe.ScaffoldDependencyProbe;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Executes the non-production dependency probe inside the packaged Android test process. */
public final class ScaffoldDependencyProbeInstrumentationTest {
    @Test
    public void pinnedSubstrateMarkersArePresentOnAndroid() {
        ScaffoldDependencyProbe probe = new ScaffoldDependencyProbe();

        Set<String> expected = Set.of(
                "koin",
                "decompose",
                "ktor",
                "ktorfit",
                "sqldelight",
                "serialization",
                "coroutines"
        );
        assertEquals(expected, probe.dependencyMarkers());
    }
}
