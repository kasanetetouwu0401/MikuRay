package com.v2ray.ang.ui.preference

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

/**
 * Instantly jumps to the target preference without scroll animation,
 * then highlights it using a shape-aware overlay (fade in/out).
 */
object SearchPreferenceHighlighter {

    fun applyFromIntent(fragment: PreferenceFragmentCompat) {
        val key = fragment.activity?.intent
            ?.getStringExtra(AppConfig.EXTRA_HIGHLIGHT_KEY)
            ?: return

        Handler(Looper.getMainLooper()).post {
            jumpAndHighlight(fragment, key)
        }
    }

    private fun jumpAndHighlight(fragment: PreferenceFragmentCompat, key: String) {
        val appBarLayout = fragment.activity?.findViewById<AppBarLayout>(R.id.app_bar)
        val recyclerView = fragment.listView
        
        // 1. Kecilkan AppBar secara instan.
        // false = langsung mengecil tanpa memicu render animasi layout yang berat.
        appBarLayout?.setExpanded(false, false)

        val pref = fragment.findPreference<androidx.preference.Preference>(key) ?: return
        val adapter = recyclerView.adapter ?: return

        // 2. Lompat instan ke target preference (Bypass semua lag/jank dari scroll)
        fragment.scrollToPreference(pref)

        // Beri jeda sangat singkat (150ms) agar RecyclerView selesai memuat (inflate) kotak tujuan
        Handler(Looper.getMainLooper()).postDelayed({
            if (adapter is PreferenceGroup.PreferencePositionCallback) {
                val position = adapter.getPreferenceAdapterPosition(pref)
                if (position != RecyclerView.NO_POSITION) {
                    
                    // Pastikan posisinya benar-benar terlihat di layar
                    recyclerView.scrollToPosition(position)
                    
                    // 3. Eksekusi animasi kedip (fade) setelah layout siap
                    Handler(Looper.getMainLooper()).postDelayed({
                        val holder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (holder != null) flashCard(holder.itemView)
                    }, 50)
                }
            }
        }, 150)
    }

    private fun flashCard(itemView: View) {
        val card = itemView as? MaterialCardView ?: return

        // Resolve colorPrimary menggunakan extension function dari util
        val highlightColor = card.context.getColorAttr("colorPrimary")

        // Kloning bentuk MaterialCardView (termasuk sudut melengkung Top/Mid/Bottom)
        // lalu ubah warnanya (alpha ~20%)
        val overlay = MaterialShapeDrawable(card.shapeAppearanceModel).apply {
            setTint(highlightColor and 0xFFFFFF or 0x33000000) 
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_NEVER
        }

        card.foreground = overlay
        overlay.alpha = 0

        // ObjectAnimator asli bawaan Android untuk transisi alpha (jauh lebih presisi dari XML)
        val fadeIn = ObjectAnimator.ofInt(overlay, "alpha", 0, 80).apply {
            duration = 200
        }
        val fadeOut = ObjectAnimator.ofInt(overlay, "alpha", 80, 0).apply {
            duration = 400
            startDelay = 800
        }
        
        // Bersihkan foreground dari memori ketika animasi selesai
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
