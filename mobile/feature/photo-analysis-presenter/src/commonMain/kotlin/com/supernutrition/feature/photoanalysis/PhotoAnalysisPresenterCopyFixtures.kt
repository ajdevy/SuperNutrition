package com.supernutrition.feature.photoanalysis

/** Stable non-English copy sample shared by presenter, feature, and UI contract tests. */
object PhotoAnalysisPresenterCopyFixtures {
    fun localized(): PhotoAnalysisPresenterCopy = PhotoAnalysisPresenterCopy(
        capture = CaptureCopy(
            title = "Acquisizione",
            takeMealPhoto = "Scatta foto del pasto",
            choosePhoto = "Scegli foto",
            openSettings = "Apri impostazioni",
            cancel = "Annulla",
        ),
        status = StatusCopy(
            readyToCapture = "Pronto per acquisire",
            preparingPhoto = "Preparazione foto",
            photoReady = "Foto pronta",
            analyzingPhoto = "Analisi foto",
            reviewCandidate = "Rivedi candidato",
            restoringPhotoAnalysis = "Ripristino analisi foto",
        ),
        recovery = RecoveryCopy(
            scanAgain = "Scansiona di nuovo",
            cameraPermissionRequired = "Serve il permesso fotocamera",
            cameraUnavailable = "Fotocamera non disponibile",
            photoCaptureFailed = "Acquisizione foto non riuscita",
            photoQualityTooLow = "Qualita foto insufficiente",
            noFoodFound = "Nessun alimento trovato",
            analysisOffline = "Analisi offline",
            analysisCancelled = "Analisi annullata",
        ),
    )
}
