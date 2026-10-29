package com.attentionstats.reels

import org.attentionstats.reels.ReelApp

/** Display-only visit state. Never changes observation, native preferences or retained counts. */
internal class VisitHudVisibility {
    @Volatile private var hidden: ReelApp? = null
    fun hide(app: ReelApp) { hidden = app }
    fun hiddenFor(app: ReelApp): Boolean = hidden == app
    fun awaitingDeparture(): Boolean = hidden != null
    fun foreground(packageName: String?) {
        val active = hidden ?: return
        // Missing roots and transient system UI do not prove departure from the app.
        if (packageName != null && packageName != "com.android.systemui" && packageName != active.packageName) hidden = null
    }
}
