package com.v2ray.ang.util

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

/**
 * Centralized loader for the bottomsheet ("sheet") custom banner, used by every
 * bottom sheet that shows the `img_banner_sheet` image (main menu, more menu,
 * share/asset/routing/add-config sheets, etc).
 *
 * Each bottom sheet re-inflates its view (and therefore the ImageView) every time
 * it's opened, so a per-view tag check alone can't prevent a re-decode: the tag is
 * always fresh/null on a brand new View instance. Caching the decoded [Bitmap] here,
 * keyed by URI and shared process-wide, means the very first open after app start
 * pays the async Glide decode cost once — every subsequent sheet open (even a
 * different sheet type) applies the cached bitmap synchronously via
 * [ImageView.setImageBitmap], with no async round-trip and therefore no blank/flicker
 * frame while the sheet animates in.
 */
object SheetBannerLoader {

    private const val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    /** Call from a bottom sheet's onViewCreated (or equivalent) once the banner ImageView exists. */
    fun load(fragment: Fragment, imageView: ImageView) {
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
        val targetTag = if (uriString.isNullOrBlank()) TAG_SHEET_DEFAULT else uriString

        if (uriString.isNullOrBlank()) {
            Glide.with(fragment).clear(imageView)
            imageView.setImageResource(R.drawable.uwu_banner_image_about)
            imageView.tag = targetTag
            return
        }

        bitmapCache[uriString]?.let { cached ->
            imageView.setImageBitmap(cached)
            imageView.tag = targetTag
            return
        }

        try {
            Glide.with(fragment)
                .asBitmap()
                .load(Uri.parse(uriString))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        bitmapCache[uriString] = resource
                        imageView.setImageBitmap(resource)
                        imageView.tag = targetTag
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // No-op: keep whatever is currently displayed.
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        imageView.setImageResource(R.drawable.uwu_banner_image_about)
                        imageView.tag = null
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
            imageView.setImageResource(R.drawable.uwu_banner_image_about)
            imageView.tag = null
        }
    }

    /** Call when the sheet banner setting actually changes (new image picked or removed). */
    fun invalidateCache() {
        bitmapCache.clear()
    }
}
