package com.supernutrition.android;

import android.app.Application;

import com.supernutrition.feature.photoanalysis.MealCatalogFactory;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidCatalogProvider;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;

public class BootstrapApplication extends Application
        implements com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeProvider,
        PhotoAnalysisAndroidCatalogProvider {
    private final com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeFactory
            photoAnalysisRuntimeFactory = new AndroidPocRuntimeFactory();
    private final CoroutineDispatcher photoAnalysisBackgroundDispatcher = Dispatchers.getDefault();
    private final MealCatalogFactory photoAnalysisCatalogFactory = "production".equals(BuildConfig.FLAVOR)
            ? new AndroidBundledUsdaCatalogFactory(this) : () -> null;
    private final CoroutineDispatcher photoAnalysisCatalogBackgroundDispatcher = Dispatchers.getIO();

    @Override
    public com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeFactory
    getPhotoAnalysisRuntimeFactory() {
        return photoAnalysisRuntimeFactory;
    }

    @Override
    public CoroutineDispatcher getPhotoAnalysisBackgroundDispatcher() {
        return photoAnalysisBackgroundDispatcher;
    }

    @Override
    public MealCatalogFactory getPhotoAnalysisCatalogFactory() {
        return photoAnalysisCatalogFactory;
    }

    @Override
    public CoroutineDispatcher getPhotoAnalysisCatalogBackgroundDispatcher() {
        return photoAnalysisCatalogBackgroundDispatcher;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidBootstrap.start();
    }
}
