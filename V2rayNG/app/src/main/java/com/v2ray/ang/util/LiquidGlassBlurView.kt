package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
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
import com.v2ray.ang.R
import com.v2ray.ang.blur3.Blur3Compat
import com.v2ray.ang.blur3.LiquidGlassColorProvider
import com.v2ray.ang.blur3.drawable.BlurredBackgroundDrawableRenderNode
import com.v2ray.ang.blur3.source.BlurredBackgroundSourceRenderNode

/**
 * MikuRay's own real-time blur view, backed by the ported version of Telegram's
 * blur3 "Liquid Glass" engine. There is no third-party blur library behind this
 * anymore (the old com.qmdeve.blurview dependency has been removed entirely) -
 * below the API level real blur needs, this simply falls back to a flat tinted
 * color with no blur, rather than wrapping another blur implementation.
 *
 * - API 31+ (S): real RenderNode capture of whatever's behind this view in the same
 *   window, blurred via RenderEffect, drawn through BlurredBackgroundDrawableRenderNode.
 * - API 33+ (Tiramisu): additionally runs the liquid-glass refraction shader on top.
 * - Below API 31: no real blur is possible without RenderEffect, so this just shows
 *   a flat ColorDrawable in the overlay tint. setBlurRadius()/setBlurRounds() are
 *   no-ops on these devices since there is nothing left for them to drive.
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
        private val SUPPORTS_REAL_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    // Plain-tint path (API < 31): no blur library, just a flat color.
    private var tintDrawable: ColorDrawable? = null

    // Real blur pipeline (API 31+)
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

        if (SUPPORTS_REAL_BLUR) {
            setupRealBlur()
        } else {
            setupPlainTint()
        }
    }

    private fun setupPlainTint() {
        val drawable = ColorDrawable(overlayColor)
        background = drawable
        tintDrawable = drawable
        applyCornerClip()
    }

    /**
     * Rounds this view's own corners via the standard View outline-clip mechanism.
     * Only needed for the plain-tint path; the real blur pipeline rounds its own
     * shape internally via BlurredBackgroundDrawable.setRadius().
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
    private fun setupRealBlur() {
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

    /** Radius is in dp, matching the old BlurView's slider-driven usage. No-op below API 31. */
    fun setBlurRadius(radiusDp: Float) {
        if (SUPPORTS_REAL_BLUR) {
            setBlurRadiusReal(radiusDp)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setBlurRadiusReal(radiusDp: Float) {
        source?.setBlur(Blur3Compat.dpf2(radiusDp))
        glassDrawable?.invalidateDisplayList()
        invalidate()
    }

    /**
     * The old qmdeve BlurView interpreted "rounds" as iterated box-blur passes; that
     * library and concept are both gone now. Reused here as a control for the
     * liquid-glass edge thickness instead (rounds * 3dp), which only has a visible
     * effect on API 33+ where the refraction shader actually runs - see
     * BlurIntensityDialog, which hides this control entirely below that. No-op
     * below API 31 (nothing left to drive without RenderEffect).
     */
    fun setBlurRounds(rounds: Int) {
        if (SUPPORTS_REAL_BLUR) {
            glassDrawable?.setThickness(Blur3Compat.dp(rounds * 3f))
            glassDrawable?.invalidateDisplayList()
            invalidate()
        }
    }

    fun setOverlayColor(@ColorInt color: Int) {
        overlayColor = color
        tintDrawable?.color = color
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
        if (SUPPORTS_REAL_BLUR) {
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
