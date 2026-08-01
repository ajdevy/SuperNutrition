package com.supernutrition.domain.inference

/**
 * Per-device-tier resource budgets for a local engine, and the qualification
 * decision that depends on them.
 *
 * NFR-3 requires storage, download, memory, thermal, and battery budgets to be
 * measured per device tier before activation, with no universal parameter-count
 * proxy accepted. That measurement is Gate G-3's exit artifact, so this file is
 * the mechanism and [DeviceTierMatrix.unmeasured] is its deliberately empty
 * state: nothing activates, the deterministic floor answers, and the product is
 * usable while the gate is open rather than blocked by it.
 *
 * Nothing here enumerates device tiers. The tier list is itself part of what
 * G-3 selects — "signed real-device benchmark selecting OS/device tiers" — so an
 * enum of low/mid/high would be this file guessing the gate's answer and would
 * have to be a breaking change when the real one arrives. A tier is an opaque
 * declared id until then.
 *
 * There is no parameter-count input anywhere in this file, and that is the
 * enforceable form of "no universal proxy": a budget cannot be constructed
 * without [BudgetEvidence] naming the physical device it was measured on. A
 * number derived from a model's size has nowhere to go.
 */
data class DeviceTierId(val value: String) {
    init {
        requireNonBlankValue(value, "A device tier id must not be blank")
    }
}

/**
 * Android and iOS qualify separately.
 *
 * The PRD is explicit that the two platforms may use different adapters,
 * runtimes, models, and device tiers. A budget measured on one says nothing
 * about the other, so the matrix is keyed by both.
 */
enum class EnginePlatform { ANDROID, IOS }

/**
 * Where a budget number came from.
 *
 * A simulator or emulator is not performance evidence — NFR-1 says so and
 * AGENTS.md repeats it — so the obvious identifiers are rejected rather than
 * trusted to reviewer attention. This cannot catch a deliberately mislabelled
 * measurement; it catches the accident of benchmarking on the emulator that was
 * already running, which is the way this actually happens.
 *
 * The record is authored, not sampled. This module is pure KMP domain with no
 * platform source sets, so nothing here can read `UIDevice.current.model` or
 * `ro.product.model` — [measuredOnEpochDay] says the same thing from the other
 * side, since a runtime-collected record would have a clock. What the marker
 * list guards is what a person types when transcribing a benchmark result.
 */
data class BudgetEvidence(
    val deviceModel: String,
    val osVersion: String,
    /** Days since the epoch. Staleness is the caller's decision; the domain has no clock. */
    val measuredOnEpochDay: Long,
) {
    init {
        requireNonBlankValue(deviceModel, "Budget evidence must name the device it was measured on")
        requireNonBlankValue(osVersion, "Budget evidence must name the OS version it was measured on")
        require(measuredOnEpochDay > 0) { "Budget evidence must record when it was measured" }
        val lowered = deviceModel.lowercase()
        require(SYNTHETIC_DEVICE_MARKERS.none { it in lowered }) {
            "Budget evidence names a simulator or emulator ($deviceModel); that is not performance proof"
        }
    }

    private companion object {
        val SYNTHETIC_DEVICE_MARKERS = listOf(
            "simulator",
            "emulator",
            "sdk_gphone",
            "generic_",
            "android sdk built for",
        )
    }
}

/**
 * Every budget dimension G-3 must measure, for one tier on one platform.
 *
 * Six rather than five, and the arithmetic is worth stating because getting it
 * wrong is easy: NFR-3 names storage, download, memory, thermal and battery,
 * while G-3's own exit artifact names "latency/memory/thermal/battery budgets"
 * and NFR-1 requires the SM-3 latency hypothesis to be replaced by an approved
 * release budget at this gate. The union is what a benchmark has to produce, and
 * building to the NFR alone drops latency silently.
 *
 * Every value is required. A budget missing thermal because it was inconvenient
 * to measure is the shape in which a device gets activated on five dimensions
 * and throttles on the sixth, and the user experiences that as the app becoming
 * slow rather than as a missing measurement.
 */
data class DeviceTierBudget(
    val tier: DeviceTierId,
    val platform: EnginePlatform,
    val modelStorageBytes: Long,
    val downloadBytes: Long,
    val peakMemoryBytes: Long,
    /** Attempts sustainable before the device throttles. Thermal headroom, in the unit a user feels. */
    val sustainedAttemptsBeforeThrottle: Int,
    /** Battery cost of one attempt, in thousandths of a percent. */
    val batteryMilliPercentPerAttempt: Int,
    /**
     * Ceiling for one attempt on this tier, request to candidates.
     *
     * The engine-side measurement, which is SM-3's. SM-2's camera-ready time is
     * a different number owned by the camera path: it is measured before any
     * engine runs and does not vary with the model, so putting it here would
     * make it look like a per-model budget.
     */
    val attemptLatencyBudgetMillis: Int,
    val evidence: BudgetEvidence,
) {
    init {
        require(modelStorageBytes > 0) { "Model storage budget must be positive" }
        require(downloadBytes > 0) { "Download budget must be positive" }
        require(peakMemoryBytes > 0) { "Peak memory budget must be positive" }
        require(sustainedAttemptsBeforeThrottle > 0) {
            "A tier that cannot sustain one attempt is not qualified; it is unsupported"
        }
        require(batteryMilliPercentPerAttempt > 0) { "Battery budget must be positive" }
        require(attemptLatencyBudgetMillis > 0) { "Attempt latency budget must be positive" }
    }
}

/** Why a tier is not qualified to run a local engine. */
enum class TierDisqualification {
    /** No measured budget exists for this tier and platform. The state every tier is in until G-3. */
    BUDGETS_UNMEASURED,

    ;

    /**
     * `DEVICE_UNSUPPORTED` rather than `RESOURCE_BUDGET`: the budget was never
     * measured, which is a different thing from an attempt that would exceed
     * one. The second reason stays available for the case it names, once
     * budgets exist to be exceeded.
     *
     * State and reason are the version-neutral enums both contract releases
     * share, not a v1 `EngineCapability`. That envelope requires
     * `photo-inference-engine/v1`, so building one here for a text engine would
     * repeat the crossing v2 exists to end.
     */
    val capabilityReason: CapabilityReason
        get() = when (this) {
            BUDGETS_UNMEASURED -> CapabilityReason.DEVICE_UNSUPPORTED
        }

    val capabilityState: CapabilityState
        get() = when (this) {
            BUDGETS_UNMEASURED -> CapabilityState.UNSUPPORTED
        }
}

sealed interface TierQualification {
    data class Qualified(val budget: DeviceTierBudget) : TierQualification

    data class NotQualified(val reason: TierDisqualification) : TierQualification
}

/**
 * The qualified device matrix.
 *
 * Empty until G-3 signs one. That is the point rather than a placeholder to be
 * filled in locally: an invented budget is the same failure as an invented
 * nutrition number — it looks like data, nothing in the app can tell it is not,
 * and the test below fails the moment one appears.
 */
class DeviceTierMatrix(budgets: List<DeviceTierBudget>) {
    val budgets: List<DeviceTierBudget> = budgets.toList()

    init {
        val keys = budgets.map { it.tier to it.platform }
        require(keys.toSet().size == keys.size) {
            "A tier and platform may have one budget; two measurements of the same tier need one reconciled answer"
        }
    }

    /**
     * Whether a local engine may activate on this tier.
     *
     * Fails closed. An unknown tier is unqualified rather than assumed similar
     * to a known one — "close enough to the mid tier" is how a device gets a
     * model it cannot run.
     */
    fun qualify(tier: DeviceTierId, platform: EnginePlatform): TierQualification =
        budgets.firstOrNull { it.tier == tier && it.platform == platform }
            ?.let(TierQualification::Qualified)
            ?: TierQualification.NotQualified(TierDisqualification.BUDGETS_UNMEASURED)

    companion object {
        /**
         * The shipped matrix. Empty until G-3 delivers a signed benchmark.
         *
         * Every tier is therefore unqualified, every local engine reports
         * unsupported, and every typed description is answered by the E7.2
         * deterministic floor — which is the journey UJ-4 needs anyway, on the
         * low-end offline device least able to run a model.
         */
        val unmeasured: DeviceTierMatrix = DeviceTierMatrix(emptyList())
    }
}
