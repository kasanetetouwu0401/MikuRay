package com.v2ray.ang.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

object BannerImageCache {

    private val bitmapCache = mutableMapOf<String, Bitmap>()

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
                .transition(BitmapTransitionOptions.withCrossFade(300))
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        bitmapCache[cacheKey] = resource
                        target.tag = tagKey

                        var isTransitionHandled = false
                        if (transition != null) {
                            val viewAdapter = object : Transition.ViewAdapter {
                                override fun getView() = target
                                override fun getCurrentDrawable() = target.drawable
                                override fun setDrawable(drawable: Drawable) {
                                    target.setImageDrawable(drawable)
                                }
                            }
                            isTransitionHandled = transition.transition(resource, viewAdapter)
                        }

                        if (!isTransitionHandled) {
                            target.setImageBitmap(resource)
                        }
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

    fun remove(namespace: String, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        bitmapCache.remove("$namespace::$uriString")
    }

    fun invalidate(namespace: String? = null) {
        if (namespace == null) {
            bitmapCache.clear()
        } else {
            bitmapCache.keys.filter { it.startsWith("$namespace::") }.forEach { bitmapCache.remove(it) }
        }
    }

    private const val TAG_PREFIX = "banner_image_cache_tag::"
}
