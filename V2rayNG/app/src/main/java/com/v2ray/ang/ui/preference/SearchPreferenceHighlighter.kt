package com.v2ray.ang.ui.preference

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

/**
 * Smoothly collapses the CollapsingToolbar programmatically,
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
        val appBarLayout = fragment.activity?.findViewById<AppBarLayout>(R.id.app_bar)
        
        if (appBarLayout != null) {
            appBarLayout.setExpanded(false, true)
        } else {
            fragment.listView.smoothScrollBy(0, 1000)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            highlight(fragment, key)
        }, 400)
    }

    private fun highlight(fragment: PreferenceFragmentCompat, key: String) {
        val pref = fragment.findPreference<androidx.preference.Preference>(key) ?: return
        val recyclerView = fragment.listView
        val adapter = recyclerView.adapter ?: return

        if (adapter is PreferenceGroup.PreferencePositionCallback) {
            val position = adapter.getPreferenceAdapterPosition(pref)
            if (position != RecyclerView.NO_POSITION) {

                // 1. Buat Custom Scroller agar meluncur mulus dan tidak memaksa CPU
                val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
                    override fun getVerticalSnapPreference(): Int {
                        // SNAP_TO_ANY akan mencoba menaruh item di tengah area layar yang kosong
                        return SNAP_TO_ANY
                    }

                    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                        // Secara bawaan scroller Android terlalu cepat sehingga sering lag di list panjang.
                        // Di sini kita perlambat sedikit (50f) agar gliding-nya lebih elegan dan ringan.
                        return 50f / displayMetrics.densityDpi
                    }
                }
                smoothScroller.targetPosition = position

                // 2. Jalankan scroll menggunakan layout manager (tanpa menggunakan scrollToPreference bawaan fragment)
                recyclerView.layoutManager?.startSmoothScroll(smoothScroller)

                // 3. Tunggu estimasi scroll selesai (~600ms), lalu eksekusi animasi nyala (highlight)
                Handler(Looper.getMainLooper()).postDelayed({
                    val holder = recyclerView.findViewHolderForAdapterPosition(position)
                    if (holder != null) {
                        flashCard(holder.itemView)
                    } else {
                        // Fallback: Jika listnya sangat amat panjang dan scroll belum selesai total
                        recyclerView.scrollToPosition(position)
                        Handler(Looper.getMainLooper()).postDelayed({
                            val lateHolder = recyclerView.findViewHolderForAdapterPosition(position)
                            if (lateHolder != null) flashCard(lateHolder.itemView)
                        }, 100)
                    }
                }, 600)
            }
        }
    }

    private fun flashCard(itemView: View) {
        val card = itemView as? MaterialCardView ?: return
        val highlightColor = card.context.getColorAttr(com.google.android.material.R.attr.colorPrimary)

        val overlay = MaterialShapeDrawable(card.shapeAppearanceModel).apply {
            setTint(highlightColor and 0xFFFFFF or 0x33000000) // ~20% alpha
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_NEVER
        }

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
