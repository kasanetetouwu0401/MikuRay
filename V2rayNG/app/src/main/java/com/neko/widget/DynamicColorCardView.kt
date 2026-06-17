package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import androidx.appcompat.R as AppCompatR
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.RandomIconColor
import com.v2ray.ang.util.getColorAttr

/**
 * A [MaterialCardView] used as a square icon background (e.g. the quick-action cards inside
 * horizontal scroll menus) that can optionally take a vivid, identity-based random color
 * instead of a fixed `colorPrimary`, mirroring [DynamicShapeImageView]'s behavior.
 */
class DynamicColorCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    /** Whether this particular view instance is allowed to use random colors (set via XML). */
    private var randomColorEligible: Boolean = false

    private var lastIconIdentity: Int? = null

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.BROADCAST_ACTION_RANDOM_ICON_COLOR_CHANGED) {
                lastIconIdentity = null
                applyBackgroundColor()
            }
        }
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.DynamicColorCardView,
                defStyleAttr,
                0
            )
            randomColorEligible = typedArray.getBoolean(
                R.styleable.DynamicColorCardView_randomColorBackground,
                false
            )
            typedArray.recycle()
        }
    }

    private val isRandomColorEnabled: Boolean
        get() = randomColorEligible && MmkvManager.decodeSettingsBool(AppConfig.PREF_RANDOM_ICON_COLOR, false)

    private fun applyBackgroundColor() {
        setCardBackgroundColor(resolveBackgroundColor())
    }

    private fun resolveBackgroundColor(): Int {
        if (isRandomColorEnabled) {
            val identity = findIconIdentity()
            if (identity != null) {
                val isDark = (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                return RandomIconColor.forIdentity(identity, isDark)
            }
        }
        return context.getColorAttr(AppCompatR.attr.colorPrimary)
    }

    /**
     * Derives a stable identity from this card's icon ImageView plus the nearby title text,
     * so the same menu item always maps to the same color even when several items share icons.
     */
    private fun findIconIdentity(): Int? {
        val iconView = findChildImageView(this) ?: return null
        val drawable = iconView.drawable ?: return null
        val iconIdentity = drawable.constantState?.hashCode() ?: drawable.hashCode()

        val rootView = rootViewUpTo(this, maxLevels = 5)
        val titleText = rootView?.let { firstNonEmptyTextView(it) }

        return if (titleText != null) {
            31 * iconIdentity + titleText.hashCode()
        } else {
            iconIdentity
        }
    }

    /** Finds the first direct ImageView child of this card (the icon, not the small accent pill). */
    private fun findChildImageView(viewGroup: ViewGroup): ImageView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ImageView) return child
        }
        return null
    }

    private fun firstNonEmptyTextView(viewGroup: ViewGroup): String? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is android.widget.TextView) {
                val text = child.text?.toString()
                if (!text.isNullOrEmpty()) return text
            } else if (child is ViewGroup) {
                firstNonEmptyTextView(child)?.let { return it }
            }
        }
        return null
    }

    private fun rootViewUpTo(start: ViewGroup, maxLevels: Int): ViewGroup? {
        var current: ViewGroup = start
        repeat(maxLevels) {
            val nextParent = current.parent as? ViewGroup ?: return current
            current = nextParent
        }
        return current
    }

    private fun refreshIfNeeded() {
        if (!isRandomColorEnabled) return
        val identity = findIconIdentity() ?: return
        if (identity != lastIconIdentity) {
            lastIconIdentity = identity
            applyBackgroundColor()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            lastIconIdentity = null
            applyBackgroundColor()

            if (randomColorEligible) {
                ContextCompat.registerReceiver(
                    context, changeReceiver,
                    IntentFilter(AppConfig.BROADCAST_ACTION_RANDOM_ICON_COLOR_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode && randomColorEligible) {
            try { context.unregisterReceiver(changeReceiver) } catch (_: Exception) {}
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (randomColorEligible) {
            refreshIfNeeded()
        }
    }
}
