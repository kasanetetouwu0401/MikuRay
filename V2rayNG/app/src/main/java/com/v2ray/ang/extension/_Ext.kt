package com.v2ray.ang.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr
import java.io.Serializable
import java.net.URI
import java.util.Locale

val Context.v2RayApplication: AngApplication?
    get() = applicationContext as? AngApplication

private fun Context.findSnackbarParent(): View? {
    val activity = this as? Activity ?: ForegroundActivityTracker.currentActivity ?: return null
    return activity.window?.decorView?.findViewById(android.R.id.content) as? View
        ?: activity.window?.decorView
}

private fun showSnackbar(
    context: Context,
    title: CharSequence,
    message: CharSequence,
    @DrawableRes iconRes: Int,
    backgroundColorAttrName: String?,
    textColorAttrName: String?,
    duration: Int
) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post {
            showSnackbar(context, title, message, iconRes, backgroundColorAttrName, textColorAttrName, duration)
        }
        return
    }

    val parent = context.findSnackbarParent()
    if (parent == null) {
        val fallbackMessage = if (title.isNotNullEmpty()) "$title: $message" else message
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
        return
    }
    
    val snackbar = Snackbar.make(parent, "", Snackbar.LENGTH_INDEFINITE)
    val snackbarLayout = snackbar.view as ViewGroup
    snackbarLayout.contentDescription = if (title.isNotNullEmpty()) "$title: $message" else message

    snackbarLayout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        ?.visibility = View.INVISIBLE

    val contentView = LayoutInflater.from(parent.context)
        .inflate(R.layout.layout_snackbar_custom, snackbarLayout, false)

    val resolvedTextColor = if (textColorAttrName != null) {
        parent.context.getColorAttr(textColorAttrName)
    } else {
        parent.context.getColorAttr("colorOnSurfaceInverse")
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

    val layoutParams = snackbarLayout.layoutParams
    when (layoutParams) {
        is CoordinatorLayout.LayoutParams -> layoutParams.gravity = Gravity.TOP
        is FrameLayout.LayoutParams -> layoutParams.gravity = Gravity.TOP
    }
    snackbarLayout.layoutParams = layoutParams

    ViewCompat.setOnApplyWindowInsetsListener(snackbarLayout) { view, insets ->
        val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        
        val margin5dp = (5f * view.context.resources.displayMetrics.density).toInt()
        val margin16dp = (16f * view.context.resources.displayMetrics.density).toInt()

        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarTop + margin5dp
            bottomMargin = margin5dp
            leftMargin = margin16dp
            rightMargin = margin16dp
        }
        insets
    }

    snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE

    snackbarLayout.doOnPreDraw { view ->
        view.translationY = -view.height.toFloat()
        view.animate()
            .translationY(0f)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun slideRightThenDismiss() {
        if (!snackbarLayout.isAttachedToWindow) {
            snackbar.dismiss()
            return
        }
        snackbarLayout.animate()
            .translationX(snackbarLayout.width.toFloat())
            .alpha(0f)
            .setDuration(250L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { snackbar.dismiss() }
            .start()
    }

    val autoDismissDelayMs = when (duration) {
        Snackbar.LENGTH_INDEFINITE -> null
        Snackbar.LENGTH_SHORT -> 1500L
        else -> 2750L
    }
    autoDismissDelayMs?.let {
        Handler(Looper.getMainLooper()).postDelayed(::slideRightThenDismiss, it)
    }

    val cornerRadiusPx = 10f * parent.context.resources.displayMetrics.density
    val backgroundColor = if (backgroundColorAttrName != null) {
        parent.context.getColorAttr(backgroundColorAttrName)
    } else {
        parent.context.getColorAttr("colorSurfaceInverse")
    }

    snackbarLayout.backgroundTintList = null
    snackbarLayout.backgroundTintMode = null

    snackbarLayout.background = MaterialShapeDrawable(
        ShapeAppearanceModel.builder().setAllCornerSizes(cornerRadiusPx).build()
    ).apply {
        fillColor = ColorStateList.valueOf(backgroundColor)
        elevation = snackbarLayout.elevation
    }

    snackbar.show()
}

/**
 * Shows a neutral Snackbar with the given resource ID, and an optional title.
 *
 * @param message The resource ID of the message to show.
 * @param title Optional title shown bold above the message.
 */
fun Context.snackbarDefault(message: Int, title: CharSequence = "") {
    showSnackbar(this, title, getString(message), R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_LONG)
}

/**
 * Shows a neutral Snackbar with the given text, and an optional title.
 *
 * @param message The text of the message to show.
 * @param title Optional title shown bold above the message.
 */
fun Context.snackbarDefault(message: CharSequence, title: CharSequence = "") {
    showSnackbar(this, title, message, R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_LONG)
}

/**
 * Shows a success Snackbar (colorPrimary background, colorOnPrimary text) with the
 * given resource ID, and an optional title.
 *
 * @param message The resource ID of the message to show.
 * @param title Optional title shown bold above the message.
 */
fun Context.snackbarSuccess(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), R.drawable.ic_check_circle,
        "colorPrimary",
        "colorOnPrimary",
        Snackbar.LENGTH_LONG
    )
}

/**
 * Shows a success Snackbar (colorPrimary background, colorOnPrimary text) with the
 * given text, and an optional title.
 *
 * @param message The text of the message to show.
 * @param title Optional title shown bold above the message.
 */
fun Context.snackbarSuccess(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, R.drawable.ic_check_circle,
        "colorPrimary",
        "colorOnPrimary",
        Snackbar.LENGTH_LONG
    )
}

/**
 * Shows an error Snackbar (colorError background, colorOnError text) with the
 * given resource ID, and an optional title.
 *
 * @param message The resource ID of the message to show.
 * @param title Optional title shown bold above the message.
 */
fun Context.snackbarError(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), R.drawable.ic_warning,
        "colorError",
        "colorOnError",
        Snackbar.LENGTH_LONG
    )
}

/**
 * Shows an error Snackbar (colorError background, colorOnError text) with the
 * given text, and an optional title.
 *
 * @param message The text of the message to show.
 * @param title Optional title shown bold above the message.
 */
fun Context.snackbarError(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, R.drawable.ic_warning,
        "colorError",
        "colorOnError",
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
