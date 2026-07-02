package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.contracts.GroupServerHost
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.ui.bottomsheet.SwitchProfileBottomSheet
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Overlay Activity used to switch the active profile from the connection notification.
 *
 * It shows nothing of its own besides [SwitchProfileBottomSheet]: the same group-tabs +
 * server-list UI used on the main screen, reused here via [GroupServerHost] so
 * [GroupServerFragment] doesn't need to know which host it's running in. Once the sheet is
 * dismissed (a server was picked, or the user backed out) the Activity finishes itself, so
 * the app is never actually brought to the foreground - whatever app the user was using
 * stays in view underneath the sheet the whole time.
 */
class QuickProfileSwitchActivity : BaseActivity(), GroupServerHost {

    val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)

        // A fresh MainViewModel isn't wired up to the service yet; register + reload so the
        // group tabs and server list reflect what's actually configured/selected right now.
        mainViewModel.startListenBroadcast()
        mainViewModel.reloadServerList()

        if (savedInstanceState == null) {
            SwitchProfileBottomSheet().show(supportFragmentManager, SwitchProfileBottomSheet.TAG)
        }

        // Finish as soon as the sheet goes away - this Activity has no UI of its own.
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                    super.onFragmentViewDestroyed(fm, f)
                    if (f is SwitchProfileBottomSheet) {
                        if (!isFinishing && !isChangingConfigurations) finish()
                        fm.unregisterFragmentLifecycleCallbacks(this)
                    }
                }
            },
            false
        )
    }

    override fun restartV2Ray() {
        if (CoreServiceManager.isRunning()) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            CoreServiceManager.startVService(this@QuickProfileSwitchActivity)
        }
    }

    override fun refreshGroupTabTitles(refreshAll: Boolean) {
        val sheet = supportFragmentManager.findFragmentByTag(SwitchProfileBottomSheet.TAG) as? SwitchProfileBottomSheet
        sheet?.refreshTabBadges()
    }

    override fun showShareBottomSheet(guid: String, configType: Int) {
        // Sharing needs the full app UI, which this lightweight overlay intentionally never opens.
        toastInfo(getString(R.string.toast_share_unavailable_quick_switch))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val sheet = supportFragmentManager.findFragmentByTag(SwitchProfileBottomSheet.TAG)
                as? SwitchProfileBottomSheet
        if (sheet != null) {
            sheet.dismiss()
        } else {
            super.onBackPressed()
        }
    }
}
