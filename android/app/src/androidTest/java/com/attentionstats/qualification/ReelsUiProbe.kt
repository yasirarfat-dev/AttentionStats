package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import android.widget.FrameLayout
import com.attentionstats.MainActivity
import com.attentionstats.reels.CounterHudView
import com.attentionstats.reels.ScrollingCountView
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.PageCategory
import org.attentionstats.reels.ReelApp
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads and captures only our Activity window. Never queries other apps or grants consent. */
internal class ReelsUiProbe(private val instrumentation: Instrumentation) {
    var checks = 0
        private set
    var stage = "launch"
        private set
    private val visitedRoutes = mutableSetOf<String>()
    val routes: Int get() = visitedRoutes.size
    private lateinit var activity: MainActivity

    private fun resumeOperatorActivity() {
        // startActivitySync waits for a new Activity and can hang on our existing singleTask instance.
        val application = instrumentation.targetContext.applicationContext as android.app.Application
        val resumed = CountDownLatch(1)
        val callback = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(candidate: android.app.Activity) {
                if (candidate is MainActivity) { activity = candidate; resumed.countDown() }
            }
            override fun onActivityCreated(candidate: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(candidate: android.app.Activity) = Unit
            override fun onActivityPaused(candidate: android.app.Activity) = Unit
            override fun onActivityStopped(candidate: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(candidate: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(candidate: android.app.Activity) = Unit
        }
        instrumentation.runOnMainSync {
            application.registerActivityLifecycleCallbacks(callback)
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("attentionstats://reels"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        try { check(resumed.await(20, TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { application.unregisterActivityLifecycleCallbacks(callback) } }
    }

    /** ADR-042 operator flow, never part of synthetic qualification or a production action. */
    fun operatorStartStructure() {
        stage = "operator_power"
        check(android.os.Build.VERSION.SDK_INT >= 26)
        check(instrumentation.targetContext.getSystemService(PowerManager::class.java).isInteractive &&
            !instrumentation.targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        stage = "operator_resume"
        resumeOperatorActivity()
        stage = "operator_reels"
        awaitText("Your Reels")
        // User-authorized operator only: review and accept the updated source notices in our UI.
        for (app in ReelApp.entries) {
            val done = CountDownLatch(1)
            var current = false
            var installed = false
            ReelsCoordinator.get(instrumentation.targetContext).snapshot({
                val sources = org.json.JSONObject(it).getJSONArray("sources")
                val source = (0 until sources.length()).map { index -> sources.getJSONObject(index) }
                    .single { row -> row.getString("app") == app.key }
                installed = source.getBoolean("installed")
                current = source.getBoolean("enabled") && source.getBoolean("consentCurrent")
                done.countDown()
            }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS))
            if (installed && !current) {
                scrollTop()
                val label = "Observe ${app.observationLabel}"
                awaitText(label, scrollSearch = true); tap(label)
                acceptOwnDialog("I agree — enable observation")
                SystemClock.sleep(1_500)
                check(ReelsCoordinator.get(instrumentation.targetContext).allowed(app))
            }
        }
        scrollTop()
        stage = "operator_structure_button"
        awaitText("Start structure check", scrollSearch = true)
        check(ReelsCoordinator.get(instrumentation.targetContext).grantEnabled())
        tap("Start structure check")
        acceptOwnDialog("Allow structure check")
        stage = "operator_active"
        awaitText("Structure check active", scrollSearch = true)
    }

    private fun acceptOwnDialog(button: String) {
        stage = "operator_own_dialog"
        check(android.os.Build.VERSION.SDK_INT >= 29)
        val until = SystemClock.uptimeMillis() + 10_000
        var accepted = false
        while (!accepted && SystemClock.uptimeMillis() < until) {
            instrumentation.runOnMainSync {
                // WindowInspector returns only windows owned by this process, including our AlertDialog.
                for (root in android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    if (!root.isShown || !root.hasWindowFocus() ||
                        root.context.packageName != instrumentation.targetContext.packageName) continue
                    val candidates = allViews(root).filterIsInstance<android.widget.Button>().filter {
                        it.isShown && it.isEnabled && it.text.toString().equals(button, ignoreCase = true)
                    }
                    if (candidates.size == 1) accepted = candidates.single().performClick()
                }
            }
            if (!accepted) SystemClock.sleep(150)
        }
        check(accepted)
    }

    fun verify() {
        check(android.os.Build.VERSION.SDK_INT >= 26)
        stage = "phone_locked"
        check(instrumentation.targetContext.getSystemService(PowerManager::class.java).isInteractive &&
            !instrumentation.targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        stage = "launch"
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var keptAwake = false
        instrumentation.runOnMainSync {
            val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            keptAwake = activity.window.attributes.flags and flag != 0
            activity.window.addFlags(flag)
        }
        try { verifyScreens() }
        finally {
            if (!keptAwake) instrumentation.runOnMainSync {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun verifyScreens() {
        verifyShell()
        stage = "reels_navigation"
        awaitText("Open Reels & short videos", scrollSearch = true)
        tap("Open Reels & short videos")
        visitedRoutes.add("reels")
        awaitText("Your Reels")
        awaitText("Instagram")
        awaitText("Normal-feed posts: —", scrollSearch = true)
        capture("reels")
        awaitText("Facebook", scrollSearch = true)
        capture("facebook")
        awaitText("YouTube Shorts", scrollSearch = true)
        awaitText("TikTok", scrollSearch = true)
        awaitText("Observation", scrollSearch = true)
        capture("controls")
        awaitText("About your data", scrollSearch = true)
        stage = "privacy_navigation"
        tap("About your data")
        awaitText("Your choices come first.")
        visitedRoutes.add("privacy")
        capture("privacy")
        awaitText("Read the privacy policy", scrollSearch = true)
        capture("privacy-policy")
        stage = "back_navigation"
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        awaitText("Reels & short videos")
        instrumentation.runOnMainSync {
            allViews(activity.window.decorView).filterIsInstance<ScrollView>()
                .firstOrNull { it.isShown && it.getGlobalVisibleRect(Rect()) }?.scrollTo(0, 0)
        }
        awaitText("Your Reels")
        verifyCounterButton()
    }

    private fun scrollTop() = instrumentation.runOnMainSync {
        allViews(activity.window.decorView).filterIsInstance<ScrollView>()
            .firstOrNull { it.isShown && it.getGlobalVisibleRect(Rect()) }?.scrollTo(0, 0)
    }

    private fun verifyShell() {
        stage = "shell_navigation"
        fun visit(id: String, heading: String) {
            awaitText(heading); visitedRoutes.add(id); capture("shell-$id")
        }
        fun detail(button: String, heading: String, id: String, backHeading: String) {
            stage = "shell_${id}_button"
            awaitText(button, scrollSearch = true)
            stage = "shell_${id}_tap"
            tap(button)
            stage = "shell_${id}_visit"
            visit(id, heading)
            if (id == "counter") verifyDisplayPreferences()
            stage = "shell_${id}_back"
            instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            awaitText(backHeading, atTop = true)
        }
        visit("today", "Your day, at a glance.")
        for (label in listOf("Today", "History", "Patterns", "Protect", "Settings")) awaitText(label)
        tap("History"); visit("history", "A clearer timeline.")
        tap("Week"); awaitText("This week’s activity")
        tap("Month"); awaitText("This month’s activity")
        detail("Detailed activity diary", "Your activity, with context.", "diary", "A clearer timeline.")
        tap("Patterns"); visit("patterns", "Understand, without judging.")
        detail("How patterns work", "Evidence before interpretation.", "explanation", "Understand, without judging.")
        detail("App categories", "Your own way to organize.", "categories", "Understand, without judging.")
        tap("Protect"); visit("protect", "Make room for what matters.")
        detail("Focus sessions", "A pause, on your terms.", "focus", "Make room for what matters.")
        detail("Budgets & schedules", "Choose a boundary deliberately.", "rules", "Make room for what matters.")
        tap("Settings"); visit("settings", "Make it yours.")
        for ((button, heading, id) in listOf(
            Triple("Sources & permissions", "Know what is observing.", "sources"),
            Triple("Local storage", "Your history stays yours.", "storage"),
            Triple("Backup & recovery", "A copy you control.", "backup"),
            Triple("App appearance", "Comfortable to read.", "appearance"),
            Triple("Floating counter", "A glance, your way.", "counter"),
            Triple("Convenience features", "Extras, only if you want them.", "convenience"),
            Triple("About Attention Stats", "Attention Stats", "about")))
            detail(button, heading, id, "Make it yours.")
        awaitText("About Attention Stats", scrollSearch = true); tap("About Attention Stats")
        awaitText("Read the introduction", scrollSearch = true); tap("Read the introduction")
        visit("welcome", "Notice your patterns. Choose what comes next.")
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        awaitText("Open by design", scrollSearch = true)
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        tap("Today"); awaitText("Your day, at a glance.", atTop = true)
    }

    private fun verifyCounterButton() {
        stage = "counter_button"
        val actions = mutableListOf<String>()
        lateinit var view: CounterHudView
        val state = ReelsCoordinator.OverlaySnapshot(ReelApp.INSTAGRAM, 8, 21, 0,
            listOf(ReelsCoordinator.OverlayCategory(PageCategory.REEL, 5, 14),
                ReelsCoordinator.OverlayCategory(PageCategory.PHOTO, 1, 2),
                ReelsCoordinator.OverlayCategory(PageCategory.AD, 1, 3),
                ReelsCoordinator.OverlayCategory(PageCategory.UNCLASSIFIED, 1, 2)))
        instrumentation.runOnMainSync {
            view = CounterHudView(activity, { actions.add("dashboard") }, { actions.add("pause") }, { actions.add("hide") })
            view.update(state)
            (activity.window.decorView as FrameLayout).addView(view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = (48 * activity.resources.displayMetrics.density).toInt()
                })
            check(!view.expanded)
            check(allViews(view).filterIsInstance<TextView>().filter { it.isShown }.all { it.text.toString().matches(Regex("[0-9]+")) })
            checks++
        }
        try {
            capture("hud-collapsed")
            instrumentation.runOnMainSync {
                val header = view.getChildAt(0)
                check(header.contentDescription.toString().contains("This visit 8. All observed apps today 21."))
                check(header.height >= (48 * activity.resources.displayMetrics.density).toInt())
                checks++
                header.performClick()
                check(view.expanded); checks++
            }
            capture("hud-expanded")
            instrumentation.runOnMainSync {
                view.update(state.copy(visit = 9, today = 22))
                check(view.expanded && view.getChildAt(0).contentDescription.toString().contains("This visit 9. All observed apps today 22."))
                checks++
                val counter = allViews(view.getChildAt(0)).filterIsInstance<ScrollingCountView>().first()
                check((counter.currentView as TextView).text.toString() == "9" &&
                    (counter.nextView as TextView).text.toString() == "8" && counter.clipChildren && counter.measureAllChildren)
                checks++ // Retain the old page while the committed replacement enters the clipped viewport.
                fun verticalMotion(back: Boolean) {
                    fun translation(animation: android.view.animation.Animation): Float {
                        animation.initialize(counter.width, counter.height, view.width, view.height)
                        val start = SystemClock.uptimeMillis()
                        animation.startTime = start
                        val result = android.view.animation.Transformation()
                        animation.getTransformation(start + animation.duration / 2, result)
                        val matrix = FloatArray(9); result.matrix.getValues(matrix)
                        return matrix[android.graphics.Matrix.MTRANS_Y]
                    }
                    val incoming = translation(checkNotNull(counter.inAnimation))
                    val outgoing = translation(checkNotNull(counter.outAnimation))
                    val sign = if (back) -1 else 1
                    check(incoming * sign > counter.height / 4f && outgoing * sign < -counter.height / 4f)
                    checks++ // Genuine opposing full-height pages, rather than replacement plus a small wiggle.
                }
                verticalMotion(false)
                view.update(state.copy(visit = 10, today = 23, scrollBack = true))
                verticalMotion(true)
                for (label in listOf("Open dashboard", "Pause observation", "Hide counter; keep observation on"))
                    check(allViews(view).single { it.contentDescription == label }.performClick())
                check(actions == listOf("dashboard", "pause", "hide")); checks++
                view.getChildAt(0).performClick()
                check(!view.expanded); checks++
                var moved = false
                view.moveCounter = { dx, _ -> moved = dx > 0 }
                val header = view.getChildAt(0)
                val moveRight = checkNotNull(header.createAccessibilityNodeInfo().actionList
                    .firstOrNull { it.label == "Move counter right" })
                check(header.performAccessibilityAction(moveRight.id, null) && moved && !view.expanded)
                checks++
                var movedX = 0f; var movedY = 0f
                view.moveCounter = { dx, dy -> movedX += dx; movedY += dy }
                val downTime = SystemClock.uptimeMillis()
                for ((action, point) in listOf(Pair(MotionEvent.ACTION_DOWN, Pair(10f, 10f)),
                        Pair(MotionEvent.ACTION_MOVE, Pair(30f, 40f)), Pair(MotionEvent.ACTION_UP, Pair(50f, 80f)))) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
                    try { check(header.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
                check(movedX == 40f && movedY == 70f && !view.expanded); checks++
                view.update(state.copy(showIcons = false, allAppsToday = false))
                val compactHeader = view.getChildAt(0) as android.widget.LinearLayout
                check(compactHeader.getChildAt(0).visibility == View.GONE && compactHeader.getChildAt(3).visibility == View.GONE)
                check(compactHeader.contentDescription.toString().contains("Instagram today 21."))
                checks += 2
            }
            capture("hud-numbers")
        } finally { instrumentation.runOnMainSync { (activity.window.decorView as FrameLayout).removeView(view) } }
    }

    private fun verifyDisplayPreferences() {
        stage = "counter_preferences"
        fun snapshot(): org.json.JSONObject {
            val done = CountDownLatch(1)
            var value: org.json.JSONObject? = null
            ReelsCoordinator.get(instrumentation.targetContext).snapshot({
                value = org.json.JSONObject(it); done.countDown()
            }, { done.countDown() })
            check(done.await(15, TimeUnit.SECONDS))
            return checkNotNull(value)
        }
        val before = snapshot()
        val original = before.getJSONObject("counterAppearance")
        fun change(label: String, key: String, expected: Boolean) {
            awaitText(label, scrollSearch = true); tap(label)
            val until = SystemClock.uptimeMillis() + 15_000
            var current = snapshot()
            while (current.getJSONObject("counterAppearance").getBoolean(key) != expected &&
                SystemClock.uptimeMillis() < until) { SystemClock.sleep(100); current = snapshot() }
            check(current.getJSONObject("counterAppearance").getBoolean(key) == expected)
            check(current.getBoolean("overlayEnabled") == before.getBoolean("overlayEnabled"))
            val oldSources = before.getJSONArray("sources"); val nextSources = current.getJSONArray("sources")
            for (index in 0 until oldSources.length()) for (flag in listOf("enabled", "consentCurrent", "paused"))
                check(oldSources.getJSONObject(index).getBoolean(flag) == nextSources.getJSONObject(index).getBoolean(flag))
            checks++
        }
        try {
            change("Show session and Today icons", "showIcons", !original.getBoolean("showIcons"))
            change("Today across all apps", "allAppsToday", !original.getBoolean("allAppsToday"))
            change("Show session and Today icons", "showIcons", original.getBoolean("showIcons"))
            change("Today across all apps", "allAppsToday", original.getBoolean("allAppsToday"))
        } finally {
            val latest = snapshot()
            val actual = latest.getJSONObject("counterAppearance")
            if (actual.toString() != original.toString()) {
                val restored = CountDownLatch(1)
                var success = false
                ReelsCoordinator.get(instrumentation.targetContext).setCounterAppearance(
                    original.getBoolean("showIcons"), original.getBoolean("allAppsToday"), latest.getString("revision"),
                    { success = true; restored.countDown() }, { restored.countDown() })
                check(restored.await(15, TimeUnit.SECONDS) && success)
            }
        }
    }

    private fun allViews(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(allViews(view.getChildAt(it))) }
    }

    private fun find(label: String): View? {
        val matches = allViews(activity.window.decorView).filter {
            (it is TextView && it.text.toString() == label) || it.contentDescription?.toString() == label
        }
        return matches.firstOrNull { it is android.widget.CompoundButton && it.isShown && it.getGlobalVisibleRect(Rect()) }
            ?: matches.firstOrNull { it.isShown && it.getGlobalVisibleRect(Rect()) }
            ?: matches.firstOrNull { it.isShown }
    }

    private fun awaitText(label: String, scrollSearch: Boolean = false, atTop: Boolean = false) {
        val until = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < until) {
            if (atTop) scrollTop() // Retry after the returning native stack becomes visible.
            var found = false
            instrumentation.runOnMainSync {
                val view = find(label)
                found = view != null && view.isShown && view.getGlobalVisibleRect(Rect())
                if (!found && scrollSearch) {
                    allViews(activity.window.decorView).filterIsInstance<ScrollView>().firstOrNull { it.isShown }?.let {
                        it.scrollBy(0, maxOf(1, it.height / 3))
                    }
                }
            }
            if (found) { checks++; return }
            SystemClock.sleep(100)
        }
        capture("failure")
        error("Own app screen did not become ready")
    }

    private fun tap(label: String) {
        val bounds = Rect()
        instrumentation.runOnMainSync {
            val view = checkNotNull(find(label))
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250) // Let scrolling/layout settle before measuring the touch target.
        instrumentation.runOnMainSync {
            val view = checkNotNull(find(label))
            check(view.getGlobalVisibleRect(bounds) && !bounds.isEmpty)
        }
        val origin = IntArray(2)
        instrumentation.runOnMainSync {
            check(activity.hasWindowFocus())
            activity.window.decorView.getLocationOnScreen(origin)
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX() - origin[0], bounds.exactCenterY() - origin[1], 0)
            // Exercise our real view hierarchy without system-window input targeting.
            // OS overlay/touch delivery is qualified separately in live operator trials.
            try { instrumentation.runOnMainSync {
                check(activity.hasWindowFocus())
                check(activity.window.decorView.dispatchTouchEvent(event))
            } }
            catch (failure: IllegalArgumentException) {
                capture("tap-failure")
                instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putInt("touch_x", bounds.centerX()); putInt("touch_y", bounds.centerY())
                    putInt("own_window_focus", if (activity.hasWindowFocus()) 1 else 0)
                    putInt("screen_height", activity.resources.displayMetrics.heightPixels)
                })
                throw failure
            } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(80)
        }
        checks++
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(150) // Present the scrolled layout before PixelCopy.
        val done = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, checkNotNull(bitmap), {
                result = it; done.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        check(done.await(10, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS)
        val image = checkNotNull(bitmap)
        try {
            File(instrumentation.targetContext.noBackupFilesDir, "reels-ui-$name.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { image.recycle() }
        checks++
    }
}
