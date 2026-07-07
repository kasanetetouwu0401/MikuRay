package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.util.showBlur
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.ui.compose.screens.AboutRowsSection
import com.v2ray.ang.ui.compose.theme.MikuComposeTheme
import com.v2ray.ang.util.Utils

class AboutActivity : BaseActivity() {
    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom,    displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_about))

        // Lima baris aksi (Source Code, OSS Licenses, Feedback, Telegram, Privacy Policy)
        // sekarang dirender lewat Compose, tapi tetap di dalam <ExpandableView> XML yang sama
        // jadi animasi expand/collapse-nya tidak berubah sama sekali.
        findViewById<ComposeView>(R.id.compose_about_rows).apply {
            setContent {
                MikuComposeTheme {
                    AboutRowsSection(
                        onSourceCodeClick = { Utils.openUri(this@AboutActivity, AppConfig.APP_URL) },
                        onOssLicensesClick = { showOssLicensesDialog() },
                        onFeedbackClick = { Utils.openUri(this@AboutActivity, AppConfig.APP_ISSUES_URL) },
                        onTelegramChannelClick = { Utils.openUri(this@AboutActivity, AppConfig.TG_CHANNEL_URL) },
                        onPrivacyPolicyClick = { Utils.openUri(this@AboutActivity, AppConfig.APP_PRIVACY_POLICY) },
                    )
                }
            }
        }

        "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }
        BuildConfig.APPLICATION_ID.also {
            binding.tvAppId.text = it
        }
    }

    private fun showOssLicensesDialog() {
        val webView = android.webkit.WebView(this)
        webView.loadUrl("file:///android_asset/open_source_licenses.html")
        MaterialAlertDialogBuilder(this)
            .setTitle("Open source licenses")
            .setView(webView)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .showBlur()
    }
}
