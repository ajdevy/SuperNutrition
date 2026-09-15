package com.supernutrition.android;

import android.app.Application;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;

public final class BootstrapApplication extends Application
        implements com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeProvider {
    private final com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeFactory
            photoAnalysisRuntimeFactory = new AndroidPocRuntimeFactory();
    private final CoroutineDispatcher photoAnalysisBackgroundDispatcher = Dispatchers.getDefault();

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
    public void onCreate() {
        super.onCreate();
        AndroidBootstrap.start();
    }
}
