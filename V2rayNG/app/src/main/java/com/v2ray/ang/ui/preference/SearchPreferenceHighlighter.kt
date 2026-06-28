package com.v2ray.ang.ui.preference

import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

/**
 * Collapses the AppBarLayout, scrolls to, and briefly highlights a preference
 * identified by [AppConfig.EXTRA_HIGHLIGHT_KEY] passed in the host Activity's intent.
 * Call from [PreferenceFragmentCompat.onViewCreated].
 */
object SearchPreferenceHighlighter {

    fun applyFromIntent(fragment: PreferenceFragmentCompat) {
        val key = fragment.activity?.intent
            ?.getStringExtra(AppConfig.EXTRA_HIGHLIGHT_KEY)
            ?: return

        // Collapse the toolbar first, then scroll + highlight after it settles
        val appBar = fragment.activity?.findViewById<AppBarLayout>(R.id.app_bar)
        if (appBar != null) {
            appBar.setExpanded(false, true)
            // Wait for collapse animation (~300ms) before scrolling
            Handler(Looper.getMainLooper()).postDelayed({
                highlight(fragment, key)
            }, 350)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                highlight(fragment, key)
            }, 300)
        }
    }

    private fun highlight(fragment: PreferenceFragmentCompat, key: String) {
        val pref = fragment.findPreference<androidx.preference.Preference>(key) ?: return

        fragment.scrollToPreference(pref)

        val recyclerView = fragment.listView
        val adapter = recyclerView.adapter

        Handler(Looper.getMainLooper()).postDelayed({
            if (adapter is PreferenceGroup.PreferencePositionCallback) {
                val position = adapter.getPreferenceAdapterPosition(pref)
                if (position != RecyclerView.NO_POSITION) {
                    val holder = recyclerView.findViewHolderForAdapterPosition(position)
                    if (holder != null) {
                        flashView(holder)
                        return@postDelayed
                    }
                }
            }
            fragment.scrollToPreference(pref)
        }, 200)
    }

    private fun flashView(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val typedValue = TypedValue()
        view.context.theme.resolveAttribute(android.R.attr.colorControlHighlight, typedValue, true)
        val highlightColor = typedValue.data.takeIf { it != 0 }
            ?: (view.context.getColor(android.R.color.darker_gray) and 0xFFFFFF or 0x44000000)
        val originalBackground = view.background
        view.setBackgroundColor(highlightColor)
        Handler(Looper.getMainLooper()).postDelayed({
            view.background = originalBackground
        }, 1200)
    }
}
