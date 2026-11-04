package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.detection.AdapterFrame
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit operator run only. Samples stay in memory; output contains classifications/count deltas only. */
internal object LiveSourceVerification {
    fun run(instrumentation: Instrumentation, key: String): String {
        val app = checkNotNull(ReelApp.byKey(key))
        val context = instrumentation.targetContext
        val owner = ReelsCoordinator.get(context)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        var viewport: org.attentionstats.detection.LayoutBounds? = null
        fun snapshot(): JSONObject {
            val latch = CountDownLatch(1); var raw: String? = null
            owner.snapshot({ raw = it; latch.countDown() }, { latch.countDown() })
            check(latch.await(10, TimeUnit.SECONDS))
            val rows = JSONObject(checkNotNull(raw)).getJSONArray("sources")
            return (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("app") == app.key }
        }
        fun counts(): Map<String, Long> {
            val data = snapshot().getJSONObject("counts")
            return listOf("reel", "photo", "ad", "unclassified").associateWith { data.getString(it).toLong() }
        }
        fun frame(): AdapterFrame {
            val root = automation.rootInActiveWindow ?: return AdapterFrame.OUTSIDE
            try {
                if (!app.owns(root.packageName?.toString())) return AdapterFrame.OUTSIDE
                val captured = SnapshotReader.read(root, app.packages)
                viewport = captured.screen
                return AdapterRegistry.of(app).read(captured)
            } finally { SnapshotReader.recycle(root) }
        }
        snapshot() // Open/reload the normal native store first.
        // The operator opens the exact surface first. Relaunching Snapchat resets its selected tab.
        SystemClock.sleep(3_000)
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!owner.serviceConnected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
        val connected = owner.serviceConnected && owner.grantEnabled()
        if (!connected || !owner.allowed(app)) return "UNVERIFIED $key: connected=$connected allowed=${owner.allowed(app)}"
        val initial = frame()
        if (initial.surface != Surface.REELS) return "UNVERIFIED $key: current surface=${initial.surface}; open the requested source surface first"
        val before = counts()
        fun diaryPhotos(): Long {
            val latch = CountDownLatch(1); var raw: String? = null
            owner.diarySnapshot(1, { raw = it; latch.countDown() }, { latch.countDown() })
            check(latch.await(10, TimeUnit.SECONDS))
            val apps = JSONObject(checkNotNull(raw)).getJSONArray("days").getJSONObject(0).getJSONArray("apps")
            return (0 until apps.length()).map { apps.getJSONObject(it) }
                .first { it.optString("social") == app.key }.getString("photoCount").toLong()
        }
        val initialPhotoTotal = diaryPhotos()
        SystemClock.sleep(2_000)
        val afterIdle = counts()
        var moved = 0
        var same = 0
        var photos = if (initial.mediaKind == "photo") 1 else 0
        repeat(5) {
            val current = frame()
            val feed = initial.route == org.attentionstats.reels.ReelRoute.HOME
            val page = if (feed) viewport else current.page
            if (current.method == "typing" || current.surface == Surface.UNKNOWN || page == null || page.empty ||
                (!feed && current.surface != Surface.REELS)) return@repeat
            val x = page.left + page.width * 0.4f
            val y1 = page.top + page.height * 0.78f
            val y2 = page.top + page.height * 0.25f
            val down = SystemClock.uptimeMillis()
            for (step in 0..12) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y1 + (y2 - y1) * step / 12f, 0)
                try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                SystemClock.sleep(25)
            }
            SystemClock.sleep(1_400)
            val next = frame()
            if (next.mediaKind == "photo") photos++
            if (next.identity != null && next.identity != current.identity) moved++ else same++
        }
        val after = counts()
        val delta = before.keys.joinToString(",") { "$it=${after.getValue(it) - afterIdle.getValue(it)}" }
        return "LIVE $key: initial=${initial.category}/${initial.route}/${initial.mediaKind}, idleDelta=${afterIdle.values.sum() - before.values.sum()}, observedItemChanges=$moved, unchangedOrUnknown=$same, photoLayouts=$photos, initialCount=${before.values.sum()}, newCounts[$delta], photoFacet=$initialPhotoTotal->${diaryPhotos()}, service=${owner.acquisitionStatus}, reducer=${owner.observationStatus}, granted=${owner.grantEnabled()}"
    }
}
