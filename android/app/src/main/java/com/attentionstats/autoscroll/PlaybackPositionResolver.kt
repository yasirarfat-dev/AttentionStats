package com.attentionstats.autoscroll

import com.attentionstats.detectors.LayoutBounds
import com.attentionstats.detectors.NumericRange
import org.attentionstats.reels.ReelApp

/** App-specific numeric player candidates; unrelated sliders never become playback. */
internal object PlaybackPositionResolver {
    fun position(app: ReelApp, ranges: List<NumericRange>, viewport: LayoutBounds, complete: Boolean): Double? {
        if (!complete || ranges.size >= 8 || viewport.width <= 0 || viewport.height <= 0) return null
        val selected = ranges.filter { range ->
            range.role == "android.widget.SeekBar" &&
                (app == ReelApp.FACEBOOK || (app == ReelApp.INSTAGRAM && range.id == "scrubber") ||
                    (app == ReelApp.TIKTOK && range.id == "vt6" && range.max == 10_000.0)) &&
                range.bounds.width >= viewport.width * 0.8 &&
                range.bounds.top >= viewport.top + viewport.height * 0.7 &&
                range.bounds.bottom <= viewport.bottom && range.bounds.height > 0 &&
                range.min.isFinite() && range.max.isFinite() && range.current.isFinite() &&
                range.min == 0.0 && range.max > range.min && range.current in range.min..range.max
        }
        return selected.singleOrNull()?.let { it.current / it.max }
    }
}
