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
    private var breakTimeLive = false
    private var breakAbsenceLive = false
    private var scheduleLive = false
    private var scheduleSelective = false
    private var autoScrollSafety = false
    private var playbackProbe: String? = null
    private var disableBackup = false
    private var autoScrollLive = false
    private var autoScrollApp = "facebook"
    private var hiddenPlaybackProbe: String? = null
    private var autoScrollUseCurrent = false
    private var autoScrollControls = false
    private var pendingRestore = false
    private var reminderDismissal = false
    private var keyLossRecovery = false
    private var hudBehavior = false
    private var scheduleUi = false
    private var backupAdmission = false
    private var surfaceLifecycle = false
    private var surfaceSampling = false
    private var scheduleDraft = false
    private var autoScrollInterruption = false
    private var serviceRevocation = false
    private var sourceRevocation = false
    private var autoScrollRestart: String? = null
    private var autoScrollLock = false
    private var breakTiming = false
    private var historyPage = false
    private var numericAudit: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        historyPage = arguments?.getString("historyPage") == "true"
        autoScrollLock = arguments?.getString("autoScrollLock") == "true"
        breakTiming = arguments?.getString("breakTiming") == "true"
        numericAudit = arguments?.getString("numericAudit")?.takeIf { org.attentionstats.reels.ReelApp.byKey(it) != null }
        autoScrollInterruption = arguments?.getString("autoScrollInterruption") == "true"
        serviceRevocation = arguments?.getString("serviceRevocation") == "true"
        sourceRevocation = arguments?.getString("sourceRevocation") == "true"
        autoScrollRestart = arguments?.getString("autoScrollRestart")?.takeIf { it in setOf("prepare", "verify") }
        surfaceLifecycle = arguments?.getString("surfaceLifecycle") == "true"
        surfaceSampling = arguments?.getString("surfaceSampling") == "true"
        beta = arguments?.getString("beta") == "true"
        hudLive = arguments?.getString("hudLive") == "true"
        protection = arguments?.getString("protection") == "true"
        breakLive = arguments?.getString("breakLive") == "true"
        breakTimeLive = arguments?.getString("breakTimeLive") == "true"
        breakAbsenceLive = arguments?.getString("breakAbsenceLive") == "true"
        scheduleLive = arguments?.getString("scheduleLive") == "true"
        scheduleSelective = arguments?.getString("scheduleSelective") == "true"
        autoScrollSafety = arguments?.getString("autoScrollSafety") == "true"
        playbackProbe = arguments?.getString("playbackProbe")?.takeIf { org.attentionstats.reels.ReelApp.byKey(it) != null }
        disableBackup = arguments?.getString("disableBackup") == "true"
        autoScrollLive = arguments?.getString("autoScrollLive") == "true"
        autoScrollApp = arguments?.getString("autoScrollApp")?.takeIf { org.attentionstats.reels.ReelApp.byKey(it) != null } ?: "facebook"
        hiddenPlaybackProbe = arguments?.getString("hiddenPlaybackProbe")?.takeIf { org.attentionstats.reels.ReelApp.byKey(it) != null }
        autoScrollUseCurrent = arguments?.getString("autoScrollUseCurrent") == "true"
        autoScrollControls = arguments?.getString("autoScrollControls") == "true"
        pendingRestore = arguments?.getString("pendingRestore") == "true"
        reminderDismissal = arguments?.getString("reminderDismissal") == "true"
        keyLossRecovery = arguments?.getString("keyLossRecovery") == "true"
        hudBehavior = arguments?.getString("hudBehavior") == "true"
        scheduleUi = arguments?.getString("scheduleUi") == "true"
        backupAdmission = arguments?.getString("backupAdmission") == "true"
        scheduleDraft = arguments?.getString("scheduleDraft") == "true"
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
            if (historyPage) {
                result.putString("stream", "PASS: ${HistoryPageVerification.run(this)} isolated retained-history aggregate/page checks; synthetic facts, user history unchanged")
                finish(Activity.RESULT_OK, result); return
            }
            if (breakTiming) {
                result.putString("stream", "PASS: ${BreakTimingVerification.run(this)} isolated diary break timing/reset checks; synthetic clocks, no real history/settings changes")
                finish(Activity.RESULT_OK, result); return
            }
            if (autoScrollLock) {
                result.putString("stream", "PASS: ${LiveAutoScrollLockVerification.run(this)} actual screen-off/wake safety checks; run stopped before wake, no credentials entered")
                finish(Activity.RESULT_OK, result); return
            }
            val restart = autoScrollRestart
            if (restart != null) {
                result.putString("stream", "PASS: ${LiveAutoScrollRestartVerification.run(this, restart)} process restart $restart checks; operator must pair prepare with app-only force-stop")
                finish(Activity.RESULT_OK, result); return
            }
            if (sourceRevocation) {
                result.putString("stream", "PASS: ${LiveSourceRevocationVerification.run(this)} actual source withdrawal/restoration checks; no source gestures")
                finish(Activity.RESULT_OK, result); return
            }
            if (serviceRevocation) {
                result.putString("stream", "PASS: ${LiveServiceRevocationVerification.run(this)} actual service revocation/reconnection checks; no source gestures")
                finish(Activity.RESULT_OK, result); return
            }
            val audit = numericAudit
            if (audit != null) {
                result.putString("stream", NumericPlaybackAudit.run(this, audit))
                finish(Activity.RESULT_OK, result); return
            }
            if (autoScrollInterruption) {
                result.putString("stream", "PASS: ${LiveAutoScrollInterruptionVerification.run(this)} actual active-run shade interruption checks; no source gestures")
                finish(Activity.RESULT_OK, result); return
            }
            if (surfaceLifecycle) {
                result.putString("stream", "PASS: ${LiveSurfaceLifecycleVerification.run(this)} actual source/HUD shade-return checks; no source gestures")
                finish(Activity.RESULT_OK, result); return
            }
            if (surfaceSampling) {
                result.putString("stream", "PASS: ${SurfaceSamplingVerification.run(this)} isolated surface scheduling checks; no source or history changes")
                finish(Activity.RESULT_OK, result); return
            }
            if (scheduleDraft) {
                result.putString("stream", ScheduleDraftVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (backupAdmission) {
                result.putString("stream", "PASS: ${BackupAdmissionVerification.run(this)} isolated backup admission/delivery checks; no transport or real history changes")
                finish(Activity.RESULT_OK, result); return
            }
            if (disableBackup) {
                com.attentionstats.backup.SystemBackupState.configure(targetContext, false)
                check(!com.attentionstats.backup.SystemBackupState.enabled(targetContext))
                result.putString("stream", "PASS: operator-requested automatic backup off; no transfer requested")
                finish(Activity.RESULT_OK, result); return
            }
            if (autoScrollLive) {
                result.putString("stream", LiveAutoScrollVerification.run(this, autoScrollApp, autoScrollUseCurrent))
                finish(Activity.RESULT_OK, result); return
            }
            if (autoScrollControls) {
                result.putString("stream", "PASS: ${LiveAutoScrollControlsVerification.run(this)} actual HUD start/stop/waiting checks; no source gestures")
                finish(Activity.RESULT_OK, result); return
            }
            if (pendingRestore) {
                result.putString("stream", "PASS: ${PendingRestoreVerification.run(this)} isolated pending-restore AtomicFile checks; no transport")
                finish(Activity.RESULT_OK, result); return
            }
            if (reminderDismissal) {
                result.putString("stream", "PASS: ${ReminderDismissalVerification.run(this)} isolated reminder queue checks; no history or settings changes")
                finish(Activity.RESULT_OK, result); return
            }
            if (keyLossRecovery) {
                result.putString("stream", "PASS: ${KeyLossRecoveryVerification.run(this)} isolated key-loss recovery checks; no transport or real history changes")
                finish(Activity.RESULT_OK, result); return
            }
            if (hudBehavior) {
                result.putString("stream", "PASS: ${HudBehaviorVerification.run(this)} synthetic HUD behavior/accessibility checks")
                finish(Activity.RESULT_OK, result); return
            }
            val hiddenProbe = hiddenPlaybackProbe
            if (hiddenProbe != null) {
                result.putString("stream", HiddenPlaybackProbe.run(this, hiddenProbe))
                finish(Activity.RESULT_OK, result); return
            }
            val probe = playbackProbe
            if (probe != null) {
                result.putString("stream", PlaybackProbeVerification.run(this, probe))
                finish(Activity.RESULT_OK, result); return
            }
            if (autoScrollSafety) {
                result.putString("stream", "PASS: ${AutoScrollSafetyVerification.run(this)} synthetic auto-scroll safety checks; no real gestures")
                finish(Activity.RESULT_OK, result); return
            }
            if (scheduleSelective) {
                result.putString("stream", SelectiveScheduleVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (scheduleUi) {
                result.putString("stream", ScheduleUiVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (scheduleLive) {
                result.putString("stream", LiveScheduleVerification.run(this))
                finish(Activity.RESULT_OK, result); return
            }
            if (breakLive || breakTimeLive || breakAbsenceLive) {
                result.putString("stream", LiveBreakVerification.run(this, timeOnly = breakTimeLive, absenceOnly = breakAbsenceLive))
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
            val location = failure.stackTrace.firstOrNull { it.className.startsWith("com.attentionstats.") }
                ?.let { " (${it.fileName}:${it.lineNumber})" }.orEmpty()
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}: ${failure.message?.take(160)}$location")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
