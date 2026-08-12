package com.supernutrition.android;

import android.app.Application;

public final class BootstrapApplication extends Application
        implements com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeProvider {
    private final com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeFactory
            photoAnalysisRuntimeFactory = new AndroidPocRuntimeFactory();

    @Override
    public com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeFactory
    getPhotoAnalysisRuntimeFactory() {
        return photoAnalysisRuntimeFactory;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidBootstrap.start();
    }
}
