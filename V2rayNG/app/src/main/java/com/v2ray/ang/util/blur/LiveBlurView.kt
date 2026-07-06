package com.v2ray.ang.util.blur

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

class LiveBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rootView: ViewGroup? = null
    private var excludeView: View? = null
    private var capturing = false

    private var captureNode: RenderNode? = null
    private var fillNode: RenderNode? = null
    private var liquidGlass: LiquidGlassEffect? = null

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        updateBlurCapture()
        true
    }

    var overlayColor: Int = Color.argb(120, 0, 0, 0)
        set(value) {
            field = value
            invalidate()
        }

    var blurRadiusPx: Float = 0f
        set(value) {
            field = value
            captureNode?.setRenderEffect(
                if (supportsBlur() && value > 0f)
                    RenderEffect.createBlurEffect(value, value, Shader.TileMode.CLAMP)
                else null
            )
            invalidate()
        }

    var liquidGlassEnabled: Boolean = false
        set(value) {
            field = value
            if (value && supportsLiquidGlass() && liquidGlass == null) {
                fillNode?.let { liquidGlass = LiquidGlassEffect(context.applicationContext, it) }
            }
            invalidate()
        }

    var liquidThicknessPx: Float = 0f
    var liquidIntensity: Float = 0.12f
    var liquidRefractIndex: Float = 1.2f
    var liquidCornerRadiusPx: Float = 0f
    var liquidForegroundColor: Int = Color.argb(30, 255, 255, 255)

    init {
        if (supportsBlur()) {
            captureNode = RenderNode("live_blur_capture")
            fillNode = RenderNode("live_blur_fill")
            setWillNotDraw(false)
        }
    }

    private fun supportsBlur() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private fun supportsLiquidGlass() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun attachTo(root: ViewGroup, exclude: View? = null) {
        rootView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        
        rootView = root
        excludeView = exclude
        
        root.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rootView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
    }

    private fun updateBlurCapture() {
        val root = rootView ?: return
        val cNode = captureNode ?: return
        val fNode = fillNode ?: return

        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val loc = IntArray(2)
        getLocationInWindow(loc)
        val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        val offsetX = (rootLoc[0] - loc[0]).toFloat()
        val offsetY = (rootLoc[1] - loc[1]).toFloat()

        cNode.setPosition(0, 0, w, h)
        fNode.setPosition(0, 0, w, h)

        val prevExcludeVisibility = excludeView?.visibility
        excludeView?.visibility = INVISIBLE

        capturing = true
        try {
            val recCanvas = cNode.beginRecording()
            recCanvas.save()
            recCanvas.translate(offsetX, offsetY)
            root.draw(recCanvas)
            recCanvas.restore()
        } finally {
            cNode.endRecording()
            capturing = false
        }

        excludeView?.visibility = prevExcludeVisibility ?: VISIBLE

        val fillCanvas = fNode.beginRecording()
        fillCanvas.drawRenderNode(cNode)
        fNode.endRecording()

        if (liquidGlassEnabled && supportsLiquidGlass()) {
            val glass = liquidGlass ?: LiquidGlassEffect(context.applicationContext, fNode).also { liquidGlass = it }
            glass.update(
                0f, 0f, w.toFloat(), h.toFloat(),
                liquidCornerRadiusPx, liquidCornerRadiusPx, liquidCornerRadiusPx, liquidCornerRadiusPx,
                if (liquidThicknessPx > 0f) liquidThicknessPx else 11f,
                liquidIntensity,
                liquidRefractIndex,
                liquidForegroundColor
            )
        } else {
            fNode.setRenderEffect(null)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (capturing) return

        val fNode = fillNode
        if (fNode == null || !canvas.isHardwareAccelerated) {
            if (Color.alpha(overlayColor) != 0) canvas.drawColor(overlayColor)
            return
        }

        canvas.drawRenderNode(fNode)
        
        if (Color.alpha(overlayColor) != 0) {
            canvas.drawColor(overlayColor)
        }
    }
}
