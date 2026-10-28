package com.attentionstats.qualification

import android.view.accessibility.AccessibilityNodeInfo
import com.attentionstats.detectors.accessibility.TransientContentIdentity
import org.attentionstats.reels.ReelApp

/** Artificial values only; exposes ordinals/assertion totals, never comparison tokens. */
internal object ContentIdentityProbe {
    @Suppress("DEPRECATION")
    fun verify(): Int {
        var checks = 0
        fun expect(value: Boolean) { check(value); checks++ }
        val matcher = TransientContentIdentity()
        fun node(value: String): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain().apply {
            packageName = ReelApp.TIKTOK.packageName
            isVisibleToUser = true; text = value
        }
        fun page(caption: String, reverse: Boolean = false): Long? {
            val collector = matcher.begin(ReelApp.TIKTOK, 1, 1)
            val nodes = listOf("title" to node("Synthetic author"), "desc" to node(caption))
            try { (if (reverse) nodes.reversed() else nodes).forEach { collector.add(it.first, it.second) } }
            finally { nodes.forEach { it.second.recycle() } }
            return collector.finish(true)
        }
        expect(page("Synthetic A") == 0L)
        expect(page("Synthetic A") == 0L)
        expect(page("Synthetic A", true) == 0L)
        expect(page("Synthetic B") == 1L)
        expect(page("Synthetic A") == 2L)
        val stale = matcher.begin(ReelApp.TIKTOK, 1, 1)
        matcher.begin(ReelApp.TIKTOK, 1, 2).finish(false)
        val artificial = node("Synthetic C")
        try {
            stale.add("desc", artificial); expect(stale.finish(true) == null)
            fun rejected(change: (AccessibilityNodeInfo) -> Unit, id: String = "desc") {
                val collector = matcher.begin(ReelApp.TIKTOK, 1, 2)
                val candidate = node("Synthetic C")
                try { change(candidate); collector.add(id, candidate) } finally { candidate.recycle() }
                expect(collector.finish(true) == null)
            }
            rejected({ it.isEditable = true })
            rejected({ it.isPassword = true })
            rejected({ it.isVisibleToUser = false })
            rejected({ it.packageName = "org.example.synthetic" })
            rejected({ it.text = "x".repeat(4097) })
            rejected({ it.text = " " })
            rejected({}, "other")
            rejected({}, "title")
            val duplicate = matcher.begin(ReelApp.TIKTOK, 1, 2)
            duplicate.add("desc", artificial); duplicate.add("desc", artificial)
            expect(duplicate.finish(true) == null)
            val reset = matcher.begin(ReelApp.TIKTOK, 1, 2)
            reset.add("desc", artificial); matcher.reset()
            expect(reset.finish(true) == null)
            val excluded = matcher.begin(ReelApp.TIKTOK, 1, 2)
            excluded.add("desc", artificial); expect(excluded.finish(false) == null)
            var rejectedApp = false
            try { matcher.begin(ReelApp.INSTAGRAM, 1, 2) } catch (_: IllegalStateException) { rejectedApp = true }
            expect(rejectedApp)
        } finally { artificial.recycle(); matcher.reset() }
        expect(page("Synthetic A") == 0L)
        return checks
    }
}
