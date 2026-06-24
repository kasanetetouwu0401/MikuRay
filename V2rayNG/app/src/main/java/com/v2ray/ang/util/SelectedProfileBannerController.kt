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

/**
 * Controller that applies a custom banner image (via Glide) as the background of a
 * server card's indicator layout whenever that card represents the currently
 * selected profile. Designed to be driven from [com.v2ray.ang.ui.MainRecyclerAdapter]
 * during onBindViewHolder, mirroring the existing IndicatorStyle behaviour but
 * backed by a user-picked image instead of a static drawable resource.
 *
 * Usage:
 *   val controller = SelectedProfileBannerController(context)
 *   ...
 *   if (controller.isEnabled() && isSelectedServer) {
 *       controller.applyTo(holder.itemMainBinding.layoutIndicator)
 *   } else {
 *       controller.clear(holder.itemMainBinding.layoutIndicator)
 *   }
 *
 * Call [registerChangeListener] from the RecyclerView's onAttachedToRecyclerView and
 * [unregisterChangeListener] from onDetachedFromRecyclerView to get live updates
 * (e.g. notifyDataSetChanged) whenever the user changes the banner from settings.
 */
class SelectedProfileBannerController(private val context: Context) {

    private var changeReceiver: BroadcastReceiver? = null

    /** Whether the selected-profile banner style is turned on in settings. */
    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)

    /** Whether the user has actually picked a banner image to use. */
    fun hasBanner(): Boolean =
        !MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI).isNullOrEmpty()

    /**
     * Applies the saved banner image as the background of [target], dimmed for
     * text legibility. Safe to call repeatedly (e.g. on every bind).
     *
     * The decoded bitmap is cached by URI in [bitmapCache], a process-wide cache
     * shared by every controller instance. Once a banner has been decoded once,
     * every subsequent bind anywhere in the app applies it synchronously from that
     * cache instead of waiting on Glide's async callback — this is what avoids the
     * brief "no background" flash that otherwise shows up whenever the RecyclerView
     * rebinds (e.g. returning from another Activity).
     */
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

        // Cache hit: apply immediately, no flash, no Glide round-trip.
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
                        // No-op: leave whatever indicator drawable was set before.
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

    /** Clears any banner previously applied via [applyTo] and resets the bind tag. */
    fun clear(target: View) {
        if (target.getTag(TAG_KEY) == null) return
        target.setTag(TAG_KEY, null)
        Glide.with(context).clear(target)
    }

    private fun dimColorFor(isDark: Boolean, dimPercent: Int): Int {
        val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        // Light mode: wash with white so the overlay reads as a soft veil over a
        // light card. Dark mode: wash with black.
        return if (isDark) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
    }

    /**
     * Draws [bitmap] center-cropped to fill whatever bounds it is given, with a flat
     * dim color on top. Deliberately reports no intrinsic size (-1) so that, when used
     * as a View background, it never influences a wrap_content measure pass — the
     * card's height stays driven by its text content, exactly like the non-banner
     * indicator drawables, and the image simply fills whatever height results.
     */
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

            // Center-crop scale: cover the bounds, cropping overflow on one axis.
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

        // No intrinsic size: don't let this drawable dictate a wrap_content parent's
        // measured height/width. It simply fills whatever bounds it's assigned.
        override fun getIntrinsicWidth(): Int = -1
        override fun getIntrinsicHeight(): Int = -1
    }

    /**
     * Registers a receiver so any open RecyclerView refreshes its bound items the
     * moment the user changes/removes the selected-profile banner from settings.
     */
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

        /**
         * Process-wide cache of decoded banner bitmaps, keyed by the banner's URI
         * string alone (dark mode and dim level are applied on top at draw time, not
         * baked into the cached bitmap). Shared across every
         * [SelectedProfileBannerController] instance (e.g. one per RecyclerView) so a
         * banner decoded once in any list applies instantly everywhere else too.
         * Cleared via [broadcastChanged] whenever the underlying image actually
         * changes (new pick or deletion) so stale bitmaps never linger.
         */
        private val bitmapCache = mutableMapOf<String, Bitmap>()

        fun broadcastChanged(context: Context) {
            bitmapCache.clear()
            context.sendBroadcast(Intent(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED))
        }
    }
}
