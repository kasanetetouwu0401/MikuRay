package com.v2ray.ang.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
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
     * text legibility. Safe to call repeatedly (e.g. on every bind); Glide will
     * dedupe identical requests against its own cache.
     */
    fun applyTo(target: View) {
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
        if (uriString.isNullOrEmpty()) {
            clear(target)
            return
        }

        val tagKey = "selected_banner::$uriString"
        if (target.getTag(TAG_KEY) == tagKey) return

        try {
            val uri = Uri.parse(uriString)
            Glide.with(context)
                .asBitmap()
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        target.background = buildDimmedDrawable(resource)
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

    private fun buildDimmedDrawable(bitmap: Bitmap): Drawable {
        val bitmapDrawable = BitmapDrawable(context.resources, bitmap)
        val dimPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        ).coerceIn(AppConfig.SELECTED_BANNER_DIM_MIN, AppConfig.SELECTED_BANNER_DIM_MAX)
        val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        val dimColor = Color.argb(alpha, 0, 0, 0)
        return LayerDrawable(arrayOf(bitmapDrawable, ColorDrawable(dimColor)))
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

        fun broadcastChanged(context: Context) {
            context.sendBroadcast(Intent(AppConfig.BROADCAST_ACTION_SELECTED_BANNER_CHANGED))
        }
    }
}
