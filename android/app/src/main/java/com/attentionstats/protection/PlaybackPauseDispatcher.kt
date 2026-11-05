package com.attentionstats.protection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detection.SnapshotReader
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.protection.PauseControlState
import org.attentionstats.protection.PlaybackPausePolicy
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface

/** One bounded, user-authorized Pause attempt. No retries, blind toggles, media keys or resume action. */
internal object PlaybackPauseDispatcher {
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
