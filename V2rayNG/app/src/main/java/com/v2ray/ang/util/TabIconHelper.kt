package com.v2ray.ang.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

object TabIconHelper {

    const val CUSTOM_PREFIX = "custom://"
    private const val DIR_NAME = "tab_icons"

    fun iconDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun isCustom(iconName: String?): Boolean =
        iconName?.startsWith(CUSTOM_PREFIX) == true

    fun filename(iconName: String): String =
        iconName.removePrefix(CUSTOM_PREFIX)

    fun iconFile(context: Context, iconName: String): File =
        File(iconDir(context), filename(iconName))

    fun saveBitmap(context: Context, subId: String, bitmap: Bitmap): String {
        val filename = "icon_${subId}.png"
        val file = File(iconDir(context), filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return "$CUSTOM_PREFIX$filename"
    }

    fun deleteIcon(context: Context, iconName: String?) {
        if (!isCustom(iconName)) return
        iconFile(context, iconName!!).delete()
    }

    fun applyToImageView(context: Context, iconName: String, imageView: ImageView, tintColor: Int): Boolean {
        val file = iconFile(context, iconName)
        if (!file.exists()) return false
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        imageView.setImageDrawable(BitmapDrawable(context.resources, bmp))
        imageView.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        return true
    }

    fun clearTint(imageView: ImageView) {
        imageView.clearColorFilter()
    }
}
