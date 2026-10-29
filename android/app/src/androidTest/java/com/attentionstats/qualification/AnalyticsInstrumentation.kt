package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.attentionstats.analytics.main as verifyAnalytics
import org.attentionstats.reels.main as verifyReels

/** Synthetic APK qualifications; the default mode preserves the original analytics suite. */
class AnalyticsInstrumentation : Instrumentation() {
    private var qualification: String? = null
    private var operatorAuthorized = false
    private var playbackAuthorized = false
    private var autoScrollAuthorized = false
    private var restoreAuthorized = false
    private var progressApp = "instagram"
    private var comparisonPackage: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        qualification = arguments?.getString("qualification")
        progressApp = arguments?.getString("progress_app") ?: "instagram"
        comparisonPackage = arguments?.getString("comparison_package")?.also {
            check(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*){1,8}").matches(it))
        }
        operatorAuthorized = arguments?.getString("operator_authorized") == "adb-reels-structure"
        playbackAuthorized = arguments?.getString("operator_authorized") == "adb-reels-playback"
        autoScrollAuthorized = arguments?.getString("operator_authorized") == "adb-reels-autoscroll"
        restoreAuthorized = arguments?.getString("operator_authorized") == "adb-reels-restore-service"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var probe: SelfUsageProbe? = null
        var reels: ReelsStorageProbe? = null
        var ui: ReelsUiProbe? = null
        try {
            when (qualification) {
                "reels-home-session-operator" -> {
                    check(operatorAuthorized && com.attentionstats.BuildConfig.DEBUG)
                    result.putAll(HomeSessionProbe.verify(this, progressApp))
                }
                "reels-media-session-operator" -> {
                    check(playbackAuthorized && com.attentionstats.BuildConfig.DEBUG)
                    val automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    try {
                        automation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
                        sendStatus(0, Bundle().apply { putInt("media_ready", 1) })
                        val until = android.os.SystemClock.elapsedRealtime() + 60_000
                        while (android.os.SystemClock.elapsedRealtime() < until) {
                            sendStatus(0, Bundle().apply { putString("media_numeric", MediaPlaybackProbe.inspect(this@AnalyticsInstrumentation, automation).toString()) })
                            sendStatus(0, Bundle().apply { putString("numeric_controls", NumericPlaybackProbe.inspect(this@AnalyticsInstrumentation, automation).toString()) })
                            android.os.SystemClock.sleep(500)
                        }
                        result.putString("stream", "PASS: numeric media session investigation\n")
                    } finally { automation.dropShellPermissionIdentity() }
                }
                "reels-numeric-controls-operator" -> {
                    check(playbackAuthorized && com.attentionstats.BuildConfig.DEBUG)
                    val automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    sendStatus(0, Bundle().apply { putInt("numeric_ready", 1) })
                    val until = android.os.SystemClock.elapsedRealtime() + 60_000
                    while (android.os.SystemClock.elapsedRealtime() < until) {
                        sendStatus(0, Bundle().apply { putString("numeric_controls", NumericPlaybackProbe.inspect(this@AnalyticsInstrumentation, automation).toString()) })
                        android.os.SystemClock.sleep(500)
                    }
                    result.putString("stream", "PASS: bounded numeric controls investigation\n")
                }
                "reels-restore-service-operator" -> {
                    check(restoreAuthorized && com.attentionstats.BuildConfig.DEBUG)
                    check(ServiceRestoreProbe.restore(this))
                    result.putString("stream", "PASS: authorized own-service restoration\n")
                }
                "reels-autoscroll-operator" -> {
                    check(autoScrollAuthorized && com.attentionstats.BuildConfig.DEBUG)
                    val coordinator = com.attentionstats.reels.ReelsCoordinator.get(targetContext)
                    fun snapshot(): org.json.JSONObject {
                        val done = java.util.concurrent.CountDownLatch(1)
                        var value: org.json.JSONObject? = null
                        coordinator.snapshot({ value = org.json.JSONObject(it); done.countDown() }, { done.countDown() })
                        check(done.await(15, java.util.concurrent.TimeUnit.SECONDS))
                        return checkNotNull(value)
                    }
                    fun setPaused(value: Boolean) {
                        val revision = snapshot().getString("revision")
                        val done = java.util.concurrent.CountDownLatch(1)
                        var success = false
                        coordinator.setPaused(value, revision, { success = true; done.countDown() }, { done.countDown() })
                        check(done.await(15, java.util.concurrent.TimeUnit.SECONDS) && success)
                    }
                    val before = snapshot()
                    val sources = before.getJSONArray("sources")
                    val selected = (0 until sources.length()).map { sources.getJSONObject(it) }
                        .single { it.getString("app") == progressApp }
                    check(selected.getBoolean("enabled") && selected.getBoolean("consentCurrent"))
                    val restorePause = selected.getBoolean("paused")
                    var startedCount: Long? = null
                    fun currentCount(): Long {
                        val rows = snapshot().getJSONArray("sources")
                        return (0 until rows.length()).map { rows.getJSONObject(it) }
                            .single { it.getString("app") == progressApp }.getString("today").toLong()
                    }
                    if (restorePause) check((0 until sources.length()).map { sources.getJSONObject(it) }
                        .filter { it.getBoolean("enabled") }.all { it.getBoolean("paused") })
                    ui = ReelsUiProbe(this)
                    val navigationReady = java.io.File(targetContext.noBackupFilesDir, "reels-auto-operator-ready")
                    check(!navigationReady.exists() || navigationReady.delete())
                    try {
                        if (restorePause) setPaused(false)
                        ui.operatorAllowAutoScroll()
                        sendStatus(0, Bundle().apply { putInt("auto_controls_ready", 1) })
                        var startReported = false
                        var stopReported = false
                        var startedAt = 0L
                        var completed = false
                        var lastNavigationSample = 0L
                        val until = android.os.SystemClock.elapsedRealtime() + 210_000
                        while (android.os.SystemClock.elapsedRealtime() < until && !completed) {
                            val auto = com.attentionstats.autoscroll.AutoScrollSession
                            if (android.os.SystemClock.elapsedRealtime() - lastNavigationSample >= 1_000) {
                                lastNavigationSample = android.os.SystemClock.elapsedRealtime()
                                sendStatus(0, Bundle().apply { putString("auto_navigation",
                                    com.attentionstats.reels.ReelsAccessibilityService.inspectPlaybackForOperator().toString()) })
                                sendStatus(0, Bundle().apply { putString("auto_runtime", org.json.JSONObject()
                                    .put("running", auto.running).put("reason", auto.reason)
                                    .put("attempts", auto.attempts).put("confirmed", auto.confirmedTransitions).toString()) })
                            }
                            if (navigationReady.isFile && !startReported && com.attentionstats.reels.ReelsCoordinator.get(targetContext).liveOverlay?.app?.key == progressApp) {
                                ui.operatorAutoControlBounds("Start auto-scroll")?.let { bounds ->
                                    sendStatus(0, Bundle().apply { putInt("auto_start_x", bounds[0]); putInt("auto_start_y", bounds[1]) })
                                    startReported = true
                                }
                            }
                            if (auto.running && startedAt == 0L) {
                                startedAt = android.os.SystemClock.elapsedRealtime()
                                startedCount = currentCount()
                                sendStatus(0, Bundle().apply { putInt("auto_started", 1) })
                            }
                            if (startedAt > 0 && auto.confirmedTransitions >= 1 && !stopReported) {
                                ui.operatorAutoControlBounds("Stop auto-scroll")?.let { bounds ->
                                    sendStatus(0, Bundle().apply { putInt("auto_stop_x", bounds[0]); putInt("auto_stop_y", bounds[1]) })
                                    stopReported = true
                                }
                            }
                            if (stopReported && !auto.running) {
                                check(auto.attempts == 1 && auto.confirmedTransitions == 1)
                                check(auto.reason == "stopped") // A trailing pager event is not proof that Stop worked.
                                result.putInt("auto_attempts", auto.attempts)
                                result.putInt("auto_confirmed", auto.confirmedTransitions)
                                completed = true
                            }
                            if (startedAt > 0 && !auto.running && !stopReported)
                                error("Auto-scroll stopped before confirmed transition: " + auto.reason)
                            android.os.SystemClock.sleep(250)
                        }
                        check(completed)
                        result.putString("stream", "PASS: authorized auto-scroll end and stop controls\n")
                    } finally {
                        navigationReady.delete()
                        result.putInt("auto_attempts", com.attentionstats.autoscroll.AutoScrollSession.attempts)
                        result.putInt("auto_confirmed", com.attentionstats.autoscroll.AutoScrollSession.confirmedTransitions)
                        result.putString("auto_last_reason", com.attentionstats.autoscroll.AutoScrollSession.reason)
                        runOnMainSync { com.attentionstats.autoscroll.AutoScrollSession.clear() }
                        if (restorePause) setPaused(true)
                        startedCount?.let {
                            val delta = currentCount() - it
                            // A day boundary or concurrent deletion invalidates this diagnostic.
                            if (delta in 0..100) result.putInt("auto_exposures_added", delta.toInt())
                        }
                        val after = snapshot().getJSONArray("sources")
                        for (index in 0 until sources.length()) for (flag in listOf("enabled", "paused", "consentCurrent"))
                            check(sources.getJSONObject(index).getBoolean(flag) == after.getJSONObject(index).getBoolean(flag))
                        result.putInt("auto_source_choices_preserved", 1)
                    }
                }
                "reels-autoscroll" -> {
                    result.putInt("autoscroll_checks", AutoScrollProbe.verify() + AutoRuntimeProbe.verify(this))
                    result.putString("stream", "PASS: synthetic auto-scroll decision safety\n")
                }
                "reels-playback-probe" -> {
                    check(playbackAuthorized && com.attentionstats.BuildConfig.DEBUG)
                    sendStatus(0, Bundle().apply { putInt("playback_ready", 1) })
                    val probeUntil = android.os.SystemClock.elapsedRealtime() + 75_000
                    while (android.os.SystemClock.elapsedRealtime() < probeUntil) {
                        val sample = com.attentionstats.reels.ReelsAccessibilityService.inspectPlaybackForOperator()
                        sendStatus(0, Bundle().apply { putString("playback", sample.toString()) })
                        android.os.SystemClock.sleep(500)
                    }
                    result.putString("stream", "PASS: numeric playback investigation\n")
                }
                null -> {
                    verifyAnalytics(emptyArray())
                    result.putString("stream", "PASS: synthetic analytics APK checks completed\n")
                }
                "usage-self" -> {
                    probe = SelfUsageProbe(this)
                    result.putString("stream", probe.verify())
                    probe.measurements.forEach { (key, value) -> result.putLong(key, value) }
                }
                "reels-storage" -> {
                    verifyReels()
                    result.putInt("detector_checks", DetectorProbe.verify())
                    reels = ReelsStorageProbe(this)
                    reels.verify()
                    result.putInt("storage_checks", reels.checks)
                    result.putInt("key_rejections", reels.keyRejections)
                    result.putInt("sentinel_files", reels.sentinelFiles)
                    result.putInt("fixture_removed", if (reels.removed) 1 else 0)
                    result.putString("stream", "PASS: synthetic main-app Reels storage checks\n")
                }
                "reels-ui" -> {
                    ui = ReelsUiProbe(this)
                    ui.verify()
                    result.putInt("ui_checks", ui.checks)
                    result.putInt("shell_routes", ui.routes)
                    result.putString("stream", "PASS: own-window React Native preview screens\n")
                }
                "reels-operator-structure" -> {
                    check(operatorAuthorized)
                    val coordinator = com.attentionstats.reels.ReelsCoordinator.get(targetContext)
                    check(!coordinator.calibrationEnabled)
                    try {
                        ui = ReelsUiProbe(this)
                        ui.operatorStartStructure()
                        check(coordinator.calibrationEnabled)
                        sendStatus(0, Bundle().apply { putInt("diagnostic_ready", 1) })
                        val comparison = comparisonPackage?.let {
                            CounterComparisonProbe(getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES), it)
                        }
                        if (comparison == null) android.os.SystemClock.sleep(60_000)
                        else {
                            val until = android.os.SystemClock.elapsedRealtime() + 120_000
                            while (android.os.SystemClock.elapsedRealtime() < until) {
                                sendStatus(0, Bundle().apply { putString("comparison", comparison.inspect().toString()) })
                                android.os.SystemClock.sleep(750)
                            }
                        }
                        result.putString("stream", "PASS: authorized own-window structure controls\n")
                    } finally { coordinator.setCalibration(false) }
                }
                "reels-live-progress" -> {
                    // Explicitly operator-authorized trial only. No source/grant changes or target reads/actions.
                    val coordinator = com.attentionstats.reels.ReelsCoordinator.get(targetContext)
                    val alreadyStopped = !coordinator.calibrationEnabled
                    val previousEpoch = coordinator.epoch()
                    coordinator.setCalibration(false) // End an active trial; an already-stopped check is idempotent.
                    check(!alreadyStopped || coordinator.epoch() == previousEpoch)
                    fun snapshot(): org.json.JSONObject {
                        val done = java.util.concurrent.CountDownLatch(1)
                        var value: org.json.JSONObject? = null
                        coordinator.snapshot({ value = org.json.JSONObject(it); done.countDown() }, { done.countDown() })
                        check(done.await(15, java.util.concurrent.TimeUnit.SECONDS))
                        return checkNotNull(value)
                    }
                    check(org.attentionstats.reels.ReelApp.byKey(progressApp) != null)
                    fun selectedSource(value: org.json.JSONObject): org.json.JSONObject {
                        val sources = value.getJSONArray("sources")
                        return (0 until sources.length()).map { sources.getJSONObject(it) }
                            .single { it.getString("app") == progressApp }
                    }
                    val before = snapshot()
                    val source = selectedSource(before)
                    check(before.getBoolean("accessibilityEnabled") && before.getBoolean("serviceConnected") &&
                        before.getBoolean("overlayEnabled") && before.getString("storage") == "ready" &&
                        source.getBoolean("enabled") && source.getBoolean("consentCurrent") &&
                        source.getBoolean("supported") && !source.getBoolean("paused"))
                    sendStatus(0, Bundle().apply { putInt("progress_ready", 1) })
                    result.putInt("before_home_route", if (coordinator.liveOverlay?.route == org.attentionstats.reels.ReelRoute.HOME) 1 else 0)
                    result.putInt("before_home_visit_committed", if (coordinator.liveOverlay?.route == org.attentionstats.reels.ReelRoute.HOME &&
                        (source.optString("visit", "0").toLongOrNull() ?: 0) > 0) 1 else 0)
                    val progressUntil = android.os.SystemClock.elapsedRealtime() + 12_000
                    while (android.os.SystemClock.elapsedRealtime() < progressUntil) {
                        sendStatus(0, Bundle().apply { putString("home_scan", com.attentionstats.reels.ReelsAccessibilityService.inspectHomeForOperator().toString()) })
                        android.os.SystemClock.sleep(1_000)
                    }
                    val after = snapshot()
                    check(before.getString("revision") == after.getString("revision") &&
                        before.getString("day") == after.getString("day"))
                    val final = selectedSource(after)
                    val delta = final.getString("today").toLong() - source.getString("today").toLong()
                    check(delta in 0..10)
                    val categories = final.getJSONObject("counts")
                    val sum = listOf("reel", "photo", "ad", "unclassified").sumOf { categories.getString(it).toLong() }
                    check(sum == final.getString("today").toLong())
                    result.putInt("controlled_exposures_added", delta.toInt())
                    result.putInt("category_totals_conserved", 1)
                    result.putInt("after_home_route", if (coordinator.liveOverlay?.route == org.attentionstats.reels.ReelRoute.HOME) 1 else 0)
                    result.putString("stream", "PASS: authorized live aggregate progress check\n")
                }
                "reels-health", "reels-show-counter" -> {
                    val done = java.util.concurrent.CountDownLatch(1)
                    var snapshot: org.json.JSONObject? = null
                    com.attentionstats.reels.ReelsCoordinator.get(targetContext).snapshot({
                        snapshot = org.json.JSONObject(it); done.countDown()
                    }, { done.countDown() })
                    check(done.await(30, java.util.concurrent.TimeUnit.SECONDS))
                    var value = checkNotNull(snapshot)
                    if (qualification == "reels-show-counter") check(!value.getBoolean("calibrationEnabled"))
                    if (qualification == "reels-show-counter" && !value.getBoolean("overlayEnabled")) {
                        // User-directed display opt-in only; never enables a source, diagnostics or OS grant.
                        val applied = java.util.concurrent.CountDownLatch(1)
                        snapshot = null
                        com.attentionstats.reels.ReelsCoordinator.get(targetContext).setOverlay(true,
                            value.getString("revision"), { snapshot = org.json.JSONObject(it); applied.countDown() },
                            { applied.countDown() })
                        check(applied.await(30, java.util.concurrent.TimeUnit.SECONDS))
                        value = checkNotNull(snapshot)
                    }
                    for (key in listOf("accessibilityEnabled", "serviceConnected", "calibrationEnabled", "overlayEnabled"))
                        result.putInt(key, if (value.getBoolean(key)) 1 else 0)
                    result.putInt("storageReady", if (value.getString("storage") == "ready") 1 else 0)
                    val sources = value.getJSONArray("sources")
                    var enabled = 0
                    var paused = 0
                    for (index in 0 until sources.length()) {
                        val item = sources.getJSONObject(index)
                        val bit = if (item.getString("app") == "instagram") 1 else 2
                        if (item.getBoolean("enabled")) enabled = enabled or bit
                        if (item.getBoolean("paused")) paused = paused or bit
                    }
                    result.putInt("enabledSources", enabled)
                    result.putInt("pausedSources", paused)
                    result.putString("stream", "PASS: bounded preview control health\n")
                }
                else -> error("Unknown qualification mode")
            }
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            // Bounded code locations only: never exception messages, target values or arbitrary logs.
            if (ui != null) {
                val locations = failure.stackTrace.filter {
                    it.className.startsWith("com.attentionstats.qualification.") ||
                        it.className.startsWith("android.")
                }.take(8).joinToString(";") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                result.putString("probe_location", locations)
                result.putInt("probe_interactive", if (targetContext.getSystemService(android.os.PowerManager::class.java).isInteractive) 1 else 0)
            }
            ui?.let { result.putString("probe_stage", it.stage) }
            reels?.let { result.putString("probe_stage", it.stage) }
            probe?.let {
                result.putString("probe_stage", it.stage)
                result.putInt("fixture_resumed", it.resumedFixtures)
                result.putInt("fixture_paused", it.pausedFixtures)
            }
            // Fixtures are synthetic; return only the failure type, never arbitrary runtime messages.
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
