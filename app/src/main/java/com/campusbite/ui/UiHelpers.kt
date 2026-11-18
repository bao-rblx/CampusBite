package com.campusbite.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.campusbite.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
fun LinearLayout.label(value: String, size: Float = 16f, bold: Boolean = false): TextView = TextView(context).also {
    it.text = value; it.textSize = size
    if (bold) it.setTypeface(null, Typeface.BOLD)
    it.setPadding(0, context.dp(10), 0, context.dp(10))
    it.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    addView(it)
}
fun LinearLayout.button(value: String, action: () -> Unit): MaterialButton = MaterialButton(context).also {
    it.text = value; it.minHeight = context.dp(52); it.cornerRadius = context.dp(16)
    addView(it, LinearLayout.LayoutParams(-1, -2)); it.setOnClickListener { action() }
}
data class InputField(val layout: TextInputLayout, val edit: TextInputEditText) { val value get() = edit.text?.toString().orEmpty() }
fun LinearLayout.field(hint: String, initial: String = "", type: Int = InputType.TYPE_CLASS_TEXT): InputField {
    val wrapper = TextInputLayout(context).apply {
        this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(context.dp(14).toFloat(), context.dp(14).toFloat(), context.dp(14).toFloat(), context.dp(14).toFloat())
    }
    val edit = TextInputEditText(context).apply { inputType = type; setText(initial); minHeight = context.dp(56) }
    wrapper.addView(edit)
    if (type and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 && type and InputType.TYPE_CLASS_TEXT != 0) wrapper.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
    addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10); bottomMargin = context.dp(6) })
    return InputField(wrapper, edit)
}
fun foodArt(category: String) = when (category) { "Noodles" -> R.drawable.food_noodles; "Drinks" -> R.drawable.food_drink; "Snacks" -> R.drawable.food_snack; else -> R.drawable.food_rice }
fun View.show(visible: Boolean) { visibility = if (visible) View.VISIBLE else View.GONE }
