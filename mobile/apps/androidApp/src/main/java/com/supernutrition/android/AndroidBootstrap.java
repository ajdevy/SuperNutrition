package com.supernutrition.android;

import org.koin.core.KoinApplication;
import org.koin.core.context.GlobalContext;

/** The sole Android Koin bootstrap owner for the thin host. */
public final class AndroidBootstrap {
    private static boolean started;

    private AndroidBootstrap() {
    }

    public static synchronized boolean start() {
        if (started) {
            return false;
        }

        KoinApplication application = KoinApplication.Companion.init();
        GlobalContext.INSTANCE.startKoin(application);
        started = true;
        return true;
    }
}
