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

/**
 * Self-contained "blur3-lite": a live background blur view, adapted in spirit
 * from NagramXF-dev's org.telegram.ui.Components.blur3 package
 * (BlurredBackgroundSourceRenderNode + BlurredBackgroundDrawableRenderNode),
 * with all of Telegram's Theme/AndroidUtilities/source-abstraction machinery
 * stripped out since it doesn't exist in MikuRay.
 *
 * How it works:
 * - [captureNode] records whatever [rootView] (minus [excludeView], if set)
 *   draws behind this view, then has a Gaussian [RenderEffect] blur applied
 *   to it directly (this is the "blur biasa" / regular blur).
 * - If [liquidGlassEnabled] is true (and the device is API 33+), a second
 *   node, [fillNode], draws the already-blurred [captureNode] output and then
 *   has the "liquid glass" refraction shader ([LiquidGlassEffect]) applied on
 *   top of it — matching how Nagram layers LiquidGlassEffect over an
 *   already-blurred source.
 *
 * On API < 31 (no RenderEffect/RenderNode blur support) this silently falls
 * back to drawing [overlayColor] as a flat scrim, same as the old behavior.
 */
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

    /**
     * @param root the view whose subtree should be captured & blurred (usually
     *   the activity's decor view, or the screen's root layout).
     * @param exclude an optional view (and its children) to hide during the
     *   capture pass — typically the container this blur view itself sits
     *   inside of (e.g. a card that also holds foreground text/buttons), so
     *   that foreground content doesn't get captured as part of the "background".
     */
    fun attachTo(root: ViewGroup, exclude: View? = null) {
        rootView = root
        excludeView = exclude
    }

    override fun onDraw(canvas: Canvas) {
        if (capturing) return

        val root = rootView
        val cNode = captureNode
        val fNode = fillNode
        if (root == null || cNode == null || fNode == null || !canvas.isHardwareAccelerated) {
            if (Color.alpha(overlayColor) != 0) canvas.drawColor(overlayColor)
            return
        }

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
        val recCanvas = cNode.beginRecording()
        recCanvas.save()
        recCanvas.translate(offsetX, offsetY)
        root.draw(recCanvas)
        recCanvas.restore()
        cNode.endRecording()
        capturing = false

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

        canvas.drawRenderNode(fNode)
        if (Color.alpha(overlayColor) != 0) {
            canvas.drawColor(overlayColor)
        }
    }
}
