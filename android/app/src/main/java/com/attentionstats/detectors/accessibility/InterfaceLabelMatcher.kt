package com.attentionstats.detectors.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.attentionstats.reels.ReelApp

/** Exact Facebook interface tokens under source notice 3 or the separate temporary diagnostic. */
internal object InterfaceLabelMatcher {
    private val labels = mapOf("Reels" to "reels", "Video" to "video", "Home" to "home",
        "Close" to "close", "Comments" to "comments", "Search" to "search", "Back" to "back")

    private val diagnosticLabels = buildMap {
        put("Like", "like"); put("Comment", "comment_button"); put("Share", "share")
        for ((label, token) in listOf("Play" to "play", "Pause" to "pause", "Play video" to "play_video",
            "Pause video" to "pause_video", "Reel" to "reel", "Watch reel" to "watch_reel",
            "Watch again" to "watch_again", "Watch more" to "watch_more")) put(label, token)
        for ((name, token) in listOf("Home" to "home_tab", "Reels" to "reels_tab", "Video" to "video_tab"))
            for (total in 4..6) for (index in 1..total) {
                put("$name, tab $index of $total", token)
                put("$name, Tab $index of $total", token)
            }
    }

    private val homeLabels = buildMap {
        put("Reel", "reel")
        for (total in 4..6) for (index in 1..total) {
            put("Home, tab $index of $total", "home_tab")
            put("Home, Tab $index of $total", "home_tab")
        }
    }
    private val sourceLabels = labels + homeLabels
    private val allLabels = sourceLabels + diagnosticLabels

    fun match(app: ReelApp, node: AccessibilityNodeInfo, authorized: Boolean, diagnostics: Boolean = false): String? {
        if (!authorized || (app != ReelApp.FACEBOOK && !(diagnostics && app == ReelApp.INSTAGRAM)) ||
            node.packageName?.toString() != app.packageName || !node.isVisibleToUser ||
            node.isEditable || node.isPassword) return null
        val description = node.contentDescription ?: return null
        if (description.length !in 1..64) return null
        // No conversion, normalization, partial match, hash, log or retention of the source value.
        return (if (diagnostics) allLabels else sourceLabels).entries.firstOrNull { (label, _) ->
            label.length == description.length && label.indices.all { label[it] == description[it] }
        }?.value
    }
}
