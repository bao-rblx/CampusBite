package com.campusbite.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.campusbite.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

/** Apple spring-like tactile touch feedback animation. */
@android.annotation.SuppressLint("ClickableViewAccessibility")
fun View.addSpringTouchEffect() {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            MotionEvent.ACTION_UP -> {
                // Do NOT call performClick() here: returning false lets the View's own
                // touch handling dispatch the click, and calling it explicitly would
                // fire every action twice (e.g. adding two items to the cart).
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
            }
            MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
            }
        }
        false
    }
}

fun LinearLayout.label(value: String, size: Float = 16f, bold: Boolean = false): TextView = TextView(context).also {
    it.text = value; it.textSize = size
    if (bold) it.setTypeface(null, Typeface.BOLD)
    it.setPadding(0, context.dp(6), 0, context.dp(6))
    it.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    addView(it)
}

fun LinearLayout.button(value: String, action: () -> Unit): MaterialButton = MaterialButton(context).also {
    it.text = value; it.minHeight = context.dp(54); it.cornerRadius = context.dp(18)
    it.textSize = 14f
    it.setTypeface(null, Typeface.BOLD)
    it.addSpringTouchEffect()
    val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6); bottomMargin = context.dp(6) }
    addView(it, lp); it.setOnClickListener { action() }
}

data class InputField(val layout: TextInputLayout, val edit: TextInputEditText) { val value get() = edit.text?.toString().orEmpty() }

fun LinearLayout.field(hint: String, initial: String = "", type: Int = InputType.TYPE_CLASS_TEXT): InputField {
    val wrapper = TextInputLayout(context).apply {
        this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(context.dp(16).toFloat(), context.dp(16).toFloat(), context.dp(16).toFloat(), context.dp(16).toFloat())
    }
    val edit = TextInputEditText(context).apply { inputType = type; setText(initial); minHeight = context.dp(56) }
    wrapper.addView(edit)
    if (type and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 && type and InputType.TYPE_CLASS_TEXT != 0) wrapper.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
    addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8); bottomMargin = context.dp(6) })
    return InputField(wrapper, edit)
}

fun LinearLayout.card(bgColor: Int? = null, strokeColor: Int? = null, block: LinearLayout.() -> Unit): MaterialCardView {
    val cardView = MaterialCardView(context).apply {
        radius = context.dp(24).toFloat()
        cardElevation = context.dp(4).toFloat()
        strokeWidth = context.dp(1)
        val defaultOutline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
        setStrokeColor(strokeColor ?: defaultOutline)
        val defaultSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        setCardBackgroundColor(bgColor ?: defaultSurface)
        addSpringTouchEffect()
    }
    val inner = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(18))
    }
    inner.block()
    cardView.addView(inner)
    val lp = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = context.dp(6)
        bottomMargin = context.dp(6)
    }
    addView(cardView, lp)
    return cardView
}

fun foodArt(category: String) = when (category) { "Noodles" -> R.drawable.food_noodles; "Drinks" -> R.drawable.food_drink; "Snacks" -> R.drawable.food_snack; else -> R.drawable.food_rice }
fun View.show(visible: Boolean) { visibility = if (visible) View.VISIBLE else View.GONE }
