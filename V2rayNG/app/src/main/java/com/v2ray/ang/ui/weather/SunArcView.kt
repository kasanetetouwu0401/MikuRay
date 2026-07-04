package com.v2ray.ang.ui.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dashed half-arc from sunrise to sunset with a solid dot marking the
 * current position of the sun (or where it will be, before sunrise / after
 * sunset). [progress] is 0f at sunrise, 1f at sunset, and can go outside
 * that range (it's clamped for drawing) to represent night.
 */
class SunArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var progress: Float = 0.5f
        set(value) {
            field = value
            invalidate()
        }

    var arcColor: Int = Color.CYAN
        set(value) {
            field = value
            arcPaint.color = value
            dotPaint.color = value
            invalidate()
        }

    var baselineColor: Int = Color.GRAY
        set(value) {
            field = value
            basePaint.color = value
            invalidate()
        }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arcRect = RectF()

    init {
        arcPaint.color = arcColor
        basePaint.color = baselineColor
        dotPaint.color = arcColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokeW = (w * 0.012f).coerceAtLeast(3f)
        arcPaint.strokeWidth = strokeW
        basePaint.strokeWidth = strokeW * 0.7f
        val margin = w * 0.08f
        arcRect.set(margin, h * 0.1f, w - margin, h * 1.65f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val baseY = h * 0.62f
        canvas.drawLine(0f, baseY, w, baseY, basePaint)
        canvas.drawArc(arcRect, 180f, 180f, false, arcPaint)

        val clamped = progress.coerceIn(0f, 1f)
        val angle = Math.PI * (1 - clamped)
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()
        val radiusX = arcRect.width() / 2f
        val radiusY = arcRect.height() / 2f
        val dotX = (cx + radiusX * cos(angle)).toFloat()
        val dotY = (cy - radiusY * sin(angle)).toFloat()
        canvas.drawCircle(dotX, dotY, w * 0.028f, dotPaint)
    }
}
