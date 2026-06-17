package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import androidx.appcompat.R as AppCompatR
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.RandomIconColor
import com.v2ray.ang.util.getColorAttr

class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private var currentShapeKey: String? = AppConfig.PREF_ICON_SHAPE_DEFAULT
    
    private var customBgColor: Int? = null

    /** Whether this particular view instance is allowed to use random colors (set via XML). */
    private var randomColorEligible: Boolean = false

    private var lastIconIdentity: Int? = null

    private val shapeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                AppConfig.BROADCAST_ACTION_ICON_SHAPE_CHANGED -> {
                    val newKey = MmkvManager.decodeSettingsString(AppConfig.PREF_ICON_SHAPE)
                        ?: AppConfig.PREF_ICON_SHAPE_DEFAULT
                    applyShape(newKey)
                }
                AppConfig.BROADCAST_ACTION_RANDOM_ICON_COLOR_CHANGED -> {
                    lastIconIdentity = null
                    loadColorBitmap()
                }
            }
        }
    }

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(resolveShapeId())
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs, 
                R.styleable.DynamicShapeImageView, 
                defStyleAttr, 
                0
            )
            
            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_shapeBackgroundColor)) {
                customBgColor = typedArray.getColor(
                    R.styleable.DynamicShapeImageView_shapeBackgroundColor, 
                    0
                )
            }

            randomColorEligible = typedArray.getBoolean(
                R.styleable.DynamicShapeImageView_randomColorBackground,
                false
            )
            
            typedArray.recycle()
        }

        scaleType = ScaleType.CENTER_CROP
        loadColorBitmap()
    }

    private fun loadColorBitmap() {
        try {
            val color = resolveBackgroundColor()
            
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            
            setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** True only when this view is eligible (via XML) AND the user has enabled the feature. */
    private val isRandomColorEnabled: Boolean
        get() = randomColorEligible && MmkvManager.decodeSettingsBool(AppConfig.PREF_RANDOM_ICON_COLOR, false)

    private fun resolveBackgroundColor(): Int {
        customBgColor?.let { return it }

        if (isRandomColorEnabled) {
            val identity = findSiblingIconIdentity()
            if (identity != null) {
                return RandomIconColor.forIdentity(context, identity)
            }
        }

        return context.getColorAttr(AppCompatR.attr.colorPrimary)
    }

    /**
     * Finds the sibling icon ImageView and nearby title text within the row, and derives
     * a stable identity combining both, so the same row always maps to the same color even
     * when several rows share the same icon resource. Works both for Preference rows (which
     * use `@android:id/icon` / `@android:id/title`) and bottom sheet menu items (which use
     * plain, unidentified ImageView/TextView siblings).
     */
    private fun findSiblingIconIdentity(): Int? {
        val parentView = parent as? ViewGroup ?: return null
        val siblingIcon = findSiblingImageView(parentView) ?: return null
        val drawable = siblingIcon.drawable ?: return null
        val iconIdentity = drawable.constantState?.hashCode() ?: drawable.hashCode()

        // Walk up to the row's root to find the title text, since title/icon may not share
        // the same immediate parent depending on the layout (preference vs bottom sheet).
        val rootView = rootViewUpTo(parentView, maxLevels = 4)
        val titleText = rootView?.let { findRowTitleText(it) }

        return if (titleText != null) {
            31 * iconIdentity + titleText.hashCode()
        } else {
            iconIdentity
        }
    }

    /** Finds the icon ImageView sibling: the other child of our immediate FrameLayout parent. */
    private fun findSiblingImageView(parentView: ViewGroup): ImageView? {
        for (i in 0 until parentView.childCount) {
            val child = parentView.getChildAt(i)
            if (child !== this && child is ImageView) return child
        }
        return null
    }

    /** Finds the first non-empty title-like TextView under the row root, preferring @android:id/title. */
    private fun findRowTitleText(root: ViewGroup): String? {
        root.findViewById<View>(android.R.id.title)?.let {
            (it as? android.widget.TextView)?.text?.toString()?.let { text -> if (text.isNotEmpty()) return text }
        }
        return firstNonEmptyTextView(root)
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

    /**
     * Re-checks the sibling icon and refreshes the background color if it changed
     * (e.g. when this view/holder gets recycled and rebound to a different preference).
     */
    private fun refreshRandomColorIfNeeded() {
        if (!isRandomColorEnabled || customBgColor != null) return
        val identity = findSiblingIconIdentity() ?: return
        if (identity != lastIconIdentity) {
            lastIconIdentity = identity
            loadColorBitmap()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (randomColorEligible) {
            refreshRandomColorIfNeeded()
        }
        super.onDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            val savedKey = MmkvManager.decodeSettingsString(AppConfig.PREF_ICON_SHAPE)
                ?: AppConfig.PREF_ICON_SHAPE_DEFAULT
            applyShape(savedKey)

            if (randomColorEligible) {
                lastIconIdentity = null
                loadColorBitmap()
            }

            val filter = IntentFilter(AppConfig.BROADCAST_ACTION_ICON_SHAPE_CHANGED).apply {
                addAction(AppConfig.BROADCAST_ACTION_RANDOM_ICON_COLOR_CHANGED)
            }
            ContextCompat.registerReceiver(
                context, shapeChangeReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            try { context.unregisterReceiver(shapeChangeReceiver) } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        
        if (hasWindowFocus && !isInEditMode) {
            val savedKey = MmkvManager.decodeSettingsString(AppConfig.PREF_ICON_SHAPE)
                ?: AppConfig.PREF_ICON_SHAPE_DEFAULT
            applyShape(savedKey)
        }
    }

    private fun applyShape(shapeKey: String) {
        if (currentShapeKey != shapeKey) {
            currentShapeKey = shapeKey
            reloadShape()
            invalidate()
        }
    }

    private fun resolveShapeId(): Int = when (currentShapeKey ?: AppConfig.PREF_ICON_SHAPE_DEFAULT) {
        "uwu_shape_clover"         -> R.raw.uwu_shape_clover
        "uwu_shape_circle"         -> R.raw.uwu_shape_circle
        "uwu_shape_diamond"        -> R.raw.uwu_shape_diamond
        "uwu_shape_pentagon"       -> R.raw.uwu_shape_pentagon
        "uwu_shape_hexagon"        -> R.raw.uwu_shape_hexagon
        "uwu_shape_octagon"        -> R.raw.uwu_shape_octagon
        "uwu_shape_rounded_square" -> R.raw.uwu_shape_rounded_square
        "uwu_shape_squircle"       -> R.raw.uwu_shape_squircle
        "uwu_shape_heart"          -> R.raw.uwu_shape_heart
        "uwu_shape_hive"       -> R.raw.uwu_shape_hive
        "uwu_shape_pill"       -> R.raw.uwu_shape_pill
        "uwu_shape_scallop"       -> R.raw.uwu_shape_scallop
        else                       -> R.raw.uwu_shape_cookie
    }
}
