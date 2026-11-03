package fr.bonamy.iptv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

internal class ConnectingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    var isError = false
        set(value) {
            field = value
            contentDescription = if (value) "Stream unavailable" else "Connecting stream"
            motion.duration = if (value) 2800 else 1800
            invalidate()
        }
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { contentDescription = "Connecting stream" }

    override fun onDraw(canvas: Canvas) {
        val x = width / 2f; val y = height / 2f; val radius = context.dp(36).toFloat()
        val accent = if (isError) 0xFFED6673.toInt() else ACCENT
        val pulse = .5f + .5f * sin(phase * Math.PI.toFloat() * 2)
        paint.style = Paint.Style.FILL
        val glow = ((if (isError) 30 + (pulse * 24).toInt() else 48) shl 24) or (accent and 0xFFFFFF)
        paint.shader = RadialGradient(x, y, radius * 1.8f, intArrayOf(glow, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius * 1.8f, paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dp(2).toFloat(); paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0x25000000 or (accent and 0xFFFFFF)
        canvas.drawCircle(x, y, radius, paint)
        paint.color = accent
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, phase * 360f - 90f, 78f, false, paint)
        paint.color = if (isError) 0x80ED6673.toInt() else 0x804FBAFF.toInt()
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, phase * 360f + 90f, 28f, false, paint)
        paint.strokeWidth = context.dp(4).toFloat()
        paint.color = if (isError) 0xFFFFB1B8.toInt() else 0xFFD6EEFF.toInt()
        if (isError) {
            canvas.drawLine(x, y - context.dp(10), x, y + context.dp(2), paint)
            canvas.drawPoint(x, y + context.dp(10), paint)
            return
        }
        repeat(3) { index ->
            val height = context.dp(5) + context.dp(7) * (.5f + .5f * sin((phase * Math.PI * 2 + index * .9).toFloat()))
            val barX = x + (index - 1) * context.dp(9)
            canvas.drawLine(barX, y - height, barX, y + height, paint)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) motion.start() else motion.cancel()
    }

    override fun onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow() }
}
