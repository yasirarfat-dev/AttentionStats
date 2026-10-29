package com.attentionstats.detectors

import kotlin.math.abs

/** Layout continuity for one Home feed. No content, timer counts, durable node IDs or swipe counts. */
internal class InlineEncounterTracker(private val window: Int, private val epoch: Long, freshEntry: Boolean = false) {
    data class Result(val page: Long?, val freshEntry: Boolean)
    private data class Tracked(val item: InlineAnchor, val motion: Long, val video: Boolean)
    private var previous: Tracked? = null
    private var ordinal = -1L
    private var firstVideo = freshEntry

    fun matches(window: Int, epoch: Long): Boolean = this.window == window && this.epoch == epoch

    fun sample(selection: InlineSelection, motion: Long, viewport: LayoutBounds): Result {
        val item = selection.item ?: run {
            previous?.let {
                val displacement = motion - it.motion
                if (it.item.bounds.bottom - displacement <= viewport.top || it.item.bounds.top - displacement >= viewport.bottom)
                    previous = null
            }
            return Result(null, false)
        }
        val last = previous
        val displacement = last?.let { motion - it.motion } ?: 0L
        val tolerance = maxOf(8, item.bounds.width / 32)
        val continuousEdge = last?.let {
            abs(item.bounds.top.toLong() + motion - it.item.bounds.top - it.motion) <= tolerance ||
                abs(item.bounds.bottom.toLong() + motion - it.item.bounds.bottom - it.motion) <= tolerance
        } ?: false
        val same = last != null && (continuousEdge ||
            (last.item.key == item.key && abs(displacement) <= tolerance &&
                abs(last.item.bounds.height - item.bounds.height) <= tolerance * 2))
        if (!same) ordinal = Math.addExact(ordinal, 1)
        val video = selection.indicator != null || (same && !selection.photo && last.video)
        previous = Tracked(item, motion, video)
        if (!video) return Result(null, false)
        val fresh = firstVideo
        firstVideo = false
        return Result(ordinal, fresh)
    }
}
