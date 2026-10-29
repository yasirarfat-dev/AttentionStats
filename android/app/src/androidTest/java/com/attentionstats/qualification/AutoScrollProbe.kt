package com.attentionstats.qualification

import com.attentionstats.autoscroll.domain.CompletionGate
import com.attentionstats.autoscroll.PlaybackPositionResolver
import com.attentionstats.autoscroll.PlaybackTimeParser
import com.attentionstats.detectors.LayoutBounds
import com.attentionstats.detectors.NumericRange
import org.attentionstats.reels.ReelApp

/** Artificial decision safety only. Does not qualify a target app or dispatch a gesture. */
internal object AutoScrollProbe {
    fun verify(): Int {
        var checks = 0
        val key = CompletionGate.Context("synthetic", 2, 10, 4)
        val gate = CompletionGate()
        fun expect(value: Boolean) { check(value); checks++ }
        fun run(keys: (Int) -> CompletionGate.Context = { key },
            values: List<Double> = (0..20).map { it / 20.0 },
            times: (Int) -> Long = { it * 500L }): List<Boolean> {
            gate.reset()
            return values.mapIndexed { index, position -> gate.sample(keys(index), times(index), position, true) }
        }
        expect(run().none { it })
        expect(gate.sample(key, 10_500, 0.0, true))
        expect(!gate.sample(key, 11_000, 0.0, true))
        expect(!gate.sample(key, 11_500, 1.0, true))
        expect(!gate.sample(key, 12_000, 0.0, true))
        expect(run(values = listOf(0.0, 1.0, 0.0)).none { it })
        expect(run(values = List(30) { 0.99 } + 0.0).none { it })
        expect(run(values = List(30) { 0.0 }).none { it })
        expect(run(values = (10..20).map { it / 20.0 } + 0.0).none { it })
        expect(run(values = (0..18).map { it / 20.0 } + 0.0).none { it })
        expect(run(keys = { if (it < 20) key else key.copy(page = 11) },
            values = (0..19).map { it / 19.0 } + 0.0).none { it })
        expect(run(keys = { if (it < 20) key else key.copy(window = 3) },
            values = (0..19).map { it / 19.0 } + 0.0).none { it })
        expect(run(keys = { if (it < 20) key else key.copy(epoch = 5) },
            values = (0..19).map { it / 19.0 } + 0.0).none { it })
        expect(run(times = { it * 2_000L }, values = (0..20).map { it / 20.0 } + 0.0).none { it })
        expect(run(times = { 0L }, values = (0..20).map { it / 20.0 } + 0.0).none { it })
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            run(); expect(!gate.sample(key, 10_500, invalid, true))
            expect(!gate.sample(key, 11_000, 0.0, true))
        }
        run(); expect(!gate.sample(key, 10_500, 0.0, false))
        expect(!gate.sample(key, 11_000, 0.0, true))
        run(); gate.reset(); expect(!gate.sample(key, 10_500, 0.0, true))
        val viewport = LayoutBounds(0, 0, 720, 1600)
        val range = NumericRange("scrubber", "android.widget.SeekBar", 0.0, 20_000.0, 10_000.0,
            LayoutBounds(0, 1400, 720, 1420))
        fun resolve(ranges: List<NumericRange>, complete: Boolean = true, app: ReelApp = ReelApp.INSTAGRAM) =
            PlaybackPositionResolver.position(app, ranges, viewport, complete)
        expect(resolve(listOf(range)) == 0.5)
        expect(resolve(listOf(range), app = ReelApp.FACEBOOK) == 0.5)
        expect(resolve(listOf(range), app = ReelApp.YOUTUBE) == null)
        expect(resolve(listOf(range), app = ReelApp.TIKTOK) == null)
        val tiktok = range.copy(id = "vt6", max = 10_000.0, current = 3_000.0)
        expect(resolve(listOf(tiktok), app = ReelApp.TIKTOK) == 0.3)
        expect(resolve(listOf(tiktok.copy(max = 20_000.0)), app = ReelApp.TIKTOK) == null)
        expect(resolve(listOf(tiktok.copy(id = "other")), app = ReelApp.TIKTOK) == null)
        expect(resolve(listOf(tiktok, tiktok), app = ReelApp.TIKTOK) == null)
        expect(resolve(listOf(range), complete = false) == null)
        expect(resolve(listOf(range, range)) == null)
        expect(resolve(listOf(range) + List(7) { range.copy(id = "unrelated") }) == null)
        expect(resolve(listOf(range.copy(id = "other"))) == null)
        expect(resolve(listOf(range.copy(max = Double.POSITIVE_INFINITY))) == null)
        expect(resolve(listOf(range.copy(bounds = LayoutBounds(0, 0, 720, 20)))) == null)
        expect(resolve(listOf(range.copy(bounds = LayoutBounds(0, 1400, 200, 1420)))) == null)
        expect(PlaybackTimeParser.parse("0:02 / 0:30") == Pair(2_000L, 30_000L))
        expect(PlaybackTimeParser.parse("Video progress, 00:02 of 01:30") == Pair(2_000L, 90_000L))
        expect(PlaybackTimeParser.elapsed("00:12") == 12_000L)
        expect(PlaybackTimeParser.elapsed("synthetic caption 00:12") == null)
        for (text in listOf("synthetic caption 0:02 / 0:30", "0:99 / 1:00", "1:01 / 1:00", "0:00 / 0:00", "private content"))
            expect(PlaybackTimeParser.parse(text) == null)
        val visibility = com.attentionstats.reels.VisitHudVisibility()
        visibility.hide(ReelApp.INSTAGRAM)
        expect(visibility.awaitingDeparture())
        expect(visibility.hiddenFor(ReelApp.INSTAGRAM) && !visibility.hiddenFor(ReelApp.FACEBOOK))
        visibility.foreground(null); expect(visibility.hiddenFor(ReelApp.INSTAGRAM))
        visibility.foreground("com.android.systemui"); expect(visibility.hiddenFor(ReelApp.INSTAGRAM))
        visibility.foreground(ReelApp.INSTAGRAM.packageName); expect(visibility.hiddenFor(ReelApp.INSTAGRAM))
        visibility.foreground("synthetic.launcher"); expect(!visibility.hiddenFor(ReelApp.INSTAGRAM))
        expect(!visibility.awaitingDeparture())
        visibility.hide(ReelApp.INSTAGRAM); visibility.foreground(ReelApp.FACEBOOK.packageName)
        expect(!visibility.hiddenFor(ReelApp.INSTAGRAM))
        expect(!com.attentionstats.reels.VisitHudVisibility().hiddenFor(ReelApp.INSTAGRAM))
        return checks
    }
}
