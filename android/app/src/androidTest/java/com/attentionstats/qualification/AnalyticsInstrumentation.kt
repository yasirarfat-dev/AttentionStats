package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.attentionstats.analytics.main as verifyAnalytics
import org.attentionstats.reels.main as verifyReels

/**
 * On-device run of the pure analytics and encounter checks (screen-layout fixtures run on
 * the JVM with `:reels-core:verifyReels`). `adb shell am instrument -w <test package>/<this>`.
 */
class AnalyticsInstrumentation : Instrumentation() {
    private var beta = false
    private var source: String? = null
    private var inspect: String? = null
    private var feedTrial: String? = null
    private var feedReverse = false
    private var feedSamples = 90
    private var hudLive = false
    private var protection = false
    private var breakLive = false
    private var scheduleLive = false
    private var scheduleUi = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        beta = arguments?.getString("beta") == "true"
        hudLive = arguments?.getString("hudLive") == "true"
        protection = arguments?.getString("protection") == "true"
        breakLive = arguments?.getString("breakLive") == "true"
        scheduleLive = arguments?.getString("scheduleLive") == "true"
        scheduleUi = arguments?.getString("scheduleUi") == "true"
        feedTrial = arguments?.getString("feedTrial")?.takeIf { it in setOf("instagram", "facebook") }
        feedReverse = arguments?.getString("feedReverse") == "true"
        feedSamples = arguments?.getString("feedSamples")?.toIntOrNull()?.takeIf { it in 10..90 } ?: 90
        source = arguments?.getString("source")?.takeIf { org.attentionstats.reels.ReelApp.byKey(it) != null }
        inspect = arguments?.getString("inspect")?.takeIf { it in setOf("own", "settings") || org.attentionstats.reels.ReelApp.byKey(it) != null }
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            if (scheduleUi) {
                result.putString("stream", ScheduleUiVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (scheduleLive) {
                result.putString("stream", LiveScheduleVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (breakLive) {
                result.putString("stream", LiveBreakVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (protection) {
                result.putString("stream", "PASS: ${ProtectionVerification.run(this)} synthetic protection codec/attached-view checks")
                finish(Activity.RESULT_OK, result); return
            }
            if (hudLive) {
                val automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                automation.serviceInfo = automation.serviceInfo.apply {
                    flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
                android.os.SystemClock.sleep(4_000)
                result.putString("stream", "PASS: ${LiveHudVerification.run(this, automation)} actual source-HUD interaction checks")
                finish(Activity.RESULT_OK, result); return
            }
            val feed = feedTrial
            if (feed != null) {
                result.putString("stream", FeedTrialVerification.run(this, feed, feedReverse, feedSamples))
                finish(Activity.RESULT_OK, result); return
            }
            val inspection = inspect
            if (inspection != null) {
                result.putString("stream", OperatorInspection.run(this, inspection))
                finish(Activity.RESULT_OK, result); return
            }
            val target = source
            if (target != null) {
                result.putString("stream", LiveSourceVerification.run(this, target))
                finish(Activity.RESULT_OK, result)
                return
            }
            verifyAnalytics(emptyArray())
            verifyReels()
            val count = if (beta) BetaVerification.run(this) else 0
            val hudChecks = if (beta) HudBehaviorVerification.run(this) else 0
            val backupUi = if (beta) BackupUiVerification.run(this) else 0
            result.putString("stream", "PASS: analytics and encounter checks; $count isolated beta archive checks; $hudChecks HUD choice/view-state checks; $backupUi attached backup UI checks")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}: ${failure.message?.take(160)}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
