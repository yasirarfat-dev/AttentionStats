package com.attentionstats.protection

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
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

/** Secrets remain in native secure-window controls; JS can only open this page. */
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        request = intent.getStringExtra("request")
        if (savedInstanceState != null) { finish(); return } // No restored challenge or secret.
        overview()
    }
    private fun ensureActive() { check(active.get() && !isFinishing && !isDestroyed) }
    private fun wipeDrafts() { drafts.forEach { it.fill(0) }; drafts.clear() }
    private fun start(title: String): NativePage {
        inputs.forEach { it.text.clear() }; inputs.clear()
        patterns.forEach { it.clear() }; patterns.clear()
        wipeDrafts(); challenges.revoke()
        return NativePage(this).also {
            page = it; form = ProtectionForm(this, it) { edit -> inputs.add(edit) }
            it.text(title, 24f)
            message = null
        }
    }
    private fun progress() {
        message = TextView(this).apply {
            textSize = 15f; visibility = View.GONE
            setTextColor(ui.palette.text)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { checkNotNull(page).flexible(it) }
    }
    private fun notice(value: String) { message?.apply { text = value; visibility = View.VISIBLE } }
    private fun invalid() { notice("Check the highlighted entries."); ui.focusFirstError() }
    private fun wipe(values: List<CharArray?>) = values.forEach { it?.fill('\u0000') }
    private fun keyValid() = runCatching { biometrics.keyValid() }.getOrDefault(false)
    private fun label(kind: ParentAuthority) = when (kind) {
        ParentAuthority.PIN -> "PIN"; ParentAuthority.PATTERN -> "Pattern"; else -> "Backup phrase"
    }
    private fun runSecrets(secrets: List<CharArray>, success: () -> Unit = ::overview, rejected: () -> Unit = {}, operation: () -> Unit) {
        if (busy) { secrets.forEach { it.fill('\u0000') }; return }
        busy = true; notice("Checking securely…")
        ProtectionLock.worker.execute {
            var reason: String? = null
            val problem = try { ensureActive(); operation(); null } catch (failure: Exception) {
                reason = (failure as? ProtectionCredentials.Denied)?.reason
                when (reason) {
                    "TRY_LATER" -> "Too many attempts. Wait before trying again. Reopening will not reset the wait."
                    "INCORRECT_SECRET" -> "The parent key or backup phrase did not match."
                    "NOT_CONFIGURED" -> "Protection Lock is not set up."
                    else -> "Could not complete this change. Check the entries and try again. Saved protection remains in effect."
                }
            } finally { secrets.forEach { it.fill('\u0000') } }
            runOnUiThread {
                busy = false
                if (active.get() && !isFinishing && !isDestroyed) {
                    if (problem == null) success() else { notice(problem); if (reason == "INCORRECT_SECRET") rejected() }
                }
            }
        }
    }

    /** Draws the app pattern; for a new secret the same pattern must be drawn twice. Paths are wiped on leaving. */
    private inner class PatternEntry(parent: LinearLayout, private val confirm: Boolean, private val drawn: (IntArray) -> Unit = {}) {
        private val status = ui.body(parent, "", ui.palette.text)
        val view = ProtectionPatternView(this@ProtectionLockActivity, ui.palette)
        private var first: IntArray? = null
        var confirmed: IntArray? = null; private set
        private var failed = false
        init {
            status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            patterns.add(view)
            parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.onStart = { if (failed) { failed = false; say(instruction()) } }
            view.onComplete = { path -> drafts.add(path); accept(path) }
            if (confirm) ui.link(parent, "Start pattern again") { restart() }
            say(instruction())
        }
        private fun instruction() = when {
            !confirm -> "Draw the app pattern."
            first != null -> "Draw the same pattern again to confirm."
            else -> "Draw a pattern through at least 4 dots."
        }
        fun say(text: String, color: Int = ui.palette.text) { status.text = text; status.setTextColor(color) }
        private fun accept(path: IntArray) {
            failed = false
            if (!ParentSecretRules.validPattern(path)) { path.fill(0); fail("Connect at least 4 dots. Try again."); return }
            if (!confirm) { view.clear(); drawn(path); return }
            val previous = first
            when {
                previous == null -> { first = path; view.clear(); say(instruction()) }
                previous.contentEquals(path) -> {
                    path.fill(0); confirmed = previous; view.clear(); view.inputEnabled = false
                    say("Pattern confirmed. It is only used by this app.", ui.palette.accent)
                }
                else -> { path.fill(0); previous.fill(0); first = null; fail("The patterns did not match. Draw a new pattern.") }
            }
        }
        fun fail(text: String) {
            failed = true; say(text, ui.palette.danger); view.showError()
            view.postDelayed({ if (failed) view.clear() }, 900)
            status.requestRectangleOnScreen(android.graphics.Rect(0, 0, status.width, view.bottom - status.top))
        }
        fun restart() {
            first?.fill(0); first = null; confirmed = null; failed = false
            view.clear(); view.inputEnabled = true; say(instruction())
        }
    }

    /** Existing-secret proof: PIN, app pattern or a recovery phrase, as the action and configuration allow. */
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
                    ParentAuthority.PIN -> pin = ui.secretField(section, if (title != null) "Current parental PIN" else "Parental PIN", ProtectionForm.Kind.PIN)
                    ParentAuthority.PATTERN -> pattern = PatternEntry(section, false) { drawn ->
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
        /** The proof secret (encoded for a pattern), or null after showing an inline error. Caller clears. */
        fun collect(): CharArray? = when (kind) {
            ParentAuthority.PIN -> {
                val field = checkNotNull(pin); val value = field.chars()
                if (ParentSecretRules.validPin(value)) value else { value.fill('\u0000'); field.fail("Enter the 4–12 digit parental PIN."); null }
            }
            ParentAuthority.PATTERN -> {
                val drawn = path; path = null
                if (drawn != null && ParentSecretRules.validPattern(drawn)) ParentSecretRules.encodePattern(drawn).also { drawn.fill(0) }
                else { checkNotNull(pattern).fail("Draw the app pattern first."); null }
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
                ParentAuthority.PIN -> pin?.fail("That PIN did not match.")
                ParentAuthority.PATTERN -> pattern?.fail("That pattern did not match.")
                else -> phrase?.fail("That phrase did not match.")
            }
        }
    }

    private class NewSecrets(val pin: CharArray?, val pinConfirm: CharArray?, val pattern: CharArray?, val patternConfirm: CharArray?) {
        fun all() = listOfNotNull(pin, pinConfirm, pattern, patternConfirm)
    }

    /** A new PIN or app pattern, confirmed, in one card. With two [modes] the parent chooses which. */
    private inner class NewSecretEntry(private val modes: List<Set<ParentAuthority>>, step: String? = null) {
        private var mode = modes.first()
        private var pinCard: LinearLayout? = null
        private var patternCard: LinearLayout? = null
        private var pin: ProtectionForm.Field? = null
        private var pinConfirm: ProtectionForm.Field? = null
        private var pattern: PatternEntry? = null
        init {
            val choosing = modes.size > 1
            val only = modes.first().first()
            val card = ui.card(
                if (choosing) "Parent key" else if (only == ParentAuthority.PIN) "New parental PIN" else "New app pattern",
                "Works only inside Attention Stats. It is not the code that unlocks this phone, so pick one your child has never seen you use.",
                step)
            if (choosing) ui.segmented(card, modes.map { if (it.first() == ParentAuthority.PIN) "PIN" else "Pattern" }, 0) { select(it) }
            val box = { LinearLayout(this@ProtectionLockActivity).apply { orientation = LinearLayout.VERTICAL; card.addView(this) } }
            if (modes.any { ParentAuthority.PIN in it }) {
                val section = box()
                pin = ui.secretField(section, "New parental PIN", ProtectionForm.Kind.PIN, "4–12 digits")
                pinConfirm = ui.secretField(section, "Type it again", ProtectionForm.Kind.PIN)
                pinCard = section
            }
            if (modes.any { ParentAuthority.PATTERN in it }) {
                val section = box()
                pattern = PatternEntry(section, true)
                patternCard = section
            }
            select(0)
        }
        private fun select(index: Int) {
            mode = modes[index]
            pinCard?.visibility = if (ParentAuthority.PIN in mode) View.VISIBLE else View.GONE
            patternCard?.visibility = if (ParentAuthority.PATTERN in mode) View.VISIBLE else View.GONE
        }
        fun collect(): NewSecrets? {
            var ok = true
            var entered: CharArray? = null; var repeated: CharArray? = null
            var encoded: CharArray? = null; var encodedConfirm: CharArray? = null
            if (ParentAuthority.PIN in mode) {
                val field = checkNotNull(pin); val confirm = checkNotNull(pinConfirm)
                entered = field.chars(); repeated = confirm.chars()
                if (!ParentSecretRules.validPin(entered)) { field.fail("Use 4 to 12 digits."); ok = false }
                else if (!entered.contentEquals(repeated)) { confirm.fail("Does not match the PIN above."); ok = false }
            }
            if (ParentAuthority.PATTERN in mode) {
                val entry = checkNotNull(pattern)
                val path = entry.confirmed
                if (path == null) { entry.restart(); entry.fail("Draw your pattern, then draw it again to confirm."); ok = false }
                else { encoded = ParentSecretRules.encodePattern(path); encodedConfirm = ParentSecretRules.encodePattern(path) }
            }
            val result = NewSecrets(entered, repeated, encoded, encodedConfirm)
            if (!ok) { wipe(result.all()); return null }
            return result
        }
    }

    /** Three recovery phrase cards, each grouping the phrase with its confirmation. Entries are visible text. */
    private inner class PhraseSet {
        private val pairs = (1..ParentSecretRules.PHRASE_COUNT).map { n ->
            val card = ui.card("Backup phrase $n")
            ui.secretField(card, "Phrase $n", ProtectionForm.Kind.PHRASE) to ui.secretField(card, "Confirm phrase $n", ProtectionForm.Kind.PHRASE)
        }
        fun collect(): Pair<List<CharArray>, List<CharArray>>? {
            val phrases = pairs.map { it.first.chars() }
            val confirmations = pairs.map { it.second.chars() }
            val problems = ParentSecretRules.phraseProblems(phrases, confirmations)
            problems.forEachIndexed { i, problem ->
                when (problem) {
                    ParentSecretRules.PhraseProblem.LENGTH -> pairs[i].first.fail("Use 3 to 128 characters.")
                    ParentSecretRules.PhraseProblem.DUPLICATE -> pairs[i].first.fail("Use a phrase different from the ones above.")
                    ParentSecretRules.PhraseProblem.MISMATCH -> pairs[i].second.fail("Does not match phrase ${i + 1}.")
                    null -> {}
                }
            }
            if (problems.any { it != null }) { wipe(phrases + confirmations); return null }
            return phrases to confirmations
        }
    }

    /** Parent-allowed convenience methods. Changing them always needs a knowledge proof on the same page. */
    private inner class MethodChoices(initial: UnlockMethods, strongKeyValid: Boolean, parent: LinearLayout? = null) {
        private val strongHardware = runCatching { biometrics.available() }.getOrDefault(false)
        private val weakHardware = runCatching { biometrics.weakAvailable() }.getOrDefault(false)
        private val deviceReady = runCatching { biometrics.deviceAvailable() }.getOrDefault(false)
        private val strong: Switch
        private val weak: Switch
        private val device: Switch
        init {
            val card = parent ?: ui.card("Phone unlock shortcuts", "Optional. Only turn these on if your child cannot unlock this phone with their own fingerprint, face or code: Android does not tell apps whose finger or face was used.")
            strong = ui.toggle(card, "Fingerprint", "Any fingerprint saved on this phone will work.",
                null, initial.strong, strongHardware, "No fingerprint is saved on this phone.")
            if (initial.strong && strongHardware && !strongKeyValid) ui.warning(card, "Fingerprints on this phone changed since this was turned on. Save to verify a parent fingerprint again.")
            weak = ui.toggle(card, "Face unlock", "Any face saved on this phone will work. Needs Fingerprint on.",
                "Less secure: some phones accept a photo or a lookalike.", initial.weak, weakHardware && strongHardware,
                if (!weakHardware) "No face is saved on this phone." else null)
            device = ui.toggle(card, "Phone's PIN, pattern or password", "The code that unlocks this phone.",
                "Only if your child does not know it.", initial.device, deviceReady,
                if (Build.VERSION.SDK_INT < ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK) "Needs Android 11 or later." else "Set up a phone screen lock first.")
            strong.setOnCheckedChangeListener { _, on -> if (!on) weak.isChecked = false; weak.isEnabled = on && weakHardware }
            weak.isEnabled = strong.isChecked && weakHardware
        }
        /** Applies the setup answer: biometrics only when the child cannot unlock this phone with their own. */
        fun recommend(childEnrolled: Boolean) {
            strong.isChecked = !childEnrolled && strongHardware
            if (childEnrolled) { weak.isChecked = false; device.isChecked = false }
            // A child's own biometrics would approve too, so those switches are not even offered.
            val visibility = if (childEnrolled) View.GONE else View.VISIBLE
            (strong.parent as? View)?.visibility = visibility
            (weak.parent as? View)?.visibility = visibility
        }
        fun value() = UnlockMethods(strong.isChecked, weak.isChecked, device.isChecked).normalized()
    }

    private fun overview() {
        ProtectionLock.worker.execute {
            val state = runCatching { credentials.state() }
            val strongKey = state.getOrNull()?.biometric == true && keyValid()
            runOnUiThread {
                if (!active.get() || isFinishing || isDestroyed) return@runOnUiThread
                val current = state.getOrNull()
                val token = request
                if (token != null) {
                    val label = ProtectionLock.label(token)
                    if (label == null || current == null || !current.enabled) { finish(); return@runOnUiThread }
                    challenge(label, current, token); return@runOnUiThread
                }
                val screen = start("Protection Lock")
                if (current == null) {
                    screen.text("Protection Lock is unavailable. Existing protection has not been reset.")
                    screen.button("Done") { finish() }; return@runOnUiThread
                }
                if (!current.enabled) {
                    val intro = ui.card("Keep protection in a parent's hands", "When it is on, extra time, ending focus early and changes to limits, schedules or blocked apps need a parent's approval first.")
                    ui.feature(intro, R.drawable.ic_lock_key, "Parent key", "A PIN or pattern that only works in this app. Your child should not know it.")
                    ui.feature(intro, R.drawable.ic_lock_phone, "Phone unlock shortcuts", "Optional fingerprint or face, if your child's are not saved on this phone.")
                    ui.feature(intro, R.drawable.ic_lock_note, "Backup phrases", "Three phrases you write down, in case the parent key is forgotten.")
                    ui.body(intro, "Android Settings, uninstall and emergency calls always stay available.", ui.palette.faint)
                    if (Build.VERSION.SDK_INT >= 26) screen.button("Set up Protection Lock", true) { setup() }
                    else screen.text("Protection Lock requires Android 8 or later. Other protection tools remain available.")
                } else {
                    val methods = current.methods
                    val status = ui.card("Protection Lock is on", "Extra time, ending focus early and changes to protection need a parent's approval. Each approval covers one action.")
                    ui.feature(status, R.drawable.ic_lock_key, "Parent key",
                        "Works only in this app. Not the phone's own unlock.",
                        when { current.pin && current.pattern -> "PIN + pattern"; current.pin -> "PIN"; else -> "Pattern" })
                    val shortcuts = listOfNotNull("Fingerprint".takeIf { methods.strong }, "Face".takeIf { methods.weak }, "Phone code".takeIf { methods.device })
                    ui.feature(status, R.drawable.ic_lock_phone, "Phone unlock shortcuts",
                        if (methods.strong && !strongKey) "Fingerprints changed on this phone. Open shortcuts to verify again."
                        else if (shortcuts.isEmpty()) "Off. Only the parent key approves." else "Anything saved on this phone works, including a child's.",
                        if (shortcuts.isEmpty()) "Off" else shortcuts.joinToString(" + "))
                    ui.feature(status, R.drawable.ic_lock_note, "Backup phrases", "Any one resets a forgotten parent key.", "Set")
                    screen.text("Parent key", 20f)
                    if (current.pin && current.pattern) {
                        // Records from earlier builds may hold both; saving either one keeps only that one.
                        screen.button("Keep a parental PIN only") { change("pin", current) }
                        screen.button("Keep an app pattern only") { change("pattern", current) }
                    } else if (current.pin) {
                        screen.button("Change parental PIN") { change("pin", current) }
                        screen.button("Use a pattern instead") { change("pattern", current) }
                    } else {
                        screen.button("Change app pattern") { change("pattern", current) }
                        screen.button("Use a PIN instead") { change("pin", current) }
                    }
                    screen.button("Forgot parent key") { change("reset", current) }
                    screen.text("Shortcuts and backup", 20f)
                    screen.button("Phone unlock shortcuts") { change("methods", current) }
                    screen.button("Replace backup phrases") { change("phrases", current) }
                    screen.button("Turn off Protection Lock") { change("disable", current) }
                }
                screen.button("Done") { finish() }
            }
        }
    }
    private fun recoveryHelp(parent: LinearLayout) {
        ui.body(parent, "Three different phrases, like a line from a song or an old address. Write them on paper and keep them away from this phone. Any one of them resets a forgotten parent key.", ui.palette.text)
        ui.body(parent, "Phrases show as you type, so check nobody is watching. Capital letters matter; extra spaces do not.")
    }
    private fun setup() {
        val screen = start("Set up Protection Lock")
        screen.text("Three short steps. Only a parent should do this.")
        val secret = NewSecretEntry(listOf(setOf(ParentAuthority.PIN), setOf(ParentAuthority.PATTERN)), "Step 1 of 3")

        val shortcuts = ui.card("Phone unlock shortcuts", "Can your child unlock this phone with their own fingerprint or face?", "Step 2 of 3")
        lateinit var methods: MethodChoices
        lateinit var advice: TextView
        fun answer(childEnrolled: Boolean) {
            methods.recommend(childEnrolled)
            advice.text = if (childEnrolled) "Fingerprint and face stay off: Android accepts any fingerprint or face saved on this phone, so your child's would approve too. Your parent key does the job."
                else "Fingerprint is on for one-touch approval. Your parent key still works whenever fingerprint does not."
        }
        ui.segmented(shortcuts, listOf("Yes, or not sure", "No, only mine"), 0) { answer(it == 0) }
        advice = ui.advice(shortcuts)
        methods = MethodChoices(UnlockMethods(strong = false), strongKeyValid = true, shortcuts)
        answer(true)

        val backup = ui.card("Backup phrases", null, "Step 3 of 3")
        recoveryHelp(backup)
        val phrases = PhraseSet()
        progress()
        screen.button("Turn on Protection Lock", true) { submitSetup(secret, phrases, methods) }
        screen.button("Cancel") { if (busy) finish() else overview() }
        ui.chain { submitSetup(secret, phrases, methods) }
    }
    private fun submitSetup(secret: NewSecretEntry, phrases: PhraseSet, methods: MethodChoices) {
        if (busy) return
        ui.clearErrors()
        val chosen = secret.collect()
        val words = phrases.collect()
        if (chosen == null || words == null) {
            wipe(chosen?.all().orEmpty()); wipe(words?.let { it.first + it.second }.orEmpty())
            invalid(); return
        }
        val wanted = methods.value()
        val final = wanted.copy(strong = wanted.strong && runCatching { biometrics.available() }.getOrDefault(false)).normalized()
        inputs.forEach { it.text.clear() }
        runSecrets(chosen.all() + words.first + words.second) { runBlocking { ProtectionLock.changes.withLock {
            credentials.create(chosen.pin, chosen.pinConfirm, chosen.pattern, chosen.patternConfirm, words.first, words.second, final) {
                ensureActive(); if (final.strong) biometrics.provision()
            }
        } } }
    }
    private fun change(action: String, state: ProtectionCredentials.State) {
        val replacing = action == "pin" || action == "pattern"
        val switching = (action == "pin" && !state.pin) || (action == "pattern" && !state.pattern)
        val screen = start(when (action) {
            "reset" -> "Forgot parent key"; "phrases" -> "Replace backup phrases"
            "pin" -> if (switching) "Use a PIN instead" else "Change parental PIN"
            "pattern" -> if (switching) "Use a pattern instead" else "Change app pattern"
            "remove-pin" -> "Remove parental PIN"; "remove-pattern" -> "Remove app pattern"
            "methods" -> "Phone unlock shortcuts"; else -> "Turn off Protection Lock"
        })
        when (action) {
            "reset" -> screen.text("Enter any one of your three backup phrases, then choose a new parent key. Phone unlock shortcuts turn off; turn them on again afterwards if wanted.")
            "disable" -> screen.text("Turning off deletes the parent key and backup phrases from this phone and turns off every shortcut. Setting up again needs new ones.")
            "remove-pin" -> screen.text("Only the parental PIN is deleted. The app pattern and recovery phrases stay.")
            "remove-pattern" -> screen.text("Only the app pattern is deleted. The parental PIN and recovery phrases stay.")
            "pin", "pattern" -> if (replacing) screen.text((if (switching) "The new ${if (action == "pin") "PIN replaces the app pattern" else "pattern replaces the parental PIN"}. " else "") +
                "Saving it turns off phone unlock shortcuts. You can turn them on again afterwards.")
        }
        val configured = listOfNotNull(ParentAuthority.PIN.takeIf { state.pin }, ParentAuthority.PATTERN.takeIf { state.pattern })
        val kinds = when (action) { "reset" -> listOf(ParentAuthority.PHRASE); "methods" -> configured + ParentAuthority.PHRASE; else -> configured }
        if (kinds.isEmpty()) { overview(); return }
        val strongKey = state.biometric && keyValid()
        var methods: MethodChoices? = null
        if (action == "methods") methods = MethodChoices(state.methods, strongKey)
        val proof = ProofEntry(kinds, if (action == "reset") "Backup phrase" else "Current parent key")
        var oldPhrase: ProtectionForm.Field? = null
        var phrases: PhraseSet? = null
        var secret: NewSecretEntry? = null
        when (action) {
            "phrases" -> {
                oldPhrase = ui.secretField(ui.card("Current backup phrase"), "Any current backup phrase", ProtectionForm.Kind.PHRASE)
                recoveryHelp(ui.card("New backup phrases")); phrases = PhraseSet()
            }
            "pin" -> secret = NewSecretEntry(listOf(setOf(ParentAuthority.PIN)))
            "pattern" -> secret = NewSecretEntry(listOf(setOf(ParentAuthority.PATTERN)))
            "reset" -> secret = NewSecretEntry(listOf(setOf(ParentAuthority.PIN), setOf(ParentAuthority.PATTERN)))
        }
        progress()
        val submit = submit@{
            if (busy) return@submit
            ui.clearErrors()
            val proofValue = proof.collect()
            val old = oldPhrase?.let { field ->
                val value = field.chars()
                val normalized = ParentSecretRules.normalizePhrase(value)
                if (normalized != null) { normalized.fill('\u0000'); value } else { value.fill('\u0000'); field.fail("Enter one complete current backup phrase."); null }
            }
            val fresh = secret?.collect()
            val words = phrases?.collect()
            if (proofValue == null || (oldPhrase != null && old == null) || (secret != null && fresh == null) || (phrases != null && words == null)) {
                wipe(listOf(proofValue, old) + fresh?.all().orEmpty() + words?.let { it.first + it.second }.orEmpty())
                invalid(); return@submit
            }
            val kind = proof.kind
            val desired = methods?.value()
            inputs.forEach { it.text.clear() }
            if (desired != null && desired.strong && (!state.biometric || !keyValid())) { enableStrong(proofValue, kind, desired); return@submit }
            val change = ProtectionCredentials.Change(action, proofValue, kind, old ?: CharArray(0),
                fresh?.pin ?: fresh?.pattern ?: CharArray(0), fresh?.pinConfirm ?: fresh?.patternConfirm ?: CharArray(0),
                if (fresh?.pattern != null) ParentAuthority.PATTERN else ParentAuthority.PIN,
                words?.first.orEmpty(), words?.second.orEmpty(), desired)
            runSecrets(listOfNotNull(proofValue, old) + fresh?.all().orEmpty() + words?.let { it.first + it.second }.orEmpty(),
                success = { if (request != null) finish() else overview() },
                rejected = { if (action != "phrases") proof.rejected() }) {
                runBlocking { ProtectionLock.changes.withLock { credentials.commit(change, ::ensureActive) } }
            }
        }
        screen.button(if (action == "disable") "Turn off and delete secrets" else "Confirm change", true) { submit() }
        screen.button("Cancel") { if (busy || request != null) finish() else overview() }
        ui.chain { submit() }
    }
    /** Turning strong biometrics on: disable the old shortcut under knowledge authority, rotate the key, verify a touch. */
    private fun enableStrong(proof: CharArray, kind: ParentAuthority, desired: UnlockMethods) {
        val held = proof.copyOf()
        heldProof = held
        runSecrets(listOf(proof), success = {
            try {
                prompt = biometrics.prompt(this, "Verify a parent fingerprint") { success ->
                    prompt = null
                    if (!success || !active.get()) {
                        held.fill('\u0000'); heldProof = null
                        notice("Fingerprint was not turned on and other unlock method changes were not saved. The parent key still works.")
                    } else runSecrets(listOf(held), success = { heldProof = null; if (request != null) finish() else overview() }) {
                        runBlocking { ProtectionLock.changes.withLock {
                            credentials.commit(ProtectionCredentials.Change("biometric-on", held, kind, methods = desired), ::ensureActive)
                        } }
                    }
                }
            } catch (_: Exception) { held.fill('\u0000'); heldProof = null; notice("Strong biometrics are unavailable. Use the parent key.") }
        }) {
            try {
                runBlocking { ProtectionLock.changes.withLock {
                    credentials.commit(ProtectionCredentials.Change("biometric-off", held, kind), ::ensureActive)
                    biometrics.provision()
                } }
            } catch (failure: Exception) { held.fill('\u0000'); throw failure }
        }
    }
    private fun challenge(label: String, state: ProtectionCredentials.State, token: String) {
        val kinds = listOfNotNull(ParentAuthority.PIN.takeIf { state.pin }, ParentAuthority.PATTERN.takeIf { state.pattern })
        if (kinds.isEmpty()) { finish(); return }
        val screen = start("Parent approval")
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
        val methods = state.methods
        if (methods.strong) screen.button(if (methods.weak) "Use fingerprint or face" else "Use fingerprint") { biometricChallenge(state, token) }
        if (methods.device && Build.VERSION.SDK_INT >= ParentSecretRules.DEVICE_CREDENTIAL_MIN_SDK)
            screen.button("Use phone's unlock code") { boundChallenge(ParentAuthority.DEVICE_CREDENTIAL, state, token) }
        screen.button("Forgot parent key") { change("reset", state) }
        screen.button("Cancel") { finish() }
        ui.chain(go = true) { authorize() }
    }
    private fun biometricChallenge(state: ProtectionCredentials.State, token: String) {
        if (busy || prompt != null) return
        val keyValid = keyValid()
        val weakHardware = runCatching { biometrics.weakAvailable() }.getOrDefault(false)
        when (ParentSecretRules.biometricRoute(state.methods, keyValid, weakHardware)) {
            ParentAuthority.WEAK_BIOMETRIC -> boundChallenge(ParentAuthority.WEAK_BIOMETRIC, state, token)
            ParentAuthority.STRONG_BIOMETRIC -> try {
                prompt = biometrics.prompt(this) { success ->
                    prompt = null
                    if (success && active.get()) { ProtectionLock.finish(token, state.revision); finish() }
                    else notice("Biometric authorization did not complete. Use the parent key.")
                }
            } catch (_: Exception) { notice("Fingerprints changed or biometrics are unavailable. Use the parent key, then turn fingerprint on again in Protect.") }
            else -> notice("Biometrics are off. Use the parent key.")
        }
    }
    /** Weak biometrics or the phone screen lock: one-use challenge, then a fresh policy and revision check. */
    private fun boundChallenge(authority: ParentAuthority, state: ProtectionCredentials.State, token: String) {
        if (busy || prompt != null) return
        try {
            prompt = biometrics.promptBound(this, authority, challenges, token) { ok ->
                prompt = null
                if (!ok || !active.get()) { notice("Authorization did not complete. Use the parent key."); return@promptBound }
                busy = true; notice("Checking securely…")
                ProtectionLock.worker.execute {
                    val now = runCatching { credentials.state() }.getOrNull()
                    val strongKey = authority != ParentAuthority.WEAK_BIOMETRIC || keyValid()
                    val allowed = now != null && now.enabled && now.revision == state.revision &&
                        ParentSecretRules.authorizesAction(authority, now.methods, strongKey, Build.VERSION.SDK_INT)
                    runOnUiThread {
                        busy = false
                        if (!active.get() || isFinishing || isDestroyed) return@runOnUiThread
                        if (allowed) { ProtectionLock.finish(token, now.revision); finish() }
                        else notice("This unlock method is no longer allowed or fingerprints changed. Use the parent key.")
                    }
                }
            }
        } catch (_: Exception) { prompt = null; notice("This unlock method is unavailable right now. Use the parent key.") }
    }
    override fun onStop() {
        active.set(false); prompt?.cancelAuthentication(); prompt = null
        challenges.revoke()
        heldProof?.fill('\u0000'); heldProof = null
        inputs.forEach { it.text.clear() }
        patterns.forEach { it.clear() }; wipeDrafts()
        request?.let { ProtectionLock.finish(it, null) }
        super.onStop()
        finish() // Leaving the page never leaves an authorization or secrets waiting.
    }
    override fun onDestroy() { request?.let { ProtectionLock.finish(it, null) }; super.onDestroy() }
}
