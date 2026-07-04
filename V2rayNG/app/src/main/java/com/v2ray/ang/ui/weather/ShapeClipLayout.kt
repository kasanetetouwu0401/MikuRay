package com.v2ray.ang.ui.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import com.v2ray.ang.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * FrameLayout that fills and clips itself (and its children) to one of a
 * handful of hand-authored M3-Expressive-style shapes, scaled to whatever
 * size it's laid out with. Used as the outer container for the weather
 * detail cards (Humidity, UV index, Wind, etc.) so each block can carry a
 * distinct silhouette the way Google's weather app does, while staying a
 * normal ViewGroup that any children (TextViews, other custom views) can
 * be dropped into from XML.
 *
 * The squircle path reuses the same curve as MikuRay's `uwu_shape_rounded_square`
 * icon shape; cookie/circle/blob are new additions for this grid.
 */
class ShapeClipLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Shape { SQUIRCLE, COOKIE, CIRCLE, BLOB }

    var shape: Shape = Shape.SQUIRCLE
        set(value) {
            field = value
            templatePath = templateFor(value)
            rebuildScaledPath()
        }

    var fillColor: Int = Color.BLACK
        set(value) {
            field = value
            fillPaint.color = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var templatePath: Path = templateFor(shape)
    private val scaledPath = Path()
    private val matrix = Matrix()

    init {
        setWillNotDraw(false)
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ShapeClipLayout)
        val shapeIndex = ta.getInt(R.styleable.ShapeClipLayout_shapeType, 0)
        shape = Shape.values().getOrElse(shapeIndex) { Shape.SQUIRCLE }
        fillColor = ta.getColor(R.styleable.ShapeClipLayout_shapeFillColor, Color.BLACK)
        ta.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildScaledPath()
    }

    private fun rebuildScaledPath() {
        if (width == 0 || height == 0) return
        val bounds = RectF()
        templatePath.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        matrix.reset()
        matrix.setRectToRect(bounds, RectF(0f, 0f, width.toFloat(), height.toFloat()), Matrix.ScaleToFit.FILL)
        templatePath.transform(matrix, scaledPath)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(scaledPath)
        canvas.drawPath(scaledPath, fillPaint)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    companion object {
        /** 0..375 viewport, identical curve to `res/drawable/uwu_shape_rounded_square.xml`. */
        private fun squirclePath(): Path = Path().apply {
            moveTo(90f, 0f)
            lineTo(285f, 0f)
            cubicTo(300.797f, 0f, 316.319f, 4.159f, 330f, 12.058f)
            cubicTo(343.681f, 19.956f, 355.044f, 31.319f, 362.942f, 45f)
            cubicTo(370.841f, 58.681f, 375f, 74.203f, 375f, 90f)
            lineTo(375f, 285f)
            cubicTo(375f, 300.797f, 370.841f, 316.319f, 362.942f, 330f)
            cubicTo(355.044f, 343.681f, 343.681f, 355.044f, 330f, 362.942f)
            cubicTo(316.319f, 370.841f, 300.797f, 375f, 285f, 375f)
            lineTo(90f, 375f)
            cubicTo(66.14f, 375f, 43.232f, 365.511f, 26.36f, 348.64f)
            cubicTo(9.489f, 331.768f, 0f, 308.86f, 0f, 285f)
            lineTo(0f, 90f)
            cubicTo(0f, 66.14f, 9.489f, 43.232f, 26.36f, 26.36f)
            cubicTo(43.232f, 9.489f, 66.14f, 0f, 90f, 0f)
            close()
        }

        /** 12-bump scalloped "cookie" shape, generated on a 375x375 viewport. */
        private fun cookiePath(): Path {
            val path = Path()
            val cx = 187.5
            val cy = 187.5
            val bumps = 12
            val outerR = 187.5
            val innerR = 158.0
            val step = Math.PI * 2 / bumps
            for (i in 0 until bumps) {
                val angle = -Math.PI / 2 + i * step
                val nextAngle = angle + step
                val midAngle = angle + step / 2
                val outerX = (cx + outerR * cos(midAngle)).toFloat()
                val outerY = (cy + outerR * sin(midAngle)).toFloat()
                val startX = (cx + innerR * cos(angle)).toFloat()
                val startY = (cy + innerR * sin(angle)).toFloat()
                val endX = (cx + innerR * cos(nextAngle)).toFloat()
                val endY = (cy + innerR * sin(nextAngle)).toFloat()
                if (i == 0) path.moveTo(startX, startY)
                path.quadTo(outerX, outerY, endX, endY)
            }
            path.close()
            return path
        }

        private fun circlePath(): Path = Path().apply {
            addCircle(187.5f, 187.5f, 187.5f, Path.Direction.CW)
        }

        /** Hand-authored organic blob (soft rounded triangle) for the wind card. */
        private fun blobPath(): Path = Path().apply {
            moveTo(190f, 8f)
            cubicTo(235f, 35f, 345f, 85f, 368f, 168f)
            cubicTo(388f, 238f, 338f, 302f, 265f, 337f)
            cubicTo(195f, 372f, 85f, 366f, 35f, 302f)
            cubicTo(-10f, 245f, 18f, 155f, 58f, 95f)
            cubicTo(95f, 40f, 148f, -22f, 190f, 8f)
            close()
        }

        fun templateFor(shape: Shape): Path = when (shape) {
            Shape.SQUIRCLE -> squirclePath()
            Shape.COOKIE -> cookiePath()
            Shape.CIRCLE -> circlePath()
            Shape.BLOB -> blobPath()
        }
    }
}
