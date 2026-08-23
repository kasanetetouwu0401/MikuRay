package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerSshBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.server.fields.AddressPortFields
import com.miku.ray.util.Utils
import com.miku.ray.util.showDeleteConfirmDialog

class ServerSshActivity : BaseActivity() {
    private val binding by lazy { ActivityServerSshBinding.inflate(layoutInflater) }
    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false) && editGuid.isNotEmpty() && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.SSH.value)) ?: EConfigType.SSH
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }
    private lateinit var addressPortFields: AddressPortFields

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = MmkvManager.decodeServerConfig(editGuid)
        setContentView(binding.root)
        binding.serverScrollContent.applyEdgeToEdgeListInsets()
        addressPortFields = AddressPortFields(binding.root)
        setupToolbar(binding.toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString(), subtitle = getString(R.string.subtitle_server_config))
        if (config != null) bindServer(config) else clearServer()
    }

    private fun bindServer(config: ProfileItem) {
        addressPortFields.bind(config.copy(server = config.sshServer, serverPort = config.sshPort))
        binding.etSecurity.text = Utils.getEditable(config.sshUser.orEmpty())
        binding.etId.text = Utils.getEditable(config.sshPass.orEmpty())
        binding.etLocalPort.text = Utils.getEditable(config.sshPortaLocal ?: AppConfig.SSH_LOCAL_PORT)
        binding.etPayload.text = Utils.getEditable(config.sshPayload.orEmpty())
        binding.etProxy.text = Utils.getEditable(config.sshRemoteProxy?.let { "$it:${config.sshRemoteProxyPort ?: "8080"}" }.orEmpty())
        binding.etTunnelType.text = Utils.getEditable(config.sshTunnelType ?: "1")
        binding.etSni.text = Utils.getEditable(config.sshTlsServerName ?: config.sshWsPayload.orEmpty())
        binding.etTlsForcing.text = Utils.getEditable(config.sshTlsForcing ?: "tlsAuto")
        binding.switchTrustAll.isChecked = config.sshTrustAllCertificates != false
    }

    private fun clearServer() {
        addressPortFields.clear()
        binding.etLocalPort.text = Utils.getEditable(AppConfig.SSH_LOCAL_PORT)
        binding.etTunnelType.text = Utils.getEditable("1")
        binding.etTlsForcing.text = Utils.getEditable("tlsAuto")
        binding.switchTrustAll.isChecked = true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(addressPortFields.remarksText)) {
            snackbarError(getString(R.string.server_lab_remarks), title = getString(R.string.title_alerter_error))
            return false
        }
        if (TextUtils.isEmpty(addressPortFields.addressText)) {
            snackbarError(getString(R.string.server_lab_address), title = getString(R.string.title_alerter_error))
            return false
        }
        val sshPort = Utils.parseInt(addressPortFields.portText)
        if (sshPort !in 1..65535) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return false
        }
        if (binding.etSecurity.text.isNullOrBlank() || binding.etId.text == null) {
            snackbarError("SSH username and password are required")
            return false
        }
        val localPort = Utils.parseInt(binding.etLocalPort.text.toString())
        if (localPort !in 1..65535) {
            snackbarError("SSH local SOCKS port is invalid")
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)
        config.remarks = addressPortFields.remarksText.trim()
        config.sshServer = addressPortFields.addressText.trim()
        config.sshPort = sshPort.toString()
        config.sshUser = binding.etSecurity.text.toString().trim()
        config.sshPass = binding.etId.text.toString()
        config.sshPortaLocal = localPort.toString()
        config.sshPayload = binding.etPayload.text.toString().takeIf { it.isNotBlank() }
        config.sshUseDefaultPayload = config.sshPayload.isNullOrBlank()
        val tunnelType = Utils.parseInt(binding.etTunnelType.text.toString())
        if (tunnelType !in 1..5) {
            snackbarError("Neko tunnel type must be between 1 and 5")
            return false
        }
        config.sshTunnelType = tunnelType.toString()
        config.sshTlsServerName = binding.etSni.text.toString().trim().takeIf { it.isNotEmpty() }
        config.sshWsPayload = config.sshTlsServerName
        config.sshTlsForcing = binding.etTlsForcing.text.toString().trim().ifBlank { "tlsAuto" }
        config.sshTrustAllCertificates = binding.switchTrustAll.isChecked

        val proxy = binding.etProxy.text.toString().trim()
        if (proxy.isNotEmpty()) {
            val split = proxy.lastIndexOf(':')
            if (split <= 0 || proxy.substring(split + 1).toIntOrNull() !in 1..65535) {
                snackbarError("Remote proxy must be host:port")
                return false
            }
            config.sshRemoteProxy = proxy.substring(0, split)
            config.sshRemoteProxyPort = proxy.substring(split + 1)
            config.sshTunnelType = when (tunnelType) {
                1, 2 -> "2"
                else -> "5"
            }
        } else {
            config.sshRemoteProxy = null
            config.sshRemoteProxyPort = null
            if (tunnelType == 2 || tunnelType == 5) {
                snackbarError("Remote proxy is required for tunnel type $tunnelType")
                return false
            }
        }
        config.sshCompression = true
        config.description = AngConfigManager.generateDescription(config)
        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) config.subscriptionId = subscriptionId.orEmpty()
        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer() {
        if (editGuid.isEmpty()) return
        if (editGuid == MmkvManager.getSelectServer() || MmkvManager.isServerPinned(editGuid)) {
            snackbarError(getString(R.string.toast_action_not_allowed), title = getString(R.string.title_alerter_error))
            return
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                MmkvManager.removeServer(editGuid)
                finish()
            }
        } else {
            MmkvManager.removeServer(editGuid)
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        menu.findItem(R.id.del_config)?.isVisible = editGuid.isNotEmpty() && !isRunning
        menu.findItem(R.id.save_config)?.isVisible = !isRunning
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> { deleteServer(); true }
        R.id.save_config -> { saveServer(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
