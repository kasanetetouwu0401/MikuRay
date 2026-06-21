package com.v2ray.ang.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import java.io.Serializable
import java.net.URI
import java.util.Locale

val Context.v2RayApplication: AngApplication?
    get() = applicationContext as? AngApplication

/**
 * Finds the most suitable root view to anchor a Snackbar to.
 *
 * If this Context is itself an Activity, its window's decor view is used.
 * Otherwise (e.g. a Service or BroadcastReceiver context), this falls back to
 * whichever Activity is currently in the foreground via [ForegroundActivityTracker].
 *
 * @return The root View to show a Snackbar on, or null if no Activity is available
 * (e.g. the app is fully backgrounded), in which case callers should fall back to a Toast.
 */
private fun Context.findSnackbarParent(): View? {
    val activity = this as? Activity ?: ForegroundActivityTracker.currentActivity ?: return null
    return activity.window?.decorView?.findViewById(android.R.id.content) as? View
        ?: activity.window?.decorView
}

/**
 * Internal helper to show a Material Snackbar with an icon, optional title, and message.
 *
 * @param context The context to resolve theme colors and fall back to Toast from.
 * @param title Optional title, shown bold above the message. Hidden entirely when blank.
 * @param message The message text to display.
 * @param iconRes Drawable resource ID for the leading icon.
 * @param backgroundColorAttr Theme color attribute for the Snackbar background, or null for default styling.
 * @param textColorAttr Theme color attribute for the Snackbar text/icon, or null for default styling.
 * @param duration Snackbar duration constant (e.g. Snackbar.LENGTH_SHORT).
 */
private fun showSnackbar(
    context: Context,
    title: CharSequence,
    message: CharSequence,
    @DrawableRes iconRes: Int,
    backgroundColorAttr: Int?,
    textColorAttr: Int?,
    duration: Int
) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post {
            showSnackbar(context, title, message, iconRes, backgroundColorAttr, textColorAttr, duration)
        }
        return
    }

    val parent = context.findSnackbarParent()
    if (parent == null) {
        val fallbackMessage = if (title.isNotNullEmpty()) "$title: $message" else message
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
        return
    }

    val snackbar = Snackbar.make(parent, "", duration)
    val snackbarLayout = snackbar.view as ViewGroup
    snackbarLayout.contentDescription = if (title.isNotNullEmpty()) "$title: $message" else message

    // Hide the default Snackbar text view; we render our own icon + title + message instead.
    snackbarLayout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        ?.visibility = View.INVISIBLE

    val contentView = LayoutInflater.from(context)
        .inflate(R.layout.layout_snackbar_custom, snackbarLayout, false)

    val resolvedTextColor = if (textColorAttr != null) {
        MaterialColors.getColor(parent, textColorAttr)
    } else {
        MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurface)
    }

    contentView.findViewById<ImageView>(R.id.iv_snackbar_icon)?.apply {
        setImageResource(iconRes)
        DrawableCompat.setTint(drawable.mutate(), resolvedTextColor)
    }
    contentView.findViewById<TextView>(R.id.tv_snackbar_title)?.apply {
        if (title.isNotNullEmpty()) {
            text = title
            visibility = View.VISIBLE
            setTextColor(resolvedTextColor)
        } else {
            visibility = View.GONE
        }
    }
    contentView.findViewById<TextView>(R.id.tv_snackbar_message)?.apply {
        text = message
        setTextColor(resolvedTextColor)
    }

    snackbarLayout.addView(contentView, 0)

    if (backgroundColorAttr != null) {
        snackbar.setBackgroundTint(MaterialColors.getColor(parent, backgroundColorAttr))
    }

    snackbar.show()
}

/**
 * Shows a neutral toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toast(message: Int) {
    showSnackbar(this, "", getString(message), R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_SHORT)
}

fun Context.alert(message: Int, title: CharSequence = "") {
    showSnackbar(this, title, getString(message), R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_LONG)
}

/**
 * Shows a neutral toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toast(message: CharSequence) {
    showSnackbar(this, "", message, R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_SHORT)
}

fun Context.alert(message: CharSequence, title: CharSequence = "") {
    showSnackbar(this, title, message, R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_LONG)
}

/**
 * Shows a success toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastSuccess(message: Int) {
    showSnackbar(
        this, "", getString(message), R.drawable.ic_check_circle,
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorOnPrimary,
        Snackbar.LENGTH_SHORT
    )
}

fun Context.alertSuccess(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), R.drawable.ic_check_circle,
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorOnPrimary,
        Snackbar.LENGTH_LONG
    )
}

/**
 * Shows a success toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(message: CharSequence) {
    showSnackbar(
        this, "", message, R.drawable.ic_check_circle,
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorOnPrimary,
        Snackbar.LENGTH_SHORT
    )
}

fun Context.alertSuccess(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, R.drawable.ic_check_circle,
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorOnPrimary,
        Snackbar.LENGTH_LONG
    )
}

/**
 * Shows an error toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastError(message: Int) {
    showSnackbar(
        this, "", getString(message), R.drawable.ic_warning,
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorOnError,
        Snackbar.LENGTH_SHORT
    )
}

fun Context.alertError(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), R.drawable.ic_warning,
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorOnError,
        Snackbar.LENGTH_LONG
    )
}

/**
 * Shows an error toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastError(message: CharSequence) {
    showSnackbar(
        this, "", message, R.drawable.ic_warning,
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorOnError,
        Snackbar.LENGTH_SHORT
    )
}

fun Context.alertError(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, R.drawable.ic_warning,
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorOnError,
        Snackbar.LENGTH_LONG
    )
}

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

/**
 * Converts a Long value to a speed string.
 *
 * @return The speed string.
 */
fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

/**
 * Converts a Long value to a traffic string.
 *
 * @return The traffic string.
 */
fun Long.toTrafficString(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= THRESHOLD && unitIndex < units.size - 1) {
        size /= DIVISOR
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
}

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "").orEmpty()

/**
 * Removes all whitespace from the string.
 *
 * @return The string without whitespace.
 */
fun String?.removeWhiteSpace(): String? = this?.replace(" ", "")

/**
 * Returns null if the string is null or blank, otherwise returns the string itself.
 *
 * @return The string or null.
 */
fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

/**
 * Converts the string to a Long value, or returns 0 if the conversion fails.
 *
 * @return The Long value.
 */
fun String.toLongEx(): Long = toLongOrNull() ?: 0

/**
 * Listens for package changes and executes a callback when a change occurs.
 *
 * @param onetime Whether to unregister the receiver after the first callback.
 * @param callback The callback to execute when a package change occurs.
 * @return The BroadcastReceiver that was registered.
 */
fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            })
        }
    }

/**
 * Retrieves a serializable object from the Bundle.
 *
 * @param key The key of the serializable object.
 * @return The serializable object, or null if not found.
 */
inline fun <reified T : Serializable> Bundle.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

/**
 * Retrieves a serializable object from the Intent.
 *
 * @param key The key of the serializable object.
 * @return The serializable object, or null if not found.
 */
inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
}

/**
 * Checks if the CharSequence is not null and not empty.
 *
 * @return True if the CharSequence is not null and not empty, false otherwise.
 */
fun CharSequence?.isNotNullEmpty(): Boolean = !this.isNullOrBlank()

fun String.concatUrl(vararg paths: String): String {
    val builder = StringBuilder(this.trimEnd('/'))

    paths.forEach { path ->
        val trimmedPath = path.trim('/')
        if (trimmedPath.isNotEmpty()) {
            builder.append('/').append(trimmedPath)
        }
    }

    return builder.toString()
}

/**
 * Helper function to match text either by Regex or literal string.
 */
fun String.matchesPattern(regex: Regex?, keyword: String?, ignoreCase: Boolean = true): Boolean {
    if (keyword.isNullOrEmpty()) {
        return true
    }
    return regex?.containsMatchIn(this)
        ?: this.contains(keyword, ignoreCase = ignoreCase)
}

/**
 * Checks if the config type is a group type (PolicyGroup or ProxyChain).
 *
 * @return True if the config type is PolicyGroup or ProxyChain, false otherwise.
 */
fun EConfigType.isGroupType(): Boolean {
    return this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

/**
 * Checks if the config type is a complex type (Custom, PolicyGroup, or ProxyChain).
 *
 * @return True if the config type is Custom, PolicyGroup, or ProxyChain, false otherwise.
 */
fun EConfigType.isComplexType(): Boolean {
    return this == EConfigType.CUSTOM || this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}