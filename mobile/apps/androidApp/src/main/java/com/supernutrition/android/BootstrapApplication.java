package com.supernutrition.android;

import android.app.Application;

public final class BootstrapApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AndroidBootstrap.start();
    }
}
