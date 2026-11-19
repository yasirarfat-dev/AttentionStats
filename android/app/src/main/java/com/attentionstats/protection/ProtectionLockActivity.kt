package com.attentionstats.protection

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.attentionstats.R
import com.attentionstats.appearance.NativePage
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.attentionstats.protection.OneUseChallenges
import org.attentionstats.protection.ParentAuthority
import org.attentionstats.protection.ParentSecretRules
import org.attentionstats.protection.UnlockMethods

/**
 * Secrets remain in native secure-window controls; JS can only open this page.
 *
 * Two exclusive ways to approve (ADR-121), shown as two tabs: **Phone fingerprint** (the phone's own
 * fingerprint, set up with one touch) and **App PIN** (a PIN or pattern that works only in this app, set up step
 * by step: create, confirm, backup phrase).
 */
class ProtectionLockActivity : FragmentActivity() {
    private val credentials by lazy {
        request?.let { ProtectionLock.credentials(it) ?: throw ProtectionCredentials.Denied("AUTH_STALE") } ?: ProtectionCredentials(this)
    }
    private val biometrics by lazy { ProtectionBiometric(this, credentials.biometricAlias) }
    private val challenges = OneUseChallenges({ SecureRandom().nextBytes(it) })
    private var page: NativePage? = null
    private var form: ProtectionForm? = null
    private val ui get() = checkNotNull(form)
    private var message: TextView? = null
    private val inputs = mutableListOf<EditText>()
    private val patterns = mutableListOf<ProtectionPatternView>()
    private val drafts = mutableListOf<IntArray>()
    private var busy = false
    private var heldProof: CharArray? = null
    private var request: String? = null
    private var prompt: BiometricPrompt? = null
    private var active = AtomicBoolean(true)
    /** What system Back does on the current page; null closes the page. */
    private var back: (() -> Unit)? = null
    /** Bumped on every page change so late callbacks (prompts, delayed auto-prompts) never act on a newer page. */
    private var pageGeneration = 0

    private enum class Tab { PHONE, APP }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        request = intent.getStringExtra("request")
        if (savedInstanceState != null) { finish(); return } // No restored challenge or secret.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (busy) return; back?.invoke() ?: finish() }
        })
        overview()
    }
    private fun ensureActive() { check(active.get() && !isFinishing && !isDestroyed) }
    private fun live(generation: Int = pageGeneration) = active.get() && !isFinishing && !isDestroyed && generation == pageGeneration
    private fun wipeDrafts() { drafts.forEach { it.fill(0) }; drafts.clear() }
    /** A new page. [backTo] names the back link above the title; [onBack] also handles system Back. */
    private fun start(title: String, backTo: String? = null, onBack: (() -> Unit)? = null): NativePage {
        inputs.forEach { it.text.clear() }; inputs.clear()
        patterns.forEach { it.clear() }; patterns.clear()
        wipeDrafts(); challenges.revoke(); pageGeneration++
        prompt?.cancelAuthentication(); prompt = null
        back = onBack
        return NativePage(this).also {
            page = it; form = ProtectionForm(this, it) { edit -> inputs.add(edit) }
            if (backTo != null) it.back(backTo) { if (!busy) (onBack ?: ::finish)() }
            it.text(title, 24f)
            message = null
        }
    }
    private fun progress(parent: LinearLayout? = null) {
        message = TextView(this).apply {
            textSize = 15f; visibility = View.GONE
            setTextColor(ui.palette.text)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { if (parent != null) parent.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.dp(12) }) else checkNotNull(page).flexible(it) }
    }
    private fun notice(value: String) { message?.apply { text = value; visibility = View.VISIBLE } }
    private fun quiet() { message?.visibility = View.GONE }
    /** Checks are fast now (ADR-122); "Checking…" appears only if one takes longer than a moment. */
    private val slowCheck = Runnable { if (busy) notice("Checking…") }
    private fun checking() { window.decorView.removeCallbacks(slowCheck); window.decorView.postDelayed(slowCheck, 400) }
    private fun invalid() { notice("Check the highlighted entries."); ui.focusFirstError() }
    private fun wipe(values: List<CharArray?>) = values.forEach { it?.fill('\u0000') }
    private fun keyValid() = runCatching { biometrics.keyValid() }.getOrDefault(false)
    private fun fingerprintHardware() = runCatching { biometrics.available() }.getOrDefault(false)
    private fun deviceReady() = runCatching { biometrics.deviceAvailable() }.getOrDefault(false)
    private fun label(kind: ParentAuthority) = when (kind) {
        ParentAuthority.PIN -> "PIN"; ParentAuthority.PATTERN -> "Pattern"; else -> "Backup phrase"
    }
    /** "PIN" or "pattern", as the app key is named in sentences. */
    private fun keyName(state: ProtectionCredentials.State) = if (state.pattern && !state.pin) "pattern" else "PIN"
    private fun keyName(kind: ParentAuthority) = if (kind == ParentAuthority.PATTERN) "pattern" else "PIN"

    /**
     * Runs [operation] on the worker; every secret is wiped afterwards. [failed] runs on any problem. A wrong
     * secret goes to [rejected] when given (it marks the field itself), otherwise it is reported below the form.
     */
    private fun runSecrets(secrets: List<CharArray>, success: () -> Unit = { overview() }, rejected: (() -> Unit)? = null,
        failed: () -> Unit = {}, operation: () -> Unit) {
        if (busy) { secrets.forEach { it.fill('\u0000') }; failed(); return }
        busy = true; quiet(); checking()
        ProtectionLock.worker.execute {
            var reason: String? = null
            val problem = try { ensureActive(); operation(); null } catch (failure: Exception) {
                reason = (failure as? ProtectionCredentials.Denied)?.reason
                when (reason) {
                    "TRY_LATER" -> "Too many attempts. Wait before trying again. Reopening will not reset the wait."
                    "INCORRECT_SECRET" -> "That did not match. Try again."
                    "NOT_CONFIGURED" -> "Protection Lock is not set up."
                    else -> "Could not complete this change. Check the entries and try again. Saved protection remains in effect."
                }
            } finally { secrets.forEach { it.fill('\u0000') } }
            runOnUiThread {
                busy = false
                if (active.get() && !isFinishing && !isDestroyed) {
                    if (problem == null) success()
                    else if (reason == "INCORRECT_SECRET" && rejected != null) { quiet(); failed(); rejected() }
                    else { notice(problem); failed() }
                } else if (problem != null) failed()
            }
        }
    }
    /** Loads the current state off the main thread, then continues with it on the page. */
    private fun withState(next: (ProtectionCredentials.State) -> Unit) {
        ProtectionLock.worker.execute {
            val state = runCatching { credentials.state() }.getOrNull()
            runOnUiThread { if (active.get() && !isFinishing && !isDestroyed) { if (state == null) overview() else next(state) } }
        }
    }

    /** Draws the app pattern once (proof entry). The path is wiped on leaving. */
    private inner class PatternEntry(parent: LinearLayout, private val drawn: (IntArray) -> Unit) {
        private val status = ui.body(parent, "Draw your app pattern.", ui.palette.text)
        val view = ProtectionPatternView(this@ProtectionLockActivity, ui.palette)
        private var failed = false
        init {
            status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            patterns.add(view)
            parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.onStart = { if (failed) { failed = false; say("Draw your app pattern.") } }
            view.onComplete = { path -> drafts.add(path); accept(path) }
        }
        fun say(text: String, color: Int = ui.palette.text) { status.text = text; status.setTextColor(color) }
        private fun accept(path: IntArray) {
            failed = false
            if (!ParentSecretRules.validPattern(path)) { path.fill(0); fail("Connect at least 4 dots. Try again."); return }
            view.clear(); drawn(path)
        }
        fun fail(text: String) {
            failed = true; say(text, ui.palette.danger); view.showError()
            view.postDelayed({ if (failed) view.clear() }, 900)
        }
    }

    /** Existing-secret proof: PIN, app pattern or a backup phrase, as the action and configuration allow. */
    private inner class ProofEntry(private val kinds: List<ParentAuthority>, title: String?, onPattern: (() -> Unit)? = null) {
        var kind = kinds.first(); private set
        private var pin: ProtectionForm.Field? = null
        private var phrase: ProtectionForm.Field? = null
        private var pattern: PatternEntry? = null
        private var path: IntArray? = null
        private val sections = kinds.associateWith { LinearLayout(this@ProtectionLockActivity).apply { orientation = LinearLayout.VERTICAL } }
        init {
            val card = ui.card(title)
            if (kinds.size > 1) ui.segmented(card, kinds.map(::label), 0) { select(kinds[it]) }
            for (each in kinds) {
                val section = sections.getValue(each)
                card.addView(section)
                when (each) {
                    ParentAuthority.PIN -> pin = ui.secretField(section, "App PIN", ProtectionForm.Kind.PIN)
                    ParentAuthority.PATTERN -> pattern = PatternEntry(section) { drawn ->
                        path?.fill(0); path = drawn
                        if (onPattern != null) onPattern() else pattern?.say("Pattern entered. Draw again to replace it.", ui.palette.accent)
                    }
                    else -> phrase = ui.secretField(section, "Any one backup phrase", ProtectionForm.Kind.PHRASE)
                }
            }
            select(kind)
        }
        private fun select(next: ParentAuthority) {
            kind = next
            sections.forEach { (each, section) -> section.visibility = if (each == next) View.VISIBLE else View.GONE }
        }
        fun focus() { (pin ?: phrase)?.takeIf { kind != ParentAuthority.PATTERN }?.let(ui::focus) }
        /** The proof secret (encoded for a pattern), or null after showing an inline error. Caller clears. */
        fun collect(): CharArray? = when (kind) {
            ParentAuthority.PIN -> {
                val field = checkNotNull(pin); val value = field.chars()
                if (ParentSecretRules.validPin(value)) value else { value.fill('\u0000'); field.fail("Enter your 4–12 digit app PIN."); null }
            }
            ParentAuthority.PATTERN -> {
                val drawn = path; path = null
                if (drawn != null && ParentSecretRules.validPattern(drawn)) ParentSecretRules.encodePattern(drawn).also { drawn.fill(0) }
                else { checkNotNull(pattern).fail("Draw your app pattern first."); null }
            }
            else -> {
                val field = checkNotNull(phrase); val value = field.chars()
                val normalized = ParentSecretRules.normalizePhrase(value)
                if (normalized != null) { normalized.fill('\u0000'); value }
                else { value.fill('\u0000'); field.fail("Enter one complete backup phrase (3 to 128 characters)."); null }
            }
        }
        fun rejected() {
            when (kind) {
                ParentAuthority.PIN -> pin?.apply { clear(); fail("That PIN did not match.") }
                ParentAuthority.PATTERN -> pattern?.fail("That pattern did not match.")
                else -> phrase?.fail("That phrase did not match.")
            }
        }
    }

    /**
     * Backup phrases, one card each with its confirmation (visible text). The first is required; up to
     * [ParentSecretRules.PHRASE_COUNT] - 1 more are optional and revealed one at a time (ADR-120).
     */
    private inner class PhraseSet {
        private val cards = mutableListOf<LinearLayout>()
        private val pairs = (1..ParentSecretRules.PHRASE_COUNT).map { n ->
            val card = ui.card(if (n == 1) "Backup phrase" else "Backup phrase $n", if (n == 1) null else "Optional")
            cards.add(card)
            if (n > 1) card.visibility = View.GONE
            ui.secretField(card, "Phrase", ProtectionForm.Kind.PHRASE) to ui.secretField(card, "Type it again", ProtectionForm.Kind.PHRASE)
        }
        init {
            val holder = LinearLayout(this@ProtectionLockActivity).apply { orientation = LinearLayout.VERTICAL }
            checkNotNull(page).flexible(holder)
            lateinit var more: TextView
            more = ui.link(holder, "+ Add another backup phrase (optional)") {
                cards.firstOrNull { it.visibility != View.VISIBLE }?.let { it.visibility = View.VISIBLE; ui.refreshIme() }
                if (cards.all { it.visibility == View.VISIBLE }) more.visibility = View.GONE
            }
        }
        /** Required first phrase plus any optional ones the parent started; an untouched optional card is skipped. */
        fun collect(): Pair<List<CharArray>, List<CharArray>>? {
            val used = pairs.indices.filter { i -> i == 0 || (cards[i].visibility == View.VISIBLE &&
                (pairs[i].first.edit.length() > 0 || pairs[i].second.edit.length() > 0)) }
            val phrases = used.map { pairs[it].first.chars() }
            val confirmations = used.map { pairs[it].second.chars() }
            val problems = ParentSecretRules.phraseProblems(phrases, confirmations)
            problems.forEachIndexed { k, problem ->
                val pair = pairs[used[k]]
                when (problem) {
                    ParentSecretRules.PhraseProblem.LENGTH -> pair.first.fail("Use 3 to 128 characters.")
                    ParentSecretRules.PhraseProblem.DUPLICATE -> pair.first.fail("Use a phrase different from the one above.")
                    ParentSecretRules.PhraseProblem.MISMATCH -> pair.second.fail("Does not match the phrase above.")
                    null -> {}
                }
            }
            if (problems.any { it != null }) { wipe(phrases + confirmations); return null }
            return phrases to confirmations
        }
    }

    /** Optional extras next to phone fingerprint. Changing them needs a parent fingerprint on the same page. */
    private inner class ExtraChoices(initial: UnlockMethods) {
        private val weakHardware = runCatching { biometrics.weakAvailable() }.getOrDefault(false)
        private val weak: Switch
        private val device: Switch
        init {
            val card = ui.card("Also accept", "Optional. Only turn these on if your child cannot unlock this phone with their own face or code.")
            weak = ui.toggle(card, "Face unlock", "Any face saved on this phone will work.",
                "Less secure: some phones accept a photo or a lookalike.", initial.weak, weakHardware, "No face is saved on this phone.")
            device = ui.toggle(card, "Phone's PIN, pattern or password", "The code that unlocks this phone.",
                "Only if your child does not know it.", initial.device, deviceReady(),
                if (Build.VERSION.SDK_INT < ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK) "Needs Android 11 or later." else "Set up a phone screen lock first.")
        }
        fun value() = UnlockMethods(true, weak.isChecked, device.isChecked).normalized()
    }

    // ---- Overview: two exclusive tabs (ADR-121) ---------------------------------------------------------

    private fun overview(tab: Tab? = null, flash: String? = null) {
        ProtectionLock.worker.execute {
            val state = runCatching { credentials.state() }
            val strongKey = state.getOrNull()?.biometric == true && keyValid()
            val hardware = fingerprintHardware()
            runOnUiThread {
                if (!active.get() || isFinishing || isDestroyed) return@runOnUiThread
                val current = state.getOrNull()
                val token = request
                if (token != null) {
                    val label = ProtectionLock.label(token)
                    if (label == null || current == null || !current.enabled) { finish(); return@runOnUiThread }
                    challenge(label, current, token, strongKey); return@runOnUiThread
                }
                val screen = start("Protection Lock", "Protect")
                if (current == null) {
                    screen.text("Protection Lock is unavailable. Existing protection has not been reset.")
                    screen.button("Done") { finish() }; return@runOnUiThread
                }
                if (Build.VERSION.SDK_INT < 26) {
                    screen.text("Protection Lock requires Android 8 or later. Other protection tools remain available.")
                    screen.button("Done") { finish() }; return@runOnUiThread
                }
                if (flash != null) ui.flash(flash)
                status(current, strongKey)
                val inUse = when { !current.enabled -> null; current.keyless -> Tab.PHONE; else -> Tab.APP }
                val selected = tab ?: inUse ?: if (hardware) Tab.PHONE else Tab.APP
                val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val generation = pageGeneration
                fun render(which: Tab) {
                    if (!live(generation)) return
                    holder.removeAllViews(); message = null
                    ui.target = holder
                    try { if (which == Tab.PHONE) phoneTab(holder, current, strongKey, hardware) else appTab(holder, current, strongKey) }
                    finally { ui.target = null }
                }
                ui.tabs(listOf(R.drawable.ic_lock_fingerprint to "Phone fingerprint", R.drawable.ic_lock_key to "App PIN"),
                    selected.ordinal, inUse?.ordinal ?: -1) { render(Tab.entries[it]) }
                screen.flexible(holder)
                render(selected)
                screen.button("Done") { finish() }
            }
        }
    }

    /** One line that says whether the lock is on and which way approves. */
    private fun status(current: ProtectionCredentials.State, strongKey: Boolean) {
        val card = ui.card()
        when {
            !current.enabled -> ui.feature(card, R.drawable.ic_lock_shield, "Protection Lock is off",
                "Pick how a parent approves extra time and changes.", "Off")
            current.keyless && !strongKey -> ui.feature(card, R.drawable.ic_lock_fingerprint, "Fingerprint approval is paused",
                "A fingerprint was added to this phone or the sensor changed. Reset it below to approve again.", "Paused")
            current.keyless -> ui.feature(card, R.drawable.ic_lock_fingerprint, "Protection Lock is on",
                "A parent's fingerprint approves extra time and protection changes, one action at a time.", "On")
            else -> ui.feature(card, R.drawable.ic_lock_key, "Protection Lock is on",
                "Your app ${keyName(current)} approves extra time and protection changes, one action at a time.", "On")
        }
    }

    private fun noFingerprint(holder: LinearLayout, offerApp: Boolean) {
        ui.hero(R.drawable.ic_lock_fingerprint, "No fingerprint on this phone",
            "Save a fingerprint in the phone's settings first${if (offerApp) ", or use an app PIN instead" else ""}.")
        ui.action(holder, "Open phone settings", true) {
            val enroll = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_BIOMETRIC_ENROLL) else Intent(Settings.ACTION_SECURITY_SETTINGS)
            runCatching { startActivity(enroll) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } }
        }
        if (offerApp) ui.action(holder, "Use an app PIN") { overview(Tab.APP) }
    }
    private fun fingerprintFacts() {
        val card = ui.card()
        ui.feature(card, R.drawable.ic_lock_phone, "Uses the phone's saved fingerprints",
            "Any fingerprint saved on this phone approves, so choose this only if your child's is not saved.")
        ui.feature(card, R.drawable.ic_lock_shield, "New fingerprints pause it",
            "If anyone adds a fingerprint later, approval pauses until a parent resets it.")
    }

    private fun phoneTab(holder: LinearLayout, current: ProtectionCredentials.State, strongKey: Boolean, hardware: Boolean) {
        when {
            !current.enabled && !hardware -> noFingerprint(holder, offerApp = true)
            !current.enabled -> {
                ui.hero(R.drawable.ic_lock_fingerprint, "Approve with your fingerprint", "One touch on this phone's sensor.\nNothing new to remember.")
                progress(holder)
                ui.action(holder, "Turn on with fingerprint", true) { fingerprintSetup() }
                fingerprintFacts()
            }
            current.keyless -> {
                // The status card above already says on or paused; this card holds what goes with it.
                val card = ui.card()
                if (strongKey) ui.feature(card, R.drawable.ic_lock_phone, "Keep only parents' fingerprints saved",
                    "Any fingerprint saved on this phone approves.")
                val extras = listOfNotNull("Face".takeIf { current.methods.weak }, "Phone code".takeIf { current.methods.device })
                if (extras.isNotEmpty()) ui.feature(card, R.drawable.ic_lock_phone, "Also accepted",
                    "Anything saved on this phone works, including a child's.", extras.joinToString(" + "))
                ui.feature(card, R.drawable.ic_lock_note, "Backup phrase",
                    if (current.phrases > 0) "Any one resets fingerprint approval if it pauses."
                    else "None saved. Recommended: with one, only you can reset fingerprint approval. Without, the phone's screen lock resets it.",
                    if (current.phrases > 0) "${current.phrases} saved" else "None")
                val manage = ui.card("Manage")
                if (!strongKey) ui.option(manage, R.drawable.ic_lock_fingerprint, "Reset fingerprint approval",
                    if (current.phrases > 0) "Enter a backup phrase, then touch the sensor." else "Use the phone's screen lock, then touch the sensor.") { fingerprintReset(current) }
                else {
                    ui.option(manage, R.drawable.ic_lock_note, if (current.phrases == 0) "Add a backup phrase" else "Replace backup phrases",
                        if (current.phrases == 0) "Recommended. Write it on paper away from this phone." else "Needs a fingerprint and a current phrase.") { change("phrases", current) }
                    ui.option(manage, R.drawable.ic_lock_phone, "Face and phone code", "Optional extras next to fingerprint. Off unless you turn them on.") { change("methods", current) }
                    ui.option(manage, R.drawable.ic_lock_fingerprint, "Fingerprint not working?",
                        if (current.phrases > 0) "Reset it with a backup phrase." else "Reset it with the phone's screen lock.") { fingerprintReset(current) }
                    ui.option(manage, R.drawable.ic_lock_shield, "Turn off Protection Lock", "Deletes fingerprint approval and backup phrases.", danger = true) { change("disable", current) }
                }
            }
            !hardware -> noFingerprint(holder, offerApp = false)
            else -> {
                val key = keyName(current)
                ui.hero(R.drawable.ic_lock_fingerprint, "Switch to fingerprint",
                    "Approve with one touch instead of your app $key.\nThe $key is deleted; backup phrases stay.")
                ui.action(holder, "Switch to fingerprint", true) { switchToFingerprint(current) }
                fingerprintFacts()
            }
        }
    }

    private fun appTab(holder: LinearLayout, current: ProtectionCredentials.State, strongKey: Boolean) {
        when {
            !current.enabled -> {
                ui.hero(R.drawable.ic_lock_key, "Approve with an app PIN",
                    "A PIN or pattern that works only in this app.\nIt is not the code that unlocks the phone.")
                ui.action(holder, "Create app PIN", true) { keyFlow(KeyFlow(Purpose.SETUP, current)) }
                val card = ui.card()
                ui.feature(card, R.drawable.ic_lock_shield, "Safe with a child's fingerprint here", "Only someone who knows the PIN or pattern can approve.")
                ui.feature(card, R.drawable.ic_lock_note, "A backup phrase resets it", "Written on paper, in case the PIN is ever forgotten.")
            }
            !current.keyless -> {
                val key = keyName(current)
                // The status card above already names the key and says it is on.
                val card = ui.card()
                ui.feature(card, R.drawable.ic_lock_note, "Backup phrases", "Any one resets a forgotten $key.", "${current.phrases} saved")
                val shortcuts = listOfNotNull("Fingerprint".takeIf { current.methods.strong }, "Face".takeIf { current.methods.weak }, "Phone code".takeIf { current.methods.device })
                if (shortcuts.isNotEmpty()) ui.feature(card, R.drawable.ic_lock_phone, "Phone unlock also approves",
                    if (current.methods.strong && !strongKey) "Turned on in an earlier version. Fingerprints changed since, so only the app $key works now."
                    else "Turned on in an earlier version. Anything saved on this phone works, including a child's.", shortcuts.joinToString(" + "))
                val manage = ui.card("Manage")
                if (current.pin && current.pattern) {
                    // Records from earlier builds may hold both; saving either one keeps only that one.
                    ui.option(manage, R.drawable.ic_lock_key, "Keep the PIN only", "Enter the current one, then choose a new PIN.") { keyFlow(KeyFlow(Purpose.CHANGE, current, ParentAuthority.PIN)) }
                    ui.option(manage, R.drawable.ic_lock_key, "Keep the pattern only", "Enter the current one, then draw a new pattern.") { keyFlow(KeyFlow(Purpose.CHANGE, current, ParentAuthority.PATTERN)) }
                } else {
                    val kind = if (current.pin) ParentAuthority.PIN else ParentAuthority.PATTERN
                    val other = if (current.pin) ParentAuthority.PATTERN else ParentAuthority.PIN
                    ui.option(manage, R.drawable.ic_lock_key, "Change $key", "Enter the current $key, then choose a new one.") { keyFlow(KeyFlow(Purpose.CHANGE, current, kind)) }
                    ui.option(manage, R.drawable.ic_lock_key, "Use a ${keyName(other)} instead", "The new ${keyName(other)} replaces your $key.") { keyFlow(KeyFlow(Purpose.CHANGE, current, other)) }
                }
                if (shortcuts.isNotEmpty()) ui.option(manage, R.drawable.ic_lock_phone, "Stop phone unlock approving", "Only the app $key approves from then on.") { change("methods-off", current) }
                ui.option(manage, R.drawable.ic_lock_note, "Forgot $key?", "Reset it with a backup phrase.") { keyFlow(KeyFlow(Purpose.RESET, current)) }
                ui.option(manage, R.drawable.ic_lock_note, "Replace backup phrases", "Needs your $key and a current phrase.") { change("phrases", current) }
                ui.option(manage, R.drawable.ic_lock_shield, "Turn off Protection Lock", "Deletes the app $key and backup phrases.", danger = true) { change("disable", current) }
            }
            strongKey -> {
                ui.hero(R.drawable.ic_lock_key, "Switch to an app PIN",
                    "Approve with a PIN or pattern instead of fingerprint.\nBest if your child also has a fingerprint on this phone.")
                ui.action(holder, "Switch to app PIN", true) { keyFlow(KeyFlow(Purpose.SWITCH, current)) }
            }
            else -> {
                ui.hero(R.drawable.ic_lock_key, "Switch to an app PIN",
                    if (current.phrases > 0) "Fingerprint approval is paused. Enter a backup phrase, then choose an app PIN or pattern."
                    else "Fingerprint approval is paused. Reset it in Phone fingerprint first, then switch here.")
                if (current.phrases > 0) ui.action(holder, "Use a backup phrase", true) { keyFlow(KeyFlow(Purpose.RESET, current)) }
                else ui.action(holder, "Reset fingerprint approval", true) { fingerprintReset(current) }
            }
        }
    }

    // ---- Phone fingerprint: one touch to turn on (ADR-121) -----------------------------------------------

    /** Provisions the enrollment-bound key and proves a parent's finger works before the lock depends on it. */
    private fun fingerprintSetup() {
        if (busy || prompt != null) return
        busy = true; notice("Preparing fingerprint…")
        val generation = pageGeneration
        ProtectionLock.worker.execute {
            val ready = runCatching { ensureActive(); biometrics.provision(); true }.getOrDefault(false)
            runOnUiThread {
                busy = false
                if (!live(generation)) return@runOnUiThread
                if (!ready) { notice("Fingerprint is not available right now. Try again, or use an app PIN."); return@runOnUiThread }
                quiet()
                try {
                    prompt = biometrics.prompt(this, "Turn on fingerprint approval", "Cancel", onDismiss = { prompt = null; quiet() }) { ok ->
                        prompt = null
                        if (!ok || !live(generation)) { notice("Fingerprint was not confirmed. Try again."); return@prompt }
                        // Android 11+ can always fall back to the phone's screen lock, so no phrase is needed to start.
                        if (Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK) createKeyless(emptyList(), emptyList())
                        else fingerprintPhrase()
                    }
                } catch (_: Exception) { prompt = null; notice("Fingerprint is not available right now. Try again, or use an app PIN.") }
            }
        }
    }
    /** Android 8–10 only: no screen-lock fallback, so fingerprint setup also asks for a backup phrase. */
    private fun fingerprintPhrase() {
        val screen = start("Add a backup phrase", "Protection Lock") { overview(Tab.PHONE) }
        ui.stepper(2, 2)
        ui.card("Your way back in", "If fingerprints on this phone change, a backup phrase turns fingerprint approval back on. " +
            "Pick something you will remember and write it on paper away from this phone.")
        val phrases = PhraseSet()
        progress()
        val go = go@{
            if (busy) return@go
            ui.clearErrors()
            val words = phrases.collect() ?: run { invalid(); return@go }
            inputs.forEach { it.text.clear() }
            createKeyless(words.first, words.second)
        }
        screen.button("Turn on Protection Lock", true) { go() }
        ui.chain { go() }
    }
    private fun createKeyless(phrases: List<CharArray>, confirmations: List<CharArray>) {
        runSecrets(phrases + confirmations, success = { setupDone(null, phrases.size) }) {
            runBlocking { ProtectionLock.changes.withLock {
                credentials.create(null, null, null, null, phrases, confirmations, UnlockMethods(strong = true)) {
                    ensureActive(); check(keyValid()) // Provisioned and touched just before.
                }
            } }
        }
    }

    /** From an app key: prove the key, then a fresh fingerprint key and one touch replace it. */
    private fun switchToFingerprint(state: ProtectionCredentials.State) {
        val key = keyName(state)
        val screen = start("Switch to fingerprint", "Protection Lock") { overview(Tab.PHONE) }
        screen.text("Enter your app $key, then touch the fingerprint sensor. The $key is deleted and your backup phrases stay.")
        val kinds = listOfNotNull(ParentAuthority.PIN.takeIf { state.pin }, ParentAuthority.PATTERN.takeIf { state.pattern })
        if (kinds.isEmpty()) { overview(); return }
        lateinit var proof: ProofEntry
        fun go() {
            if (busy || prompt != null) return
            ui.clearErrors()
            val secret = proof.collect() ?: return
            val kind = proof.kind
            val held = secret.copyOf(); heldProof = held
            inputs.forEach { it.text.clear() }
            val generation = pageGeneration
            fun drop() { held.fill('\u0000'); if (heldProof === held) heldProof = null }
            runSecrets(listOf(secret), rejected = { proof.rejected() }, failed = ::drop, success = {
                try {
                    prompt = biometrics.prompt(this, "Confirm your fingerprint", "Cancel",
                        onDismiss = { prompt = null; drop(); notice("Nothing changed. Your app $key still works.") }) { ok ->
                        prompt = null
                        if (!ok || !live(generation)) { drop(); notice("Fingerprint was not confirmed. Nothing changed; your app $key still works."); return@prompt }
                        runSecrets(listOf(held), success = { heldProof = null; overview(Tab.PHONE, "Fingerprint approval is on. The app $key was deleted.") }, failed = ::drop) {
                            runBlocking { ProtectionLock.changes.withLock {
                                credentials.commit(ProtectionCredentials.Change("fingerprint-only", held, kind)) { ensureActive(); check(keyValid()) }
                            } }
                        }
                    }
                } catch (_: Exception) { prompt = null; drop(); notice("Fingerprint is not available right now. Nothing changed.") }
            }) {
                // The key is proved before a new fingerprint key exists, so a wrong guess can never re-arm fingerprints.
                credentials.verify(secret, kind)
                biometrics.provision()
            }
        }
        proof = ProofEntry(kinds, "Your app $key") { go() }
        progress()
        screen.button("Continue with fingerprint", true) { go() }
        ui.chain(go = true) { go() }
        proof.focus()
    }

    /** Re-arms fingerprint approval after an enrollment change: a backup phrase, or the screen lock when none is saved. */
    private fun fingerprintReset(state: ProtectionCredentials.State) {
        val toOverview = { if (request != null) overview() else overview(Tab.PHONE) }
        val screen = start("Reset fingerprint approval", home, toOverview)
        val withPhrase = state.phrases > 0
        screen.text(if (withPhrase) "Enter any one of your backup phrases. Fingerprint approval then trusts the fingerprints saved on this phone now."
            else "Confirm with the phone's screen lock. Fingerprint approval then trusts the fingerprints saved on this phone now.")
        val field = if (withPhrase) ui.secretField(ui.card("Backup phrase"), "Any one backup phrase", ProtectionForm.Kind.PHRASE) else null
        progress()
        val generation = pageGeneration
        fun rearmed() {
            // A touch to show it works again; the reset itself is already saved.
            try {
                prompt = biometrics.prompt(this, "Touch to check", "Skip", onDismiss = { prompt = null; toOverview() }) { ok ->
                    prompt = null
                    if (active.get()) {
                        if (request != null) overview()
                        else overview(Tab.PHONE, if (ok) "Fingerprint approval is back on." else "Fingerprint approval is back on. That touch did not match; try again when approving.")
                    }
                }
            } catch (_: Exception) { prompt = null; toOverview() }
        }
        fun withProof(proof: CharArray, kind: ParentAuthority) {
            runSecrets(listOf(proof), success = ::rearmed, rejected = { field?.fail("That phrase did not match.") }) {
                runBlocking { ProtectionLock.changes.withLock {
                    // Proof first: a new fingerprint key is created only after the phrase matched, so a wrong
                    // guess can never re-arm fingerprints someone just added.
                    if (kind == ParentAuthority.PHRASE) credentials.verify(proof, kind)
                    biometrics.provision()
                    credentials.commit(ProtectionCredentials.Change("fingerprint-reset", proof, kind), ::ensureActive)
                } }
            }
        }
        if (field != null) {
            val go = go@{
                if (busy || prompt != null) return@go
                ui.clearErrors()
                val value = field.chars()
                val normalized = ParentSecretRules.normalizePhrase(value)
                if (normalized == null) { value.fill('\u0000'); field.fail("Enter one complete backup phrase (3 to 128 characters)."); return@go }
                normalized.fill('\u0000'); inputs.forEach { it.text.clear() }
                withProof(value, ParentAuthority.PHRASE)
            }
            screen.button("Continue", true) { go() }
            ui.chain(go = true) { go() }
            ui.focus(field)
        } else screen.button("Use phone's screen lock", true) {
            if (busy || prompt != null) return@button
            if (!deviceReady()) { notice("Set up a phone screen lock first, then try again."); return@button }
            try {
                prompt = biometrics.promptBound(this, ParentAuthority.DEVICE_CREDENTIAL, challenges, "fingerprint-reset", "Cancel") { ok ->
                    prompt = null
                    if (!ok || !live(generation)) { notice("The screen lock was not confirmed. Nothing changed."); return@promptBound }
                    withProof(CharArray(0), ParentAuthority.DEVICE_CREDENTIAL)
                }
            } catch (_: Exception) { prompt = null; notice("The phone's screen lock is unavailable right now.") }
        }
        if (withPhrase && request == null) screen.button("Use an app PIN instead") { keyFlow(KeyFlow(Purpose.RESET, state)) }
    }

    // ---- App PIN: step by step, like a phone's own lock setup (ADR-121) ----------------------------------

    private enum class Purpose { SETUP, SWITCH, CHANGE, RESET }

    /** One app-key flow. Secrets live only here between steps and are wiped when the flow ends or the page goes away. */
    private inner class KeyFlow(val purpose: Purpose, val state: ProtectionCredentials.State, fixed: ParentAuthority? = null) {
        var kind = fixed ?: ParentAuthority.PIN
        val choosable = fixed == null
        var proof: CharArray? = null
        var proofKind = ParentAuthority.PIN
        var first: CharArray? = null
        val phrases = purpose == Purpose.SETUP || (purpose == Purpose.SWITCH && state.phrases == 0)
        val proving = purpose == Purpose.CHANGE || purpose == Purpose.RESET
        val total = (if (proving) 1 else 0) + 2 + (if (phrases) 1 else 0)
        fun step(name: String) = when (name) {
            "proof" -> 1
            "create" -> if (proving) 2 else 1
            "confirm" -> if (proving) 3 else 2
            else -> total
        }
        fun dropKey() { first?.fill('\u0000'); first = null }
        fun wipe() { proof?.fill('\u0000'); proof = null; dropKey() }
        val title get() = when (purpose) {
            Purpose.SETUP -> "Create app ${keyName(kind)}"
            Purpose.SWITCH -> "Switch to app ${keyName(kind)}"
            Purpose.CHANGE -> if ((kind == ParentAuthority.PIN) == state.pin && !(state.pin && state.pattern)) "Change app ${keyName(kind)}" else "Use a ${keyName(kind)} instead"
            Purpose.RESET -> "Reset with a backup phrase"
        }
    }
    private var flow: KeyFlow? = null
    private fun endFlow(next: () -> Unit) { flow?.wipe(); flow = null; next() }
    private fun keyFlow(f: KeyFlow) {
        flow?.wipe(); flow = f
        if (f.proving) proofStep(f) else createStep(f)
    }
    private fun leave(@Suppress("UNUSED_PARAMETER") f: KeyFlow) = endFlow { if (request != null) overview() else overview(Tab.APP) }
    /** Back-link name for pages that return to the overview, or to the approval when one is pending. */
    private val home get() = if (request != null) "Parent approval" else "Protection Lock"

    private fun proofStep(f: KeyFlow) {
        f.proof?.fill('\u0000'); f.proof = null
        val screen = start(f.title, home) { leave(f) }
        ui.stepper(f.step("proof"), f.total)
        val kinds = if (f.purpose == Purpose.RESET) listOf(ParentAuthority.PHRASE)
            else listOfNotNull(ParentAuthority.PIN.takeIf { f.state.pin }, ParentAuthority.PATTERN.takeIf { f.state.pattern })
        if (kinds.isEmpty()) { leave(f); return }
        screen.text(if (f.purpose == Purpose.RESET) "Enter any one of your backup phrases. Then you choose a new app PIN or pattern."
            else "First, enter your current app ${keyName(f.state)}.")
        lateinit var proof: ProofEntry
        fun go() {
            if (busy) return
            ui.clearErrors()
            val secret = proof.collect() ?: return
            val kind = proof.kind
            val held = secret.copyOf()
            inputs.forEach { it.text.clear() }
            runSecrets(listOf(secret), rejected = { proof.rejected() }, failed = { held.fill('\u0000') }, success = {
                if (flow === f) { f.proof = held; f.proofKind = kind; createStep(f) } else held.fill('\u0000')
            }) { credentials.verify(secret, kind) }
        }
        proof = ProofEntry(kinds, if (f.purpose == Purpose.RESET) "Backup phrase" else "Current app ${keyName(f.state)}") { go() }
        progress()
        screen.button("Continue", true) { go() }
        ui.chain(go = true) { go() }
        proof.focus()
    }

    private fun createStep(f: KeyFlow) {
        f.dropKey()
        val previous = { if (f.proving) proofStep(f) else leave(f) }
        val screen = start(f.title, if (f.proving) "Back" else home, previous)
        ui.stepper(f.step("create"), f.total)
        if (f.choosable) ui.segmented(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; screen.flexible(this) },
            listOf("PIN", "Pattern"), if (f.kind == ParentAuthority.PIN) 0 else 1) { index ->
            val next = if (index == 0) ParentAuthority.PIN else ParentAuthority.PATTERN
            if (next != f.kind) { f.kind = next; createStep(f) }
        }
        progress()
        if (f.kind == ParentAuthority.PIN) {
            val card = ui.card("Choose a PIN", "4 to 12 digits. It works only in this app, so pick one your child has never seen you type.")
            val field = ui.secretField(card, "New PIN", ProtectionForm.Kind.PIN, "4–12 digits")
            val go = go@{
                if (busy) return@go
                ui.clearErrors()
                val value = field.chars()
                if (!ParentSecretRules.validPin(value)) { value.fill('\u0000'); field.fail("Use 4 to 12 digits."); return@go }
                inputs.forEach { it.text.clear() }
                f.first = value; confirmStep(f)
            }
            screen.button("Continue", true) { go() }
            ui.chain(go = true) { go() }
            ui.focus(field)
        } else {
            val card = ui.card("Draw a pattern", "Connect at least 4 dots. It works only in this app, not on the phone's lock screen.")
            lateinit var clear: () -> Unit
            val status = ui.statusRow(card, "Draw your pattern.", "Clear") { clear() }
            val view = ProtectionPatternView(this, ui.palette)
            patterns.add(view)
            card.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            var recorded: IntArray? = null
            var failed = false
            fun say(text: String, color: Int = ui.palette.text) { status.text = text; status.setTextColor(color) }
            view.onStart = { recorded?.fill(0); recorded = null; failed = false; say("Release when you're done.") }
            view.onComplete = { path ->
                drafts.add(path)
                if (!ParentSecretRules.validPattern(path)) {
                    failed = true; say("Connect at least 4 dots. Try again.", ui.palette.danger); view.showError()
                    view.postDelayed({ if (failed) { view.clear(); say("Draw your pattern.") } }, 1_000)
                } else { recorded = path; say("Pattern recorded. Tap Continue.", ui.palette.accent) }
            }
            clear = { recorded?.fill(0); recorded = null; failed = false; view.clear(); say("Draw your pattern.") }
            screen.button("Continue", true) {
                val path = recorded
                if (path == null) { failed = true; say("Draw a pattern through at least 4 dots first.", ui.palette.danger); return@button }
                f.first = ParentSecretRules.encodePattern(path); confirmStep(f)
            }
        }
    }

    private fun confirmStep(f: KeyFlow) {
        val key = keyName(f.kind)
        val screen = start(if (f.kind == ParentAuthority.PIN) "Confirm your PIN" else "Confirm your pattern", "Back") { createStep(f) }
        ui.stepper(f.step("confirm"), f.total)
        progress()
        val finalLabel = when {
            f.phrases -> "Continue"
            f.purpose == Purpose.SWITCH -> "Confirm with fingerprint"
            else -> "Save new $key"
        }
        val next = { if (f.phrases) phraseStep(f) else commit(f, null) }
        if (f.kind == ParentAuthority.PIN) {
            val card = ui.card("Enter the PIN again", "Type the same PIN to make sure it's right.")
            val field = ui.secretField(card, "Confirm PIN", ProtectionForm.Kind.PIN)
            val go = go@{
                if (busy) return@go
                ui.clearErrors()
                val value = field.chars()
                val matches = f.first?.contentEquals(value) == true
                value.fill('\u0000')
                if (!matches) { field.clear(); field.fail("The PINs don't match. Try again, or go back to choose a new one."); return@go }
                inputs.forEach { it.text.clear() }
                next()
            }
            screen.button(finalLabel, true) { go() }
            ui.chain(go = true) { go() }
            ui.focus(field)
        } else {
            val card = ui.card("Draw the pattern again", "Draw the same pattern to make sure it's right.")
            val status = ui.statusRow(card, "Draw your pattern again.", "Start over") { createStep(f) }
            val view = ProtectionPatternView(this, ui.palette)
            patterns.add(view)
            card.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            var confirmed = false
            var failed = false
            fun say(text: String, color: Int = ui.palette.text) { status.text = text; status.setTextColor(color) }
            view.onStart = { confirmed = false; failed = false; say("Release when you're done.") }
            view.onComplete = { path ->
                drafts.add(path)
                val encoded = if (ParentSecretRules.validPattern(path)) ParentSecretRules.encodePattern(path) else null
                if (encoded != null && f.first?.contentEquals(encoded) == true) {
                    confirmed = true; say("Pattern confirmed. Tap $finalLabel.", ui.palette.accent)
                } else {
                    failed = true; say("Not the same pattern. Try again.", ui.palette.danger); view.showError()
                    view.postDelayed({ if (failed) { view.clear(); say("Draw your pattern again.") } }, 1_000)
                }
                encoded?.fill('\u0000')
            }
            screen.button(finalLabel, true) {
                if (!confirmed) { say("Draw the same pattern to confirm it first.", ui.palette.danger); return@button }
                next()
            }
        }
    }

    private fun phraseStep(f: KeyFlow) {
        val screen = start("Add a backup phrase", "Back") { confirmStep(f) }
        ui.stepper(f.step("phrase"), f.total)
        val intro = ui.card("Your way back in",
            "If the app ${keyName(f.kind)} is forgotten, a backup phrase resets it.\n" +
                "Pick words you'll remember, like a song line, and write them on paper.")
        ui.body(intro, "It shows as you type, so check nobody is watching.\nCapital letters matter; extra spaces do not.", ui.palette.faint)
        val phrases = PhraseSet()
        progress()
        val go = go@{
            if (busy || prompt != null) return@go
            ui.clearErrors()
            val words = phrases.collect() ?: run { invalid(); return@go }
            inputs.forEach { it.text.clear() }
            commit(f, words)
        }
        screen.button(if (f.purpose == Purpose.SWITCH) "Confirm with fingerprint" else "Turn on Protection Lock", true) { go() }
        ui.chain { go() }
    }

    /** Saves the flow's key. Every array handed to the worker is wiped there; the flow keeps nothing afterwards. */
    private fun commit(f: KeyFlow, words: Pair<List<CharArray>, List<CharArray>>?) {
        if (busy || prompt != null) { words?.let { wipe(it.first + it.second) }; return }
        val key = f.first ?: run { words?.let { wipe(it.first + it.second) }; createStep(f); return }
        val pin = f.kind == ParentAuthority.PIN
        val phrases = words?.first.orEmpty(); val confirmations = words?.second.orEmpty()
        val name = keyName(f.kind)
        when (f.purpose) {
            Purpose.SETUP -> {
                val value = key.copyOf(); val repeat = key.copyOf()
                runSecrets(listOf(value, repeat) + phrases + confirmations, success = { endFlow { setupDone(f.kind, phrases.size) } }) {
                    runBlocking { ProtectionLock.changes.withLock {
                        credentials.create(value.takeIf { pin }, repeat.takeIf { pin }, value.takeIf { !pin }, repeat.takeIf { !pin },
                            phrases, confirmations, UnlockMethods.NONE, ::ensureActive)
                    } }
                }
            }
            Purpose.SWITCH -> {
                val generation = pageGeneration
                try {
                    prompt = biometrics.prompt(this, "Confirm it's a parent", "Cancel",
                        onDismiss = { prompt = null; wipe(phrases + confirmations); notice("Nothing changed. Fingerprint still approves.") }) { ok ->
                        prompt = null
                        if (!ok || !live(generation)) { wipe(phrases + confirmations); notice("Fingerprint was not confirmed. Nothing changed."); return@prompt }
                        val value = key.copyOf(); val repeat = key.copyOf()
                        val change = ProtectionCredentials.Change(if (pin) "pin" else "pattern", CharArray(0), ParentAuthority.STRONG_BIOMETRIC,
                            replacement = value, confirm = repeat, replacementKind = f.kind, phrases = phrases, confirmations = confirmations,
                            methods = UnlockMethods.NONE)
                        runSecrets(listOf(value, repeat) + phrases + confirmations,
                            success = { endFlow { overview(Tab.APP, "Your app $name is on. Fingerprint no longer approves.") } }) {
                            runBlocking { ProtectionLock.changes.withLock { credentials.commit(change, ::ensureActive) } }
                        }
                    }
                } catch (_: Exception) {
                    prompt = null; wipe(phrases + confirmations)
                    notice("Fingerprint approval is paused because fingerprints on this phone changed. Use a backup phrase instead.")
                }
            }
            Purpose.CHANGE, Purpose.RESET -> {
                val proof = f.proof ?: run { proofStep(f); return }
                val reset = f.purpose == Purpose.RESET
                val held = proof.copyOf(); val value = key.copyOf(); val repeat = key.copyOf()
                val change = ProtectionCredentials.Change(if (reset) "reset" else if (pin) "pin" else "pattern", held, f.proofKind,
                    replacement = value, confirm = repeat, replacementKind = f.kind)
                runSecrets(listOf(held, value, repeat), success = {
                    endFlow { if (request != null) overview() else overview(Tab.APP, if (reset) "Your new app $name is saved. Use it from now on." else "Your new app $name is saved.") }
                }, rejected = { endFlow { overview(Tab.APP, "Your current entry no longer matched, so nothing changed.") } }) {
                    runBlocking { ProtectionLock.changes.withLock { credentials.commit(change, ::ensureActive) } }
                }
            }
        }
    }

    /** The lock just turned on: say plainly what happens next. [kind] is null for phone fingerprint. */
    private fun setupDone(kind: ParentAuthority?, phrases: Int) {
        val screen = start("Protection Lock is on")
        val fingerprint = kind == null
        val name = kind?.let(::keyName)
        ui.hero(R.drawable.ic_lock_shield, "You're all set",
            if (fingerprint) "From now on, extra time and changes to protection need a parent's fingerprint."
            else "From now on, extra time and changes to protection need your app $name.")
        val how = ui.card("What happens next")
        if (fingerprint) ui.feature(how, R.drawable.ic_lock_fingerprint, "Touch to approve", "When approval is needed, this phone asks for a fingerprint.")
        else ui.feature(how, R.drawable.ic_lock_key, "Enter your app $name", "It only works in this app, not on the phone's lock screen.")
        if (phrases > 0) ui.feature(how, R.drawable.ic_lock_note, "Keep your backup phrase safe",
            if (fingerprint) "It resets fingerprint approval if fingerprints change." else "It resets a forgotten app $name.")
        ui.feature(how, R.drawable.ic_lock_phone, "Change it any time", "In Protect, under Parent approval. Changes need a parent's approval too.")
        if (fingerprint && phrases == 0) {
            val tip = ui.card("Recommended: a backup phrase")
            ui.body(tip, "If anyone adds a fingerprint, approval pauses. With a backup phrase only you can turn it back on; without one, the phone's screen lock does.")
            ui.option(tip, R.drawable.ic_lock_note, "Add a backup phrase", "Takes a minute. Write it on paper.") { withState { change("phrases", it) } }
        }
        screen.button("Done", true) { finish() }
    }

    // ---- Other changes ------------------------------------------------------------------------------

    private fun change(action: String, state: ProtectionCredentials.State) {
        val keyless = state.keyless
        val tab = if (keyless) Tab.PHONE else Tab.APP
        val firstPhrase = keyless && state.phrases == 0 && action == "phrases"
        val screen = start(when (action) {
            "phrases" -> if (firstPhrase) "Add a backup phrase" else "Replace backup phrases"
            "methods" -> "Face and phone code"
            "methods-off" -> "Stop phone unlock approving"
            else -> "Turn off Protection Lock"
        }, home) { if (request != null) finish() else overview(tab) }
        when (action) {
            "disable" -> screen.text("Turning off deletes the ${if (keyless) "fingerprint approval" else "app ${keyName(state)}"} and backup phrases from this phone. Setting up again starts fresh.")
            "methods-off" -> screen.text("Fingerprint, face and the phone's code stop approving. Only your app ${keyName(state)} approves from then on.")
            "phrases" -> if (firstPhrase) screen.text("With a backup phrase, only you can turn fingerprint approval back on if someone adds a fingerprint.")
        }
        val methods = if (action == "methods") ExtraChoices(state.methods) else null
        val proof: ProofEntry? = if (keyless) null else {
            val configured = listOfNotNull(ParentAuthority.PIN.takeIf { state.pin }, ParentAuthority.PATTERN.takeIf { state.pattern })
            val kinds = if (action == "methods-off") configured + ParentAuthority.PHRASE else configured
            if (kinds.isEmpty()) { overview(); return }
            ProofEntry(kinds, "Your app ${keyName(state)}")
        }
        if (keyless) ui.feature(ui.card("Confirm it's a parent"), R.drawable.ic_lock_fingerprint, "Fingerprint",
            "You'll be asked to touch the fingerprint sensor when you confirm.")
        var oldPhrase: ProtectionForm.Field? = null
        var phrases: PhraseSet? = null
        if (action == "phrases") {
            if (state.phrases > 0) oldPhrase = ui.secretField(ui.card("Current backup phrase"), "Any current backup phrase", ProtectionForm.Kind.PHRASE)
            ui.card(if (firstPhrase) "New backup phrase" else "New backup phrases",
                "Pick something you will remember and write it on paper away from this phone. One is enough; up to three.")
            phrases = PhraseSet()
        }
        progress()
        val submit = submit@{
            if (busy || prompt != null) return@submit
            ui.clearErrors()
            val proofValue = if (proof != null) proof.collect() else CharArray(0)
            val old = oldPhrase?.let { field ->
                val value = field.chars()
                val normalized = ParentSecretRules.normalizePhrase(value)
                if (normalized != null) { normalized.fill('\u0000'); value } else { value.fill('\u0000'); field.fail("Enter one complete current backup phrase."); null }
            }
            val words = phrases?.collect()
            val all = listOf(proofValue, old) + words?.let { it.first + it.second }.orEmpty()
            if (proofValue == null || (oldPhrase != null && old == null) || (phrases != null && words == null)) {
                wipe(all); invalid(); return@submit
            }
            val kind = proof?.kind ?: ParentAuthority.STRONG_BIOMETRIC
            inputs.forEach { it.text.clear() }
            val change = ProtectionCredentials.Change(if (action == "methods-off") "methods" else action, proofValue, kind, old ?: CharArray(0),
                phrases = words?.first.orEmpty(), confirmations = words?.second.orEmpty(),
                methods = if (action == "methods-off") UnlockMethods.NONE else methods?.value())
            val done = when (action) {
                "disable" -> "Protection Lock is off."
                "phrases" -> if (firstPhrase) "Backup phrase saved." else "Backup phrases replaced."
                "methods-off" -> "Only your app ${keyName(state)} approves now."
                else -> "Saved."
            }
            val commit = {
                runSecrets(all.filterNotNull(), success = { if (request != null) finish() else overview(if (action == "disable") null else tab, done) },
                    rejected = if (action != "phrases" && proof != null) proof::rejected else null) {
                    runBlocking { ProtectionLock.changes.withLock { credentials.commit(change, ::ensureActive) } }
                }
            }
            if (!keyless) commit()
            else try {
                val generation = pageGeneration
                prompt = biometrics.prompt(this, "Confirm it's a parent", "Cancel", onDismiss = { prompt = null; wipe(all); quiet() }) { ok ->
                    prompt = null
                    if (ok && live(generation)) commit()
                    else { wipe(all); notice("Fingerprint was not confirmed. Nothing changed.") }
                }
            } catch (_: Exception) {
                prompt = null; wipe(all)
                notice("Fingerprint approval is paused because fingerprints on this phone changed. Reset it first.")
            }
        }
        screen.button(if (action == "disable") "Turn off and delete" else "Confirm", true) { submit() }
        ui.chain { submit() }
        if (proof != null && action != "phrases") proof.focus()
    }

    // ---- Approval: the phone's fingerprint first when allowed (ADR-120) -----------------------------

    private fun challenge(label: String, state: ProtectionCredentials.State, token: String, strongKey: Boolean) {
        val keyed = state.pin || state.pattern
        when {
            state.methods.strong && strongKey -> fingerprintChallenge(label, state, token, keyed)
            keyed -> keyChallenge(label, state, token, fingerprint = false)
            else -> pausedChallenge(label, state, token)
        }
    }
    /** One big action: touch the sensor. The prompt opens by itself; an app key (if any) stays one tap away. */
    private fun fingerprintChallenge(label: String, state: ProtectionCredentials.State, token: String, keyed: Boolean) {
        val screen = start("Parent approval", "Cancel", ::finish)
        ui.hero(R.drawable.ic_lock_fingerprint, "Touch the fingerprint sensor", label)
        progress()
        screen.button(if (state.methods.weak) "Use fingerprint or face" else "Use fingerprint", true) { biometricChallenge(label, state, token, keyed) }
        if (keyed) screen.button("Use app ${keyName(state)} instead") { keyChallenge(label, state, token, fingerprint = true) }
        if (state.methods.device && Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
            screen.button("Use phone's unlock code") { boundChallenge(ParentAuthority.DEVICE_CREDENTIAL, state, token, keyed) }
        if (!keyed) screen.button("Fingerprint not working?") { fingerprintReset(state) }
        screen.button("Cancel") { finish() }
        val generation = pageGeneration
        window.decorView.postDelayed({
            if (live(generation) && prompt == null && !busy) biometricChallenge(label, state, token, keyed)
        }, 350)
    }
    private fun keyChallenge(label: String, state: ProtectionCredentials.State, token: String, fingerprint: Boolean) {
        val kinds = listOfNotNull(ParentAuthority.PIN.takeIf { state.pin }, ParentAuthority.PATTERN.takeIf { state.pattern })
        if (kinds.isEmpty()) { finish(); return }
        val screen = start("Parent approval", "Cancel", ::finish)
        screen.text(label)
        lateinit var proof: ProofEntry
        fun authorize() {
            if (busy) return
            ui.clearErrors()
            val secret = proof.collect() ?: return
            val kind = proof.kind
            inputs.forEach { it.text.clear() }
            var revision: String? = null
            runSecrets(listOf(secret), success = { ProtectionLock.finish(token, revision); finish() }, rejected = { proof.rejected() }) {
                revision = credentials.verify(secret, kind)
            }
        }
        proof = ProofEntry(kinds, null) { authorize() }
        progress()
        screen.button("Approve", true) { authorize() }
        if (fingerprint) screen.button(if (state.methods.weak) "Use fingerprint or face" else "Use fingerprint") { biometricChallenge(label, state, token, true) }
        if (state.methods.device && Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
            screen.button("Use phone's unlock code") { boundChallenge(ParentAuthority.DEVICE_CREDENTIAL, state, token, true) }
        screen.button("Forgot ${keyName(state)}?") { keyFlow(KeyFlow(Purpose.RESET, state)) }
        screen.button("Cancel") { finish() }
        ui.chain(go = true) { authorize() }
        proof.focus()
    }
    /** Fingerprint-only, but an enrollment change invalidated the key: reset first (or an allowed phone code approves). */
    private fun pausedChallenge(label: String, state: ProtectionCredentials.State, token: String) {
        val screen = start("Parent approval", "Cancel", ::finish)
        ui.hero(R.drawable.ic_lock_fingerprint, "Fingerprint approval is paused",
            "Fingerprints on this phone changed, so the lock no longer trusts them. A parent can reset it, then approve.")
        screen.text(label)
        progress()
        if (state.methods.device && Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
            screen.button("Use phone's unlock code", true) { boundChallenge(ParentAuthority.DEVICE_CREDENTIAL, state, token, false) }
        screen.button(if (state.phrases > 0) "Reset with a backup phrase" else "Reset with phone's screen lock", !state.methods.device) { fingerprintReset(state) }
        screen.button("Cancel") { finish() }
    }
    private fun fallbackHint(keyed: Boolean) = if (keyed) "Use the app PIN or pattern." else "Try again, or reset it if it keeps failing."
    private fun biometricChallenge(label: String, state: ProtectionCredentials.State, token: String, keyed: Boolean) {
        if (busy || prompt != null) return
        val keyValid = keyValid()
        val weakHardware = runCatching { biometrics.weakAvailable() }.getOrDefault(false)
        when (ParentSecretRules.biometricRoute(state.methods, keyValid, weakHardware)) {
            ParentAuthority.WEAK_BIOMETRIC -> boundChallenge(ParentAuthority.WEAK_BIOMETRIC, state, token, keyed)
            ParentAuthority.STRONG_BIOMETRIC -> try {
                prompt = biometrics.prompt(this, "Parent approval", if (keyed) "Use app ${keyName(state)}" else "Cancel",
                    onNegative = if (keyed) ({ prompt = null; keyChallenge(label, state, token, fingerprint = true) }) else null,
                    onDismiss = { prompt = null; quiet() }) { success ->
                    prompt = null
                    if (success && active.get()) { ProtectionLock.finish(token, state.revision); finish() }
                    else notice("Fingerprint did not approve. ${fallbackHint(keyed)}")
                }
            } catch (_: Exception) {
                prompt = null
                notice(if (keyed) "Fingerprints changed or biometrics are unavailable. Use the app ${keyName(state)}."
                    else "Fingerprints on this phone changed, so approval is paused. Reset it to approve again.")
            }
            else -> notice("Fingerprint is off. ${fallbackHint(keyed)}")
        }
    }
    /** Weak biometrics or the phone screen lock: one-use challenge, then a fresh policy and revision check. */
    private fun boundChallenge(authority: ParentAuthority, state: ProtectionCredentials.State, token: String, keyed: Boolean) {
        if (busy || prompt != null) return
        try {
            prompt = biometrics.promptBound(this, authority, challenges, token, if (keyed) "Use app ${keyName(state)}" else "Cancel") { ok ->
                prompt = null
                if (!ok || !active.get()) { notice("Approval did not complete. ${fallbackHint(keyed)}"); return@promptBound }
                busy = true; quiet(); checking()
                ProtectionLock.worker.execute {
                    val now = runCatching { credentials.state() }.getOrNull()
                    val strongKey = authority != ParentAuthority.WEAK_BIOMETRIC || keyValid()
                    val allowed = now != null && now.enabled && now.revision == state.revision &&
                        ParentSecretRules.authorizesAction(authority, now.methods, strongKey, Build.VERSION.SDK_INT)
                    runOnUiThread {
                        busy = false
                        if (!active.get() || isFinishing || isDestroyed) return@runOnUiThread
                        if (allowed) { ProtectionLock.finish(token, now.revision); finish() }
                        else notice("This unlock method is no longer allowed or fingerprints changed. ${fallbackHint(keyed)}")
                    }
                }
            }
        } catch (_: Exception) { prompt = null; notice("This unlock method is unavailable right now. ${fallbackHint(keyed)}") }
    }

    override fun onStop() {
        active.set(false); prompt?.cancelAuthentication(); prompt = null
        challenges.revoke()
        flow?.wipe(); flow = null
        heldProof?.fill('\u0000'); heldProof = null
        inputs.forEach { it.text.clear() }
        patterns.forEach { it.clear() }; wipeDrafts()
        request?.let { ProtectionLock.finish(it, null) }
        super.onStop()
        finish() // Leaving the page never leaves an authorization or secrets waiting.
    }
    override fun onDestroy() { request?.let { ProtectionLock.finish(it, null) }; super.onDestroy() }
}
