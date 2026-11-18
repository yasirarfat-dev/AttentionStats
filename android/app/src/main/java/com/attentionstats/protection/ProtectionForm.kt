package com.attentionstats.protection

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.attentionstats.R
import com.attentionstats.appearance.AppearanceOwner
import com.attentionstats.appearance.NativePage
import org.attentionstats.protection.ParentSecretRules

/** Native secret-entry building blocks for Protection Lock pages, using the app's generated palette. */
internal class ProtectionForm(private val activity: Activity, private val page: NativePage, private val track: (EditText) -> Unit) {
    val palette = AppearanceOwner.palette(activity)
    private val fields = mutableListOf<Field>()
    private var submit: () -> Unit = {}
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun params(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun shape(fill: Int, stroke: Int, width: Int = 1, radius: Int = 14) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(width), stroke)
    }
    private fun role(view: View, className: String) {
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.className = className
            }
        }
    }

    enum class Kind { PIN, PHRASE }

    /** One labelled secret input with an inline error line. PIN fields are masked with a show/hide toggle. */
    inner class Field(val edit: EditText, private val frame: LinearLayout, private val error: TextView) {
        var failed = false; private set
        /** Copies the current entry without creating a String; the caller clears the array. */
        fun chars(): CharArray { val text: CharSequence = edit.text; return CharArray(text.length) { text[it] } }
        fun fail(message: String) {
            failed = true; error.text = message; error.visibility = View.VISIBLE; paint()
        }
        fun ok() { if (!failed) return; failed = false; error.visibility = View.GONE; paint() }
        fun paint() {
            frame.background = shape(palette.surfaceAlt, when {
                failed -> palette.danger
                edit.hasFocus() -> palette.accent
                else -> palette.border
            }, if (failed || edit.hasFocus()) 2 else 1)
        }
        fun clear() { edit.text.clear(); ok() }
    }

    /** A rounded section card; [title] is an accessibility heading and [step] a small label above it ("Step 1 of 3"). */
    fun card(title: String? = null, detail: String? = null, step: String? = null): LinearLayout {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(14))
            background = shape(palette.surface, palette.border, radius = 22)
        }
        if (step != null) TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
            text = step.uppercase(); textSize = 12f; letterSpacing = .08f; setTextColor(palette.accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            card.addView(this, params(bottom = 4))
        }
        if (title != null) heading(card, title)
        if (detail != null) body(card, detail)
        page.flexible(card)
        return card
    }
    fun heading(parent: LinearLayout, value: String) = TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
        text = value; textSize = 17f; setTextColor(palette.text); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        parent.addView(this, params(bottom = 6))
    }
    fun body(parent: LinearLayout, value: String, color: Int = palette.muted) = TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
        text = value; textSize = 14f; setTextColor(color); setLineSpacing(dp(2).toFloat(), 1.05f)
        parent.addView(this, params(bottom = 8))
    }
    fun warning(parent: LinearLayout, value: String) = body(parent, value, palette.warn)

    /** An icon-led explanation: a tinted rounded badge, a short title and a muted detail. Read as one sentence. */
    fun feature(parent: LinearLayout, icon: Int, title: String, detail: String, status: String? = null): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; isFocusable = true
            contentDescription = listOfNotNull(title, status, detail).joinToString(". ")
        }
        ImageView(activity).apply {
            setImageResource(icon); imageTintList = ColorStateList.valueOf(palette.accent)
            background = shape(palette.accentSoft, palette.accentSoft, radius = 13); setPadding(dp(9), dp(9), dp(9), dp(9))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.addView(this, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14); topMargin = dp(2) })
        }
        val copy = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
            text = title; textSize = 15f; setTextColor(palette.text); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            head.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (status != null) TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
            text = status; textSize = 12f; setTextColor(palette.accent); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = shape(palette.accentSoft, palette.accentSoft, radius = 10); setPadding(dp(9), dp(3), dp(9), dp(3))
            head.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        }
        copy.addView(head)
        TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
            text = detail; textSize = 14f; setTextColor(palette.muted); setLineSpacing(dp(2).toFloat(), 1.05f)
            copy.addView(this, params(top = 2))
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row, params(top = 6, bottom = 10))
        return row
    }

    /** A highlighted recommendation line inside a card; returns the view so callers can update it. */
    fun advice(parent: LinearLayout): TextView = TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
        textSize = 14f; setTextColor(palette.text); setLineSpacing(dp(2).toFloat(), 1.05f)
        background = shape(palette.surfaceAlt, palette.surfaceAlt, radius = 14); setPadding(dp(14), dp(12), dp(14), dp(12))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(this, params(top = 2, bottom = 10))
    }

    fun secretField(parent: LinearLayout, label: String, kind: Kind, hint: String? = null): Field {
        val masked = kind == Kind.PIN
        val labelView = TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
            text = label; textSize = 14f; setTextColor(palette.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        parent.addView(labelView, params(top = 6, bottom = 6))
        val frame = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val edit = EditText(activity).apply {
            contentDescription = label; this.hint = hint
            labelView.labelFor = View.generateViewId().also { id = it }
            setTextColor(palette.text); setHintTextColor(palette.faint); textSize = 17f
            background = null; setPadding(dp(14), dp(12), dp(8), dp(12)); minHeight = dp(52)
            if (masked) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                filters = arrayOf(InputFilter.LengthFilter(ParentSecretRules.PIN_MAX))
                transformationMethod = PasswordTransformationMethod.getInstance()
                letterSpacing = .12f
            } else {
                // Visible text (parents need to see phrases); still no suggestions or keyboard learning.
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                filters = arrayOf(InputFilter.LengthFilter(ParentSecretRules.PHRASE_MAX))
                setHorizontallyScrolling(false); maxLines = 4 // Wraps long phrases while Enter stays an IME action.
            }
            var options = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            if (Build.VERSION.SDK_INT >= 26) {
                options = options or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            imeOptions = options or EditorInfo.IME_ACTION_NEXT
        }
        frame.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (masked) frame.addView(eye(edit, label), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(4) })
        parent.addView(frame, params())
        val error = TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
            textSize = 13f; setTextColor(palette.danger); visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; setPadding(dp(4), dp(4), dp(4), 0)
        }
        parent.addView(error, params(bottom = 6))
        val field = Field(edit, frame, error)
        edit.setOnFocusChangeListener { _, _ -> field.paint() }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { field.ok() }
        })
        edit.setOnEditorActionListener { view, action, _ ->
            if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO) {
                advance(view as EditText); true
            } else false
        }
        field.paint(); fields.add(field); track(edit)
        return field
    }

    private fun eye(edit: EditText, label: String): ImageButton = ImageButton(activity).apply {
        val outValue = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        scaleType = android.widget.ImageView.ScaleType.CENTER
        imageTintList = ColorStateList.valueOf(palette.muted)
        fun render() {
            val hidden = edit.transformationMethod is PasswordTransformationMethod
            setImageResource(if (hidden) R.drawable.ic_protection_show else R.drawable.ic_protection_hide)
            contentDescription = if (hidden) "Show $label" else "Hide $label"
        }
        render()
        setOnClickListener {
            val selection = edit.selectionEnd
            edit.transformationMethod = if (edit.transformationMethod is PasswordTransformationMethod) null else PasswordTransformationMethod.getInstance()
            edit.setSelection(selection.coerceIn(0, edit.length()))
            render()
        }
    }

    /** Single-choice segmented row; returns a function that changes the selection programmatically. */
    fun segmented(parent: LinearLayout, options: List<String>, selected: Int, onSelect: (Int) -> Unit): (Int) -> Unit {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4))
            background = shape(palette.surfaceAlt, palette.border, radius = 26)
        }
        val items = options.map { option ->
            TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
                text = option; textSize = 15f; gravity = Gravity.CENTER; minHeight = dp(48)
                setPadding(dp(8), dp(8), dp(8), dp(8)); isClickable = true; isFocusable = true
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                role(this, "android.widget.RadioButton")
                row.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        fun select(index: Int) {
            items.forEachIndexed { i, item ->
                val on = i == index
                item.isSelected = on
                item.background = if (on) shape(palette.accentFill, palette.accentFill, radius = 22) else null
                item.setTextColor(if (on) palette.onAccent else palette.text)
                if (Build.VERSION.SDK_INT >= 30) item.stateDescription = if (on) "Selected" else "Not selected"
            }
        }
        items.forEachIndexed { i, item -> item.setOnClickListener { select(i); onSelect(i); refreshIme() } }
        select(selected)
        parent.addView(row, params(bottom = 12))
        return { index -> select(index) }
    }

    /** A labelled switch with a muted detail line and an optional warning, grouped so callers can hide it as one (`switch.parent`). */
    fun toggle(parent: LinearLayout, title: String, detail: String, caution: String?, checked: Boolean, enabled: Boolean,
        unavailable: String?): Switch {
        val switch = Switch(activity).apply {
            text = title; textSize = 16f; minHeight = dp(52); isChecked = checked && enabled; isEnabled = enabled
            setTextColor(palette.text)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(palette.accentFill, palette.track))
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(palette.onAccent, palette.muted))
        }
        val group = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(group, params())
        group.addView(switch, params(top = 4))
        body(group, detail)
        if (caution != null) warning(group, caution)
        if (!enabled && unavailable != null) body(group, unavailable, palette.faint)
        return switch
    }

    /** Low-emphasis inline action inside a card (kept distinct from page buttons). */
    fun link(parent: LinearLayout, label: String, action: () -> Unit) = TextView(activity).apply {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED; hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE;
        text = label; textSize = 15f; setTextColor(palette.accent); minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); isClickable = true; isFocusable = true
        setOnClickListener { action() }; role(this, "android.widget.Button")
        parent.addView(this, params())
    }

    private fun shown(view: View): Boolean {
        var current: View? = view
        while (current != null) { if (current.visibility != View.VISIBLE) return false; current = current.parent as? View }
        return true
    }
    private fun advance(from: EditText) {
        val index = fields.indexOfFirst { it.edit === from }
        val next = fields.drop(index + 1).firstOrNull { shown(it.edit) }
        if (next != null) next.edit.requestFocus()
        else { hideKeyboard(from); submit() }
    }
    fun hideKeyboard(view: View) {
        (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken, 0)
    }
    /** The last visible field gets Done (or Go for a single-entry challenge) and submits; others get Next. */
    fun chain(go: Boolean = false, action: () -> Unit) { submit = action; refreshIme(go) }
    private var lastGo = false
    fun refreshIme(go: Boolean = lastGo) {
        lastGo = go
        val visible = fields.filter { shown(it.edit) }
        val manager = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
        for (field in fields) {
            val last = field === visible.lastOrNull()
            val action = if (!last) EditorInfo.IME_ACTION_NEXT else if (go) EditorInfo.IME_ACTION_GO else EditorInfo.IME_ACTION_DONE
            val options = (field.edit.imeOptions and EditorInfo.IME_MASK_ACTION.inv()) or action
            if (options != field.edit.imeOptions) {
                field.edit.imeOptions = options
                if (field.edit.hasFocus()) manager?.restartInput(field.edit)
            }
        }
    }
    fun clearErrors() = fields.forEach { it.ok() }
    fun focusFirstError(): Boolean = fields.firstOrNull { it.failed && shown(it.edit) }?.edit?.requestFocus() == true
}
