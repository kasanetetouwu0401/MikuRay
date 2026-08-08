package com.v2ray.ang.ui.about

import com.v2ray.ang.ui.base.BaseActivity
import android.content.Intent
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.extension.applyEdgeToEdgeListInsets
import com.v2ray.ang.util.Utils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : BaseActivity() {
    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(binding.root)

        binding.aboutContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_about), subtitle = getString(R.string.subtitle_about))

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_URL)
        }

        binding.layoutFeedback.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_ISSUES_URL)
        }

        binding.layoutOssLicenses.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(this, AppConfig.TG_CHANNEL_URL)
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY)
        }

        // getLibVersion() is a native call into the same Go library the VPN core uses,
        // and can block on the core's shared lock if startLoop()/reload is in flight
        // elsewhere in the (same-process) app. Fetch it off the main thread so opening
        // this screen never stalls while the tunnel is coming up.
        lifecycleScope.launch {
            val libVersion = withContext(Dispatchers.IO) { CoreNativeManager.getLibVersion() }
            binding.tvVersion.text = "v${BuildConfig.VERSION_NAME} ($libVersion)"
        }
        BuildConfig.APPLICATION_ID.also {
            binding.tvAppId.text = it
        }
    }
}
