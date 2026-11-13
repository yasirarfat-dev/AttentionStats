package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.detection.AdapterFrame
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual HUD Start, up to three consecutive automatic transitions, then actual Stop. No source input. */
internal object LiveAutoScrollContinuousVerification {
    fun run(instrumentation: Instrumentation, key: String, adOnly: Boolean = false, mixedAds: Boolean = false): String {
        val required = if (adOnly) 1 else 3
        val owner = ReelsCoordinator.get(instrumentation.targetContext)
        val app = checkNotNull(ReelApp.byKey(key))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // The operator reconnects only this app's service after instrumentation attaches.
        // Finish that setup before Start; teardown from the prior binding must not clear a new run.
        SystemClock.sleep(3_000)
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            failure?.let { throw it }
        }
        fun await(call: ((String) -> Unit, (String) -> Unit) -> Unit): JSONObject {
            val done = CountDownLatch(1); var raw: String? = null
            call({ raw = it; done.countDown() }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS)) { "native read timeout" }
            return JSONObject(checkNotNull(raw) { "native read failed" })
        }
        val original = await { ok, bad -> owner.diarySnapshot(1, ok, bad) }
        fun reminders(enabled: Boolean) {
            val current = await { ok, bad -> owner.diarySnapshot(1, ok, bad) }
            await { ok, bad -> owner.configureDiary("reminder", enabled, original.getInt("reminderCount"),
                original.getInt("reminderMinutes"), current.getString("revision").toLong(), ok, bad) }
        }
        fun frame(): AdapterFrame {
            val until = SystemClock.elapsedRealtime() + 3_000
            do {
                val focused = automation.windows.firstOrNull {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
                }?.root
                val root = focused ?: automation.rootInActiveWindow
                if (root != null) {
                    try {
                        check(app.owns(root.packageName?.toString())) { "selected source left foreground" }
                        val snapshot = SnapshotReader.read(root, app.packages)
                        check(snapshot.complete && !snapshot.typing) { "incomplete/editable source" }
                        return AdapterRegistry.of(app).read(snapshot)
                    } finally { SnapshotReader.recycle(root) }
                }
                SystemClock.sleep(100)
            } while (SystemClock.elapsedRealtime() < until)
            error("source window unavailable")
        }
        val connectedUntil = SystemClock.elapsedRealtime() + 12_000
        while (!owner.serviceConnected && SystemClock.elapsedRealtime() < connectedUntil) SystemClock.sleep(100)
        check(owner.serviceConnected && owner.allowed(app) && owner.overlayEnabled) { "service/source/HUD unavailable" }
        val initial = frame()
        check(initial.surface == Surface.REELS && initial.route == ReelRoute.VIEWER && initial.identity != null) {
            "settled dedicated viewer required before actual Start"
        }
        check(!adOnly || initial.standaloneAd) { "qualified standalone ad required before actual Start" }
        check(owner.blockDecision(app, initial, app.packages.first()) == null) { "active source protection preserved" }
        // Observe rather than bypass whole-app schedules/focus: a visible unpaused own HUD is required.
        // Production enforcement remains active throughout; saved schedules are never modified here.
        var wasEnabled = false; var hadNotice = false; var hadAdChoice = false
        main {
            check(!AutoScrollSession.running) { "existing active run preserved" }
            wasEnabled = AutoScrollSession.enabled; hadNotice = AutoScrollSession.noticeAccepted
            hadAdChoice = AutoScrollSession.skipStandaloneAds
        }
        var expandedByTest = false
        val stages = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val trace = java.util.Collections.synchronizedList(mutableListOf<String>())
        val traceStart = SystemClock.elapsedRealtime()
        var previousHook: ((String) -> Unit)? = null
        var samples = 0; var numeric = 0; var unknown = 0; var independent = 0
        var previous = initial
        val kinds = mutableMapOf<String, Int>()
        try {
            reminders(false)
            main {
                previousHook = AutoScrollSession.onDispatchDiagnostic
                AutoScrollSession.onDispatchDiagnostic = {
                    stages.merge(it, 1, Int::plus)
                    if (trace.size < 160) trace.add("${SystemClock.elapsedRealtime()-traceStart}:$it")
                }
                AutoScrollSession.configure(true, 2)
                if (adOnly || mixedAds) AutoScrollSession.configureAds(true, 2)
            }
            val hudUntil = SystemClock.elapsedRealtime() + 5_000
            var hud = LiveHudVerification.state(instrumentation, automation)
            while (hud.header == null && SystemClock.elapsedRealtime() < hudUntil) {
                SystemClock.sleep(100); hud = LiveHudVerification.state(instrumentation, automation)
            }
            check(hud.header != null && !hud.paused) { "visible unpaused HUD required" }
            expandedByTest = !hud.expanded
            if (expandedByTest) LiveHudVerification.choose(instrumentation, automation, "header")
            LiveHudVerification.choose(instrumentation, automation, "off")
            check(AutoScrollSession.running) { "actual HUD Start failed" }
            instrumentation.sendStatus(1, android.os.Bundle().apply {
                putString("stream", "CONTINUOUS_STARTED: actual HUD On; source=$key; setupSwipes=0; requiredTransitions=$required; standaloneAd=$adOnly")
            })
            val start = SystemClock.elapsedRealtime()
            var nextStatus = start
            var nextScan = start
            while (SystemClock.elapsedRealtime() - start < (if (adOnly) 15_000 else 180_000) && independent < required) {
                check(owner.allowed(app) && owner.serviceConnected && AutoScrollSession.running) { "source/service/run interrupted" }
                val now = SystemClock.elapsedRealtime()
                if (now >= nextScan) {
                    val current = frame(); samples++
                    check(owner.blockDecision(app, current, app.packages.first()) == null) { "active source protection preserved" }
                    if (current.surface == Surface.REELS && current.route == ReelRoute.VIEWER) {
                        kinds.merge("${current.category.name}/${current.mediaKind}", 1, Int::plus)
                        if (current.progress == null) unknown++ else numeric++
                        if (AutoScrollSession.confirmedTransitions > independent && current.identity != null && current.identity != previous.identity) {
                            check(AutoScrollSession.confirmedTransitions == independent + 1) { "missed independent arrival" }
                            independent++; previous = current
                        }
                    }
                    nextScan = now + 1_000
                }
                check(AutoScrollSession.attempts <= required) { "more than allowed automatic attempts; attempts=${AutoScrollSession.attempts}; confirmed=${AutoScrollSession.confirmedTransitions}; stages=$stages" }
                if (now >= nextStatus) {
                    instrumentation.sendStatus(1, android.os.Bundle().apply {
                        putString("stream", "Continuous seconds=${(now-start)/1_000}; attempts=${AutoScrollSession.attempts}; confirmed=${AutoScrollSession.confirmedTransitions}; independent=$independent; numeric=$numeric; unavailable=$unknown; reason=${AutoScrollSession.reason}; stages=$stages")
                    })
                    nextStatus = now + 5_000
                }
                SystemClock.sleep(200)
            }
            LiveHudVerification.choose(instrumentation, automation, "on")
            check(!AutoScrollSession.running) { "actual HUD Stop failed" }
            val attemptsAtStop = AutoScrollSession.attempts
            SystemClock.sleep(2_000)
            check(!AutoScrollSession.running && AutoScrollSession.attempts == attemptsAtStop) { "Stop did not fence later actions" }
            instrumentation.sendStatus(1, android.os.Bundle().apply { putString("stream", "Ordered fixed-code trace=$trace") })
            check(independent == required && attemptsAtStop == required && AutoScrollSession.confirmedTransitions == required) {
                "continuous scrolling not achieved; seconds=${(SystemClock.elapsedRealtime()-start)/1_000}; attempts=$attemptsAtStop; confirmed=${AutoScrollSession.confirmedTransitions}; independent=$independent; samples=$samples; numeric=$numeric; unavailable=$unknown; kinds=$kinds; stages=$stages"
            }
            return "PASS: actual HUD Start/$required independent automatic arrivals/Stop; source=$key; standaloneAd=$adOnly; sourceSetupSwipes=0; samples=$samples; numeric=$numeric; kinds=$kinds"
        } finally {
            try {
                val hud = LiveHudVerification.state(instrumentation, automation)
                if (AutoScrollSession.running && hud.controls.singleOrNull()?.first == "on")
                    LiveHudVerification.choose(instrumentation, automation, "on")
                main { AutoScrollSession.stop() }
                if (expandedByTest && LiveHudVerification.state(instrumentation, automation).expanded)
                    LiveHudVerification.choose(instrumentation, automation, "header")
            } finally {
                main {
                    AutoScrollSession.onDispatchDiagnostic = previousHook
                    AutoScrollSession.clear()
                    if (hadNotice || wasEnabled) {
                        AutoScrollSession.configure(true, 2)
                        if (hadAdChoice) AutoScrollSession.configureAds(true, 2)
                        if (!wasEnabled) AutoScrollSession.configure(false, 2)
                    }
                }
                reminders(original.getBoolean("reminderEnabled"))
            }
        }
    }
}
