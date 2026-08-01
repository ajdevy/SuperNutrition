package com.supernutrition.domain.nutrition

/**
 * A text engine that needs no model.
 *
 * It splits a description on ordinary separators and resolves each fragment
 * through the catalog. That makes it worse than a language model at
 * understanding language and better at being available: it runs on every
 * supported device, downloads nothing, negotiates no resource budget, and does
 * not wait on the G-3 device matrix.
 *
 * This is the same layering the rest of the design uses — a floor that always
 * answers, with cleverness above it. When an on-device model is approved it
 * becomes a swap behind the same port, not a prerequisite for the story.
 */
class DeterministicTextEngine(private val catalog: CatalogSearch) {
    fun identify(description: String): TextEngineOutcome {
        val fragments = TextFragments.of(description)
        if (fragments.isEmpty()) return TextEngineOutcome.Abstained("no-recognizable-fragment")

        val items = mutableListOf<DetectedItem>()
        for (fragment in fragments) {
            val resolved = catalog.search(fragment.text).firstOrNull()
                // Partial segmentation is the FM-6 failure: returning the
                // fragments that resolved would silently drop the rest of the
                // meal while looking authoritative. Abstain for the whole
                // description instead, and let the floor answer.
                ?: return TextEngineOutcome.Abstained("fragment-unresolved")
            items += DetectedItem(resolved.canonicalItemId, fragment.span, resolved.unmatchedPreparation)
        }
        return TextEngineOutcome.Segmented(items)
    }
}
