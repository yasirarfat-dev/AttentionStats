package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.reels.ReelsCoordinator

/** Explicit operator recovery of this app's previously enabled service through system UI only. */
internal object ServiceRestoreProbe {
    @Suppress("DEPRECATION") // Boolean checked state remains required on supported API 24–36 phones.
    fun restore(instrumentation: Instrumentation): Boolean {
        val context = instrumentation.targetContext
        val ownLabel = context.getString(com.attentionstats.R.string.reels_accessibility_label)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val until = SystemClock.elapsedRealtime() + 55_000
        var openedOwn = false
        var toggled = false
        while (SystemClock.elapsedRealtime() < until) {
            if (ReelsCoordinator.get(context).grantEnabled()) return true
            val root = automation.rootInActiveWindow
            if (root == null) { SystemClock.sleep(300); continue }
            val owned = mutableListOf<AccessibilityNodeInfo>()
            try {
                val owner = root.packageName?.toString()
                if (owner !in setOf("com.android.settings", "com.google.android.permissioncontroller", "com.android.permissioncontroller", "android")) {
                    SystemClock.sleep(300); continue
                }
                val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
                val nodes = mutableListOf<AccessibilityNodeInfo>()
                while (queue.isNotEmpty() && nodes.size < 160) {
                    val node = queue.removeFirst(); nodes.add(node)
                    if (!node.isVisibleToUser || node.isEditable || node.isPassword) continue
                    for (index in 0 until minOf(node.childCount, 160 - nodes.size - queue.size))
                        node.getChild(index)?.let { owned.add(it); queue.add(it) }
                }
                fun click(node: AccessibilityNodeInfo): Boolean {
                    var candidate: AccessibilityNodeInfo? = node
                    repeat(4) {
                        val current = candidate ?: return false
                        if (current.isClickable && current.isEnabled)
                            return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        candidate = current.parent?.also { owned.add(it) }
                    }
                    return false
                }
                val ownMatches = nodes.count { node -> node.text?.let { it.length <= 256 && it.contains(ownLabel) } == true }
                val containsOwn = ownMatches > 0
                if (containsOwn && openedOwn && toggled) {
                    val allow = nodes.singleOrNull { it.className?.toString() == "android.widget.Button" &&
                        it.text?.toString() in setOf("Allow", "ALLOW", "Use service", "OK") }
                    if (allow != null && click(allow)) { SystemClock.sleep(500); continue }
                }
                if (openedOwn && ownMatches >= 2) {
                    val switches = nodes.filter { it.isCheckable && it.isEnabled && !it.isChecked &&
                        it.className?.toString() in setOf("android.widget.Switch", "android.widget.SwitchButton", "androidx.appcompat.widget.SwitchCompat") }
                    val first = switches.minByOrNull { Rect().also { bounds -> it.getBoundsInScreen(bounds) }.top }
                    if (first != null && !toggled && click(first)) { toggled = true; SystemClock.sleep(500); continue }
                }
                val row = nodes.firstOrNull { it.text?.toString() == ownLabel }
                if (!openedOwn && row != null && click(row)) { openedOwn = true; SystemClock.sleep(500); continue }
                val installed = nodes.firstOrNull { it.text?.toString() in setOf("Installed apps", "Downloaded apps", "Installed services") }
                if (!openedOwn && installed != null && click(installed)) { SystemClock.sleep(500); continue }
            } finally {
                owned.forEach { recycle(it) }; recycle(root)
            }
            SystemClock.sleep(300)
        }
        return ReelsCoordinator.get(context).grantEnabled()
    }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { if (Build.VERSION.SDK_INT < 33) node.recycle() }
}
