package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject

/** Operator-only feasibility: transient display pixels, no saved frames, OCR or gestures. */
internal object VisualProgressAudit {
    private data class Band(val top: Int, val bottom: Int, val width: Int, val inset: Int)

    // Research hypothesis only: a thin red prefix followed by a neutral track.
    // A match is not a qualified player signal, much less permission to act.
    private fun bands(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int,
        lightPrefix: Boolean = false): List<Band> {
        val width = right - left
        val row = IntArray(width)
        val groups = mutableListOf<Band>()
        var current: Band? = null
        fun flush() { current?.let { if (it.bottom - it.top in 2..10) groups.add(it) }; current = null }
        for (y in top until bottom) {
            bitmap.getPixels(row, 0, width, left, y, width, 1)
            fun foreground(pixel: Int): Boolean {
                val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
                return if (lightPrefix) minOf(r, g, b) >= 210 && maxOf(r, g, b) - minOf(r, g, b) <= 18
                    else r >= 160 && g < 100 && b < 150
            }
            var inset = 0
            while (inset < width * .08 && !foreground(row[inset])) inset++
            var prefix = 0
            while (inset + prefix < width && foreground(row[inset + prefix])) prefix++
            val remaining = width - inset - prefix
            var neutral = 0
            for (x in inset + prefix until width) {
                val pixel = row[x]; val r = (pixel shr 16) and 255
                val g = (pixel shr 8) and 255; val b = pixel and 255
                if (kotlin.math.abs(r - g) <= 18 && kotlin.math.abs(r - b) <= 18 &&
                    (!lightPrefix || maxOf(r, g, b) < 180)) neutral++
            }
            if (prefix >= width * .02 && (remaining == 0 || neutral >= remaining * .95)) {
                val before = current
                if (before != null && kotlin.math.abs(before.width - prefix) <= 2 && kotlin.math.abs(before.inset - inset) <= 2)
                    current = before.copy(bottom = y + 1)
                else { flush(); current = Band(y, y + 1, prefix, inset) }
            } else flush()
        }
        flush(); return groups
    }

    fun run(instrumentation: Instrumentation, key: String): String {
        val app = checkNotNull(ReelApp.byKey(key)); check(app in setOf(ReelApp.YOUTUBE, ReelApp.TIKTOK))
        val owner = ReelsCoordinator.get(instrumentation.targetContext)
        val loaded = java.util.concurrent.CountDownLatch(1); var failed = false
        owner.snapshot({ loaded.countDown() }, { failed = true; loaded.countDown() })
        check(loaded.await(15, java.util.concurrent.TimeUnit.SECONDS) && !failed)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun sourceRoot(): android.view.accessibility.AccessibilityNodeInfo {
            val root = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }?.root
                ?: checkNotNull(automation.rootInActiveWindow)
            if (app.owns(root.packageName?.toString())) return root
            SnapshotReader.recycle(root); error("selected source left foreground; no pixels read")
        }
        val synthetic = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        try {
            synthetic.eraseColor(0xff404040.toInt())
            for (y in 40..43) for (x in 0..49) synthetic.setPixel(x, y, 0xffff0033.toInt())
            check(bands(synthetic, 0, 30, 100, 60).single().width == 50)
            synthetic.eraseColor(0xff404040.toInt())
            for (y in 30..59) for (x in 0..49) synthetic.setPixel(x, y, 0xffff0033.toInt())
            check(bands(synthetic, 0, 30, 100, 60).isEmpty())
            synthetic.eraseColor(0xff404040.toInt())
            for (y in 40..43) for (x in 4..53) synthetic.setPixel(x, y, 0xffff0033.toInt())
            check(bands(synthetic, 0, 30, 100, 60).single().let { it.width == 50 && it.inset == 4 })
            synthetic.eraseColor(0xff404040.toInt())
            for (y in 40..43) for (x in 0..49) synthetic.setPixel(x, y, 0xffeeeeee.toInt())
            check(bands(synthetic, 0, 30, 100, 60, true).single().width == 50)
            synthetic.eraseColor(0xff404040.toInt())
            for (y in 40..43) for (x in 70..99) synthetic.setPixel(x, y, 0xffff0033.toInt())
            check(bands(synthetic, 0, 30, 100, 60).isEmpty())
        } finally { synthetic.recycle() }
        var unique = 0; var ambiguous = 0; var absent = 0; var advances = 0; var resets = 0
        var numeric = 0; var redSamples = 0; var lightSamples = 0
        var previous: Triple<Long, Int, Double>? = null
        val positions = mutableSetOf<Int>(); var minimum = 1.0; var maximum = 0.0
        repeat(30) {
            check(owner.allowed(app) && !AutoScrollSession.running) { "source consent/run changed" }
            val root = sourceRoot()
            val snapshot = try { SnapshotReader.read(root, app.packages) } finally { SnapshotReader.recycle(root) }
            val frame = AdapterRegistry.of(app).read(snapshot)
            check(snapshot.complete && !snapshot.typing && frame.surface == Surface.REELS && frame.route == ReelRoute.VIEWER)
            val page = checkNotNull(frame.page); val identity = checkNotNull(frame.identity)
            if (frame.progress != null) numeric++
            val captured = checkNotNull(automation.takeScreenshot())
            val bitmap = try { checkNotNull(captured.copy(Bitmap.Config.ARGB_8888, false)) }
                finally { captured.recycle() }
            try {
                // Revalidate foreground after capture; never save the transient full-display bitmap.
                val afterRoot = sourceRoot()
                val after = try { SnapshotReader.read(afterRoot, app.packages) } finally { SnapshotReader.recycle(afterRoot) }
                val afterFrame = AdapterRegistry.of(app).read(after)
                check(after.complete && !after.typing && after.windowId == snapshot.windowId &&
                    afterFrame.surface == Surface.REELS && afterFrame.route == ReelRoute.VIEWER && afterFrame.identity == identity) {
                    "source context changed during transient capture; pixels discarded"
                }
                check(page.left >= 0 && page.right <= bitmap.width && page.top >= 0 && page.bottom <= bitmap.height)
                val red = bands(bitmap, page.left, page.top + page.height * 3 / 4, page.right, page.bottom)
                val light = bands(bitmap, page.left, page.top + page.height * 3 / 4, page.right, page.bottom, true)
                if (red.isNotEmpty()) redSamples++
                if (light.isNotEmpty()) lightSamples++
                val matches = red + light
                val candidate = matches.singleOrNull()
                if (candidate == null) { if (matches.isEmpty()) absent++ else ambiguous++; previous = null }
                else {
                    unique++; val value = candidate.width.toDouble() / page.width
                    minimum = minOf(minimum, value); maximum = maxOf(maximum, value)
                    positions.add(candidate.width)
                    val before = previous
                    if (before != null && before.first == identity && kotlin.math.abs(before.second - candidate.top) <= 2) {
                        if (value > before.third && value - before.third <= .15) advances++
                        if (before.third >= .98 && value <= .05) resets++
                    }
                    previous = Triple(identity, candidate.top, value)
                }
            } finally { bitmap.recycle() }
            SystemClock.sleep(300)
        }
        return JSONObject().put("samples", 30).put("syntheticShapeChecks", 5)
            .put("redBandSamples", redSamples).put("lightBandSamples", lightSamples)
            .put("uniqueBandSamples", unique).put("ambiguousBandSamples", ambiguous).put("absentBandSamples", absent)
            .put("distinctPixelPositions", positions.size).put("sameItemSmallAdvances", advances).put("observedResets", resets)
            .put("minimumFraction", if (unique > 0) minimum else JSONObject.NULL)
            .put("maximumFraction", if (unique > 0) maximum else JSONObject.NULL).put("accessibilityProgressSamples", numeric)
            .put("framesSaved", 0).put("sourceGestures", 0).put("productionCaptureCapabilityAdded", false)
            .put("transientFullDisplayCapture", true).put("analysisRegion", "lower quarter of qualified player page")
            .put("qualification", "visual shape feasibility only; no end/action acceptance").toString()
    }
}
