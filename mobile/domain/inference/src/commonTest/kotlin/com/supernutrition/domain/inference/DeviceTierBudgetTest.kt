package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The mechanism NFR-3 needs, and the proof that it is still inert.
 *
 * The most important test here is the one asserting the shipped matrix is
 * empty. It is what stops a plausible budget being invented locally to unblock
 * work — the same failure as an invented nutrition number, and equally
 * invisible once it exists.
 */
class DeviceTierBudgetTest {
    private val evidence = BudgetEvidence(
        deviceModel = "Pixel 6a",
        osVersion = "Android 14",
        measuredOnEpochDay = 20_000,
    )

    private fun budget(tier: String = "mid", platform: EnginePlatform = EnginePlatform.ANDROID) = DeviceTierBudget(
        tier = DeviceTierId(tier),
        platform = platform,
        modelStorageBytes = 180_000_000,
        downloadBytes = 175_000_000,
        peakMemoryBytes = 420_000_000,
        sustainedAttemptsBeforeThrottle = 12,
        batteryMilliPercentPerAttempt = 40,
        attemptLatencyBudgetMillis = 4_000,
        evidence = evidence,
    )

    @Test
    fun `the shipped matrix carries no budget until G-3 signs one`() {
        // Deliberately inert. When this fails, either G-3 delivered and this
        // test should be retargeted at the real matrix, or someone invented a
        // number — and the second is why the test exists.
        assertEquals(emptyList(), DeviceTierMatrix.unmeasured.budgets)
    }

    @Test
    fun `every tier is therefore unqualified, on both platforms`() {
        for (platform in EnginePlatform.entries) {
            val qualification = DeviceTierMatrix.unmeasured.qualify(DeviceTierId("mid"), platform)

            val notQualified = assertIs<TierQualification.NotQualified>(qualification)
            assertEquals(TierDisqualification.BUDGETS_UNMEASURED, notQualified.reason)
        }
    }

    @Test
    fun `an unmeasured tier reports unsupported, not over-budget`() {
        // The two are different facts. Reporting an unmeasured device as
        // over-budget would say we measured it and it failed.
        val reason = TierDisqualification.BUDGETS_UNMEASURED

        assertEquals(CapabilityState.UNSUPPORTED, reason.capabilityState)
        assertEquals(CapabilityReason.DEVICE_UNSUPPORTED, reason.capabilityReason)
    }

    @Test
    fun `an unknown tier is unqualified rather than assumed similar to a known one`() {
        // "Close enough to the mid tier" is how a device gets a model it cannot
        // run, so the lookup is exact and the miss fails closed.
        val matrix = DeviceTierMatrix(listOf(budget(tier = "mid")))

        assertIs<TierQualification.Qualified>(matrix.qualify(DeviceTierId("mid"), EnginePlatform.ANDROID))
        assertIs<TierQualification.NotQualified>(matrix.qualify(DeviceTierId("mid-2024"), EnginePlatform.ANDROID))
    }

    @Test
    fun `a budget measured on Android does not qualify the same tier on iOS`() {
        // The platforms use different adapters, runtimes and models, so a
        // measurement on one is not evidence about the other.
        val matrix = DeviceTierMatrix(listOf(budget(platform = EnginePlatform.ANDROID)))

        assertIs<TierQualification.Qualified>(matrix.qualify(DeviceTierId("mid"), EnginePlatform.ANDROID))
        assertIs<TierQualification.NotQualified>(matrix.qualify(DeviceTierId("mid"), EnginePlatform.IOS))
    }

    @Test
    fun `simulator and emulator evidence is refused`() {
        // NFR-1: simulator results are not performance proof. This catches the
        // way it actually happens — benchmarking on the emulator that was
        // already running — rather than a deliberate mislabelling, which no
        // check here could catch.
        val synthetic = listOf(
            "iPhone 15 Pro Simulator",
            "Android Emulator",
            "sdk_gphone64_arm64",
            "generic_x86_64",
            "Android SDK built for x86",
        )

        for (model in synthetic) {
            val failure = assertFailsWith<IllegalArgumentException>("accepted $model as physical-device evidence") {
                BudgetEvidence(deviceModel = model, osVersion = "Android 14", measuredOnEpochDay = 20_000)
            }
            assertTrue("not performance proof" in failure.message.orEmpty())
        }
    }

    @Test
    fun `a real device name is not mistaken for a synthetic one`() {
        // The marker list must not swallow legitimate names, or the check
        // becomes something to work around rather than to satisfy.
        for (model in listOf("Pixel 6a", "iPhone 15 Pro", "Galaxy S23", "Xperia 1 V")) {
            BudgetEvidence(deviceModel = model, osVersion = "17.4", measuredOnEpochDay = 20_000)
        }
    }

    @Test
    fun `a budget cannot exist without evidence naming where it was measured`() {
        // The enforceable form of "no universal parameter-count proxy": there
        // is no parameter-count input on any type here, and a number that came
        // from a model's size has no evidence to offer.
        assertFailsWith<IllegalArgumentException> {
            BudgetEvidence(deviceModel = "  ", osVersion = "Android 14", measuredOnEpochDay = 20_000)
        }
        assertFailsWith<IllegalArgumentException> {
            BudgetEvidence(deviceModel = "Pixel 6a", osVersion = "", measuredOnEpochDay = 20_000)
        }
        assertFailsWith<IllegalArgumentException> {
            BudgetEvidence(deviceModel = "Pixel 6a", osVersion = "Android 14", measuredOnEpochDay = 0)
        }
    }

    @Test
    fun `every dimension the gate must measure is required, none defaultable`() {
        // A budget missing thermal because it was awkward to measure activates
        // a device on five dimensions and throttles on the sixth, which a user
        // experiences as the app getting slow rather than as a gap.
        assertFailsWith<IllegalArgumentException> { budget().copy(modelStorageBytes = 0) }
        assertFailsWith<IllegalArgumentException> { budget().copy(downloadBytes = 0) }
        assertFailsWith<IllegalArgumentException> { budget().copy(peakMemoryBytes = 0) }
        assertFailsWith<IllegalArgumentException> { budget().copy(sustainedAttemptsBeforeThrottle = 0) }
        assertFailsWith<IllegalArgumentException> { budget().copy(batteryMilliPercentPerAttempt = 0) }
        assertFailsWith<IllegalArgumentException> { budget().copy(attemptLatencyBudgetMillis = 0) }
    }

    @Test
    fun `latency is one of them, because the gate names it even though NFR-3 does not`() {
        // The dimension this file originally dropped. NFR-3 lists storage,
        // download, memory, thermal and battery; G-3's exit artifact says
        // "latency/memory/thermal/battery budgets" and NFR-1 sends SM-3 here.
        // Building to the NFR alone loses it silently, which is what happened.
        assertEquals(4_000, budget().attemptLatencyBudgetMillis)
    }

    @Test
    fun `one tier and platform may not carry two budgets`() {
        // Two measurements of the same tier need one reconciled answer. Keeping
        // both and taking the first makes the qualification depend on list
        // order, which nothing about a benchmark controls.
        val failure = assertFailsWith<IllegalArgumentException> {
            DeviceTierMatrix(listOf(budget(), budget().copy(peakMemoryBytes = 999_000_000)))
        }

        assertTrue("one reconciled answer" in failure.message.orEmpty())
    }

    @Test
    fun `a tier id must name something`() {
        assertFailsWith<IllegalArgumentException> { DeviceTierId(" ") }
    }
}
