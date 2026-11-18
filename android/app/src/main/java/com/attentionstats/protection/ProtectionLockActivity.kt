package com.attentionstats.protection

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.attentionstats.appearance.NativePage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

/** Secrets remain in native secure-window controls; JS can only open this page. */
class ProtectionLockActivity : FragmentActivity() {
    private val credentials by lazy {
        request?.let { ProtectionLock.credentials(it) ?: throw ProtectionCredentials.Denied("AUTH_STALE") } ?: ProtectionCredentials(this)
    }
    private val biometrics by lazy { ProtectionBiometric(this, credentials.biometricAlias) }
    private var page: NativePage? = null
    private var message: TextView? = null
    private val inputs = mutableListOf<EditText>()
    private var busy = false
    private var biometricPin: CharArray? = null
    private var request: String? = null
    private var prompt: BiometricPrompt? = null
    private var active = AtomicBoolean(true)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        request = intent.getStringExtra("request")
        if (savedInstanceState != null) { finish(); return } // No restored challenge or secret.
        overview()
    }
    private fun ensureActive() { check(active.get() && !isFinishing && !isDestroyed) }
    private fun start(title: String): NativePage {
        inputs.forEach { it.text.clear() }; inputs.clear()
        return NativePage(this).also {
            page = it; it.text(title, 24f)
            message = null
        }
    }
    private fun field(label: String, pin: Boolean = false): EditText {
        val palette = com.attentionstats.appearance.AppearanceOwner.palette(this)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(palette.surface); cornerRadius = dp(18).toFloat(); setStroke(dp(1), palette.border)
            }
        }
        container.addView(TextView(this).apply {
            text = label; textSize = 14f; setTextColor(palette.muted)
        })
        return EditText(this).apply {
            hint = label; contentDescription = label
            setTextColor(palette.text); setHintTextColor(palette.muted)
            backgroundTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            inputType = if (pin) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(if (pin) 12 else 128))
            setSingleLine(true)
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            minHeight = dp(56)
            container.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            inputs.add(this); checkNotNull(page).flexible(container)
        }
    }
    private fun progress() {
        message = TextView(this).apply {
            textSize = 15f; visibility = View.GONE
            setTextColor(com.attentionstats.appearance.AppearanceOwner.palette(this@ProtectionLockActivity).text)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { checkNotNull(page).flexible(it) }
    }
    private fun notice(value: String) { message?.apply { text = value; visibility = View.VISIBLE } }
    private fun values() = inputs.map { it.text.toString().toCharArray().also { _ -> it.text.clear() } }
    private fun runSecrets(secrets: List<CharArray>, success: () -> Unit = ::overview, operation: () -> Unit) {
        if (busy) { secrets.forEach { it.fill('\u0000') }; return }
        busy = true; notice("Checking securely…")
        ProtectionLock.worker.execute {
            val problem = try { ensureActive(); operation(); null } catch (failure: Exception) {
                when ((failure as? ProtectionCredentials.Denied)?.reason) {
                    "TRY_LATER" -> "Too many attempts. Wait before trying again. Reopening will not reset the wait."
                    "INCORRECT_SECRET" -> "The PIN or complete recovery phrase did not match."
                    else -> "Could not complete this change. Check the entries and try again. Saved protection remains in effect."
                }
            } finally { secrets.forEach { it.fill('\u0000') } }
            runOnUiThread {
                busy = false
                if (active.get() && !isFinishing && !isDestroyed) {
                    if (problem == null) success() else notice(problem)
                }
            }
        }
    }
    private fun overview() {
        ProtectionLock.worker.execute {
            val state = runCatching { credentials.state() }
            runOnUiThread {
                if (!active.get() || isFinishing || isDestroyed) return@runOnUiThread
                val current = state.getOrNull()
                val token = request
                if (token != null) {
                    val label = ProtectionLock.label(token)
                    if (label == null || current == null || !current.enabled) { finish(); return@runOnUiThread }
                    challenge(label, current); return@runOnUiThread
                }
                val screen = start("Protection Lock")
                if (current == null) {
                    screen.text("Protection Lock is unavailable. Existing protection has not been reset.")
                    screen.button("Done") { finish() }; return@runOnUiThread
                }
                screen.text(if (current.enabled) "Protection Lock is on" else "Protection Lock is off")
                screen.text("Android Settings, uninstall and emergency access remain available.")
                screen.section("What is protected", "Ordinary allowances, early focus ending, source/pause changes, limits, reminders, schedules, groups, history erase and restore replacement require authorization. Each approval applies only to the requested action.")
                if (!current.enabled) {
                    if (android.os.Build.VERSION.SDK_INT >= 26) screen.button("Set up parental PIN", true) { setup() }
                    else screen.text("Protection Lock requires Android 8 or later. Other protection tools remain available.")
                } else {
                    screen.section("Biometric convenience", "Only enable this if enrolled biometrics belong to the parent. Any eligible enrolled fingerprint or face may authorize an ordinary action. The phone PIN is never accepted here. Enrollment changes require the parental PIN to enable biometrics again.")
                    screen.text(if (current.biometric) "Biometric shortcut is on" else "Biometric shortcut is off")
                    screen.button("Enable or re-enable biometrics") { change("biometric-on") }
                    if (current.biometric) screen.button("Turn off biometric shortcut") { change("biometric-off") }
                    screen.button("Change parental PIN") { change("pin") }
                    screen.button("Replace recovery phrases") { change("phrases") }
                    screen.button("Forgot parental PIN") { change("reset") }
                    screen.button("Turn off Protection Lock") { change("disable") }
                }
                screen.button("Done") { finish() }
            }
        }
    }
    private fun phraseFields() {
        repeat(3) { field("Recovery phrase ${it + 1}"); field("Confirm recovery phrase ${it + 1}") }
    }
    private fun recoveryHelp() {
        checkNotNull(page).text("Choose three distinct phrases of 12–128 characters and keep them outside this phone. Any complete phrase can reset the PIN. Capitalization matters; outer/repeated whitespace is normalized. If all secrets are lost, recovery may require deleting app data.")
    }
    private fun setup() {
        val screen = start("Set up parental PIN")
        screen.text("Choose a separate 6–12 digit parental PIN. Do not use a PIN the child knows.")
        recoveryHelp(); field("New parental PIN", true); field("Confirm parental PIN", true); phraseFields()
        progress()
        screen.button("Enable Protection Lock", true) {
            if (busy) return@button
            val secrets = values()
            runSecrets(secrets) { runBlocking { ProtectionLock.changes.withLock {
                credentials.setup(secrets[0], secrets[1], listOf(secrets[2], secrets[4], secrets[6]),
                    listOf(secrets[3], secrets[5], secrets[7]), ::ensureActive)
            } } }
        }
        screen.button("Cancel") { if (busy) finish() else overview() }
    }
    private fun change(action: String) {
        val screen = start(when (action) { "reset" -> "Reset parental PIN"; "phrases" -> "Replace recovery phrases";
            "pin" -> "Change parental PIN"; "disable" -> "Turn off Protection Lock"; else -> "Biometric shortcut" })
        if (action == "reset") field("Any complete recovery phrase") else field("Current parental PIN", true)
        if (action == "phrases") { field("Any current recovery phrase"); recoveryHelp(); phraseFields() }
        if (action == "pin" || action == "reset") { field("New parental PIN", true); field("Confirm parental PIN", true) }
        if (action == "biometric-on") screen.text("Only continue if the enrolled biometrics belong to the parent. You will verify your fingerprint or face before the shortcut turns on.")
        progress()
        screen.button("Confirm change", true) {
            if (busy) return@button
            val secrets = values()
            if (action == "biometric-on") { enableBiometrics(secrets); return@button }
            val empty = CharArray(0)
            runSecrets(secrets, success = { if (request != null) finish() else overview() }) {
                runBlocking { ProtectionLock.changes.withLock {
                    credentials.change(action, if (action == "reset") empty else secrets[0],
                        if (action == "reset") secrets[0] else if (action == "phrases") secrets[1] else empty,
                        if (action in setOf("pin", "reset")) secrets[1] else empty,
                        if (action in setOf("pin", "reset")) secrets[2] else empty,
                        if (action == "phrases") listOf(secrets[2], secrets[4], secrets[6]) else emptyList(),
                        if (action == "phrases") listOf(secrets[3], secrets[5], secrets[7]) else emptyList(), ::ensureActive)
                } }
            }
        }
        screen.button("Cancel") { if (busy || request != null) finish() else overview() }
    }
    private fun enableBiometrics(secrets: List<CharArray>) {
        // Disable the old shortcut under PIN authority before rotating an enrollment-bound key.
        val pin = secrets[0].copyOf()
        biometricPin = pin
        runSecrets(secrets, success = {
            try {
                prompt = biometrics.prompt(this) { success ->
                    if (!success || !active.get()) { pin.fill('\u0000'); notice("Biometric setup cancelled. PIN protection remains on.") }
                    else runSecrets(listOf(pin)) { runBlocking { ProtectionLock.changes.withLock {
                        credentials.change("biometric-on", pin, CharArray(0), CharArray(0), CharArray(0), beforeCommit = ::ensureActive)
                    } } }
                }
            } catch (_: Exception) { pin.fill('\u0000'); notice("Strong biometrics unavailable. Use the parental PIN.") }
        }) {
            try {
                runBlocking { ProtectionLock.changes.withLock {
                    credentials.change("biometric-off", pin, CharArray(0), CharArray(0), CharArray(0), beforeCommit = ::ensureActive)
                    biometrics.provision()
                } }
            } catch (failure: Exception) { pin.fill('\u0000'); throw failure }
        }
    }
    private fun challenge(label: String, state: ProtectionCredentials.State) {
        val screen = start("Parent authorization")
        screen.text(label)
        field("Parental PIN", true)
        progress()
        screen.button("Authorize this action", true) {
            if (busy) return@button
            val secrets = values()
            var revision: String? = null
            runSecrets(secrets, success = {
                request?.let { ProtectionLock.finish(it, revision) }; finish()
            }) { revision = credentials.verify(secrets[0]) }
        }
        if (state.biometric) screen.button("Use parent biometrics") {
            if (busy) return@button
            try { prompt = biometrics.prompt(this) { success ->
                if (success && active.get()) { request?.let { ProtectionLock.finish(it, state.revision) }; finish() }
                else notice("Biometric authorization did not complete. Use the parental PIN.")
            } } catch (_: Exception) { notice("Biometrics changed or unavailable. Use the parental PIN; re-enable the shortcut in Protect.") }
        }
        screen.button("Forgot parental PIN") { change("reset") }
        screen.button("Cancel") { finish() }
    }
    override fun onStop() {
        active.set(false); prompt?.cancelAuthentication(); prompt = null
        biometricPin?.fill('\u0000'); biometricPin = null
        inputs.forEach { it.text.clear() }
        request?.let { ProtectionLock.finish(it, null) }
        super.onStop()
        finish() // Leaving the page never leaves an authorization or secrets waiting.
    }
    override fun onDestroy() { request?.let { ProtectionLock.finish(it, null) }; super.onDestroy() }
}
