package org.attentionstats.reels

fun main() {
    var checks = 0
    fun expect(value: Boolean) { check(value); checks++ }
    fun signal(time: Long, page: Long? = 0, surface: Surface = Surface.REELS, window: Int = 1) =
        CounterSignal(time, surface, page, window)
    val counter = CounterReducer(false)
    expect(counter.observe(signal(0)) == null && !counter.visible)
    expect(counter.observe(signal(499)) == null && !counter.visible)
    val first = checkNotNull(counter.observe(signal(500)))
    expect(first.page == 0L && counter.visible)
    expect(counter.observe(signal(501)) == null) // Pending writes cannot produce duplicate proposals.
    counter.acknowledge(first)
    expect(counter.observe(signal(50_000)) == null) // Redraw much later still isn't another item.
    expect(counter.observe(signal(50_001, 8)) == null)
    expect(counter.observe(signal(50_500, 8)) == null)
    val skipped = checkNotNull(counter.observe(signal(50_501, 8)))
    expect(skipped.sequence == 2L) // Never extrapolate seven unseen pages.
    expect(!skipped.scrollBack)
    counter.acknowledge(skipped)
    expect(counter.observe(signal(50_502, 0)) == null)
    val returned = checkNotNull(counter.observe(signal(51_002, 0)))
    expect(returned.sequence == 3L)
    expect(returned.scrollBack) // Presentation direction does not turn a revisit into a unique item.
    counter.acknowledge(returned)
    expect(counter.observe(signal(51_003, surface = Surface.EXCLUDED)) == null && !counter.visible)
    expect(counter.observe(signal(51_004)) == null)
    expect(counter.observe(signal(51_504)) == null) // Returning from comments to same item dedupes.
    expect(counter.observe(signal(51_505, surface = Surface.OUTSIDE)) == null)
    expect(counter.observe(signal(51_506)) == null)
    expect(counter.observe(signal(52_006)) == null) // Uncertain re-entry suppresses initial item.
    expect(counter.observe(signal(52_007, 1)) == null)
    val next = checkNotNull(counter.observe(signal(52_507, 1)))
    counter.acknowledge(next)
    expect(next.sequence == 4L)
    expect(counter.observe(signal(52_508, null)) == null && !counter.visible)
    expect(counter.observe(signal(52_509, 2, Surface.UNKNOWN)) == null)
    expect(counter.observe(signal(52_510, 2)) == null)
    expect(counter.observe(signal(53_010, 2)) == null) // Unknown breaks continuity.
    expect(counter.observe(signal(53_011, 3, window = 2)) == null)
    expect(counter.observe(signal(53_511, 3, window = 2)) == null) // New window also uncertain.
    expect(counter.observe(signal(1, 4, window = 2)) == null)
    expect(counter.observe(signal(501, 4, window = 2)) == null) // Monotonic regression invalidates context.
    var badAcknowledgement = false
    try { counter.acknowledge(first) } catch (_: IllegalStateException) { badAcknowledgement = true }
    expect(badAcknowledgement)
    val other = CounterReducer(false)
    other.observe(signal(0, 99))
    expect(checkNotNull(other.observe(signal(500, 99))).sequence == 1L)
    expect(ReelApp.byKey("instagram") == ReelApp.INSTAGRAM)
    expect(ReelApp.byPackage("com.facebook.katana") == ReelApp.FACEBOOK)
    expect(ReelApp.byKey("unsupported") == null)
    val classified = CounterReducer(false)
    classified.observe(CounterSignal(0, Surface.REELS, 0, 1, PageCategory.UNCLASSIFIED))
    expect(classified.observe(CounterSignal(400, Surface.REELS, 0, 1, PageCategory.AD)) == null)
    expect(classified.observe(CounterSignal(500, Surface.REELS, 0, 1, PageCategory.AD)) == null)
    val ad = checkNotNull(classified.observe(CounterSignal(900, Surface.REELS, 0, 1, PageCategory.AD)))
    expect(ad.category == PageCategory.AD)
    classified.acknowledge(ad)
    classified.observe(CounterSignal(901, Surface.REELS, 0, 1, PageCategory.REEL))
    expect(classified.observe(CounterSignal(1401, Surface.REELS, 0, 1, PageCategory.REEL)) == null)
    expect(PageCategory.entries.map { it.key }.toSet().size == 5)
    println("PASS: $checks synthetic Reels reducer checks")
}
