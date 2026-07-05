package com.v2ray.ang.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import com.hoko.blur.HokoBlur
import com.v2ray.ang.R
import com.v2ray.ang.util.getActivity

/**
 * A drop-in-ish replacement for com.qmdeve.blurview.widget.BlurView, backed by HokoBlur.
 *
 * HokoBlur only operates on Bitmaps, so real-time "live" blur is faked here by, every frame:
 * 1) temporarily hiding this view,
 * 2) snapshotting the host Activity's decorView into a heavily downsampled Bitmap,
 * 3) restoring visibility,
 * 4) running it through HokoBlur (native scheme, stack algorithm),
 * 5) drawing the result scaled back up, clipped to a rounded rect, with an optional color overlay.
 *
 * This is intentionally continuous/live (matches the previous BlurView behavior) rather than a
 * one-shot snapshot, so it costs a capture+blur pass every frame while attached & visible.
 *
 * blurRadius/blurRounds accept the same ranges as before (radius up to 100, rounds up to 15).
 * Since HokoBlur caps a single pass at radius 25, larger radii are approximated by increasing the
 * downsample factor and by running `rounds` sequential blur passes.
 */
class LiveBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var blurRadiusPx: Float
    private var blurRounds: Int = 3
    private var overlayColor: Int
    private var cornerRadiusPx: Float

    private var blurTarget: ViewGroup? = null
    private var blurredBitmap: Bitmap? = null
    private var snapshotBitmap: Bitmap? = null
    private var hasShownFirstBlur = false

    private val srcRect = Rect()
    private val dstRectF = RectF()
    private val clipPath = Path()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isAttachedToWindow && visibility == VISIBLE) {
                updateBlur()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        val density = resources.displayMetrics.density
        var radius = 20f * density
        var corner = 0f
        var overlay = Color.TRANSPARENT

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.LiveBlurView)
            radius = ta.getDimension(R.styleable.LiveBlurView_blurRadius, radius)
            corner = ta.getDimension(R.styleable.LiveBlurView_cornerRadius, corner)
            overlay = ta.getColor(R.styleable.LiveBlurView_overlayColor, overlay)
            ta.recycle()
        }

        blurRadiusPx = radius
        cornerRadiusPx = corner
        overlayColor = overlay

        setWillNotDraw(false)
        alpha = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        blurredBitmap = null
        snapshotBitmap?.recycle()
        snapshotBitmap = null
        blurTarget = null
        super.onDetachedFromWindow()
    }

    fun setBlurRadius(radiusDp: Float) {
        blurRadiusPx = radiusDp * resources.displayMetrics.density
    }

    fun setBlurRounds(rounds: Int) {
        blurRounds = rounds.coerceIn(1, 15)
    }

    fun setOverlayColor(color: Int) {
        overlayColor = color
        invalidate()
    }

    fun setCornerRadiusPx(radiusPx: Float) {
        cornerRadiusPx = radiusPx
        invalidate()
    }

    private fun resolveBlurTarget(): ViewGroup? {
        blurTarget?.let { return it }
        val activity = context.getActivity() ?: return null
        val decorView = activity.window?.decorView as? ViewGroup ?: return null
        blurTarget = decorView
        return decorView
    }

    private fun updateBlur() {
        val target = resolveBlurTarget() ?: return
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return

        // Larger requested radius => stronger downsample, since HokoBlur caps a single pass at 25px.
        val downsampleFactor = (4f + (blurRadiusPx / 25f)).coerceIn(4f, 16f)

        val sampleW = (viewWidth / downsampleFactor).toInt().coerceAtLeast(1)
        val sampleH = (viewHeight / downsampleFactor).toInt().coerceAtLeast(1)

        try {
            var snapshot = snapshotBitmap
            if (snapshot == null || snapshot.width != sampleW || snapshot.height != sampleH || snapshot.isRecycled) {
                snapshot?.recycle()
                snapshot = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
                snapshotBitmap = snapshot
            }

            val loc = IntArray(2)
            getLocationInWindow(loc)

            val previousVisibility = visibility
            visibility = INVISIBLE
            val canvas = Canvas(snapshot)
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            canvas.save()
            canvas.scale(1f / downsampleFactor, 1f / downsampleFactor)
            canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
            target.draw(canvas)
            canvas.restore()
            visibility = previousVisibility

            val effectiveRadius = (blurRadiusPx / downsampleFactor).coerceIn(1f, 25f).toInt()

            var result = snapshot
            repeat(blurRounds) {
                result = HokoBlur.with(context)
                    .scheme(HokoBlur.SCHEME_NATIVE)
                    .mode(HokoBlur.MODE_STACK)
                    .radius(effectiveRadius)
                    .sampleFactor(1f)
                    .forceCopy(true)
                    .processor()
                    .blur(result)
            }

            blurredBitmap = result
            invalidate()

            if (!hasShownFirstBlur) {
                hasShownFirstBlur = true
                animate().alpha(1f).setDuration(180).start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = blurredBitmap
        if (bmp == null || bmp.isRecycled) return

        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRectF.set(0f, 0f, width.toFloat(), height.toFloat())

        val hasRoundedCorners = cornerRadiusPx > 0f
        if (hasRoundedCorners) {
            clipPath.reset()
            clipPath.addRoundRect(dstRectF, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
        }

        canvas.drawBitmap(bmp, srcRect, dstRectF, bitmapPaint)

        if (Color.alpha(overlayColor) > 0) {
            overlayPaint.color = overlayColor
            canvas.drawRect(dstRectF, overlayPaint)
        }

        if (hasRoundedCorners) canvas.restore()
    }
}
