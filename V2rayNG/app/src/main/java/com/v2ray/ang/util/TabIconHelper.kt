package com.v2ray.ang.util

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Xml
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import java.io.File
import java.io.FileInputStream

/**
 * Helper untuk tab icon custom dari internal storage.
 *
 * Format nilai `tabIcon` di SubscriptionItem:
 *  - null / ""           → tidak ada icon
 *  - "custom://{name}"   → Vector XML di filesDir/tab_icons/{name}
 *  - nama lain           → built-in drawable resource (existing behavior)
 *
 * Icon custom di-inflate via VectorDrawableCompat.createFromXml() sehingga
 * tint bekerja normal via imageTintList maupun colorFilter.
 */
object TabIconHelper {

    const val CUSTOM_PREFIX = "custom://"
    private const val DIR_NAME  = "tab_icons"

    fun iconDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun isCustom(iconName: String?): Boolean =
        iconName?.startsWith(CUSTOM_PREFIX) == true

    fun filename(iconName: String): String =
        iconName.removePrefix(CUSTOM_PREFIX)

    fun iconFile(context: Context, iconName: String): File =
        File(iconDir(context), filename(iconName))

    /**
     * Salin file XML dari URI ke internal storage.
     * Return nilai iconName yang disimpan ke SubscriptionItem.tabIcon.
     */
    fun saveXml(context: Context, subId: String, xmlBytes: ByteArray): String {
        val filename = "icon_${subId}.xml"
        File(iconDir(context), filename).writeBytes(xmlBytes)
        return "$CUSTOM_PREFIX$filename"
    }

    fun deleteIcon(context: Context, iconName: String?) {
        if (!isCustom(iconName)) return
        iconFile(context, iconName!!).delete()
    }

    /**
     * Load VectorDrawableCompat dari file XML di storage.
     * Return null jika gagal.
     */
    fun loadVector(context: Context, iconName: String): VectorDrawableCompat? {
        val file = iconFile(context, iconName)
        if (!file.exists()) return null
        return try {
            val parser = Xml.newPullParser()
            FileInputStream(file).use { stream ->
                parser.setInput(stream, null)
                // advance ke START_TAG pertama
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    event = parser.next()
                }
                VectorDrawableCompat.createFromXml(context.resources, parser)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Apply icon ke ImageView dengan tint via imageTintList
     * (bekerja karena hasilnya VectorDrawable asli).
     * Return true jika berhasil.
     */
    fun applyToImageView(
        context: Context,
        iconName: String,
        imageView: ImageView,
        tintColor: Int,
    ): Boolean {
        val drawable = loadVector(context, iconName) ?: return false
        imageView.setImageDrawable(drawable)
        imageView.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        return true
    }

    fun clearTint(imageView: ImageView) {
        imageView.clearColorFilter()
    }
}
