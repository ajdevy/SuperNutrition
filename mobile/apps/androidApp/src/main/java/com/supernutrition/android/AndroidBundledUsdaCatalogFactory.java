package com.supernutrition.android;

import android.content.Context;
import android.os.Looper;

import app.cash.sqldelight.driver.android.AndroidSqliteDriver;

import com.supernutrition.data.nutrition.BundledUsdaCatalogKt;
import com.supernutrition.data.nutrition.BundledUsdaCatalogSession;
import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog;
import com.supernutrition.feature.photoanalysis.MealCatalogFactory;
import com.supernutrition.feature.photoanalysis.MealCatalogHandle;
import com.supernutrition.platform.photoandroid.AndroidSha256Kt;

/** App-root selection of a fresh, verified, in-memory USDA database for each owned session. */
public final class AndroidBundledUsdaCatalogFactory implements MealCatalogFactory {
    private final Context context;

    /** Application field initialization precedes attachment; context access belongs only in open. */
    public AndroidBundledUsdaCatalogFactory(Context context) {
        this.context = context;
    }

    @Override
    public MealCatalogHandle open() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("The nutrition catalog must be opened off the main thread");
        }
        BundledUsdaCatalogSession session = BundledUsdaCatalogKt.openBundledUsdaCatalog(
                () -> new AndroidSqliteDriver(
                        BundledUsdaCatalogKt.getUsdaCatalogSchema(), context.getApplicationContext(), null),
                AndroidSha256Kt::androidSha256
        );
        return session == null ? null : new OwnedCatalogHandle(session);
    }

    private static final class OwnedCatalogHandle implements MealCatalogHandle {
        private final BundledUsdaCatalogSession session;

        OwnedCatalogHandle(BundledUsdaCatalogSession session) {
            this.session = session;
        }

        @Override
        public ApprovedNutritionCatalog getCatalog() {
            return session;
        }

        @Override
        public void close() {
            session.close();
        }
    }
}
