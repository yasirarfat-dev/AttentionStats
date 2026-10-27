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
import com.attentionstats.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads and captures only our Activity window. Never queries other apps or grants consent. */
internal class ReelsUiProbe(private val instrumentation: Instrumentation) {
    var checks = 0
        private set
    var stage = "launch"
        private set
    private lateinit var activity: MainActivity

    fun verify() {
        check(android.os.Build.VERSION.SDK_INT >= 26)
        stage = "phone_locked"
        check(instrumentation.targetContext.getSystemService(PowerManager::class.java).isInteractive &&
            !instrumentation.targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        stage = "launch"
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        stage = "reels_navigation"
        awaitText("Your Reels")
        awaitText("Instagram")
        capture("reels")
        awaitText("Facebook", scrollSearch = true)
        capture("facebook")
        awaitText("Observation", scrollSearch = true)
        capture("controls")
        awaitText("About your data", scrollSearch = true)
        stage = "privacy_navigation"
        tap("About your data")
        awaitText("Your choices come first.")
        capture("privacy")
        stage = "back_navigation"
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        awaitText("Attention Stats")
        instrumentation.runOnMainSync {
            allViews(activity.window.decorView).filterIsInstance<ScrollView>()
                .firstOrNull { it.isShown && it.getGlobalVisibleRect(Rect()) }?.scrollTo(0, 0)
        }
        awaitText("Your Reels")
    }

    private fun allViews(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(allViews(view.getChildAt(it))) }
    }

    private fun find(label: String): View? = allViews(activity.window.decorView).firstOrNull {
        (it is TextView && it.text.toString() == label) || it.contentDescription?.toString() == label
    }

    private fun awaitText(label: String, scrollSearch: Boolean = false) {
        val until = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < until) {
            var found = false
            instrumentation.runOnMainSync {
                val view = find(label)
                found = view != null && view.isShown && view.getGlobalVisibleRect(Rect())
                if (!found && scrollSearch) {
                    allViews(activity.window.decorView).filterIsInstance<ScrollView>().firstOrNull()?.let {
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
            check(view.getGlobalVisibleRect(bounds) && !bounds.isEmpty)
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
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
