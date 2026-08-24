package com.miku.ray.util

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

/** Loads the persisted application background directly into an Activity window. */
object AppBackgroundController {

    private const val BACKGROUND_OVERLAY_ALPHA = 42

    fun load(
        activity: Activity,
        previousTarget: Target<Drawable>? = null,
        onCustomBackgroundStateChanged: (Boolean) -> Unit = {}
    ): CustomTarget<Drawable>? {
        previousTarget?.let { Glide.with(activity).clear(it) }

        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_APP_BACKGROUND_URI)
        if (uriString.isNullOrBlank()) {
            onCustomBackgroundStateChanged(false)
            activity.window.setBackgroundDrawable(defaultBackground(activity))
            return null
        }

        onCustomBackgroundStateChanged(true)

        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                val image = resource.constantState?.newDrawable()?.mutate() ?: resource.mutate()
                val overlay = ColorDrawable(Color.argb(BACKGROUND_OVERLAY_ALPHA, 0, 0, 0))
                activity.window.setBackgroundDrawable(LayerDrawable(arrayOf(image, overlay)))
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                activity.window.setBackgroundDrawable(defaultBackground(activity))
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                onCustomBackgroundStateChanged(false)
                activity.window.setBackgroundDrawable(defaultBackground(activity))
            }
        }

        Glide.with(activity)
            .asDrawable()
            .load(Uri.parse(uriString))
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(target)
        return target
    }

    fun clear(activity: Activity, target: Target<Drawable>?) {
        target?.let { Glide.with(activity).clear(it) }
        activity.window.setBackgroundDrawable(defaultBackground(activity))
    }

    private fun defaultBackground(activity: Activity): Drawable {
        val typedValue = android.util.TypedValue()
        val resolved = activity.theme.resolveAttribute(R.attr.colorBg, typedValue, true)
        val color = when {
            resolved && typedValue.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT -> typedValue.data
            resolved && typedValue.resourceId != 0 -> ContextCompat.getColor(activity, typedValue.resourceId)
            else -> Color.TRANSPARENT
        }
        return ColorDrawable(color)
    }
}
