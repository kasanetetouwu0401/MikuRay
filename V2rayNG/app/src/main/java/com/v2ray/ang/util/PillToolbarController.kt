package com.v2ray.ang.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

/**
 * Controls the Telegram-style "pill" toolbar that can be toggled on for
 * every screen in the app, regardless of whether the screen uses the
 * shared activity_base.xml toolbar or its own CollapsingToolbarLayout.
 *
 * Rather than depending on a specific activity layout, this works directly
 * against any MaterialToolbar with id R.id.toolbar: it injects a single
 * pill view (back button + avatar/title/subtitle pill + overflow button)
 * as a child of the toolbar itself, and hides the toolbar's own title and
 * navigation icon while the pill is shown.
 */
object PillToolbarController {

    private const val TAG_PILL_VIEW = "pill_toolbar_injected_view"

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_PILL_TOOLBAR_STYLE, false)

    /**
     * Applies the current pill toolbar state to [toolbar]. [title] is the
     * title that would otherwise have been shown on the action bar.
     * [onBack] / [onMenu] are invoked when the back / overflow buttons are
     * tapped.
     */
    fun applyState(
        toolbar: MaterialToolbar?,
        title: CharSequence?,
        onBack: () -> Unit,
        onMenu: (View) -> Unit
    ) {
        toolbar ?: return

        if (isEnabled()) {
            val pillView = getOrInflatePillView(toolbar)
            bindPillContent(pillView, title, onBack, onMenu)
            pillView.visibility = View.VISIBLE
            toolbar.title = null
            toolbar.subtitle = null
        } else {
            findPillView(toolbar)?.visibility = View.GONE
        }
    }

    /**
     * When the pill toolbar style is active, the screen should behave like a
     * plain fixed toolbar instead of a collapsing/expanding one: this finds
     * the screen's [AppBarLayout] (R.id.app_bar) / [CollapsingToolbarLayout]
     * (R.id.collapsing_toolbar), if any, locks it to its collapsed height and
     * disables its scroll/expand behavior. When the pill style is disabled,
     * the original collapsing behavior is restored.
     */
    fun adjustCollapsingAppBar(appBar: AppBarLayout?, collapsingToolbar: CollapsingToolbarLayout?) {
        appBar ?: return
        collapsingToolbar ?: return

        val params = collapsingToolbar.layoutParams as? AppBarLayout.LayoutParams ?: return

        if (isEnabled()) {
            if (collapsingToolbar.getTag(R.id.tag_pill_original_scroll_flags) == null) {
                collapsingToolbar.setTag(R.id.tag_pill_original_scroll_flags, params.scrollFlags)
            }
            params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
            collapsingToolbar.layoutParams = params
            collapsingToolbar.title = null
            collapsingToolbar.isTitleEnabled = false
            appBar.setExpanded(false, false)
        } else {
            val originalFlags = collapsingToolbar.getTag(R.id.tag_pill_original_scroll_flags) as? Int
            if (originalFlags != null) {
                params.scrollFlags = originalFlags
                collapsingToolbar.layoutParams = params
            }
            collapsingToolbar.isTitleEnabled = true
        }
    }
    private fun getOrInflatePillView(toolbar: MaterialToolbar): View {
        findPillView(toolbar)?.let { return it }

        val pillView = LayoutInflater.from(toolbar.context)
            .inflate(R.layout.layout_pill_toolbar, toolbar, false)
        pillView.tag = TAG_PILL_VIEW

        val params = androidx.appcompat.widget.Toolbar.LayoutParams(
            androidx.appcompat.widget.Toolbar.LayoutParams.MATCH_PARENT,
            androidx.appcompat.widget.Toolbar.LayoutParams.MATCH_PARENT
        )
        toolbar.addView(pillView, params)
        return pillView
    }

    private fun findPillView(toolbar: MaterialToolbar): View? {
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child.tag == TAG_PILL_VIEW) return child
        }
        return null
    }

    private fun bindPillContent(
        pillView: View,
        title: CharSequence?,
        onBack: () -> Unit,
        onMenu: (View) -> Unit
    ) {
        val tvTitle = pillView.findViewById<TextView>(R.id.tv_pill_toolbar_title)
        val tvSubtitle = pillView.findViewById<TextView>(R.id.tv_pill_toolbar_subtitle)
        val btnBack = pillView.findViewById<View>(R.id.btn_pill_toolbar_back)
        val btnMenu = pillView.findViewById<View>(R.id.btn_pill_toolbar_menu)

        tvTitle?.text = title

        val subtitle = if (!title.isNullOrEmpty()) {
            pillView.context.getString(R.string.pill_toolbar_welcome_prefix, title)
        } else {
            null
        }

        if (subtitle.isNullOrEmpty()) {
            tvSubtitle?.visibility = View.GONE
        } else {
            tvSubtitle?.text = subtitle
            tvSubtitle?.visibility = View.VISIBLE
        }

        btnBack?.setOnClickListener { onBack() }
        btnMenu?.setOnClickListener { onMenu(it) }
    }

    /**
     * Registers a receiver on [context] that invokes [onChanged] whenever the
     * pill toolbar style or its subtitle is updated from settings, so the
     * currently visible activity can re-apply the toolbar state immediately.
     */
    fun registerChangeReceiver(context: Context, onChanged: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                onChanged()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_PILL_TOOLBAR_STYLE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }

    fun unregisterChangeReceiver(context: Context, receiver: BroadcastReceiver?) {
        receiver ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}
