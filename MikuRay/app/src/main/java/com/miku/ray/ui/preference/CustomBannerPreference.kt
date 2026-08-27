package com.miku.ray.ui.preference

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.UwuBannerThemeBinding
import com.miku.ray.handler.MmkvManager

class CustomBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var onImageClick: (() -> Unit)? = null
    var onImageLongClick: (() -> Unit)? = null
    private val defaultBannerTag = "DEFAULT_THEME_BANNER"

    init {
        layoutResource = R.layout.uwu_banner_theme
    }

    fun refresh() {
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        val binding = UwuBannerThemeBinding.bind(holder.itemView)
        binding.uwuNameTitleSummary.text = com.miku.ray.util.AppNameHelper.getDisplayName(context)
        binding.uwuVersionNameSummary.text = context.getString(R.string.uwu_version_name)
        binding.uwuVersionCodeSummary.text = context.getString(R.string.uwu_version_code)
        binding.uwuPackageNameSummary.text = context.getString(R.string.uwu_package_name)
        binding.uwuBuildDateSummary.text = context.getString(R.string.uwu_build_date)

        val imageView = binding.imgBannerPreference
        run {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
            val targetTag = uriString?.takeUnless { it.isBlank() } ?: defaultBannerTag
            if (imageView.tag != targetTag) {
                Glide.with(context.applicationContext).clear(imageView)
                if (targetTag == defaultBannerTag) {
                    imageView.setImageResource(R.drawable.uwu_banner_theme)
                } else {
                    Glide.with(imageView)
                        .load(Uri.parse(targetTag))
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .error(R.drawable.uwu_banner_theme)
                        .into(imageView)
                }
                imageView.tag = targetTag
            }
        }

        val imageClickTarget = binding.themeBannerImageCard
        imageClickTarget.setOnClickListener {
            onImageClick?.invoke()
        }
        imageClickTarget.setOnLongClickListener {
            onImageLongClick?.invoke()
            true
        }

        val clickTarget = binding.onClick
        clickTarget.setOnClickListener {
            this.performClick()
        }
    }
}
