package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.MainActivity
import com.attentionstats.diary.DiaryEngine
import com.attentionstats.diary.DiaryPresentationFixture
import com.attentionstats.storage.*
import kotlinx.coroutines.runBlocking
import org.attentionstats.reels.PlaybackEvidence
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Synthetic numerical evidence -> encrypted store -> real bridge/parser -> actual Today UI. */
@Suppress("DEPRECATION")
internal object PlaybackDetailsVerification {
    fun run(instrumentation: Instrumentation): String = runBlocking {
        val target = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val folder = File(target.noBackupFilesDir, "playback-details-${UUID.randomUUID()}")
        check(folder.mkdir())
        val alias = "attentionstats.playback.${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
        var store: HistoryStore? = null
        var checks = 0
        fun expect(ok: Boolean) { check(ok) { "playback details check ${checks + 1}" }; checks++ }
        fun visit(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val root = automation.rootInActiveWindow ?: return false
            if (root.packageName?.toString() != target.packageName) { root.recycle(); return false }
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            try {
                var count = 0
                while (queue.isNotEmpty() && count++ < 2_000) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != target.packageName || node.isPassword) continue
                        if (predicate(node)) return true
                        for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                    } finally { node.recycle() }
                }
                return false
            } finally { queue.forEach { it.recycle() } }
        }
        fun named(node: AccessibilityNodeInfo, label: String) = listOf(node.text, node.contentDescription)
            .any { it?.toString()?.equals(label, true) == true }
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                check(it.readBytes().isEmpty()) { "Own playback input failed" }
            }
        }
        fun tab(label: String) {
            val until = SystemClock.elapsedRealtime() + 25_000
            while (SystemClock.elapsedRealtime() < until) {
                if (visit { node ->
                    val b = Rect().also { node.getBoundsInScreen(it) }
                    if (named(node, label) && node.isClickable && node.isVisibleToUser && b.top > target.resources.displayMetrics.heightPixels * .7) {
                        shell("input touchscreen tap ${b.centerX()} ${b.centerY()}"); true
                    } else false
                }) { SystemClock.sleep(500); checks++; return }
                SystemClock.sleep(300)
            }
            error("Playback own tab unavailable")
        }
        fun scroll(up: Boolean) {
            expect(visit { it.isVisibleToUser })
            val density = target.resources.displayMetrics.density
            val height = target.resources.displayMetrics.heightPixels
            val x = (8 * density).toInt()
            val a = height * (if (up) 62 else 30) / 100
            val z = height * (if (up) 30 else 62) / 100
            shell("input touchscreen swipe $x $a $x $z 300"); SystemClock.sleep(300)
        }
        fun requireLabel(label: String) {
            repeat(24) { index ->
                // Large text makes the tab bar two rows tall; derive the real occlusion
                // boundary instead of accepting a statistic underneath the navigation.
                var bottom = Int.MAX_VALUE
                visit { node ->
                    val b = Rect().also { node.getBoundsInScreen(it) }
                    if ((named(node, "Today") || named(node, "Insights")) && node.isClickable &&
                        b.top > target.resources.displayMetrics.heightPixels / 2) {
                        // The custom bar has 6dp top padding plus its border. The tab
                        // button itself starts below the bar's occluding background.
                        bottom = minOf(bottom, b.top - (8 * target.resources.displayMetrics.density).toInt())
                        var parent = node.parent
                        var depth = 0
                        while (parent != null && depth++ < 6) {
                            val ancestor = parent
                            val rect = Rect().also { ancestor.getBoundsInScreen(it) }
                            if (rect.top > target.resources.displayMetrics.heightPixels / 2 &&
                                rect.width() >= target.resources.displayMetrics.widthPixels * .9) bottom = minOf(bottom, rect.top)
                            parent = ancestor.parent; ancestor.recycle()
                        }
                        parent?.recycle()
                    }
                    false
                }
                check(bottom != Int.MAX_VALUE) { "Playback content viewport unavailable" }
                if (visit { node ->
                    val b = Rect().also { node.getBoundsInScreen(it) }
                    named(node, label) && node.isVisibleToUser && b.top >= 70 && b.bottom < bottom - 12
                }) { checks++; return }
                scroll(index < 12)
            }
            error("Playback expected state unavailable: $label")
        }
        fun capture(name: String) {
            expect(visit { it.isVisibleToUser })
            val bitmap = checkNotNull(automation.takeScreenshot())
            if (!visit { it.isVisibleToUser }) { bitmap.recycle(); error("Own playback capture window changed") }
            File(target.cacheDir, "playback-details-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun render(snapshot: String, name: String, labels: List<String>) {
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Playback details stage: $name") })
            DiaryPresentationFixture.playback(snapshot)
            target.startActivity(Intent(target, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("attentionstats://today")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            tab("Settings"); tab("Today")
            labels.forEachIndexed { index, label -> requireLabel(label); if (index == 0) capture("$name-status") }
            capture(name)
        }
        try {
            val history = HistoryStore.open(context, alias).also { store = it }
            val dao = history.database.data()
            // The fixture is a returning-user Today scenario, not a fresh welcome tour.
            dao.putDiary(checkNotNull(dao.diary()).copy(onboardingSeen = true))
            val diary = DiaryEngine(context)
            val zone = ZoneId.systemDefault()
            val at = maxOf(LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() + 1, System.currentTimeMillis() - 30_000)
            dao.insertEpisode(SurfaceEpisode("synthetic-visit", "instagram", "viewer", at, null, 1, "synthetic"))
            dao.insertExposure(Exposure("synthetic-video", "synthetic-visit", "instagram", at, 0, 1, 1, "synthetic", 1,
                "synthetic", zone.id, 0, category = "reel", mediaKind = "video"))
            suspend fun snapshot() = diary.snapshot(history, 1, collectFirst = false)
            fun row(snapshot: String): JSONObject = JSONObject(snapshot).getJSONArray("days").getJSONObject(0).getJSONArray("apps")
                .let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.optString("social") == "instagram" } }
            val off = snapshot()
            expect(row(off).isNull("watchCount") && row(off).isNull("completionCount") && row(off).isNull("playbackMs"))
            render(off, "disabled", listOf("Playback measurement is off · enable in Settings.", "3-second watches: —", "Observed completions: —"))
            expect(diary.configure(history, "playback", true, 30, 15, checkNotNull(dao.diary()).revision))
            val unknown = snapshot()
            expect(row(unknown).isNull("measuredVideoCount"))
            render(unknown, "unknown", listOf("No readable playback times recorded for this period.", "Confirmed playback: —"))
            val evidence = PlaybackEvidence()
            suspend fun persist(result: PlaybackEvidence.Result) {
                dao.updatePlayback("synthetic-video", result.confirmedMs, result.qualified, result.completed); dao.advanceRevision()
            }
            persist(checkNotNull(evidence.sample(0, 0, 4_000)))
            val zero = snapshot()
            expect(row(zero).getString("measuredVideoCount") == "1" && row(zero).getString("watchCount") == "0")
            render(zero, "zero", listOf("3-second watches: 0", "Observed completions: 0", "Confirmed playback: 0s", "Measured visits: 1"))
            for (i in 1..4) persist(checkNotNull(evidence.sample(i * 1_000L, i * 1_000L, 4_000)))
            val complete = snapshot()
            expect(row(complete).getString("watchCount") == "1" && row(complete).getString("completionCount") == "1" && row(complete).getString("playbackMs") == "4000")
            render(complete, "complete", listOf("3-second watches: 1", "Observed completions: 1", "Confirmed playback: 4s", "Measured visits: 1"))
            expect(diary.configure(history, "playback", false, 30, 15, checkNotNull(dao.diary()).revision))
            val retained = snapshot()
            expect(row(retained).getString("completionCount") == "1")
            render(retained, "retained", listOf("Playback measurement is off · enable in Settings.", "3-second watches: 1", "Observed completions: 1", "Confirmed playback: 4s"))
            val malformed = JSONObject(retained).put("days", org.json.JSONArray()).toString()
            render(malformed, "read-error", listOf("Viewing details could not refresh. Saved history is preserved.", "Observed completions: 1"))
            render(retained, "recovered", listOf("Playback measurement is off · enable in Settings.", "Observed completions: 1", "Confirmed playback: 4s"))
        } finally {
            DiaryPresentationFixture.clear()
            store?.close()
            check(folder.canonicalPath.startsWith(target.noBackupFilesDir.canonicalPath + File.separator))
            check(folder.deleteRecursively() && !folder.exists())
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(!keys.containsAlias(alias))
        }
        "PASS: $checks isolated playback store/actual Today checks; fixture/store/key cleared; synthetic evidence only"
    }
}
