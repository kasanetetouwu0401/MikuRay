package com.miku.ray.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.miku.ray.R
import com.miku.ray.databinding.UwuActivityWelcomeBinding
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.splash.SplashActivity

class WelcomeActivity : BaseActivity() {
    private val binding by lazy { UwuActivityWelcomeBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MmkvManager.decodeSettingsBool(PREF_WELCOME_SHOW)) {
            navigateToMain()
            return
        }

        setContentView(binding.root)

        val rootLayout = binding.mainContent
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }

        setupViewsAndListeners()
    }

    private fun setupViewsAndListeners() {
        val page1 = binding.page1
        val page2 = binding.page2
        val page3 = binding.page3

        page2.visibility = View.GONE
        page3.visibility = View.GONE

        binding.page1button.setOnClickListener {
            page1.visibility = View.GONE
            page2.visibility = View.VISIBLE
        }

        binding.page2button.setOnClickListener {
            page2.visibility = View.GONE
            page3.visibility = View.VISIBLE
        }

        val navigateAction = View.OnClickListener { navigateToMain() }

        binding.page3button.setOnClickListener(navigateAction)
        binding.page1Skip.setOnClickListener(navigateAction)
        binding.page2Skip.setOnClickListener(navigateAction)
    }

    private fun navigateToMain() {
        MmkvManager.encodeSettings(PREF_WELCOME_SHOW, true)
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    companion object {
        const val PREF_WELCOME_SHOW = "pref_welcome_show"
    }
}
