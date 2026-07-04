package com.v2ray.ang.ui.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Thick circular arc gauge (track + progress) used by the Pressure card.
 * [progress] is 0f..1f, already mapped from the pressure value's position
 * within its expected range by the caller.
 */
class ArcGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var progress: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var trackColor: Int = Color.DKGRAY
        set(value) {
            field = value
            trackPaint.color = value
            invalidate()
        }

    var progressColor: Int = Color.GREEN
        set(value) {
            field = value
            progressPaint.color = value
            invalidate()
        }

    private val startAngle = 130f
    private val sweepTotal = 280f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()

    init {
        trackPaint.color = trackColor
        progressPaint.color = progressColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokeWidth = w * 0.09f
        trackPaint.strokeWidth = strokeWidth
        progressPaint.strokeWidth = strokeWidth
        val inset = strokeWidth / 2f + w * 0.07f
        arcRect.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(arcRect, startAngle, sweepTotal, false, trackPaint)
        canvas.drawArc(arcRect, startAngle, sweepTotal * progress, false, progressPaint)
    }
}
