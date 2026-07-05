package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import com.qmdeve.blurview.widget.BlurView
import com.v2ray.ang.R
import com.v2ray.ang.blur3.Blur3Compat
import com.v2ray.ang.blur3.LiquidGlassColorProvider
import com.v2ray.ang.blur3.drawable.BlurredBackgroundDrawableRenderNode
import com.v2ray.ang.blur3.source.BlurredBackgroundSourceRenderNode

/**
 * Drop-in replacement for com.qmdeve.blurview.widget.BlurView, backed by MikuRay's
 * ported version of Telegram's blur3 "Liquid Glass" engine.
 *
 * - API 31+ (S): real RenderNode capture of whatever's behind this view in the same
 *   window, blurred via RenderEffect, drawn through BlurredBackgroundDrawableRenderNode.
 * - API 33+ (Tiramisu): additionally runs the liquid-glass refraction shader on top.
 * - Below API 31: wraps the existing qmdeve BlurView as a child, unchanged behavior.
 *
 * Exposes the same setBlurRadius/setBlurRounds/setOverlayColor surface the old
 * BlurView had, so WindowBlurUtils, BaseActivity's loading overlay and
 * BlurBottomStatusController all keep working without touching their call sites.
 */
class LiquidGlassBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        private val USE_NEW_PIPELINE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    // Legacy path (API < 31): delegate to the existing library, unchanged.
    private var legacyBlurView: BlurView? = null

    // New pipeline (API 31+)
    private var source: BlurredBackgroundSourceRenderNode? = null
    private var glassDrawable: BlurredBackgroundDrawableRenderNode? = null
    private var colorProvider: LiquidGlassColorProvider? = null

    private var cornerRadiusPx = 0f
    @ColorInt
    private var overlayColor = Color.TRANSPARENT

    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var observedDecorView: ViewGroup? = null
    private val captureLocation = IntArray(2)

    init {
        Blur3Compat.ensureInit(context)

        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassBlurView, defStyleAttr, 0)
        cornerRadiusPx = ta.getDimension(R.styleable.LiquidGlassBlurView_lgCornerRadius, 0f)
        overlayColor = ta.getColor(R.styleable.LiquidGlassBlurView_lgOverlayColor, Color.TRANSPARENT)
        ta.recycle()

        if (USE_NEW_PIPELINE) {
            setupNewPipeline()
        } else {
            setupLegacyFallback()
        }
    }

    private fun setupLegacyFallback() {
        val bv = BlurView(context, null).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setOverlayColor(overlayColor)
        }
        addView(bv)
        legacyBlurView = bv
        applyCornerClip()
    }

    /**
     * Rounds this view's own corners (and whatever's drawn inside it, including the
     * legacy BlurView child) via the standard View outline-clip mechanism, rather than
     * assuming the external qmdeve library exposes a settable corner radius API.
     */
    private fun applyCornerClip() {
        if (cornerRadiusPx > 0f) {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
        } else {
            clipToOutline = false
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupNewPipeline() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val src = BlurredBackgroundSourceRenderNode(null)
        val drawable = src.createDrawable() as BlurredBackgroundDrawableRenderNode
        val provider = LiquidGlassColorProvider(overlayColor, isDark)

        drawable.setColorProvider(provider)
        if (cornerRadiusPx > 0f) {
            drawable.setRadius(cornerRadiusPx)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawable.setLiquidGlassEffectAllowed(context)
        }

        background = drawable
        source = src
        glassDrawable = drawable
        colorProvider = provider
    }

    /** Radius is in dp, matching the old BlurView's slider-driven usage. */
    fun setBlurRadius(radiusDp: Float) {
        legacyBlurView?.setBlurRadius(radiusDp)
        if (USE_NEW_PIPELINE) {
            setBlurRadiusNewPipeline(radiusDp)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setBlurRadiusNewPipeline(radiusDp: Float) {
        source?.setBlur(Blur3Compat.dpf2(radiusDp))
        glassDrawable?.invalidateDisplayList()
        invalidate()
    }

    /**
     * The old qmdeve BlurView interpreted "rounds" as iterated box-blur passes, which
     * doesn't map onto a single real Gaussian RenderEffect. Reused here as a control for
     * the liquid-glass edge thickness instead (rounds * 3dp), so the existing 1-15
     * slider in BlurIntensityDialog/BlurBottomIntensityDialog stays meaningful without
     * needing a new UI. Tune the multiplier if 3dp/round doesn't look right in practice.
     */
    fun setBlurRounds(rounds: Int) {
        legacyBlurView?.setBlurRounds(rounds)
        if (USE_NEW_PIPELINE) {
            glassDrawable?.setThickness(Blur3Compat.dp(rounds * 3f))
            glassDrawable?.invalidateDisplayList()
            invalidate()
        }
    }

    fun setOverlayColor(@ColorInt color: Int) {
        overlayColor = color
        legacyBlurView?.setOverlayColor(color)
        colorProvider?.setColor(color)
        glassDrawable?.updateColors()
        invalidate()
    }

    fun setCornerRadius(radiusPx: Float) {
        cornerRadiusPx = radiusPx
        applyCornerClip()
        glassDrawable?.setRadius(radiusPx)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (USE_NEW_PIPELINE) {
            attachCapture()
        }
    }

    override fun onDetachedFromWindow() {
        detachCapture()
        super.onDetachedFromWindow()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun attachCapture() {
        val decor = context.getActivity()?.window?.decorView as? ViewGroup ?: return
        detachCapture()

        val listener = ViewTreeObserver.OnPreDrawListener {
            captureFrame(decor)
            true
        }
        decor.viewTreeObserver.addOnPreDrawListener(listener)
        preDrawListener = listener
        observedDecorView = decor
    }

    private fun detachCapture() {
        val listener = preDrawListener
        val decor = observedDecorView
        if (listener != null && decor != null && decor.viewTreeObserver.isAlive) {
            decor.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        preDrawListener = null
        observedDecorView = null
    }

    /**
     * Re-records the window's own decor content (minus this view) into the source
     * RenderNode, translated so it lines up with this view's position. Runs on every
     * pre-draw pass while attached - the same "hide self, capture the root, restore"
     * technique real-time Android blur views use, e.g. Dimezis/eightbitlab BlurView.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun captureFrame(decor: ViewGroup) {
        if (visibility != View.VISIBLE) return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val src = source ?: return
        if (src.inRecording()) return

        getLocationInWindow(captureLocation)

        val wasVisible = visibility
        visibility = View.INVISIBLE
        try {
            val canvas = try {
                src.beginRecording(w, h)
            } catch (e: Exception) {
                return
            }
            try {
                canvas.translate(-captureLocation[0].toFloat(), -captureLocation[1].toFloat())
                decor.draw(canvas)
            } catch (e: Exception) {
                // A single bad frame shouldn't wedge the RenderNode in "recording" state
                // or crash the app - just skip this frame and try again next pass.
            } finally {
                src.endRecording()
            }
        } finally {
            visibility = wasVisible
        }

        glassDrawable?.invalidateDisplayList()
        invalidate()
    }
}
