package com.attentionstats.detectors

import com.attentionstats.detectors.social.instagram.InstagramDetector
import org.attentionstats.reels.ReelApp

/** Composition only. Adding a candidate never enables a source or qualifies another surface. */
internal object DetectorRegistry {
    private val detectors: List<SurfaceDetector> = listOf(InstagramDetector)

    init {
        check(detectors.map { it.definition.app to it.definition.versionName }.distinct().size == detectors.size)
    }

    fun detector(app: ReelApp, versionName: String): SurfaceDetector? =
        detectors.firstOrNull { it.definition.app == app && it.definition.versionName == versionName }
}
