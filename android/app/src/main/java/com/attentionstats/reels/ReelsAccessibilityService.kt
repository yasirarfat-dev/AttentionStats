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
import org.attentionstats.reels.CounterSignal
import org.attentionstats.reels.ReelApp
import org.attentionstats.reels.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Passive, package-scoped metadata only. Never reads text/descriptions or performs node actions. */
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
    private val pages = ConcurrentHashMap<ReelApp, Long>()
    private val samples = mutableMapOf<ReelApp, ArrayDeque<JSONObject>>()
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
        val definition = DetectorRegistry.definition(app, coordinator.installedVersion(app))
        if (definition == null && !coordinator.calibrationEnabled) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val source = event.source
            try {
                val id = resourceId(app, source?.viewIdResourceName)
                val sample = JSONObject().put("source", id ?: "").put("from", event.fromIndex)
                    .put("sourceClass", className(source))
                    .put("to", event.toIndex).put("items", event.itemCount)
                    .put("scrollY", event.scrollY).put("maxScrollY", event.maxScrollY)
                    .put("deltaY", if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0)
                synchronized(samples) {
                    samples.getOrPut(app) { ArrayDeque() }.apply {
                        if (size == 16) removeFirst()
                        addLast(sample)
                    }
                }
                if (definition != null && id in definition.scrollSourceIds && event.fromIndex >= 0 &&
                    event.fromIndex == event.toIndex) pages[app] = event.fromIndex.toLong()
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
        serviceInfo = serviceInfo?.apply {
            packageNames = (selected.ifEmpty { listOf(packageName) }).toTypedArray()
        }
        if (coordinator.calibrationEnabled) {
            if (calibrationStart == 0L) calibrationStart = SystemClock.elapsedRealtime()
        } else {
            if (calibrationStart > 0) coordinator.clearStructure()
            calibrationStart = 0L
            synchronized(samples) { samples.clear() }
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
            val definition = DetectorRegistry.definition(app, coordinator.installedVersion(app))
            if (definition == null && !coordinator.calibrationEnabled) { outside(now); return }
            val shape = inspect(app, root, definition)
            if (coordinator.calibrationEnabled) coordinator.recordStructure(app,
                JSONObject().put("version", coordinator.installedVersion(app)).put("nodes", shape.nodes)
                    .put("bounded", shape.bounded).put("editable", shape.editable)
                    .put("ids", JSONArray(shape.ids.toList())).put("positions", shape.positions)
                    .put("controls", shape.controls)
                    .put("classes", JSONArray(shape.classes.toList()))
                    .put("scrolls", JSONArray(synchronized(samples) { samples[app]?.toList() ?: emptyList<JSONObject>() })), capturedEpoch)
            val surface = when {
                shape.editable || definition?.excludedIds?.any { it in shape.ids } == true -> Surface.EXCLUDED
                definition == null || !shape.bounded -> Surface.UNKNOWN
                definition.requiredIds.all { it in shape.ids } -> Surface.REELS
                definition.scrollSourceIds.any { it in shape.ids } -> Surface.REELS // Known viewer, page still settling.
                else -> Surface.OUTSIDE
            }
            val page = if (definition?.requiredIds?.all { it in shape.ids } == true) shape.centerPage ?: pages[app] else null
            val current = rootInActiveWindow
            val sameWindow = try { current != null && current.packageName?.toString() == app.packageName && current.windowId == root.windowId }
                finally { recycleCompat(current) }
            if (!sameWindow || capturedEpoch != coordinator.epoch() || keyguard.isKeyguardLocked || !power.isInteractive) {
                outside(SystemClock.elapsedRealtime()); return
            }
            displayEligible = surface == Surface.REELS
            if (surface != Surface.REELS) removeHud()
            coordinator.observe(app, CounterSignal(SystemClock.elapsedRealtime(), surface, page, root.windowId,
                DetectorRegistry.category(app, shape.ids)), capturedEpoch)
            if (surface == Surface.REELS || coordinator.calibrationEnabled) schedule(500)
        } finally { recycleCompat(root) }
    }

    private fun outside(now: Long) {
        displayEligible = false
        removeHud()
        currentApp?.let { coordinator.observe(it, CounterSignal(now, Surface.OUTSIDE, null, 0), coordinator.epoch()) }
        currentApp = null
        pages.clear()
    }

    private data class Shape(val ids: Set<String>, val nodes: Int, val bounded: Boolean,
        val editable: Boolean, val positions: JSONArray, val controls: JSONArray,
        val centerPage: Long?, val classes: Set<String>)

    private fun inspect(app: ReelApp, root: AccessibilityNodeInfo, definition: DetectorDefinition?): Shape {
        val ids = linkedSetOf<String>()
        val classes = linkedSetOf<String>()
        val positions = JSONArray()
        val controls = JSONArray()
        var nodes = 0
        var editable = false
        var centerPage: Long? = null
        var truncated = false
        val screen = Rect().also { root.getBoundsInScreen(it) }
        data class Pending(val node: AccessibilityNodeInfo, val owner: String?, val owned: Boolean)
        val queue = ArrayDeque<Pending>()
        queue.add(Pending(root, null, false))
        try {
            while (queue.isNotEmpty() && nodes < 192) {
                val pending = queue.removeFirst()
                val node = pending.node
                try {
                    nodes++
                    if (node.packageName?.toString() != app.packageName || !node.isVisibleToUser) continue
                    // Do not read even structural descendants of editable/password fields.
                    if (node.isEditable || node.isPassword) { editable = true; continue }
                    if (classes.size < 24) className(node).takeIf { it.isNotEmpty() }?.let { classes.add(it) }
                    val id = resourceId(app, node.viewIdResourceName)
                    if (id != null && ids.size < 96) ids.add(id)
                    if (id != null && node.isClickable && controls.length() < 32) {
                        val bounds = Rect().also { node.getBoundsInScreen(it) }
                        controls.put(JSONObject().put("id", id).put("class", className(node))
                            .put("left", bounds.left).put("top", bounds.top)
                            .put("right", bounds.right).put("bottom", bounds.bottom))
                    }
                    val owner = if (id != null && (id in (definition?.scrollSourceIds ?: emptySet()) || node.isScrollable)) id else pending.owner
                    val item = node.collectionItemInfo
                    if (item != null && positions.length() < 12) {
                        val bounds = Rect().also { node.getBoundsInScreen(it) }
                        val centered = bounds.contains(screen.centerX(), screen.centerY())
                        positions.put(JSONObject().put("id", id ?: "").put("owner", owner ?: "")
                            .put("row", item.rowIndex).put("column", item.columnIndex).put("centered", centered))
                        if (centered && owner in (definition?.scrollSourceIds ?: emptySet()) && item.rowIndex >= 0)
                            centerPage = item.rowIndex.toLong()
                    }
                    val remaining = 192 - nodes - queue.size
                    if (node.childCount > remaining) truncated = true
                    for (index in 0 until minOf(node.childCount, remaining)) {
                        node.getChild(index)?.let { queue.add(Pending(it, owner, true)) }
                    }
                } finally { if (pending.owned) recycleCompat(node) }
            }
            return Shape(ids, nodes, queue.isEmpty() && !truncated, editable, positions, controls, centerPage, classes)
        } finally { queue.forEach { if (it.owned) recycleCompat(it.node) } }
    }

    private fun resourceId(app: ReelApp, value: String?): String? {
        val prefix = app.packageName + ":id/"
        if (value == null || !value.startsWith(prefix)) return null
        val name = value.removePrefix(prefix)
        return name.takeIf { Regex("[a-zA-Z0-9_]{1,80}").matches(it) }
    }

    private fun className(node: AccessibilityNodeInfo?): String = node?.className?.toString()
        ?.takeIf { Regex("[A-Za-z0-9_.$]{1,120}").matches(it) } ?: ""

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
