package com.supernutrition.android;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnitRunner;

/** Native journeys cannot read or overwrite the installed user's diary, even on assertion failure. */
public final class IsolatedPrivateDataTestRunner extends AndroidJUnitRunner {
    private IsolatedPrivateDataApplication fixtureApplication;

    @Override
    public Application newApplication(ClassLoader loader, String className, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        fixtureApplication = (IsolatedPrivateDataApplication) super.newApplication(
                loader, IsolatedPrivateDataApplication.class.getName(), context);
        return fixtureApplication;
    }

    @Override
    public void finish(int resultCode, Bundle results) {
        try {
            if (fixtureApplication != null) fixtureApplication.cleanUpPrivateDocuments();
        } finally {
            super.finish(resultCode, results);
        }
    }
}
