package com.attentionstats.reels

import android.accessibilityservice.AccessibilityService
import com.attentionstats.autoscroll.AutoScrollSession
import com.attentionstats.autoscroll.PlaybackPositionResolver
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.MainActivity
import com.attentionstats.detectors.DetectorRegistry
import com.attentionstats.detectors.SurfaceEvidence
import com.attentionstats.detectors.SurfaceObservation
import com.attentionstats.detectors.LayoutBounds
import com.attentionstats.detectors.ScrollEvidence
import com.attentionstats.detectors.InlineEncounterTracker
import com.attentionstats.detectors.accessibility.StructuralSnapshotReader
import com.attentionstats.detectors.accessibility.DiagnosticScrollTrace
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.attentionstats.reels.ReelRoute
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Package-scoped observation with independently consented, bounded auto-scroll actions. */
class ReelsAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val coordinator by lazy { ReelsCoordinator.get(this) }
    private val listener: () -> Unit = { configurationChanged() }
    private var hud: CounterHudView? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private val visitHudVisibility = VisitHudVisibility()
    private var hudOffsetX = 0
    private var hudOffsetY: Int? = null
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f
    private var scheduled = false
    private var scanning = false
    @Volatile private var destroyed = false
    private val worker = Executors.newSingleThreadExecutor()
    private var lastScan = 0L
    @Volatile private var currentApp: ReelApp? = null
    @Volatile private var foregroundSequence = 0L
    @Volatile private var displayEligible = false
    private data class Page(val value: Long?, val window: Int, val epoch: Long, val scroll: ScrollEvidence)
    private val pages = ConcurrentHashMap<ReelApp, Page>()
    private data class InlineScope(val window: Int, val epoch: Long)
    private data class InlineMotion(val scope: InlineScope, val total: Long)
    private val inlineScopes = ConcurrentHashMap<ReelApp, InlineScope>()
    private val inlineMotion = ConcurrentHashMap<ReelApp, InlineMotion>()
    private val inlineTrackers = ConcurrentHashMap<ReelApp, InlineEncounterTracker>()
    @Volatile private var freshHomeEntry = false
    private val scrollTrace = DiagnosticScrollTrace()
    private val contentIdentity = com.attentionstats.detectors.accessibility.TransientContentIdentity()
    @Volatile private var calibrationStart = 0L
    private var lastConfiguration = ""
    private val tick = Runnable {
        scheduled = false
        if (scanning) schedule(250) else {
            scanning = true
            worker.execute {
                try { if (!destroyed) sampleActiveWindow() }
                catch (_: RuntimeException) { outside(SystemClock.elapsedRealtime()) }
                finally { main.post { scanning = false } }
            }
        }
    }

    override fun onServiceConnected() {
        if (com.attentionstats.BuildConfig.DEBUG) probeService = this
        AutoScrollSession.onChanged = { main.post { renderHud() } }
        coordinator.serviceConnected = true
        coordinator.addListener(listener)
        coordinator.clearStructure()
        coordinator.snapshot({ main.post { configurationChanged() } }, { main.post { removeHud() } })
        configurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val app = ReelApp.byPackage(event.packageName?.toString() ?: "") ?: return
        if (!coordinator.allowed(app)) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            inlineScopes.remove(app)
            foregroundSequence++
            AutoScrollSession.manualInteraction("manual_click")
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            inlineScopes.remove(app)
            foregroundSequence++
        }
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            outside(SystemClock.elapsedRealtime()); return
        }
        val detector = DetectorRegistry.detector(app, coordinator.installedVersion(app))
        if (detector == null && !coordinator.calibrationEnabled) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val capturedEpoch = coordinator.epoch()
            val source = event.source
            try {
                val safeSource = source != null && source.packageName?.toString() == app.packageName &&
                    source.windowId == event.windowId && source.isVisibleToUser && !source.isEditable && !source.isPassword
                val id = if (safeSource) StructuralSnapshotReader.resourceId(app, source.viewIdResourceName) else null
                if (coordinator.calibrationEnabled) {
                    val bounds = if (safeSource) Rect().also { source.getBoundsInScreen(it) } else null
                    val sample = JSONObject().put("source", id ?: "").put("from", event.fromIndex)
                        .put("sourceClass", if (safeSource) StructuralSnapshotReader.className(source) else "")
                        .put("to", event.toIndex).put("items", event.itemCount)
                        .put("scrollY", event.scrollY).put("maxScrollY", event.maxScrollY)
                        .put("deltaY", if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0)
                        .put("sourceBounds", bounds?.let { JSONObject().put("left", it.left).put("top", it.top)
                            .put("right", it.right).put("bottom", it.bottom) } ?: JSONObject.NULL)
                    scrollTrace.record(DiagnosticScrollTrace.Scope(app, capturedEpoch, event.windowId),
                        SystemClock.uptimeMillis(), event.eventTime, sample)
                }
                if (detector != null && safeSource) {
                    val bounds = Rect().also { source.getBoundsInScreen(it) }
                    val evidence = ScrollEvidence(id, StructuralSnapshotReader.role(source),
                        LayoutBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        event.fromIndex, event.toIndex, event.itemCount)
                    val inlineScope = InlineScope(event.windowId, capturedEpoch)
                    if (inlineScopes[app] == inlineScope && detector.acceptsInlineScroll(evidence)) {
                        foregroundSequence++
                        val previous = inlineMotion[app]?.takeIf { it.scope == inlineScope }?.total ?: 0L
                        val delta = if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY.toLong() else 0L
                        inlineMotion[app] = InlineMotion(inlineScope, Math.addExact(previous, delta))
                    }
                    if (detector.acceptsScroll(evidence)) {
                        // Player progress and nested controls also emit scroll events; only the
                        // admitted viewer pager is evidence of a manual page interaction here.
                        foregroundSequence++
                        AutoScrollSession.manualInteraction("manual_scroll")
                        val page = if (event.fromIndex >= 0 && event.fromIndex == event.toIndex)
                            event.fromIndex.toLong() else null
                        pages[app] = Page(page, event.windowId, capturedEpoch, evidence)
                    }
                }
            } finally { recycleCompat(source) }
        }
        schedule(80)
    }

    private fun configurationChanged() {
        if (destroyed) return
        val selected = ReelApp.entries.filter { coordinator.allowed(it) }.map { it.packageName }
        val key = selected.joinToString() + "/" + coordinator.epoch() + "/" + coordinator.calibrationEnabled
        renderHud()
        if (key == lastConfiguration) return
        lastConfiguration = key
        scrollTrace.clear()
        contentIdentity.reset()
        pages.clear()
        inlineScopes.clear(); inlineMotion.clear(); inlineTrackers.clear()
        serviceInfo = serviceInfo?.apply {
            packageNames = (selected.ifEmpty { listOf(packageName) }).toTypedArray()
        }
        if (coordinator.calibrationEnabled) {
            if (calibrationStart == 0L) calibrationStart = SystemClock.elapsedRealtime()
        } else {
            if (calibrationStart > 0) coordinator.clearStructure()
            calibrationStart = 0L
        }
        if (selected.isEmpty()) {
            main.removeCallbacks(tick)
            scheduled = false
            currentApp = null
            pages.clear()
        } else schedule(0)
    }

    private fun schedule(delay: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { schedule(delay) }; return }
        if (destroyed) return
        if (!scheduled) { scheduled = true; main.postDelayed(tick, delay) }
    }

    private fun sampleActiveWindow() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScan < 250) { schedule(250 - (now - lastScan)); return }
        lastScan = now
        if (calibrationStart > 0 && now - calibrationStart >= 600_000) coordinator.setCalibration(false)
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) { outside(now); return }
        val root = rootInActiveWindow ?: run { outside(now); return }
        try {
            visitHudVisibility.foreground(root.packageName?.toString())
            val capturedEpoch = coordinator.epoch()
            // Outside selected packages, inspect only this root's package, never its children/content.
            val app = ReelApp.byPackage(root.packageName?.toString() ?: "")
            if (app == null || !coordinator.allowed(app)) {
                outside(now, root.packageName?.toString() != "com.android.systemui"); return
            }
            if (currentApp != null && currentApp != app) outside(now, true)
            currentApp = app
            val detector = DetectorRegistry.detector(app, coordinator.installedVersion(app))
            if (detector == null && !coordinator.calibrationEnabled) { outside(now); return }
            val diagnostic = coordinator.calibrationEnabled
            val ticket = if (diagnostic) scrollTrace.begin(DiagnosticScrollTrace.Scope(app, capturedEpoch, root.windowId),
                SystemClock.uptimeMillis()) else null
            val identity = if (detector?.usesContentIdentity == true)
                contentIdentity.begin(app, root.windowId, capturedEpoch) else null
            val collectProgress = AutoScrollSession.enabled && coordinator.overlayEnabled && !visitHudVisibility.hiddenFor(app) &&
                AutoScrollSession.candidate(app, coordinator.installedVersion(app))
            val scanSequence = foregroundSequence
            val shape = StructuralSnapshotReader.inspect(app, root, detector, diagnostic, identity, collectProgress)
            val association = ticket?.let { scrollTrace.finish(it, SystemClock.uptimeMillis()) }
            val page = pages[app]?.takeIf { it.window == root.windowId && it.epoch == capturedEpoch }
            val viewport = shape.viewport
            val evidence = SurfaceEvidence(shape.ids, shape.bounded, shape.editable,
                shape.identityPage ?: shape.centerPage ?: page?.value,
                LayoutBounds(viewport.getInt("left"), viewport.getInt("top"), viewport.getInt("right"), viewport.getInt("bottom")),
                shape.labels, page?.scroll, shape.inlineAnchors, shape.inlineComplete,
                inlineScopes[app] == InlineScope(root.windowId, capturedEpoch), shape.inlineRootPage)
            var observation = detector?.detect(evidence) ?: SurfaceObservation(Surface.UNKNOWN)
            val inline = if (observation.surface !in setOf(Surface.REELS, Surface.EXCLUDED)) detector?.inlineSelection(evidence) else null
            if (inline != null) {
                val scope = InlineScope(root.windowId, capturedEpoch)
                inlineScopes[app] = scope
                val tracker = inlineTrackers[app]?.takeIf { it.matches(root.windowId, capturedEpoch) }
                    ?: InlineEncounterTracker(root.windowId, capturedEpoch, freshHomeEntry).also {
                        inlineTrackers[app] = it
                        freshHomeEntry = false
                    }
                val total = inlineMotion[app]?.takeIf { it.scope == scope }?.total ?: 0L
                val encounter = if (scanSequence == foregroundSequence) tracker.sample(inline, total, evidence.viewport)
                    else InlineEncounterTracker.Result(null, false)
                observation = SurfaceObservation(if (encounter.page == null) Surface.EXCLUDED else Surface.REELS,
                    encounter.page, org.attentionstats.reels.PageCategory.REEL, ReelRoute.HOME, encounter.freshEntry)
            } else inlineScopes.remove(app)
            val surface = observation.surface
            if (surface == Surface.OUTSIDE || surface == Surface.UNKNOWN) pages.remove(app)
            val current = rootInActiveWindow
            val sameWindow = try { current != null && current.packageName?.toString() == app.packageName && current.windowId == root.windowId }
                finally { recycleCompat(current) }
            if (!sameWindow || capturedEpoch != coordinator.epoch() || keyguard.isKeyguardLocked || !power.isInteractive) {
                outside(SystemClock.elapsedRealtime()); return
            }
            if (diagnostic && association != null && coordinator.calibrationEnabled) coordinator.recordStructure(app,
                JSONObject().put("version", coordinator.installedVersion(app)).put("nodes", shape.nodes)
                    .put("bounded", shape.bounded).put("editable", shape.editable)
                    .put("ids", JSONArray(shape.ids.toList())).put("positions", shape.positions)
                    .put("controls", shape.controls).put("association", association)
                    .put("geometryRevision", 1).put("viewport", shape.viewport).put("anchors", shape.anchors)
                    .put("interfaceRevision", 3).put("interfaceLabels", shape.interfaceLabels)
                    .put("classes", JSONArray(shape.classes.toList())).put("scrolls", JSONArray()), capturedEpoch)
            displayEligible = surface == Surface.REELS
            if (collectProgress && observation.route == ReelRoute.VIEWER) {
                val position = PlaybackPositionResolver.position(app, shape.numericRanges,
                    LayoutBounds(viewport.getInt("left"), viewport.getInt("top"), viewport.getInt("right"), viewport.getInt("bottom")),
                    shape.bounded && !shape.editable)
                val frame = AutoScrollSession.Frame(app, root.windowId, observation.page, capturedEpoch,
                    SystemClock.elapsedRealtime(), position, surface == Surface.REELS && coordinator.overlayEnabled)
                main.post { AutoScrollSession.frame(frame, ::advanceAfterCompletion); renderHud() }
            } else if (AutoScrollSession.running) main.post { AutoScrollSession.stop("playback_unavailable") }
            if (surface != Surface.REELS) removeHud()
            coordinator.observe(app, CounterSignal(SystemClock.elapsedRealtime(), surface, observation.page, root.windowId,
                observation.category, observation.route, observation.freshEntry), capturedEpoch)
            if (surface == Surface.REELS || coordinator.calibrationEnabled) schedule(500)
            else if (inline != null || visitHudVisibility.awaitingDeparture()) schedule(1_000)
        } finally { recycleCompat(root) }
    }

    private fun outside(now: Long, confirmedDeparture: Boolean = false) {
        if (confirmedDeparture) {
            freshHomeEntry = true
            inlineScopes.clear(); inlineMotion.clear(); inlineTrackers.clear()
        }
        main.post { AutoScrollSession.unavailable("outside_viewer") }
        displayEligible = false
        removeHud()
        currentApp?.let { coordinator.observe(it, CounterSignal(now, Surface.OUTSIDE, null, 0), coordinator.epoch()) }
        currentApp = null
        pages.clear()
        scrollTrace.clear()
        contentIdentity.reset()
        // A transient system root is not departure. Keep checking root ownership until
        // departure is confirmed, including while target-package callbacks are absent.
        if ((visitHudVisibility.awaitingDeparture() || ReelApp.entries.any { coordinator.allowed(it) }) &&
            getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked) schedule(1_000)
    }

    private fun renderHud() {
        val state = coordinator.liveOverlay
        val active = currentApp
        if (!displayEligible || !coordinator.overlayEnabled || state == null || active != state.app ||
            visitHudVisibility.hiddenFor(state.app) ||
            state.epoch != coordinator.epoch() || !coordinator.allowed(state.app) ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive) {
            if (AutoScrollSession.running) AutoScrollSession.stop("viewer_unavailable")
            removeHud(); return
        }
        if (hud == null) {
            val density = resources.displayMetrics.density
            val view = CounterHudView(this, ::openDashboard, ::pauseObservation,
                ::hideCounterThisVisit, {
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
                val current = hudParams
                if (current != null) {
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
        hud?.setAutoScrollControl(state.route == ReelRoute.VIEWER && AutoScrollSession.enabled && AutoScrollSession.candidate(state.app,
            coordinator.installedVersion(state.app)), AutoScrollSession.running, AutoScrollSession.canStart())
        hud?.setAutoScrollOption(state.route == ReelRoute.VIEWER && AutoScrollSession.candidate(state.app,
            coordinator.installedVersion(state.app)), AutoScrollSession.enabled)
    }

    private fun advanceAfterCompletion(frame: AutoScrollSession.Frame, token: Long) {
        if (!AutoScrollSession.current(token)) return
        main.postDelayed({ AutoScrollSession.expire(token, SystemClock.elapsedRealtime()) }, 2_500)
        fun cancel(reason: String) { main.post { if (AutoScrollSession.current(token)) AutoScrollSession.stop(reason) } }
        // Keep the Stop button responsive while remote nodes are inspected.
        worker.execute {
            val root = rootInActiveWindow
            try {
                val sequence = foregroundSequence
                val detector = DetectorRegistry.detector(frame.app, coordinator.installedVersion(frame.app))
                if (root == null || detector == null || root.packageName?.toString() != frame.app.packageName ||
                    root.windowId != frame.window || coordinator.epoch() != frame.epoch || !coordinator.allowed(frame.app) ||
                    SystemClock.elapsedRealtime() - frame.atMs !in 0..750) {
                    cancel("context_changed"); return@execute
                }
                val identity = if (detector.usesContentIdentity)
                    contentIdentity.begin(frame.app, frame.window, frame.epoch) else null
                val shape = StructuralSnapshotReader.inspect(frame.app, root, detector, false, identity)
                val viewport = shape.viewport
                val bounds = LayoutBounds(viewport.getInt("left"), viewport.getInt("top"), viewport.getInt("right"), viewport.getInt("bottom"))
                val currentPage = pages[frame.app]?.takeIf { it.window == frame.window && it.epoch == frame.epoch }
                val detected = detector.detect(SurfaceEvidence(shape.ids, shape.bounded, shape.editable,
                    shape.identityPage ?: shape.centerPage ?: currentPage?.value, bounds, shape.labels, currentPage?.scroll))
                if (detected.surface != Surface.REELS || detected.page != frame.page || bounds.width <= 0 || bounds.height <= 0 ||
                    sequence != foregroundSequence) { cancel("context_changed"); return@execute }
                val verifiedAt = SystemClock.elapsedRealtime()
                main.post {
                    if (!AutoScrollSession.current(token)) return@post
                    val active = rootInActiveWindow
                    try {
                        if (destroyed || active == null || active.packageName?.toString() != frame.app.packageName ||
                            active.windowId != frame.window || foregroundSequence != sequence || coordinator.epoch() != frame.epoch ||
                            !coordinator.allowed(frame.app) || !coordinator.overlayEnabled || !displayEligible || hud?.isShown != true ||
                            visitHudVisibility.hiddenFor(frame.app) || !coordinator.grantEnabled() ||
                            getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                            !getSystemService(PowerManager::class.java).isInteractive ||
                            SystemClock.elapsedRealtime() - verifiedAt !in 0..750) {
                            AutoScrollSession.stop("context_changed"); return@post
                        }
                        com.attentionstats.autoscroll.AutoScrollGestureDispatcher.dispatch(this, bounds, token, main)
                    } catch (_: RuntimeException) { AutoScrollSession.stop("gesture_unavailable") }
                    finally { recycleCompat(active) }
                }
            } catch (_: RuntimeException) { cancel("gesture_unavailable") }
            finally { recycleCompat(root) }
        }
    }

    private fun moveCounter(dx: Float, dy: Float) {
        val view = hud ?: return
        val params = hudParams ?: return
        if (view.width <= 0 || view.height <= 0) return
        val metrics = resources.displayMetrics
        val inset = (8 * metrics.density).toInt()
        // Keep both compact and expanded bounds inside the usable display, including after rotation.
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
        // Android reports fractional raw coordinates. Retain subpixel displacement across events.
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

    private fun openDashboard() {
        startActivity(Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("attentionstats://reels"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        removeHud()
    }

    private fun openConvenience() {
        AutoScrollSession.stop("permission_review")
        startActivity(Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("attentionstats://convenience"))
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
            val revision = JSONObject(value).getString("revision")
            coordinator.setPaused(true, revision, { removeHud() }, { main.post { openDashboard() } })
        }, { main.post { openDashboard() } })
    }

    private fun removeHud() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { removeHud() }; return }
        hud?.let { view -> try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { } }
        hud = null
        hudParams = null
    }

    @Suppress("DEPRECATION") // recycle is required on supported API 24-32, a no-op on newer Android.
    private fun recycleCompat(node: AccessibilityNodeInfo?) { if (Build.VERSION.SDK_INT < 33) node?.recycle() }
    override fun onInterrupt() { outside(SystemClock.elapsedRealtime()) }
    override fun onDestroy() {
        AutoScrollSession.onChanged = {}
        AutoScrollSession.clear()
        if (probeService === this) probeService = null
        destroyed = true
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        coordinator.serviceConnected = false
        coordinator.removeListener(listener)
        outside(SystemClock.elapsedRealtime())
        super.onDestroy()
    }

    companion object {
        @Volatile private var probeService: ReelsAccessibilityService? = null
        /** Numeric/boolean Home diagnosis only; no layout keys or content leave this call. */
        fun inspectHomeForOperator(): JSONObject {
            check(com.attentionstats.BuildConfig.DEBUG)
            val service = probeService ?: return JSONObject().put("withheld", "service_unavailable")
            if (!service.getSystemService(PowerManager::class.java).isInteractive ||
                service.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
                return JSONObject().put("withheld", "locked")
            val root = service.rootInActiveWindow ?: return JSONObject().put("withheld", "root_unavailable")
            try {
                val app = ReelApp.byPackage(root.packageName?.toString() ?: "")
                    ?: return JSONObject().put("withheld", "outside")
                if (app !in setOf(ReelApp.INSTAGRAM, ReelApp.FACEBOOK) || !service.coordinator.allowed(app))
                    return JSONObject().put("withheld", "source_off")
                val detector = DetectorRegistry.detector(app, service.coordinator.installedVersion(app))
                    ?: return JSONObject().put("withheld", "version")
                val shape = StructuralSnapshotReader.inspect(app, root, detector, false)
                val viewport = shape.viewport
                val context = service.inlineScopes[app] == InlineScope(root.windowId, service.coordinator.epoch())
                val selected = shape.inlineRootPage == 0 || shape.inlineAnchors.any { it.id == "feed_tab" && it.selected } ||
                    shape.labels.any { it.token == "home_tab" && it.selected }
                val evidence = SurfaceEvidence(shape.ids, shape.bounded, shape.editable, null,
                    LayoutBounds(viewport.getInt("left"), viewport.getInt("top"), viewport.getInt("right"), viewport.getInt("bottom")),
                    shape.labels, inlineAnchors = shape.inlineAnchors, inlineComplete = shape.inlineComplete,
                    inlineContext = context, inlineRootPage = shape.inlineRootPage)
                val inline = detector.inlineSelection(evidence)
                return JSONObject().put("app", app.key).put("bounded", shape.bounded).put("editable", shape.editable)
                    .put("homeSelected", selected).put("homePane", shape.inlineRootPage ?: JSONObject.NULL)
                    .put("context", context).put("selection", inline != null)
                    .put("video", inline?.indicator != null).put("mediaAnchors", shape.inlineAnchors.count { it.id == "media_group" })
                    .put("videoIndicators", shape.inlineAnchors.count { it.id == "indicator_background_view" })
                    .put("homeRoute", service.coordinator.liveOverlay?.route == ReelRoute.HOME)
                    .put("committed", service.coordinator.liveOverlay?.let { it.route == ReelRoute.HOME && it.visit > 0 } == true)
            } finally { service.recycleCompat(root) }
        }
        /** Explicit adb operator qualification only; no production bridge or retained content. */
        fun inspectPlaybackForOperator(): JSONObject {
            check(com.attentionstats.BuildConfig.DEBUG)
            fun withheld(reason: String) = JSONObject().put("withheld", reason)
            val service = probeService ?: return withheld("service_unavailable")
            if (!service.getSystemService(PowerManager::class.java).isInteractive ||
                service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return withheld("locked")
            val root = service.rootInActiveWindow ?: return withheld("root_unavailable")
            try {
                val app = ReelApp.byPackage(root.packageName?.toString() ?: "") ?: return withheld("outside")
                if (!service.coordinator.allowed(app)) return withheld("source_off").put("app", app.key)
                return com.attentionstats.autoscroll.PlaybackProgressReader.inspect(root, app)
                    .put("viewer", service.displayEligible && service.currentApp == app && service.coordinator.liveOverlay?.route == ReelRoute.VIEWER)
            } finally { service.recycleCompat(root) }
        }
    }
}
