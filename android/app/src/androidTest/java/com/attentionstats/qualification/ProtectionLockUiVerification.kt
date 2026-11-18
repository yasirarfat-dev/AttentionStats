package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.attentionstats.protection.ProtectionCredentials
import com.attentionstats.protection.ProtectionLockActivity

/** Reviews the real native off/setup/cancel page without choosing real parent credentials. */
internal object ProtectionLockUiVerification {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { try { action() } catch (problem: Throwable) { failure = problem } }
            failure?.let { throw it }
        }
        val original = ProtectionCredentials(context).state()
        check(!original.enabled) { "Existing configured protection preserved; no setup trial" }
        val activity = instrumentation.startActivitySync(Intent(context, ProtectionLockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ProtectionLockActivity
        var checks = 0
        fun expect(value: Boolean) { check(value) { "protection lock UI check ${checks + 1}" }; checks++ }
        fun views(): List<View> {
            val result = mutableListOf<View>()
            fun visit(view: View) { result.add(view); if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) } }
            visit(activity.window.decorView); return result
        }
        fun waitText(value: String) {
            val until = SystemClock.elapsedRealtime() + 10_000
            var found = false
            while (!found && SystemClock.elapsedRealtime() < until) {
                main { found = views().filterIsInstance<TextView>().any { it.text.toString().equals(value, true) } }
                if (!found) SystemClock.sleep(100)
            }
            expect(found)
        }
        fun click(value: String) = main {
            val control = views().filterIsInstance<TextView>().single { it.text.toString().equals(value, true) && it.isClickable }
            expect(control.performClick())
        }
        fun capture(name: String) = main {
            // Draw only our attached page with all secret controls empty, in one main-thread turn.
            // Keep FLAG_SECURE enabled; this is an owned-view render, not a system screenshot.
            check(views().filterIsInstance<EditText>().all { it.text.isEmpty() })
            val root = activity.window.decorView
            check(root.width > 0 && root.height > 0 && root.width.toLong() * root.height <= 4_000_000)
            val output = java.io.File(context.cacheDir, "protection-lock-ui-$name.png")
            check(!output.exists())
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                root.draw(android.graphics.Canvas(bitmap))
                output.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
        try {
            waitText("Protection Lock is off")
            instrumentation.waitForIdleSync()
            capture("off")
            main {
                expect(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                expect(views().filterIsInstance<EditText>().isEmpty())
            }
            click("Set up parental PIN")
            waitText("Enable Protection Lock")
            instrumentation.waitForIdleSync()
            main {
                val fields = views().filterIsInstance<EditText>()
                expect(fields.size == 8)
                expect(fields.all { it.text.isEmpty() && it.transformationMethod is android.text.method.PasswordTransformationMethod })
                expect(fields.map { it.contentDescription.toString() }.distinct().size == 8)
                expect(fields.all { it.height >= (48 * context.resources.displayMetrics.density).toInt() })
            }
            capture("setup-top")
            main {
                val scroll = views().filterIsInstance<android.widget.ScrollView>().single()
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            instrumentation.waitForIdleSync()
            capture("setup-bottom")
            main {
                val cancel = views().filterIsInstance<TextView>().single { it.isClickable && it.text.toString().equals("Cancel", true) }
                val bounds = android.graphics.Rect()
                expect(cancel.getGlobalVisibleRect(bounds) && bounds.height() >= (48 * context.resources.displayMetrics.density).toInt())
            }
            click("Cancel"); waitText("Protection Lock is off")
            expect(ProtectionCredentials(context).state() == original)
            return "PASS: $checks actual native protection lock UI checks; secure page, blank bounded secret controls, cancel; no activation"
        } finally { main { activity.finish() } }
    }
}
