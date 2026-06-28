package com.v2ray.ang.ui.preference

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.getColorAttr

/**
 * Smoothly collapses the CollapsingToolbar via natural RecyclerView scroll,
 * then scrolls to and highlights the target preference using a shape-aware
 * overlay that respects the card's existing ShapeAppearanceModel (Top/Mid/Bottom/Single).
 */
object SearchPreferenceHighlighter {

    fun applyFromIntent(fragment: PreferenceFragmentCompat) {
        val key = fragment.activity?.intent
            ?.getStringExtra(AppConfig.EXTRA_HIGHLIGHT_KEY)
            ?: return

        Handler(Looper.getMainLooper()).post {
            collapseToolbarThenHighlight(fragment, key)
        }
    }

    private fun collapseToolbarThenHighlight(fragment: PreferenceFragmentCompat, key: String) {
        // Scroll RecyclerView programmatically so CoordinatorLayout collapses
        // the AppBar naturally — same smooth animation as user scrolling
        fragment.listView.smoothScrollBy(0, 1000)

        Handler(Looper.getMainLooper()).postDelayed({
            highlight(fragment, key)
        }, 400)
    }

    private fun highlight(fragment: PreferenceFragmentCompat, key: String) {
        val pref = fragment.findPreference<androidx.preference.Preference>(key) ?: return
        val recyclerView = fragment.listView
        val adapter = recyclerView.adapter ?: return

        fragment.scrollToPreference(pref)

        Handler(Looper.getMainLooper()).postDelayed({
            if (adapter is PreferenceGroup.PreferencePositionCallback) {
                val position = adapter.getPreferenceAdapterPosition(pref)
                if (position != RecyclerView.NO_POSITION) {
                    recyclerView.scrollToPosition(position)
                    Handler(Looper.getMainLooper()).postDelayed({
                        val holder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (holder != null) flashCard(holder.itemView)
                    }, 100)
                }
            }
        }, 200)
    }

    private fun flashCard(itemView: View) {
        val card = itemView as? MaterialCardView ?: return

        // Resolve
        val highlightColor = card.context.getColorAttr("colorPrimary")

        // Build a highlight overlay that clones the card's ShapeAppearanceModel exactly
        // — so it matches Top/Middle/Bottom/Single corners automatically
        val overlay = MaterialShapeDrawable(card.shapeAppearanceModel).apply {
            setTint(highlightColor and 0xFFFFFF or 0x33000000) // ~20% alpha
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_NEVER
        }

        // Add as foreground overlay, animate alpha in → hold → fade out
        card.foreground = overlay
        overlay.alpha = 0

        val fadeIn = ObjectAnimator.ofInt(overlay, "alpha", 0, 80).apply {
            duration = 200
        }
        val fadeOut = ObjectAnimator.ofInt(overlay, "alpha", 80, 0).apply {
            duration = 400
            startDelay = 800
        }
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                card.foreground = null
            }
        })
        AnimatorSet().apply {
            playSequentially(fadeIn, fadeOut)
            start()
        }
    }
}
