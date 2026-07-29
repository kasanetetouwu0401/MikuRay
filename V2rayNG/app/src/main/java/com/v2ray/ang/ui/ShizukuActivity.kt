package com.v2ray.ang.ui

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CompoundButton
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityShizukuTetheringBinding
import com.v2ray.ang.extension.applyEdgeToEdgeListInsets
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.shizuku.ShizukuTetheringController
import com.v2ray.ang.shizuku.ShizukuTetheringService
import com.v2ray.ang.shizuku.TetheringPlatformCompat
import com.v2ray.ang.util.Utils
import rikka.shizuku.Shizuku
import java.util.UUID

/**
 * Rootless protected-tethering screen (Shizuku-powered). MikuRay uses classic Views
 * rather than Compose, unlike upstream v2rayNG's current implementation of this screen,
 * so this is a from-scratch port of the same behaviour onto [HelperBaseActivity].
 */
class ShizukuActivity : HelperBaseActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSION = 0x5342 // "SB"
    }

    private val binding by lazy { ActivityShizukuTetheringBinding.inflate(layoutInflater) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var hotspotOperationInFlight = false

    private val stateListener = ShizukuTetheringController.StateListener { runOnUiThread { refreshUi() } }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            mainHandler.post { refreshUi() }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post { refreshUi() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mainHandler.post { refreshUi() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.shizukuScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_shizuku_tethering))

        if (!resources.getBoolean(R.bool.shizuku_tethering_supported)) {
            binding.tvShizukuStatus.setText(R.string.shizuku_status_unsupported_os)
            binding.tvShizukuDetail.text = getString(R.string.shizuku_status_unsupported_os_detail, Build.VERSION.SDK_INT)
            binding.switchEnableRouting.isEnabled = false
            binding.switchWifiHotspot.isEnabled = false
            binding.btnShizukuAction.isEnabled = false
            return
        }

        binding.btnShizukuAction.setOnClickListener { onActionClicked() }

        // Tapping anywhere on the card toggles its switch too, not just the thumb itself.
        binding.cardEnableRouting.setOnClickListener { binding.switchEnableRouting.performClick() }
        binding.cardWifiHotspot.setOnClickListener { binding.switchWifiHotspot.performClick() }

        binding.switchEnableRouting.setOnCheckedChangeListener { switchView, checked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (checked) {
                if (!ShizukuTetheringController.hasPermission()) {
                    setSwitchCheckedSilently(switchView, false)
                    ShizukuTetheringController.requestPermission(REQUEST_CODE_PERMISSION)
                    return@setOnCheckedChangeListener
                }
                // Arm the sync token BEFORE binding/querying: TetheringCoreSync (running in
                // the daemon process) gates every start/stop/snapshot broadcast behind
                // "is there already a non-blank token", so without this the daemon has no
                // way to ever begin sending events and routing stays stuck at idle forever.
                MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, UUID.randomUUID().toString())
                ShizukuTetheringController.bind(this)
                // Ask the daemon process (if a core is already running) to (re)send a
                // snapshot now that routing has been armed for this session.
                MessageUtil.sendMsg2Service(this, AppConfig.MSG_QUERY_HOTSPOT_CONFIG, "")
            } else {
                ShizukuTetheringController.unbind()
            }
            refreshUi()
        }

        binding.switchWifiHotspot.setOnCheckedChangeListener { switchView, checked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (hotspotOperationInFlight) {
                // Ignore re-entrant taps while a previous toggle (which may include a
                // stop+start bounce) is still running against the shell process.
                setSwitchCheckedSilently(switchView, !checked)
                return@setOnCheckedChangeListener
            }
            hotspotOperationInFlight = true
            switchView.isEnabled = false
            bgExecutor.execute {
                val ok = try {
                    ShizukuTetheringController.setWifiHotspotEnabled(checked)
                } catch (_: Throwable) {
                    false
                }
                mainHandler.post {
                    hotspotOperationInFlight = false
                    switchView.isEnabled = true
                    if (!ok) {
                        setSwitchCheckedSilently(switchView, !checked)
                        snackbarError(getString(R.string.shizuku_hotspot_toggle_failed), title = getString(R.string.title_alerter_error))
                    }
                    refreshUi()
                }
            }
        }
    }

    /** True while [refreshUi] is applying state to the switches programmatically. */
    private var suppressSwitchListener = false

    private fun setSwitchCheckedSilently(switchView: CompoundButton, checked: Boolean) {
        suppressSwitchListener = true
        switchView.isChecked = checked
        suppressSwitchListener = false
    }

    override fun onStart() {
        super.onStart()
        ShizukuTetheringController.addListener(stateListener)
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
        refreshUi()
    }

    override fun onStop() {
        super.onStop()
        ShizukuTetheringController.removeListener(stateListener)
        try {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdown()
    }

    private fun onActionClicked() {
        when {
            !ShizukuTetheringController.isShizukuInstalled() -> {
                Utils.openUri(this, "https://shizuku.rikka.app/download/")
            }

            !ShizukuTetheringController.isShizukuRunning() -> {
                snackbarError(getString(R.string.shizuku_status_not_running), title = getString(R.string.title_alerter_error))
            }

            !ShizukuTetheringController.hasPermission() -> {
                ShizukuTetheringController.requestPermission(REQUEST_CODE_PERMISSION)
            }

            else -> {
                ShizukuTetheringController.bind(this)
            }
        }
    }

    private fun refreshUi() {
        val installed = ShizukuTetheringController.isShizukuInstalled()
        val running = installed && ShizukuTetheringController.isShizukuRunning()
        val granted = running && ShizukuTetheringController.hasPermission()

        binding.switchEnableRouting.isEnabled = granted
        binding.switchWifiHotspot.isEnabled = granted

        when {
            !installed -> {
                binding.tvShizukuStatus.setText(R.string.shizuku_status_not_installed)
                binding.tvShizukuDetail.setText(R.string.shizuku_status_not_installed_detail)
                binding.btnShizukuAction.setText(R.string.shizuku_action_install)
                binding.btnShizukuAction.isEnabled = true
            }

            !running -> {
                binding.tvShizukuStatus.setText(R.string.shizuku_status_not_running)
                binding.tvShizukuDetail.setText(R.string.shizuku_status_not_running_detail)
                binding.btnShizukuAction.setText(R.string.shizuku_action_open_shizuku)
                binding.btnShizukuAction.isEnabled = true
            }

            !granted -> {
                binding.tvShizukuStatus.setText(R.string.shizuku_status_permission_needed)
                binding.tvShizukuDetail.setText(R.string.shizuku_status_permission_needed_detail)
                binding.btnShizukuAction.setText(R.string.shizuku_action_grant_permission)
                binding.btnShizukuAction.isEnabled = true
            }

            else -> {
                val state = ShizukuTetheringController.routingState()
                binding.btnShizukuAction.isEnabled = false
                binding.tvShizukuStatus.text = when (state) {
                    ShizukuTetheringService.ROUTING_STATE_ACTIVE -> getString(R.string.shizuku_status_active, ShizukuTetheringController.lastProfileName())
                    ShizukuTetheringService.ROUTING_STATE_STARTING -> getString(R.string.shizuku_status_starting)
                    ShizukuTetheringService.ROUTING_STATE_RETRYING -> getString(R.string.shizuku_status_retrying)
                    ShizukuTetheringService.ROUTING_STATE_ERROR -> getString(R.string.shizuku_status_error)
                    else -> getString(R.string.shizuku_status_idle)
                }
                binding.tvShizukuDetail.text = ShizukuTetheringController.routingDetail()
                setSwitchCheckedSilently(binding.switchEnableRouting, state != ShizukuTetheringService.ROUTING_STATE_IDLE)

                val mask = ShizukuTetheringController.activeTetheringTypes()
                setSwitchCheckedSilently(binding.switchWifiHotspot, (mask and TetheringPlatformCompat.TETHERING_WIFI) != 0)
                binding.tvActiveDevices.text = if (mask == 0) {
                    getString(R.string.shizuku_active_devices_none)
                } else {
                    getString(R.string.shizuku_active_devices_some)
                }
            }
        }
    }
}
