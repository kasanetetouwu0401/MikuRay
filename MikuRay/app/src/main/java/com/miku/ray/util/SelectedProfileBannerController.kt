package com.miku.ray.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.widget.BannerMediaController

class SelectedProfileBannerController(context: Context) {

    private val context: Context = context.applicationContext

    private var changeReceiver: BroadcastReceiver? = null

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)

    fun hasCustomBanner(): Boolean =
        !MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI).isNullOrEmpty()

    fun hasBanner(): Boolean = true

    fun applyTo(target: View, cornerRadiusDp: Float = 16f) {
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
        if (uriString.isNullOrEmpty()) {
            clearPendingRequest(target)
            clearMediaController(target)
            applyDefaultBanner(target, cornerRadiusDp)
            return
        }

        val dimPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        ).coerceIn(AppConfig.SELECTED_BANNER_DIM_MIN, AppConfig.SELECTED_BANNER_DIM_MAX)
        val cornerRadiusPx = cornerRadiusDp * target.context.resources.displayMetrics.density
        val dimColor = dimColorFor(target.context, dimPercent)
        val bitmapKey = "selected_banner::$uriString"
        val tagKey = "$bitmapKey::dim=$dimPercent::color=$dimColor::r=$cornerRadiusPx"
        if (target.getTag(TAG_KEY) == tagKey) return
        clearPendingRequest(target)

        getMediaController(target, cornerRadiusPx, dimColor)?.let { mediaController ->
            target.setLayerType(View.LAYER_TYPE_NONE, null)
            target.background = null
            target.setTag(TAG_KEY, tagKey)
            mediaController.load(uriString)
            return
        }

        bitmapCache.get(bitmapKey)?.let { cached ->
            target.setLayerType(View.LAYER_TYPE_NONE, null)
            target.setTag(REQUEST_TAG, null)

            target.background = CenterCropDimDrawable(cached, dimColor, cornerRadiusPx)
            target.setTag(TAG_KEY, tagKey)
            return
        }

        try {
            val uri = Uri.parse(uriString)
            val requestTarget = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (target.getTag(TAG_KEY) != tagKey) return

                    val safeCopy = try {
                        resource.copy(resource.config ?: Bitmap.Config.ARGB_8888, false)
                    } catch (e: Exception) {
                        resource
                    }
                    bitmapCache.put(bitmapKey, safeCopy)
                    target.setTag(REQUEST_TAG, null)
                    target.setLayerType(View.LAYER_TYPE_NONE, null)
                    target.background = CenterCropDimDrawable(safeCopy, dimColor, cornerRadiusPx)
                    target.setTag(TAG_KEY, tagKey)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    if (target.getTag(TAG_KEY) == tagKey) {
                        target.setTag(TAG_KEY, null)
                        target.setTag(REQUEST_TAG, null)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (target.getTag(TAG_KEY) == tagKey) {
                        target.setTag(TAG_KEY, null)
                        target.setTag(REQUEST_TAG, null)
                    }
                }
            }
            target.setTag(TAG_KEY, tagKey)
            target.setTag(REQUEST_TAG, requestTarget)
            Glide.with(target)
                .asBitmap()
                .load(uri)
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .override(MAX_BANNER_DECODE_SIZE, MAX_BANNER_DECODE_SIZE)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(requestTarget)
        } catch (e: Exception) {
            e.printStackTrace()
            target.setTag(REQUEST_TAG, null)
            target.setTag(TAG_KEY, null)
        }
    }

    private fun getMediaController(
        target: View,
        cornerRadiusPx: Float,
        dimColor: Int
    ): BannerMediaController? {
        val parent = target as? ViewGroup ?: return null
        val imageView = (target.getTag(MEDIA_IMAGE_TAG) as? ImageView) ?: ImageView(target.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setLayerType(View.LAYER_TYPE_NONE, null)
            parent.addView(this, 0)
            target.setTag(MEDIA_IMAGE_TAG, this)
        }

        val dimView = (target.getTag(MEDIA_DIM_TAG) as? View) ?: View(target.context).also {
            it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            parent.addView(it, (parent.indexOfChild(imageView) + 2).coerceAtMost(parent.childCount))
            target.setTag(MEDIA_DIM_TAG, it)
        }
        dimView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(dimColor)
        }

        return BannerMediaController.forImageView(imageView, R.drawable.uwu_banner_selected)
    }

    private fun clearMediaController(target: View) {
        val imageView = target.getTag(MEDIA_IMAGE_TAG) as? ImageView
        if (imageView != null) {
            BannerMediaController.forImageView(imageView, R.drawable.uwu_banner_selected).release()
            (imageView.parent as? ViewGroup)?.removeView(imageView)
        }
        (target.getTag(MEDIA_DIM_TAG) as? View)?.let { dimView ->
            (dimView.parent as? ViewGroup)?.removeView(dimView)
        }
        target.setTag(MEDIA_IMAGE_TAG, null)
        target.setTag(MEDIA_DIM_TAG, null)
    }

    private fun applyDefaultBanner(target: View, cornerRadiusDp: Float = 16f) {
        val dimPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        ).coerceIn(AppConfig.SELECTED_BANNER_DIM_MIN, AppConfig.SELECTED_BANNER_DIM_MAX)
        val cornerRadiusPx = cornerRadiusDp * target.context.resources.displayMetrics.density
        val dimColor = dimColorFor(target.context, dimPercent)
        val tagKey = "selected_banner::default::dim=$dimPercent::color=$dimColor::r=$cornerRadiusPx"
        if (target.getTag(TAG_KEY) == tagKey) return

        val cacheKey = "selected_banner::default"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null) {
            target.setLayerType(View.LAYER_TYPE_NONE, null)
            target.setTag(REQUEST_TAG, null)
            target.background = CenterCropDimDrawable(cached, dimColor, cornerRadiusPx)
            target.setTag(TAG_KEY, tagKey)
            return
        }

        try {
            val bitmap = BitmapFactory.decodeResource(target.context.resources, R.drawable.uwu_banner_selected)
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap)
                target.setLayerType(View.LAYER_TYPE_NONE, null)
                target.background = CenterCropDimDrawable(bitmap, dimColor, cornerRadiusPx)
                target.setTag(TAG_KEY, tagKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearPendingRequest(target: View) {
        (target.getTag(REQUEST_TAG) as? Target<*>)?.let { requestTarget ->
            Glide.with(context.applicationContext).clear(requestTarget)
        }
        target.setTag(REQUEST_TAG, null)
    }

    fun clear(target: View) {
        clearPendingRequest(target)
        clearMediaController(target)
        target.setTag(TAG_KEY, null)
        target.setLayerType(View.LAYER_TYPE_NONE, null)
        target.background = null
    }

    private fun dimColorFor(viewContext: Context, dimPercent: Int): Int {
        val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        val baseColor = viewContext.getColorAttr("colorCard")
        return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }

    private class CenterCropDimDrawable(
        private val bitmap: Bitmap,
        private val dimColor: Int,
        private val cornerRadius: Float = 0f
    ) : Drawable() {

        private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = android.graphics.PorterDuffColorFilter(dimColor, android.graphics.PorterDuff.Mode.SRC_OVER)
        }

        private val matrix = android.graphics.Matrix()
        private val rectF = android.graphics.RectF()

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            if (bitmap.isRecycled) return

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val vw = bounds.width().toFloat()
            val vh = bounds.height().toFloat()

            val scale = maxOf(vw / bw, vh / bh)
            val scaledW = bw * scale
            val scaledH = bh * scale
            val dx = bounds.left + (vw - scaledW) / 2f
            val dy = bounds.top + (vh - scaledH) / 2f

            matrix.reset()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)

            val shader = android.graphics.BitmapShader(
                bitmap,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
            shader.setLocalMatrix(matrix)
            bitmapPaint.shader = shader

            rectF.set(bounds)
        }

        override fun draw(canvas: android.graphics.Canvas) {
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            if (bitmap.isRecycled) return

            if (cornerRadius > 0f) {
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bitmapPaint)
            } else {
                canvas.drawRect(rectF, bitmapPaint)
            }
        }

        override fun setAlpha(alpha: Int) { bitmapPaint.alpha = alpha }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = -1
        override fun getIntrinsicHeight(): Int = -1
    }

    fun registerChangeListener(onChanged: () -> Unit) {
        if (changeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED) {
                    onChanged()
                }
            }
        }
        changeReceiver = receiver
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterChangeListener() {
        changeReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        changeReceiver = null
    }

    companion object {
        private val TAG_KEY = "selected_profile_banner_tag".hashCode()
        private val REQUEST_TAG = "selected_profile_banner_request".hashCode()
        private val MEDIA_IMAGE_TAG = "selected_profile_banner_media_image".hashCode()
        private val MEDIA_DIM_TAG = "selected_profile_banner_media_dim".hashCode()

        private const val MAX_CACHE_KB = 12 * 1024
        private const val MAX_BANNER_DECODE_SIZE = 1600
        private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }

        fun broadcastChanged(context: Context) {
            bitmapCache.evictAll()
            context.sendBroadcast(Intent(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED))
        }
    }
}
