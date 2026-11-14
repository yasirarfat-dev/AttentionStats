package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.MainActivity
import com.attentionstats.bridge.HistoryPageFixture
import com.attentionstats.diary.DiaryEngine
import com.attentionstats.storage.HistoryStore
import com.attentionstats.storage.InterventionEvent
import com.facebook.react.bridge.Promise
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real RN history navigation with a separate encrypted synthetic store and delayed responses. */
@Suppress("DEPRECATION")
internal object HistoryNavigationVerification {
    fun run(instrumentation: Instrumentation): String {
        val target = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val folder = File(target.noBackupFilesDir, "history-navigation-${UUID.randomUUID()}")
        check(folder.mkdir())
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = folder }
        val alias = "attentionstats.historynavigation.${UUID.randomUUID()}"
        val executor = Executors.newSingleThreadExecutor()
        val before = listOf("appearance", "system-backup").associateWith { target.getSharedPreferences(it, 0).all.toMap() }
        val today = LocalDate.now()
        val first = today.minusDays(99)
        val zone = ZoneId.systemDefault()
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        fun date(day: LocalDate) = day.format(dateFormat)
        var checks = 0
        var store: HistoryStore? = null
        val lock = Any()
        var holdWindow: Int? = null
        var held: Pair<Promise, String>? = null
        val completed = AtomicInteger()
        fun expect(value: Boolean) { check(value) { "history navigation check ${checks + 1}" }; checks++ }
        fun visit(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val root = automation.rootInActiveWindow ?: return false
            if (root.packageName?.toString() != target.packageName) { root.recycle(); return false }
            val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
            try {
                var count = 0
                while (queue.isNotEmpty() && count++ < 2_000) {
                    val node = queue.removeFirst()
                    try {
                        if (node.packageName?.toString() != target.packageName || node.isPassword || node.isEditable) continue
                        if (predicate(node)) return true
                        for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
                    } finally { node.recycle() }
                }
                return false
            } finally { queue.forEach { it.recycle() } }
        }
        fun matches(node: AccessibilityNodeInfo, text: String) =
            node.text?.toString()?.equals(text, true) == true || node.contentDescription?.toString()?.equals(text, true) == true
        fun has(text: String) = visit { it.isVisibleToUser && matches(it, text) }
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { check(it.readBytes().isEmpty()) }
        }
        fun click(text: String) = visit {
            if (!it.isVisibleToUser || !it.isClickable || !it.isEnabled || !matches(it, text)) false
            else {
                val bounds = Rect().also(it::getBoundsInScreen)
                if (bounds.isEmpty) false else { shell("input touchscreen tap ${bounds.centerX()} ${bounds.centerY()}"); SystemClock.sleep(250); true }
            }
        }
        fun awaitReady(predicate: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 25_000
            while (SystemClock.elapsedRealtime() < until) {
                if (predicate()) { checks++; return }
                SystemClock.sleep(200)
            }
            error("history navigation bounded wait ${checks + 1}")
        }
        fun scroll(up: Boolean) {
            val bounds = Rect()
            expect(visit { it.getBoundsInScreen(bounds); !bounds.isEmpty })
            val x = bounds.left + bounds.width() / 8
            val low = bounds.top + bounds.height() * 3 / 5
            val high = bounds.top + bounds.height() * 3 / 10
            shell("input touchscreen swipe $x ${if (up) low else high} $x ${if (up) high else low} 300")
            SystemClock.sleep(300)
        }
        fun seekClick(text: String, up: Boolean = true) {
            repeat(18) { if (click(text)) { checks++; return }; scroll(up) }
            error("history navigation control not reached at ${checks + 1}")
        }
        fun capture(name: String) {
            SystemClock.sleep(800)
            expect(has("Insights"))
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(target.cacheDir, "history-navigation-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun releaseHeld() {
            val pair = synchronized(lock) { held.also { held = null; holdWindow = null } }
            checkNotNull(pair).let { it.first.resolve(it.second) }
        }
        try {
            val history = runBlocking {
                HistoryStore.open(context, alias).also { opened ->
                    store = opened
                    // Fresh isolated configuration has usage collection disabled. Only synthetic reminders are inserted.
                    check(opened.database.data().diary()?.usageEnabled == false)
                    for (offset in 0L..99L) opened.database.data().insertIntervention(InterventionEvent(
                        "synthetic-$offset", "instagram", "break", first.plusDays(offset).atStartOfDay(zone).toInstant().toEpochMilli(), 0, 0, 0))
                }
            }
            val diary = DiaryEngine(context) { first.atStartOfDay(zone).toInstant().toEpochMilli() }
            HistoryPageFixture.install { request, promise ->
                executor.execute {
                    try {
                        val raw = runBlocking { diary.historyRange(history, request.end, diary.fence.get(), { false },
                            request.start, request.through, request.size, true, request.window) }
                        val deferred = synchronized(lock) {
                            if (request.window == holdWindow && held == null) { held = promise to raw; true } else false
                        }
                        if (!deferred) promise.resolve(raw)
                        completed.incrementAndGet()
                    } catch (_: Exception) { promise.reject("STORAGE_UNAVAILABLE", "Synthetic history unavailable") }
                }
            }
            target.startActivity(Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitReady { click("Today") }
            expect(click("Insights"))
            awaitReady { has("About Video Encounters") }
            expect(click("All Time"))
            awaitReady { has("${date(first)} – ${date(today)}") }
            seekClick("By app")
            awaitReady { has("${date(today.minusDays(29))} – ${date(today)}") }
            seekClick("Older Dates")
            awaitReady { has("${date(today.minusDays(59))} – ${date(today.minusDays(30))}") }
            expect(has("Newer Dates"))
            seekClick("Older Dates")
            awaitReady { has("${date(today.minusDays(89))} – ${date(today.minusDays(60))}") }
            seekClick("Older Dates")
            awaitReady { has("${date(first)} – ${date(today.minusDays(90))}") }
            expect(visit { matches(it, "Older Dates") && !it.isEnabled })
            capture("oldest")
            seekClick("Newer Dates")
            awaitReady { has("${date(today.minusDays(89))} – ${date(today.minusDays(60))}") }
            seekClick("By day", up = false)
            seekClick("Choose Day · ${date(today.minusDays(60))}")
            awaitReady { has("Explore a Day") && has("Cancel") }
            expect(click("Cancel"))
            awaitReady { has("Choose Day · ${date(today.minusDays(60))}") }
            // Cross the lower edge of the currently loaded page through the actual day controls.
            repeat(30) { seekClick("Previous day") }
            awaitReady { has("Choose Day · ${date(today.minusDays(90))}") }
            seekClick("Next day")
            awaitReady { has("Choose Day · ${date(today.minusDays(89))}") }
            capture("cross-page")
            seekClick("Month", up = false)
            awaitReady { has("${date(today.minusDays(29))} – ${date(today)}") }
            // Arm only after warmup and the visible query have settled; then hold a new Week read.
            SystemClock.sleep(1_000)
            synchronized(lock) { holdWindow = 7 }
            expect(click("Week"))
            awaitReady { synchronized(lock) { held != null } }
            expect(click("All Time"))
            awaitReady { has("${date(first)} – ${date(today)}") }
            releaseHeld()
            SystemClock.sleep(1_000)
            expect(has("${date(first)} – ${date(today)}") && !has("${date(today.minusDays(6))} – ${date(today)}") && !has("History couldn’t refresh"))
            capture("late-range")
            // An old-generation completion must also be ignored after a real foreground return.
            synchronized(lock) { holdWindow = 7 }
            expect(click("Week"))
            awaitReady { synchronized(lock) { held != null } }
            shell("input keyevent KEYCODE_HOME")
            SystemClock.sleep(1_000)
            synchronized(lock) { holdWindow = null }
            val readsBefore = completed.get()
            target.startActivity(Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitReady { completed.get() > readsBefore && has("${date(today.minusDays(6))} – ${date(today)}") }
            releaseHeld()
            SystemClock.sleep(700)
            expect(has("${date(today.minusDays(6))} – ${date(today)}") && !has("${date(first)} – ${date(today)}") && !has("History couldn’t refresh"))
            for ((name, values) in before) expect(target.getSharedPreferences(name, 0).all == values)
            return "PASS: $checks synthetic-history actual RN paging/day-boundary/calendar-cancel/late-range/foreground checks; real history untouched; captures require review"
        } finally {
            HistoryPageFixture.clear()
            synchronized(lock) { held?.first?.reject("INTERRUPTED", "Synthetic scope ended"); held = null; holdWindow = null }
            executor.shutdown()
            check(executor.awaitTermination(30, TimeUnit.SECONDS))
            store?.close()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            check(folder.parentFile?.canonicalFile == target.noBackupFilesDir.canonicalFile)
            check(folder.deleteRecursively())
        }
    }
}
