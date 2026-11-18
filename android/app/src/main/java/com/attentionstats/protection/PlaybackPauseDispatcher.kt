package com.attentionstats.protection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detection.SnapshotReader
import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.protection.PauseControlState
import org.attentionstats.protection.PlaybackPausePolicy
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** One bounded, user-authorized Pause attempt. No retries, blind toggles, media keys or resume action. */
internal object PlaybackPauseDispatcher {
    /** Sources whose player toggles play/pause on a single tap of the video and expose numeric progress. */
    private val tapToggles = setOf(ReelApp.TIKTOK, ReelApp.YOUTUBE)

    /**
     * Tap-to-pause for players without a Pause control (ADR-116). Progress decides everything:
     * still progress means already paused (no tap, since a tap would press Play); moving progress allows
     * exactly one tap on the middle of the video, and only progress that then stops confirms the pause.
     * Never a second tap. [sample] reads a fresh frame of the same window; [tap] performs one tap.
     */
    fun prepareByTap(app: ReelApp, current: () -> Boolean, sample: () -> AdapterFrame?, tap: (Float, Float) -> Boolean): Boolean {
        if (app !in tapToggles || !current()) return false
        // YouTube reports whole seconds; a longer window still sees a playing video move.
        val window = if (app == ReelApp.YOUTUBE) 1_600L else 1_000L
        val first = sample() ?: return false
        val page = first.page ?: return false
        val start = first.progress ?: return false
        Thread.sleep(window)
        if (!current()) return false
        val second = sample() ?: return false
        if (!sameItem(first, second)) return false
        var moved = second.progress ?: return false
        if (moved == start) {
            if (start > 0.0) return true // Still mid-video: already paused, nothing to tap.
            // At exactly 0 the bar may simply not have started reporting (YouTube, just after a swipe).
            // Look once more; still 0 means unknown, so never tap a video that might be paused.
            Thread.sleep(window)
            if (!current()) return false
            val again = sample() ?: return false
            if (!sameItem(first, again)) return false
            moved = again.progress ?: return false
            if (moved == start) return false
        }
        if (!current() || !tap(page.centerX.toFloat(), page.top + page.height * 0.45f)) return false
        Thread.sleep(700)
        if (!current()) return false
        val third = sample() ?: return false
        Thread.sleep(window)
        if (!current()) return false
        val fourth = sample() ?: return false
        return sameItem(first, third) && sameItem(first, fourth) && third.progress != null && third.progress == fourth.progress
    }

    private fun sameItem(a: AdapterFrame, b: AdapterFrame): Boolean = b.page != null &&
        (a.identity == b.identity || com.attentionstats.autoscroll.AutoScrollSession.sameItem(a.keys, b.keys))

    fun prepare(app: ReelApp, root: AccessibilityNodeInfo, current: () -> Boolean): Boolean {
        if (!current() || !app.owns(root.packageName?.toString())) return false
        val snapshot = SnapshotReader.read(root, app.packages)
        val frame = AdapterRegistry.of(app).read(snapshot)
        val page = frame.page ?: return false
        if (!snapshot.complete || snapshot.typing || frame.surface != Surface.REELS) return false
        val candidates = snapshot.nodes.filter {
            page.contains(it.bounds) && PlaybackPausePolicy.classify(app.key, it.id, it.label) != PauseControlState.UNKNOWN
        }
        val control = candidates.singleOrNull() ?: return false
        val id = control.id ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId("${snapshot.packageName}:id/$id")
        try {
            val node = nodes.singleOrNull { candidate ->
                val bounds = Rect().also { candidate.getBoundsInScreen(it) }
                bounds == Rect(control.bounds.left, control.bounds.top, control.bounds.right, control.bounds.bottom)
            } ?: return false
            if (!current() || !node.refresh() || !node.isVisibleToUser || !node.isEnabled || node.isPassword || node.isEditable ||
                node.windowId != snapshot.windowId || node.packageName?.toString() != snapshot.packageName) return false
            // Refresh and compare the exact current control label immediately before any action.
            if (!SnapshotReader.hasFixedControlLabel(node, checkNotNull(control.label))) return false
            if (PlaybackPausePolicy.classify(app.key, id, control.label) == PauseControlState.PAUSED) return true
            if (!node.isClickable || !current()) return false
            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false
            // A successful click alone does not prove Pause. Never click a second time to "fix" it.
            Thread.sleep(200)
            return current() && node.refresh() && node.isVisibleToUser &&
                node.windowId == snapshot.windowId && node.packageName?.toString() == snapshot.packageName &&
                SnapshotReader.hasFixedControlLabel(node, "Play")
        } finally { nodes.forEach { SnapshotReader.recycle(it) } }
    }
}
