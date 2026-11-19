package com.attentionstats.reels

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.attentionstats.MainActivity
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.detection.SnapshotReader
import org.attentionstats.detection.AdapterFrame
import org.attentionstats.detection.AdapterRegistry
import org.attentionstats.detection.LayoutBounds
import org.attentionstats.reels.ItemFrame
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.ReelRoute
import org.attentionstats.reels.Surface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Reads the foreground window of a selected short-video app, hands a value-only snapshot to
 * that app's adapter, and forwards the result to the coordinator. Renders the live counter,
 * mindful breaks and blocks. Never reads other apps, never stores screen text.
 */
class ReelsAccessibilityService : AccessibilityService() {
    internal data class PlaybackAcquisitionDiagnostic(val app: ReelApp, val frame: AdapterFrame,
        val window: Int, val epoch: Long, val atMs: Long, val complete: Boolean)
    internal companion object {
        /** Explicit operator debug test only. No nodes/labels, bridge or persistent subscription. */
        @Volatile var onPlaybackAcquisitionDiagnostic:
            ((ReelsAccessibilityService, PlaybackAcquisitionDiagnostic) -> Unit)? = null
    }
    private val main = Handler(Looper.getMainLooper())
    private val coordinator by lazy { ReelsCoordinator.get(this) }
    private val listener: () -> Unit = { main.post {
        updateEventTargets()
        sampling.configurationChanged(coordinator.epoch(), coordinator.acquisitionSettingsRevision())
        renderOverlays()
    } }
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var destroyed = false
    private var lastScan = 0L

    private val scrollSequence = ConcurrentHashMap<ReelApp, AtomicLong>()
    @Volatile private var currentApp: ReelApp? = null
    /** Source app in front while counting is paused: show the resume pill, count nothing. */
    @Volatile private var pausedApp: ReelApp? = null
    @Volatile private var displayEligible = false
    @Volatile private var transientAcquisition = false
    @Volatile private var lastFrame: AdapterFrame? = null
    /** One completed acquisition publication: never combine a prior frame with a new gap flag. */
    private data class HudAcquisition(val frame: AdapterFrame? = null, val eligible: Boolean = false, val transient: Boolean = false)
    @Volatile private var hudAcquisition = HudAcquisition()
    private data class Seen(val app: ReelApp, val window: Int, val atMs: Long, val frame: AdapterFrame, val scroll: Long)
    @Volatile private var lastReels: Seen? = null

    private var hud: CounterHudView? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var hudOffsetX = 0
    private var hudOffsetY: Int? = null
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f
    private val visitHudVisibility = VisitHudVisibility()

    private var intervention: InterventionOverlay? = null
    @Volatile private var interventionKind: String? = null
    @Volatile private var breakCoversPlayer = false
    @Volatile private var foregroundPackage: String? = null
    private var interventionApp: ReelApp? = null
    private var interventionReceipt: org.attentionstats.protection.InterventionOutcome? = null
    @Volatile private var block: ReelsCoordinator.BlockDecision? = null
    private var blockActedAt = 0L
    private var blockRetried = false
    private var focusRequest: AudioFocusRequest? = null
    /** Suppress stale published reminders until the serialized dismissal acknowledges completion. */
    private var breakDismissPending = false
    @Volatile private var breakPreparationToken = 0L
    private var preparingBreak = false
    private var breakPauseConfirmed: Boolean? = null
    @Volatile private var scheduledPackage: String? = null
    private var scheduleOverlay: InterventionOverlay? = null
    private var scheduleOverlayPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { scheduledPackage = null; coordinator.resetUnlockSession(); outside(true) }
                Intent.ACTION_USER_PRESENT -> schedule(0)
            }
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (destroyed) return
            worker.execute {
                if (!destroyed) {
                    coordinator.schedules.refreshLaunchable()
                    main.post { if (!destroyed) { updateEventTargets(); schedule(0) } }
                }
            }
        }
    }
    private var packageReceiverRegistered = false
    private var receiverRegistered = false

    private val sampling = SurfaceSamplingLoop(worker, ::sample) {
        coordinator.acquisitionStatus = "read_failed"
        foregroundPackage = null
        scheduledPackage = null
        outside(false, transient = true)
        1_000L
    }

    override fun onServiceConnected() {
        AutoScrollSession.onChanged = { main.post { renderOverlays() } }
        // A parent approval opened from a block screen must not stay hidden under it; the service gets
        // no events from its own app, so it re-checks the screen itself once approval ends.
        com.attentionstats.protection.ProtectionLock.approvalListener = { approving ->
            if (!destroyed) { renderOverlays(); if (!approving) schedule(0) }
        }
        coordinator.serviceConnected = true
        coordinator.addListener(listener)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
        receiverRegistered = true
        val packages = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, packages, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(packageReceiver, packages)
        packageReceiverRegistered = true
        // Events arrive only from the supported short-video apps; departure is noticed by polling
        // the foreground window's package while one of them is in front.
        updateEventTargets()
        coordinator.snapshot({ main.post { schedule(0) } }, { })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || destroyed) return
        val foreground = event.packageName?.toString() ?: return
        if (foreground in coordinator.schedules.targets()) schedule(60)
        val app = ReelApp.byPackage(foreground) ?: return
        if (!coordinator.allowed(app)) { if (coordinator.pausedSource(app)) schedule(300); return }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                scrollSequence.getOrPut(app) { AtomicLong() }.incrementAndGet()
                AutoScrollSession.manualInteraction("manual_scroll")
                schedule(120)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> { AutoScrollSession.manualInteraction("manual_click"); schedule(150) }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> schedule(60)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> schedule(400)
            else -> schedule(200)
        }
    }

    private fun updateEventTargets() {
        val current = serviceInfo ?: return
        val wanted = (ReelApp.allPackages + coordinator.schedules.targets()).toSet()
        val needed = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        if (current.packageNames?.toSet() == wanted && current.flags and needed == needed) return
        current.packageNames = wanted.toTypedArray()
        current.flags = current.flags or needed
        serviceInfo = current
    }

    private fun schedule(delay: Long) {
        if (!destroyed) sampling.request(delay)
    }

    private fun interactive(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** A touched nonfocusable overlay can be "active" while the actual app still owns input focus. */
    private fun providerRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        val active = rootInActiveWindow ?: return null
        if (active.packageName?.toString() != packageName) return active
        val focused = windows.firstOrNull { it.isFocused && it.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }?.root
        if (focused == null) return active
        SnapshotReader.recycle(active)
        return focused
    }

    /** Worker thread. */
    private fun sample(): Long? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScan < 180) return 180 - (now - lastScan)
        lastScan = now
        if (!interactive()) { coordinator.acquisitionStatus = "not_interactive"; foregroundPackage = null; scheduledPackage = null; outside(true); return null }
        val root = providerRoot() ?: run { coordinator.acquisitionStatus = "no_active_root"; foregroundPackage = null; scheduledPackage = null; outside(false, transient = true); return 1_000L }
        try {
            val foreground = root.packageName?.toString()
            foregroundPackage = foreground
            coordinator.acquisitionStatus = "root_obtained"
            // Whole-app schedules read package ownership only. No labels/nodes of arbitrary apps.
            val targeted = foreground != null && foreground in coordinator.schedules.targets()
            scheduledPackage = foreground?.takeIf { coordinator.schedules.decision(it, now).isNotEmpty() }
            if (scheduledPackage != null) {
                outside(true)
                main.post { AutoScrollSession.suspendActions("app_schedule"); renderOverlays() }
                return 500L
            }
            visitHudVisibility.foreground(foreground)
            val app = ReelApp.byPackage(foreground ?: "")
            if (app != null && coordinator.pausedSource(app)) {
                if (currentApp != app) outside(true)
                currentApp = app; pausedApp = app; displayEligible = false
                hudAcquisition = HudAcquisition()
                main.post { renderOverlays() }
                return 1_500L
            }
            pausedApp = null
            if (app == null || !coordinator.allowed(app)) {
                // System UI (notification shade, volume) is not a departure.
                outside(foreground != "com.android.systemui")
                return if (targeted || foreground == "com.android.systemui") 1_000L else null
            }
            if (currentApp != null && currentApp != app) outside(true)
            if (currentApp != app) coordinator.pauseDiarySurface()
            currentApp = app
            val capturedEpoch = coordinator.epoch()
            val snapshot = SnapshotReader.read(root, app.packages)
            var frame = AdapterRegistry.of(app).read(snapshot)
            transientAcquisition = !snapshot.typing && (frame.surface == Surface.OUTSIDE || frame.surface == Surface.UNKNOWN)
            coordinator.acquisitionStatus = "${app.key}:${frame.surface}:${snapshot.nodes.size}:complete=${snapshot.complete}"
            if (!coordinator.countPhotos && frame.mediaKind == "photo") frame = frame.copy(surface = Surface.EXCLUDED)
            val previous = lastReels
            // A pager mid-swipe briefly shows two half pages; keep the viewer alive through it.
            if (frame.surface == Surface.OUTSIDE && previous != null && previous.app == app &&
                previous.window == snapshot.windowId && previous.frame.route == ReelRoute.VIEWER && now - previous.atMs < 1_500)
                frame = AdapterFrame(Surface.REELS, ReelRoute.VIEWER, method = previous.frame.method)
            val previousPage = previous?.frame?.page
            val currentPage = frame.page
            val sameSlot = frame.route == ReelRoute.HOME && frame.slotKey != null && previous != null &&
                previous.scroll == (scrollSequence[app]?.get() ?: 0L) &&
                previous.app == app && previous.frame.slotKey == frame.slotKey && now - previous.atMs < 900 &&
                previousPage != null && currentPage != null &&
                kotlin.math.abs(previousPage.top - currentPage.top) < snapshot.screen.height / 2
            if (frame.surface == Surface.REELS) lastReels = Seen(app, snapshot.windowId, now, frame, scrollSequence[app]?.get() ?: 0L)
            lastFrame = frame

            val decision = coordinator.blockDecision(app, frame, foreground.orEmpty())
            block = decision
            val counted = if (decision != null || breakCoversPlayer) frame.copy(surface = Surface.EXCLUDED) else frame
            val item = ItemFrame(now, counted.surface, counted.route, counted.identity, counted.keys, counted.category,
                scrollSequence.getOrPut(app) { AtomicLong() }.get(), sameSlot, counted.slotKey)
            displayEligible = decision == null && (frame.surface == Surface.REELS ||
                (frame.route == ReelRoute.HOME && frame.method.endsWith(".feed")))
            hudAcquisition = HudAcquisition(frame, displayEligible, transientAcquisition)
            // observe publishes asynchronous snapshots; publish this frame's eligibility first.
            coordinator.observe(app, counted, item, snapshot.windowId, capturedEpoch)
            // Time slots retain their cover and immediate allowance inside the chosen viewer.
            // Navigating Back here races the renderer and removes the schedule explanation.
            if (decision != null && decision.kind != "schedule") main.post { enforceBlock(decision) }
            feedAutoScroll(app, frame, snapshot.windowId, capturedEpoch, now)
            main.post { renderOverlays() }
            if (com.attentionstats.BuildConfig.DEBUG) onPlaybackAcquisitionDiagnostic?.invoke(this,
                PlaybackAcquisitionDiagnostic(app, frame, snapshot.windowId, capturedEpoch, now,
                    snapshot.complete && !snapshot.typing))
            return when {
                frame.surface == Surface.REELS -> 400L
                displayEligible || decision != null -> 700L
                else -> 1_500L // Cheap ownership check until departure.
            }
        } finally { SnapshotReader.recycle(root) }
    }

    private fun outside(departed: Boolean, transient: Boolean = false) {
        transientAcquisition = transient
        val app = currentApp
        displayEligible = false
        block = null
        lastFrame = null
        hudAcquisition = HudAcquisition(transient = transient)
        if (departed) { lastReels = null; currentApp = null; pausedApp = null; coordinator.pauseDiarySurface() }
        app?.let {
            coordinator.observe(it, AdapterFrame.OUTSIDE, ItemFrame(SystemClock.elapsedRealtime(), Surface.OUTSIDE,
                ReelRoute.VIEWER, null, emptySet(), org.attentionstats.reels.PageCategory.REEL, 0), 0, coordinator.epoch())
        }
        main.post {
            if (transient) acquisitionGap("outside_viewer")
            else AutoScrollSession.unavailable("outside_viewer")
            renderOverlays()
        }
    }

    /** Only missing acquisition inside this source's already dispatched transition may wait. */
    private fun acquisitionGap(reason: String) {
        val app = currentApp
        if (app != null && coordinator.allowed(app) && coordinator.grantEnabled() &&
            coordinator.overlayEnabled && !visitHudVisibility.hiddenFor(app) && interactive())
            AutoScrollSession.transientUnavailable(reason, app, coordinator.epoch())
        else AutoScrollSession.unavailable(reason)
    }

    // ---- Overlays --------------------------------------------------------------------------

    private fun renderOverlays() {
        if (destroyed) return
        if (com.attentionstats.protection.ProtectionLock.approving) { removeSchedule(); removeIntervention(); removeHud(); return }
        renderSchedule()
        // Watchdog: while any cover is up, keep re-checking the screen even if no app sends events,
        // so a cover that is no longer wanted can never linger until the screen is locked.
        if (scheduleOverlay != null) { removeIntervention(); removeHud(); schedule(1_500); return }
        renderIntervention()
        renderHud()
        if (intervention != null) schedule(1_500)
    }

    private fun renderSchedule() {
        val target = scheduledPackage?.takeIf { interactive() && coordinator.schedules.decision(it, SystemClock.elapsedRealtime()).isNotEmpty() }
        if (target == scheduleOverlayPackage && scheduleOverlay != null) return
        removeSchedule()
        if (target == null) return
        val view = InterventionOverlay(this, InterventionOverlay.Mode.BLOCK, "A little time away",
            "This app is covered by your focus session or a blocking period. End focus or change schedules in Attention Stats, or allow a short visit.",
            0, "Open Attention Stats", "Allow more time",
            { scheduledPackage = null; removeSchedule(); open("attentionstats://protect") },
            {},
            stats = coordinator.liveOverlay?.let { InterventionOverlay.Stats(it.visit, it.today, 0) },
            onChoice = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onAllow = { minutes -> coordinator.allowScheduled(target, minutes) { scheduledPackage = null; removeSchedule(); schedule(0) } })
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        fitBetweenBars(params)
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            scheduleOverlay = view; scheduleOverlayPackage = target
            pausePlayback(true)
        } catch (_: RuntimeException) { view.finish(); scheduleOverlay = null; scheduleOverlayPackage = null }
    }
    private fun removeSchedule() {
        val view = scheduleOverlay ?: return
        view.finish()
        try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        scheduleOverlay = null; scheduleOverlayPackage = null
        pausePlayback(false)
    }

    private fun renderIntervention() {
        val app = currentApp
        val decision = block
        val reminder = coordinator.reminder
        val wanted: String? = when {
            app == null || !app.owns(foregroundPackage) || !interactive() -> null
            decision != null && decision.app == app -> decision.kind
            interventionKind != null && interventionKind !in setOf("break", "schedule") && interventionApp == app -> interventionKind // Stay until chosen.
            !breakDismissPending && reminder != null && reminder.app == app && displayEligible -> "break"
            !breakDismissPending && reminder != null && lastFrame == null && interventionKind == "break" && interventionApp == app -> "break"
            else -> null
        }
        if (wanted == null) { removeIntervention(); return }
        if (intervention != null && interventionKind == wanted && interventionApp == app) return
        if (wanted == "break" && breakPauseConfirmed == null) {
            if (!preparingBreak) prepareBreak(checkNotNull(app))
            return
        }
        val compactBreak = wanted == "break" && breakPauseConfirmed != true
        removeIntervention()
        val current = checkNotNull(app)
        AutoScrollSession.suspendActions("intervention")
        val session = coordinator.liveOverlay?.visit ?: reminder?.count?.toLong() ?: 0
        val minutes = ((reminder?.durationMs ?: 0) / 60_000).toInt()
        val view = when (wanted) {
            "break" -> InterventionOverlay(this, InterventionOverlay.Mode.BREAK, "Time for something real?",
                buildString {
                    append("$session short videos")
                    if (minutes > 0) append(" · $minutes min")
                    append(" since you unlocked.")
                    if (compactBreak) append("\nPause the video when you're ready for a quiet moment.")
                }, coordinator.breakSeconds, "I'm done for now", "Keep watching",
                { dismissBreak("left"); performGlobalAction(GLOBAL_ACTION_HOME) },
                { dismissBreak("continued") }, compact = compactBreak,
                stats = InterventionOverlay.Stats(session, coordinator.liveOverlay?.today ?: 0, minutes))
            else -> InterventionOverlay(this, InterventionOverlay.Mode.BLOCK,
                if (wanted == "limit") "Daily limit reached" else if (wanted == "schedule") "Short videos are on a pause" else "${current.observationLabel} are off",
                if (wanted == "schedule") "A time slot you chose is active. Everything else in ${current.displayName} still works."
                else if (wanted == "limit") "You chose ${decision?.limit} short videos a day on ${current.displayName}.\n" +
                    "Everything else in the app still works." else
                    "You chose to turn them off.\nEverything else in ${current.displayName} still works.",
                coordinator.breakSeconds, "Okay", "Allow more time",
                {
                    decide("left"); blockActedAt = 0; removeIntervention()
                    if (wanted == "schedule") performGlobalAction(GLOBAL_ACTION_BACK)
                },
                {},
                stats = InterventionOverlay.Stats(coordinator.liveOverlay?.visit ?: 0, coordinator.liveOverlay?.today ?: 0, 0),
                onChoice = { decide("left"); blockActedAt = 0; removeIntervention(); performGlobalAction(GLOBAL_ACTION_HOME) },
                onAllow = { minutes -> coordinator.allowFor(current, minutes) { decide("allowed"); removeIntervention(); schedule(0) } })
        }
        // Not focusable: system Back/Home/Recents always reach Android, so the screen never traps anyone.
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        // Stop above the navigation bar so its buttons/gesture area stay visible and usable.
        val navigation = navigationBarHeight()
        if (compactBreak) {
            params.height = ((if (Build.VERSION.SDK_INT >= 30) getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
                else resources.displayMetrics.heightPixels) - navigation).let { (it * 0.8f).toInt() }
            params.gravity = Gravity.CENTER
            params.y = -navigation / 2
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else fitBetweenBars(params)
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            intervention = view; interventionKind = wanted; interventionApp = current
            // Both full and centered reminders obscure the player. Exclude covered
            // samples even when audio/playback pause could not be confirmed.
            breakCoversPlayer = wanted == "break"
            val receipt = org.attentionstats.protection.InterventionOutcome { id, choice -> coordinator.decideIntervention(id, choice) }
            interventionReceipt = receipt
            view.alpha = 0f; view.animate().alpha(1f).setDuration(220).start()
            if (wanted != "break") pausePlayback(true)
            coordinator.recordIntervention(current, wanted) { id ->
                receipt.recorded(id)
            }
        } catch (_: RuntimeException) { view.finish(); removeIntervention() }
    }

    /** Pause before covering the player. Unconfirmed playback gets a nonmodal reminder instead. */
    private fun prepareBreak(app: ReelApp) {
        preparingBreak = true
        val token = ++breakPreparationToken
        val epoch = coordinator.epoch()
        val reminder = coordinator.reminder
        AutoScrollSession.suspendActions("prepare_break")
        worker.execute {
            var confirmed = false
            var valid = false
            try {
                val root = providerRoot()
                if (root != null) try {
                    val window = root.windowId
                    fun current(): Boolean {
                        if (destroyed || token != breakPreparationToken || coordinator.epoch() != epoch ||
                            coordinator.reminder != reminder || currentApp != app || !coordinator.allowed(app) || !interactive()) return false
                        val active = providerRoot() ?: return false
                        return try { active.windowId == window && app.owns(active.packageName?.toString()) }
                            finally { SnapshotReader.recycle(active) }
                    }
                    valid = current()
                    if (valid) confirmed = com.attentionstats.protection.PlaybackPauseDispatcher.prepare(app, root, ::current)
                    if (valid && !confirmed) confirmed = com.attentionstats.protection.PlaybackPauseDispatcher.prepareByTap(app, ::current,
                        { breakSample(app, window) }, ::tapOnce)
                    valid = current()
                } finally { SnapshotReader.recycle(root) }
            } catch (_: Exception) { valid = false }
            main.post {
                if (destroyed || token != breakPreparationToken) return@post
                preparingBreak = false
                if (valid && currentApp == app && coordinator.reminder == reminder) {
                    breakPauseConfirmed = confirmed
                    renderOverlays()
                } else schedule(0)
            }
        }
    }

    /**
     * Full covers sit exactly between the status and navigation bars. Android keeps accessibility
     * overlays below the status bar, so a top-anchored window sized "screen minus navigation bar"
     * slid its bottom behind the navigation bar; anchor to the bottom above the bar instead.
     */
    private fun fitBetweenBars(params: WindowManager.LayoutParams) {
        val navigation = navigationBarHeight()
        val screen = if (Build.VERSION.SDK_INT >= 30) getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
            else resources.displayMetrics.heightPixels + navigation
        params.gravity = Gravity.BOTTOM
        params.y = navigation
        params.height = screen - navigation - statusBarHeight()
    }

    @android.annotation.SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun statusBarHeight(): Int {
        val fromInsets = if (Build.VERSION.SDK_INT >= 30) getSystemService(WindowManager::class.java).currentWindowMetrics
            .windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top else 0
        if (fromInsets > 0) return fromInsets
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** Service contexts often report empty window insets, so fall back to the system dimension. */
    private fun navigationBarHeight(): Int {
        val fromInsets = if (Build.VERSION.SDK_INT >= 30) getSystemService(WindowManager::class.java).currentWindowMetrics
            .windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom else 0
        if (fromInsets > 0) return fromInsets
        @android.annotation.SuppressLint("InternalInsetResource", "DiscouragedApi")
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun decide(choice: String) {
        interventionReceipt?.choose(choice)
    }

    private fun dismissBreak(choice: String) {
        if (breakDismissPending || destroyed) return
        breakDismissPending = true
        decide(choice)
        removeIntervention()
        coordinator.dismissReminder {
            if (destroyed) return@dismissReminder
            breakDismissPending = false
            renderOverlays()
            schedule(0)
        }
    }

    /**
     * Leave the short-video player with Back: once immediately, and once more if the player is
     * still showing 2.5 s later (some apps animate or swallow the first Back). Never presses Home,
     * so people stay in the app; the pause screen keeps the player unwatchable meanwhile.
     */
    private fun enforceBlock(decision: ReelsCoordinator.BlockDecision) {
        if (decision.app != currentApp) return
        val now = SystemClock.elapsedRealtime()
        val sinceFirst = now - blockActedAt
        when {
            blockActedAt == 0L || sinceFirst > 10_000 -> { blockActedAt = now; blockRetried = false; performGlobalAction(GLOBAL_ACTION_BACK) }
            !blockRetried && sinceFirst > 2_500 -> { blockRetried = true; performGlobalAction(GLOBAL_ACTION_BACK) }
        }
    }

    private fun removeIntervention() {
        breakCoversPlayer = false
        breakPreparationToken++
        preparingBreak = false
        breakPauseConfirmed = null
        intervention?.let { view ->
            view.finish()
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        if (intervention != null) pausePlayback(false)
        intervention = null; interventionKind = null; interventionApp = null; interventionReceipt = null
    }

    /** Holding transient audio focus makes video players pause while a break or block is shown. */
    /** Worker thread: a fresh frame of the same source window, or null. */
    private fun breakSample(app: ReelApp, window: Int): AdapterFrame? {
        val active = providerRoot() ?: return null
        return try {
            if (active.windowId != window || !app.owns(active.packageName?.toString())) return null
            val snapshot = SnapshotReader.read(active, app.packages)
            if (!snapshot.complete || snapshot.typing) return null
            AdapterRegistry.of(app).read(snapshot).takeIf { it.surface == Surface.REELS && it.route == ReelRoute.VIEWER }
        } finally { SnapshotReader.recycle(active) }
    }

    /** Worker thread: one short tap for a user-enabled break's pause; waits for the gesture result. */
    private fun tapOnce(x: Float, y: Float): Boolean {
        val done = java.util.concurrent.CountDownLatch(1)
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        main.post {
            val path = android.graphics.Path().apply { moveTo(x, y) }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)).build()
            val accepted = try {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(g: android.accessibilityservice.GestureDescription?) { completed.set(true); done.countDown() }
                    override fun onCancelled(g: android.accessibilityservice.GestureDescription?) { done.countDown() }
                }, main)
            } catch (_: RuntimeException) { false }
            if (!accepted) done.countDown()
        }
        return done.await(1_500, java.util.concurrent.TimeUnit.MILLISECONDS) && completed.get()
    }

    private fun pausePlayback(pause: Boolean) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        if (Build.VERSION.SDK_INT < 26) return
        if (pause) {
            if (focusRequest != null) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setOnAudioFocusChangeListener { }.build()
            if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = request
        } else focusRequest?.let { audio.abandonAudioFocusRequest(it); focusRequest = null }
    }

    private fun renderHud() {
        val acquisition = hudAcquisition
        val state = coordinator.liveOverlay
        val active = currentApp
        val pausedHere = pausedApp != null && pausedApp == active && coordinator.overlayEnabled && intervention == null &&
            !visitHudVisibility.hiddenFor(checkNotNull(active)) && interactive()
        if (pausedHere) {
            ensureHud(); hud?.setPausedState(true)
            hud?.setAutoScrollControl(true, AutoScrollSession.running, false)
            return
        }
        val unavailable = when {
            !acquisition.eligible -> "hud_display_ineligible"
            intervention != null -> "hud_intervention"
            !coordinator.overlayEnabled -> "hud_disabled"
            state == null -> "hud_snapshot_missing"
            active != state.app -> "hud_source_mismatch"
            visitHudVisibility.hiddenFor(state.app) -> "hud_hidden"
            state.epoch != coordinator.epoch() -> "hud_epoch_mismatch"
            !coordinator.allowed(state.app) -> "hud_consent_unavailable"
            !interactive() -> "hud_not_interactive"
            else -> null
        }
        if (unavailable != null) {
            if (AutoScrollSession.running) AutoScrollSession.onDispatchDiagnostic?.invoke(unavailable)
            if (AutoScrollSession.running && intervention == null) {
                // The asynchronous count snapshot can briefly be absent after our swipe
                // while the new source page is already valid. Preserve only the existing
                // bounded confirmation token; hidden/disabled/foreign contexts still stop.
                val validViewerAwaitingDisplay = unavailable == "hud_display_ineligible" && block == null &&
                    scheduledPackage == null && !preparingBreak && acquisition.frame?.let {
                        it.surface == Surface.REELS && it.route == ReelRoute.VIEWER
                    } == true
                AutoScrollSession.onDispatchDiagnostic?.invoke("renderer_${acquisition.frame?.surface?.name ?: "absent"}_${acquisition.frame?.route?.name ?: "absent"}_${if (acquisition.transient) "gap" else "settled"}_${if (block == null) "clear" else "blocked"}")
                if (acquisition.transient || unavailable == "hud_snapshot_missing" || validViewerAwaitingDisplay) acquisitionGap("viewer_unavailable")
                else AutoScrollSession.suspendActions("viewer_unavailable")
            }
            removeHud(); return
        }
        ensureHud()
        hud?.setPausedState(false)
        hud?.update(checkNotNull(state))
        hud?.setAutoScrollControl(true, AutoScrollSession.running, AutoScrollSession.canStart())
    }

    private fun ensureHud() {
        if (hud == null) {
            val density = resources.displayMetrics.density
            val view = CounterHudView(this, ::openDashboard, ::pauseObservation, ::hideCounterThisVisit, {
                when {
                    AutoScrollSession.running -> AutoScrollSession.stop()
                    AutoScrollSession.enabled -> AutoScrollSession.start()
                    AutoScrollSession.noticeAccepted -> { AutoScrollSession.configure(true, 2); AutoScrollSession.start() }
                    else -> openConvenience()
                }
            })
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = hudOffsetX
                y = hudOffsetY ?: (32 * density).toInt()
            }
            view.moveCounter = { dx, dy -> moveCounter(dx, dy) }
            view.resetPosition = {
                hudParams?.let { current ->
                    hudOffsetX = 0; hudOffsetY = (32 * density).toInt()
                    moveRemainderX = 0f; moveRemainderY = 0f
                    current.x = hudOffsetX; current.y = checkNotNull(hudOffsetY)
                    moveCounter(0f, 0f)
                }
            }
            view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) moveCounter(0f, 0f)
            }
            view.onResume = ::resumeObservation
            try { hud = view; hudParams = params; getSystemService(WindowManager::class.java).addView(view, params) }
            catch (_: RuntimeException) { hud = null; hudParams = null; return }
        }
    }

    private fun resumeObservation() {
        coordinator.snapshot({ value ->
            val revision = org.json.JSONObject(value).getString("revision")
            coordinator.setPaused(false, revision, { main.post { hud?.setPausedState(false); schedule(0) } }, { })
        }, { })
    }

    // ---- Auto-scroll (optional convenience) ------------------------------------------------

    private fun feedAutoScroll(app: ReelApp, frame: AdapterFrame, window: Int, epoch: Long, now: Long) {
        if (!AutoScrollSession.enabled || frame.route != ReelRoute.VIEWER || frame.surface != Surface.REELS) {
            if (AutoScrollSession.running) {
                val transient = transientAcquisition
                main.post {
                    if (transient) acquisitionGap("playback_unavailable")
                    else AutoScrollSession.suspendActions("playback_unavailable")
                }
            }
            return
        }
        val value = AutoScrollSession.Frame(app, window, frame.identity, epoch, now, frame.progress,
            coordinator.overlayEnabled && !visitHudVisibility.hiddenFor(app), frame.mediaKind, skippableAd(app, frame),
            if (frame.standaloneAd) frame.standaloneAdKeys else frame.keys)
        main.post { AutoScrollSession.frame(value) { expected, token -> advance(token, expected) } }
    }

    private fun skippableAd(app: ReelApp, frame: AdapterFrame): Boolean =
        if (app == ReelApp.FACEBOOK) frame.standaloneAd else frame.skippableAd

    private fun advance(token: Long, expected: AutoScrollSession.Frame) {
        fun reject(stage: String, reason: String = "context_changed") {
            AutoScrollSession.onDispatchDiagnostic?.invoke(stage)
            AutoScrollSession.suspendActions(reason)
        }
        if (!AutoScrollSession.current(token)) {
            AutoScrollSession.onDispatchDiagnostic?.invoke("token_expired"); return
        }
        AutoScrollSession.onDispatchDiagnostic?.invoke("validation_started")
        main.postDelayed({ AutoScrollSession.expire(token, SystemClock.elapsedRealtime()) }, 2_500)
        if (preparingBreak || intervention != null) return reject("intervention_present")
        if (hud?.isShown != true) return reject("hud_missing")
        if (!interactive()) return reject("not_interactive")
        if (expected.epoch != coordinator.epoch()) return reject("epoch_changed")
        if (!coordinator.allowed(expected.app) || !coordinator.grantEnabled()) return reject("consent_unavailable")
        if (SystemClock.elapsedRealtime() - expected.atMs !in 0..750) return reject("frame_stale")
        val root = providerRoot() ?: return reject("root_missing")
        val bounds = try {
            if (!expected.app.owns(root.packageName?.toString()) || root.windowId != expected.window)
                return reject("window_rejected")
            val snapshot = SnapshotReader.read(root, expected.app.packages)
            val current = AdapterRegistry.of(expected.app).read(snapshot)
            if (!snapshot.complete || snapshot.typing) return reject("snapshot_rejected")
            if (current.surface != Surface.REELS || current.route != ReelRoute.VIEWER) return reject("viewer_rejected")
            if (current.identity != expected.page && !AutoScrollSession.sameItem(
                    if (current.standaloneAd) current.standaloneAdKeys else current.keys, expected.itemKeys))
                return reject("identity_rejected")
            if (expected.standaloneAd && AutoScrollSession.skipStandaloneAds) {
                if (!skippableAd(expected.app, current))
                    return reject("standalone_ad_rejected", "playback_unavailable")
            } else if (expected.position == null) {
                if (expected.mediaKind != "photo" || current.mediaKind != "photo" || current.progress != null)
                    return reject("photo_rejected", "playback_unavailable")
            } else if (current.progress?.let { it.isFinite() && it in 0.0..1.0 } != true) {
                return reject("progress_rejected", "playback_unavailable")
            }
            current.page ?: return reject("page_missing")
        } finally { SnapshotReader.recycle(root) }
        AutoScrollSession.onDispatchDiagnostic?.invoke("validation_passed")
        try { com.attentionstats.autoscroll.AutoScrollGestureDispatcher.dispatch(this, bounds, token, main) }
        catch (_: RuntimeException) { AutoScrollSession.suspendActions("gesture_unavailable") }
    }

    // ---- HUD helpers -----------------------------------------------------------------------

    private fun moveCounter(dx: Float, dy: Float) {
        val view = hud ?: return
        val params = hudParams ?: return
        if (view.width <= 0 || view.height <= 0) return
        val metrics = resources.displayMetrics
        val inset = (8 * metrics.density).toInt()
        val safeInsets = Rect(0, inset, 0, inset)
        if (Build.VERSION.SDK_INT >= 30) {
            view.rootWindowInsets?.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())?.let {
                safeInsets.set(it.left, it.top, it.right, it.bottom)
            }
        }
        val safeWidth = metrics.widthPixels - safeInsets.left - safeInsets.right - 2 * inset
        val maxX = maxOf(0, (safeWidth - view.width) / 2)
        val maxY = maxOf(inset, metrics.heightPixels - safeInsets.top - safeInsets.bottom - view.height - inset)
        val preciseX = dx + moveRemainderX
        val preciseY = dy + moveRemainderY
        val requestedX = params.x + preciseX.toInt()
        val requestedY = params.y + preciseY.toInt()
        params.x = requestedX.coerceIn(-maxX, maxX)
        params.y = requestedY.coerceIn(inset, maxY)
        moveRemainderX = if (params.x == requestedX) preciseX - preciseX.toInt() else 0f
        moveRemainderY = if (params.y == requestedY) preciseY - preciseY.toInt() else 0f
        hudOffsetX = params.x; hudOffsetY = params.y
        try { getSystemService(WindowManager::class.java).updateViewLayout(view, params) }
        catch (_: RuntimeException) { removeHud() }
    }

    private fun openDashboard() { open("attentionstats://today") }
    private fun openConvenience() { AutoScrollSession.suspendActions("permission_review"); open("attentionstats://convenience") }
    private fun open(uri: String) {
        startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        removeHud()
    }

    private fun hideCounterThisVisit() {
        currentApp?.let { visitHudVisibility.hide(it) }
        AutoScrollSession.suspendActions("counter_hidden")
        removeHud()
    }

    private fun pauseObservation() {
        coordinator.snapshot({ value ->
            val revision = org.json.JSONObject(value).getString("revision")
            coordinator.setPaused(true, revision, { main.post { renderOverlays(); schedule(0) } }, { main.post { openDashboard() } })
        }, { main.post { openDashboard() } })
    }

    private fun removeHud() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { removeHud() }; return }
        hud?.let { view -> try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { } }
        hud = null
        hudParams = null
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        removeSchedule()
        destroyed = true
        removeIntervention()
        removeHud()
        coordinator.suspendObservation()
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        if (packageReceiverRegistered) { unregisterReceiver(packageReceiver); packageReceiverRegistered = false }
        AutoScrollSession.onChanged = {}
        com.attentionstats.protection.ProtectionLock.approvalListener = null
        AutoScrollSession.clear()
        sampling.close()
        main.removeCallbacksAndMessages(null)
        coordinator.serviceConnected = false
        coordinator.removeListener(listener)
        super.onDestroy()
    }
}
