package com.attentionstats.reels

import android.accessibilityservice.AccessibilityService
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
import com.attentionstats.detectors.accessibility.StructuralSnapshotReader
import com.attentionstats.detectors.accessibility.DiagnosticScrollTrace
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Passive package-scoped metadata. Temporary Facebook label matching is isolated under ADR-045. */
class ReelsAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val coordinator by lazy { ReelsCoordinator.get(this) }
    private val listener: () -> Unit = { configurationChanged() }
    private var hud: CounterHudView? = null
    private var hudParams: WindowManager.LayoutParams? = null
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
    @Volatile private var displayEligible = false
    private data class Page(val value: Long?, val window: Int, val epoch: Long, val scroll: ScrollEvidence)
    private val pages = ConcurrentHashMap<ReelApp, Page>()
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
                    if (detector.acceptsScroll(evidence)) {
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
            val capturedEpoch = coordinator.epoch()
            // Outside selected packages, inspect only this root's package, never its children/content.
            val app = ReelApp.byPackage(root.packageName?.toString() ?: "")
            if (app == null || !coordinator.allowed(app)) { outside(now); return }
            if (currentApp != null && currentApp != app) outside(now)
            currentApp = app
            val detector = DetectorRegistry.detector(app, coordinator.installedVersion(app))
            if (detector == null && !coordinator.calibrationEnabled) { outside(now); return }
            val diagnostic = coordinator.calibrationEnabled
            val ticket = if (diagnostic) scrollTrace.begin(DiagnosticScrollTrace.Scope(app, capturedEpoch, root.windowId),
                SystemClock.uptimeMillis()) else null
            val identity = if (detector?.usesContentIdentity == true)
                contentIdentity.begin(app, root.windowId, capturedEpoch) else null
            val shape = StructuralSnapshotReader.inspect(app, root, detector, diagnostic, identity)
            val association = ticket?.let { scrollTrace.finish(it, SystemClock.uptimeMillis()) }
            val page = pages[app]?.takeIf { it.window == root.windowId && it.epoch == capturedEpoch }
            val viewport = shape.viewport
            val observation = detector?.detect(SurfaceEvidence(shape.ids, shape.bounded, shape.editable,
                shape.identityPage ?: shape.centerPage ?: page?.value,
                LayoutBounds(viewport.getInt("left"), viewport.getInt("top"), viewport.getInt("right"), viewport.getInt("bottom")),
                shape.labels, page?.scroll)) ?: SurfaceObservation(Surface.UNKNOWN)
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
                    .put("interfaceRevision", 2).put("interfaceLabels", shape.interfaceLabels)
                    .put("classes", JSONArray(shape.classes.toList())).put("scrolls", JSONArray()), capturedEpoch)
            displayEligible = surface == Surface.REELS
            if (surface != Surface.REELS) removeHud()
            coordinator.observe(app, CounterSignal(SystemClock.elapsedRealtime(), surface, observation.page, root.windowId,
                observation.category), capturedEpoch)
            if (surface == Surface.REELS || coordinator.calibrationEnabled) schedule(500)
        } finally { recycleCompat(root) }
    }

    private fun outside(now: Long) {
        displayEligible = false
        removeHud()
        currentApp?.let { coordinator.observe(it, CounterSignal(now, Surface.OUTSIDE, null, 0), coordinator.epoch()) }
        currentApp = null
        pages.clear()
        scrollTrace.clear()
        contentIdentity.reset()
    }

    private fun renderHud() {
        val state = coordinator.liveOverlay
        val active = currentApp
        if (!displayEligible || !coordinator.overlayEnabled || state == null || active != state.app ||
            state.epoch != coordinator.epoch() || !coordinator.allowed(state.app) ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive) { removeHud(); return }
        if (hud == null) {
            val density = resources.displayMetrics.density
            val view = CounterHudView(this, ::openDashboard, { counterControl(pause = true) },
                { counterControl(pause = false) })
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

    private fun counterControl(pause: Boolean) {
        coordinator.snapshot({ value ->
            val revision = JSONObject(value).getString("revision")
            if (pause) coordinator.setPaused(true, revision, { removeHud() }, { main.post { openDashboard() } })
            else coordinator.setOverlay(false, revision, { removeHud() }, { main.post { openDashboard() } })
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
        destroyed = true
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        coordinator.serviceConnected = false
        coordinator.removeListener(listener)
        outside(SystemClock.elapsedRealtime())
        super.onDestroy()
    }
}
