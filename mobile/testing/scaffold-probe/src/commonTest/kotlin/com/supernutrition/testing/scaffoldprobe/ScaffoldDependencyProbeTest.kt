package com.supernutrition.testing.scaffoldprobe

import app.cash.sqldelight.db.SqlDriver
import com.arkivanov.decompose.ComponentContext
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.core.Koin
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaffoldDependencyProbeTest {
    @Test
    fun `bootstrap probe exposes the pinned substrate dependency set`() {
        val probe = ScaffoldDependencyProbe()

        assertEquals(
            setOf("koin", "decompose", "ktor", "ktorfit", "sqldelight", "serialization", "coroutines"),
            probe.dependencyMarkers(),
        )

        // Keep the acceptance probe tied to the actual pinned substrate APIs.
        assertEquals(
            7,
            listOf(
                Koin::class,
                ComponentContext::class,
                HttpClient::class,
                Ktorfit::class,
                SqlDriver::class,
                Json::class,
                CoroutineScope::class,
            ).size,
        )
    }
}
