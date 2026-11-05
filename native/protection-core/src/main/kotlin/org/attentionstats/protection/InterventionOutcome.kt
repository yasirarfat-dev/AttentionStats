package org.attentionstats.protection

/** Joins a user choice with its asynchronous storage ID in either order, at most once. */
class InterventionOutcome(private val resolved: (String, String) -> Unit) {
    private var recordId: String? = null
    private var choice: String? = null
    private var delivered = false

    fun recorded(id: String) {
        if (recordId != null) return
        recordId = id
        deliver()
    }
    fun choose(value: String) {
        if (choice != null) return
        choice = value
        deliver()
    }
    private fun deliver() {
        val id = recordId ?: return
        val value = choice ?: return
        if (delivered) return
        delivered = true
        resolved(id, value)
    }
}
