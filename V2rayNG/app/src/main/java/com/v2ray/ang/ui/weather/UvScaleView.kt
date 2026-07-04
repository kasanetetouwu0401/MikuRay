package com.v2ray.ang.ui.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Ring of 5 colored dots (Low/Moderate/High/Very High/Extreme) for the UV
 * index card, with the dot matching [activeIndex] drawn larger to show the
 * current category — mirrors the small colored-dot palette on Google's
 * weather app UV badge.
 */
class UvScaleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var activeIndex: Int = 0
        set(value) {
            field = value.coerceIn(0, dotColors.size - 1)
            invalidate()
        }

    private val dotColors = intArrayOf(
        Color.parseColor("#4CAF50"),
        Color.parseColor("#FFC107"),
        Color.parseColor("#FF9800"),
        Color.parseColor("#F44336"),
        Color.parseColor("#9C27B0")
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h * 0.4f
        val radius = min(w, h) * 0.32f
        val baseDot = min(w, h) * 0.045f

        for (i in dotColors.indices) {
            val angle = -Math.PI / 2 + i * (Math.PI * 2 / dotColors.size)
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            paint.color = dotColors[i]
            val r = if (i == activeIndex) baseDot * 1.9f else baseDot
            canvas.drawCircle(x, y, r, paint)
        }
    }
}
