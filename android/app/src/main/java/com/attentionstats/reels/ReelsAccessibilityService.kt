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
    private val main = Handler(Looper.getMainLooper())
    private val coordinator by lazy { ReelsCoordinator.get(this) }
    private val listener: () -> Unit = { main.post { renderOverlays() } }
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var destroyed = false
    private var scheduled = false
    @Volatile private var scanning = false
    private var lastScan = 0L

    private val scrollSequence = ConcurrentHashMap<ReelApp, AtomicLong>()
    @Volatile private var currentApp: ReelApp? = null
    @Volatile private var displayEligible = false
    @Volatile private var lastFrame: AdapterFrame? = null
    private data class Seen(val app: ReelApp, val window: Int, val atMs: Long, val frame: AdapterFrame)
    @Volatile private var lastReels: Seen? = null

    private var hud: CounterHudView? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var hudOffsetX = 0
    private var hudOffsetY: Int? = null
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f
    private val visitHudVisibility = VisitHudVisibility()

    private var intervention: InterventionOverlay? = null
    private var interventionKind: String? = null
    private var interventionApp: ReelApp? = null
    private var interventionId: String? = null
    @Volatile private var block: ReelsCoordinator.BlockDecision? = null
    private var blockActedAt = 0L
    private var blockRetried = false
    private var focusRequest: AudioFocusRequest? = null
    /** After "Keep watching", the dismissal is queued; do not re-show the break meanwhile. */
    private var breakQuietUntil = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { coordinator.resetUnlockSession(); outside(true) }
                Intent.ACTION_USER_PRESENT -> schedule(0)
            }
        }
    }
    private var receiverRegistered = false

    private val tick = Runnable {
        scheduled = false
        if (scanning) { schedule(200); return@Runnable }
        scanning = true
        worker.execute {
            try { if (!destroyed) sample() }
            catch (_: RuntimeException) { outside(false) }
            finally { scanning = false }
        }
    }

    override fun onServiceConnected() {
        AutoScrollSession.onChanged = { main.post { renderOverlays() } }
        coordinator.serviceConnected = true
        coordinator.addListener(listener)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
        receiverRegistered = true
        // Events arrive only from the supported short-video apps; departure is noticed by polling
        // the foreground window's package while one of them is in front.
        serviceInfo = serviceInfo?.apply { packageNames = ReelApp.allPackages.toTypedArray() }
        coordinator.snapshot({ main.post { schedule(0) } }, { })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || destroyed) return
        val app = ReelApp.byPackage(event.packageName?.toString() ?: "") ?: return
        if (!coordinator.allowed(app)) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                scrollSequence.getOrPut(app) { AtomicLong() }.incrementAndGet()
                AutoScrollSession.manualInteraction("manual_scroll")
                schedule(120)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> { AutoScrollSession.manualInteraction("manual_click"); schedule(150) }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> schedule(60)
            else -> schedule(200)
        }
    }

    private fun schedule(delay: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { schedule(delay) }; return }
        if (destroyed || scheduled) return
        scheduled = true
        main.postDelayed(tick, delay)
    }

    private fun interactive(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** Worker thread. */
    private fun sample() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScan < 180) { schedule(180 - (now - lastScan)); return }
        lastScan = now
        if (!interactive()) { outside(true); return }
        val root = rootInActiveWindow ?: run { outside(false); return }
        try {
            val foreground = root.packageName?.toString()
            visitHudVisibility.foreground(foreground)
            val app = ReelApp.byPackage(foreground ?: "")
            if (app == null || !coordinator.allowed(app)) {
                // System UI (notification shade, volume) is not a departure.
                outside(foreground != "com.android.systemui" && foreground != packageName); return
            }
            if (currentApp != null && currentApp != app) outside(true)
            if (currentApp != app) coordinator.pauseDiarySurface()
            currentApp = app
            val capturedEpoch = coordinator.epoch()
            val snapshot = SnapshotReader.read(root, app.packages)
            var frame = AdapterRegistry.of(app).read(snapshot)
            val previous = lastReels
            // A pager mid-swipe briefly shows two half pages; keep the viewer alive through it.
            if (frame.surface == Surface.OUTSIDE && previous != null && previous.app == app &&
                previous.window == snapshot.windowId && previous.frame.route == ReelRoute.VIEWER && now - previous.atMs < 1_500)
                frame = AdapterFrame(Surface.REELS, ReelRoute.VIEWER, method = previous.frame.method)
            val previousPage = previous?.frame?.page
            val currentPage = frame.page
            val sameSlot = frame.route == ReelRoute.HOME && frame.slotKey != null && previous != null &&
                previous.app == app && previous.frame.slotKey == frame.slotKey && now - previous.atMs < 900 &&
                previousPage != null && currentPage != null &&
                kotlin.math.abs(previousPage.top - currentPage.top) < snapshot.screen.height / 2
            if (frame.surface == Surface.REELS) lastReels = Seen(app, snapshot.windowId, now, frame)
            lastFrame = frame

            val decision = coordinator.blockDecision(app, frame)
            block = decision
            val counted = if (decision != null) frame.copy(surface = Surface.EXCLUDED) else frame
            val item = ItemFrame(now, counted.surface, counted.route, counted.identity, counted.keys, counted.category,
                scrollSequence.getOrPut(app) { AtomicLong() }.get(), sameSlot)
            coordinator.observe(app, counted, item, snapshot.windowId, capturedEpoch)
            displayEligible = decision == null && (frame.surface == Surface.REELS ||
                (frame.route == ReelRoute.HOME && frame.method.endsWith(".feed") && frame.surface == Surface.EXCLUDED))
            if (decision != null) main.post { enforceBlock(decision) }
            feedAutoScroll(app, frame, snapshot.windowId, capturedEpoch, now)
            main.post { renderOverlays() }
            when {
                frame.surface == Surface.REELS -> schedule(400)
                displayEligible || decision != null -> schedule(700)
                else -> schedule(1_500) // Cheap ownership check until departure.
            }
        } finally { SnapshotReader.recycle(root) }
    }

    private fun outside(departed: Boolean) {
        val app = currentApp
        displayEligible = false
        block = null
        lastFrame = null
        if (departed) { lastReels = null; currentApp = null; coordinator.pauseDiarySurface() }
        app?.let {
            coordinator.observe(it, AdapterFrame.OUTSIDE, ItemFrame(SystemClock.elapsedRealtime(), Surface.OUTSIDE,
                ReelRoute.VIEWER, null, emptySet(), org.attentionstats.reels.PageCategory.REEL, 0), 0, coordinator.epoch())
        }
        main.post { AutoScrollSession.unavailable("outside_viewer"); renderOverlays() }
    }

    // ---- Overlays --------------------------------------------------------------------------

    private fun renderOverlays() {
        if (destroyed) return
        renderIntervention()
        renderHud()
    }

    private fun renderIntervention() {
        val app = currentApp
        val decision = block
        val reminder = coordinator.reminder
        val wanted: String? = when {
            app == null || !interactive() -> null
            decision != null && decision.app == app -> decision.kind
            interventionKind != null && interventionKind != "break" && interventionApp == app -> interventionKind // Stay until chosen.
            reminder != null && reminder.app == app && displayEligible && SystemClock.elapsedRealtime() > breakQuietUntil -> "break"
            interventionKind == "break" && interventionApp == app -> "break"
            else -> null
        }
        if (wanted == null) { removeIntervention(); return }
        if (intervention != null && interventionKind == wanted && interventionApp == app) return
        removeIntervention()
        val current = checkNotNull(app)
        AutoScrollSession.stop("intervention")
        val session = coordinator.liveOverlay?.visit ?: reminder?.count?.toLong() ?: 0
        val minutes = ((reminder?.durationMs ?: 0) / 60_000).toInt()
        val view = when (wanted) {
            "break" -> InterventionOverlay(this, InterventionOverlay.Mode.BREAK, "Time for a breather?",
                buildString {
                    append("$session short videos")
                    if (minutes > 0) append(" · $minutes min")
                    append(" since you unlocked your phone.")
                }, coordinator.breakSeconds, "I'm done for now", "Keep watching",
                { decide("left"); breakQuietUntil = SystemClock.elapsedRealtime() + 5_000; coordinator.dismissReminder()
                    performGlobalAction(GLOBAL_ACTION_HOME) },
                { decide("continued"); breakQuietUntil = SystemClock.elapsedRealtime() + 5_000; coordinator.dismissReminder() })
            else -> InterventionOverlay(this, InterventionOverlay.Mode.BLOCK,
                if (wanted == "limit") "Daily limit reached" else "${current.observationLabel} are off",
                if (wanted == "limit") "You chose ${decision?.limit} short videos a day on ${current.displayName}. " +
                    "Everything else in the app still works." else
                    "You chose to block them. Everything else in ${current.displayName} still works.",
                coordinator.breakSeconds, "Okay", "Allow 5 minutes",
                { decide("left"); blockActedAt = 0; removeIntervention() },
                { decide("allowed"); coordinator.allowFor(current, 5); removeIntervention(); schedule(0) })
        }
        // Not focusable: system Back/Home/Recents always reach Android, so the screen never traps anyone.
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        // Stop above the navigation bar so its buttons/gesture area stay visible and usable.
        val navigation = navigationBarHeight()
        if (navigation > 0) {
            params.height = resources.displayMetrics.heightPixels.let { visible ->
                if (Build.VERSION.SDK_INT >= 30) getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height() - navigation
                else visible
            }
            params.gravity = Gravity.TOP
        }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            intervention = view; interventionKind = wanted; interventionApp = current; interventionId = null
            view.alpha = 0f; view.animate().alpha(1f).setDuration(220).start()
            pausePlayback(true)
            coordinator.recordIntervention(current, wanted) { id -> if (intervention === view) interventionId = id }
        } catch (_: RuntimeException) { intervention = null; interventionKind = null }
    }

    private fun navigationBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            return getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom
        }
        @android.annotation.SuppressLint("InternalInsetResource", "DiscouragedApi")
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun decide(choice: String) { coordinator.decideIntervention(interventionId, choice) }

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
        intervention?.let { view ->
            view.finish()
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        if (intervention != null) pausePlayback(false)
        intervention = null; interventionKind = null; interventionApp = null; interventionId = null
    }

    /** Holding transient audio focus makes video players pause while a break or block is shown. */
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
        val state = coordinator.liveOverlay
        val active = currentApp
        if (!displayEligible || intervention != null || !coordinator.overlayEnabled || state == null || active != state.app ||
            visitHudVisibility.hiddenFor(state.app) || state.epoch != coordinator.epoch() || !coordinator.allowed(state.app) ||
            !interactive()) {
            if (AutoScrollSession.running && intervention == null) AutoScrollSession.stop("viewer_unavailable")
            removeHud(); return
        }
        if (hud == null) {
            val density = resources.displayMetrics.density
            val view = CounterHudView(this, ::openDashboard, ::pauseObservation, ::hideCounterThisVisit, {
                if (AutoScrollSession.running) AutoScrollSession.stop() else AutoScrollSession.start()
            }, {
                if (AutoScrollSession.enabled) AutoScrollSession.configure(false, 1)
                else if (AutoScrollSession.noticeAccepted) AutoScrollSession.configure(true, 1)
                else openConvenience()
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
            try { hud = view; hudParams = params; getSystemService(WindowManager::class.java).addView(view, params) }
            catch (_: RuntimeException) { hud = null; hudParams = null; return }
        }
        hud?.update(state)
        val viewer = state.route == ReelRoute.VIEWER
        hud?.setAutoScrollControl(viewer && AutoScrollSession.enabled, AutoScrollSession.running, AutoScrollSession.canStart())
        hud?.setAutoScrollOption(viewer, AutoScrollSession.enabled)
    }

    // ---- Auto-scroll (optional convenience) ------------------------------------------------

    private fun feedAutoScroll(app: ReelApp, frame: AdapterFrame, window: Int, epoch: Long, now: Long) {
        if (!AutoScrollSession.enabled || frame.route != ReelRoute.VIEWER || frame.surface != Surface.REELS) {
            if (AutoScrollSession.running) main.post { AutoScrollSession.stop("playback_unavailable") }
            return
        }
        val value = AutoScrollSession.Frame(app, window, frame.identity, epoch, now, frame.progress,
            coordinator.overlayEnabled && !visitHudVisibility.hiddenFor(app))
        main.post { AutoScrollSession.frame(value) { _, token -> advance(token, frame.page) } }
    }

    private fun advance(token: Long, page: LayoutBounds?) {
        if (!AutoScrollSession.current(token)) return
        main.postDelayed({ AutoScrollSession.expire(token, SystemClock.elapsedRealtime()) }, 2_500)
        val bounds = page ?: return AutoScrollSession.stop("context_changed")
        if (intervention != null || hud?.isShown != true || !interactive()) { AutoScrollSession.stop("context_changed"); return }
        try { com.attentionstats.autoscroll.AutoScrollGestureDispatcher.dispatch(this, bounds, token, main) }
        catch (_: RuntimeException) { AutoScrollSession.stop("gesture_unavailable") }
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
    private fun openConvenience() { AutoScrollSession.stop("permission_review"); open("attentionstats://convenience") }
    private fun open(uri: String) {
        startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        removeHud()
    }

    private fun hideCounterThisVisit() {
        currentApp?.let { visitHudVisibility.hide(it) }
        AutoScrollSession.stop("counter_hidden")
        removeHud()
    }

    private fun pauseObservation() {
        coordinator.snapshot({ value ->
            val revision = org.json.JSONObject(value).getString("revision")
            coordinator.setPaused(true, revision, { main.post { removeHud() } }, { main.post { openDashboard() } })
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
        destroyed = true
        removeIntervention()
        removeHud()
        coordinator.stopDiaryLive()
        coordinator.resetUnlockSession()
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        AutoScrollSession.onChanged = {}
        AutoScrollSession.clear()
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        coordinator.serviceConnected = false
        coordinator.removeListener(listener)
        super.onDestroy()
    }
}
