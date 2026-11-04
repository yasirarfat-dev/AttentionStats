package com.attentionstats.protection

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.attentionstats.reels.ReelsCoordinator
import org.attentionstats.protection.AppGroup
import org.attentionstats.protection.BlockWindow
import org.attentionstats.protection.SchedulePlan
import org.json.JSONObject
import java.util.UUID

/** Native editor: saved app groups, multiple weekday/time windows, explicit activation preview. */
class SchedulesActivity : Activity() {
    private val coordinator get() = ReelsCoordinator.get(this)
    private var plan = SchedulePlan()
    private var revision = 0L
    private lateinit var content: LinearLayout
    private var busy = false
    private var apps: List<Pair<String, String>> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        com.attentionstats.appearance.AppearanceOwner.prepare(this)
        super.onCreate(savedInstanceState)
        title = "App schedules"
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48) }
        setContentView(ScrollView(this).apply { addView(content) })
        Thread {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val choices = packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName !in coordinator.schedules.excluded }
                .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString().take(100) }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }.take(500)
            runOnUiThread { apps = choices; refresh() }
        }.start()
    }
    override fun onResume() { super.onResume(); if (::content.isInitialized) refresh() }
    private fun refresh() {
        coordinator.scheduleSnapshot({ raw -> runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val state = JSONObject(raw)
            revision = state.getString("revision").toLong()
            plan = ScheduleOwner.decode(JSONObject().put("groups", state.getJSONArray("groups")).put("windows", state.getJSONArray("windows")).toString())
            render(state.getBoolean("accessibility"))
        } }, { runOnUiThread { message("Schedules unavailable. Existing history is preserved.") } })
    }
    private fun label(value: String, heading: Boolean = false) {
        content.addView(TextView(this).apply { text = value; textSize = if (heading) 21f else 16f; setPadding(0, 16, 0, 12)
            if (heading && android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true })
    }
    private fun button(value: String, action: () -> Unit) { content.addView(Button(this).apply { text = value; setOnClickListener { if (!busy) action() } }) }
    private fun render(available: Boolean) {
        content.removeAllViews()
        label("App schedules", true)
        label("Choose apps or make a category such as Social, Games or Work. Add as many periods as you need. Times follow this phone's local clock. An overnight period belongs to its starting day.")
        label(if (available) "Accessibility is connected. Enabled schedules apply while their apps are in front."
            else "Schedules are not enforcing: enable Attention Stats in Accessibility. Android may stop the service; blocking is best effort.")
        if (!available) button("Open Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        label("A schedule checks only the foreground app's package, never its screen content. It covers the selected app and offers immediate access to Settings or a 5-minute allowance. Emergency, keyboard and system recovery apps cannot be selected.")
        label("App groups", true)
        plan.groups.forEach { group ->
            label("${group.name} · ${group.packages.size} apps")
            button("Edit ${group.name}") { editGroup(group) }
            button("Delete ${group.name}") { confirm("Delete this group and all its periods?") {
                save(plan.copy(groups = plan.groups.filter { it.id != group.id }, windows = plan.windows.filter { it.groupId != group.id }))
            } }
        }
        button("Add app group") { editGroup(null) }
        label("Blocking periods", true)
        plan.windows.forEach { window ->
            val name = plan.groups.firstOrNull { it.id == window.groupId }?.name ?: "Group"
            val summary = "$name · ${time(window.startMinute)}–${time(window.endMinute)}${if (window.startMinute > window.endMinute) " next day" else ""} · ${days(window.days)}"
            label("$summary · ${if (window.enabled) "On" else "Off"}")
            button(if (window.enabled) "Turn off $name period" else "Enable $name period") {
                confirm(if (window.enabled) "Turn off this period?" else "Block $summary? This applies to ${plan.groups.first { it.id == window.groupId }.packages.size} selected apps. You can always allow 5 minutes.") {
                    save(plan.copy(windows = plan.windows.map { if (it.id == window.id) it.copy(enabled = !it.enabled) else it }))
                }
            }
            button("Edit $name period") { editWindow(window) }
            button("Delete $name period") { confirm("Delete $summary?") { save(plan.copy(windows = plan.windows.filter { it.id != window.id })) } }
        }
        button("Add blocking period") { editWindow(null) }
        button("Done") { finish() }
    }
    private fun editGroup(existing: AppGroup?) {
        val draft = EditText(this).apply { setSingleLine(); hint = "For example: Social"; setText(existing?.name ?: "") }
        AlertDialog.Builder(this).setTitle("Group name").setView(draft).setNegativeButton("Cancel", null)
            .setPositiveButton("Choose apps") { _, _ ->
                val name = draft.text.toString().trim()
                if (name.isBlank() || name.length > 60) { message("Use a name from 1 to 60 characters."); return@setPositiveButton }
                val selected = (existing?.packages ?: emptySet()).toMutableSet()
                AlertDialog.Builder(this).setTitle("Apps in $name")
                    .setMultiChoiceItems(apps.map { "${it.second} (${it.first})" }.toTypedArray(), BooleanArray(apps.size) { apps[it].first in selected }) { _, which, checked ->
                        if (checked) selected.add(apps[which].first) else selected.remove(apps[which].first)
                    }.setNegativeButton("Cancel", null).setPositiveButton("Review") review@{ _, _ ->
                        if (selected.isEmpty() || selected.size > 100) { message("Choose between 1 and 100 apps."); return@review }
                        val group = AppGroup(existing?.id ?: UUID.randomUUID().toString(), name, selected.toSet())
                        confirm("Save $name with ${selected.size} apps? Existing enabled periods for this group will apply to this membership.") {
                            save(plan.copy(groups = plan.groups.filter { it.id != group.id } + group))
                        }
                    }.show()
            }.show()
    }
    private fun editWindow(existing: BlockWindow?) {
        if (plan.groups.isEmpty()) { message("Add an app group first. A group can contain just one app."); return }
        AlertDialog.Builder(this).setTitle("Choose app group").setItems(plan.groups.map { it.name }.toTypedArray()) { _, index ->
            val group = plan.groups[index]
            val selected = BooleanArray(7) { existing?.let { window -> window.days and (1 shl it) != 0 } ?: true }
            AlertDialog.Builder(this).setTitle("Starting days").setMultiChoiceItems(DAYS, selected) { _, day, checked -> selected[day] = checked }
                .setNegativeButton("Cancel", null).setPositiveButton("Start time") { _, _ ->
                    val mask = selected.indices.filter { selected[it] }.sumOf { 1 shl it }
                    if (mask == 0) { message("Choose at least one day."); return@setPositiveButton }
                    val start = existing?.startMinute ?: 19 * 60
                    TimePickerDialog(this, { _, hour, minute ->
                        val from = hour * 60 + minute
                        val end = existing?.endMinute ?: 23 * 60
                        TimePickerDialog(this, endTime@{ _, toHour, toMinute ->
                            val to = toHour * 60 + toMinute
                            if (from == to) { message("Start and end must differ. Use two periods for a full day."); return@endTime }
                            val window = BlockWindow(existing?.id ?: UUID.randomUUID().toString(), group.id, mask, from, to, false)
                            confirm("Save ${group.name}, ${time(from)}–${time(to)}, ${days(mask)}? Saved off; enable after reviewing. Editing an active period turns it off.") {
                                save(plan.copy(windows = plan.windows.filter { it.id != window.id } + window))
                            }
                        }, end / 60, end % 60, android.text.format.DateFormat.is24HourFormat(this)).show()
                    }, start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(this)).show()
                }.show()
        }.show()
    }
    private fun save(next: SchedulePlan) {
        busy = true
        coordinator.saveSchedules(ScheduleOwner.encode(next).toString(), revision, { runOnUiThread { busy = false; refresh() } }, {
            runOnUiThread { busy = false; message("Could not save. The settings may have changed; refresh and try again."); refresh() }
        })
    }
    private fun confirm(value: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle("Review schedule").setMessage(value)
        .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> action() }.show() }
    private fun message(value: String) { if (!isFinishing) AlertDialog.Builder(this).setMessage(value).setPositiveButton("Okay", null).show() }
    private fun time(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    private fun days(mask: Int) = DAYS.indices.filter { mask and (1 shl it) != 0 }.joinToString(", ") { DAYS[it] }
    companion object { private val DAYS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") }
}
