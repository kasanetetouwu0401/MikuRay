package com.v2ray.ang.util

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

/**
 * Glare/glassmorphism shine effect for buttons and cards.
 *
 * Opt-in per view: a view only receives the effect if it's explicitly
 * marked in its layout XML with `android:tag="glare"`. This means the
 * effect does NOT automatically show up on every Button/CardView in the
 * app anymore — add the tag only to the specific views you want to glint.
 *
 * Example:
 * ```xml
 * <com.google.android.material.button.MaterialButton
 *     android:id="@+id/btn_connect"
 *     android:tag="glare"
 *     ... />
 * ```
 *
 * Toggleable from UI Settings (pref_glare_effect_enabled). When disabled,
 * the glare foreground is stripped from any tagged view, restoring the
 * view's default look. Safe to call repeatedly (e.g. on every
 * Activity.onContentChanged) since it tags views to avoid double-applying.
 */
object GlareEffectController {

    /** Set this as `android:tag="glare"` in XML on any View to opt it into the glare effect. */
    const val GLARE_TAG = "glare"

    private const val TAG_KEY = "miku_glare_applied"

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_GLARE_EFFECT_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_GLARE_EFFECT_ENABLED, enabled)
    }

    /**
     * Walks the view tree under [root] and applies (or removes) the glare
     * foreground on every view explicitly tagged with [GLARE_TAG].
     */
    fun applyToRoot(root: View?) {
        if (root == null) return
        if (isEnabled()) {
            applyRecursive(root)
        } else {
            removeRecursive(root)
        }
    }

    fun applyToView(view: View) {
        if (!isEnabled()) {
            removeFrom(view)
            return
        }
        if (view.getTag(R.id.tag_glare_applied_key) == true) return

        view.foreground = GlareDrawable(
            cornerRadiusProvider = { resolveCornerRadiusPx(view) },
            contentRectProvider = { resolveContentRect(view) }
        )
        view.setTag(R.id.tag_glare_applied_key, true)
    }

    fun removeFrom(view: View) {
        if (view.foreground is GlareDrawable) {
            view.foreground = null
        }
        view.setTag(R.id.tag_glare_applied_key, null)
    }

    /**
     * Tags and applies (or removes) the glare effect on a Toolbar's
     * navigation/up icon (the back arrow, e.g. `uwu_back_arrow`).
     *
     * This view is created internally by AppCompat/Toolbar at runtime — it's
     * never declared in any layout XML, so it can't be opted in via
     * `android:tag="glare"` like a normal Button/CardView. Call this once
     * right after `setSupportActionBar()` / `setDisplayHomeAsUpEnabled()`,
     * after which the regular [applyToRoot] walk (onResume, settings toggle,
     * etc.) will keep picking it up automatically since it's now tagged.
     */
    fun applyToToolbarNavigationIcon(toolbar: Toolbar?) {
        val navButton = findToolbarNavigationIcon(toolbar) ?: return
        navButton.tag = GLARE_TAG
        applyToView(navButton)
    }

    private fun findToolbarNavigationIcon(toolbar: Toolbar?): View? {
        toolbar ?: return null
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is ImageButton) return child
        }
        return null
    }

    private fun applyRecursive(view: View) {
        if (isGlareTarget(view)) {
            applyToView(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursive(view.getChildAt(i))
            }
        }
    }

    private fun removeRecursive(view: View) {
        if (isGlareTarget(view)) {
            removeFrom(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                removeRecursive(view.getChildAt(i))
            }
        }
    }

    /** Only views explicitly tagged "glare" in XML are targeted — no more blanket type matching. */
    private fun isGlareTarget(view: View): Boolean = view.tag == GLARE_TAG

    /**
     * The visible drawn surface of [view]. For most views this is just the
     * full view bounds, but MaterialButton (and similar Material widgets)
     * often render their rounded background *inset* from the view's
     * bounds via `android:insetTop`/`insetBottom`/`insetLeft`/`insetRight`
     * — reserved space so elevation shadows don't get clipped. Using the
     * raw view bounds in that case makes the glare look offset from /
     * wider than the actual visible button.
     */
    private fun resolveContentRect(view: View): RectF {
        if (view is MaterialButton) {
            val left = view.insetLeft.toFloat()
            val top = view.insetTop.toFloat()
            val right = (view.width - view.insetRight).toFloat()
            val bottom = (view.height - view.insetBottom).toFloat()
            if (right > left && bottom > top) {
                return RectF(left, top, right, bottom)
            }
        }
        return RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
    }

    /**
     * Resolved live (called fresh on every draw via [GlareDrawable]'s
     * provider), so it's safe even before the view has been laid out.
     *
     * MaterialButton styles that get their rounding from a `shapeAppearance`
     * (e.g. M3 Expressive's fully-rounded icon buttons) instead of an
     * explicit `app:cornerRadius` report a [MaterialButton.getCornerRadius]
     * of 0 — in that case we fall back to a pill-shape radius based on the
     * (inset-aware) content rect, which matches what's actually rendered.
     */
    private fun resolveCornerRadiusPx(view: View): Float {
        val typedRadius = when (view) {
            is MaterialCardView -> view.radius
            is CardView -> view.radius
            is MaterialButton -> view.cornerRadius.toFloat()
            else -> 0f
        }
        if (typedRadius > 0f) return typedRadius
        val rect = resolveContentRect(view)
        return minOf(rect.width(), rect.height()) / 2f
    }
}
