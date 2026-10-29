package com.attentionstats.qualification

import android.app.Instrumentation
import android.app.KeyguardManager
import android.app.UiAutomation
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.PowerManager
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.reels.ReelApp
import org.json.JSONArray
import org.json.JSONObject

/** Shell-authorized feasibility probe only. No notification grant or media control actions.
 * Reads only duration and numeric playback state for the selected foreground source.
 */
internal object MediaPlaybackProbe {
    fun inspect(instrumentation: Instrumentation, automation: UiAutomation): JSONObject {
        fun withheld(reason: String) = JSONObject().put("withheld", reason)
        val context = instrumentation.targetContext
        val coordinator = ReelsCoordinator.get(context)
        if (!context.getSystemService(PowerManager::class.java).isInteractive ||
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return withheld("locked")
        val root = automation.rootInActiveWindow ?: return withheld("root_unavailable")
        try {
            val app = ReelApp.byPackage(root.packageName?.toString() ?: "") ?: return withheld("outside")
            if (!coordinator.grantEnabled() || !coordinator.allowed(app)) return withheld("source_off")
            val sessions = context.getSystemService(MediaSessionManager::class.java).getActiveSessions(null)
                .filter { it.packageName == app.packageName }
            val values = JSONArray()
            if (sessions.size <= 4) for (session in sessions) {
                val metadata = session.metadata
                val state = session.playbackState
                values.put(JSONObject().put("durationMs", metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0)
                    .put("state", state?.state ?: -1).put("positionMs", state?.position ?: -1)
                    .put("speed", state?.playbackSpeed?.toDouble() ?: 0.0)
                    .put("updatedAtMs", state?.lastPositionUpdateTime ?: 0))
            }
            return JSONObject().put("app", app.key).put("bounded", sessions.size <= 4).put("sessions", values)
        } finally {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) root.recycle()
        }
    }
}
