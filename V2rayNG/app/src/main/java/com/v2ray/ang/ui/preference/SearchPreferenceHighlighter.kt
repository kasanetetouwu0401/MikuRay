package com.v2ray.ang.ui.preference

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

object SearchPreferenceHighlighter {

    fun applyFromIntent(fragment: PreferenceFragmentCompat) {
        val intent = fragment.activity?.intent ?: return
        val key = intent.getStringExtra(AppConfig.EXTRA_HIGHLIGHT_KEY) ?: return

        // Hapus extra agar tidak nge-trigger highlight berulang kali
        intent.removeExtra(AppConfig.EXTRA_HIGHLIGHT_KEY)

        fragment.view?.post {
            jumpAndHighlight(fragment, key)
        }
    }

    private fun jumpAndHighlight(fragment: PreferenceFragmentCompat, key: String) {
        val appBarLayout = fragment.activity?.findViewById<AppBarLayout>(R.id.app_bar)
        val recyclerView = fragment.listView
        
        // Pakai 'true' agar AppBarLayout mengecil secara perlahan/animasi
        appBarLayout?.setExpanded(false, true)

        val pref = fragment.findPreference<androidx.preference.Preference>(key) ?: return
        val adapter = recyclerView.adapter ?: return

        if (adapter is PreferenceGroup.PreferencePositionCallback) {
            val position = adapter.getPreferenceAdapterPosition(pref)
            if (position != RecyclerView.NO_POSITION) {
                
                recyclerView.stopScroll()
                
                val layoutManager = recyclerView.layoutManager
                
                // Buat custom scroller agar pergerakannya natural dan pas nyampe di atas
                val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
                    
                    // Memastikan target berhenti tepat di bagian paling atas layar (offset 0)
                    override fun getVerticalSnapPreference(): Int {
                        return SNAP_TO_START
                    }

                    // Callback ini akan dipanggil otomatis saat layar selesai nge-scroll
                    override fun onStop() {
                        super.onStop()
                        // Beri jeda sangat halus biar mata user fokus dulu sebelum berkedip
                        recyclerView.postDelayed({
                            val holder = recyclerView.findViewHolderForAdapterPosition(position)
                            holder?.itemView?.let { flashCard(it) }
                        }, 100)
                    }
                }

                smoothScroller.targetPosition = position

                if (layoutManager is LinearLayoutManager) {
                    // Mulai luncurkan layarnya
                    layoutManager.startSmoothScroll(smoothScroller)
                } else {
                    // Fallback jika bukan LinearLayoutManager
                    recyclerView.smoothScrollToPosition(position)
                }
            }
        }
    }

    private fun flashCard(itemView: View) {
        val card = itemView as? MaterialCardView ?: return
        
        val highlightColor = card.context.getColorAttr("colorPrimary")

        val overlay = MaterialShapeDrawable(card.shapeAppearanceModel).apply {
            setTint(highlightColor) 
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_NEVER
        }

        card.foreground = overlay
        overlay.alpha = 0

        val targetAlpha = 128 

        val fadeIn = ObjectAnimator.ofInt(overlay, "alpha", 0, targetAlpha).apply {
            duration = 200
        }
        val fadeOut = ObjectAnimator.ofInt(overlay, "alpha", targetAlpha, 0).apply {
            duration = 400
            startDelay = 800
        }
        
        val animatorSet = AnimatorSet().apply {
            playSequentially(fadeIn, fadeOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    card.foreground = null
                }
            })
        }

        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                animatorSet.cancel()
                card.foreground = null
                card.removeOnAttachStateChangeListener(this)
            }
        }
        card.addOnAttachStateChangeListener(attachListener)
        
        animatorSet.start()
    }
}
