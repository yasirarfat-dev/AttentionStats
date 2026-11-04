package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit 50-post operator qualification only. Private screenshots require independent visual review. */
internal object FeedTrialVerification {
    fun run(instrumentation: Instrumentation, source: String, reverse: Boolean = false, samples: Int = 90): String {
        require(samples in 10..90)
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val app = checkNotNull(ReelApp.byKey(source)).also { check(it in setOf(ReelApp.INSTAGRAM, ReelApp.FACEBOOK)) }
        val folder = File(context.cacheDir, "operator-feed-trial")
        check(!folder.exists() && folder.mkdir()) { "Previous private trial needs cleanup" }
        fun totals(): Map<String, Long> {
            val ready = CountDownLatch(1); var value: String? = null
            owner.snapshot({ value = it; ready.countDown() }, { ready.countDown() })
            check(ready.await(10, TimeUnit.SECONDS))
            val rows = JSONObject(checkNotNull(value)).getJSONArray("sources")
            val source = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("app") == app.key }
            val counts = source.getJSONObject("counts")
            val primary = listOf("reel", "photo", "ad", "unclassified").associateWith { counts.getString(it).toLong() }
            val diaryReady = CountDownLatch(1); var diary: String? = null
            owner.diarySnapshot(1, { diary = it; diaryReady.countDown() }, { diaryReady.countDown() })
            check(diaryReady.await(10, TimeUnit.SECONDS))
            val apps = JSONObject(checkNotNull(diary)).getJSONArray("days").getJSONObject(0).getJSONArray("apps")
            val detail = (0 until apps.length()).map { apps.getJSONObject(it) }.first { it.optString("social") == app.key }
            return primary + mapOf("photoFacet" to detail.getString("photoCount").toLong(),
                "videoFacet" to detail.getString("videoCount").toLong())
        }
        totals()
        SystemClock.sleep(4_000) // Operator reconnects only our service after instrumentation starts.
        check(owner.serviceConnected && owner.allowed(app)) { "Source/service not available" }
        val baseline = totals()
        val rows = JSONArray()
        repeat(samples) { index ->
            val root = checkNotNull(automation.rootInActiveWindow)
            val captured = try {
                check(app.owns(root.packageName?.toString())) { "Requested source left foreground" }
                SnapshotReader.read(root, app.packages)
            } finally { SnapshotReader.recycle(root) }
            check(!captured.typing) { "Editable context; trial stopped" }
            val frame = AdapterRegistry.of(app).read(captured)
            check(frame.surface != org.attentionstats.reels.Surface.REELS || frame.route != org.attentionstats.reels.ReelRoute.VIEWER) { "Expected feed, not viewer" }
            if (OperatorInspection.run(instrumentation, app.key).startsWith("CAPTURE")) {
                File(context.cacheDir, "beta-layout-${app.key}.xml").copyTo(File(folder, "layout-${index + 1}.xml"))
            }
            val image = checkNotNull(automation.takeScreenshot())
            try { File(folder, "sample-${index + 1}.jpg").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.JPEG, 75, it))
            } } finally { image.recycle() }
            SystemClock.sleep(1_500)
            val current = totals()
            val row = JSONObject().put("sample", index + 1).put("surface", frame.surface.name)
                .put("route", frame.route.name).put("category", frame.category.name)
                .put("mediaKind", frame.mediaKind).put("complete", captured.complete)
                .put("positivePhotoButtons", captured.nodes.count { node ->
                    node.cls.endsWith("Button") && node.bounds.width >= captured.screen.width * .18 &&
                        node.bounds.height >= captured.screen.height * .06 &&
                        listOf("photo", "may be an image of", "image:").any { node.label?.lowercase()?.startsWith(it) == true }
                })
                .put("hud", LiveHudVerification.state(instrumentation, automation).json())
                .put("focus", frame.page?.let { JSONObject().put("left", it.left).put("top", it.top)
                    .put("right", it.right).put("bottom", it.bottom) } ?: JSONObject.NULL)
                .put("countsDelta", JSONObject().apply { baseline.forEach { (key, start) -> put(key, current.getValue(key) - start) } })
            rows.put(row)
            File(folder, "results.json").writeText(JSONObject().put("samples", rows).toString())
            val progress = android.os.Bundle().apply { putString("stream", "Sample ${index + 1}: ${frame.surface}/${frame.category}/${frame.mediaKind}\n") }
            instrumentation.sendStatus(0, progress)
            if (index == samples - 1) return@repeat
            val bounds = captured.screen
            val x = bounds.left + bounds.width * .4f
            val from = bounds.top + bounds.height * (if (reverse) .13f else .77f)
            val to = bounds.top + bounds.height * (if (reverse) .77f else .13f)
            val down = SystemClock.uptimeMillis()
            for (step in 0..12) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, from + (to - from) * step / 12, 0)
                try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                SystemClock.sleep(25)
            }
            SystemClock.sleep(1_000)
        }
        val beforeIdle = totals()
        SystemClock.sleep(4_000)
        val afterIdle = totals()
        File(folder, "results.json").writeText(JSONObject().put("samples", rows)
            .put("idleDelta", afterIdle.values.sum() - beforeIdle.values.sum()).toString())
        val hudChecks = LiveHudVerification.run(instrumentation, automation)
        File(folder, "results.json").writeText(JSONObject().put("samples", rows)
            .put("idleDelta", afterIdle.values.sum() - beforeIdle.values.sum()).put("liveHudChecks", hudChecks).toString())
        return "$samples private feed samples captured; independent review must establish actual posts, duplicates and mismatches."
    }
}
