package com.v2ray.ang.ui.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Horizontal rounded segmented bar with a marker dot showing where the
 * current reading falls. Shared by the Air Quality and Pollen cards, each
 * of which supplies its own severity color segments.
 */
class LevelBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var segmentColors: IntArray = intArrayOf(Color.GRAY)
        set(value) {
            field = value
            invalidate()
        }

    /** 0f..1f position of the marker along the bar. */
    var markerFraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var markerColor: Int = Color.WHITE
        set(value) {
            field = value
            markerPaint.color = value
            invalidate()
        }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        alpha = 60
    }
    private val rect = RectF()
    private val clipPath = Path()

    init {
        markerPaint.color = markerColor
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || segmentColors.isEmpty()) return

        val segW = w / segmentColors.size
        val cornerR = h / 2f
        rect.set(0f, 0f, w, h)
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerR, cornerR, Path.Direction.CW)

        val save = canvas.save()
        canvas.clipPath(clipPath)
        for (i in segmentColors.indices) {
            segmentPaint.color = segmentColors[i]
            canvas.drawRect(i * segW, 0f, (i + 1) * segW, h, segmentPaint)
        }
        canvas.restoreToCount(save)

        val markerX = (w * markerFraction).coerceIn(h / 2f, w - h / 2f)
        markerStrokePaint.strokeWidth = h * 0.08f
        canvas.drawCircle(markerX, h / 2f, h * 0.42f, markerPaint)
        canvas.drawCircle(markerX, h / 2f, h * 0.42f, markerStrokePaint)
    }
}
