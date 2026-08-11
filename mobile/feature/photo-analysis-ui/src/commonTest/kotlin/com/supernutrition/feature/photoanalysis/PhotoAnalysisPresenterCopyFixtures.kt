package com.supernutrition.feature.photoanalysis

/** Test-only non-English copy used to prove hosts do not depend on English literals. */
object PhotoAnalysisPresenterCopyFixtures {
    fun localized(): PhotoAnalysisPresenterCopy = PhotoAnalysisPresenterCopy(
        capture = captureCopy(),
        status = statusCopy(),
        evidence = evidenceCopy(),
        recovery = recoveryCopy(),
    )

    private fun captureCopy() = CaptureCopy(
        title = "Acquisizione",
        takeMealPhoto = "Scatta foto del pasto",
        choosePhoto = "Scegli foto",
        openSettings = "Apri impostazioni",
        cancel = "Annulla",
        researchWarning = "Avviso di ricerca",
        gridHintsTitle = "Seleziona area foto",
        gridOverlayDescription = "Griglia foto",
        gridCellDescription = "Analizza area {index}",
        selectedState = "Selezionato",
        notSelectedState = "Non selezionato",
        resultPlaceholder = "Risultati dopo l'analisi",
        loadingLabel = "Analisi",
    )

    private fun statusCopy() = StatusCopy(
        readyToCapture = "Pronto per acquisire",
        preparingPhoto = "Preparazione foto",
        photoReady = "Foto pronta",
        analyzingPhoto = "Analisi foto",
        findingVisibleFoods = "Ricerca cibi visibili",
        identifyingDish = "Identificazione piatto",
        estimatingPortions = "Stima porzioni",
        checkingResults = "Verifica risultati",
        reviewCandidate = "Rivedi candidato",
        restoringPhotoAnalysis = "Ripristino analisi foto",
    )

    private fun evidenceCopy() = EvidenceCopy(
        fromThisPhoto = "Dalla foto",
        highConfidence = "Alta confidenza",
        mediumConfidence = "Media confidenza",
        lowConfidence = "Bassa confidenza",
        identityClear = "Identita chiara",
        identityAmbiguous = "Identita incerta",
        mixedContent = "Contenuto misto",
        fullyVisible = "Completamente visibile",
        partiallyOccluded = "Parzialmente coperto",
        regionAvailable = "Area nella foto disponibile",
        regionUnavailable = "Area nella foto non disponibile",
        estimatedMass = "Estimated: {lower}–{upper} g",
        massNotEvaluable = "Peso non valutabile",
        massAbstained = "Peso non disponibile",
        localProvenance = "Analisi sul dispositivo",
        remoteProvenance = "Analisi remota",
        selected = "Selezionato",
        notSelected = "Non selezionato",
        dishCandidatesTitle = "Piatti candidati",
        visibleComponentsTitle = "Visibile nella foto",
        visibleRegionCount = "Aree visibili: {count}",
        compositionHintsTitle = "Possibili ingredienti",
        compositionHintSource = "Suggerimento del piatto",
        partialDegradation = "Suggerimenti piatto non disponibili",
    )

    private fun recoveryCopy() = RecoveryCopy(
        scanAgain = "Scansiona di nuovo",
        cameraPermissionRequired = "Serve il permesso fotocamera",
        cameraUnavailable = "Fotocamera non disponibile",
        photoCaptureFailed = "Acquisizione foto non riuscita",
        photoQualityTooLow = "Qualita foto insufficiente",
        noFoodFound = "Nessun alimento trovato",
        analysisOffline = "Analisi offline",
        analysisCancelled = "Analisi annullata",
        reviewRestoreUnavailable = "Impossibile ripristinare i risultati",
    )
}
