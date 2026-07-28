package com.supernutrition.testing.scaffoldprobe

/**
 * Non-production bootstrap probe. It references the pinned KMP substrate
 * slots without exposing any product API or fake nutrition/camera behavior.
 * The concrete substrate types are compiled and asserted by this module's
 * commonTest source set; keeping the packaged probe type-erased prevents a
 * test APK from leaking implementation-only dependency graphs.
 */
class ScaffoldDependencyProbe {
    fun dependencyMarkers(): Set<String> = setOf(
        "koin",
        "decompose",
        "ktor",
        "ktorfit",
        "sqldelight",
        "serialization",
        "coroutines",
    )
}
