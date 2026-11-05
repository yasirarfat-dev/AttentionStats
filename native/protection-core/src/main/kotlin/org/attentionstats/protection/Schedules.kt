package org.attentionstats.protection

import java.time.ZonedDateTime

data class AppGroup(val id: String, val name: String, val packages: Set<String>)

enum class BlockScope(val key: String) { APP("app"), SHORT_VIDEOS("short_videos") }

/** Days belong to the start of a window; Monday is bit 0, Sunday bit 6. */
data class BlockWindow(val id: String, val groupId: String, val days: Int,
    val startMinute: Int, val endMinute: Int, val enabled: Boolean = false,
    val scope: BlockScope = BlockScope.APP, val allDay: Boolean = false) {
    fun active(at: ZonedDateTime): Boolean {
        if (!enabled) return false
        val minute = at.hour * 60 + at.minute
        val day = at.dayOfWeek.value - 1
        fun selected(index: Int) = days and (1 shl index) != 0
        if (allDay) return selected(day)
        return if (startMinute < endMinute) selected(day) && minute >= startMinute && minute < endMinute
        else (selected(day) && minute >= startMinute) || (selected((day + 6) % 7) && minute < endMinute)
    }
}

data class SchedulePlan(val groups: List<AppGroup> = emptyList(), val windows: List<BlockWindow> = emptyList()) {
    fun validate(excluded: Set<String>) {
        require(groups.size <= 30 && windows.size <= 100)
        val id = Regex("[A-Za-z0-9-]{1,64}")
        val pkg = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        require(groups.map { it.id }.toSet().size == groups.size && windows.map { it.id }.toSet().size == windows.size)
        groups.forEach { group ->
            require(id.matches(group.id) && group.name.isNotBlank() && group.name.length <= 60)
            require(group.packages.size in 1..100)
            require(group.packages.all { it.length <= 256 && pkg.matches(it) && it !in excluded })
        }
        windows.forEach { window ->
            require(id.matches(window.id) && groups.any { it.id == window.groupId })
            require(window.days in 1..127 && window.startMinute in 0..1439 && window.endMinute in 0..1439)
            require(if (window.allDay) window.startMinute == 0 && window.endMinute == 0 else window.startMinute != window.endMinute)
        }
    }

    fun matching(packageName: String, at: ZonedDateTime, excluded: Set<String>, scope: BlockScope = BlockScope.APP): List<BlockWindow> {
        if (packageName in excluded) return emptyList()
        val memberships = groups.filter { packageName in it.packages }.map { it.id }.toSet()
        return windows.filter { it.groupId in memberships && it.scope == scope && it.active(at) }.sortedBy { it.id }
    }
    fun targets(): Set<String> {
        val activeGroups = windows.filter { it.enabled }.map { it.groupId }.toSet()
        return groups.filter { it.id in activeGroups }.flatMap { it.packages }.toSet()
    }
}
