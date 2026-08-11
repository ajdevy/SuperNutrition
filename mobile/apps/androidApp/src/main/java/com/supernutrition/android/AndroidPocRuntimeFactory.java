package com.supernutrition.android;

import android.content.Context;
import android.util.Log;

import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort;
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeBinding;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeCloseAction;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAndroidRuntimeFactory;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisAnalysisConfiguration;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisFeature;
import com.supernutrition.feature.photoanalysis.PhotoAnalysisLauncherFactory;
import com.supernutrition.inference.localandroidpoc.AndroidPocRuntimeEngines;
import com.supernutrition.inference.localandroidpoc.AndroidPocRuntimeKt;

import kotlinx.coroutines.CoroutineScope;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Installs the local model runtime used by the current Android app. */
public final class AndroidPocRuntimeFactory implements PhotoAnalysisAndroidRuntimeFactory {
    @Override
    public PhotoAnalysisAndroidRuntimeBinding create(
            Context context,
            PhotoAnalysisFeature feature,
            PhotoLeaseStorePort leases,
            PhotoSessionStorePort sessions,
            CoroutineScope scope
    ) {
        AndroidPocRuntimeEngines engines = new AndroidPocRuntimeEngines(context, sessions, leases);
        PhotoAnalysisAnalysisConfiguration configuration = new PhotoAnalysisAnalysisConfiguration(
                "android-poc-analysis",
                "poc-analysis-v1",
                AndroidPocRuntimeKt.ANDROID_POC_ARTIFACT_DIGEST
        );
        return new PhotoAnalysisAndroidRuntimeBinding(
                PhotoAnalysisLauncherFactory.INSTANCE.create(
                        feature,
                        engines.getDishEngine(),
                        engines.getComponentEngine(),
                        scope,
                        configuration,
                        new Function1<Throwable, Unit>() {
                            @Override
                            public Unit invoke(Throwable error) {
                                Log.e("SuperNutritionInference", "Photo analysis failed: "
                                        + error.getClass().getSimpleName());
                                return Unit.INSTANCE;
                            }
                        }
                ),
                new PhotoAnalysisAndroidRuntimeCloseAction() {
                    @Override
                    public void close() {
                        engines.close();
                    }
                }
        );
    }
}
