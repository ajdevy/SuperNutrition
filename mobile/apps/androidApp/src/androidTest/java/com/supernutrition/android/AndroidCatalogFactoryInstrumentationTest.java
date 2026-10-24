package com.supernutrition.android;

import android.content.Context;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.supernutrition.feature.photoanalysis.MealCatalogFactory;
import com.supernutrition.feature.photoanalysis.MealCatalogHandle;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import kotlinx.coroutines.Dispatchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/** Real catalog databases only; these fixtures never open or mutate private user documents. */
public final class AndroidCatalogFactoryInstrumentationTest {
    @Test
    public void detachedApplicationCreatesOnlyACheapFlavorScopedFactory() {
        BootstrapApplication unattached = new BootstrapApplication();
        MealCatalogFactory factory = unattached.getPhotoAnalysisCatalogFactory();
        assertNotNull(factory);
        assertSame(factory, unattached.getPhotoAnalysisCatalogFactory());
        assertSame(Dispatchers.getIO(), unattached.getPhotoAnalysisCatalogBackgroundDispatcher());
        boolean production = "production".equals(BuildConfig.FLAVOR);
        assertEquals(production, factory instanceof AndroidBundledUsdaCatalogFactory);
        if (!production) {
            // Unattached Context cannot provide storage. Isolation must succeed without accessing it.
            assertNull(factory.open());
        }
    }

    @Test
    public void openingOnMainIsRejectedBeforeAnyCatalogWork() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidBundledUsdaCatalogFactory factory = new AndroidBundledUsdaCatalogFactory(context);
        onMain(() -> {
            try {
                factory.open();
                fail("Catalog verification and database creation must never block Main");
            } catch (IllegalStateException expected) {
                // The background-only factory contract is enforced before opening a database.
            }
        });
    }

    @Test
    public void backgroundOpenSupportsMainGoldenQueriesAndIndependentIdempotentClose() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidBundledUsdaCatalogFactory factory = new AndroidBundledUsdaCatalogFactory(context);
        ExecutorService background = Executors.newSingleThreadExecutor();
        MealCatalogHandle first = null;
        MealCatalogHandle second = null;
        try {
            first = openInBackground(background, factory);
            second = openInBackground(background, factory);
            MealCatalogHandle firstOwned = first;
            MealCatalogHandle secondOwned = second;
            onMain(() -> {
                assertSame(Looper.getMainLooper(), Looper.myLooper());
                assertNotSame(firstOwned, secondOwned);
                assertNotSame(firstOwned.getCatalog(), secondOwned.getCatalog());
                AndroidCatalogFixture.assertGolden(firstOwned.getCatalog());
                firstOwned.close();
                firstOwned.close();
                AndroidCatalogFixture.assertClosed(firstOwned.getCatalog());
                AndroidCatalogFixture.assertGolden(secondOwned.getCatalog());
                secondOwned.close();
                secondOwned.close();
                AndroidCatalogFixture.assertClosed(secondOwned.getCatalog());
            });
        } finally {
            MealCatalogHandle firstOwned = first;
            MealCatalogHandle secondOwned = second;
            onMain(() -> {
                if (firstOwned != null) firstOwned.close();
                if (secondOwned != null) secondOwned.close();
            });
            background.shutdownNow();
        }
    }

    private static MealCatalogHandle openInBackground(
            ExecutorService background, MealCatalogFactory factory) throws Exception {
        MealCatalogHandle opened = background.submit(() -> {
            assertNotSame(Looper.getMainLooper(), Looper.myLooper());
            return factory.open();
        }).get(120, TimeUnit.SECONDS);
        assertNotNull("The pinned bundled USDA package must activate on the real Android driver", opened);
        return opened;
    }

    private static void onMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
