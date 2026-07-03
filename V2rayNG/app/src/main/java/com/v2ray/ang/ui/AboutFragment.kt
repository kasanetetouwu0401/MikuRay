package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showBlur

class AboutFragment : MikuFragment<ActivityAboutBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        ActivityAboutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar(binding.toolbar, title = getString(R.string.title_about))

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(requireContext(), AppConfig.APP_URL)
        }

        binding.layoutFeedback.setOnClickListener {
            Utils.openUri(requireContext(), AppConfig.APP_ISSUES_URL)
        }

        binding.layoutOssLicenses.setOnClickListener {
            val webView = WebView(requireContext())
            webView.loadUrl("file:///android_asset/open_source_licenses.html")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Open source licenses")
                .setView(webView)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .showBlur()
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(requireContext(), AppConfig.TG_CHANNEL_URL)
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(requireContext(), AppConfig.APP_PRIVACY_POLICY)
        }

        "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }
        BuildConfig.APPLICATION_ID.also {
            binding.tvAppId.text = it
        }
    }
}
