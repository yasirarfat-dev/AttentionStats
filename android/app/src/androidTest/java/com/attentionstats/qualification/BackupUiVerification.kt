package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.attentionstats.backup.BackupActivity
import com.attentionstats.backup.SystemBackupState

/** Inspect the attached backup page without changing the person's saved backup choice. */
@Suppress("DEPRECATION")
internal object BackupUiVerification {
    fun run(instrumentation: Instrumentation): Int {
        val context = instrumentation.targetContext
        val before = SystemBackupState.enabled(context)
        val activity = instrumentation.startActivitySync(
            Intent(context, BackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            var checks = 0
            instrumentation.runOnMainSync {
                val views = mutableListOf<View>()
                fun visit(view: View) {
                    views.add(view)
                    if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
                }
                visit(activity.window.decorView)
                fun expect(value: Boolean) { check(value) { "backup UI check ${checks + 1}" }; checks++ }
                expect(activity.window.decorView.isAttachedToWindow)
                val toggle = views.filterIsInstance<Switch>().single()
                expect(toggle.isChecked == before && toggle.isEnabled)
                val text = views.filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
                expect(text.contains("starts enabled unless you previously turned it off"))
                expect(text.contains("does not sync live history between devices"))
                expect(SystemBackupState.enabled(context) == before)
            }
            return checks
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
