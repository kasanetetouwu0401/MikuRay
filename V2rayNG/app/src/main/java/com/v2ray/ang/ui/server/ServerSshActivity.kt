package com.v2ray.ang.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.applyEdgeToEdgeListInsets
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.server.fields.AddressPortFields
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showDeleteConfirmDialog

class ServerSshActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val etUsername: EditText by lazy { findViewById(R.id.et_ssh_username) }
    private val spAuthType: AutoCompleteTextView by lazy { findViewById(R.id.sp_ssh_auth_type) }

    private val tilPassword: TextInputLayout by lazy { findViewById(R.id.til_ssh_password) }
    private val etPassword: EditText by lazy { findViewById(R.id.et_ssh_password) }

    private val tilPrivateKey: TextInputLayout by lazy { findViewById(R.id.til_ssh_private_key) }
    private val etPrivateKey: EditText by lazy { findViewById(R.id.et_ssh_private_key) }

    private val tilPrivateKeyPassphrase: TextInputLayout by lazy { findViewById(R.id.til_ssh_private_key_passphrase) }
    private val etPrivateKeyPassphrase: EditText by lazy { findViewById(R.id.et_ssh_private_key_passphrase) }

    private val tilCertificate: TextInputLayout by lazy { findViewById(R.id.til_ssh_certificate) }
    private val etCertificate: EditText by lazy { findViewById(R.id.et_ssh_certificate) }

    private val spConnectionType: AutoCompleteTextView by lazy { findViewById(R.id.sp_ssh_connection_type) }
    private val tilPayload: TextInputLayout by lazy { findViewById(R.id.til_ssh_payload) }
    private val etPayload: EditText by lazy { findViewById(R.id.et_ssh_payload) }
    private val tilProxy: TextInputLayout by lazy { findViewById(R.id.til_ssh_proxy) }
    private val etProxy: EditText by lazy { findViewById(R.id.et_ssh_proxy) }
    private val tilSni: TextInputLayout by lazy { findViewById(R.id.til_ssh_sni) }
    private val etSni: EditText by lazy { findViewById(R.id.et_ssh_sni) }

    private val chkCompression: com.google.android.material.materialswitch.MaterialSwitch by lazy { findViewById(R.id.chk_ssh_compression) }
    private val chkUdpgw: com.google.android.material.materialswitch.MaterialSwitch by lazy { findViewById(R.id.chk_ssh_udpgw) }
    private val tilUdpgwAddress: TextInputLayout by lazy { findViewById(R.id.til_ssh_udpgw_address) }
    private val etUdpgwAddress: EditText by lazy { findViewById(R.id.et_ssh_udpgw_address) }

    private lateinit var addressPortFields: AddressPortFields

    private val authTypes by lazy { resources.getStringArray(R.array.ssh_auth_types) }
    private val connectionTypes by lazy { resources.getStringArray(R.array.ssh_connection_types) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(R.layout.activity_server_ssh)

        findViewById<androidx.core.widget.NestedScrollView>(R.id.server_scroll_content).applyEdgeToEdgeListInsets()

        addressPortFields = AddressPortFields(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = EConfigType.SSH.toString())

        spAuthType.setOnItemClickListener { _, _, _, _ -> updateAuthFieldsVisibility() }
        spConnectionType.setOnItemClickListener { _, _, _, _ -> updateConnectionTypeFieldsVisibility() }
        chkUdpgw.setOnCheckedChangeListener { _, checked -> tilUdpgwAddress.visibility = if (checked) View.VISIBLE else View.GONE }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
        updateAuthFieldsVisibility()
        updateConnectionTypeFieldsVisibility()
    }

    /**
     * Neko Injector-style presets: each connection type shows only the fields relevant to
     * that combination of tricks, instead of always showing SNI/Payload/Proxy at once.
     */
    private fun updateConnectionTypeFieldsVisibility() {
        val type = connectionTypes.getOrElse(Utils.arrayFind(connectionTypes, spConnectionType.text?.toString().orEmpty()).takeIf { it >= 0 } ?: 0) {
            AppConfig.SSH_TYPE_DIRECT
        }
        val showSni = type == AppConfig.SSH_TYPE_SSL || type == AppConfig.SSH_TYPE_SSL_PAYLOAD || type == AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY
        val showPayload = type == AppConfig.SSH_TYPE_PAYLOAD || type == AppConfig.SSH_TYPE_SSL_PAYLOAD || type == AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY
        val showProxy = type == AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY

        tilSni.visibility = if (showSni) View.VISIBLE else View.GONE
        tilPayload.visibility = if (showPayload) View.VISIBLE else View.GONE
        tilProxy.visibility = if (showProxy) View.VISIBLE else View.GONE
    }

    private fun updateAuthFieldsVisibility() {
        when (spAuthType.text?.toString()) {
            AppConfig.SSH_AUTH_PRIVATE_KEY -> {
                tilPassword.visibility = View.GONE
                tilPrivateKey.visibility = View.VISIBLE
                tilPrivateKeyPassphrase.visibility = View.VISIBLE
                tilCertificate.visibility = View.GONE
            }

            AppConfig.SSH_AUTH_CERTIFICATE -> {
                tilPassword.visibility = View.GONE
                tilPrivateKey.visibility = View.VISIBLE
                tilPrivateKeyPassphrase.visibility = View.VISIBLE
                tilCertificate.visibility = View.VISIBLE
            }

            else -> {
                tilPassword.visibility = View.VISIBLE
                tilPrivateKey.visibility = View.GONE
                tilPrivateKeyPassphrase.visibility = View.GONE
                tilCertificate.visibility = View.GONE
            }
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)
        etUsername.text = Utils.getEditable(config.username.orEmpty())
        val authPos = Utils.arrayFind(authTypes, config.sshAuthType.orEmpty())
        spAuthType.setText(authTypes.getOrElse(authPos.takeIf { it >= 0 } ?: 0) { authTypes.first() }, false)
        etPassword.text = Utils.getEditable(config.password.orEmpty())
        etPrivateKey.text = Utils.getEditable(config.sshPrivateKey.orEmpty())
        etPrivateKeyPassphrase.text = Utils.getEditable(config.sshPrivateKeyPassphrase.orEmpty())
        etCertificate.text = Utils.getEditable(config.sshCertificate.orEmpty())
        etPayload.text = Utils.getEditable(config.sshPayload.orEmpty())
        etProxy.text = Utils.getEditable(config.sshProxy.orEmpty())
        etSni.text = Utils.getEditable(config.sni.orEmpty())
        // Configs saved before the Type selector existed have no sshConnectionType: infer one
        // from whichever fields already hold values, so saving again doesn't silently wipe them.
        val effectiveType = config.sshConnectionType.orEmpty().ifEmpty {
            val hasProxy = !config.sshProxy.isNullOrEmpty()
            val hasPayload = !config.sshPayload.isNullOrEmpty()
            val hasSni = !config.sni.isNullOrEmpty()
            when {
                hasProxy -> AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY
                hasSni && hasPayload -> AppConfig.SSH_TYPE_SSL_PAYLOAD
                hasPayload -> AppConfig.SSH_TYPE_PAYLOAD
                hasSni -> AppConfig.SSH_TYPE_SSL
                else -> AppConfig.SSH_TYPE_DIRECT
            }
        }
        val typePos = Utils.arrayFind(connectionTypes, effectiveType)
        spConnectionType.setText(connectionTypes.getOrElse(typePos.takeIf { it >= 0 } ?: 0) { connectionTypes.first() }, false)
        chkCompression.isChecked = config.sshCompression ?: false
        chkUdpgw.isChecked = config.sshUdpgwEnabled ?: false
        tilUdpgwAddress.visibility = if (chkUdpgw.isChecked) View.VISIBLE else View.GONE
        etUdpgwAddress.text = Utils.getEditable(config.sshUdpgwAddress.orEmpty())
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        etUsername.text = null
        spAuthType.setText(authTypes.firstOrNull().orEmpty(), false)
        etPassword.text = null
        etPrivateKey.text = null
        etPrivateKeyPassphrase.text = null
        etCertificate.text = null
        etPayload.text = null
        etProxy.text = null
        etSni.text = null
        spConnectionType.setText(connectionTypes.firstOrNull().orEmpty(), false)
        chkCompression.isChecked = false
        chkUdpgw.isChecked = false
        tilUdpgwAddress.visibility = View.GONE
        etUdpgwAddress.text = null
        return true
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
        if (Utils.parseInt(addressPortFields.portText) <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return false
        }
        if (TextUtils.isEmpty(etUsername.text)) {
            snackbarError(getString(R.string.server_lab_ssh_username), title = getString(R.string.title_alerter_error))
            return false
        }

        val authType = authTypes.getOrElse(Utils.arrayFind(authTypes, spAuthType.text?.toString().orEmpty()).takeIf { it >= 0 } ?: 0) {
            AppConfig.SSH_AUTH_PASSWORD
        }

        if (authType == AppConfig.SSH_AUTH_PRIVATE_KEY || authType == AppConfig.SSH_AUTH_CERTIFICATE) {
            if (TextUtils.isEmpty(etPrivateKey.text)) {
                snackbarError(getString(R.string.server_lab_ssh_private_key), title = getString(R.string.title_alerter_error))
                return false
            }
        }
        if (authType == AppConfig.SSH_AUTH_CERTIFICATE && TextUtils.isEmpty(etCertificate.text)) {
            snackbarError(getString(R.string.server_lab_ssh_certificate), title = getString(R.string.title_alerter_error))
            return false
        }

        val connectionType = connectionTypes.getOrElse(Utils.arrayFind(connectionTypes, spConnectionType.text?.toString().orEmpty()).takeIf { it >= 0 } ?: 0) {
            AppConfig.SSH_TYPE_DIRECT
        }
        val showSni = connectionType == AppConfig.SSH_TYPE_SSL || connectionType == AppConfig.SSH_TYPE_SSL_PAYLOAD || connectionType == AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY
        val showPayload = connectionType == AppConfig.SSH_TYPE_PAYLOAD || connectionType == AppConfig.SSH_TYPE_SSL_PAYLOAD || connectionType == AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY
        val showProxy = connectionType == AppConfig.SSH_TYPE_SSL_PAYLOAD_PROXY

        val proxyText = if (showProxy) etProxy.text.toString().trim() else ""
        if (proxyText.isNotEmpty() && (!proxyText.contains(":") || Utils.parseInt(proxyText.substringAfterLast(":")) <= 0)) {
            snackbarError(getString(R.string.server_lab_ssh_proxy), title = getString(R.string.title_alerter_error))
            return false
        }

        val udpgwEnabled = chkUdpgw.isChecked
        val udpgwAddress = etUdpgwAddress.text.toString().trim()
        if (udpgwEnabled && (udpgwAddress.isEmpty() || !udpgwAddress.contains(":") || Utils.parseInt(udpgwAddress.substringAfterLast(":")) <= 0)) {
            snackbarError(getString(R.string.server_lab_ssh_udpgw_address), title = getString(R.string.title_alerter_error))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.SSH)

        addressPortFields.save(config)
        config.username = etUsername.text.toString().trim()
        config.sshAuthType = authType
        config.password = etPassword.text.toString()
        config.sshPrivateKey = etPrivateKey.text.toString()
        config.sshPrivateKeyPassphrase = etPrivateKeyPassphrase.text.toString()
        config.sshCertificate = etCertificate.text.toString()
        config.sshConnectionType = connectionType
        config.sshPayload = if (showPayload) etPayload.text.toString() else ""
        config.sshProxy = proxyText
        config.sni = if (showSni) etSni.text.toString().trim() else ""
        config.sshCompression = chkCompression.isChecked
        config.sshUdpgwEnabled = udpgwEnabled
        config.sshUdpgwAddress = if (udpgwEnabled) udpgwAddress else ""

        config.description = AngConfigManager.generateDescription(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (editGuid != MmkvManager.getSelectServer()) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                    showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                        MmkvManager.removeServer(editGuid)
                        finish()
                    }
                } else {
                    MmkvManager.removeServer(editGuid)
                    finish()
                }
            } else {
                snackbarError(getString(R.string.toast_action_not_allowed), title = getString(R.string.title_alerter_error))
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu.findItem(R.id.del_config)
        val saveButton = menu.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
