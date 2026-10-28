package com.attentionstats.bridge

import android.content.Intent
import android.provider.Settings
import com.attentionstats.reels.ReelsCoordinator
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import org.attentionstats.reels.ReelApp

@ReactModule(name = ReelsModule.NAME)
class ReelsModule(private val context: ReactApplicationContext) : NativeReelsSpec(context) {
    private val coordinator get() = ReelsCoordinator.get(context)
    override fun getName() = NAME
    private fun fail(promise: Promise, code: String) { promise.reject(code, "Counter operation unavailable; saved data preserved") }
    override fun getSnapshot(promise: Promise) = coordinator.snapshot({ promise.resolve(it) }, { fail(promise, it) })
    override fun configure(app: String, enabled: Boolean, noticeRevision: Double, expectedRevision: String, promise: Promise) {
        val selected = ReelApp.byKey(app)
        if (selected == null || noticeRevision != ReelsCoordinator.requiredNotice(selected).toDouble() || expectedRevision.length !in 38..60) { fail(promise, "INVALID_ARGUMENT"); return }
        coordinator.configure(app, enabled, expectedRevision, { promise.resolve(it) }, { fail(promise, it) })
    }
    override fun setPaused(paused: Boolean, expectedRevision: String, promise: Promise) =
        coordinator.setPaused(paused, expectedRevision, { promise.resolve(it) }, { fail(promise, it) })
    override fun setOverlay(enabled: Boolean, expectedRevision: String, promise: Promise) =
        coordinator.setOverlay(enabled, expectedRevision, { promise.resolve(it) }, { fail(promise, it) })
    override fun setCounterAppearance(showIcons: Boolean, allAppsToday: Boolean, expectedRevision: String, promise: Promise) =
        coordinator.setCounterAppearance(showIcons, allAppsToday, expectedRevision, { promise.resolve(it) }, { fail(promise, it) })
    override fun deleteHistory(expectedRevision: String, promise: Promise) =
        coordinator.deleteHistory(expectedRevision, { promise.resolve(it) }, { fail(promise, it) })
    override fun openAccessibilitySettings(promise: Promise) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            promise.resolve(true) // Launched is not granted; getSnapshot rechecks the actual service.
        } catch (_: RuntimeException) { promise.resolve(false) }
    }
    override fun openTarget(app: String, promise: Promise) {
        val target = ReelApp.byKey(app) ?: run { fail(promise, "INVALID_ARGUMENT"); return }
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(target.packageName)
            if (intent == null) promise.resolve(false) else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); promise.resolve(true)
            }
        } catch (_: RuntimeException) { promise.resolve(false) }
    }
    override fun setCalibration(enabled: Boolean, promise: Promise) {
        try { coordinator.setCalibration(enabled); promise.resolve(true) }
        catch (_: RuntimeException) { fail(promise, "CONSENT_REQUIRED") }
    }
    companion object { const val NAME = "NativeReels" }
}
