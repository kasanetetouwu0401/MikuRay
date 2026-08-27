package com.miku.ray.ui.shortcut
import com.miku.ray.ui.base.BaseActivity
import android.os.Bundle
import com.miku.ray.databinding.ActivityNoneBinding
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.core.LauncherManager

class ScStartActivity : BaseActivity() {
    private val binding by lazy { ActivityNoneBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(binding.root)

        if (!CoreServiceManager.isRunning()) {
            LauncherManager.startServiceFromToggle(this)
        }
        finish()
    }
}

