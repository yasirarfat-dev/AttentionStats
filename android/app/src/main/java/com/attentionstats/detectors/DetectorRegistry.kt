package com.attentionstats.detectors

import com.attentionstats.detectors.social.instagram.InstagramDetector
import com.attentionstats.detectors.social.facebook.FacebookDetector
import com.attentionstats.detectors.social.youtube.YouTubeDetector
import com.attentionstats.detectors.social.tiktok.TikTokDetector
import org.attentionstats.reels.ReelApp

/** Composition only. Adding a candidate never enables a source or qualifies another surface. */
internal object DetectorRegistry {
    private val detectors: List<SurfaceDetector> = listOf(InstagramDetector, FacebookDetector, YouTubeDetector, TikTokDetector)

    init {
        check(detectors.map { it.definition.app to it.definition.versionName }.distinct().size == detectors.size)
    }

    fun detector(app: ReelApp, versionName: String): SurfaceDetector? =
        detectors.firstOrNull { it.definition.app == app && it.definition.versionName == versionName }
}
