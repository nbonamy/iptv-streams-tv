package fr.bonamy.iptv

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Circular controls with icons centered by geometry rather than font metrics. */
internal class PlayerControlButton(context: Context, initialIcon: Icon, description: String) : View(context) {
    enum class Icon { PREVIOUS, NEXT, PLAY, PAUSE }
    var icon = initialIcon
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    init {
        contentDescription = description; isFocusable = true; isClickable = true
        fun surface(focused: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (focused) 0xD91A3349.toInt() else 0x6607111D)
            setStroke(context.dp(1), if (focused) ACCENT else 0x304E6378)
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), surface(true))
            addState(intArrayOf(android.R.attr.state_pressed), surface(true))
            addState(intArrayOf(), surface(false))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(width / 2f, height / 2f)
        val scale = resources.displayMetrics.density
        canvas.scale(scale, scale)
        paint.color = if (isEnabled) Color.WHITE else MUTED
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        when (icon) {
            Icon.PREVIOUS, Icon.NEXT -> {
                val direction = if (icon == Icon.NEXT) 1f else -1f
                val path = Path().apply { moveTo(-3f * direction, -7f); lineTo(3f * direction, 0f); lineTo(-3f * direction, 7f) }
                canvas.drawPath(path, paint)
            }
            Icon.PAUSE -> {
                paint.strokeWidth = 3f
                canvas.drawLine(-4f, -7f, -4f, 7f, paint)
                canvas.drawLine(4f, -7f, 4f, 7f, paint)
            }
            Icon.PLAY -> {
                paint.style = Paint.Style.FILL
                canvas.drawPath(Path().apply { moveTo(-6f, -8f); lineTo(6f, 0f); lineTo(-6f, 8f); close() }, paint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
    }
}
