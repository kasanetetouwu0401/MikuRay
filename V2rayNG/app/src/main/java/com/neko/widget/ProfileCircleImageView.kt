package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

/**
 * A plain, regular circular ImageView (no shape-shader / SVG masking) that
 * always renders as a circle regardless of the user's chosen profile banner
 * shape preference, while still keeping its image content in sync with the
 * profile banner picture the user has set (same source/behaviour as
 * [ProfileBannerImageView], just always circle-clipped).
 */
class ProfileCircleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val TAG_PROFILE_DEFAULT = "DEFAULT_BANNER_PROFILE"

    private var borderColor: Int = 0
    private var borderWidth: Float = 0f
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clipPath = android.graphics.Path()

    init {
        scaleType = ScaleType.CENTER_CROP

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.ShaderImageView)
            borderColor = ta.getColor(R.styleable.ShaderImageView_siBorderColor, 0)
            borderWidth = ta.getDimension(R.styleable.ShaderImageView_siBorderWidth, 0f)
            ta.recycle()
        }
        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth
    }

    private val bannerChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.BROADCAST_ACTION_PROFILE_BANNER_CHANGED) {
                post { loadImage() }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ContextCompat.registerReceiver(
                context, bannerChangeReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_PROFILE_BANNER_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            loadImage()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            try { context.unregisterReceiver(bannerChangeReceiver) } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !isInEditMode) loadImage()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        val inset = borderWidth / 2f
        clipPath.addOval(RectF(inset, inset, w - inset, h - inset), android.graphics.Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(save)

        if (borderWidth > 0f && borderColor != 0) {
            val inset = borderWidth / 2f
            canvas.drawOval(RectF(inset, inset, width - inset, height - inset), borderPaint)
        }
    }

    private fun loadImage() {
        try {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
            val targetTag = if (uriString.isNullOrEmpty()) TAG_PROFILE_DEFAULT else uriString

            if (this.tag != targetTag) {
                if (!uriString.isNullOrEmpty()) {
                    val savedUri = Uri.parse(uriString)
                    Glide.with(this)
                        .asBitmap()
                        .load(savedUri)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontAnimate()
                        .error(R.drawable.uwu_banner_profile)
                        .into(this)
                } else {
                    loadDefault()
                }
                this.tag = targetTag
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (this.tag != TAG_PROFILE_DEFAULT) {
                loadDefault()
                this.tag = TAG_PROFILE_DEFAULT
            }
        }
    }

    private fun loadDefault() {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.uwu_banner_profile)
    }
}
