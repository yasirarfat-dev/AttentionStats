package org.attentionstats.protection

/** Frozen targets; elapsed time owns expiry within a boot, UTC is a bounded reboot fallback. */
data class FocusSession(val id: String, val packages: Set<String>, val startedMs: Long,
    val deadlineMs: Long, val elapsedStart: Long, val elapsedDeadline: Long, val boot: Int) {
    fun validate(excluded: Set<String>) {
        SchedulePlan(groups = listOf(AppGroup(id, "Focus", packages))).validate(excluded)
        require(startedMs > 0 && elapsedStart >= 0 && boot >= 0)
        require(deadlineMs > startedMs)
        val duration = deadlineMs - startedMs
        require(duration in 60_000L..180 * 60_000L && duration % 60_000L == 0L)
        require(elapsedDeadline > elapsedStart && elapsedDeadline - elapsedStart == duration)
    }

    fun remaining(wall: Long, elapsed: Long, currentBoot: Int): Long =
        if (currentBoot == boot) {
            if (elapsed < elapsedStart) 0 else (elapsedDeadline - elapsed).coerceIn(0, deadlineMs - startedMs)
        } else {
            // Unknown boot/rollback cannot extend a session or silently activate it.
            if (currentBoot < 0 || currentBoot <= boot || wall < startedMs) 0
            else (deadlineMs - wall).coerceIn(0, deadlineMs - startedMs)
        }
}
