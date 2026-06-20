package com.v2ray.ang.util

import android.view.View
import android.view.ViewGroup
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

        val cornerRadiusPx = resolveCornerRadiusPx(view)
        view.foreground = GlareDrawable(cornerRadiusPx)
        view.setTag(R.id.tag_glare_applied_key, true)
    }

    fun removeFrom(view: View) {
        if (view.foreground is GlareDrawable) {
            view.foreground = null
        }
        view.setTag(R.id.tag_glare_applied_key, null)
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

    private fun resolveCornerRadiusPx(view: View): Float {
        return when (view) {
            is MaterialCardView -> view.radius
            is CardView -> view.radius
            is MaterialButton -> view.cornerRadius.toFloat()
            else -> view.height / 2f // pill-shaped buttons fall back to half height
        }
    }
}
