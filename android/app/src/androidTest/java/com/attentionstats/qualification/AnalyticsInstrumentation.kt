package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.attentionstats.analytics.main as verifyAnalytics
import org.attentionstats.reels.main as verifyReels

/** Synthetic APK qualifications; the default mode preserves the original analytics suite. */
class AnalyticsInstrumentation : Instrumentation() {
    private var qualification: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        qualification = arguments?.getString("qualification")
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var probe: SelfUsageProbe? = null
        var reels: ReelsStorageProbe? = null
        var ui: ReelsUiProbe? = null
        try {
            when (qualification) {
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
