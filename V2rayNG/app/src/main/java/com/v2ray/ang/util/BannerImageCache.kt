package com.v2ray.ang.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

/**
 * Shared in-memory bitmap cache for banner images (home banner + sheet banners),
 * following the same anti-blink pattern already used by SelectedProfileBannerController.
 *
 * First load decodes via Glide (custom URI) or BitmapFactory (default drawable) once,
 * caches the resulting Bitmap, then every subsequent call for the same key sets the
 * bitmap directly with no async round-trip, no transition, and no flicker.
 */
object BannerImageCache {

    private val bitmapCache = mutableMapOf<String, Bitmap>()

    /**
     * Loads a banner into [target].
     *
     * @param namespace distinguishes different banner contexts (e.g. "home", "sheet")
     *                  so cache keys never collide between unrelated banners.
     * @param uriString custom banner URI string, or null/blank to use [defaultDrawableRes]
     * @param defaultDrawableRes drawable resource shown when there's no custom banner
     */
    fun load(
        context: Context,
        target: ImageView,
        namespace: String,
        uriString: String?,
        defaultDrawableRes: Int
    ) {
        val cacheKey = if (uriString.isNullOrBlank()) {
            "$namespace::default::$defaultDrawableRes"
        } else {
            "$namespace::$uriString"
        }
        val tagKey = TAG_PREFIX + cacheKey
        if (target.tag == tagKey) return

        bitmapCache[cacheKey]?.let { cached ->
            target.setImageBitmap(cached)
            target.tag = tagKey
            return
        }

        if (uriString.isNullOrBlank()) {
            try {
                val bitmap = BitmapFactory.decodeResource(context.resources, defaultDrawableRes)
                if (bitmap != null) {
                    bitmapCache[cacheKey] = bitmap
                    target.setImageBitmap(bitmap)
                    target.tag = tagKey
                } else {
                    target.setImageResource(defaultDrawableRes)
                    target.tag = tagKey
                }
            } catch (e: Exception) {
                e.printStackTrace()
                target.setImageResource(defaultDrawableRes)
                target.tag = tagKey
            }
            return
        }

        try {
            Glide.with(context)
                .asBitmap()
                .load(Uri.parse(uriString))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        bitmapCache[cacheKey] = resource
                        target.setImageBitmap(resource)
                        target.tag = tagKey
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        target.setImageResource(defaultDrawableRes)
                        target.tag = null
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
            target.setImageResource(defaultDrawableRes)
            target.tag = null
        }
    }

    /**
     * Removes the cached bitmap for a specific banner URI from the in-memory cache,
     * dropping our strong reference so it becomes eligible for GC once no ImageView
     * is still displaying it. Call this when the user deletes a custom banner, right
     * alongside clearing the MMKV pref / deleting the file.
     *
     * Note: this deliberately does NOT call bitmap.recycle(). The bitmap may still be
     * actively set on a live ImageView (banner change broadcasts are delivered
     * asynchronously), and recycling a bitmap a View is still drawing crashes with
     * "Canvas: trying to use a recycled bitmap". Dropping the reference is enough —
     * native memory is freed once nothing (cache or View) holds onto it anymore.
     *
     * @param namespace must match the namespace used in [load] (e.g. "home", "sheet")
     * @param uriString the URI string that was previously loaded; safe to pass null/blank
     */
    fun remove(namespace: String, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        bitmapCache.remove("$namespace::$uriString")
    }

    /** Call when a banner preference changes so stale cached bitmaps don't linger. */
    fun invalidate(namespace: String? = null) {
        if (namespace == null) {
            bitmapCache.clear()
        } else {
            bitmapCache.keys.filter { it.startsWith("$namespace::") }.forEach { bitmapCache.remove(it) }
        }
    }

    private const val TAG_PREFIX = "banner_image_cache_tag::"
}
