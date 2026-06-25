package com.v2ray.ang.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

class SelectedProfileBannerController(private val context: Context) {

    private var changeReceiver: BroadcastReceiver? = null

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)

    fun hasBanner(): Boolean =
        !MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI).isNullOrEmpty()

    fun applyTo(target: View) {
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
        if (uriString.isNullOrEmpty()) {
            clear(target)
            return
        }

        val isDark = Utils.getDarkModeStatus(context)
        val dimPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        ).coerceIn(AppConfig.SELECTED_BANNER_DIM_MIN, AppConfig.SELECTED_BANNER_DIM_MAX)
        val bitmapKey = "selected_banner::$uriString"
        val tagKey = "$bitmapKey::dark=$isDark::dim=$dimPercent"
        if (target.getTag(TAG_KEY) == tagKey) return

        bitmapCache[bitmapKey]?.let { cached ->
            target.background = CenterCropDimDrawable(cached, dimColorFor(isDark, dimPercent))
            target.setTag(TAG_KEY, tagKey)
            return
        }

        try {
            val uri = Uri.parse(uriString)
            Glide.with(context)
                .asBitmap()
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        bitmapCache[bitmapKey] = resource
                        target.background = CenterCropDimDrawable(resource, dimColorFor(isDark, dimPercent))
                        target.setTag(TAG_KEY, tagKey)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        target.setTag(TAG_KEY, null)
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
            target.setTag(TAG_KEY, null)
        }
    }

    fun clear(target: View) {
        if (target.getTag(TAG_KEY) == null) return
        target.setTag(TAG_KEY, null)
        Glide.with(context).clear(target)
    }

    private fun dimColorFor(isDark: Boolean, dimPercent: Int): Int {
        val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        return if (isDark) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
    }

    private class CenterCropDimDrawable(
        private val bitmap: Bitmap,
        private val dimColor: Int
    ) : Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        private val dimPaint = android.graphics.Paint().apply { color = dimColor }
        private val matrix = android.graphics.Matrix()

        override fun draw(canvas: android.graphics.Canvas) {
            val bounds = bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return

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

            canvas.save()
            canvas.clipRect(bounds)
            canvas.drawBitmap(bitmap, matrix, paint)
            canvas.drawRect(bounds, dimPaint)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
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

        private val bitmapCache = mutableMapOf<String, Bitmap>()

        fun broadcastChanged(context: Context) {
            bitmapCache.clear()
            context.sendBroadcast(Intent(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED))
        }
    }
}
