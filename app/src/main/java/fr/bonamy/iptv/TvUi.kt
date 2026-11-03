package fr.bonamy.iptv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal val INK = Color.rgb(7, 17, 29)
internal val PANEL = Color.rgb(18, 23, 30)
internal val MUTED = Color.rgb(143, 163, 184)
internal val ACCENT = Color.rgb(52, 152, 218)
internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
internal fun Context.label(text: String, size: Float = 18f, color: Int = Color.WHITE) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color)
    typeface = resources.getFont(R.font.theme); includeFontPadding = false
}
internal fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
internal fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun Context.surface(color: Int, border: Int = color) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(7).toFloat(); setStroke(dp(2), border)
}
internal fun Context.focusBackground(selected: Boolean = false) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), surface(0xff121b26.toInt(), ACCENT))
    addState(intArrayOf(android.R.attr.state_pressed), surface(0xff121b26.toInt(), ACCENT))
    addState(intArrayOf(), surface(if (selected) 0xff163047.toInt() else PANEL))
}
internal fun Context.action(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
    typeface = resources.getFont(R.font.theme); background = focusBackground()
    minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
    setPadding(dp(18), dp(8), dp(18), dp(8)); setOnClickListener { onClick() }
}
internal fun TextView.bold() { setTypeface(typeface, Typeface.BOLD) }

internal fun Context.browserBackground() = GradientDrawable(
    GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(7, 17, 29), Color.rgb(9, 18, 28), Color.rgb(2, 6, 11)))
internal fun Context.headerBackground() = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), surface(0x20ffffff, 0x20ffffff))
    addState(intArrayOf(android.R.attr.state_pressed), surface(0x20ffffff, 0x20ffffff))
    addState(intArrayOf(), surface(Color.TRANSPARENT))
}
internal fun Context.headerAction(text: String, clicked: () -> Unit) = action(text, clicked).apply {
    background = headerBackground(); textSize = 15f; elevation = 0f; stateListAnimator = null
    setPadding(dp(10), 0, dp(10), 0)
}
internal fun Context.playerAction(text: String, clicked: () -> Unit) = action(text, clicked).apply {
    background = getDrawable(R.drawable.playback_button_bg_selector); textSize = 14f
    setTextColor(0xffcccccc.toInt()); isAllCaps = true; bold(); elevation = 0f; stateListAnimator = null
    setPadding(dp(16), dp(8), dp(16), dp(8))
}
