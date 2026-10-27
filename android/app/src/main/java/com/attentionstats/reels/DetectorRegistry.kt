package com.attentionstats.reels

import org.attentionstats.reels.ReelApp

/** Only source-reviewed, live-qualified per-version definitions may enable counting. */
internal data class DetectorDefinition(
    val app: ReelApp,
    val versionName: String,
    val revision: Int,
    val requiredIds: Set<String>,
    val excludedIds: Set<String>,
    val scrollSourceIds: Set<String>,
)

internal object DetectorRegistry {
    // Filled from consented structural qualification; no guessed platform selectors.
    val definitions: List<DetectorDefinition> = emptyList()
    fun definition(app: ReelApp, versionName: String): DetectorDefinition? =
        definitions.firstOrNull { it.app == app && it.versionName == versionName }
}
